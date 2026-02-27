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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF2F2F2))
                    .padding(16.dp)
            ) {
                if (currentPlan == null) {
                    PlanEditorScreen { blocks ->
                        currentPlan = RunPlan(name = "Custom Plan", blocks = blocks)
                    }
                } else {
                    RunScreen(plan = currentPlan!!, onFinish = { currentPlan = null })
                }
            }
        }
    }
}
