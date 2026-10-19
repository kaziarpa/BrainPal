package com.brainpal.quitscrolling.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brainpal.quitscrolling.data.model.SocialPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brainpal_user_prefs")

data class UserPreferencesState(
    val pillOffsetYDp: Int = 14,
    val onlyShowPillInReels: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val blockerEnabled: Boolean = true,
    val instantBlockMode: Boolean = false,
    val autoBackOnBlock: Boolean = true,
    val dailyGoalLimit: Int = 50,
    val blockedPlatforms: Set<String> = SocialPlatform.entries.map { it.name }.toSet(),
    val snoozeUntilTimestamp: Long = 0L,
    val blockedCountToday: Int = 0,
    val userNickname: String = "YOU",
    val rivalName: String = "ALEX",
    val rivalDailyCount: Int = 64,
    val rivalPacemakerEnabled: Boolean = true,
    val lastResetDate: String = ""
) {
    fun isPlatformBlocked(platform: SocialPlatform): Boolean {
        return platform.name in blockedPlatforms
    }

    fun isSnoozeActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return snoozeUntilTimestamp > nowMillis
    }
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val PILL_OFFSET_Y_DP = intPreferencesKey("pill_offset_y_dp")
        val ONLY_SHOW_PILL_IN_REELS = booleanPreferencesKey("only_show_pill_in_reels")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val BLOCKER_ENABLED = booleanPreferencesKey("blocker_enabled")
        val INSTANT_BLOCK_MODE = booleanPreferencesKey("instant_block_mode")
        val AUTO_BACK_ON_BLOCK = booleanPreferencesKey("auto_back_on_block")
        val DAILY_GOAL_LIMIT = intPreferencesKey("daily_goal_limit")
        val BLOCKED_PLATFORMS = stringSetPreferencesKey("blocked_platforms")
        val SNOOZE_UNTIL_TIMESTAMP = longPreferencesKey("snooze_until_timestamp")
        val BLOCKED_COUNT_TODAY = intPreferencesKey("blocked_count_today")
        val USER_NICKNAME = stringPreferencesKey("user_nickname")
        val RIVAL_NAME = stringPreferencesKey("rival_name")
        val RIVAL_DAILY_COUNT = intPreferencesKey("rival_daily_count")
        val RIVAL_PACEMAKER_ENABLED = booleanPreferencesKey("rival_pacemaker_enabled")
        val LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
    }

    private val defaultBlockedPlatforms = SocialPlatform.entries.map { it.name }.toSet()

    val preferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
        UserPreferencesState(
            pillOffsetYDp = prefs[Keys.PILL_OFFSET_Y_DP] ?: 14,
            onlyShowPillInReels = prefs[Keys.ONLY_SHOW_PILL_IN_REELS] ?: true,
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK_ENABLED] ?: true,
            blockerEnabled = prefs[Keys.BLOCKER_ENABLED] ?: true,
            instantBlockMode = prefs[Keys.INSTANT_BLOCK_MODE] ?: false,
            autoBackOnBlock = prefs[Keys.AUTO_BACK_ON_BLOCK] ?: true,
            dailyGoalLimit = prefs[Keys.DAILY_GOAL_LIMIT] ?: 50,
            blockedPlatforms = prefs[Keys.BLOCKED_PLATFORMS] ?: defaultBlockedPlatforms,
            snoozeUntilTimestamp = prefs[Keys.SNOOZE_UNTIL_TIMESTAMP] ?: 0L,
            blockedCountToday = prefs[Keys.BLOCKED_COUNT_TODAY] ?: 0,
            userNickname = prefs[Keys.USER_NICKNAME] ?: "YOU",
            rivalName = prefs[Keys.RIVAL_NAME] ?: "ALEX",
            rivalDailyCount = prefs[Keys.RIVAL_DAILY_COUNT] ?: 64,
            rivalPacemakerEnabled = prefs[Keys.RIVAL_PACEMAKER_ENABLED] ?: true,
            lastResetDate = prefs[Keys.LAST_RESET_DATE] ?: ""
        )
    }

    suspend fun setPillOffsetYDp(offsetDp: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PILL_OFFSET_Y_DP] = offsetDp.coerceIn(0, 80)
        }
    }

    suspend fun setOnlyShowPillInReels(onlyInReels: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONLY_SHOW_PILL_IN_REELS] = onlyInReels
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setBlockerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLOCKER_ENABLED] = enabled
        }
    }

    suspend fun setInstantBlockMode(instant: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INSTANT_BLOCK_MODE] = instant
        }
    }

    suspend fun setAutoBackOnBlock(autoBack: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_BACK_ON_BLOCK] = autoBack
        }
    }

    suspend fun setDailyGoalLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL_LIMIT] = limit.coerceIn(5, 300)
        }
    }

    suspend fun togglePlatformBlock(platform: SocialPlatform) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BLOCKED_PLATFORMS] ?: defaultBlockedPlatforms
            val updated = if (platform.name in current) {
                current - platform.name
            } else {
                current + platform.name
            }
            prefs[Keys.BLOCKED_PLATFORMS] = updated
        }
    }

    suspend fun snoozeBlockerForMinutes(minutes: Int) {
        val until = System.currentTimeMillis() + (minutes * 60_000L)
        context.dataStore.edit { prefs ->
            prefs[Keys.SNOOZE_UNTIL_TIMESTAMP] = until
        }
    }

    suspend fun clearBlockerSnooze() {
        context.dataStore.edit { prefs ->
            prefs[Keys.SNOOZE_UNTIL_TIMESTAMP] = 0L
        }
    }

    suspend fun incrementBlockedCount() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BLOCKED_COUNT_TODAY] ?: 0
            prefs[Keys.BLOCKED_COUNT_TODAY] = current + 1
        }
    }

    suspend fun setUserNickname(nickname: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NICKNAME] = nickname.trim().ifEmpty { "YOU" }.uppercase()
        }
    }

    suspend fun updateRivalProfile(name: String, count: Int, pacemakerEnabled: Boolean = false) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RIVAL_NAME] = name.trim().ifEmpty { "FRIEND" }.uppercase()
            prefs[Keys.RIVAL_DAILY_COUNT] = count.coerceAtLeast(0)
            prefs[Keys.RIVAL_PACEMAKER_ENABLED] = pacemakerEnabled
        }
    }

    suspend fun setLastResetDate(dateString: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_RESET_DATE] = dateString
            prefs[Keys.BLOCKED_COUNT_TODAY] = 0
        }
    }
}
