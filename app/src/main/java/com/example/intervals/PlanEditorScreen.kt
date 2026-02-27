package com.example.intervalrunner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun PlanEditorScreen(
    onStartPlan: (List<IntervalBlock>) -> Unit
) {
    val blocks = remember { mutableStateListOf<IntervalBlock>() }
    val currentBlock = remember { mutableStateListOf<Interval>() }
    var currentRepeat by remember { mutableStateOf(false) }

    var intervalLabel by remember { mutableStateOf("") }
    var intervalSeconds by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Interval Plan", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // --- Interval label ---
            OutlinedTextField(
                value = intervalLabel,
                onValueChange = { intervalLabel = it },
                label = { Text("Label") },
                modifier = Modifier.weight(2f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // --- Minutes input ---
            var minutes by remember { mutableStateOf("0") }
            OutlinedTextField(
                value = minutes,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) minutes = input
                },
                label = { Text("Min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // --- Seconds input ---
            var seconds by remember { mutableStateOf("0") }
            OutlinedTextField(
                value = seconds,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) seconds = input
                },
                label = { Text("Sec") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // --- Add Interval button ---
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${interval.label}: ${interval.durationSeconds} sec", modifier = Modifier.weight(1f))
                        Button(onClick = { currentBlock.removeAt(index) }) { Text("Delete") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Finish Block row with infinity checkbox ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (currentBlock.isNotEmpty()) {
                        blocks.add(IntervalBlock(currentBlock.toList(), currentRepeat))
                        currentBlock.clear()
                        currentRepeat = false
                    }
                }) {
                    Text("Finish Block")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = currentRepeat, onCheckedChange = { currentRepeat = it })
                    Text("∞")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Saved blocks preview ---
        if (blocks.isNotEmpty()) {
            Text("Saved Blocks", style = MaterialTheme.typography.titleMedium)

            LazyColumn {
                itemsIndexed(blocks) { blockIndex, block ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Block ${blockIndex + 1} (${if (block.repeatIndefinitely) "∞" else "Once"})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { blocks.removeAt(blockIndex) }) { Text("Delete Block") }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        block.intervals.forEachIndexed { intervalIndex, interval ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("- ${interval.label}: ${interval.durationSeconds} sec", modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    // Remove the interval
                                    val mutableList = block.intervals.toMutableList()
                                    mutableList.removeAt(intervalIndex)

                                    if (mutableList.isEmpty()) {
                                        // Delete the block if no intervals remain
                                        blocks.removeAt(blockIndex)
                                    } else {
                                        // Update the block with remaining intervals
                                        blocks[blockIndex] = block.copy(intervals = mutableList)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete interval"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(onClick = { onStartPlan(blocks) },
            enabled = blocks.any { it.intervals.isNotEmpty() }
            ) {
            Text("Start Plan")
        }
    }
}
