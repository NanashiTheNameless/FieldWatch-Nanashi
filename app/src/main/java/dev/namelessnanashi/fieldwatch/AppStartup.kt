package dev.namelessnanashi.fieldwatch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class StartupState { LOADING, READY, FAILED }

/** One shared load for activities, boot receivers, and restarted services. */
internal class AppStartup(
    private val scope: CoroutineScope,
    private val prepare: suspend () -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private val mutable = MutableStateFlow(StartupState.LOADING)
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true || mutable.value == StartupState.READY) return
        mutable.value = StartupState.LOADING
        job = scope.launch {
            try {
                prepare()
                mutable.value = StartupState.READY
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = StartupState.FAILED
                onFailure(e)
            }
        }
    }

    suspend fun awaitReady(): Boolean =
        state.first { it != StartupState.LOADING } == StartupState.READY
}
