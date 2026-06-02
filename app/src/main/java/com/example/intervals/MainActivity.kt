package com.example.intervalrunner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        setContent {
            var currentPlan by remember { mutableStateOf<RunPlan?>(null) }
            var lastFinishedPlan by remember { mutableStateOf<RunPlan?>(null) }
            var lastExecutions by remember { mutableStateOf<List<BlockExecution>>(emptyList()) }
            var showingSummary by remember { mutableStateOf(false) }

            // Holds the editor's rememberSaveable state while RunScreen/Summary are
            // showing, so the user comes back to their plan instead of an empty one.
            val saveableStateHolder = rememberSaveableStateHolder()

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
                            onFinish = { finishedPlan, executions ->
                                lastFinishedPlan = finishedPlan
                                lastExecutions = executions
                                currentPlan = null
                                showingSummary = true
                            }
                        )
                    }
                    showingSummary && lastFinishedPlan != null -> {
                        PlanSummaryScreen(
                            plan = lastFinishedPlan!!,
                            executions = lastExecutions,
                            onBack = {
                                showingSummary = false
                                lastFinishedPlan = null
                                lastExecutions = emptyList()
                            }
                        )
                    }
                    else -> {
                        saveableStateHolder.SaveableStateProvider("plan-editor") {
                            PlanEditorScreen { blocks, warnHalfway ->
                                currentPlan = RunPlan(
                                    name = "Custom Plan",
                                    blocks = blocks,
                                    warnHalfway = warnHalfway,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
