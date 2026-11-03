package com.example

import com.example.data.model.AiSignalResult
import com.example.data.model.CryptoPair
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.OhlcvCandle
import com.example.domain.ai.UserGeminiQuotaManager
import com.example.domain.quant.MarketDynamicsAndQuantEngine
import com.example.domain.quant.QuantitativeIndicatorEngine
import com.example.domain.quant.SmcCalculator
import com.example.domain.quant.TradeLifecycleEngine
import com.example.ui.viewmodel.BinanceStreamViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun quantitativeIndicatorEngine_computesValidIndicatorsFrom200Candles() {
        val candles = (0 until 200).map { i ->
            val base = 90000.0 + (i * 25.0)
            OhlcvCandle(
                openTime = 1700000000000L + (i * 60000L),
                open = base - 15.0,
                high = base + 45.0,
                low = base - 35.0,
                close = base + 20.0,
                volume = 120.0 + (i % 10) * 15.0,
                closeTime = 1700000059999L + (i * 60000L),
                quoteVolume = (base + 20.0) * (120.0 + (i % 10) * 15.0)
            )
        }

        val telemetry = QuantitativeIndicatorEngine.computeTelemetry(
            symbol = "BTCUSDT",
            timeframe = "15m",
            candles = candles
        )

        assertTrue(telemetry.ema20 > telemetry.ema50)
        assertTrue(telemetry.ema50 > telemetry.ema200)
        assertTrue(telemetry.rsi14 in 50.0..100.0)
        assertTrue(telemetry.vwap > 0.0)
        assertTrue(telemetry.atr14 > 0.0)
        assertTrue(telemetry.supertrendBullish)
        assertTrue(telemetry.swingHigh20 >= telemetry.swingLow20)
        assertTrue(telemetry.smc.equilibriumPrice > 0.0)
        assertTrue(telemetry.smc.dealingRangeHigh50 >= telemetry.smc.dealingRangeLow50)
    }

    @Test
    fun smcCalculator_computesFractalsBosFvgOrderBlockAndEquilibrium() {
        val candles = mutableListOf<OhlcvCandle>()
        for (i in 0 until 200) {
            val base = 50000.0 + (i * 10.0)
            when (i) {
                170 -> candles.add(
                    OhlcvCandle(
                        openTime = i * 60000L,
                        open = base - 50.0,
                        high = base - 10.0,
                        low = base - 220.0,
                        close = base - 40.0,
                        volume = 150.0,
                        closeTime = i * 60000L + 59999L,
                        quoteVolume = 150.0 * base
                    )
                )
                178 -> candles.add(
                    OhlcvCandle(
                        openTime = i * 60000L,
                        open = base + 30.0,
                        high = base + 200.0,
                        low = base - 10.0,
                        close = base + 80.0,
                        volume = 180.0,
                        closeTime = i * 60000L + 59999L,
                        quoteVolume = 180.0 * base
                    )
                )
                190 -> candles.add(
                    OhlcvCandle(
                        openTime = i * 60000L,
                        open = base + 40.0,
                        high = base + 55.0,
                        low = base - 60.0,
                        close = base - 45.0,
                        volume = 210.0,
                        closeTime = i * 60000L + 59999L,
                        quoteVolume = 210.0 * base
                    )
                )
                191 -> candles.add(
                    OhlcvCandle(
                        openTime = i * 60000L,
                        open = base - 40.0,
                        high = base + 420.0,
                        low = base - 42.0,
                        close = base + 400.0,
                        volume = 500.0,
                        closeTime = i * 60000L + 59999L,
                        quoteVolume = 500.0 * base
                    )
                )
                192 -> candles.add(
                    OhlcvCandle(
                        openTime = i * 60000L,
                        open = base + 390.0,
                        high = base + 480.0,
                        low = base + 260.0,
                        close = base + 450.0,
                        volume = 320.0,
                        closeTime = i * 60000L + 59999L,
                        quoteVolume = 320.0 * base
                    )
                )
                else -> {
                    val offset = if (i > 192) 450.0 else 0.0
                    candles.add(
                        OhlcvCandle(
                            openTime = i * 60000L,
                            open = base + offset - 10.0,
                            high = base + offset + 25.0,
                            low = base + offset - 25.0,
                            close = base + offset + 12.0,
                            volume = 100.0,
                            closeTime = i * 60000L + 59999L,
                            quoteVolume = 100.0 * (base + offset)
                        )
                    )
                }
            }
        }

        val pivots = SmcCalculator.identifySwingPivots(candles)
        assertTrue("3-bar fractal Swing Highs should be identified", pivots.swingHighs.isNotEmpty())
        assertTrue("3-bar fractal Swing Lows should be identified", pivots.swingLows.isNotEmpty())

        val structure = SmcCalculator.detectMarketStructure(candles, pivots)
        assertTrue("Market structure should be bullish", structure.isBullish)

        val fvgResult = SmcCalculator.calculateFairValueGaps(candles)
        assertNotNull("Bullish FVG (BISI) should be calculated", fvgResult.nearestBullishFvg)

        val obResult = SmcCalculator.defineOrderBlocks(
            candles = candles,
            bullDisplacementIndices = fvgResult.bullDisplacementIndices,
            bearDisplacementIndices = fvgResult.bearDisplacementIndices,
            swingHighs = pivots.swingHighs,
            swingLows = pivots.swingLows
        )
        assertNotNull("Unmitigated Bullish OB should be defined", obResult.nearestBullishOb)

        val dealingRange = SmcCalculator.calculateDealingRange(candles, structure.isBullish)
        assertEquals(
            (dealingRange.dealingRangeHigh50 + dealingRange.dealingRangeLow50) / 2.0,
            dealingRange.equilibriumPrice,
            0.001
        )

        val smc = SmcCalculator.computeSmcTelemetry(candles, atr14 = 65.0)
        assertTrue("Confirmed Swing High should be positive", smc.confirmedSwingHigh > 0.0)
        assertTrue("Confirmed Swing Low should be positive", smc.confirmedSwingLow > 0.0)
        assertTrue("Structure should detect Bullish BOS or CHoCH", smc.structureState.contains("Bullish"))
        assertNotNull("Active Bullish FVG (BISI) should be detected", smc.nearestBullishFvg)
        assertNotNull("Unmitigated Bullish Order Block should be detected", smc.nearestBullishOb)
        assertTrue(smc.oteLow > 0.0 && smc.oteHigh > 0.0)
    }

    @Test
    fun binanceStreamViewModel_mergeLiveKlineCandle_updatesInPlaceAndRolls200Window() {
        val initial = (0 until 200).map { i ->
            OhlcvCandle(
                openTime = 1000L * (i + 1),
                open = 100.0 + i,
                high = 105.0 + i,
                low = 95.0 + i,
                close = 102.0 + i,
                volume = 50.0,
                closeTime = 1000L * (i + 1) + 999L,
                quoteVolume = 5000.0
            )
        }

        val updatedTick = initial.last().copy(close = 999.5, high = 1005.0)
        val afterInPlace = BinanceStreamViewModel.mergeLiveKlineCandle(initial, updatedTick, maxCandles = 200)
        assertEquals(200, afterInPlace.size)
        assertEquals(999.5, afterInPlace.last().close, 0.001)

        val nextCandle = OhlcvCandle(
            openTime = 201_000L,
            open = 999.5,
            high = 1010.0,
            low = 990.0,
            close = 1008.0,
            volume = 75.0,
            closeTime = 201_999L,
            quoteVolume = 75600.0
        )
        val afterRoll = BinanceStreamViewModel.mergeLiveKlineCandle(afterInPlace, nextCandle, maxCandles = 200)
        assertEquals(200, afterRoll.size)
        assertEquals(2000L, afterRoll.first().openTime)
        assertEquals(201_000L, afterRoll.last().openTime)
        assertEquals(1008.0, afterRoll.last().close, 0.001)
    }

    @Test
    fun tradeLifecycleEngine_ratchetsTslForwardOnlyAndDetectsEarlyExit() {
        val candles = (0 until 60).map { i ->
            val base = 90000.0 + (i * 20.0)
            OhlcvCandle(
                openTime = i * 60000L,
                open = base - 10.0,
                high = base + 30.0,
                low = base - 20.0,
                close = base + 15.0,
                volume = 100.0,
                closeTime = i * 60000L + 59999L,
                quoteVolume = base * 100.0,
                isClosed = i < 59
            )
        }
        val closedOnly = TradeLifecycleEngine.extractClosedCandlesForNonRepainting(candles)
        assertEquals(59, closedOnly.size)

        val telemetry = QuantitativeIndicatorEngine.computeTelemetry("BTCUSDT", "15m", closedOnly)
        val aiSignal = AiSignalResult(
            symbol = "BTCUSDT",
            displayPair = "BTC/USD",
            timeframe = "15m",
            tradingStyle = "Scalp 5m/15m",
            signal = "STRONG BUY",
            confidence = 88,
            marketRegime = "Bullish Momentum",
            entryLow = 90900.0,
            entryHigh = 91100.0, // midpoint = 91000.0
            stopLoss = 90000.0,
            takeProfit1 = 92500.0, // 1.5R
            takeProfit2 = 93500.0, // 2.5R
            takeProfit3 = 95000.0, // 4.0R
            riskRewardRatio = "1 : 2.5",
            indicatorConfluence = listOf("SMC Bullish OB"),
            invalidationCondition = "Close below $90,000"
        )

        val locked = TradeLifecycleEngine.createLockedNonRepaintingTrade(
            signalResult = aiSignal,
            telemetry = telemetry,
            closedBarTimestampMs = 1700000000000L,
            userEmailWatermark = "trader@orixvex.ai"
        )
        assertEquals(90000.0, locked.currentTrailingStop, 0.01)
        assertFalse(locked.tp1Hit)

        // 1. Hit TP1 ($92,550) -> TSL ratchets to Break-Even + 0.10% fee buffer ($91,000 * 1.001 = $91,091.0)
        val afterTp1 = TradeLifecycleEngine.evaluateTickUpdate(locked, livePrice = 92550.0, liveAtr14 = 300.0)
        assertTrue(afterTp1.tp1Hit)
        assertEquals(91091.0, afterTp1.currentTrailingStop, 0.5)

        // 2. Hit TP2 ($93,800) -> TSL ratchets to max(currentTSL, highestPrice - 1.5 * ATR14 = 93800 - 450 = 93350)
        val afterTp2 = TradeLifecycleEngine.evaluateTickUpdate(afterTp1, livePrice = 93800.0, liveAtr14 = 300.0)
        assertTrue(afterTp2.tp2Hit)
        assertEquals(93350.0, afterTp2.currentTrailingStop, 0.5)

        // 3. Pullback to $93,600 -> TSL NEVER moves backward (stays at $93,350)
        val afterPullback = TradeLifecycleEngine.evaluateTickUpdate(afterTp2, livePrice = 93600.0, liveAtr14 = 300.0)
        assertEquals(93350.0, afterPullback.currentTrailingStop, 0.5)
    }

    @Test
    fun marketDynamicsAndQuotaManager_computeStrengthBullsBearsAndBurstLimits() {
        val candles = (0 until 60).map { i ->
            val base = 90000.0 + (i * 25.0)
            OhlcvCandle(
                openTime = i * 60000L,
                open = base - 10.0,
                high = base + 35.0,
                low = base - 25.0,
                close = base + 18.0,
                volume = 120.0,
                closeTime = i * 60000L + 59999L,
                quoteVolume = base * 120.0,
                takerBuyBaseVolume = 75.0
            )
        }
        val telemetry = QuantitativeIndicatorEngine.computeTelemetry("BTCUSDT", "15m", candles)
        val fundamentals = FundamentalMacroSnapshot(takerBuyPercent = 62.5, takerBuySellRatio = 1.66)

        val strength = MarketDynamicsAndQuantEngine.computeSignalStrengthBreakdown(
            telemetry = telemetry,
            fundamentals = fundamentals,
            mtfRows = emptyList()
        )
        assertTrue(strength.overallScore in 0..100)
        assertTrue(strength.institutionalGrade in listOf("S", "A+", "A", "B", "C"))

        val power = MarketDynamicsAndQuantEngine.computeBullsVsBearsPower(candles, telemetry, fundamentals)
        assertEquals(100.0, power.bullsPercent + power.bearsPercent, 0.15)

        val quotaManager = UserGeminiQuotaManager()
        val now = 1_700_000_000_000L
        for (i in 1..15) {
            assertNull(quotaManager.checkRateLimitBeforeRequest(now + i * 100L))
            quotaManager.recordRequestCompleted(newRpdCount = i, nowMs = now + i * 100L)
        }
        // 16th request within 60 seconds should trigger burst limit message
        val throttleMsg = quotaManager.checkRateLimitBeforeRequest(now + 2000L)
        assertNotNull(throttleMsg)
        assertTrue(throttleMsg!!.contains("15 RPM"))
    }
}
