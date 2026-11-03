package com.example.domain.quant

import com.example.data.model.AutoFibonacciAndPivots
import com.example.data.model.BullsVsBearsPower
import com.example.data.model.CryptoPair
import com.example.data.model.DeepQuantSuiteTelemetry
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.MtfTimeframeRow
import com.example.data.model.OhlcvCandle
import com.example.data.model.ProjectedSwingRoadmap
import com.example.data.model.QuantitativeTelemetry
import com.example.data.model.SignalStrengthBreakdown
import com.example.data.model.VolatilitySqueezeRadar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Computes:
 * 1. AI Signal Strength Breakdown (0-100% + Institutional Grade S / A+ / A / B / C + 4 weighted sub-bars)
 * 2. Live Bulls vs. Bears Power Meter (Bulls XX% vs. Bears YY%)
 * 3. Possible Future Swings & Trajectory Projection (Immediate Micro Swing Target, Bullish/Bearish Liquidity Magnets, Trajectory Curve)
 * 4. Expanded Technical Suite (Auto-Fibonacci Retracements/Extensions, Classic Pivot Points, Bollinger vs. Keltner Volatility Squeeze Radar)
 */
object MarketDynamicsAndQuantEngine {

    /**
     * Computes the 4-pillar weighted AI Signal Strength Breakdown:
     * - SMC Structure & PD Arrays (35%)
     * - Multi-Timeframe Trend & Momentum (30%)
     * - Order Flow & Volume Delta (20%)
     * - News & Macro Sentiment (15%)
     */
    fun computeSignalStrengthBreakdown(
        telemetry: QuantitativeTelemetry,
        fundamentals: FundamentalMacroSnapshot,
        mtfRows: List<MtfTimeframeRow>
    ): SignalStrengthBreakdown {
        val smc = telemetry.smc
        val isBullBias = smc.isStructureBullish

        // 1. SMC Structure & PD Arrays (Max 35)
        var smcScore = 18
        if (smc.structureBreakType.contains("BOS")) smcScore += 6 else if (smc.structureBreakType.contains("CHOCH")) smcScore += 5
        if ((isBullBias && smc.isDiscountZone) || (!isBullBias && !smc.isDiscountZone)) smcScore += 5
        if (smc.isInOteZone) smcScore += 3
        if (smc.hasActiveSweep) smcScore += 3
        val smcClamped = smcScore.coerceIn(10, 35)

        // 2. Multi-Timeframe Trend & Momentum (Max 30)
        val alignedMtfCount = if (mtfRows.isNotEmpty()) {
            mtfRows.count { it.isBullish == isBullBias }
        } else {
            3
        }
        var mtfScore = (alignedMtfCount * 4).coerceAtMost(20)
        if ((isBullBias && telemetry.supertrendBullish) || (!isBullBias && !telemetry.supertrendBullish)) {
            mtfScore += 5
        }
        val rsiHealthy = if (isBullBias) telemetry.rsi14 in 48.0..68.0 else telemetry.rsi14 in 32.0..52.0
        if (rsiHealthy) mtfScore += 5 else mtfScore += 2
        val mtfClamped = mtfScore.coerceIn(8, 30)

        // 3. Order Flow & Volume Delta (Max 20)
        var flowScore = 10
        val obImbalance = fundamentals.orderBookDepth.bidAskImbalancePct
        if ((isBullBias && obImbalance > 3.0) || (!isBullBias && obImbalance < -3.0)) flowScore += 4
        val takerBuyPct = fundamentals.takerBuyPercent
        if ((isBullBias && takerBuyPct >= 52.0) || (!isBullBias && takerBuyPct <= 48.0)) flowScore += 4
        if (telemetry.isInstitutionalVolumeSpike) flowScore += 2
        val flowClamped = flowScore.coerceIn(5, 20)

        // 4. News & Macro Sentiment (Max 15)
        var newsScore = 8
        val netNews = fundamentals.netNewsSentimentScore
        if ((isBullBias && netNews >= 55.0) || (!isBullBias && netNews <= 45.0)) newsScore += 4
        val fgVal = fundamentals.fearAndGreed.currentValue
        if ((isBullBias && fgVal in 25..78) || (!isBullBias && fgVal in 22..75)) newsScore += 3
        val newsClamped = newsScore.coerceIn(4, 15)

        val total = (smcClamped + mtfClamped + flowClamped + newsClamped).coerceIn(0, 100)
        val grade = when {
            total >= 90 -> "S"
            total >= 82 -> "A+"
            total >= 74 -> "A"
            total >= 64 -> "B"
            else -> "C"
        }

        return SignalStrengthBreakdown(
            overallScore = total,
            institutionalGrade = grade,
            smcStructureScore35 = smcClamped,
            mtfTrendMomentumScore30 = mtfClamped,
            orderFlowVolumeScore20 = flowClamped,
            newsMacroSentimentScore15 = newsClamped
        )
    }

    /**
     * Computes the real-time Bulls vs. Bears tug-of-war power meter from:
     * 1. Taker Buy vs. Taker Sell Volume Ratio
     * 2. Order Book Top-20 Bid vs. Ask Liquidity Depth
     * 3. Price position relative to EMA 20/50/200 & Rolling Session VWAP
     * 4. RSI (14) & MACD Histogram slope
     */
    fun computeBullsVsBearsPower(
        candles: List<OhlcvCandle>,
        telemetry: QuantitativeTelemetry,
        fundamentals: FundamentalMacroSnapshot
    ): BullsVsBearsPower {
        val takerBuyPct = fundamentals.takerBuyPercent.coerceIn(15.0, 85.0)
        val obBidPct = fundamentals.orderBookDepth.bidRatioPct.coerceIn(15.0, 85.0)

        // EMA 20/50/200 + VWAP structural bias (0..100)
        var structuralBullPoints = 0.0
        if (telemetry.currentPrice > telemetry.ema20) structuralBullPoints += 25.0
        if (telemetry.ema20 > telemetry.ema50) structuralBullPoints += 25.0
        if (telemetry.currentPrice > telemetry.ema200) structuralBullPoints += 25.0
        if (telemetry.currentPrice > telemetry.vwap) structuralBullPoints += 25.0

        // RSI(14) + MACD momentum bias (0..100)
        val closes = candles.map { it.close }
        val macdHistBonus = if (closes.size >= 30) {
            val ema12 = computeSimpleEma(closes, 12)
            val ema26 = computeSimpleEma(closes, 26)
            val macdLine = ema12 - ema26
            if (macdLine >= 0) 8.0 else -8.0
        } else 0.0
        val rsiMomentumPct = (telemetry.rsi14 + macdHistBonus).coerceIn(15.0, 85.0)

        val weightedBulls = (
            takerBuyPct * 0.30 +
                obBidPct * 0.25 +
                structuralBullPoints * 0.25 +
                rsiMomentumPct * 0.20
            ).coerceIn(12.0, 88.0)

        val bullsRounded = (weightedBulls * 10.0).roundToInt() / 10.0
        val bearsRounded = ((100.0 - bullsRounded) * 10.0).roundToInt() / 10.0

        val dominantLabel = when {
            bullsRounded >= 65.0 -> "Aggressive Bullish Absorption"
            bullsRounded >= 54.0 -> "Bulls Controlling Orderflow"
            bearsRounded >= 65.0 -> "Aggressive Bearish Distribution"
            bearsRounded >= 54.0 -> "Bears Controlling Orderflow"
            else -> "Institutional Equilibrium (Balanced)"
        }

        return BullsVsBearsPower(
            bullsPercent = bullsRounded,
            bearsPercent = bearsRounded,
            takerBuyRatioPct = takerBuyPct,
            orderBookBidRatioPct = obBidPct,
            emaVwapBiasPct = structuralBullPoints,
            rsiMacdMomentumPct = rsiMomentumPct,
            dominantSideLabel = dominantLabel
        )
    }

    /**
     * Computes Possible Future Swings & Trajectory Projection from current SMC PD arrays,
     * ATR(14), and confirmed Swing High/Low liquidity pools.
     */
    fun computeProjectedSwingRoadmap(
        telemetry: QuantitativeTelemetry,
        pair: CryptoPair
    ): ProjectedSwingRoadmap {
        val dec = pair.priceDecimals
        val smc = telemetry.smc
        val price = telemetry.currentPrice
        val atr = telemetry.atr14.coerceAtLeast(price * 0.003)

        val bullObTarget = smc.nearestBullishOb?.meanThreshold ?: (price - 1.2 * atr)
        val bearObTarget = smc.nearestBearishOb?.meanThreshold ?: (price + 1.2 * atr)
        val fvgMid = if (smc.isStructureBullish) {
            smc.nearestBullishFvg?.midpoint ?: bullObTarget
        } else {
            smc.nearestBearishFvg?.midpoint ?: bearObTarget
        }

        val microLow = if (smc.isStructureBullish) {
            min(bullObTarget, fvgMid).coerceAtMost(price - 0.35 * atr)
        } else {
            (price - 1.35 * atr)
        }
        val microHigh = if (smc.isStructureBullish) {
            max( bullObTarget, fvgMid).coerceAtMost(price - 0.10 * atr)
        } else {
            max(bearObTarget, fvgMid).coerceAtLeast(price + 0.35 * atr)
        }

        val upsideMagnet = max(smc.confirmedSwingHigh, bearObTarget).coerceAtLeast(price + 1.8 * atr)
        val downsideMagnet = min(smc.confirmedSwingLow, bullObTarget).coerceAtMost(price - 1.8 * atr)

        val bullProb = when {
            smc.isStructureBullish && telemetry.supertrendBullish && smc.isDiscountZone -> 72
            smc.isStructureBullish && telemetry.supertrendBullish -> 65
            smc.isStructureBullish -> 57
            !smc.isStructureBullish && !telemetry.supertrendBullish -> 33
            else -> 44
        }
        val bearProb = 100 - bullProb

        fun fmt(v: Double) = String.format(Locale.US, "$%,.${dec}f", v)

        val narrative = if (smc.isStructureBullish) {
            "Expected Sweep into ${fmt(microLow)}–${fmt(microHigh)} Discount OB/FVG → Displacement Rally toward ${fmt(upsideMagnet)} BSL"
        } else {
            "Expected Retest of ${fmt(microLow)}–${fmt(microHigh)} Premium OB/FVG → Markdown Expansion toward ${fmt(downsideMagnet)} SSL"
        }

        val trajectoryPoints = if (smc.isStructureBullish) {
            listOf(0.46f, 0.34f, 0.28f, 0.49f, 0.72f, 0.90f)
        } else {
            listOf(0.54f, 0.68f, 0.74f, 0.51f, 0.28f, 0.12f)
        }

        return ProjectedSwingRoadmap(
            microSwingTargetLow = min(microLow, microHigh),
            microSwingTargetHigh = max(microLow, microHigh),
            microWindowLabel = "Next 4–8 candles (${telemetry.timeframe})",
            bullishMagnetPrice = upsideMagnet,
            bullishMagnetLabel = "Upside BSL / Bearish OB (${fmt(upsideMagnet)})",
            bullishProbabilityPct = bullProb,
            bearishMagnetPrice = downsideMagnet,
            bearishMagnetLabel = "Downside SSL / Bullish OB (${fmt(downsideMagnet)})",
            bearishProbabilityPct = bearProb,
            expectedSwingPathNarrative = narrative,
            trajectoryNormalizedPoints = trajectoryPoints
        )
    }

    /**
     * Computes Auto-Fibonacci Retracements/Extensions, Classic Pivot Points (P, R1, R2, S1, S2),
     * and the Bollinger Bands (20, 2) vs. Keltner Channels (20, 1.5) Volatility Squeeze Radar.
     */
    fun computeDeepQuantSuite(
        candles: List<OhlcvCandle>,
        telemetry: QuantitativeTelemetry,
        mtfRows: List<MtfTimeframeRow>
    ): DeepQuantSuiteTelemetry {
        val smc = telemetry.smc
        val sh = smc.dealingRangeHigh50.coerceAtLeast(telemetry.swingHigh20)
        val sl = smc.dealingRangeLow50.coerceAtMost(telemetry.swingLow20)
        val range = (sh - sl).coerceAtLeast(1e-8)

        val isUp = smc.isStructureBullish
        val fib382 = if (isUp) sh - 0.382 * range else sl + 0.382 * range
        val fib500 = (sh + sl) / 2.0
        val fib618 = if (isUp) sh - 0.618 * range else sl + 0.618 * range
        val fib786 = if (isUp) sh - 0.786 * range else sl + 0.786 * range
        val ext1272 = if (isUp) sh + 0.272 * range else sl - 0.272 * range
        val ext1618 = if (isUp) sh + 0.618 * range else sl - 0.618 * range

        // Classic Pivot Points from the recent 24-bar session window
        val sessionWindow = candles.takeLast(24).ifEmpty { candles }
        val sessionHigh = sessionWindow.maxOfOrNull { it.high } ?: sh
        val sessionLow = sessionWindow.minOfOrNull { it.low } ?: sl
        val sessionClose = sessionWindow.lastOrNull()?.close ?: telemetry.currentPrice

        val pivotP = (sessionHigh + sessionLow + sessionClose) / 3.0
        val pivotR1 = 2.0 * pivotP - sessionLow
        val pivotS1 = 2.0 * pivotP - sessionHigh
        val pivotR2 = pivotP + (sessionHigh - sessionLow)
        val pivotS2 = pivotP - (sessionHigh - sessionLow)

        val fibAndPivots = AutoFibonacciAndPivots(
            swingHigh = sh,
            swingLow = sl,
            fib382 = fib382,
            fib500 = fib500,
            fib618GoldenPocket = fib618,
            fib786 = fib786,
            ext1272 = ext1272,
            ext1618 = ext1618,
            pivotP = pivotP,
            pivotR1 = pivotR1,
            pivotR2 = pivotR2,
            pivotS1 = pivotS1,
            pivotS2 = pivotS2
        )

        // Bollinger Bands (20, 2.0) vs Keltner Channels (20, 1.5 ATR)
        val last20 = candles.takeLast(20)
        val bbMiddle = if (last20.isNotEmpty()) {
            last20.map { it.close }.average()
        } else {
            telemetry.currentPrice
        }
        val variance = if (last20.size > 1) {
            last20.sumOf { (it.close - bbMiddle) * (it.close - bbMiddle) } / last20.size
        } else 0.0
        val stdDev = sqrt(variance)
        val bbUpper = bbMiddle + 2.0 * stdDev
        val bbLower = bbMiddle - 2.0 * stdDev

        val kcUpper = telemetry.ema20 + 1.5 * telemetry.atr14
        val kcLower = telemetry.ema20 - 1.5 * telemetry.atr14

        val isSqueeze = bbUpper <= kcUpper && bbLower >= kcLower
        val atrPct = if (telemetry.currentPrice > 0) {
            (telemetry.atr14 / telemetry.currentPrice) * 100.0
        } else 0.0

        val regime = when {
            isSqueeze -> "Low Compression (BB Inside Keltner Squeeze — Breakout Imminent)"
            atrPct >= 1.35 -> "High Expansion (Explosive ATR Displacement)"
            else -> "Normal Institutional Volatility"
        }

        val squeezeRadar = VolatilitySqueezeRadar(
            bbUpper = bbUpper,
            bbMiddle = bbMiddle,
            bbLower = bbLower,
            kcUpper = kcUpper,
            kcLower = kcLower,
            isSqueezeActive = isSqueeze,
            atrPercent = atrPct,
            volatilityRegime = regime
        )

        return DeepQuantSuiteTelemetry(
            mtfRows = mtfRows,
            fibAndPivots = fibAndPivots,
            squeezeRadar = squeezeRadar
        )
    }

    private fun computeSimpleEma(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        if (values.size <= period) return values.average()
        val k = 2.0 / (period + 1.0)
        var ema = values.take(period).average()
        for (i in period until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
        }
        return ema
    }
}
