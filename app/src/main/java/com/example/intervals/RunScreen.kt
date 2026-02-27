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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RunScreen(plan: RunPlan, onFinish: (() -> Unit)? = null) {
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
    var secondsRemaining by remember { mutableStateOf(allBlocks[0].intervals[0].durationSeconds) }
    var running by remember { mutableStateOf(false) }

    var isHoldingStop by remember { mutableStateOf(false) }
    var stopProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(running, currentBlockIndex, currentIntervalIndex) {
        if (running) {
            val block = allBlocks[currentBlockIndex]
            val interval = block.intervals[currentIntervalIndex]

            // Speak the interval label
            tts.speak(interval.label, TextToSpeech.QUEUE_FLUSH, null, null)

            while (secondsRemaining > 0) {
                delay(1000)
                secondsRemaining--
            }

            currentIntervalIndex++
            if (currentIntervalIndex >= block.intervals.size) {
                // move to next block
                currentBlockIndex++
                currentIntervalIndex = 0
                if (currentBlockIndex >= allBlocks.size) {
                    // check if last block should repeat
                    if (block.repeatIndefinitely) {
                        currentBlockIndex = allBlocks.size - 1
                    } else {
                        running = false
                        onFinish?.invoke()
                        return@LaunchedEffect
                    }
                }
            }
            val nextInterval = allBlocks[currentBlockIndex].intervals[currentIntervalIndex]
            secondsRemaining = nextInterval.durationSeconds
        }
    }

    LaunchedEffect(isHoldingStop) {
        if (isHoldingStop) {
            stopProgress = 0f
            val steps = 20
            repeat(steps) {
                stopProgress += 1f / steps
                delay(50)
            }
            onFinish?.invoke()
            isHoldingStop = false
        } else stopProgress = 0f
    }

    val currentInterval = allBlocks[currentBlockIndex].intervals[currentIntervalIndex]

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(currentInterval.label, style = MaterialTheme.typography.headlineMedium)
        Text("$secondsRemaining sec", style = MaterialTheme.typography.displayLarge)

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

    // Clean up TTS when this composable leaves
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
}

