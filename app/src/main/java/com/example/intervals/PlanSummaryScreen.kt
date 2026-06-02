package com.example.intervalrunner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

@Composable
fun PlanSummaryScreen(
    plan: RunPlan,
    executions: List<BlockExecution>,
    onBack: () -> Unit // to go back to main menu or editor
) {
    val executionByBlock = executions.associateBy { it.blockIndex }
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
                    val exec = executionByBlock[blockIndex]
                    val actualText = when {
                        exec == null -> "Not done"
                        // Infinite blocks have no planned amount, so the actual is
                        // always informative.
                        block.repeatIndefinitely -> "Did ${exec.completedPasses}x"
                        // Otherwise only annotate when the user fell short of the plan.
                        exec.completed -> null
                        block.repeatDurationSeconds != null ->
                            "Did ${formatDuration(exec.actualSeconds)}"
                        else -> "Did ${exec.completedPasses}x"
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Block ${blockIndex + 1} ($repeatLabel)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (actualText != null) {
                            Text(
                                actualText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "${m}m ${s}s"
}
