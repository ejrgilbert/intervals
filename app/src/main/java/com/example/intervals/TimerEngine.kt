package com.example.intervalrunner

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimerSnapshot(
    val plan: RunPlan? = null,
    val blockIndex: Int = 0,
    val intervalIndex: Int = 0,
    val blockPass: Int = 0,
    val blockElapsedSeconds: Int = 0,
    val secondsRemaining: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    // SystemClock.elapsedRealtime() when the current interval will hit zero.
    // null while paused or before the first tick — notification drops chronometer in that case.
    val endRealtimeMs: Long? = null,
) {
    val currentBlock: IntervalBlock?
        get() = plan?.blocks?.getOrNull(blockIndex)
    val currentInterval: Interval?
        get() = currentBlock?.intervals?.getOrNull(intervalIndex)
}

object TimerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TimerSnapshot())
    val state: StateFlow<TimerSnapshot> = _state.asStateFlow()

    private var tickJob: Job? = null

    private var beep: (() -> Unit)? = null
    private var speak: ((String) -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    fun setAudio(beep: (() -> Unit)?, speak: ((String) -> Unit)?) {
        this.beep = beep
        this.speak = speak
    }

    // Load a plan and reset to the first interval, paused. The user (via
    // notification or RunScreen) starts the countdown with pauseResume().
    fun prepare(plan: RunPlan) {
        tickJob?.cancel()
        val firstDur = plan.blocks.firstOrNull()?.intervals?.firstOrNull()?.durationSeconds ?: 0
        _state.value = TimerSnapshot(
            plan = plan,
            secondsRemaining = firstDur,
            running = false,
            endRealtimeMs = null,
        )
    }

    fun pauseResume() {
        val s = _state.value
        if (s.plan == null || s.finished || s.currentInterval == null) return
        if (s.running) {
            tickJob?.cancel()
            _state.value = s.copy(running = false, endRealtimeMs = null)
        } else {
            _state.value = s.copy(running = true)
            runLoop()
        }
    }

    fun skipBlock() {
        val s = _state.value
        val plan = s.plan ?: return
        if (s.finished) return
        tickJob?.cancel()
        val nextBlockIndex = s.blockIndex + 1
        if (nextBlockIndex >= plan.blocks.size) {
            finish()
            return
        }
        val firstDur = plan.blocks[nextBlockIndex].intervals.firstOrNull()?.durationSeconds ?: 0
        _state.value = s.copy(
            blockIndex = nextBlockIndex,
            intervalIndex = 0,
            blockPass = 0,
            blockElapsedSeconds = 0,
            secondsRemaining = firstDur,
            endRealtimeMs = null,
        )
        if (s.running) runLoop()
    }

    fun stop() {
        tickJob?.cancel()
        finish()
    }

    private fun finish() {
        // Preserve plan so RunScreen can tell this finished state belongs to the
        // plan it was watching, vs. a leftover from a previous run.
        _state.value = _state.value.copy(
            finished = true,
            running = false,
            endRealtimeMs = null,
        )
        onFinished?.invoke()
    }

    private fun runLoop() {
        tickJob = scope.launch {
            while (true) {
                val snap = _state.value
                if (!snap.running || snap.finished) return@launch
                val plan = snap.plan ?: return@launch
                val block = snap.currentBlock ?: return@launch
                val interval = snap.currentInterval ?: return@launch

                // Beep then announce the interval label (matches pre-refactor UX).
                beep?.invoke()
                delay(300)
                speak?.invoke(interval.label)

                val segmentStart = SystemClock.elapsedRealtime()
                val baseBlockElapsed = snap.blockElapsedSeconds
                val endAt = segmentStart + snap.secondsRemaining * 1000L
                _state.value = _state.value.copy(endRealtimeMs = endAt)

                var durationCutoff = false
                while (true) {
                    val cur = _state.value
                    if (!cur.running || cur.finished) return@launch

                    val now = SystemClock.elapsedRealtime()
                    val msLeft = endAt - now
                    val segmentElapsedSec = ((now - segmentStart) / 1000).toInt()
                    val curBlockElapsed = baseBlockElapsed + segmentElapsedSec

                    val budget = block.repeatDurationSeconds
                    if (budget != null && curBlockElapsed >= budget) {
                        _state.value = cur.copy(
                            blockElapsedSeconds = budget,
                            secondsRemaining = 0,
                        )
                        durationCutoff = true
                        break
                    }
                    if (msLeft <= 0) {
                        _state.value = cur.copy(
                            blockElapsedSeconds = curBlockElapsed,
                            secondsRemaining = 0,
                        )
                        break
                    }
                    _state.value = cur.copy(
                        blockElapsedSeconds = curBlockElapsed,
                        // Round up so we display "1s" until we actually hit zero.
                        secondsRemaining = ((msLeft + 999) / 1000).toInt(),
                    )
                    delay(msLeft.coerceAtMost(250L))
                }

                advanceAfterInterval(plan, block, durationCutoff)
            }
        }
    }

    private fun advanceAfterInterval(
        plan: RunPlan,
        block: IntervalBlock,
        durationCutoff: Boolean,
    ) {
        val s = _state.value

        val nextBlockIndex: Int
        val nextIntervalIndex: Int
        val nextPass: Int
        val resetBlockElapsed: Boolean

        if (durationCutoff) {
            nextBlockIndex = s.blockIndex + 1
            nextIntervalIndex = 0
            nextPass = 0
            resetBlockElapsed = true
        } else {
            val tentative = s.intervalIndex + 1
            if (tentative < block.intervals.size) {
                nextBlockIndex = s.blockIndex
                nextIntervalIndex = tentative
                nextPass = s.blockPass
                resetBlockElapsed = false
            } else {
                val targetPasses = block.repeatCount ?: 1
                val completedPasses = s.blockPass + 1
                if (block.repeatIndefinitely || completedPasses < targetPasses) {
                    nextBlockIndex = s.blockIndex
                    nextIntervalIndex = 0
                    nextPass = completedPasses
                    resetBlockElapsed = false
                } else {
                    nextBlockIndex = s.blockIndex + 1
                    nextIntervalIndex = 0
                    nextPass = 0
                    resetBlockElapsed = true
                }
            }
        }

        if (nextBlockIndex >= plan.blocks.size) {
            finish()
            return
        }
        val nextDur = plan.blocks[nextBlockIndex].intervals.firstOrNull()?.durationSeconds ?: 0
        _state.value = s.copy(
            blockIndex = nextBlockIndex,
            intervalIndex = nextIntervalIndex,
            blockPass = nextPass,
            blockElapsedSeconds = if (resetBlockElapsed) 0 else s.blockElapsedSeconds,
            secondsRemaining = nextDur,
            endRealtimeMs = null,
        )
    }
}
