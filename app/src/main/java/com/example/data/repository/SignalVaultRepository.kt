package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiSignalResult
import com.example.data.model.SavedSignal
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SignalVaultRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeSignals(userId: String = requireUserId()): Flow<List<SavedSignal>> = flow {
        val path = "users/$userId/signals"
        emitAll(
            db.collection("users").document(userId).collection("signals")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedSignal::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserSignals(userId: String = requireUserId()): Result<List<SavedSignal>> {
        val path = "users/$userId/signals"
        return try {
            val snapshot = db.collection("users").document(userId).collection("signals")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val list = snapshot.toObjects(
                SavedSignal::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getSignalById(ownerUserId: String, signalId: String): Result<SavedSignal?> {
        val path = "users/$ownerUserId/signals/$signalId"
        return try {
            val doc = db.collection("users").document(ownerUserId)
                .collection("signals").document(signalId)
                .get()
                .await()
            Result.success(
                doc.toObject(
                    SavedSignal::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun bookmarkSignal(aiSignal: AiSignalResult): Result<String> {
        val uid = requireUserId()
        val signalId = "sig_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        val payload = mapOf(
            "userId" to uid,
            "symbol" to aiSignal.symbol,
            "displayPair" to aiSignal.displayPair,
            "timeframe" to aiSignal.timeframe,
            "tradingStyle" to aiSignal.tradingStyle,
            "signal" to aiSignal.signal,
            "confidence" to aiSignal.confidence,
            "marketRegime" to aiSignal.marketRegime,
            "entryLow" to aiSignal.entryLow,
            "entryHigh" to aiSignal.entryHigh,
            "stopLoss" to aiSignal.stopLoss,
            "takeProfit1" to aiSignal.takeProfit1,
            "takeProfit2" to aiSignal.takeProfit2,
            "takeProfit3" to aiSignal.takeProfit3,
            "riskRewardRatio" to aiSignal.riskRewardRatio,
            "indicatorConfluence" to aiSignal.indicatorConfluence.take(8).ifEmpty { listOf("EMA & VWAP quantitative confluence") },
            "invalidationCondition" to aiSignal.invalidationCondition.take(400).ifBlank { "Close beyond ATR stop level" },
            "status" to "ACTIVE",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(signalId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSignalStatus(signalId: String, newStatus: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        return try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSignal(signalId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserSettings(userId: String = requireUserId()): Flow<UserSettings?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveUserSettings(
        strategyProfile: String,
        defaultTimeframe: String,
        accountBalanceUsd: Double,
        riskPercentPerTrade: Double
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "strategyProfile" to strategyProfile,
                        "defaultTimeframe" to defaultTimeframe,
                        "accountBalanceUsd" to accountBalanceUsd,
                        "riskPercentPerTrade" to riskPercentPerTrade,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "strategyProfile" to strategyProfile,
                        "defaultTimeframe" to defaultTimeframe,
                        "accountBalanceUsd" to accountBalanceUsd,
                        "riskPercentPerTrade" to riskPercentPerTrade,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
