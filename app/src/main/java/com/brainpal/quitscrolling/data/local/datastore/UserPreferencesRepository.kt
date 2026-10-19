package com.brainpal.quitscrolling.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brainpal_user_prefs")

data class UserPreferencesState(
    val pillOffsetYDp: Int = 14,
    val overlayAlwaysVisibleInDemo: Boolean = true,
    val userNickname: String = "YOU",
    val rivalName: String = "ALEX",
    val rivalDailyCount: Int = 64,
    val rivalPacemakerEnabled: Boolean = true,
    val dailyGoalLimit: Int = 50,
    val lastResetDate: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val PILL_OFFSET_Y_DP = intPreferencesKey("pill_offset_y_dp")
        val OVERLAY_ALWAYS_VISIBLE = booleanPreferencesKey("overlay_always_visible")
        val USER_NICKNAME = stringPreferencesKey("user_nickname")
        val RIVAL_NAME = stringPreferencesKey("rival_name")
        val RIVAL_DAILY_COUNT = intPreferencesKey("rival_daily_count")
        val RIVAL_PACEMAKER_ENABLED = booleanPreferencesKey("rival_pacemaker_enabled")
        val DAILY_GOAL_LIMIT = intPreferencesKey("daily_goal_limit")
        val LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
        UserPreferencesState(
            pillOffsetYDp = prefs[Keys.PILL_OFFSET_Y_DP] ?: 14,
            overlayAlwaysVisibleInDemo = prefs[Keys.OVERLAY_ALWAYS_VISIBLE] ?: true,
            userNickname = prefs[Keys.USER_NICKNAME] ?: "YOU",
            rivalName = prefs[Keys.RIVAL_NAME] ?: "ALEX",
            rivalDailyCount = prefs[Keys.RIVAL_DAILY_COUNT] ?: 64,
            rivalPacemakerEnabled = prefs[Keys.RIVAL_PACEMAKER_ENABLED] ?: true,
            dailyGoalLimit = prefs[Keys.DAILY_GOAL_LIMIT] ?: 50,
            lastResetDate = prefs[Keys.LAST_RESET_DATE] ?: ""
        )
    }

    suspend fun setPillOffsetYDp(offsetDp: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PILL_OFFSET_Y_DP] = offsetDp.coerceIn(0, 80)
        }
    }

    suspend fun setOverlayAlwaysVisible(visible: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OVERLAY_ALWAYS_VISIBLE] = visible
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

    suspend fun setDailyGoalLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_GOAL_LIMIT] = limit.coerceIn(10, 500)
        }
    }

    suspend fun setLastResetDate(dateString: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_RESET_DATE] = dateString
        }
    }
}
