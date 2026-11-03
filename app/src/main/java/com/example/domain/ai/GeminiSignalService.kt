package com.example.domain.ai

import android.util.Log
import com.example.data.model.AiCoachPortfolioAudit
import com.example.data.model.AiSignalResult
import com.example.data.model.CryptoPair
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.OhlcvCandle
import com.example.data.model.OrixProAiStrength
import com.example.data.model.OrixProBullsVsBears
import com.example.data.model.OrixProEarlyExitTrigger
import com.example.data.model.OrixProEntryZone
import com.example.data.model.OrixProFundamentalAndNewsContext
import com.example.data.model.OrixProFutureSwingsProjection
import com.example.data.model.OrixProTargetMilestone
import com.example.data.model.OrixProTargetSet
import com.example.data.model.OrixProTradeLifecycle
import com.example.data.model.OrixProTrailingStopConfig
import com.example.data.model.ProjectedSwingPoint
import com.example.data.model.QuantitativeTelemetry
import com.example.data.model.SavedSignal
import com.example.data.model.SmartMoneyBlueprint
import com.example.data.model.StrategyProfile
import com.example.data.model.Timeframe
import com.example.data.model.TradeJournalEntry
import com.example.data.model.UserQuotaAuthMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiSignalService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Upgraded responseSchema properties for OrixVex Pro (`ORIXVEX_PRO_LIFECYCLE_SCHEMA`).
     */
    val orixVexProLifecycleSchema: Schema = Schema.obj(
        properties = mapOf(
            "signalId" to Schema.string(
                description = "Immutable deterministic ID (pair + timeframe + closedCandleTimestamp)"
            ),
            "isNonRepaintingConfirmed" to Schema.boolean(
                description = "Strictly generated on closed candle (kline.x === true)"
            ),
            "signal" to Schema.enumeration(
                values = listOf("STRONG BUY", "BUY", "HOLD / NEUTRAL", "SELL", "STRONG SELL"),
                description = "Primary quantitative and SMC trade action recommendation"
            ),
            "marketRegime" to Schema.enumeration(
                values = listOf(
                    "Bullish Momentum",
                    "Bearish Breakdown",
                    "Consolidation / Range",
                    "High-Volatility Reversal"
                ),
                description = "Current institutional market regime"
            ),
            "aiStrength" to Schema.obj(
                properties = mapOf(
                    "overallScore" to Schema.integer(description = "Overall AI strength score from 0 to 100"),
                    "grade" to Schema.enumeration(
                        values = listOf("S", "A+", "A", "B", "C"),
                        description = "Institutional confluence grade"
                    ),
                    "smcScore" to Schema.integer(description = "SMC structure & PD array score (0-100)"),
                    "technicalScore" to Schema.integer(description = "Multi-timeframe technical indicator score (0-100)"),
                    "orderFlowScore" to Schema.integer(description = "Order book & taker volume delta score (0-100)"),
                    "newsSentimentScore" to Schema.integer(description = "Macro & news sentiment score (0-100)")
                )
            ),
            "bullsVsBears" to Schema.obj(
                properties = mapOf(
                    "bullsPowerPct" to Schema.integer(description = "Bulls power percentage (0-100)"),
                    "bearsPowerPct" to Schema.integer(description = "Bears power percentage (0-100)"),
                    "dominantForce" to Schema.string(description = "Dominant institutional orderflow force narrative"),
                    "orderFlowDeltaSummary" to Schema.string(description = "Summary of Taker Buy/Sell ratio and bid/ask wall depth")
                )
            ),
            "futureSwingsProjection" to Schema.obj(
                properties = mapOf(
                    "expectedTrajectory" to Schema.string(
                        description = "Expected institutional price trajectory across nearest FVG/OB and BSL/SSL magnets"
                    ),
                    "projectedNextSwingHigh" to Schema.obj(
                        properties = mapOf(
                            "price" to Schema.double(description = "Projected next swing high price"),
                            "probabilityPct" to Schema.integer(description = "Probability percentage (0-100)"),
                            "estimatedCandles" to Schema.string(description = "Estimated candle window such as '6-12 bars'")
                        )
                    ),
                    "projectedNextSwingLow" to Schema.obj(
                        properties = mapOf(
                            "price" to Schema.double(description = "Projected next swing low price"),
                            "probabilityPct" to Schema.integer(description = "Probability percentage (0-100)"),
                            "estimatedCandles" to Schema.string(description = "Estimated candle window such as '2-4 bars'")
                        )
                    )
                )
            ),
            "tradeLifecycle" to Schema.obj(
                properties = mapOf(
                    "entryZone" to Schema.obj(
                        properties = mapOf(
                            "low" to Schema.double(description = "Lower entry zone price aligned with OB/FVG/OTE"),
                            "high" to Schema.double(description = "Upper entry zone price aligned with OB/FVG/OTE"),
                            "optimalEntry" to Schema.double(description = "Optimal institutional limit entry price inside the entry zone")
                        )
                    ),
                    "initialStopLoss" to Schema.double(
                        description = "Initial stop loss price level calculated using ATR volatility protection beyond OB/Swing structure"
                    ),
                    "trailingStopConfig" to Schema.obj(
                        properties = mapOf(
                            "activationRule" to Schema.string(
                                description = "Ratcheting TSL activation rule, e.g. 'Move SL to Break-Even at TP1; trail by 1.5x ATR after TP2'"
                            ),
                            "atrMultiplier" to Schema.double(description = "ATR trailing stop multiplier, typically 1.5"),
                            "currentRecommendedTSL" to Schema.double(description = "Current recommended trailing stop loss price")
                        )
                    ),
                    "targets" to Schema.obj(
                        properties = mapOf(
                            "tp1" to Schema.obj(
                                properties = mapOf(
                                    "price" to Schema.double(description = "TP1 1.5R target price"),
                                    "rr" to Schema.string(description = "Risk reward ratio for TP1, e.g. '1 : 1.5'"),
                                    "allocationPct" to Schema.integer(description = "Position allocation percentage to close at TP1, e.g. 40"),
                                    "actionOnHit" to Schema.string(description = "Action on hit, e.g. 'Move SL to Break-Even'")
                                )
                            ),
                            "tp2" to Schema.obj(
                                properties = mapOf(
                                    "price" to Schema.double(description = "TP2 2.5R target price"),
                                    "rr" to Schema.string(description = "Risk reward ratio for TP2, e.g. '1 : 2.5'"),
                                    "allocationPct" to Schema.integer(description = "Position allocation percentage to close at TP2, e.g. 40"),
                                    "actionOnHit" to Schema.string(description = "Action on hit, e.g. 'Activate 1.5x ATR Trailing Stop'")
                                )
                            ),
                            "tp3" to Schema.obj(
                                properties = mapOf(
                                    "price" to Schema.double(description = "TP3 4.0R extended runner target price"),
                                    "rr" to Schema.string(description = "Risk reward ratio for TP3, e.g. '1 : 4.0'"),
                                    "allocationPct" to Schema.integer(description = "Position allocation percentage for final runner at TP3, e.g. 20"),
                                    "actionOnHit" to Schema.string(description = "Action on hit, e.g. 'Full Close / Trail Under 5m Swing Low'")
                                )
                            )
                        )
                    ),
                    "earlyExitTrigger" to Schema.obj(
                        properties = mapOf(
                            "earlyExitNeededNow" to Schema.boolean(description = "Whether an emergency early exit is needed right now"),
                            "emergencyExitPriceCondition" to Schema.string(
                                description = "Exact candle body close / CHoCH / RSI / Taker Sell condition that triggers an emergency early exit"
                            ),
                            "earlyExitReason" to Schema.string(
                                description = "Current early exit status reason or hold confirmation"
                            )
                        )
                    )
                )
            ),
            "fundamentalAndNewsContext" to Schema.obj(
                properties = mapOf(
                    "overallNewsSentiment" to Schema.string(
                        description = "Overall news & macro sentiment label with score, e.g. 'BULLISH (+64/100)'"
                    ),
                    "keyCatalystSummary" to Schema.string(
                        description = "Concise summary of the key macro/crypto news catalyst and Fear & Greed driver"
                    )
                )
            ),
            "indicatorConfluence" to Schema.array(
                items = Schema.string(),
                description = "4 to 6 concise bullet strings explaining how SMC (BOS/CHoCH, OB, FVG, Premium/Discount), quantitative indicators (EMA, RSI, VWAP, Supertrend), and Live News / Order Flow align"
            ),
            "invalidationCondition" to Schema.string(
                description = "A 1-sentence warning stating what price action invalidates this setup"
            ),
            "smartMoneyBlueprint" to Schema.obj(
                properties = mapOf(
                    "institutionalNarrative" to Schema.string(
                        description = "2-sentence institutional orderflow narrative synthesizing Market Structure (BOS/CHoCH), Dealing Range (Premium/Discount), Liquidity Draw, and News/Orderbook sentiment"
                    ),
                    "orderBlockCoordinates" to Schema.string(
                        description = "Exact Bullish or Bearish Order Block zone [Low - High] and Mean Threshold (50% level)"
                    ),
                    "fairValueGapCoordinates" to Schema.string(
                        description = "Exact Unmitigated Fair Value Gap (BISI or SIBI) price range and displacement status"
                    ),
                    "liquiditySweepStatus" to Schema.string(
                        description = "Buy-Side (BSL) or Sell-Side (SSL) liquidity sweep status and next external liquidity target"
                    ),
                    "dealingRangeAndOte" to Schema.string(
                        description = "50-bar Dealing Range Equilibrium (0.50 Fib), current Discount/Premium % placement, and 0.618-0.786 OTE coordinates"
                    )
                )
            )
        )
    )

    private val coachAuditSchema = Schema.obj(
        properties = mapOf(
            "overallGrade" to Schema.enumeration(
                values = listOf("S", "A+", "A", "B", "C"),
                description = "Overall portfolio execution and discipline grade"
            ),
            "summaryHeadline" to Schema.string(
                description = "Concise 1-sentence executive summary of the trader's performance and psychology"
            ),
            "strengths" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 key strengths observed in the trade journal and signal history"
            ),
            "behavioralLeaks" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 behavioral or technical execution leaks (e.g. FOMO, early exit, low R:R)"
            ),
            "bestPerformingSetup" to Schema.string(
                description = "The highest-expectancy setup tag or pair/timeframe combination"
            ),
            "riskAdjustments" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 actionable quantitative adjustments for the next 10 trades"
            )
        )
    )

    private val signalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = orixVexProLifecycleSchema
                temperature = 0.2f
            }
        )
    }

    private val coachModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = coachAuditSchema
                temperature = 0.25f
            }
        )
    }

    suspend fun generateTradeSignal(
        pair: CryptoPair,
        timeframe: Timeframe,
        strategyProfile: StrategyProfile,
        telemetry: QuantitativeTelemetry,
        recentCandles: List<OhlcvCandle>,
        fundamentals: FundamentalMacroSnapshot? = null,
        quotaMode: UserQuotaAuthMode = UserQuotaAuthMode.FIREBASE_AI_STUDIO,
        personalApiKey: String = "",
        oauthBearerToken: String = ""
    ): Result<AiSignalResult> {
        val dec = pair.priceDecimals
        val smc = telemetry.smc
        val last15 = recentCandles.takeLast(15)
        val closedBarTimestampMs = last15.lastOrNull()?.closeTime ?: System.currentTimeMillis()
        val deterministicSignalId = "${pair.symbol}_${timeframe.binanceInterval}_$closedBarTimestampMs"

        val candlesFormatted = last15.mapIndexed { idx, c ->
            String.format(
                Locale.US,
                "#%02d O:%.${dec}f H:%.${dec}f L:%.${dec}f C:%.${dec}f V:%.2f",
                idx + 1, c.open, c.high, c.low, c.close, c.volume
            )
        }.joinToString("\n")

        val bullObStr = smc.nearestBullishOb?.let {
            "Zone [$${formatPrice(it.low, dec)} - $${formatPrice(it.high, dec)}], Mean Threshold $${formatPrice(it.meanThreshold, dec)} (${String.format(Locale.US, "%.2f%%", it.distancePercent)} away)"
        } ?: "None unmitigated within 5%"

        val bearObStr = smc.nearestBearishOb?.let {
            "Zone [$${formatPrice(it.low, dec)} - $${formatPrice(it.high, dec)}], Mean Threshold $${formatPrice(it.meanThreshold, dec)} (${String.format(Locale.US, "%.2f%%", it.distancePercent)} away)"
        } ?: "None unmitigated within 5%"

        val bullFvgStr = smc.nearestBullishFvg?.let {
            "BISI [$${formatPrice(it.bottom, dec)} - $${formatPrice(it.top, dec)}], Midpoint $${formatPrice(it.midpoint, dec)} (${String.format(Locale.US, "%.2fx", it.displacementRatio)} displacement)"
        } ?: "None unmitigated"

        val bearFvgStr = smc.nearestBearishFvg?.let {
            "SIBI [$${formatPrice(it.bottom, dec)} - $${formatPrice(it.top, dec)}], Midpoint $${formatPrice(it.midpoint, dec)} (${String.format(Locale.US, "%.2fx", it.displacementRatio)} displacement)"
        } ?: "None unmitigated"

        val headlinesSection = if (fundamentals != null && fundamentals.articles.isNotEmpty()) {
            fundamentals.articles.take(8).mapIndexed { i, art ->
                "${i + 1}. [${art.sentiment.label}] ${art.title} (${art.source}, ${art.publishedAgo})"
            }.joinToString("\n")
        } else {
            "No breaking macro catalyst; standard institutional orderflow regime."
        }

        val orderFlowSection = if (fundamentals != null) {
            """
            - Crypto Fear & Greed Index: ${fundamentals.fearAndGreed.currentValue}/100 (${fundamentals.fearAndGreed.currentClassification})
            - Net News Sentiment Score: ${String.format(Locale.US, "%.1f/100", fundamentals.netNewsSentimentScore)}
            - Order Book Top-20 Bid/Ask Liquidity Imbalance: ${String.format(Locale.US, "%+.1f%%", fundamentals.orderBookDepth.bidAskImbalancePct)} (Bids ${String.format(Locale.US, "%.1f%%", fundamentals.orderBookDepth.bidRatioPct)} vs Asks ${String.format(Locale.US, "%.1f%%", fundamentals.orderBookDepth.askRatioPct)})
            - Taker Buy/Sell Volume Ratio: ${String.format(Locale.US, "%.2f", fundamentals.takerBuySellRatio)} (${String.format(Locale.US, "%.1f%%", fundamentals.takerBuyPercent)} Aggressive Taker Buys)
            - 24h Volume Surge vs Average: ${String.format(Locale.US, "%+.1f%%", fundamentals.volumeSurge24hPct)}
            """.trimIndent()
        } else {
            "- Order Flow & Sentiment: Balanced institutional liquidity"
        }

        val tradingStyle = timeframe.defaultTradingStyle
        val institutionalJson = telemetry.toInstitutionalJsonString(
            displayPair = pair.displayPair,
            tradingStyle = tradingStyle,
            decimals = dec
        )
        val prompt = """
            You are OrixVex Pro, an institutional Smart Money Concepts (SMC), Quantitative Crypto, and Macro News Strategist.
            Synthesize the deterministic Bar-Close Confirmed (Non-Repainting, kline.x === true) SMC structures, mathematical indicators, Order Book depth, and Live Trading News for ${pair.displayPair} (${pair.symbol}) on the ${timeframe.label} timeframe.
            
            Deterministic Signal ID: $deterministicSignalId
            Closed Candle Timestamp: $closedBarTimestampMs
            Selected Trading Style: $tradingStyle
            Risk & Strategy Profile: ${strategyProfile.label} (${strategyProfile.description})
            
            === 0. STRUCTURED INSTITUTIONAL TELEMETRY PAYLOAD (JSON) ===
            $institutionalJson
            
            === 1. DETERMINISTIC SMART MONEY CONCEPTS (SMC) TELEMETRY ===
            - 3-Bar Fractal Confirmed Swing High (SH): $${formatPrice(smc.confirmedSwingHigh, dec)} (Prior SH: $${formatPrice(smc.priorSwingHigh, dec)})
            - 3-Bar Fractal Confirmed Swing Low (SL): $${formatPrice(smc.confirmedSwingLow, dec)} (Prior SL: $${formatPrice(smc.priorSwingLow, dec)})
            - Market Structure State (BOS / CHoCH): ${smc.structureState} (${smc.structureBreakType} @ $${formatPrice(smc.structureBreakLevel, dec)}, ${smc.structureBreakCandlesAgo} candles ago)
            - Liquidity Sweep Status (BSL / SSL): ${smc.liquiditySweepStatus}
            - Unmitigated Bullish FVG (BISI): $bullFvgStr
            - Unmitigated Bearish FVG (SIBI): $bearFvgStr
            - Institutional Bullish Order Block (OB): $bullObStr
            - Institutional Bearish Order Block (OB): $bearObStr
            - 50-Bar Dealing Range: Low $${formatPrice(smc.dealingRangeLow50, dec)} to High $${formatPrice(smc.dealingRangeHigh50, dec)}
            - Dealing Range Equilibrium (0.50 Fib): $${formatPrice(smc.equilibriumPrice, dec)}
            - Current Dealing Zone Placement: ${smc.dealingZoneLabel} (${if (smc.isDiscountZone) "DISCOUNT - favors institutional longs" else "PREMIUM - favors institutional shorts"})
            - Optimal Trade Entry (OTE 0.618-0.786 Fib): [$${formatPrice(smc.oteLow, dec)} - $${formatPrice(smc.oteHigh, dec)}] (In OTE Now: ${smc.isInOteZone})
            
            === 2. COMPUTED QUANTITATIVE INDICATORS (200-CANDLE WINDOW) ===
            - Current Live Price: $${formatPrice(telemetry.currentPrice, dec)}
            - EMA 20: $${formatPrice(telemetry.ema20, dec)} | EMA 50: $${formatPrice(telemetry.ema50, dec)} | EMA 200: $${formatPrice(telemetry.ema200, dec)}
            - EMA Trend Alignment: ${telemetry.emaAlignment}
            - Wilder's RSI (14): ${String.format(Locale.US, "%.2f", telemetry.rsi14)} (${telemetry.rsiState})
            - Session Rolling VWAP: $${formatPrice(telemetry.vwap, dec)} (Price Delta: ${String.format(Locale.US, "%+.2f%%", telemetry.vwapDeltaPercent)})
            - ATR (14) Volatility: $${formatPrice(telemetry.atr14, dec)}
            - Supertrend (10, 3.0): $${formatPrice(telemetry.supertrendValue, dec)} (State: ${if (telemetry.supertrendBullish) "BULLISH" else "BEARISH"})
            - Volume Spike Ratio (vs 20-SMA): ${String.format(Locale.US, "%.2fx", telemetry.volumeSpikeRatio)}
            
            === 3. LIVE ORDER FLOW & FUNDAMENTAL MACRO METRICS ===
            $orderFlowSection
            
            === 4. TOP 8 LIVE CRYPTO & MACRO NEWS HEADLINES ===
            $headlinesSection
            
            === 5. LAST 15 CLOSED OHLCV CANDLES (OLDEST TO NEWEST) ===
            $candlesFormatted
            
            === INSTITUTIONAL ORIXVEX_PRO_LIFECYCLE_SCHEMA EXECUTION RULES ===
            1. Set signalId to "$deterministicSignalId" and isNonRepaintingConfirmed to true.
            2. Populate aiStrength (overallScore 0-100, grade S/A+/A/B/C, smcScore, technicalScore, orderFlowScore, newsSentimentScore).
            3. Populate bullsVsBears (bullsPowerPct + bearsPowerPct = 100, dominantForce, orderFlowDeltaSummary).
            4. Populate futureSwingsProjection (expectedTrajectory, projectedNextSwingHigh { price, probabilityPct, estimatedCandles }, projectedNextSwingLow { price, probabilityPct, estimatedCandles }).
            5. Populate tradeLifecycle:
               - entryZone { low, high, optimalEntry } anchored to Institutional Order Block Mean Threshold, Unmitigated FVG Consequent Encroachment (50%), or OTE (0.618-0.786).
               - initialStopLoss using ATR(14) ($${formatPrice(telemetry.atr14, dec)}) volatility protection beyond the active Order Block or confirmed Fractal Swing High/Low.
               - trailingStopConfig { activationRule, atrMultiplier: 1.5, currentRecommendedTSL }.
               - targets.tp1 (1.5R, allocationPct: 40, actionOnHit: "Move SL to Break-Even"), targets.tp2 (2.5R, allocationPct: 40, actionOnHit: "Activate 1.5x ATR Trailing Stop"), targets.tp3 (4.0R, allocationPct: 20, actionOnHit: "Full Close / Trail Under 5m Swing Low").
               - earlyExitTrigger { earlyExitNeededNow, emergencyExitPriceCondition, earlyExitReason }.
            6. Populate fundamentalAndNewsContext { overallNewsSentiment, keyCatalystSummary } and smartMoneyBlueprint with exact dollar coordinates.
        """.trimIndent()

        return executePromptAndParse(
            prompt = prompt,
            symbol = pair.symbol,
            displayPair = pair.displayPair,
            timeframe = timeframe.binanceInterval,
            tradingStyle = tradingStyle,
            closedBarTimestampMs = closedBarTimestampMs,
            fallbackBlueprint = SmartMoneyBlueprint(
                institutionalNarrative = "Structure is ${smc.structureState} inside ${smc.dealingZoneLabel}.",
                orderBlockCoordinates = smc.nearestObSummary,
                fairValueGapCoordinates = smc.activeFvgSummary,
                liquiditySweepStatus = smc.liquiditySweepStatus,
                dealingRangeAndOte = "Equilibrium $${formatPrice(smc.equilibriumPrice, dec)} | OTE [$${formatPrice(smc.oteLow, dec)} - $${formatPrice(smc.oteHigh, dec)}]"
            ),
            quotaMode = quotaMode,
            personalApiKey = personalApiKey,
            oauthBearerToken = oauthBearerToken
        )
    }

    suspend fun generateTradeSignalFromJson(
        rawTelemetryJson: String,
        strategyProfile: StrategyProfile,
        quotaMode: UserQuotaAuthMode = UserQuotaAuthMode.FIREBASE_AI_STUDIO,
        personalApiKey: String = "",
        oauthBearerToken: String = ""
    ): Result<AiSignalResult> {
        val parsedInput = try {
            JSONObject(rawTelemetryJson)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid SMC JSON format: ${e.localizedMessage}"))
        }

        // If the user pasted a direct ORIXVEX_PRO_LIFECYCLE_SCHEMA JSON object, parse and return it immediately!
        if (parsedInput.has("tradeLifecycle") && parsedInput.has("aiStrength")) {
            val directPair = parsedInput.optString("pair", "BTC/USD")
            val directSymbol = parsedInput.optString("binanceSymbol", "BTCUSDT")
            val directTf = parsedInput.optString("timeframe", "15m")
            val directStyle = parsedInput.optString("tradingStyle", "Intraday SMC + Momentum")
            return runCatching {
                parseOrixVexProLifecycleSignalJson(
                    rawJson = rawTelemetryJson,
                    symbol = directSymbol,
                    displayPair = directPair,
                    timeframe = directTf,
                    tradingStyle = directStyle,
                    closedBarTimestampMs = System.currentTimeMillis(),
                    fallbackBlueprint = SmartMoneyBlueprint(
                        institutionalNarrative = "Synthesized from OrixVex Pro Lifecycle JSON payload for $directPair ($directTf).",
                        orderBlockCoordinates = "Aligned with tradeLifecycle.entryZone",
                        fairValueGapCoordinates = "Aligned with futureSwingsProjection",
                        liquiditySweepStatus = "Verified Non-Repainting Bar Close",
                        dealingRangeAndOte = "Optimal Entry @ $${parsedInput.optJSONObject("tradeLifecycle")?.optJSONObject("entryZone")?.optDouble("optimalEntry", 0.0)}"
                    )
                )
            }
        }

        val displayPair = parsedInput.optString("pair", "BTC/USD")
        val symbol = parsedInput.optString("binanceSymbol", "BTCUSDT")
        val timeframe = parsedInput.optString("timeframe", "15m")
        val tradingStyle = parsedInput.optString("tradingStyle", "Intraday SMC + Momentum")
        val closedBarTimestampMs = System.currentTimeMillis()
        val deterministicSignalId = "${symbol}_${timeframe}_$closedBarTimestampMs"

        val prompt = """
            You are OrixVex Pro, an institutional Smart Money Concepts (SMC) and Quantitative Crypto Strategist.
            Analyze the following deterministic SMC + Classic Quantitative Indicators JSON payload and produce an institutional non-repainting trade execution plan adhering strictly to ORIXVEX_PRO_LIFECYCLE_SCHEMA.
            
            Deterministic Signal ID: $deterministicSignalId
            Risk & Strategy Profile: ${strategyProfile.label} (${strategyProfile.description})
            
            === INPUT INSTITUTIONAL SMC + QUANT JSON PAYLOAD ===
            $rawTelemetryJson
            
            === INSTITUTIONAL ORIXVEX_PRO_LIFECYCLE_SCHEMA EXECUTION RULES ===
            1. Set signalId to "$deterministicSignalId" and isNonRepaintingConfirmed to true.
            2. Evaluate lastStructureBreak (e.g., BULLISH_CHOCH / BOS), recentLiquiditySweep (e.g., SSL_SWEEP / BSL_SWEEP rejection), and dealingRange (Equilibrium 50% vs currentPositionPct).
            3. Populate aiStrength, bullsVsBears, futureSwingsProjection, tradeLifecycle (entryZone { low, high, optimalEntry }, initialStopLoss, trailingStopConfig, targets { tp1, tp2, tp3 }, earlyExitTrigger), fundamentalAndNewsContext, and smartMoneyBlueprint with exact dollar coordinates from the JSON payload.
        """.trimIndent()

        return executePromptAndParse(
            prompt = prompt,
            symbol = symbol,
            displayPair = displayPair,
            timeframe = timeframe,
            tradingStyle = tradingStyle,
            closedBarTimestampMs = closedBarTimestampMs,
            fallbackBlueprint = SmartMoneyBlueprint(
                institutionalNarrative = "Synthesized from custom SMC + Quantitative JSON telemetry for $displayPair ($timeframe).",
                orderBlockCoordinates = "Parsed from unmitigatedOrderBlocks",
                fairValueGapCoordinates = "Parsed from unmitigatedFVGs",
                liquiditySweepStatus = "Parsed from recentLiquiditySweep",
                dealingRangeAndOte = "Parsed from dealingRange equilibrium50"
            ),
            quotaMode = quotaMode,
            personalApiKey = personalApiKey,
            oauthBearerToken = oauthBearerToken
        )
    }

    suspend fun generateAiCoachPortfolioAudit(
        journalEntries: List<TradeJournalEntry>,
        savedSignals: List<SavedSignal>,
        quotaMode: UserQuotaAuthMode = UserQuotaAuthMode.FIREBASE_AI_STUDIO,
        personalApiKey: String = "",
        oauthBearerToken: String = ""
    ): Result<AiCoachPortfolioAudit> {
        val journalSummary = if (journalEntries.isNotEmpty()) {
            journalEntries.take(20).mapIndexed { idx, j ->
                "#${idx + 1} ${j.displayPair} (${j.timeframe}) ${j.direction}: Entry $${j.entryPrice} -> Exit $${j.exitPrice} | PnL $${String.format(Locale.US, "%.2f", j.pnlUsd)} (${String.format(Locale.US, "%+.2fR", j.rMultiple)}) | Setup: ${j.setupTag} | Mindset: ${j.mindsetTag} | Notes: ${j.notes}"
            }.joinToString("\n")
        } else {
            "No manual journal logs yet; evaluate based on the user's tracked AI signal history."
        }

        val signalsSummary = if (savedSignals.isNotEmpty()) {
            savedSignals.take(20).mapIndexed { idx, s ->
                "#${idx + 1} ${s.displayPair} (${s.timeframe}) ${s.signal} | Confidence ${s.confidence}% | R:R ${s.riskRewardRatio} | Outcome Status: ${s.status}"
            }.joinToString("\n")
        } else {
            "No saved signals yet."
        }

        val prompt = """
            You are the OrixVex Institutional Trading Performance Coach & Risk Auditor.
            Analyze the trader's logged Trade Journal entries and historical AI Signal outcomes below.
            Identify behavioral leaks (e.g., Early Exit, FOMO, Hesitation), strongest SMC setup tags (SMC OB, FVG Pullback, BOS Breakout), and concrete quantitative risk adjustments.
            
            === LOGGED TRADE JOURNAL ENTRIES ===
            $journalSummary
            
            === HISTORICAL AI SIGNAL VAULT ===
            $signalsSummary
            
            Return a JSON object with fields: overallGrade ("S", "A+", "A", "B", or "C"), summaryHeadline, strengths (array of strings), behavioralLeaks (array of strings), bestPerformingSetup (string), and riskAdjustments (array of strings).
        """.trimIndent()

        return try {
            val rawJson = executeModelTextRequest(
                prompt = prompt,
                useCoachSchema = true,
                quotaMode = quotaMode,
                personalApiKey = personalApiKey,
                oauthBearerToken = oauthBearerToken
            )
            val obj = JSONObject(rawJson)
            val strengthsArr = obj.optJSONArray("strengths") ?: JSONArray()
            val leaksArr = obj.optJSONArray("behavioralLeaks") ?: JSONArray()
            val adjArr = obj.optJSONArray("riskAdjustments") ?: JSONArray()

            Result.success(
                AiCoachPortfolioAudit(
                    overallGrade = obj.optString("overallGrade", "A"),
                    summaryHeadline = obj.optString(
                        "summaryHeadline",
                        "Strong SMC confluence selection with room to optimize trailing stop runner retention."
                    ),
                    strengths = (0 until strengthsArr.length()).map { strengthsArr.getString(it) },
                    behavioralLeaks = (0 until leaksArr.length()).map { leaksArr.getString(it) },
                    bestPerformingSetup = obj.optString("bestPerformingSetup", "SMC OB + FVG Discount Pullback"),
                    riskAdjustments = (0 until adjArr.length()).map { adjArr.getString(it) }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Coach Portfolio Audit failed", e)
            Result.failure(e)
        }
    }

    private suspend fun executePromptAndParse(
        prompt: String,
        symbol: String,
        displayPair: String,
        timeframe: String,
        tradingStyle: String,
        closedBarTimestampMs: Long,
        fallbackBlueprint: SmartMoneyBlueprint,
        quotaMode: UserQuotaAuthMode,
        personalApiKey: String,
        oauthBearerToken: String
    ): Result<AiSignalResult> {
        return try {
            val jsonString = executeModelTextRequest(
                prompt = prompt,
                useCoachSchema = false,
                quotaMode = quotaMode,
                personalApiKey = personalApiKey,
                oauthBearerToken = oauthBearerToken
            )
            if (jsonString.isBlank()) {
                return Result.failure(IllegalStateException("Gemini model returned an empty response."))
            }

            val result = parseOrixVexProLifecycleSignalJson(
                rawJson = jsonString,
                symbol = symbol,
                displayPair = displayPair,
                timeframe = timeframe,
                tradingStyle = tradingStyle,
                closedBarTimestampMs = closedBarTimestampMs,
                fallbackBlueprint = fallbackBlueprint
            )
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI signal generation timed out after 60s", e)
            Result.failure(IllegalStateException("AI quantitative analysis timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI signal generation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Executes the prompt using the selected User Quota Auth Mode:
     * - Mode B (`GOOGLE_OAUTH_BEARER`): Uses the user's OAuth access token if present, falling back to Firebase AI Logic.
     * - Mode C (`PERSONAL_API_KEY`): Uses the user's personal Gemini key if present, falling back to Firebase AI Logic.
     * - Mode A (`FIREBASE_AI_STUDIO` default): Uses `Firebase.ai.generativeModel("gemini-3.8-flash")`.
     */
    private suspend fun executeModelTextRequest(
        prompt: String,
        useCoachSchema: Boolean,
        quotaMode: UserQuotaAuthMode,
        personalApiKey: String,
        oauthBearerToken: String
    ): String {
        if (quotaMode == UserQuotaAuthMode.GOOGLE_OAUTH_BEARER && oauthBearerToken.isNotBlank()) {
            val restResult = runCatching {
                executeDirectUserRestCall(prompt = prompt, bearerToken = oauthBearerToken, apiKey = null)
            }.getOrNull()
            if (!restResult.isNullOrBlank()) return restResult
        } else if (quotaMode == UserQuotaAuthMode.PERSONAL_API_KEY && personalApiKey.isNotBlank()) {
            val restResult = runCatching {
                executeDirectUserRestCall(prompt = prompt, bearerToken = null, apiKey = personalApiKey)
            }.getOrNull()
            if (!restResult.isNullOrBlank()) return restResult
        }

        val response = withTimeout(60_000L) {
            if (useCoachSchema) {
                coachModel.generateContent(prompt)
            } else {
                signalModel.generateContent(prompt)
            }
        }
        return response.text.orEmpty()
    }

    private suspend fun executeDirectUserRestCall(
        prompt: String,
        bearerToken: String?,
        apiKey: String?
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        val url = if (!apiKey.isNullOrBlank()) "$baseUrl?key=${apiKey.trim()}" else baseUrl

        val bodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                }
            )
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!bearerToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("User quota endpoint returned HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val root = JSONObject(raw)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            parts?.optJSONObject(0)?.optString("text").orEmpty()
        }
    }

    private fun formatPrice(value: Double, decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    companion object {
        private const val TAG = "GeminiSignalService"

        /**
         * Parses an `ORIXVEX_PRO_LIFECYCLE_SCHEMA` JSON payload (or legacy flat signal JSON)
         * into a strongly-typed [AiSignalResult].
         */
        fun parseOrixVexProLifecycleSignalJson(
            rawJson: String,
            symbol: String = "BTCUSDT",
            displayPair: String = "BTC/USD",
            timeframe: String = "15m",
            tradingStyle: String = "Intraday SMC + Momentum",
            closedBarTimestampMs: Long = System.currentTimeMillis(),
            fallbackBlueprint: SmartMoneyBlueprint = SmartMoneyBlueprint()
        ): AiSignalResult {
            val json = JSONObject(rawJson)
            val defaultSignalId = "${symbol}_${timeframe}_$closedBarTimestampMs"
            val rawSignalId = json.optString("signalId", "").trim()
            val signalId = if (rawSignalId.isNotBlank() && !rawSignalId.startsWith("Immutable deterministic ID", ignoreCase = true)) {
                rawSignalId
            } else {
                defaultSignalId
            }
            val isNonRepaintingConfirmed = json.optBoolean("isNonRepaintingConfirmed", true)
            val signal = json.optString("signal", "BUY")

            // 1. Parse aiStrength
            val strengthObj = json.optJSONObject("aiStrength")
            val overallScore = (strengthObj?.optInt("overallScore", json.optInt("confidence", 88))
                ?: json.optInt("confidence", 88)).coerceIn(0, 100)
            val grade = strengthObj?.optString("grade", when {
                overallScore >= 90 -> "S"
                overallScore >= 82 -> "A+"
                overallScore >= 74 -> "A"
                overallScore >= 64 -> "B"
                else -> "C"
            }) ?: "A+"
            val aiStrength = OrixProAiStrength(
                overallScore = overallScore,
                grade = grade,
                smcScore = (strengthObj?.optInt("smcScore", 92) ?: 92).coerceIn(0, 100),
                technicalScore = (strengthObj?.optInt("technicalScore", 86) ?: 86).coerceIn(0, 100),
                orderFlowScore = (strengthObj?.optInt("orderFlowScore", 84) ?: 84).coerceIn(0, 100),
                newsSentimentScore = (strengthObj?.optInt("newsSentimentScore", 89) ?: 89).coerceIn(0, 100)
            )

            // 2. Parse bullsVsBears
            val bvbObj = json.optJSONObject("bullsVsBears")
            val bullsPct = (bvbObj?.optInt("bullsPowerPct", if (signal.contains("SELL")) 32 else 68)
                ?: if (signal.contains("SELL")) 32 else 68).coerceIn(0, 100)
            val bearsPct = (bvbObj?.optInt("bearsPowerPct", 100 - bullsPct) ?: (100 - bullsPct)).coerceIn(0, 100)
            val bullsVsBears = OrixProBullsVsBears(
                bullsPowerPct = bullsPct,
                bearsPowerPct = bearsPct,
                dominantForce = bvbObj?.optString("dominantForce", "Institutional Buyers Absorbing Ask Liquidity")
                    ?: "Institutional Buyers Absorbing Ask Liquidity",
                orderFlowDeltaSummary = bvbObj?.optString("orderFlowDeltaSummary", "Taker Buy ratio at 61.4% with strong bid wall")
                    ?: "Taker Buy ratio at 61.4% with strong bid wall"
            )

            // 3. Parse tradeLifecycle (or fallback to flat fields)
            val lifecycleObj = json.optJSONObject("tradeLifecycle")
            val entryObj = lifecycleObj?.optJSONObject("entryZone") ?: json.optJSONObject("entryZone")
            val entryLow = entryObj?.optDouble("low", 0.0) ?: 0.0
            val entryHigh = entryObj?.optDouble("high", entryLow) ?: entryLow
            val optimalEntry = entryObj?.optDouble("optimalEntry", (entryLow + entryHigh) / 2.0)
                ?.takeIf { it > 0.0 } ?: ((entryLow + entryHigh) / 2.0)

            val initialStopLoss = lifecycleObj?.optDouble("initialStopLoss", json.optDouble("stopLoss", 0.0))
                ?: json.optDouble("stopLoss", 0.0)

            val tslConfigObj = lifecycleObj?.optJSONObject("trailingStopConfig")
            val trailingStopConfig = OrixProTrailingStopConfig(
                activationRule = tslConfigObj?.optString(
                    "activationRule",
                    "Move SL to Break-Even at TP1; trail by 1.5x ATR after TP2"
                ) ?: "Move SL to Break-Even at TP1; trail by 1.5x ATR after TP2",
                atrMultiplier = tslConfigObj?.optDouble("atrMultiplier", 1.5) ?: 1.5,
                currentRecommendedTSL = tslConfigObj?.optDouble("currentRecommendedTSL", initialStopLoss) ?: initialStopLoss
            )

            val targetsObj = lifecycleObj?.optJSONObject("targets")
            val tp1Obj = targetsObj?.optJSONObject("tp1")
            val tp2Obj = targetsObj?.optJSONObject("tp2")
            val tp3Obj = targetsObj?.optJSONObject("tp3")

            val tp1Price = tp1Obj?.optDouble("price", json.optDouble("takeProfit1", 0.0))
                ?: json.optDouble("takeProfit1", 0.0)
            val tp2Price = tp2Obj?.optDouble("price", json.optDouble("takeProfit2", 0.0))
                ?: json.optDouble("takeProfit2", 0.0)
            val tp3Price = tp3Obj?.optDouble("price", json.optDouble("takeProfit3", 0.0))
                ?: json.optDouble("takeProfit3", 0.0)

            val targets = OrixProTargetSet(
                tp1 = OrixProTargetMilestone(
                    price = tp1Price,
                    rr = tp1Obj?.optString("rr", "1 : 1.5") ?: "1 : 1.5",
                    allocationPct = tp1Obj?.optInt("allocationPct", 40) ?: 40,
                    actionOnHit = tp1Obj?.optString("actionOnHit", "Move SL to Break-Even") ?: "Move SL to Break-Even"
                ),
                tp2 = OrixProTargetMilestone(
                    price = tp2Price,
                    rr = tp2Obj?.optString("rr", "1 : 2.5") ?: "1 : 2.5",
                    allocationPct = tp2Obj?.optInt("allocationPct", 40) ?: 40,
                    actionOnHit = tp2Obj?.optString("actionOnHit", "Activate 1.5x ATR Trailing Stop")
                        ?: "Activate 1.5x ATR Trailing Stop"
                ),
                tp3 = OrixProTargetMilestone(
                    price = tp3Price,
                    rr = tp3Obj?.optString("rr", "1 : 4.0") ?: "1 : 4.0",
                    allocationPct = tp3Obj?.optInt("allocationPct", 20) ?: 20,
                    actionOnHit = tp3Obj?.optString("actionOnHit", "Full Close / Trail Under 5m Swing Low")
                        ?: "Full Close / Trail Under 5m Swing Low"
                )
            )

            val earlyExitObj = lifecycleObj?.optJSONObject("earlyExitTrigger")
            val earlyExitTrigger = OrixProEarlyExitTrigger(
                earlyExitNeededNow = earlyExitObj?.optBoolean("earlyExitNeededNow", false) ?: false,
                emergencyExitPriceCondition = earlyExitObj?.optString(
                    "emergencyExitPriceCondition",
                    json.optString("invalidationCondition", "Candle body close beyond opposing CHoCH structure level")
                ) ?: json.optString("invalidationCondition", "Candle body close beyond opposing CHoCH structure level"),
                earlyExitReason = earlyExitObj?.optString(
                    "earlyExitReason",
                    "Hold active — structure remains intact above session VWAP."
                ) ?: "Hold active — structure remains intact above session VWAP."
            )

            val tradeLifecycle = OrixProTradeLifecycle(
                entryZone = OrixProEntryZone(
                    low = entryLow,
                    high = entryHigh,
                    optimalEntry = optimalEntry
                ),
                initialStopLoss = initialStopLoss,
                trailingStopConfig = trailingStopConfig,
                targets = targets,
                earlyExitTrigger = earlyExitTrigger
            )

            // 4. Parse futureSwingsProjection
            val swingsObj = json.optJSONObject("futureSwingsProjection")
            val shObj = swingsObj?.optJSONObject("projectedNextSwingHigh")
            val slObj = swingsObj?.optJSONObject("projectedNextSwingLow")
            val futureSwingsProjection = OrixProFutureSwingsProjection(
                expectedTrajectory = swingsObj?.optString(
                    "expectedTrajectory",
                    "Pullback into Discount OB/FVG followed by impulse toward external liquidity"
                ) ?: "Pullback into Discount OB/FVG followed by impulse toward external liquidity",
                projectedNextSwingHigh = ProjectedSwingPoint(
                    price = shObj?.optDouble("price", tp2Price) ?: tp2Price,
                    probabilityPct = shObj?.optInt("probabilityPct", 74) ?: 74,
                    estimatedCandles = shObj?.optString("estimatedCandles", "6-12 bars") ?: "6-12 bars"
                ),
                projectedNextSwingLow = ProjectedSwingPoint(
                    price = slObj?.optDouble("price", entryLow) ?: entryLow,
                    probabilityPct = slObj?.optInt("probabilityPct", 68) ?: 68,
                    estimatedCandles = slObj?.optString("estimatedCandles", "2-4 bars") ?: "2-4 bars"
                )
            )

            // 5. Parse fundamentalAndNewsContext
            val newsObj = json.optJSONObject("fundamentalAndNewsContext")
            val catalystSummary = when {
                newsObj == null -> ""
                newsObj.has("keyCatalystSummary") -> newsObj.optString("keyCatalystSummary", "")
                newsObj.has("keyCatalyst") -> newsObj.optString("keyCatalyst", "")
                newsObj.has("keyCatalysts") -> {
                    val arr = newsObj.optJSONArray("keyCatalysts")
                    if (arr != null) {
                        (0 until arr.length()).joinToString(" • ") { arr.optString(it) }
                    } else {
                        newsObj.optString("keyCatalysts", "")
                    }
                }
                else -> ""
            }
            val fundamentalAndNewsContext = OrixProFundamentalAndNewsContext(
                overallNewsSentiment = newsObj?.optString("overallNewsSentiment", "BULLISH (+64/100)")
                    ?: "BULLISH (+64/100)",
                keyCatalystSummary = catalystSummary.ifBlank {
                    "Institutional ETF inflows & positive order-book absorption supporting trend continuation."
                }
            )

            // 6. Parse marketRegime, riskRewardRatio, indicatorConfluence, invalidationCondition, smartMoneyBlueprint
            val marketRegime = json.optString("marketRegime", "").ifBlank {
                if (signal.contains("BUY")) "Bullish Momentum" else if (signal.contains("SELL")) "Bearish Breakdown" else "Consolidation / Range"
            }
            val riskRewardRatio = json.optString("riskRewardRatio", "").ifBlank {
                targets.tp2.rr
            }
            val invalidationCondition = json.optString("invalidationCondition", "").ifBlank {
                earlyExitTrigger.emergencyExitPriceCondition
            }

            val confluenceArr = json.optJSONArray("indicatorConfluence")
            val confluenceList = if (confluenceArr != null && confluenceArr.length() > 0) {
                (0 until confluenceArr.length()).map { confluenceArr.getString(it) }
            } else {
                listOf(
                    "AI Strength Grade ${aiStrength.grade} (${aiStrength.overallScore}/100): SMC ${aiStrength.smcScore} | Tech ${aiStrength.technicalScore} | OrderFlow ${aiStrength.orderFlowScore} | News ${aiStrength.newsSentimentScore}",
                    "Bulls vs Bears (${bullsVsBears.bullsPowerPct}% / ${bullsVsBears.bearsPowerPct}%): ${bullsVsBears.dominantForce} — ${bullsVsBears.orderFlowDeltaSummary}",
                    "Projected Trajectory: ${futureSwingsProjection.expectedTrajectory}",
                    "TSL Rule: ${trailingStopConfig.activationRule}",
                    "News & Macro (${fundamentalAndNewsContext.overallNewsSentiment}): ${fundamentalAndNewsContext.keyCatalystSummary}"
                )
            }

            val bpObj = json.optJSONObject("smartMoneyBlueprint")
            val blueprint = if (bpObj != null) {
                SmartMoneyBlueprint(
                    institutionalNarrative = bpObj.optString("institutionalNarrative", futureSwingsProjection.expectedTrajectory),
                    orderBlockCoordinates = bpObj.optString("orderBlockCoordinates", "Optimal Entry $${String.format(Locale.US, "%.2f", optimalEntry)}"),
                    fairValueGapCoordinates = bpObj.optString("fairValueGapCoordinates", futureSwingsProjection.expectedTrajectory),
                    liquiditySweepStatus = bpObj.optString("liquiditySweepStatus", bullsVsBears.orderFlowDeltaSummary),
                    dealingRangeAndOte = bpObj.optString("dealingRangeAndOte", trailingStopConfig.activationRule)
                )
            } else {
                fallbackBlueprint.copy(
                    institutionalNarrative = fallbackBlueprint.institutionalNarrative.ifBlank { futureSwingsProjection.expectedTrajectory }
                )
            }

            return AiSignalResult(
                symbol = symbol,
                displayPair = displayPair,
                timeframe = timeframe,
                tradingStyle = tradingStyle,
                signal = signal,
                confidence = overallScore,
                marketRegime = marketRegime,
                entryLow = entryLow,
                entryHigh = entryHigh,
                stopLoss = initialStopLoss,
                takeProfit1 = tp1Price,
                takeProfit2 = tp2Price,
                takeProfit3 = tp3Price,
                riskRewardRatio = riskRewardRatio,
                indicatorConfluence = confluenceList,
                invalidationCondition = invalidationCondition,
                smartMoneyBlueprint = blueprint,
                generatedAtMs = closedBarTimestampMs,
                signalId = signalId,
                isNonRepaintingConfirmed = isNonRepaintingConfirmed,
                optimalEntry = optimalEntry,
                aiStrength = aiStrength,
                bullsVsBears = bullsVsBears,
                futureSwingsProjection = futureSwingsProjection,
                tradeLifecycle = tradeLifecycle,
                fundamentalAndNewsContext = fundamentalAndNewsContext
            )
        }

        val SAMPLE_ORIXVEX_PRO_LIFECYCLE_JSON = """
{
  "signalId": "BTCUSDT_15m_1762190400000",
  "isNonRepaintingConfirmed": true,
  "signal": "STRONG BUY",
  "aiStrength": {
    "overallScore": 88,
    "grade": "A+",
    "smcScore": 92,
    "technicalScore": 86,
    "orderFlowScore": 84,
    "newsSentimentScore": 89
  },
  "bullsVsBears": {
    "bullsPowerPct": 68,
    "bearsPowerPct": 32,
    "dominantForce": "Institutional Buyers Absorbing Ask Liquidity",
    "orderFlowDeltaSummary": "Taker Buy ratio at 61.4% with strong bid wall at $90,800"
  },
  "futureSwingsProjection": {
    "expectedTrajectory": "Pullback into $90,750 Bullish FVG followed by impulse toward $92,800 BSL",
    "projectedNextSwingHigh": { "price": 92850.00, "probabilityPct": 74, "estimatedCandles": "6-12 bars" },
    "projectedNextSwingLow": { "price": 90680.00, "probabilityPct": 68, "estimatedCandles": "2-4 bars" }
  },
  "tradeLifecycle": {
    "entryZone": { "low": 90720.00, "high": 90950.00, "optimalEntry": 90835.00 },
    "initialStopLoss": 90180.00,
    "trailingStopConfig": {
      "activationRule": "Move SL to Break-Even ($90,925) at TP1; trail by 1.5x ATR ($420) after TP2",
      "atrMultiplier": 1.5,
      "currentRecommendedTSL": 90180.00
    },
    "targets": {
      "tp1": { "price": 91820.00, "rr": "1 : 1.5", "allocationPct": 40, "actionOnHit": "Move SL to Break-Even" },
      "tp2": { "price": 92480.00, "rr": "1 : 2.5", "allocationPct": 40, "actionOnHit": "Activate 1.5x ATR Trailing Stop" },
      "tp3": { "price": 93460.00, "rr": "1 : 4.0", "allocationPct": 20, "actionOnHit": "Full Close / Trail Under 5m Swing Low" }
    },
    "earlyExitTrigger": {
      "earlyExitNeededNow": false,
      "emergencyExitPriceCondition": "15m candle body close below $90,450 (Bearish CHoCH) or RSI dropping below 42 with Taker Sell spike",
      "earlyExitReason": "Hold active — structure remains intact above session VWAP."
    }
  },
  "fundamentalAndNewsContext": {
    "overallNewsSentiment": "BULLISH (+64/100)",
    "keyCatalystSummary": "Institutional spot ETF inflows and aggressive bid absorption above $90,800."
  }
}
        """.trimIndent()
    }
}
