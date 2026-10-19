package com.brainpal.quitscrolling.ui.screens.battle

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brainpal.quitscrolling.data.local.datastore.UserPreferencesRepository
import com.brainpal.quitscrolling.data.repository.ScrollRepository
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class BattleUiState(
    val userNickname: String = "YOU",
    val userTodayCount: Int = 0,
    val rivalName: String = "FRIEND",
    val rivalCount: Int = 64,
    val isUserWinning: Boolean = true,
    val statusBannerMessage: String? = null
)

class BattleViewModel(
    private val scrollRepository: ScrollRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val statusMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BattleUiState> = combine(
        scrollRepository.todayCountFlow,
        scrollRepository.previewCountOverride,
        userPreferencesRepository.preferencesFlow,
        statusMessageFlow
    ) { todayCount, previewOverride, prefs, statusMsg ->
        val activeUserCount = previewOverride ?: todayCount
        BattleUiState(
            userNickname = prefs.userNickname,
            userTodayCount = activeUserCount,
            rivalName = prefs.rivalName,
            rivalCount = prefs.rivalDailyCount,
            isUserWinning = activeUserCount <= prefs.rivalDailyCount,
            statusBannerMessage = statusMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BattleUiState()
    )

    fun generateShareBattleCode(): String {
        val state = uiState.value
        val json = JSONObject().apply {
            put("name", state.userNickname)
            put("reels", state.userTodayCount)
            put("app", "Likhon")
        }.toString()
        val encoded = Base64.encodeToString(
            json.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return "LIKHON-$encoded"
    }

    fun generateShareMessage(): String {
        val code = generateShareBattleCode()
        val state = uiState.value
        return "⚔️ I've scrolled ${state.userTodayCount} reels today on Likhon! Can you keep your Brain Rot lower than mine?\n\nImport my Rival Battle Code in Likhon:\n$code"
    }

    fun importFriendBattleCode(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return false

        return try {
            val base64Part = when {
                trimmed.contains("LIKHON-") -> {
                    trimmed.substringAfter("LIKHON-").trim().substringBefore(" ").substringBefore("\n")
                }
                trimmed.contains("BRAINPAL-") -> {
                    trimmed.substringAfter("BRAINPAL-").trim().substringBefore(" ").substringBefore("\n")
                }
                else -> trimmed
            }
            val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
            val jsonStr = String(decodedBytes, StandardCharsets.UTF_8)
            val json = JSONObject(jsonStr)
            val friendName = json.optString("name", "FRIEND").uppercase()
            val friendReels = json.optInt("reels", 50).coerceAtLeast(0)

            viewModelScope.launch {
                userPreferencesRepository.updateRivalProfile(
                    name = friendName,
                    count = friendReels,
                    pacemakerEnabled = false
                )
                statusMessageFlow.value = "Synced rival $friendName ($friendReels reels today)!"
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setCustomRival(name: String, count: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateRivalProfile(
                name = name,
                count = count,
                pacemakerEnabled = true
            )
            statusMessageFlow.value = "Rival updated: ${name.uppercase()} ($count reels)"
        }
    }

    fun updateUserNickname(nickname: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserNickname(nickname)
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    companion object {
        fun provideFactory(
            scrollRepository: ScrollRepository,
            userPreferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BattleViewModel(scrollRepository, userPreferencesRepository) as T
            }
        }
    }
}
