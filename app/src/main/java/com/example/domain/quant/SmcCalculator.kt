package com.example.domain.quant

import com.example.data.model.FairValueGap
import com.example.data.model.OhlcvCandle
import com.example.data.model.OrderBlockZone
import com.example.data.model.SmcTelemetry
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic Smart Money Concepts (SMC) Pre-Processor.
 * Processes 200 raw Binance OHLCV candles into institutional market structure insights:
 * 1. 3-Bar Left / 3-Bar Right Fractal Swing Pivots (SH / SL)
 * 2. Market Structure Breaks (Bullish/Bearish BOS & CHoCH) and BSL/SSL Liquidity Sweeps (>= 0.1 * ATR14)
 * 3. Unmitigated 3-Candle Fair Value Gaps (BISI / SIBI with > 1.2x 20-bar average body displacement)
 * 4. Institutional Order Blocks (Bullish & Bearish OB with Mean Threshold & 5% proximity filter)
 * 5. 50-Bar Dealing Range Equilibrium (0.50 Fib Premium/Discount + 0.618–0.786 OTE retracement zone)
 */
object SmcCalculator {

    data class SwingPivot(
        val index: Int,
        val price: Double,
        val isHigh: Boolean
    )

    data class SwingPivotAnalysis(
        val swingHighs: List<SwingPivot>,
        val swingLows: List<SwingPivot>,
        val confirmedSwingHigh: Double,
        val priorSwingHigh: Double,
        val confirmedSwingLow: Double,
        val priorSwingLow: Double
    )

    data class StructureDetectionResult(
        val label: String,
        val isBullish: Boolean,
        val breakCode: String,
        val breakLevel: Double,
        val candlesAgo: Int
    )

    data class SweepDetectionResult(
        val label: String,
        val swept: Boolean,
        val sweepCode: String,
        val sweptLevel: Double,
        val wickExtreme: Double
    )

    data class FvgAnalysisResult(
        val unmitigatedBullishFvgs: List<FairValueGap>,
        val unmitigatedBearishFvgs: List<FairValueGap>,
        val nearestBullishFvg: FairValueGap?,
        val nearestBearishFvg: FairValueGap?,
        val activeFvgSummary: String,
        val bullDisplacementIndices: Set<Int>,
        val bearDisplacementIndices: Set<Int>
    )

    data class OrderBlockAnalysisResult(
        val unmitigatedBullishObs: List<OrderBlockZone>,
        val unmitigatedBearishObs: List<OrderBlockZone>,
        val nearestBullishOb: OrderBlockZone?,
        val nearestBearishOb: OrderBlockZone?,
        val nearestObSummary: String
    )

    data class DealingRangeAnalysisResult(
        val dealingRangeHigh50: Double,
        val dealingRangeLow50: Double,
        val equilibriumPrice: Double,
        val dealingRangePct: Double,
        val dealingZoneLabel: String,
        val isDiscountZone: Boolean,
        val oteLow: Double,
        val oteHigh: Double,
        val isInOteZone: Boolean
    )

    /**
     * Master orchestrator: computes all 5 institutional SMC structures deterministically
     * from the 200-candle OHLCV window.
     */
    fun computeSmcTelemetry(
        candles: List<OhlcvCandle>,
        atr14: Double,
        priceDecimals: Int = 2
    ): SmcTelemetry {
        require(candles.isNotEmpty()) { "Candles must not be empty for SMC calculation" }

        val currentPrice = candles.last().close

        // 1. Identify 3-Bar Left / 3-Bar Right Swing Pivots
        val pivotAnalysis = identifySwingPivots(candles)

        // 2. Detect Market Structure (BOS / CHoCH) & Liquidity Sweeps (BSL / SSL)
        val structureInfo = detectMarketStructure(
            candles = candles,
            swingPivots = pivotAnalysis
        )

        val sweepInfo = detectLiquiditySweeps(
            candles = candles,
            confirmedSH = pivotAnalysis.confirmedSwingHigh,
            confirmedSL = pivotAnalysis.confirmedSwingLow,
            atr14 = atr14,
            decimals = priceDecimals
        )

        // 3. Calculate Unmitigated 3-Candle Fair Value Gaps (BISI / SIBI)
        val fvgResult = calculateFairValueGaps(
            candles = candles,
            priceDecimals = priceDecimals
        )

        // 4. Define Unmitigated Institutional Order Blocks (Bullish & Bearish OB within 5%)
        val obResult = defineOrderBlocks(
            candles = candles,
            bullDisplacementIndices = fvgResult.bullDisplacementIndices,
            bearDisplacementIndices = fvgResult.bearDisplacementIndices,
            swingHighs = pivotAnalysis.swingHighs,
            swingLows = pivotAnalysis.swingLows,
            currentPrice = currentPrice,
            confirmedSL = pivotAnalysis.confirmedSwingLow,
            priceDecimals = priceDecimals
        )

        // 5. Calculate 50-Bar Dealing Range Equilibrium (Premium / Discount + 0.618-0.786 OTE)
        val dealingRange = calculateDealingRange(
            candles = candles,
            isStructureBullish = structureInfo.isBullish
        )

        return SmcTelemetry(
            confirmedSwingHigh = pivotAnalysis.confirmedSwingHigh,
            confirmedSwingLow = pivotAnalysis.confirmedSwingLow,
            priorSwingHigh = pivotAnalysis.priorSwingHigh,
            priorSwingLow = pivotAnalysis.priorSwingLow,
            structureState = structureInfo.label,
            isStructureBullish = structureInfo.isBullish,
            liquiditySweepStatus = sweepInfo.label,
            hasActiveSweep = sweepInfo.swept,
            nearestBullishFvg = fvgResult.nearestBullishFvg,
            nearestBearishFvg = fvgResult.nearestBearishFvg,
            activeFvgSummary = fvgResult.activeFvgSummary,
            nearestBullishOb = obResult.nearestBullishOb,
            nearestBearishOb = obResult.nearestBearishOb,
            nearestObSummary = obResult.nearestObSummary,
            dealingRangeHigh50 = dealingRange.dealingRangeHigh50,
            dealingRangeLow50 = dealingRange.dealingRangeLow50,
            equilibriumPrice = dealingRange.equilibriumPrice,
            dealingRangePct = dealingRange.dealingRangePct,
            dealingZoneLabel = dealingRange.dealingZoneLabel,
            isDiscountZone = dealingRange.isDiscountZone,
            oteLow = dealingRange.oteLow,
            oteHigh = dealingRange.oteHigh,
            isInOteZone = dealingRange.isInOteZone,
            structureBreakType = structureInfo.breakCode,
            structureBreakLevel = structureInfo.breakLevel,
            structureBreakCandlesAgo = structureInfo.candlesAgo,
            sweepType = sweepInfo.sweepCode,
            sweptLevel = sweepInfo.sweptLevel,
            sweepWickExtreme = sweepInfo.wickExtreme
        )
    }

    /**
     * 1. Swing Pivots (3-Bar Left / 3-Bar Right Fractals):
     * Identifies all confirmed Swing Highs (SH) and Swing Lows (SL) where the pivot high/low
     * exceeds the 3 preceding and 3 succeeding bars.
     */
    fun identifySwingPivots(candles: List<OhlcvCandle>): SwingPivotAnalysis {
        val swingHighs = mutableListOf<SwingPivot>()
        val swingLows = mutableListOf<SwingPivot>()

        if (candles.size >= 7) {
            for (i in 3..(candles.size - 4)) {
                val c = candles[i]
                val isFractalHigh =
                    c.high >= candles[i - 1].high &&
                    c.high >= candles[i - 2].high &&
                    c.high >= candles[i - 3].high &&
                    c.high > candles[i + 1].high &&
                    c.high >= candles[i + 2].high &&
                    c.high >= candles[i + 3].high

                val isFractalLow =
                    c.low <= candles[i - 1].low &&
                    c.low <= candles[i - 2].low &&
                    c.low <= candles[i - 3].low &&
                    c.low < candles[i + 1].low &&
                    c.low <= candles[i + 2].low &&
                    c.low <= candles[i + 3].low

                if (isFractalHigh) {
                    swingHighs.add(SwingPivot(index = i, price = c.high, isHigh = true))
                }
                if (isFractalLow) {
                    swingLows.add(SwingPivot(index = i, price = c.low, isHigh = false))
                }
            }
        }

        val recent20 = candles.takeLast(min(20, candles.size))
        val fallbackHigh = recent20.maxOf { it.high }
        val fallbackLow = recent20.minOf { it.low }

        val confirmedSH = swingHighs.lastOrNull()?.price ?: fallbackHigh
        val priorSH = if (swingHighs.size >= 2) swingHighs[swingHighs.size - 2].price else confirmedSH
        val confirmedSL = swingLows.lastOrNull()?.price ?: fallbackLow
        val priorSL = if (swingLows.size >= 2) swingLows[swingLows.size - 2].price else confirmedSL

        return SwingPivotAnalysis(
            swingHighs = swingHighs,
            swingLows = swingLows,
            confirmedSwingHigh = confirmedSH,
            priorSwingHigh = priorSH,
            confirmedSwingLow = confirmedSL,
            priorSwingLow = priorSL
        )
    }

    /**
     * 2A. Market Structure (BOS & CHoCH):
     * - Bullish BOS: In an existing uptrend, a candle's body (close) closes above the prior Swing High.
     * - Bearish BOS: In an existing downtrend, a candle's body (close) closes below the prior Swing Low.
     * - Bullish CHoCH: In a prior downtrend, the first candle body (close) that closes above the most recent Lower High (LH).
     * - Bearish CHoCH: In a prior uptrend, the first candle body (close) that closes below the most recent Higher Low (HL).
     */
    fun detectMarketStructure(
        candles: List<OhlcvCandle>,
        swingPivots: SwingPivotAnalysis
    ): StructureDetectionResult {
        val confirmedSH = swingPivots.confirmedSwingHigh
        val priorSH = swingPivots.priorSwingHigh
        val confirmedSL = swingPivots.confirmedSwingLow
        val priorSL = swingPivots.priorSwingLow

        val lastClose = candles.last().close
        val priorTrendBullish = confirmedSH >= priorSH && confirmedSL >= priorSL
        val priorTrendBearish = confirmedSH < priorSH && confirmedSL < priorSL

        val lastShIdx = swingPivots.swingHighs.lastOrNull()?.index ?: 0
        val lastSlIdx = swingPivots.swingLows.lastOrNull()?.index ?: 0

        var bullBreakIdx = -1
        for (idx in (lastShIdx + 1) until candles.size) {
            if (candles[idx].close > confirmedSH) {
                bullBreakIdx = idx
                break
            }
        }
        var bearBreakIdx = -1
        for (idx in (lastSlIdx + 1) until candles.size) {
            if (candles[idx].close < confirmedSL) {
                bearBreakIdx = idx
                break
            }
        }

        val brokeAboveSh = bullBreakIdx != -1 || lastClose > confirmedSH
        val brokeBelowSl = bearBreakIdx != -1 || lastClose < confirmedSL
        val bullCandlesAgo = if (bullBreakIdx != -1) {
            (candles.lastIndex - bullBreakIdx).coerceAtLeast(1)
        } else {
            (candles.lastIndex - lastShIdx).coerceAtLeast(1)
        }
        val bearCandlesAgo = if (bearBreakIdx != -1) {
            (candles.lastIndex - bearBreakIdx).coerceAtLeast(1)
        } else {
            (candles.lastIndex - lastSlIdx).coerceAtLeast(1)
        }

        return when {
            brokeAboveSh && priorTrendBearish ->
                StructureDetectionResult("Bullish CHoCH", true, "BULLISH_CHOCH", confirmedSH, bullCandlesAgo)
            brokeAboveSh ->
                StructureDetectionResult("Bullish BOS", true, "BULLISH_BOS", confirmedSH, bullCandlesAgo)
            brokeBelowSl && priorTrendBullish ->
                StructureDetectionResult("Bearish CHoCH", false, "BEARISH_CHOCH", confirmedSL, bearCandlesAgo)
            brokeBelowSl ->
                StructureDetectionResult("Bearish BOS", false, "BEARISH_BOS", confirmedSL, bearCandlesAgo)
            priorTrendBullish ->
                StructureDetectionResult("Bullish BOS", true, "BULLISH_BOS", confirmedSH, bullCandlesAgo)
            priorTrendBearish ->
                StructureDetectionResult("Bearish BOS", false, "BEARISH_BOS", confirmedSL, bearCandlesAgo)
            lastClose >= (confirmedSH + confirmedSL) / 2.0 ->
                StructureDetectionResult("Bullish CHoCH", true, "BULLISH_CHOCH", confirmedSH, bullCandlesAgo)
            else ->
                StructureDetectionResult("Bearish CHoCH", false, "BEARISH_CHOCH", confirmedSL, bearCandlesAgo)
        }
    }

    /**
     * 2B. Buy-Side / Sell-Side Liquidity Sweep (BSL / SSL):
     * A candle's high wicks above a Swing High (or low wicks below a Swing Low) by at least
     * 0.1 * ATR(14), but its close finishes back inside the range (wick rejection / stop hunt).
     */
    fun detectLiquiditySweeps(
        candles: List<OhlcvCandle>,
        confirmedSH: Double,
        confirmedSL: Double,
        atr14: Double,
        decimals: Int = 2
    ): SweepDetectionResult {
        val minSweepDistance = 0.1 * atr14
        val recentSlice = candles.takeLast(min(15, candles.size))

        for (i in recentSlice.indices.reversed()) {
            val c = recentSlice[i]
            // Buy-Side Liquidity Sweep (BSL): high wicks above Swing High by >= 0.1 * ATR(14) but closes back inside
            if (c.high >= confirmedSH + minSweepDistance && c.close < confirmedSH) {
                return SweepDetectionResult(
                    label = "BSL Swept ($${fmt(c.high, decimals)} Wick Rejection)",
                    swept = true,
                    sweepCode = "BSL_SWEEP",
                    sweptLevel = confirmedSH,
                    wickExtreme = c.high
                )
            }
            // Sell-Side Liquidity Sweep (SSL): low wicks below Swing Low by >= 0.1 * ATR(14) but closes back inside
            if (c.low <= confirmedSL - minSweepDistance && c.close > confirmedSL) {
                return SweepDetectionResult(
                    label = "SSL Swept ($${fmt(c.low, decimals)} Stop Hunt)",
                    swept = true,
                    sweepCode = "SSL_SWEEP",
                    sweptLevel = confirmedSL,
                    wickExtreme = c.low
                )
            }
        }
        return SweepDetectionResult(
            label = "Intact (BSL $${fmt(confirmedSH, decimals)} / SSL $${fmt(confirmedSL, decimals)})",
            swept = false,
            sweepCode = "NONE",
            sweptLevel = confirmedSL,
            wickExtreme = candles.last().low
        )
    }

    /**
     * 3. Unmitigated Fair Value Gaps (3-Candle Imbalance):
     * - Bullish FVG (BISI): Candle[i-2].high < Candle[i].low where middle Candle[i-1] body > 1.2x
     *   the 20-bar average body size (displacement). Unmitigated if no subsequent low <= Candle[i-2].high.
     * - Bearish FVG (SIBI): Candle[i-2].low > Candle[i].high with > 1.2x displacement.
     *   Unmitigated if no subsequent high >= Candle[i-2].low.
     */
    fun calculateFairValueGaps(
        candles: List<OhlcvCandle>,
        priceDecimals: Int = 2
    ): FvgAnalysisResult {
        val currentPrice = candles.last().close
        val avgBodies20 = computeRolling20BodyAverages(candles)
        val unmitigatedBullishFvgs = mutableListOf<FairValueGap>()
        val unmitigatedBearishFvgs = mutableListOf<FairValueGap>()
        val allDisplacementBullIndices = mutableSetOf<Int>()
        val allDisplacementBearIndices = mutableSetOf<Int>()

        if (candles.size >= 3) {
            for (i in 2 until candles.size) {
                val cMinus2 = candles[i - 2]
                val cMinus1 = candles[i - 1]
                val cCurr = candles[i]

                val midBody = abs(cMinus1.close - cMinus1.open)
                val avgBody = avgBodies20[i - 1].coerceAtLeast(1e-8)
                val displacementRatio = midBody / avgBody

                // Bullish FVG (BISI): Candle[i-2].high < Candle[i].low & middle body > 1.2x avg
                if (cMinus2.high < cCurr.low && cMinus1.close > cMinus1.open && displacementRatio > 1.2) {
                    allDisplacementBullIndices.add(i - 1)
                    var mitigated = false
                    for (j in (i + 1) until candles.size) {
                        if (candles[j].low <= cMinus2.high) {
                            mitigated = true
                            break
                        }
                    }
                    if (!mitigated) {
                        val bottom = cMinus2.high
                        val top = cCurr.low
                        unmitigatedBullishFvgs.add(
                            FairValueGap(
                                isBullish = true,
                                top = top,
                                bottom = bottom,
                                midpoint = (top + bottom) / 2.0,
                                candleIndex = i - 1,
                                displacementRatio = displacementRatio
                            )
                        )
                    }
                }

                // Bearish FVG (SIBI): Candle[i-2].low > Candle[i].high & middle body > 1.2x avg
                if (cMinus2.low > cCurr.high && cMinus1.close < cMinus1.open && displacementRatio > 1.2) {
                    allDisplacementBearIndices.add(i - 1)
                    var mitigated = false
                    for (j in (i + 1) until candles.size) {
                        if (candles[j].high >= cMinus2.low) {
                            mitigated = true
                            break
                        }
                    }
                    if (!mitigated) {
                        val top = cMinus2.low
                        val bottom = cCurr.high
                        unmitigatedBearishFvgs.add(
                            FairValueGap(
                                isBullish = false,
                                top = top,
                                bottom = bottom,
                                midpoint = (top + bottom) / 2.0,
                                candleIndex = i - 1,
                                displacementRatio = displacementRatio
                            )
                        )
                    }
                }
            }
        }

        val nearestBullFvg = unmitigatedBullishFvgs.minByOrNull { abs(currentPrice - it.midpoint) }
        val nearestBearFvg = unmitigatedBearishFvgs.minByOrNull { abs(currentPrice - it.midpoint) }
        val primaryFvg = listOfNotNull(nearestBullFvg, nearestBearFvg)
            .minByOrNull { abs(currentPrice - it.midpoint) }

        val activeFvgSummary = if (primaryFvg != null) {
            val tag = if (primaryFvg.isBullish) "BISI" else "SIBI"
            "$${fmt(primaryFvg.bottom, priceDecimals)}-$${fmt(primaryFvg.top, priceDecimals)} ($tag)"
        } else {
            "Balanced (No Open FVG)"
        }

        return FvgAnalysisResult(
            unmitigatedBullishFvgs = unmitigatedBullishFvgs,
            unmitigatedBearishFvgs = unmitigatedBearishFvgs,
            nearestBullishFvg = nearestBullFvg,
            nearestBearishFvg = nearestBearFvg,
            activeFvgSummary = activeFvgSummary,
            bullDisplacementIndices = allDisplacementBullIndices,
            bearDisplacementIndices = allDisplacementBearIndices
        )
    }

    /**
     * 4. Institutional Order Blocks (Bullish & Bearish OB):
     * - Bullish OB: Last bearish (close < open) candle immediately preceding an impulsive bullish
     *   displacement leg that created a Bullish FVG and broke structure (BOS/CHoCH).
     * - Bearish OB: Last bullish (close > open) candle immediately preceding an impulsive bearish
     *   displacement leg that created a Bearish FVG and broke structure.
     * - Zone = [OB.low, OB.high], Mean Threshold = (OB.high + OB.low) / 2, filtered within 5% of current price.
     */
    fun defineOrderBlocks(
        candles: List<OhlcvCandle>,
        bullDisplacementIndices: Set<Int>,
        bearDisplacementIndices: Set<Int>,
        swingHighs: List<SwingPivot>,
        swingLows: List<SwingPivot>,
        currentPrice: Double = candles.last().close,
        confirmedSL: Double = candles.minOf { it.low },
        priceDecimals: Int = 2
    ): OrderBlockAnalysisResult {
        val bullishObs = mutableListOf<OrderBlockZone>()
        val bearishObs = mutableListOf<OrderBlockZone>()

        // Identify Bullish Order Blocks: last bearish candle before a bullish displacement leg + structure break
        for (dispIdx in bullDisplacementIndices.sorted()) {
            var obIdx = -1
            for (k in (dispIdx - 1) downTo max(0, dispIdx - 6)) {
                if (candles[k].close < candles[k].open) {
                    obIdx = k
                    break
                }
            }
            if (obIdx != -1) {
                val obCandle = candles[obIdx]
                val relevantSh = swingHighs.lastOrNull { it.index < dispIdx }?.price
                val brokeStructure = relevantSh == null ||
                    candles.subList(dispIdx, min(candles.size, dispIdx + 8)).any { it.close > relevantSh }

                if (brokeStructure) {
                    val mitigated = candles.drop(dispIdx + 2).any { it.low < obCandle.low }
                    val meanThreshold = (obCandle.high + obCandle.low) / 2.0
                    val distPct = (abs(currentPrice - meanThreshold) / currentPrice) * 100.0
                    if (!mitigated && distPct <= 5.0) {
                        bullishObs.add(
                            OrderBlockZone(
                                isBullish = true,
                                low = obCandle.low,
                                high = obCandle.high,
                                meanThreshold = meanThreshold,
                                candleIndex = obIdx,
                                distancePercent = distPct
                            )
                        )
                    }
                }
            }
        }

        // Identify Bearish Order Blocks: last bullish candle before a bearish displacement leg + structure break
        for (dispIdx in bearDisplacementIndices.sorted()) {
            var obIdx = -1
            for (k in (dispIdx - 1) downTo max(0, dispIdx - 6)) {
                if (candles[k].close > candles[k].open) {
                    obIdx = k
                    break
                }
            }
            if (obIdx != -1) {
                val obCandle = candles[obIdx]
                val relevantSl = swingLows.lastOrNull { it.index < dispIdx }?.price
                val brokeStructure = relevantSl == null ||
                    candles.subList(dispIdx, min(candles.size, dispIdx + 8)).any { it.close < relevantSl }

                if (brokeStructure) {
                    val mitigated = candles.drop(dispIdx + 2).any { it.high > obCandle.high }
                    val meanThreshold = (obCandle.high + obCandle.low) / 2.0
                    val distPct = (abs(currentPrice - meanThreshold) / currentPrice) * 100.0
                    if (!mitigated && distPct <= 5.0) {
                        bearishObs.add(
                            OrderBlockZone(
                                isBullish = false,
                                low = obCandle.low,
                                high = obCandle.high,
                                meanThreshold = meanThreshold,
                                candleIndex = obIdx,
                                distancePercent = distPct
                            )
                        )
                    }
                }
            }
        }

        val nearestBullOb = bullishObs.minByOrNull { it.distancePercent }
        val nearestBearOb = bearishObs.minByOrNull { it.distancePercent }
        val primaryOb = listOfNotNull(nearestBullOb, nearestBearOb)
            .minByOrNull { it.distancePercent }

        val nearestObSummary = if (primaryOb != null) {
            val prefix = if (primaryOb.isBullish) "Bull OB" else "Bear OB"
            "$prefix $${fmt(primaryOb.meanThreshold, priceDecimals)}"
        } else {
            "OB Support $${fmt(confirmedSL, priceDecimals)}"
        }

        return OrderBlockAnalysisResult(
            unmitigatedBullishObs = bullishObs,
            unmitigatedBearishObs = bearishObs,
            nearestBullishOb = nearestBullOb,
            nearestBearishOb = nearestBearOb,
            nearestObSummary = nearestObSummary
        )
    }

    /**
     * 5. Dealing Range Equilibrium (Premium vs. Discount & OTE):
     * - Active 50-bar Swing High and Swing Low dealing range
     * - Equilibrium = (RangeHigh + RangeLow) / 2
     * - Price > Equilibrium (> 0.50 Fib) = Premium Zone
     * - Price < Equilibrium (< 0.50 Fib) = Discount Zone
     * - OTE (Optimal Trade Entry) = 0.618 to 0.786 Fibonacci retracement of active dealing leg
     */
    fun calculateDealingRange(
        candles: List<OhlcvCandle>,
        isStructureBullish: Boolean
    ): DealingRangeAnalysisResult {
        val currentPrice = candles.last().close
        val range50Candles = candles.takeLast(min(50, candles.size))
        val dealingHigh = range50Candles.maxOf { it.high }
        val dealingLow = range50Candles.minOf { it.low }
        val dealingSpan = (dealingHigh - dealingLow).coerceAtLeast(currentPrice * 0.0001)
        val equilibrium = (dealingHigh + dealingLow) / 2.0
        val dealingPct = (((currentPrice - dealingLow) / dealingSpan) * 100.0).coerceIn(0.0, 100.0)
        val isDiscount = currentPrice < equilibrium
        val dealingZoneLabel = if (isDiscount) {
            String.format(Locale.US, "Discount (%.1f%%)", dealingPct)
        } else {
            String.format(Locale.US, "Premium (%.1f%%)", dealingPct)
        }

        val (oteLow, oteHigh) = if (isStructureBullish) {
            val fib786 = dealingHigh - 0.786 * dealingSpan
            val fib618 = dealingHigh - 0.618 * dealingSpan
            Pair(min(fib786, fib618), max(fib786, fib618))
        } else {
            val fib618 = dealingLow + 0.618 * dealingSpan
            val fib786 = dealingLow + 0.786 * dealingSpan
            Pair(min(fib618, fib786), max(fib618, fib786))
        }
        val isInOteZone = currentPrice in oteLow..oteHigh

        return DealingRangeAnalysisResult(
            dealingRangeHigh50 = dealingHigh,
            dealingRangeLow50 = dealingLow,
            equilibriumPrice = equilibrium,
            dealingRangePct = dealingPct,
            dealingZoneLabel = dealingZoneLabel,
            isDiscountZone = isDiscount,
            oteLow = oteLow,
            oteHigh = oteHigh,
            isInOteZone = isInOteZone
        )
    }

    private fun computeRolling20BodyAverages(candles: List<OhlcvCandle>): DoubleArray {
        val result = DoubleArray(candles.size)
        var runningSum = 0.0
        for (i in candles.indices) {
            val body = abs(candles[i].close - candles[i].open)
            runningSum += body
            if (i >= 20) {
                runningSum -= abs(candles[i - 20].close - candles[i - 20].open)
                result[i] = runningSum / 20.0
            } else {
                result[i] = runningSum / (i + 1).toDouble()
            }
        }
        return result
    }

    private fun fmt(value: Double, decimals: Int): String {
        return String.format(Locale.US, "%,.${decimals}f", value)
    }
}
