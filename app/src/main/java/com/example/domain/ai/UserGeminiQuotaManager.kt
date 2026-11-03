package com.example.domain.ai

import com.example.data.model.UserQuotaAuthMode
import com.example.data.model.UserQuotaStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil

/**
 * 3-Tier User Quota Authentication & Sliding-Window Rate-Limit Tracker (15 RPM / 1,500 RPD).
 * Tracks requests-per-minute (RPM) and requests-per-day (RPD) for the signed-in user's
 * Gemini quota and provides burst rate-limit protection with countdown messaging.
 */
class UserGeminiQuotaManager {

    private val requestTimestamps60s = ArrayDeque<Long>()

    private val _quotaStatus = MutableStateFlow(UserQuotaStatus())
    val quotaStatus: StateFlow<UserQuotaStatus> = _quotaStatus.asStateFlow()

    private var cachedPersonalKey: String = ""
    private var cachedOAuthBearerToken: String = ""

    fun syncFromPreferences(
        modeName: String,
        personalKey: String,
        oauthToken: String,
        rpdUsedToday: Int,
        deepWebSearchGrounding: Boolean
    ) {
        cachedPersonalKey = personalKey
        cachedOAuthBearerToken = oauthToken
        val mode = runCatching { UserQuotaAuthMode.valueOf(modeName) }
            .getOrDefault(UserQuotaAuthMode.FIREBASE_AI_STUDIO)
        pruneSlidingWindow(System.currentTimeMillis())
        _quotaStatus.update {
            it.copy(
                mode = mode,
                rpmUsed = requestTimestamps60s.size,
                rpdUsed = rpdUsedToday,
                hasCustomKey = personalKey.isNotBlank(),
                hasOAuthToken = oauthToken.isNotBlank(),
                deepWebSearchGroundingEnabled = deepWebSearchGrounding
            )
        }
    }

    fun getPersonalKey(): String = cachedPersonalKey
    fun getOAuthBearerToken(): String = cachedOAuthBearerToken

    /**
     * Checks if the user is within their 15 RPM / 1,500 RPD quota before executing a Gemini request.
     * Returns null if permitted, or a user-facing rate-limit countdown message if throttled.
     */
    @Synchronized
    fun checkRateLimitBeforeRequest(nowMs: Long = System.currentTimeMillis()): String? {
        pruneSlidingWindow(nowMs)
        val status = _quotaStatus.value
        if (status.rpdUsed >= status.rpdLimit) {
            return "Daily user quota reached (${status.rpdLimit}/${status.rpdLimit} RPD). Quota resets at 00:00 UTC."
        }
        if (requestTimestamps60s.size >= status.rpmLimit) {
            val oldest = requestTimestamps60s.firstOrNull() ?: nowMs
            val waitSec = ceil(((oldest + 60_000L) - nowMs) / 1000.0).toInt().coerceAtLeast(1)
            _quotaStatus.update {
                it.copy(
                    rpmUsed = requestTimestamps60s.size,
                    cooldownRemainingSec = waitSec
                )
            }
            return "Personal 15 RPM burst limit reached (${requestTimestamps60s.size}/15 RPM). Next slot opens in ${waitSec}s."
        }
        return null
    }

    @Synchronized
    fun recordRequestCompleted(newRpdCount: Int, nowMs: Long = System.currentTimeMillis()) {
        pruneSlidingWindow(nowMs)
        requestTimestamps60s.addLast(nowMs)
        _quotaStatus.update {
            it.copy(
                rpmUsed = requestTimestamps60s.size,
                rpdUsed = newRpdCount,
                cooldownRemainingSec = 0
            )
        }
    }

    @Synchronized
    fun refreshSlidingWindow(nowMs: Long = System.currentTimeMillis()) {
        pruneSlidingWindow(nowMs)
        val oldest = requestTimestamps60s.firstOrNull()
        val cooldown = if (requestTimestamps60s.size >= _quotaStatus.value.rpmLimit && oldest != null) {
            ceil(((oldest + 60_000L) - nowMs) / 1000.0).toInt().coerceAtLeast(0)
        } else {
            0
        }
        _quotaStatus.update {
            it.copy(
                rpmUsed = requestTimestamps60s.size,
                cooldownRemainingSec = cooldown
            )
        }
    }

    private fun pruneSlidingWindow(nowMs: Long) {
        val cutoff = nowMs - 60_000L
        while (requestTimestamps60s.isNotEmpty() && requestTimestamps60s.first() < cutoff) {
            requestTimestamps60s.removeFirst()
        }
    }
}
