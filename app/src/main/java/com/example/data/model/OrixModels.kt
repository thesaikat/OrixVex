package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CryptoPair(
    val symbol: String,
    val displayPair: String,
    val baseAsset: String,
    val fullName: String,
    val priceDecimals: Int
) {
    BTC("BTCUSDT", "BTC/USD", "BTC", "Bitcoin", 2),
    ETH("ETHUSDT", "ETH/USD", "ETH", "Ethereum", 2),
    SOL("SOLUSDT", "SOL/USD", "SOL", "Solana", 2),
    BNB("BNBUSDT", "BNB/USD", "BNB", "BNB", 2),
    XRP("XRPUSDT", "XRP/USD", "XRP", "XRP", 4),
    ADA("ADAUSDT", "ADA/USD", "ADA", "Cardano", 4),
    AVAX("AVAXUSDT", "AVAX/USD", "AVAX", "Avalanche", 2),
    DOGE("DOGEUSDT", "DOGE/USD", "DOGE", "Dogecoin", 5),
    LINK("LINKUSDT", "LINK/USD", "LINK", "Chainlink", 3),
    TON("TONUSDT", "TON/USD", "TON", "Toncoin", 3);

    val tradingViewSymbol: String
        get() = "BINANCE:$symbol"

    companion object {
        fun fromSymbol(symbol: String): CryptoPair =
            entries.find { it.symbol.equals(symbol, ignoreCase = true) } ?: BTC
    }
}

enum class Timeframe(
    val binanceInterval: String,
    val label: String,
    val tvInterval: String,
    val defaultTradingStyle: String
) {
    M1("1m", "1m", "1", "Scalp 5m/15m"),
    M5("5m", "5m", "5", "Scalp 5m/15m"),
    M15("15m", "15m", "15", "Scalp 5m/15m"),
    H1("1h", "1H", "60", "Intraday 1h"),
    H4("4h", "4H", "240", "Swing 4h/1d"),
    D1("1d", "1D", "D", "Swing 4h/1d");

    companion object {
        fun fromInterval(interval: String): Timeframe =
            entries.find { it.binanceInterval.equals(interval, ignoreCase = true) } ?: M15
    }
}

enum class StrategyProfile(val label: String, val description: String) {
    CONSERVATIVE("Conservative", "High-confluence setups, wider ATR stops, lower drawdown focus"),
    BALANCED("Balanced", "Institutional equilibrium between R:R ratio and win probability"),
    AGGRESSIVE("Aggressive", "Early momentum entries, tighter stops, extended 4.0R targets");

    companion object {
        fun fromLabel(label: String): StrategyProfile =
            entries.find { it.label.equals(label, ignoreCase = true) } ?: BALANCED
    }
}

data class OhlcvCandle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long,
    val quoteVolume: Double,
    val takerBuyBaseVolume: Double = volume * 0.5,
    val takerBuyQuoteVolume: Double = quoteVolume * 0.5,
    val isClosed: Boolean = true
)

data class LiveTicker(
    val pair: CryptoPair,
    val price: Double,
    val priceChangePercent24h: Double,
    val high24h: Double,
    val low24h: Double,
    val volumeUsd24h: Double,
    val lastTickDirection: Int = 0, // 1 = up, -1 = down, 0 = neutral
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

data class FairValueGap(
    val isBullish: Boolean, // true = BISI, false = SIBI
    val top: Double,
    val bottom: Double,
    val midpoint: Double,
    val candleIndex: Int,
    val displacementRatio: Double
) {
    val typeCode: String get() = if (isBullish) "Bullish FVG (BISI)" else "Bearish FVG (SIBI)"
}

data class OrderBlockZone(
    val isBullish: Boolean,
    val low: Double,
    val high: Double,
    val meanThreshold: Double,
    val candleIndex: Int,
    val distancePercent: Double
) {
    val label: String get() = if (isBullish) "Bullish OB" else "Bearish OB"
}

data class SmcTelemetry(
    val confirmedSwingHigh: Double,
    val confirmedSwingLow: Double,
    val priorSwingHigh: Double,
    val priorSwingLow: Double,
    val structureState: String, // e.g., "Bullish BOS", "Bearish BOS", "Bullish CHoCH", "Bearish CHoCH"
    val isStructureBullish: Boolean,
    val liquiditySweepStatus: String, // e.g., "SSL Swept ($90,120 Stop Hunt)", "BSL Swept ($92,400 Wick Rejection)", "No Recent Sweep"
    val hasActiveSweep: Boolean,
    val nearestBullishFvg: FairValueGap?,
    val nearestBearishFvg: FairValueGap?,
    val activeFvgSummary: String,
    val nearestBullishOb: OrderBlockZone?,
    val nearestBearishOb: OrderBlockZone?,
    val nearestObSummary: String,
    val dealingRangeHigh50: Double,
    val dealingRangeLow50: Double,
    val equilibriumPrice: Double,
    val dealingRangePct: Double, // 0% = RangeLow, 100% = RangeHigh
    val dealingZoneLabel: String, // e.g., "Discount (38.2%)" or "Premium (71.4%)"
    val isDiscountZone: Boolean,
    val oteLow: Double,
    val oteHigh: Double,
    val isInOteZone: Boolean,
    val structureBreakType: String = "BULLISH_BOS",
    val structureBreakLevel: Double = confirmedSwingHigh,
    val structureBreakCandlesAgo: Int = 1,
    val sweepType: String = "NONE",
    val sweptLevel: Double = 0.0,
    val sweepWickExtreme: Double = 0.0
)

data class QuantitativeTelemetry(
    val symbol: String,
    val timeframe: String,
    val currentPrice: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val emaAlignment: String,
    val rsi14: Double,
    val rsiState: String,
    val vwap: Double,
    val vwapDeltaPercent: Double,
    val atr14: Double,
    val supertrendValue: Double,
    val supertrendBullish: Boolean,
    val swingHigh20: Double,
    val swingLow20: Double,
    val nearestSupport: Double,
    val nearestResistance: Double,
    val volumeSpikeRatio: Double,
    val isInstitutionalVolumeSpike: Boolean,
    val smc: SmcTelemetry
) {
    fun toInstitutionalJsonString(
        displayPair: String,
        tradingStyle: String,
        decimals: Int = 2
    ): String {
        fun num(v: Double): String = String.format( java.util.Locale.US, "%.${decimals}f", v)
        val emaStackCode = when {
            ema20 > ema50 && ema50 > ema200 -> "BULLISH_ALIGNED"
            ema20 < ema50 && ema50 < ema200 -> "BEARISH_ALIGNED"
            else -> "MIXED_CONSOLIDATION"
        }
        val obEntries = buildList {
            smc.nearestBullishOb?.let {
                add("""{ "type": "BULLISH_OB", "low": ${num(it.low)}, "high": ${num(it.high)}, "meanThreshold": ${num(it.meanThreshold)} }""")
            }
            smc.nearestBearishOb?.let {
                add("""{ "type": "BEARISH_OB", "low": ${num(it.low)}, "high": ${num(it.high)}, "meanThreshold": ${num(it.meanThreshold)} }""")
            }
        }.joinToString(",\n      ")

        val fvgEntries = buildList {
            smc.nearestBullishFvg?.let {
                add("""{ "type": "BULLISH_FVG_BISI", "bottom": ${num(it.bottom)}, "top": ${num(it.top)}, "consequentEncroachment50": ${num(it.midpoint)} }""")
            }
            smc.nearestBearishFvg?.let {
                add("""{ "type": "BEARISH_FVG_SIBI", "bottom": ${num(it.bottom)}, "top": ${num(it.top)}, "consequentEncroachment50": ${num(it.midpoint)} }""")
            }
        }.joinToString(",\n      ")

        val posRatio = String.format(java.util.Locale.US, "%.2f", (smc.dealingRangePct / 100.0).coerceIn(0.0, 1.0))
        val zoneDesc = when {
            smc.dealingRangePct in 45.0..55.0 -> if (smc.isDiscountZone) "Equilibrium / Slight Discount" else "Equilibrium / Slight Premium"
            smc.isDiscountZone -> "Discount Zone"
            else -> "Premium Zone"
        }
        val wickField = if (smc.sweepType == "BSL_SWEEP") "wickHigh" else "wickLow"

        return """
{
  "pair": "$displayPair",
  "binanceSymbol": "$symbol",
  "timeframe": "$timeframe",
  "tradingStyle": "$tradingStyle",
  "currentPrice": ${num(currentPrice)},
  "classicIndicators": {
    "ema20": ${num(ema20)},
    "ema50": ${num(ema50)},
    "ema200": ${num(ema200)},
    "emaStack": "$emaStackCode",
    "rsi14": ${String.format(java.util.Locale.US, "%.1f", rsi14)},
    "vwapSession": ${num(vwap)},
    "vwapDeltaPct": "${String.format(java.util.Locale.US, "%+.2f%%", vwapDeltaPercent)}",
    "supertrend_10_3": { "direction": "${if (supertrendBullish) "BULLISH" else "BEARISH"}", "supportLine": ${num(supertrendValue)} },
    "atr14": ${num(atr14)}
  },
  "smartMoneyConcepts": {
    "lastStructureBreak": { "type": "${smc.structureBreakType}", "breakLevel": ${num(smc.structureBreakLevel)}, "candlesAgo": ${smc.structureBreakCandlesAgo} },
    "dealingRange": {
      "swingLow": ${num(smc.dealingRangeLow50)},
      "swingHigh": ${num(smc.dealingRangeHigh50)},
      "equilibrium50": ${num(smc.equilibriumPrice)},
      "currentPositionPct": $posRatio,
      "zone": "$zoneDesc"
    },
    "recentLiquiditySweep": {
      "swept": ${smc.hasActiveSweep},
      "type": "${smc.sweepType}",
      "sweptLevel": ${num(smc.sweptLevel)},
      "$wickField": ${num(smc.sweepWickExtreme)},
      "rejectionConfirmed": ${smc.hasActiveSweep}
    },
    "unmitigatedOrderBlocks": [
      $obEntries
    ],
    "unmitigatedFVGs": [
      $fvgEntries
    ]
  }
}
        """.trimIndent()
    }

    companion object {
        fun fromInstitutionalJson(rawJson: String): QuantitativeTelemetry {
            val root = JSONObject(rawJson)
            val symbol = root.optString("binanceSymbol", "BTCUSDT")
            val timeframe = root.optString("timeframe", "15m")
            val currentPrice = root.optDouble("currentPrice", 91240.50)
            val pair = CryptoPair.fromSymbol(symbol)
            val dec = pair.priceDecimals

            val classic = root.optJSONObject("classicIndicators") ?: JSONObject()
            val ema20 = classic.optDouble("ema20", currentPrice)
            val ema50 = classic.optDouble("ema50", currentPrice)
            val ema200 = classic.optDouble("ema200", currentPrice)
            val emaStack = classic.optString("emaStack", "BULLISH_ALIGNED")
            val rsi14 = classic.optDouble("rsi14", 50.0)
            val vwap = classic.optDouble("vwapSession", currentPrice)
            val vwapDeltaStr = classic.optString("vwapDeltaPct", "0.0%").replace("%", "").replace("+", "").trim()
            val vwapDeltaPct = vwapDeltaStr.toDoubleOrNull() ?: if (vwap > 0) ((currentPrice - vwap) / vwap) * 100.0 else 0.0
            val stObj = classic.optJSONObject("supertrend_10_3")
            val supertrendBullish = stObj?.optString("direction", "BULLISH")?.equals("BULLISH", ignoreCase = true) ?: true
            val supertrendLine = stObj?.optDouble("supportLine", currentPrice * 0.99) ?: (currentPrice * 0.99)
            val atr14 = classic.optDouble("atr14", currentPrice * 0.003)

            val smcObj = root.optJSONObject("smartMoneyConcepts") ?: JSONObject()
            val breakObj = smcObj.optJSONObject("lastStructureBreak")
            val breakType = breakObj?.optString("type", "BULLISH_CHOCH") ?: "BULLISH_CHOCH"
            val breakLevel = breakObj?.optDouble("breakLevel", currentPrice) ?: currentPrice
            val candlesAgo = breakObj?.optInt("candlesAgo", 1) ?: 1
            val isStructBullish = breakType.contains("BULLISH", ignoreCase = true)
            val structureLabel = when (breakType.uppercase(Locale.US)) {
                "BULLISH_CHOCH" -> "Bullish CHoCH"
                "BEARISH_CHOCH" -> "Bearish CHoCH"
                "BULLISH_BOS" -> "Bullish BOS"
                "BEARISH_BOS" -> "Bearish BOS"
                else -> breakType.replace("_", " ")
            }

            val rangeObj = smcObj.optJSONObject("dealingRange")
            val swingLow = rangeObj?.optDouble("swingLow", currentPrice * 0.98) ?: (currentPrice * 0.98)
            val swingHigh = rangeObj?.optDouble("swingHigh", currentPrice * 1.02) ?: (currentPrice * 1.02)
            val eq50 = rangeObj?.optDouble("equilibrium50", (swingHigh + swingLow) / 2.0) ?: ((swingHigh + swingLow) / 2.0)
            val posPctRaw = rangeObj?.optDouble("currentPositionPct", 0.50) ?: 0.50
            val dealingPct = if (posPctRaw <= 1.0) posPctRaw * 100.0 else posPctRaw
            val isDiscount = currentPrice < eq50
            val zoneText = rangeObj?.optString("zone", "") ?: ""
            val dealingZoneLabel = if (zoneText.isNotBlank()) {
                String.format(Locale.US, "%s (%.0f%%)", zoneText, dealingPct)
            } else if (isDiscount) {
                String.format(Locale.US, "Discount (%.1f%%)", dealingPct)
            } else {
                String.format(Locale.US, "Premium (%.1f%%)", dealingPct)
            }

            val span = (swingHigh - swingLow).coerceAtLeast(1e-6)
            val (oteLow, oteHigh) = if (isStructBullish) {
                val f786 = swingHigh - 0.786 * span
                val f618 = swingHigh - 0.618 * span
                Pair(min(f786, f618), max(f786, f618))
            } else {
                val f618 = swingLow + 0.618 * span
                val f786 = swingLow + 0.786 * span
                Pair(min(f618, f786), max(f618, f786))
            }

            val sweepObj = smcObj.optJSONObject("recentLiquiditySweep")
            val swept = sweepObj?.optBoolean("swept", false) ?: false
            val sweepType = sweepObj?.optString("type", "NONE") ?: "NONE"
            val sweptLevel = sweepObj?.optDouble("sweptLevel", swingLow) ?: swingLow
            val wickExtreme = if (sweepObj?.has("wickLow") == true) {
                sweepObj.optDouble("wickLow", sweptLevel)
            } else {
                sweepObj?.optDouble("wickHigh", sweptLevel) ?: sweptLevel
            }
            val sweepStatusLabel = if (swept) {
                val tag = if (sweepType.contains("SSL")) "SSL Swept" else "BSL Swept"
                String.format(Locale.US, "%s ($%,.${dec}f Wick $%,.${dec}f)", tag, sweptLevel, wickExtreme)
            } else {
                String.format(Locale.US, "Intact (BSL $%,.${dec}f / SSL $%,.${dec}f)", swingHigh, swingLow)
            }

            var bullOb: OrderBlockZone? = null
            var bearOb: OrderBlockZone? = null
            val obArr = smcObj.optJSONArray("unmitigatedOrderBlocks")
            if (obArr != null) {
                for (i in 0 until obArr.length()) {
                    val o = obArr.getJSONObject(i)
                    val t = o.optString("type", "BULLISH_OB")
                    val low = o.optDouble("low", 0.0)
                    val high = o.optDouble("high", 0.0)
                    val mt = o.optDouble("meanThreshold", (low + high) / 2.0)
                    val distPct = (abs(currentPrice - mt) / currentPrice) * 100.0
                    val zone = OrderBlockZone(
                        isBullish = t.contains("BULL", ignoreCase = true),
                        low = low,
                        high = high,
                        meanThreshold = mt,
                        candleIndex = 190,
                        distancePercent = distPct
                    )
                    if (zone.isBullish && bullOb == null) bullOb = zone
                    if (!zone.isBullish && bearOb == null) bearOb = zone
                }
            }

            val primaryOb = listOfNotNull(bullOb, bearOb).minByOrNull { it.distancePercent }
            val nearestObSummary = primaryOb?.let {
                val pfx = if (it.isBullish) "Bull OB" else "Bear OB"
                String.format(Locale.US, "%s $%,.${dec}f", pfx, it.meanThreshold)
            } ?: String.format(Locale.US, "OB Support $%,.${dec}f", swingLow)

            var bullFvg: FairValueGap? = null
            var bearFvg: FairValueGap? = null
            val fvgArr = smcObj.optJSONArray("unmitigatedFVGs")
            if (fvgArr != null) {
                for (i in 0 until fvgArr.length()) {
                    val f = fvgArr.getJSONObject(i)
                    val t = f.optString("type", "BULLISH_FVG_BISI")
                    val bottom = f.optDouble("bottom", 0.0)
                    val top = f.optDouble("top", 0.0)
                    val ce50 = f.optDouble("consequentEncroachment50", (bottom + top) / 2.0)
                    val gap = FairValueGap(
                        isBullish = t.contains("BULL", ignoreCase = true) || t.contains("BISI", ignoreCase = true),
                        top = top,
                        bottom = bottom,
                        midpoint = ce50,
                        candleIndex = 192,
                        displacementRatio = 1.65
                    )
                    if (gap.isBullish && bullFvg == null) bullFvg = gap
                    if (!gap.isBullish && bearFvg == null) bearFvg = gap
                }
            }

            val primaryFvg = bullFvg ?: bearFvg
            val activeFvgSummary = primaryFvg?.let {
                val tag = if (it.isBullish) "BISI" else "SIBI"
                String.format(Locale.US, "$%,.${dec}f-$%,.${dec}f (%s)", it.bottom, it.top, tag)
            } ?: "Balanced (No Open FVG)"

            val smcTelemetry = SmcTelemetry(
                confirmedSwingHigh = swingHigh,
                confirmedSwingLow = swingLow,
                priorSwingHigh = swingHigh,
                priorSwingLow = swingLow,
                structureState = structureLabel,
                isStructureBullish = isStructBullish,
                liquiditySweepStatus = sweepStatusLabel,
                hasActiveSweep = swept,
                nearestBullishFvg = bullFvg,
                nearestBearishFvg = bearFvg,
                activeFvgSummary = activeFvgSummary,
                nearestBullishOb = bullOb,
                nearestBearishOb = bearOb,
                nearestObSummary = nearestObSummary,
                dealingRangeHigh50 = swingHigh,
                dealingRangeLow50 = swingLow,
                equilibriumPrice = eq50,
                dealingRangePct = dealingPct,
                dealingZoneLabel = dealingZoneLabel,
                isDiscountZone = isDiscount,
                oteLow = oteLow,
                oteHigh = oteHigh,
                isInOteZone = currentPrice in oteLow..oteHigh,
                structureBreakType = breakType,
                structureBreakLevel = breakLevel,
                structureBreakCandlesAgo = candlesAgo,
                sweepType = sweepType,
                sweptLevel = sweptLevel,
                sweepWickExtreme = wickExtreme
            )

            return QuantitativeTelemetry(
                symbol = symbol,
                timeframe = timeframe,
                currentPrice = currentPrice,
                ema20 = ema20,
                ema50 = ema50,
                ema200 = ema200,
                emaAlignment = emaStack.replace("_", " "),
                rsi14 = rsi14,
                rsiState = when {
                    rsi14 >= 70.0 -> "Overbought (>=70)"
                    rsi14 <= 30.0 -> "Oversold (<=30)"
                    rsi14 >= 52.0 -> "Bullish Momentum"
                    rsi14 <= 48.0 -> "Bearish Momentum"
                    else -> "Neutral Equilibrium"
                },
                vwap = vwap,
                vwapDeltaPercent = vwapDeltaPct,
                atr14 = atr14,
                supertrendValue = supertrendLine,
                supertrendBullish = supertrendBullish,
                swingHigh20 = swingHigh,
                swingLow20 = swingLow,
                nearestSupport = bullOb?.meanThreshold ?: swingLow,
                nearestResistance = bearOb?.meanThreshold ?: swingHigh,
                volumeSpikeRatio = 1.45,
                isInstitutionalVolumeSpike = true,
                smc = smcTelemetry
            )
        }

        val SAMPLE_BTC_15M_SMC_JSON = """
{
  "pair": "BTC/USD",
  "binanceSymbol": "BTCUSDT",
  "timeframe": "15m",
  "tradingStyle": "Intraday SMC + Momentum",
  "currentPrice": 91240.50,
  "classicIndicators": {
    "ema20": 91080.20,
    "ema50": 90810.00,
    "ema200": 89950.40,
    "emaStack": "BULLISH_ALIGNED",
    "rsi14": 56.4,
    "vwapSession": 90920.00,
    "vwapDeltaPct": "+0.35%",
    "supertrend_10_3": { "direction": "BULLISH", "supportLine": 90640.00 },
    "atr14": 285.40
  },
  "smartMoneyConcepts": {
    "lastStructureBreak": { "type": "BULLISH_CHOCH", "breakLevel": 90980.00, "candlesAgo": 6 },
    "dealingRange": {
      "swingLow": 89820.00,
      "swingHigh": 92450.00,
      "equilibrium50": 91135.00,
      "currentPositionPct": 0.54,
      "zone": "Equilibrium / Slight Premium"
    },
    "recentLiquiditySweep": {
      "swept": true,
      "type": "SSL_SWEEP",
      "sweptLevel": 89890.00,
      "wickLow": 89820.00,
      "rejectionConfirmed": true
    },
    "unmitigatedOrderBlocks": [
      { "type": "BULLISH_OB", "low": 90580.00, "high": 90790.00, "meanThreshold": 90685.00 },
      { "type": "BEARISH_OB", "low": 92180.00, "high": 92450.00, "meanThreshold": 92315.00 }
    ],
    "unmitigatedFVGs": [
      { "type": "BULLISH_FVG_BISI", "bottom": 90790.00, "top": 91040.00, "consequentEncroachment50": 90915.00 }
    ]
  }
}
        """.trimIndent()
    }
}

data class SmartMoneyBlueprint(
    val institutionalNarrative: String = "",
    val orderBlockCoordinates: String = "",
    val fairValueGapCoordinates: String = "",
    val liquiditySweepStatus: String = "",
    val dealingRangeAndOte: String = ""
)

data class AiSignalResult(
    val symbol: String,
    val displayPair: String,
    val timeframe: String,
    val tradingStyle: String,
    val signal: String,
    val confidence: Int,
    val marketRegime: String,
    val entryLow: Double,
    val entryHigh: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val riskRewardRatio: String,
    val indicatorConfluence: List<String>,
    val invalidationCondition: String,
    val smartMoneyBlueprint: SmartMoneyBlueprint = SmartMoneyBlueprint(),
    val generatedAtMs: Long = System.currentTimeMillis(),
    val signalId: String = "${symbol}_${timeframe}_$generatedAtMs",
    val isNonRepaintingConfirmed: Boolean = true,
    val optimalEntry: Double = (entryLow + entryHigh) / 2.0,
    val aiStrength: OrixProAiStrength = OrixProAiStrength(overallScore = confidence),
    val bullsVsBears: OrixProBullsVsBears = OrixProBullsVsBears(),
    val futureSwingsProjection: OrixProFutureSwingsProjection = OrixProFutureSwingsProjection(),
    val tradeLifecycle: OrixProTradeLifecycle = OrixProTradeLifecycle(
        entryZone = OrixProEntryZone(
            low = entryLow,
            high = entryHigh,
            optimalEntry = (entryLow + entryHigh) / 2.0
        ),
        initialStopLoss = stopLoss,
        trailingStopConfig = OrixProTrailingStopConfig(
            currentRecommendedTSL = stopLoss
        ),
        targets = OrixProTargetSet(
            tp1 = OrixProTargetMilestone(price = takeProfit1, rr = "1 : 1.5", allocationPct = 40, actionOnHit = "Move SL to Break-Even"),
            tp2 = OrixProTargetMilestone(price = takeProfit2, rr = "1 : 2.5", allocationPct = 40, actionOnHit = "Activate 1.5x ATR Trailing Stop"),
            tp3 = OrixProTargetMilestone(price = takeProfit3, rr = "1 : 4.0", allocationPct = 20, actionOnHit = "Full Close / Trail Under 5m Swing Low")
        )
    ),
    val fundamentalAndNewsContext: OrixProFundamentalAndNewsContext = OrixProFundamentalAndNewsContext()
)

data class ScannerPairRow(
    val pair: CryptoPair,
    val price: Double,
    val change24hPercent: Double,
    val volumeUsd24h: Double,
    val rsi14: Double,
    val emaTrend: String,
    val supertrendBullish: Boolean,
    val vwapDeltaPercent: Double,
    val smcStructure: String = "Neutral",
    val smcDealingZone: String = "Equilibrium (50%)"
)

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val strategyProfile: String = "Balanced",
    val defaultTimeframe: String = "15m",
    val accountBalanceUsd: Double = 10000.0,
    val riskPercentPerTrade: Double = 1.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedSignal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "BTCUSDT",
    val displayPair: String = "BTC/USD",
    val timeframe: String = "15m",
    val tradingStyle: String = "Scalp 5m/15m",
    val signal: String = "BUY",
    val confidence: Int = 80,
    val marketRegime: String = "Bullish Momentum",
    val entryLow: Double = 0.0,
    val entryHigh: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit1: Double = 0.0,
    val takeProfit2: Double = 0.0,
    val takeProfit3: Double = 0.0,
    val riskRewardRatio: String = "1 : 2.5",
    val indicatorConfluence: List<String> = emptyList(),
    val invalidationCondition: String = "",
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
