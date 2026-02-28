package com.example.intervalrunner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentPlan by remember { mutableStateOf<RunPlan?>(null) }
            var lastFinishedPlan by remember { mutableStateOf<RunPlan?>(null) }
            var showingSummary by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF2F2F2))
                    .padding(16.dp)
            ) {
                when {
                    currentPlan != null -> {
                        RunScreen(
                            plan = currentPlan!!,
                            onFinish = { finishedPlan ->
                                // store plan to show summary
                                lastFinishedPlan = finishedPlan
                                currentPlan = null
                                showingSummary = true
                            }
                        )
                    }
                    showingSummary && lastFinishedPlan != null -> {
                        PlanSummaryScreen(
                            plan = lastFinishedPlan!!,
                            onBack = {
                                showingSummary = false
                                lastFinishedPlan = null
                            }
                        )
                    }
                    else -> {
                        PlanEditorScreen { blocks ->
                            currentPlan = RunPlan(name = "Custom Plan", blocks = blocks)
                        }
                    }
                }
            }
        }

    }
}
