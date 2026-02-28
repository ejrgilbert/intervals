package com.example.intervalrunner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PlanEditorScreen(
    onStartPlan: (List<IntervalBlock>) -> Unit
) {
    val blocks = remember { mutableStateListOf<IntervalBlock>() }
    val currentBlock = remember { mutableStateListOf<Interval>() }

    var intervalLabel by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("0") }
    var seconds by remember { mutableStateOf("0") }

    // --- Repeat configuration ---
    var repeatIndefinitely by remember { mutableStateOf(false) }
    var repeatCount by remember { mutableStateOf("1") } // default 1 for finite

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Interval Plan", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // --- Interval input row ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = intervalLabel,
                onValueChange = { intervalLabel = it },
                label = { Text("Label") },
                modifier = Modifier.weight(2f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = minutes,
                onValueChange = { if (it.all { c -> c.isDigit() }) minutes = it },
                label = { Text("Min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = seconds,
                onValueChange = { if (it.all { c -> c.isDigit() }) seconds = it },
                label = { Text("Sec") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {
                val totalSeconds = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
                if (intervalLabel.isNotBlank() && totalSeconds > 0) {
                    currentBlock.add(Interval(intervalLabel, totalSeconds))
                    intervalLabel = ""
                    minutes = "0"
                    seconds = "0"
                }
            }) {
                Text("Add Interval")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Current block preview ---
        if (currentBlock.isNotEmpty()) {
            Text("Current Block Intervals", style = MaterialTheme.typography.titleMedium)

            LazyColumn {
                itemsIndexed(currentBlock) { index, interval ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
//                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
//                            .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(
                            color = Color(0xFFFFF3E0), // light orange
                    shape = MaterialTheme.shapes.small
                    )
                    ) {
                        Text("${interval.label}: ${interval.durationSeconds} sec", modifier = Modifier.weight(1f))
                        IconButton(onClick = { currentBlock.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete interval")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Finish Block + Repeat config ---
            Row(verticalAlignment = Alignment.CenterVertically) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (!repeatIndefinitely) {
                        OutlinedTextField(
                            value = repeatCount,
                            onValueChange = { if (it.all { c -> c.isDigit() }) repeatCount = it },
                            label = { Text("Times") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Checkbox(checked = repeatIndefinitely, onCheckedChange = { repeatIndefinitely = it })
                    Text("∞")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(onClick = {
                    if (currentBlock.isNotEmpty()) {
                        val block = if (repeatIndefinitely) {
                            IntervalBlock(currentBlock.toList(), repeatIndefinitely = true)
                        } else {
                            IntervalBlock(currentBlock.toList(), repeatCount = repeatCount.toIntOrNull() ?: 1)
                        }
                        blocks.add(block)
                        currentBlock.clear()
                        repeatIndefinitely = false
                        repeatCount = "1"
                    }
                }) {
                    Text("Finish Block")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Saved Blocks Preview ---
        if (blocks.isNotEmpty()) {
            Text("Saved Blocks", style = MaterialTheme.typography.titleMedium)

            LazyColumn {
                itemsIndexed(blocks) { blockIndex, block ->
                    Column(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .background(
//                                color = Color(0xFFFFF3E0), // light orange
//                                shape = MaterialTheme.shapes.small
//                            )
//                            .padding(8.dp)
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
//                                .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small)

                                .padding(8.dp)) {
                            Text(
                                "Block ${blockIndex + 1} (${if (block.repeatIndefinitely) "∞" else "${block.repeatCount}x"})",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { blocks.removeAt(blockIndex) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Block")
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        block.intervals.forEachIndexed { intervalIndex, interval ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${interval.label}: ${interval.durationSeconds} sec", modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val mutableIntervals = block.intervals.toMutableList()
                                    mutableIntervals.removeAt(intervalIndex)
                                    if (mutableIntervals.isEmpty()) {
                                        blocks.removeAt(blockIndex)
                                    } else {
                                        blocks[blockIndex] = block.copy(intervals = mutableIntervals)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete interval")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

//        // --- Start Plan Button ---
//        Button(
//            onClick = { onStartPlan(blocks.toList()) },
//            enabled = blocks.any { it.intervals.isNotEmpty() },
//            modifier = Modifier.align(Alignment.CenterHorizontally)
//        ) {
//            Text("Start Plan")
//        }
        // --- place this at the bottom of your Column in PlanEditorScreen ---
        var isHoldingClear by remember { mutableStateOf(false) }
        var clearProgress by remember { mutableStateOf(0f) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Clear Plan Button (left, smaller) ---
            val canClear = blocks.isNotEmpty() || currentBlock.isNotEmpty()
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .width(120.dp)
                    .pointerInput(canClear) {
                        if (canClear) {
                            detectTapGestures(
                                onLongPress = { isHoldingClear = true }
                            )
                        }
                    }
                    .background(
                        color = if (isHoldingClear) Color.Red.copy(alpha = clearProgress)
                        else if (canClear) Color.Red.copy(alpha = 0.7f)
                        else Color.Gray.copy(alpha = 0.5f), // disabled look
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Clear Plan", color = Color.White)
            }

            // --- Start Plan Button (right) ---
            Button(
                onClick = { onStartPlan(blocks) },
                enabled = blocks.any { it.intervals.isNotEmpty() }
            ) {
                Text("Start Plan")
            }
        }


// --- LaunchedEffect to handle long-press clear ---
        LaunchedEffect(isHoldingClear) {
            if (isHoldingClear) {
                clearProgress = 0f
                val steps = 20
                repeat(steps) {
                    clearProgress += 1f / steps
                    delay(50)
                }
                // Actually clear the plan
                blocks.clear()
                currentBlock.clear()
                isHoldingClear = false
                clearProgress = 0f
            } else {
                clearProgress = 0f
            }
        }

// Optional visual progress bar for holding
        if (isHoldingClear) {
            LinearProgressIndicator(progress = clearProgress, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}
