package com.example.intervalrunner

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.*

@Composable
fun RunScreen(plan: RunPlan, onPlanFinished: (RunPlan) -> Unit) {
    val context = LocalContext.current
    val tts = remember {
        TextToSpeech(context, null)
    }
    LaunchedEffect(tts) {
        tts.language = Locale.US
    }

    var currentBlockIndex by remember { mutableStateOf(0) }
    var currentIntervalIndex by remember { mutableStateOf(0) }
    var secondsRemaining by remember { mutableStateOf(
        plan.blocks.getOrNull(0)?.intervals?.getOrNull(0)?.durationSeconds ?: 0
    ) }
    var running by remember { mutableStateOf(false) }

    var isHoldingStop by remember { mutableStateOf(false) }
    var stopProgress by remember { mutableStateOf(0f) }

    // --- Track block repeat counts ---
    val blockRepeatCounters = remember { mutableStateMapOf<Int, Int>() }

    // --- Main timer coroutine ---
    LaunchedEffect(running, currentBlockIndex, currentIntervalIndex) {
        if (!running) return@LaunchedEffect
        if (plan.blocks.isEmpty()) return@LaunchedEffect

        val block = plan.blocks.getOrNull(currentBlockIndex) ?: return@LaunchedEffect
        val interval = block.intervals.getOrNull(currentIntervalIndex) ?: return@LaunchedEffect

        // Speak interval label
        tts.speak(interval.label, TextToSpeech.QUEUE_FLUSH, null, null)

        while (secondsRemaining > 0 && running) {
            delay(1000)
            secondsRemaining--
        }

        if (!running) return@LaunchedEffect

        // Move to next interval
        currentIntervalIndex++
        val nextInterval: Interval?
        if (currentIntervalIndex >= block.intervals.size) {
            // End of block, check repeats
            if (block.repeatIndefinitely) {
                currentIntervalIndex = 0
            } else {
                val repeatsDone = blockRepeatCounters[currentBlockIndex] ?: 0
                if (repeatsDone + 1 < (block.repeatCount ?: 1)) {
                    blockRepeatCounters[currentBlockIndex] = repeatsDone + 1
                    currentIntervalIndex = 0
                } else {
                    // Move to next block
                    currentBlockIndex++
                    currentIntervalIndex = 0
                    if (currentBlockIndex >= plan.blocks.size) {
                        running = false
                        onPlanFinished(plan) // <-- navigate to summary here
                        return@LaunchedEffect
                    }
                }
            }
            nextInterval = plan.blocks.getOrNull(currentBlockIndex)?.intervals?.getOrNull(currentIntervalIndex)
        } else {
            nextInterval = block.intervals.getOrNull(currentIntervalIndex)
        }

        secondsRemaining = nextInterval?.durationSeconds ?: 0
    }

    // --- Stop button hold ---
    LaunchedEffect(isHoldingStop) {
        if (isHoldingStop) {
            stopProgress = 0f
            val steps = 20
            repeat(steps) {
                stopProgress += 1f / steps
                delay(50)
            }
            running = false
            onPlanFinished(plan) // <-- navigate to summary if user cancels
            isHoldingStop = false
        } else stopProgress = 0f
    }

    val currentInterval = plan.blocks.getOrNull(currentBlockIndex)?.intervals?.getOrNull(currentIntervalIndex)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = currentInterval?.label ?: "No Interval",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "${secondsRemaining} sec",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row {
            Button(onClick = { running = !running }) {
                Text(if (running) "Pause" else "Start")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .height(56.dp)
                    .width(100.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { isHoldingStop = true }) }
                    .background(
                        color = if (isHoldingStop) Color.Red.copy(alpha = stopProgress) else Color.Gray,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Stop", color = Color.White)
            }
        }

        if (isHoldingStop) {
            LinearProgressIndicator(progress = stopProgress, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }

    // --- Clean up TTS ---
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
}
