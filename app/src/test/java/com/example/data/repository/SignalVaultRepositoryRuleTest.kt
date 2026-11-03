package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AiSignalResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalVaultRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleAiSignal(): AiSignalResult {
        return AiSignalResult(
            symbol = "BTCUSDT",
            displayPair = "BTC/USD",
            timeframe = "15m",
            tradingStyle = "Scalp 5m/15m",
            signal = "STRONG BUY",
            confidence = 88,
            marketRegime = "Bullish Momentum",
            entryLow = 91000.0,
            entryHigh = 91250.0,
            stopLoss = 90400.0,
            takeProfit1 = 92100.0,
            takeProfit2 = 92900.0,
            takeProfit3 = 94100.0,
            riskRewardRatio = "1 : 2.6",
            indicatorConfluence = listOf(
                "EMA 20 > EMA 50 > EMA 200 bullish stack",
                "RSI(14) at 62.5 showing strong momentum",
                "Price trading +0.38% above session VWAP",
                "Supertrend(10,3) in active bullish trend"
            ),
            invalidationCondition = "15m close below $90,400 invalidates bullish continuation."
        )
    }

    @Test
    fun bookmarkSignal_and_observeSignals_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SignalVaultRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.bookmarkSignal(sampleAiSignal())
        }
        assertTrue(createResult.isSuccess)
        val signalId = createResult.getOrThrow()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSignals(aliceUid).first { list -> list.any { it.id == signalId } }
        }
        assertTrue(emitted.any { it.id == signalId && it.symbol == "BTCUSDT" })

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateSignalStatus(signalId, "TP1_HIT")
        }
        assertTrue(updateResult.isSuccess)
    }

    @Test
    fun saveUserSettings_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SignalVaultRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserSettings(
                strategyProfile = "Aggressive",
                defaultTimeframe = "1h",
                accountBalanceUsd = 50000.0,
                riskPercentPerTrade = 1.5
            )
        }
        assertTrue(saveResult.isSuccess)

        val settings = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserSettings(aliceUid).first { it != null }
        }
        assertNotNull(settings)
        assertEquals("Aggressive", settings?.strategyProfile)
    }

    @Test
    fun getSignalById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SignalVaultRepository(firestore)
        val signalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.bookmarkSignal(sampleAiSignal()).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SignalVaultRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSignalById(aliceUid, signalId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeSignals_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = SignalVaultRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeSignals(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caughtException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@orixvex.test"
        const val BOB_EMAIL = "bob@orixvex.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
