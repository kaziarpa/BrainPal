package com.brainpal.quitscrolling.di

import android.content.Context
import com.brainpal.quitscrolling.data.local.BrainPalDatabase
import com.brainpal.quitscrolling.data.local.datastore.UserPreferencesRepository
import com.brainpal.quitscrolling.data.model.SocialPlatform
import com.brainpal.quitscrolling.data.repository.ScrollRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: BrainPalDatabase by lazy {
        BrainPalDatabase.getInstance(appContext)
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    val scrollRepository: ScrollRepository by lazy {
        ScrollRepository(appContext, database.scrollEventDao())
    }

    init {
        // Seed initial multi-day scroll analytics on very first install if database is completely empty
        applicationScope.launch {
            val totalEvents = database.scrollEventDao().getTotalEventsCount()
            if (totalEvents == 0) {
                val today = LocalDate.now()
                // Historical days for the current week so the weekly bar chart & top apps breakdown are populated
                scrollRepository.recordBatchScrolls(SocialPlatform.INSTAGRAM, 22, today.minusDays(4))
                scrollRepository.recordBatchScrolls(SocialPlatform.YOUTUBE, 14, today.minusDays(4))
                scrollRepository.recordBatchScrolls(SocialPlatform.FACEBOOK, 18, today.minusDays(3))
                scrollRepository.recordBatchScrolls(SocialPlatform.X_TWITTER, 9, today.minusDays(3))
                scrollRepository.recordBatchScrolls(SocialPlatform.INSTAGRAM, 41, today.minusDays(2))
                scrollRepository.recordBatchScrolls(SocialPlatform.YOUTUBE, 25, today.minusDays(2))
                scrollRepository.recordBatchScrolls(SocialPlatform.INSTAGRAM, 34, today.minusDays(1))
                scrollRepository.recordBatchScrolls(SocialPlatform.FACEBOOK, 19, today.minusDays(1))
                scrollRepository.recordBatchScrolls(SocialPlatform.X_TWITTER, 12, today.minusDays(1))
                // Today's starting baseline (45 reels - right on the cusp of Stage 1 -> Stage 2)
                scrollRepository.recordBatchScrolls(SocialPlatform.INSTAGRAM, 21, today)
                scrollRepository.recordBatchScrolls(SocialPlatform.YOUTUBE, 12, today)
                scrollRepository.recordBatchScrolls(SocialPlatform.FACEBOOK, 7, today)
                scrollRepository.recordBatchScrolls(SocialPlatform.X_TWITTER, 5, today)
            }
        }
    }
}
