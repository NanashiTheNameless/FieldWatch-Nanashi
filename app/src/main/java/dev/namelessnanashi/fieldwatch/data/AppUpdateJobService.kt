package dev.namelessnanashi.fieldwatch.data

import android.app.job.JobParameters
import android.app.job.JobService
import dev.namelessnanashi.fieldwatch.FieldwatchApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs the periodic update check and the retry after the idle wait. */
class AppUpdateJobService : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as FieldwatchApp
        if (app.pendingCrash != null) return false
        work = app.scope.launch {
            try {
                if (app.awaitConfiguration()) {
                    withContext(Dispatchers.IO) { AppUpdateAuto.run(app, announce = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No answer from GitHub. The next period tries again.
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }
}
