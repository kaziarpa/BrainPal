package com.brainpal.quitscrolling.core.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brainpal.quitscrolling.BrainPalApplication
import com.brainpal.quitscrolling.widget.BrainRotWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MidnightResetWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now().toString()
            val app = applicationContext as? BrainPalApplication
            app?.container?.userPreferencesRepository?.setLastResetDate(today)
            app?.container?.scrollRepository?.refreshCurrentDate()
            BrainRotWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "brainpal_midnight_reset_worker"

        fun schedule(context: Context) {
            runCatching {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
                val initialDelayMinutes = Duration.between(now, nextMidnight).toMinutes().coerceAtLeast(1L)

                val request = PeriodicWorkRequestBuilder<MidnightResetWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }
    }
}
