package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserQuotaAuthMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "orixvex_preferences")

data class LocalAppPreferences(
    val isDarkTheme: Boolean = true,
    val strategyProfile: String = "Balanced",
    val defaultTimeframe: String = "15m",
    val accountBalanceUsd: Double = 25000.0,
    val riskPercentPerTrade: Double = 1.0,
    val quotaAuthMode: String = UserQuotaAuthMode.FIREBASE_AI_STUDIO.name,
    val personalGeminiKey: String = "",
    val userOAuthBearerToken: String = "",
    val deepWebSearchGrounding: Boolean = false,
    val rpdUsedToday: Int = 0,
    val rpdDateStamp: String = ""
)

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val STRATEGY_PROFILE = stringPreferencesKey("strategy_profile")
        val DEFAULT_TIMEFRAME = stringPreferencesKey("default_timeframe")
        val ACCOUNT_BALANCE_USD = doublePreferencesKey("account_balance_usd")
        val RISK_PERCENT = doublePreferencesKey("risk_percent_per_trade")
        val QUOTA_AUTH_MODE = stringPreferencesKey("quota_auth_mode")
        val DEEP_WEB_GROUNDING = booleanPreferencesKey("deep_web_grounding")
        val RPD_USED_TODAY = intPreferencesKey("rpd_used_today")
        val RPD_DATE_STAMP = stringPreferencesKey("rpd_date_stamp")

        fun userGeminiKeyForUid(uid: String) = stringPreferencesKey("user_gemini_key_${uid.ifBlank { "preview" }}")
        fun userOAuthTokenForUid(uid: String) = stringPreferencesKey("user_oauth_token_${uid.ifBlank { "preview" }}")
    }

    private fun currentDayStamp(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val preferencesFlow: Flow<LocalAppPreferences> = preferencesFlowForUser("preview")

    fun preferencesFlowForUser(uid: String): Flow<LocalAppPreferences> =
        context.dataStore.data.map { prefs ->
            val today = currentDayStamp()
            val storedStamp = prefs[Keys.RPD_DATE_STAMP] ?: ""
            val rpd = if (storedStamp == today) (prefs[Keys.RPD_USED_TODAY] ?: 0) else 0
            LocalAppPreferences(
                isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
                strategyProfile = prefs[Keys.STRATEGY_PROFILE] ?: "Balanced",
                defaultTimeframe = prefs[Keys.DEFAULT_TIMEFRAME] ?: "15m",
                accountBalanceUsd = prefs[Keys.ACCOUNT_BALANCE_USD] ?: 25000.0,
                riskPercentPerTrade = prefs[Keys.RISK_PERCENT] ?: 1.0,
                quotaAuthMode = prefs[Keys.QUOTA_AUTH_MODE] ?: UserQuotaAuthMode.FIREBASE_AI_STUDIO.name,
                personalGeminiKey = prefs[Keys.userGeminiKeyForUid(uid)] ?: "",
                userOAuthBearerToken = prefs[Keys.userOAuthTokenForUid(uid)] ?: "",
                deepWebSearchGrounding = prefs[Keys.DEEP_WEB_GROUNDING] ?: false,
                rpdUsedToday = rpd,
                rpdDateStamp = today
            )
        }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = isDark
        }
    }

    suspend fun updateTradingPreferences(
        strategyProfile: String,
        defaultTimeframe: String,
        accountBalanceUsd: Double,
        riskPercentPerTrade: Double
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STRATEGY_PROFILE] = strategyProfile
            prefs[Keys.DEFAULT_TIMEFRAME] = defaultTimeframe
            prefs[Keys.ACCOUNT_BALANCE_USD] = accountBalanceUsd
            prefs[Keys.RISK_PERCENT] = riskPercentPerTrade
        }
    }

    suspend fun saveUserQuotaConfig(
        userId: String,
        mode: String,
        personalApiKey: String,
        oauthBearerToken: String,
        deepWebSearchGrounding: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QUOTA_AUTH_MODE] = mode
            prefs[Keys.DEEP_WEB_GROUNDING] = deepWebSearchGrounding
            if (personalApiKey.isNotBlank()) {
                prefs[Keys.userGeminiKeyForUid(userId)] = personalApiKey.trim()
            }
            if (oauthBearerToken.isNotBlank()) {
                prefs[Keys.userOAuthTokenForUid(userId)] = oauthBearerToken.trim()
            }
        }
    }

    suspend fun incrementDailyRequestCount(): Int {
        var updatedCount = 1
        val today = currentDayStamp()
        context.dataStore.edit { prefs ->
            val storedStamp = prefs[Keys.RPD_DATE_STAMP] ?: ""
            val current = if (storedStamp == today) (prefs[Keys.RPD_USED_TODAY] ?: 0) else 0
            updatedCount = current + 1
            prefs[Keys.RPD_DATE_STAMP] = today
            prefs[Keys.RPD_USED_TODAY] = updatedCount
        }
        return updatedCount
    }
}
