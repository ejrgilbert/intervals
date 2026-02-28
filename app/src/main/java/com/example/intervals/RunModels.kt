package com.example.intervalrunner

// Represents a single interval with a label and duration
data class Interval(
    val label: String,
    val durationSeconds: Int
)

// Represents a block of intervals
// repeatIndefinitely = true → repeats forever
// repeatCount = optional finite number of repeats
data class IntervalBlock(
    val intervals: List<Interval>,
    val repeatIndefinitely: Boolean = false,
    val repeatCount: Int? = null
)

// Represents a full running plan composed of multiple blocks
data class RunPlan(
    val name: String,
    val blocks: List<IntervalBlock>
)
