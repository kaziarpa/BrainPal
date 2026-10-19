package com.brainpal.quitscrolling.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brainpal.quitscrolling.data.local.datastore.UserPreferencesRepository
import com.brainpal.quitscrolling.data.local.datastore.UserPreferencesState
import com.brainpal.quitscrolling.data.model.ContentType
import com.brainpal.quitscrolling.data.model.SocialPlatform
import com.brainpal.quitscrolling.data.repository.ScrollRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val todayActualCount: Int = 0,
    val previewCountOverride: Int? = null,
    val displayCount: Int = 0,
    val pillOffsetYDp: Int = 14,
    val selectedSimPlatform: SocialPlatform = SocialPlatform.INSTAGRAM,
    val dailyGoalLimit: Int = 50
)

class HomeViewModel(
    private val scrollRepository: ScrollRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val selectedPlatformFlow = kotlinx.coroutines.flow.MutableStateFlow(SocialPlatform.INSTAGRAM)

    val uiState: StateFlow<HomeUiState> = combine(
        scrollRepository.todayCountFlow,
        scrollRepository.previewCountOverride,
        userPreferencesRepository.preferencesFlow,
        selectedPlatformFlow
    ) { actualCount, previewOverride, prefs: UserPreferencesState, selectedPlatform ->
        HomeUiState(
            todayActualCount = actualCount,
            previewCountOverride = previewOverride,
            displayCount = previewOverride ?: actualCount,
            pillOffsetYDp = prefs.pillOffsetYDp,
            selectedSimPlatform = selectedPlatform,
            dailyGoalLimit = prefs.dailyGoalLimit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun selectSimPlatform(platform: SocialPlatform) {
        selectedPlatformFlow.value = platform
    }

    fun recordSimulatedReelScroll(platform: SocialPlatform = selectedPlatformFlow.value) {
        viewModelScope.launch {
            scrollRepository.setPreviewCountOverride(null)
            scrollRepository.recordScroll(
                platform = platform,
                contentType = ContentType.REEL_SHORT
            )
        }
    }

    fun setPreviewBrainRotCount(count: Int?) {
        scrollRepository.setPreviewCountOverride(count)
    }

    fun updatePillOffsetY(offsetDp: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPillOffsetYDp(offsetDp)
        }
    }

    fun resetTodayScrolls() {
        viewModelScope.launch {
            scrollRepository.resetTodayCount()
        }
    }

    companion object {
        fun provideFactory(
            scrollRepository: ScrollRepository,
            userPreferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(scrollRepository, userPreferencesRepository) as T
            }
        }
    }
}
