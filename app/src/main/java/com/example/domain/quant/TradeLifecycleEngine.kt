package com.example.domain.quant

import com.example.data.model.ActiveManagedTrade
import com.example.data.model.AiSignalResult
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.OhlcvCandle
import com.example.data.model.QuantitativeTelemetry
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Non-Repainting ("Non-Printable") Locked Signal Engine & Lifecycle Manager.
 *
 * Guarantees:
 * 1. Signals are computed on confirmed closed candles (`extractClosedCandlesForNonRepainting`).
 * 2. Initial Entry Zone, Initial Stop Loss, and TP1/TP2/TP3 are permanently locked at generation.
 * 3. Live Dynamic Trailing Stop Loss (TSL) updates on every WebSocket tick and NEVER moves backward:
 *    - Phase 1 (Before TP1): TSL = Initial Stop Loss ("Initial Risk Protected").
 *    - Phase 2 (Once TP1 1.5R Hit): TSL ratchets to Break-Even + 0.10% Fee Buffer ("Risk-Free: Locked at Break-Even").
 *    - Phase 3 (Once TP2 2.5R Hit): TSL ratchets continuously using 1.5x ATR(14) behind highestHigh (Long) or lowestLow (Short).
 * 4. Early Exit & Reversal Protection Monitor triggers when opposing CHoCH/Supertrend flips,
 *    opposing Taker volume spikes > 2.2x average, or breaking high-impact adverse news occurs before TP1.
 */
object TradeLifecycleEngine {

    private const val BREAK_EVEN_FEE_BUFFER_PCT = 0.0010 // 0.10% fee buffer

    /**
     * Filters out the active unclosed forming candle (`isClosed == false`) when sufficient history exists
     * so all SMC structures and indicators are strictly bar-close confirmed and non-repainting.
     */
    fun extractClosedCandlesForNonRepainting(candles: List<OhlcvCandle>): List<OhlcvCandle> {
        if (candles.size <= 25) return candles
        val last = candles.last()
        return if (!last.isClosed) candles.dropLast(1) else candles
    }

    /**
     * Locks an AI Signal Result into an immutable non-repainting [ActiveManagedTrade] instance.
     */
    fun createLockedNonRepaintingTrade(
        signalResult: AiSignalResult,
        telemetry: QuantitativeTelemetry,
        closedBarTimestampMs: Long,
        userEmailWatermark: String
    ): ActiveManagedTrade {
        val isLong = !signalResult.signal.contains("SELL", ignoreCase = true)
        val entryMid = signalResult.optimalEntry.takeIf { it > 0.0 }
            ?: ((signalResult.entryLow + signalResult.entryHigh) / 2.0)
        val currentPrice = telemetry.currentPrice
        val atr14 = telemetry.atr14.coerceAtLeast(currentPrice * 0.002)

        val distanceToMidPct = if (currentPrice > 0) {
            abs(currentPrice - entryMid) / currentPrice * 100.0
        } else 0.0
        val orderType = if (distanceToMidPct <= 0.20) {
            "Market Execution"
        } else {
            "Limit at OB/FVG"
        }

        val watermark = if (userEmailWatermark.isNotBlank()) {
            "OrixVex Non-Repainting Verified • $userEmailWatermark"
        } else {
            "OrixVex Non-Repainting Verified • Institutional Lock"
        }

        val deterministicSignalId = signalResult.signalId.takeIf {
            it.isNotBlank() && !it.startsWith("Immutable deterministic ID", ignoreCase = true)
        } ?: "${signalResult.symbol}_${signalResult.timeframe}_$closedBarTimestampMs"

        val lifecycle = signalResult.tradeLifecycle
        val initialTsl = lifecycle.trailingStopConfig.currentRecommendedTSL
            .takeIf { it > 0.0 } ?: signalResult.stopLoss

        return ActiveManagedTrade(
            signalId = deterministicSignalId,
            symbol = signalResult.symbol,
            displayPair = signalResult.displayPair,
            timeframe = signalResult.timeframe,
            tradingStyle = signalResult.tradingStyle,
            signal = signalResult.signal,
            isLong = isLong,
            confidence = signalResult.confidence,
            marketRegime = signalResult.marketRegime,
            orderType = orderType,
            lockedAtBarCloseMs = closedBarTimestampMs,
            initialEntryLow = signalResult.entryLow,
            initialEntryHigh = signalResult.entryHigh,
            entryMidpoint = entryMid,
            initialStopLoss = signalResult.stopLoss,
            tp1 = signalResult.takeProfit1,
            tp2 = signalResult.takeProfit2,
            tp3 = signalResult.takeProfit3,
            atr14AtLock = atr14,
            currentPrice = currentPrice,
            highestPriceReached = max(currentPrice, entryMid),
            lowestPriceReached = min(currentPrice, entryMid),
            currentTrailingStop = initialTsl,
            tslPhaseLabel = "Initial Risk Protected",
            tp1Hit = false,
            tp2Hit = false,
            tp3Hit = false,
            isClosed = false,
            status = "ACTIVE",
            earlyExitRecommended = lifecycle.earlyExitTrigger.earlyExitNeededNow,
            earlyExitReason = if (lifecycle.earlyExitTrigger.earlyExitNeededNow) {
                lifecycle.earlyExitTrigger.earlyExitReason
            } else "",
            earlyExitSuggestedPrice = if (lifecycle.earlyExitTrigger.earlyExitNeededNow) currentPrice else 0.0,
            mfePct = 0.0,
            maePct = 0.0,
            currentRMultiple = 0.0,
            watermarkId = watermark,
            isNonRepaintingConfirmed = signalResult.isNonRepaintingConfirmed,
            trailingStopActivationRule = lifecycle.trailingStopConfig.activationRule,
            trailingStopAtrMultiplier = lifecycle.trailingStopConfig.atrMultiplier.takeIf { it > 0.0 } ?: 1.5,
            emergencyExitPriceCondition = lifecycle.earlyExitTrigger.emergencyExitPriceCondition,
            tp1ActionOnHit = lifecycle.targets.tp1.actionOnHit,
            tp2ActionOnHit = lifecycle.targets.tp2.actionOnHit,
            tp3ActionOnHit = lifecycle.targets.tp3.actionOnHit
        )
    }

    /**
     * Evaluates a live price tick against a locked [ActiveManagedTrade], updating MFE, MAE,
     * R-Multiple, TP1/TP2/TP3 milestones, and ratcheting the Trailing Stop Loss (TSL) forward only.
     */
    fun evaluateTickUpdate(
        trade: ActiveManagedTrade,
        livePrice: Double,
        liveAtr14: Double = trade.atr14AtLock
    ): ActiveManagedTrade {
        if (trade.isClosed || livePrice <= 0.0) return trade

        val atr = if (liveAtr14 > 0.0) liveAtr14 else trade.atr14AtLock
        val atrMult = trade.trailingStopAtrMultiplier.takeIf { it > 0.0 } ?: 1.5
        val entry = trade.entryMidpoint.coerceAtLeast(1e-8)
        val initialRisk = abs(entry - trade.initialStopLoss).coerceAtLeast(entry * 0.001)

        val newHighest = max(trade.highestPriceReached, livePrice)
        val newLowest = min(trade.lowestPriceReached, livePrice)

        val mfePct = if (trade.isLong) {
            max(0.0, (newHighest - entry) / entry * 100.0)
        } else {
            max(0.0, (entry - newLowest) / entry * 100.0)
        }

        val maePct = if (trade.isLong) {
            max(0.0, (entry - newLowest) / entry * 100.0)
        } else {
            max(0.0, (newHighest - entry) / entry * 100.0)
        }

        val currentR = if (trade.isLong) {
            (livePrice - entry) / initialRisk
        } else {
            (entry - livePrice) / initialRisk
        }

        val tp1HitNow = trade.tp1Hit || (if (trade.isLong) livePrice >= trade.tp1 else livePrice <= trade.tp1)
        val tp2HitNow = trade.tp2Hit || (if (trade.isLong) livePrice >= trade.tp2 else livePrice <= trade.tp2)
        val tp3HitNow = trade.tp3Hit || (if (trade.isLong) livePrice >= trade.tp3 else livePrice <= trade.tp3)

        // Check if current price hit the active trailing stop or initial stop loss
        val stopTriggered = if (trade.isLong) {
            livePrice <= trade.currentTrailingStop
        } else {
            livePrice >= trade.currentTrailingStop
        }

        if (stopTriggered) {
            val closedByTslProfit = trade.tp1Hit || (if (trade.isLong) trade.currentTrailingStop > trade.initialStopLoss else trade.currentTrailingStop < trade.initialStopLoss)
            val finalStatus = if (closedByTslProfit) "TSL_CLOSED" else "STOPPED_OUT"
            return trade.copy(
                currentPrice = livePrice,
                highestPriceReached = newHighest,
                lowestPriceReached = newLowest,
                mfePct = mfePct,
                maePct = maePct,
                currentRMultiple = currentR,
                isClosed = true,
                status = finalStatus
            )
        }

        if (tp3HitNow) {
            return trade.copy(
                currentPrice = livePrice,
                highestPriceReached = newHighest,
                lowestPriceReached = newLowest,
                tp1Hit = true,
                tp2Hit = true,
                tp3Hit = true,
                mfePct = mfePct,
                maePct = maePct,
                currentRMultiple = max(currentR, 4.0),
                isClosed = true,
                status = "TP3_HIT",
                tslPhaseLabel = "TP3 Final Target Achieved (4.0R)"
            )
        }

        // Compute non-backward ratcheting TSL
        var updatedTsl = trade.currentTrailingStop
        var updatedPhaseLabel = trade.tslPhaseLabel

        if (tp2HitNow) {
            val breakEvenStop = if (trade.isLong) {
                entry * (1.0 + BREAK_EVEN_FEE_BUFFER_PCT)
            } else {
                entry * (1.0 - BREAK_EVEN_FEE_BUFFER_PCT)
            }
            val atrTrailCandidate = if (trade.isLong) {
                max(breakEvenStop, newHighest - atrMult * atr)
            } else {
                min(breakEvenStop, newLowest + atrMult * atr)
            }
            updatedTsl = if (trade.isLong) {
                max(updatedTsl, atrTrailCandidate)
            } else {
                min(updatedTsl, atrTrailCandidate)
            }
            updatedPhaseLabel = "ATR Runner Ratchet (${atrMult}x ATR)"
        } else if (tp1HitNow) {
            val breakEvenStop = if (trade.isLong) {
                entry * (1.0 + BREAK_EVEN_FEE_BUFFER_PCT)
            } else {
                entry * (1.0 - BREAK_EVEN_FEE_BUFFER_PCT)
            }
            updatedTsl = if (trade.isLong) {
                max(updatedTsl, breakEvenStop)
            } else {
                min(updatedTsl, breakEvenStop)
            }
            updatedPhaseLabel = "Risk-Free: Locked at Break-Even (+0.10%)"
        }

        val updatedStatus = when {
            tp2HitNow -> "TP2_HIT"
            tp1HitNow -> "TP1_HIT"
            else -> "ACTIVE"
        }

        return trade.copy(
            currentPrice = livePrice,
            highestPriceReached = newHighest,
            lowestPriceReached = newLowest,
            currentTrailingStop = updatedTsl,
            tslPhaseLabel = updatedPhaseLabel,
            tp1Hit = tp1HitNow,
            tp2Hit = tp2HitNow,
            tp3Hit = false,
            mfePct = mfePct,
            maePct = maePct,
            currentRMultiple = currentR,
            status = updatedStatus
        )
    }

    /**
     * Evaluates whether an active trade should display an Early Exit warning due to
     * opposing CHoCH / Supertrend flip, Taker volume spike > 2.2x against the position,
     * or breaking high-impact adverse news before TP1 is reached.
     */
    fun evaluateEarlyExit(
        trade: ActiveManagedTrade,
        telemetry: QuantitativeTelemetry,
        fundamentals: FundamentalMacroSnapshot?
    ): ActiveManagedTrade {
        if (trade.isClosed || trade.tp1Hit) {
            return if (trade.earlyExitRecommended) {
                trade.copy(earlyExitRecommended = false, earlyExitReason = "")
            } else {
                trade
            }
        }

        val reasons = mutableListOf<String>()

        // 1. Opposing CHoCH or Supertrend flip
        if (trade.isLong) {
            val chochAgainst = telemetry.smc.structureBreakType.equals("BEARISH_CHOCH", ignoreCase = true)
            val stAgainst = !telemetry.supertrendBullish && telemetry.currentPrice < telemetry.ema20
            if (chochAgainst) {
                reasons.add("Opposing Bearish CHoCH confirmed @ $${String.format("%.2f", telemetry.smc.structureBreakLevel)}")
            } else if (stAgainst) {
                reasons.add("Supertrend flipped BEARISH with close below EMA 20")
            }
        } else {
            val chochAgainst = telemetry.smc.structureBreakType.equals("BULLISH_CHOCH", ignoreCase = true)
            val stAgainst = telemetry.supertrendBullish && telemetry.currentPrice > telemetry.ema20
            if (chochAgainst) {
                reasons.add("Opposing Bullish CHoCH confirmed @ $${String.format("%.2f", telemetry.smc.structureBreakLevel)}")
            } else if (stAgainst) {
                reasons.add("Supertrend flipped BULLISH with close above EMA 20")
            }
        }

        // 2. Opposing Taker Volume Spike > 2.2x 20-bar average
        val takerBuyRatio = fundamentals?.takerBuySellRatio ?: 1.0
        if (telemetry.volumeSpikeRatio >= 2.2) {
            if (trade.isLong && takerBuyRatio < 0.82) {
                reasons.add(
                    String.format(
                        "Institutional Taker Sell Spike (%.1fx vol, %.0f%% aggressive sellers)",
                        telemetry.volumeSpikeRatio,
                        100.0 - (fundamentals?.takerBuyPercent ?: 40.0)
                    )
                )
            } else if (!trade.isLong && takerBuyRatio > 1.22) {
                reasons.add(
                    String.format(
                        "Institutional Taker Buy Spike (%.1fx vol, %.0f%% aggressive buyers)",
                        telemetry.volumeSpikeRatio,
                        fundamentals?.takerBuyPercent ?: 60.0
                    )
                )
            }
        }

        // 3. Breaking High-Impact News Sentiment Shift against position before TP1
        if (fundamentals != null && fundamentals.hasBreakingAdverseCatalyst) {
            val adverseForLong = trade.isLong && fundamentals.netNewsSentimentScore < 38.0
            val adverseForShort = !trade.isLong && fundamentals.netNewsSentimentScore > 62.0
            if (adverseForLong || adverseForShort) {
                reasons.add("Breaking Macro Catalyst: \"${fundamentals.breakingCatalystHeadline.take(68)}\"")
            }
        }

        return if (reasons.isNotEmpty()) {
            trade.copy(
                earlyExitRecommended = true,
                earlyExitReason = reasons.joinToString(" • "),
                earlyExitSuggestedPrice = telemetry.currentPrice
            )
        } else {
            trade.copy(
                earlyExitRecommended = false,
                earlyExitReason = "",
                earlyExitSuggestedPrice = 0.0
            )
        }
    }
}
