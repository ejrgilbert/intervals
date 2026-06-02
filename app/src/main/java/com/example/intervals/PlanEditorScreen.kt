package com.example.intervalrunner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private fun Interval.toJson(): JSONObject = JSONObject().apply {
    put("label", label)
    put("duration", durationSeconds)
}

private fun JSONObject.toInterval(): Interval =
    Interval(getString("label"), getInt("duration"))

private fun IntervalBlock.toJson(): JSONObject = JSONObject().apply {
    val arr = JSONArray()
    intervals.forEach { arr.put(it.toJson()) }
    put("intervals", arr)
    put("repeatIndefinitely", repeatIndefinitely)
    if (repeatCount != null) put("repeatCount", repeatCount)
}

private fun JSONObject.toIntervalBlock(): IntervalBlock {
    val arr = getJSONArray("intervals")
    val intervals = List(arr.length()) { arr.getJSONObject(it).toInterval() }
    val indefinitely = optBoolean("repeatIndefinitely", false)
    val count = if (has("repeatCount")) getInt("repeatCount") else null
    return IntervalBlock(intervals, indefinitely, count)
}

private val IntervalBlockListSaver: Saver<SnapshotStateList<IntervalBlock>, String> = Saver(
    save = { list ->
        JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    },
    restore = { json ->
        val arr = JSONArray(json)
        List(arr.length()) { arr.getJSONObject(it).toIntervalBlock() }.toMutableStateList()
    }
)

private val IntervalListSaver: Saver<SnapshotStateList<Interval>, String> = Saver(
    save = { list ->
        JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    },
    restore = { json ->
        val arr = JSONArray(json)
        List(arr.length()) { arr.getJSONObject(it).toInterval() }.toMutableStateList()
    }
)

@Composable
fun PlanEditorScreen(
    onStartPlan: (List<IntervalBlock>) -> Unit
) {
    val blocks = rememberSaveable(saver = IntervalBlockListSaver) { mutableStateListOf<IntervalBlock>() }
    val currentBlock = rememberSaveable(saver = IntervalListSaver) { mutableStateListOf<Interval>() }

    var intervalLabel by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("0") }
    var seconds by rememberSaveable { mutableStateOf("0") }

    // --- Repeat configuration ---
    var repeatIndefinitely by rememberSaveable { mutableStateOf(false) }
    var repeatCount by rememberSaveable { mutableStateOf("1") } // default 1 for finite

    // null when adding a new block; index into `blocks` when editing an existing one.
    var editingBlockIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    // null when adding a new interval; index into `currentBlock` when editing one.
    var editingIntervalIndex by rememberSaveable { mutableStateOf<Int?>(null) }

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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = {
                    val totalSeconds = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
                    if (intervalLabel.isNotBlank() && totalSeconds > 0) {
                        val newInterval = Interval(intervalLabel, totalSeconds)
                        val editIdx = editingIntervalIndex
                        if (editIdx != null && editIdx in currentBlock.indices) {
                            currentBlock[editIdx] = newInterval
                            editingIntervalIndex = null
                        } else {
                            currentBlock.add(newInterval)
                        }
                        intervalLabel = ""
                        minutes = "0"
                        seconds = "0"
                    }
                }) {
                    Text(if (editingIntervalIndex != null) "Save" else "Add Interval")
                }

                if (editingIntervalIndex != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(onClick = {
                        editingIntervalIndex = null
                        intervalLabel = ""
                        minutes = "0"
                        seconds = "0"
                    }) {
                        Text("Cancel")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Current block preview ---
        if (currentBlock.isNotEmpty()) {
            Text(
                editingBlockIndex?.let { "Editing Block ${it + 1}" } ?: "Current Block Intervals",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn {
                itemsIndexed(currentBlock) { index, interval ->
                    val editingThisInterval = editingIntervalIndex == index
                    val editingOtherInterval = editingIntervalIndex != null && !editingThisInterval
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
                        IconButton(
                            onClick = {
                                intervalLabel = interval.label
                                minutes = (interval.durationSeconds / 60).toString()
                                seconds = (interval.durationSeconds % 60).toString()
                                editingIntervalIndex = index
                            },
                            enabled = !editingOtherInterval
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit interval")
                        }
                        IconButton(
                            onClick = { currentBlock.removeAt(index) },
                            enabled = !editingThisInterval && !editingOtherInterval
                        ) {
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

                Button(
                    onClick = {
                        if (currentBlock.isNotEmpty()) {
                            val block = if (repeatIndefinitely) {
                                IntervalBlock(currentBlock.toList(), repeatIndefinitely = true)
                            } else {
                                IntervalBlock(currentBlock.toList(), repeatCount = repeatCount.toIntOrNull() ?: 1)
                            }
                            val editIdx = editingBlockIndex
                            if (editIdx != null) {
                                blocks[editIdx] = block
                            } else {
                                blocks.add(block)
                            }
                            currentBlock.clear()
                            repeatIndefinitely = false
                            repeatCount = "1"
                            editingBlockIndex = null
                            editingIntervalIndex = null
                        }
                    },
                    enabled = editingIntervalIndex == null
                ) {
                    Text(if (editingBlockIndex != null) "Save" else "Finish Block")
                }

                if (editingBlockIndex != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        currentBlock.clear()
                        repeatIndefinitely = false
                        repeatCount = "1"
                        editingBlockIndex = null
                        editingIntervalIndex = null
                        intervalLabel = ""
                        minutes = "0"
                        seconds = "0"
                    }) {
                        Text("Cancel")
                    }
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
                        val isEditingThis = editingBlockIndex == blockIndex
                        val isEditingOther = editingBlockIndex != null && !isEditingThis
                        val canStartEditing = currentBlock.isEmpty() && editingBlockIndex == null

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
                            IconButton(
                                onClick = {
                                    currentBlock.clear()
                                    currentBlock.addAll(block.intervals)
                                    repeatIndefinitely = block.repeatIndefinitely
                                    repeatCount = (block.repeatCount ?: 1).toString()
                                    editingBlockIndex = blockIndex
                                },
                                enabled = canStartEditing
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Block")
                            }
                            IconButton(
                                onClick = { blocks.removeAt(blockIndex) },
                                enabled = !isEditingOther && !isEditingThis
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Block")
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isEditingThis) {
                            Text(
                                "(editing above)",
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        } else {
                            block.intervals.forEachIndexed { intervalIndex, interval ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${interval.label}: ${interval.durationSeconds} sec", modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            val mutableIntervals = block.intervals.toMutableList()
                                            mutableIntervals.removeAt(intervalIndex)
                                            if (mutableIntervals.isEmpty()) {
                                                blocks.removeAt(blockIndex)
                                            } else {
                                                blocks[blockIndex] = block.copy(intervals = mutableIntervals)
                                            }
                                        },
                                        enabled = !isEditingOther
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete interval")
                                    }
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
                intervalLabel = ""
                minutes = "0"
                seconds = "0"
                repeatIndefinitely = false
                repeatCount = "1"
                editingBlockIndex = null
                editingIntervalIndex = null
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
