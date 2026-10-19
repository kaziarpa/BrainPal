package com.brainpal.quitscrolling.data.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.brainpal.quitscrolling.data.local.dao.ScrollEventDao
import com.brainpal.quitscrolling.data.local.entity.ScrollEventEntity
import com.brainpal.quitscrolling.data.model.AppCountBreakdown
import com.brainpal.quitscrolling.data.model.ContentType
import com.brainpal.quitscrolling.data.model.DayCountSummary
import com.brainpal.quitscrolling.data.model.SocialPlatform
import com.brainpal.quitscrolling.widget.BrainRotWidget
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class ScrollRepository(
    private val context: Context,
    private val scrollEventDao: ScrollEventDao
) {
    private val currentDateFlow = MutableStateFlow(LocalDate.now().toString())

    // Optional live preview override when user drags the Brain Rot Preview slider on HomeScreen
    private val _previewCountOverride = MutableStateFlow<Int?>(null)
    val previewCountOverride: StateFlow<Int?> = _previewCountOverride.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayCountFlow: Flow<Int> = currentDateFlow.flatMapLatest { dateStr ->
        scrollEventDao.observeDailyCount(dateStr)
    }

    fun refreshCurrentDate() {
        val today = LocalDate.now().toString()
        if (currentDateFlow.value != today) {
            currentDateFlow.value = today
        }
    }

    fun setPreviewCountOverride(overrideCount: Int?) {
        _previewCountOverride.value = overrideCount
    }

    suspend fun getTodayCountOnce(): Int {
        refreshCurrentDate()
        return scrollEventDao.getDailyCountOnce(currentDateFlow.value)
    }

    suspend fun recordScroll(
        platform: SocialPlatform,
        contentType: ContentType = ContentType.REEL_SHORT,
        packageName: String = platform.packageNames.first()
    ) {
        refreshCurrentDate()
        val now = System.currentTimeMillis()
        val today = currentDateFlow.value
        val entity = ScrollEventEntity(
            packageName = packageName,
            platform = platform.name,
            contentType = contentType.name,
            timestamp = now,
            dateString = today
        )
        scrollEventDao.insertEvent(entity)
        runCatching {
            BrainRotWidget().updateAll(context)
        }
    }

    suspend fun recordBatchScrolls(
        platform: SocialPlatform,
        count: Int,
        date: LocalDate = LocalDate.now()
    ) {
        if (count <= 0) return
        val now = System.currentTimeMillis()
        val dateString = date.toString()
        val events = List(count) { idx ->
            ScrollEventEntity(
                packageName = platform.packageNames.first(),
                platform = platform.name,
                contentType = ContentType.REEL_SHORT.name,
                timestamp = now - (idx * 1500L),
                dateString = dateString
            )
        }
        scrollEventDao.insertEvents(events)
        runCatching {
            BrainRotWidget().updateAll(context)
        }
    }

    suspend fun resetTodayCount() {
        refreshCurrentDate()
        scrollEventDao.clearDayEvents(currentDateFlow.value)
        _previewCountOverride.value = null
        runCatching {
            BrainRotWidget().updateAll(context)
        }
    }

    fun observeCountsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DayCountSummary>> {
        return scrollEventDao.observeCountsByDateRange(startDate.toString(), endDate.toString())
    }

    fun observePlatformBreakdown(startDate: LocalDate, endDate: LocalDate): Flow<List<AppCountBreakdown>> {
        return scrollEventDao.observePlatformBreakdown(startDate.toString(), endDate.toString())
    }
}
