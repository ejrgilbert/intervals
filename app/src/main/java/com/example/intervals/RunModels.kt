package com.example.intervalrunner

// Represents a single interval with a label and duration
data class Interval(
    val label: String,
    val durationSeconds: Int
)

// Represents a block of intervals
// Exactly one repeat mode is active:
//   repeatIndefinitely = true        → cycle forever
//   repeatDurationSeconds != null    → cycle until N seconds of block time elapse (cut short mid-interval)
//   repeatCount != null              → cycle a fixed number of times (default)
data class IntervalBlock(
    val intervals: List<Interval>,
    val repeatIndefinitely: Boolean = false,
    val repeatCount: Int? = null,
    val repeatDurationSeconds: Int? = null
)

// Represents a full running plan composed of multiple blocks
data class RunPlan(
    val name: String,
    val blocks: List<IntervalBlock>,
    val warnHalfway: Boolean = false,
)

// Total planned seconds for a run; null when an infinite block makes it
// unbounded (and thus "halfway" is undefined).
fun RunPlan.totalPlannedSecondsOrNull(): Int? {
    var total = 0
    for (b in blocks) {
        if (b.repeatIndefinitely) return null
        val intervalSum = b.intervals.sumOf { it.durationSeconds }
        total += b.repeatDurationSeconds ?: (intervalSum * (b.repeatCount ?: 1))
    }
    return total
}

// Records what actually happened on one block during a run.
// `completed` is true when the block ended on its own (passes exhausted or
// duration budget hit); false when the user skipped or stopped mid-block.
// `completedPasses` counts fully-finished repetitions through the block's intervals.
data class BlockExecution(
    val blockIndex: Int,
    val actualSeconds: Int,
    val completedPasses: Int,
    val completed: Boolean,
)
