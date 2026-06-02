package com.example.intervalrunner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlanSummaryScreen(
    plan: RunPlan,
    onBack: () -> Unit // to go back to main menu or editor
) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Text("Plan Summary", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(plan.blocks) { blockIndex, block ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    val repeatLabel = when {
                        block.repeatIndefinitely -> "∞"
                        block.repeatDurationSeconds != null -> {
                            val m = block.repeatDurationSeconds / 60
                            val s = block.repeatDurationSeconds % 60
                            if (s == 0) "${m}m" else "${m}m ${s}s"
                        }
                        else -> "${block.repeatCount}x"
                    }
                    Text(
                        "Block ${blockIndex + 1} ($repeatLabel)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    block.intervals.forEach { interval ->
                        val minutes = interval.durationSeconds / 60
                        val seconds = interval.durationSeconds % 60
                        Text("${interval.label}: ${minutes}m ${seconds}s")
                    }
                }
                Divider()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onBack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}
