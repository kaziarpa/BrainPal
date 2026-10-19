package com.brainpal.quitscrolling.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brainpal.quitscrolling.data.model.SocialPlatform
import com.brainpal.quitscrolling.data.repository.ScrollRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class DayBarItem(
    val date: LocalDate,
    val dayLetter: String,
    val count: Int,
    val isSelected: Boolean
)

data class PlatformStatItem(
    val platform: SocialPlatform,
    val count: Int,
    val shareFraction: Float
)

data class StatsUiState(
    val weekRangeLabel: String = "",
    val dayBars: List<DayBarItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDayCount: Int = 0,
    val weekTotalCount: Int = 0,
    val platformStats: List<PlatformStatItem> = emptyList(),
    val canGoNextWeek: Boolean = false
)

class StatsViewModel(
    private val scrollRepository: ScrollRepository
) : ViewModel() {

    private val weekOffsetFlow = MutableStateFlow(0L)
    private val selectedDateFlow = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> = weekOffsetFlow.flatMapLatest { weekOffset ->
        val today = LocalDate.now()
        val currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startMonday = currentWeekMonday.plusWeeks(weekOffset)
        val endSunday = startMonday.plusDays(6)

        combine(
            scrollRepository.observeCountsByDateRange(startMonday, endSunday),
            scrollRepository.observePlatformBreakdown(startMonday, endSunday),
            selectedDateFlow
        ) { daySummaries, platformBreakdowns, selectedDate ->
            val countMap = daySummaries.associate { it.dateString to it.totalCount }
            val activeSelection = if (selectedDate.isBefore(startMonday) || selectedDate.isAfter(endSunday)) {
                if (weekOffset == 0L) today else endSunday
            } else {
                selectedDate
            }

            val letters = listOf("M", "T", "W", "T", "F", "S", "S")
            val bars = (0..6).map { dayIdx ->
                val date = startMonday.plusDays(dayIdx.toLong())
                val count = countMap[date.toString()] ?: 0
                DayBarItem(
                    date = date,
                    dayLetter = letters[dayIdx],
                    count = count,
                    isSelected = date == activeSelection
                )
            }

            val weekTotal = bars.sumOf { it.count }
            val selectedDayCount = bars.firstOrNull { it.isSelected }?.count ?: 0

            val platformCountMap = platformBreakdowns.associate { it.platform to it.count }
            val maxPlatformCount = (platformCountMap.values.maxOrNull() ?: 1).coerceAtLeast(1)

            val platformList = SocialPlatform.entries.map { platform ->
                val cnt = platformCountMap[platform.name] ?: 0
                PlatformStatItem(
                    platform = platform,
                    count = cnt,
                    shareFraction = (cnt.toFloat() / maxPlatformCount.toFloat()).coerceIn(0f, 1f)
                )
            }.sortedByDescending { it.count }

            StatsUiState(
                weekRangeLabel = formatWeekRange(startMonday, endSunday),
                dayBars = bars,
                selectedDate = activeSelection,
                selectedDayCount = selectedDayCount,
                weekTotalCount = weekTotal,
                platformStats = platformList,
                canGoNextWeek = weekOffset < 0L
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    fun selectDay(date: LocalDate) {
        selectedDateFlow.value = date
    }

    fun previousWeek() {
        weekOffsetFlow.value -= 1L
    }

    fun nextWeek() {
        if (weekOffsetFlow.value < 0L) {
            weekOffsetFlow.value += 1L
        }
    }

    private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
        val monthDayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val dayOnlyFmt = DateTimeFormatter.ofPattern("d", Locale.US)
        return if (start.month == end.month) {
            "${start.format(monthDayFmt)} - ${end.format(dayOnlyFmt)}"
        } else {
            "${start.format(monthDayFmt)} - ${end.format(monthDayFmt)}"
        }
    }

    companion object {
        fun provideFactory(scrollRepository: ScrollRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StatsViewModel(scrollRepository) as T
                }
            }
    }
}
