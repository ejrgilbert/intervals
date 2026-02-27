package com.example.intervalrunner

data class Interval(
    val label: String,
    val durationSeconds: Int
)

data class IntervalBlock(
    val intervals: List<Interval>,
    val repeatIndefinitely: Boolean = false
)

data class RunPlan(
    val name: String,
    val blocks: List<IntervalBlock>
)
