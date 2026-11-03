package com.example.domain.quant

import com.example.data.model.CryptoPair
import com.example.data.model.OhlcvCandle
import com.example.data.model.QuantitativeTelemetry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object QuantitativeIndicatorEngine {

    fun computeTelemetry(
        symbol: String,
        timeframe: String,
        candles: List<OhlcvCandle>
    ): QuantitativeTelemetry {
        require(candles.isNotEmpty()) { "Candles list must not be empty" }

        val closes = candles.map { it.close }
        val currentPrice = closes.last()
        val decimals = CryptoPair.fromSymbol(symbol).priceDecimals

        val ema20 = calculateEma(closes, 20)
        val ema50 = calculateEma(closes, 50)
        val ema200 = calculateEma(closes, 200)

        val emaAlignment = when {
            ema20 > ema50 && ema50 > ema200 && currentPrice > ema20 -> "Bullish Stack (20 > 50 > 200)"
            ema20 < ema50 && ema50 < ema200 && currentPrice < ema20 -> "Bearish Stack (20 < 50 < 200)"
            ema20 > ema50 -> "Bullish Cross (EMA 20 > 50)"
            ema20 < ema50 -> "Bearish Cross (EMA 20 < 50)"
            else -> "Neutral / Consolidation"
        }

        val rsi14 = calculateRsi(closes, 14)
        val rsiState = when {
            rsi14 >= 70.0 -> "Overbought (>70)"
            rsi14 <= 30.0 -> "Oversold (<30)"
            rsi14 >= 55.0 -> "Bullish Momentum (>55)"
            rsi14 <= 45.0 -> "Bearish Momentum (<45)"
            else -> "Neutral Midline (~50)"
        }

        val vwap = calculateRollingVwap(candles, window = min(50, candles.size))
        val vwapDeltaPercent = if (vwap > 0.0) ((currentPrice - vwap) / vwap) * 100.0 else 0.0

        val atr14 = calculateAtr(candles, 14)
        val (supertrendValue, supertrendBullish) = calculateSupertrend(candles, period = 10, multiplier = 3.0)

        val recent20 = candles.takeLast(min(20, candles.size))
        val swingHigh20 = recent20.maxOf { it.high }
        val swingLow20 = recent20.minOf { it.low }

        val (nearestSupport, nearestResistance) = calculateSupportResistanceZones(
            candles = candles,
            currentPrice = currentPrice,
            atr = atr14,
            swingLow20 = swingLow20,
            swingHigh20 = swingHigh20
        )

        val volumeWindow = candles.takeLast(min(21, candles.size))
        val avgVolume20 = if (volumeWindow.size > 1) {
            volumeWindow.dropLast(1).map { it.volume }.average().coerceAtLeast(1e-8)
        } else {
            volumeWindow.first().volume.coerceAtLeast(1e-8)
        }
        val latestVolume = candles.last().volume
        val volumeSpikeRatio = latestVolume / avgVolume20
        val isInstitutionalVolumeSpike = volumeSpikeRatio >= 1.75

        val smcTelemetry = SmcCalculator.computeSmcTelemetry(
            candles = candles,
            atr14 = atr14,
            priceDecimals = decimals
        )

        return QuantitativeTelemetry(
            symbol = symbol,
            timeframe = timeframe,
            currentPrice = currentPrice,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            emaAlignment = emaAlignment,
            rsi14 = rsi14,
            rsiState = rsiState,
            vwap = vwap,
            vwapDeltaPercent = vwapDeltaPercent,
            atr14 = atr14,
            supertrendValue = supertrendValue,
            supertrendBullish = supertrendBullish,
            swingHigh20 = swingHigh20,
            swingLow20 = swingLow20,
            nearestSupport = nearestSupport,
            nearestResistance = nearestResistance,
            volumeSpikeRatio = volumeSpikeRatio,
            isInstitutionalVolumeSpike = isInstitutionalVolumeSpike,
            smc = smcTelemetry
        )
    }

    fun calculateEma(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        if (values.size <= period) return values.average()

        val k = 2.0 / (period + 1.0)
        var ema = values.take(period).average()
        for (i in period until values.size) {
            ema = (values[i] - ema) * k + ema
        }
        return ema
    }

    fun calculateRsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum -= diff
        }

        var avgGain = gainSum / period
        var avgLoss = lossSum / period

        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
        }

        if (avgLoss <= 1e-12) return 100.0
        val rs = avgGain / avgLoss
        return (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
    }

    fun calculateRollingVwap(candles: List<OhlcvCandle>, window: Int = 50): Double {
        val slice = candles.takeLast(window)
        var pvSum = 0.0
        var volSum = 0.0
        for (c in slice) {
            val typicalPrice = (c.high + c.low + c.close) / 3.0
            val vol = if (c.volume > 0.0) c.volume else 1.0
            pvSum += typicalPrice * vol
            volSum += vol
        }
        return if (volSum > 0.0) pvSum / volSum else candles.last().close
    }

    fun calculateAtr(candles: List<OhlcvCandle>, period: Int = 14): Double {
        if (candles.size < 2) {
            val first = candles.firstOrNull() ?: return 0.0
            return (first.high - first.low).coerceAtLeast(first.close * 0.005)
        }
        val trueRanges = DoubleArray(candles.size)
        trueRanges[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val highLow = candles[i].high - candles[i].low
            val highPrevClose = abs(candles[i].high - candles[i - 1].close)
            val lowPrevClose = abs(candles[i].low - candles[i - 1].close)
            trueRanges[i] = max(highLow, max(highPrevClose, lowPrevClose))
        }

        if (candles.size <= period) {
            return trueRanges.average()
        }

        var atr = trueRanges.take(period).average()
        for (i in period until trueRanges.size) {
            atr = ((atr * (period - 1)) + trueRanges[i]) / period
        }
        return atr
    }

    fun calculateSupertrend(
        candles: List<OhlcvCandle>,
        period: Int = 10,
        multiplier: Double = 3.0
    ): Pair<Double, Boolean> {
        if (candles.size <= period) {
            val last = candles.last()
            return Pair(last.low, true)
        }

        val tr = DoubleArray(candles.size)
        tr[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val hl = candles[i].high - candles[i].low
            val hpc = abs(candles[i].high - candles[i - 1].close)
            val lpc = abs(candles[i].low - candles[i - 1].close)
            tr[i] = max(hl, max(hpc, lpc))
        }

        val atr = DoubleArray(candles.size)
        var runningAtr = tr.take(period).average()
        for (i in 0 until candles.size) {
            if (i < period) {
                atr[i] = runningAtr
            } else {
                runningAtr = ((runningAtr * (period - 1)) + tr[i]) / period
                atr[i] = runningAtr
            }
        }

        val finalUpperBand = DoubleArray(candles.size)
        val finalLowerBand = DoubleArray(candles.size)
        val supertrend = DoubleArray(candles.size)
        val isBullish = BooleanArray(candles.size)

        for (i in candles.indices) {
            val hl2 = (candles[i].high + candles[i].low) / 2.0
            val basicUpper = hl2 + multiplier * atr[i]
            val basicLower = hl2 - multiplier * atr[i]

            if (i == 0) {
                finalUpperBand[i] = basicUpper
                finalLowerBand[i] = basicLower
                isBullish[i] = true
                supertrend[i] = finalLowerBand[i]
            } else {
                finalUpperBand[i] = if (basicUpper < finalUpperBand[i - 1] || candles[i - 1].close > finalUpperBand[i - 1]) {
                    basicUpper
                } else {
                    finalUpperBand[i - 1]
                }

                finalLowerBand[i] = if (basicLower > finalLowerBand[i - 1] || candles[i - 1].close < finalLowerBand[i - 1]) {
                    basicLower
                } else {
                    finalLowerBand[i - 1]
                }

                val prevBull = isBullish[i - 1]
                isBullish[i] = when {
                    prevBull && candles[i].close < finalLowerBand[i] -> false
                    !prevBull && candles[i].close > finalUpperBand[i] -> true
                    else -> prevBull
                }

                supertrend[i] = if (isBullish[i]) finalLowerBand[i] else finalUpperBand[i]
            }
        }

        val lastIdx = candles.lastIndex
        return Pair(supertrend[lastIdx], isBullish[lastIdx])
    }

    private fun calculateSupportResistanceZones(
        candles: List<OhlcvCandle>,
        currentPrice: Double,
        atr: Double,
        swingLow20: Double,
        swingHigh20: Double
    ): Pair<Double, Double> {
        val recent = candles.takeLast(min(60, candles.size))
        val pivotLows = mutableListOf<Double>()
        val pivotHighs = mutableListOf<Double>()

        for (i in 2 until (recent.size - 2)) {
            val c = recent[i]
            if (c.low <= recent[i - 1].low && c.low <= recent[i - 2].low &&
                c.low <= recent[i + 1].low && c.low <= recent[i + 2].low
            ) {
                pivotLows.add(c.low)
            }
            if (c.high >= recent[i - 1].high && c.high >= recent[i - 2].high &&
                c.high >= recent[i + 1].high && c.high >= recent[i + 2].high
            ) {
                pivotHighs.add(c.high)
            }
        }

        val supportCandidate = pivotLows.filter { it < currentPrice }.maxOrNull()
            ?: min(swingLow20, currentPrice - 1.5 * atr)
        val resistanceCandidate = pivotHighs.filter { it > currentPrice }.minOrNull()
            ?: max(swingHigh20, currentPrice + 1.5 * atr)

        return Pair(supportCandidate, resistanceCandidate)
    }
}
