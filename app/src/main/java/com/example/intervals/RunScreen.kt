package com.example.intervalrunner

import android.os.SystemClock
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
    var secondsRemaining by remember {
        mutableStateOf(allBlocks.firstOrNull()?.intervals?.firstOrNull()?.durationSeconds ?: 0)
    }
    var running by remember { mutableStateOf(false) }

    var isHoldingStop by remember { mutableStateOf(false) }
    var stopProgress by remember { mutableStateOf(0f) }

    // --- Countdown timer ---
    LaunchedEffect(running, currentBlockIndex, currentIntervalIndex) {
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
                currentBlockIndex++
                if (currentBlockIndex >= allBlocks.size) {
                    // Check if last block repeats
                    if (block.repeatIndefinitely) {
                        currentBlockIndex = allBlocks.size - 1
                    } else {
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
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { isHoldingStop = true })
                        }
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
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = stopProgress, modifier = Modifier.fillMaxWidth().height(4.dp))
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
