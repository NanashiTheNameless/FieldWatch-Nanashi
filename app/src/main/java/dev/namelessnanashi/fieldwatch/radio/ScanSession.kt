package dev.namelessnanashi.fieldwatch.radio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScanRunState { STOPPED, STARTING, RUNNING, STOPPING }

/** Manual Stop survives automatic activity resumes; display pause is an independent UI state. */
class ScanSession {
    private val mutable = MutableStateFlow(ScanRunState.STOPPED)
    val state = mutable.asStateFlow()
    @Volatile var stoppedByUser: Boolean = false
        private set

    private var changedByUser = false
    @Synchronized fun load(stopped: Boolean) { if (!changedByUser) stoppedByUser = stopped }

    @Synchronized fun requestStart(explicit: Boolean): Boolean {
        if (mutable.value == ScanRunState.STOPPING || mutable.value == ScanRunState.STARTING) return false
        if (stoppedByUser && !explicit) return false
        if (explicit) {
            changedByUser = true
            stoppedByUser = false
        }
        if (mutable.value == ScanRunState.STOPPED) mutable.value = ScanRunState.STARTING
        return true
    }

    @Synchronized fun requestStop() {
        changedByUser = true
        stoppedByUser = true
        if (mutable.value != ScanRunState.STOPPED) mutable.value = ScanRunState.STOPPING
    }

    @Synchronized fun started(): Boolean {
        if (stoppedByUser || mutable.value == ScanRunState.STOPPING) return false
        mutable.value = ScanRunState.RUNNING
        return true
    }

    @Synchronized fun stopped() { mutable.value = ScanRunState.STOPPED }
}
