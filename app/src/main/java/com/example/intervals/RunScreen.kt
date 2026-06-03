package com.example.intervalrunner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun RunScreen(plan: RunPlan, onFinish: (RunPlan, List<BlockExecution>) -> Unit) {
    val context = LocalContext.current

    // Prepare the engine for this plan unless it's already mid-run on the same plan.
    LaunchedEffect(plan) {
        val s = TimerEngine.state.value
        if (s.plan !== plan || s.finished) {
            TimerEngine.prepare(plan)
        }
    }

    val snap by TimerEngine.state.collectAsStateWithLifecycle()

    // Engine sets finished=true on natural completion or explicit stop. Bubble up,
    // but only when the finished state belongs to THIS plan — otherwise a leftover
    // finished=true from a previous run would skip us straight to summary.
    LaunchedEffect(snap.finished, snap.plan) {
        if (snap.finished && snap.plan === plan) onFinish(plan, snap.executions)
    }

    val allBlocks = plan.blocks
    val currentBlockIndex = snap.blockIndex.coerceAtMost(allBlocks.size - 1).coerceAtLeast(0)
    val currentBlock = allBlocks.getOrNull(currentBlockIndex) ?: return
    val currentIntervalIndex = snap.intervalIndex
        .coerceAtMost(currentBlock.intervals.size - 1).coerceAtLeast(0)
    val currentInterval = currentBlock.intervals.getOrNull(currentIntervalIndex) ?: return
    val secondsRemaining = snap.secondsRemaining
    val running = snap.running
    val blockElapsedSeconds = snap.blockElapsedSeconds
    val currentBlockPass = snap.blockPass

    var isHoldingStop by remember { mutableStateOf(false) }
    var stopProgress by remember { mutableStateOf(0f) }

    var isHoldingSkip by remember { mutableStateOf(false) }
    var skipProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isHoldingStop) {
        if (isHoldingStop) {
            stopProgress = 0f
            val steps = 20
            repeat(steps) {
                stopProgress += 1f / steps
                delay(50)
            }
            TimerService.sendCommand(context, TimerService.ACTION_STOP)
            isHoldingStop = false
        } else stopProgress = 0f
    }

    LaunchedEffect(isHoldingSkip) {
        if (isHoldingSkip) {
            skipProgress = 0f
            val steps = 10
            repeat(steps) {
                skipProgress += 1f / steps
                delay(50)
            }
            TimerService.sendCommand(context, TimerService.ACTION_SKIP)
            isHoldingSkip = false
        } else skipProgress = 0f
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(currentInterval.label, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        if (secondsRemaining > 59) {
            val minutes = secondsRemaining / 60
            val seconds = secondsRemaining % 60
            Text("${minutes}m ${seconds}s", style = MaterialTheme.typography.displayLarge)
        } else {
            Text("${secondsRemaining}s", style = MaterialTheme.typography.displayLarge)
        }

        Spacer(modifier = Modifier.height(20.dp))

        val curIntervalDur = currentBlock.intervals[currentIntervalIndex].durationSeconds
        val curIntervalFrac = if (curIntervalDur > 0) {
            ((curIntervalDur - secondsRemaining).toFloat() / curIntervalDur).coerceIn(0f, 1f)
        } else 0f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            allBlocks.forEachIndexed { index, b ->
                val fill: Float = when {
                    index < currentBlockIndex -> 1f
                    index > currentBlockIndex -> 0f
                    b.repeatIndefinitely -> {
                        val intervalsTotal = b.intervals.size.coerceAtLeast(1)
                        ((currentIntervalIndex + curIntervalFrac) / intervalsTotal).coerceIn(0f, 1f)
                    }
                    b.repeatDurationSeconds != null -> {
                        val budget = b.repeatDurationSeconds.coerceAtLeast(1).toFloat()
                        (blockElapsedSeconds.toFloat() / budget).coerceIn(0f, 1f)
                    }
                    else -> {
                        val passes = (b.repeatCount ?: 1).coerceAtLeast(1)
                        val intervalsTotal = b.intervals.size.coerceAtLeast(1)
                        val units = (passes * intervalsTotal).toFloat()
                        val done = currentBlockPass * intervalsTotal + currentIntervalIndex + curIntervalFrac
                        (done / units).coerceIn(0f, 1f)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val progressParts = buildList {
            if (currentBlock.intervals.size > 1) {
                add("Interval ${currentIntervalIndex + 1}/${currentBlock.intervals.size}")
            }
            val showRep = currentBlock.repeatIndefinitely ||
                    currentBlock.repeatDurationSeconds != null ||
                    (currentBlock.repeatCount ?: 1) > 1
            if (showRep) {
                val repTotal = when {
                    currentBlock.repeatIndefinitely -> "∞"
                    currentBlock.repeatDurationSeconds != null -> null
                    else -> (currentBlock.repeatCount ?: 1).toString()
                }
                add(if (repTotal != null) "Rep ${currentBlockPass + 1}/$repTotal" else "Rep ${currentBlockPass + 1}")
            }
            currentBlock.repeatDurationSeconds?.let { budget ->
                val left = (budget - blockElapsedSeconds).coerceAtLeast(0)
                val m = left / 60
                val s = left % 60
                add(if (m > 0) "${m}m ${s}s left" else "${s}s left")
            }
            if (allBlocks.size > 1) add("Block ${currentBlockIndex + 1}/${allBlocks.size}")
        }
        if (progressParts.isNotEmpty()) {
            Text(
                progressParts.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .width(100.dp)
                    .clickable {
                        TimerService.sendCommand(context, TimerService.ACTION_PAUSE_RESUME)
                    }
                    .background(
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (running) "Pause" else "Start",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .height(56.dp)
                    .width(100.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                tryAwaitRelease()
                                isHoldingSkip = false
                            },
                            onLongPress = { isHoldingSkip = true }
                        )
                    }
                    .background(
                        color = if (isHoldingSkip) {
                            Color(0xFFFF9800).copy(alpha = 0.5f + 0.5f * skipProgress)
                        } else Color(0xFFFF9800),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    ">>",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .height(56.dp)
                    .width(100.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                tryAwaitRelease()
                                isHoldingStop = false
                            },
                            onLongPress = { isHoldingStop = true }
                        )
                    }
                    .background(
                        color = if (isHoldingStop) Color.Red.copy(alpha = stopProgress) else Color.Gray,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Stop",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isHoldingStop) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = stopProgress, modifier = Modifier.fillMaxWidth().height(4.dp))
        }

        if (isHoldingSkip) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = skipProgress,
                color = Color(0xFFFF9800),
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
}
