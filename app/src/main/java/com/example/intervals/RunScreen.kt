package com.example.intervalrunner

import android.os.SystemClock
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RunScreen(plan: RunPlan, onFinish: (RunPlan) -> Unit) {
    val context = LocalContext.current
    val tts = remember {
        TextToSpeech(context, null)
    }
    LaunchedEffect(tts) {
        tts.language = Locale.US
    }

    val allBlocks = plan.blocks
    var currentBlockIndex by remember { mutableStateOf(0) }
    var currentIntervalIndex by remember { mutableStateOf(0) }
    var currentBlockPass by remember { mutableStateOf(0) }
    var secondsRemaining by remember {
        mutableStateOf(allBlocks.firstOrNull()?.intervals?.firstOrNull()?.durationSeconds ?: 0)
    }
    var running by remember { mutableStateOf(false) }

    var isHoldingStop by remember { mutableStateOf(false) }
    var stopProgress by remember { mutableStateOf(0f) }

    var isHoldingSkip by remember { mutableStateOf(false) }
    var skipProgress by remember { mutableStateOf(0f) }

    // --- Countdown timer ---
    LaunchedEffect(running, currentBlockIndex, currentIntervalIndex, currentBlockPass) {
        if (!running) return@LaunchedEffect
        while (running && currentBlockIndex < allBlocks.size) {
            val block = allBlocks[currentBlockIndex]
            val interval = block.intervals[currentIntervalIndex]

            // Speak the interval label
            tts.speak(interval.label, TextToSpeech.QUEUE_FLUSH, null, null)

            val endAt = SystemClock.elapsedRealtime() + secondsRemaining * 1000L
            while (running) {
                val msLeft = endAt - SystemClock.elapsedRealtime()
                if (msLeft <= 0) {
                    secondsRemaining = 0
                    break
                }
                // Round up so we display "1s" until the moment we hit zero,
                // rather than dropping to 0 with a full second still to go.
                secondsRemaining = ((msLeft + 999) / 1000).toInt()
                delay(msLeft.coerceAtMost(250L))
            }

            // Move to next interval
            currentIntervalIndex++
            if (currentIntervalIndex >= block.intervals.size) {
                currentIntervalIndex = 0
                val targetPasses = block.repeatCount ?: 1
                val completedPasses = currentBlockPass + 1
                if (block.repeatIndefinitely || completedPasses < targetPasses) {
                    currentBlockPass = completedPasses
                } else {
                    currentBlockPass = 0
                    currentBlockIndex++
                    if (currentBlockIndex >= allBlocks.size) {
                        running = false
                        onFinish(plan)
                        return@LaunchedEffect
                    }
                }
            }

            // Set next interval
            val nextInterval = allBlocks.getOrNull(currentBlockIndex)?.intervals?.getOrNull(currentIntervalIndex)
            secondsRemaining = nextInterval?.durationSeconds ?: 0
        }
    }

    // --- Hold-to-stop logic ---
    LaunchedEffect(isHoldingStop) {
        if (isHoldingStop) {
            stopProgress = 0f
            val steps = 20
            repeat(steps) {
                stopProgress += 1f / steps
                delay(50)
            }
            running = false
            onFinish(plan)
            isHoldingStop = false
        } else stopProgress = 0f
    }

    // --- Hold-to-skip-block logic ---
    LaunchedEffect(isHoldingSkip) {
        if (isHoldingSkip) {
            skipProgress = 0f
            val steps = 10
            repeat(steps) {
                skipProgress += 1f / steps
                delay(50)
            }
            val nextBlockIndex = currentBlockIndex + 1
            if (nextBlockIndex >= allBlocks.size) {
                running = false
                onFinish(plan)
            } else {
                currentBlockPass = 0
                currentIntervalIndex = 0
                currentBlockIndex = nextBlockIndex
                secondsRemaining = allBlocks[nextBlockIndex].intervals.firstOrNull()?.durationSeconds ?: 0
            }
            isHoldingSkip = false
        } else skipProgress = 0f
    }

    val currentInterval = allBlocks.getOrNull(currentBlockIndex)?.intervals?.getOrNull(currentIntervalIndex)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        currentInterval?.let { interval ->
            Text(interval.label, style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(8.dp))

            // Show "N min M sec" if > 60, otherwise just "SS sec"
            if (secondsRemaining > 59) {
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                Text("${minutes}m ${seconds}s", style = MaterialTheme.typography.displayLarge)
            } else {
                Text("${secondsRemaining}s", style = MaterialTheme.typography.displayLarge)
            }

            Spacer(modifier = Modifier.height(20.dp))

            val block = allBlocks[currentBlockIndex]
            val curIntervalDur = block.intervals[currentIntervalIndex].durationSeconds
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

            val repTotal = if (block.repeatIndefinitely) "∞" else (block.repeatCount ?: 1).toString()
            val progressParts = buildList {
                if (block.intervals.size > 1) add("Interval ${currentIntervalIndex + 1}/${block.intervals.size}")
                if (block.repeatIndefinitely || (block.repeatCount ?: 1) > 1) {
                    add("Rep ${currentBlockPass + 1}/$repTotal")
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
                        .clickable { running = !running }
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
                            detectTapGestures(onLongPress = { isHoldingSkip = true })
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
                            detectTapGestures(onLongPress = { isHoldingStop = true })
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

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
}
