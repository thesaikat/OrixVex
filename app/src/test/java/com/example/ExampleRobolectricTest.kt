package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CryptoPair
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.QuantitativeTelemetry
import com.example.data.model.SentimentClassification
import com.example.data.model.TradeJournalEntry
import com.example.data.remote.NewsAndFundamentalsService
import com.example.domain.quant.TradeLifecycleEngine
import com.example.ui.components.SmcStructureStrip
import com.example.ui.components.TerminalFearGreedAndRssCard
import com.example.ui.screens.HistoryAndJournalScreen
import com.example.ui.theme.OrixVexTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OrixVex", appName)
    }

    @Test
    fun `SmcStructureStrip renders Market Structure, Dealing Zone, nearest OBs, and unmitigated FVGs`() {
        val telemetry = QuantitativeTelemetry.fromInstitutionalJson(
            QuantitativeTelemetry.SAMPLE_BTC_15M_SMC_JSON
        )

        composeTestRule.setContent {
            OrixVexTheme(darkTheme = true) {
                SmcStructureStrip(
                    pair = CryptoPair.BTC,
                    smc = telemetry.smc
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("smc_structure_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smc_structure_chip_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smc_chip_structure").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smc_chip_dealing_zone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smc_chip_nearest_ob").assertIsDisplayed()
    }

    @Test
    fun `institutionalJsonPayload parses BTC 15m SMC telemetry and coordinates accurately`() {
        val telemetry = QuantitativeTelemetry.fromInstitutionalJson(
            QuantitativeTelemetry.SAMPLE_BTC_15M_SMC_JSON
        )

        assertEquals("BTCUSDT", telemetry.symbol)
        assertEquals("15m", telemetry.timeframe)
        assertEquals(91240.50, telemetry.currentPrice, 0.01)
        assertEquals(91080.20, telemetry.ema20, 0.01)
        assertEquals(56.4, telemetry.rsi14, 0.01)
        assertEquals(285.40, telemetry.atr14, 0.01)

        val smc = telemetry.smc
        assertEquals("BULLISH_CHOCH", smc.structureBreakType)
        assertEquals(90980.00, smc.structureBreakLevel, 0.01)
        assertEquals(6, smc.structureBreakCandlesAgo)
        assertEquals(91135.00, smc.equilibriumPrice, 0.01)
        assertEquals(54.0, smc.dealingRangePct, 0.1)
        assertEquals("SSL_SWEEP", smc.sweepType)
        assertEquals(89890.00, smc.sweptLevel, 0.01)
        assertEquals(89820.00, smc.sweepWickExtreme, 0.01)
        assertEquals(90685.00, smc.nearestBullishOb?.meanThreshold ?: 0.0, 0.01)
        assertEquals(92315.00, smc.nearestBearishOb?.meanThreshold ?: 0.0, 0.01)
        assertEquals(90915.00, smc.nearestBullishFvg?.midpoint ?: 0.0, 0.01)
    }

    @Test
    fun `NewsAndFundamentalsService parses RSS XML feeds, Fear and Greed API JSON, and renders in Terminal view`() {
        val service = NewsAndFundamentalsService()

        val sampleRssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title><![CDATA[Bitcoin Institutional ETF Inflow Triggers Major Bullish Breakout - CoinDesk]]></title>
                  <link>https://www.coindesk.com/markets/btc-breakout</link>
                  <pubDate>Tue, 03 Nov 2026 18:30:00 GMT</pubDate>
                  <source url="https://www.coindesk.com">CoinDesk</source>
                </item>
                <item>
                  <title>Federal Reserve FOMC Signals Dovish Rate Cut Path Ahead - Reuters</title>
                  <link>https://www.reuters.com/markets/fomc</link>
                  <pubDate>Tue, 03 Nov 2026 17:45:00 GMT</pubDate>
                  <source url="https://www.reuters.com">Reuters</source>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val articles = service.parseRssXmlItems(sampleRssXml)
        assertEquals(2, articles.size)
        assertEquals("CoinDesk", articles[0].source)
        assertEquals(SentimentClassification.BULLISH, articles[0].sentiment)
        assertEquals(SentimentClassification.HIGH_IMPACT_MACRO, articles[1].sentiment)

        val sampleFngJson = """
            {
              "name": "Fear and Greed Index",
              "data": [
                { "value": "71", "value_classification": "Greed", "timestamp": "1762190000" },
                { "value": "66", "value_classification": "Greed", "timestamp": "1762103600" }
              ]
            }
        """.trimIndent()

        val fearGreed = service.parseFearAndGreedJson(sampleFngJson)
        assertEquals(71, fearGreed.currentValue)
        assertEquals("Greed", fearGreed.currentClassification)
        assertTrue(fearGreed.history7d.size == 2)

        val snapshot = FundamentalMacroSnapshot(
            articles = articles,
            fearAndGreed = fearGreed,
            netNewsSentimentScore = 78.0
        )

        composeTestRule.setContent {
            OrixVexTheme(darkTheme = true) {
                TerminalFearGreedAndRssCard(
                    pair = CryptoPair.BTC,
                    fundamentals = snapshot,
                    onRefreshNews = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("terminal_fear_greed_rss_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("terminal_fear_greed_gauge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("terminal_rss_headline_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("terminal_refresh_rss_button").assertIsDisplayed()
    }

    @Test
    fun `HistoryAndJournalScreen renders Immutable Signal History TP SL status and Manual Trade Journal logging`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("History & Journal", context.getString(R.string.nav_journal))

        val seedSignals = listOf(
            com.example.data.model.ActiveManagedTrade(
                signalId = "SIG-BTC-8841",
                symbol = "BTCUSDT",
                displayPair = "BTC/USD",
                timeframe = "15m",
                tradingStyle = "Intraday SMC + Momentum",
                signal = "STRONG BUY",
                isLong = true,
                confidence = 91,
                marketRegime = "Bullish Expansion",
                orderType = "Limit at OB/FVG",
                lockedAtBarCloseMs = 1762190000000L,
                initialEntryLow = 90580.0,
                initialEntryHigh = 90790.0,
                entryMidpoint = 90685.0,
                initialStopLoss = 90210.0,
                tp1 = 91400.0,
                tp2 = 91875.0,
                tp3 = 92585.0,
                atr14AtLock = 285.0,
                currentPrice = 92600.0,
                highestPriceReached = 92640.0,
                lowestPriceReached = 90610.0,
                currentTrailingStop = 91850.0,
                tslPhaseLabel = "TP3 Final Target Achieved (4.0R)",
                tp1Hit = true,
                tp2Hit = true,
                tp3Hit = true,
                isClosed = true,
                status = "TP3_HIT",
                mfePct = 2.15,
                maePct = 0.18,
                currentRMultiple = 4.0
            ),
            com.example.data.model.ActiveManagedTrade(
                signalId = "SIG-SOL-8839",
                symbol = "SOLUSDT",
                displayPair = "SOL/USD",
                timeframe = "15m",
                tradingStyle = "Intraday SMC + Momentum",
                signal = "SELL",
                isLong = false,
                confidence = 78,
                marketRegime = "Range Distribution",
                orderType = "Market Execution",
                lockedAtBarCloseMs = 1762186400000L,
                initialEntryLow = 188.0,
                initialEntryHigh = 189.0,
                entryMidpoint = 188.5,
                initialStopLoss = 191.2,
                tp1 = 184.5,
                tp2 = 181.8,
                tp3 = 177.7,
                atr14AtLock = 1.4,
                currentPrice = 191.2,
                highestPriceReached = 191.3,
                lowestPriceReached = 187.8,
                currentTrailingStop = 191.2,
                tslPhaseLabel = "Stopped Out at Initial SL (-1.0R)",
                tp1Hit = false,
                tp2Hit = false,
                tp3Hit = false,
                isClosed = true,
                status = "STOPPED_OUT",
                mfePct = 0.35,
                maePct = 1.43,
                currentRMultiple = -1.0
            )
        )
        var loggedManualCount = 0

        composeTestRule.setContent {
            OrixVexTheme(darkTheme = true) {
                HistoryAndJournalScreen(
                    activeManagedTrade = null,
                    closedManagedTrades = seedSignals,
                    savedSignals = emptyList(),
                    journalEntries = listOf(
                        TradeJournalEntry(
                            id = "JNL-TEST-1",
                            displayPair = "BTC/USD",
                            timeframe = "15m",
                            direction = "LONG",
                            entryPrice = 90000.0,
                            exitPrice = 91800.0,
                            positionSizeUsd = 5000.0,
                            pnlUsd = 100.0,
                            rMultiple = 2.5,
                            setupTag = "SMC OB",
                            mindsetTag = "Disciplined",
                            notes = "Clean OB test and hold to TP2."
                        )
                    ),
                    aiCoachAudit = null,
                    isRunningCoachAudit = false,
                    onImportTradeToJournal = {},
                    onAddManualJournalEntry = { _, _, _, _, _, _, _, _, _ ->
                        loggedManualCount++
                    },
                    onDeleteJournalEntry = {},
                    onDeleteSavedSignal = {},
                    onRunAiCoachAudit = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify Primary View: Immutable Signal History with TP/SL resolution status
        composeTestRule.onNodeWithTag("history_and_journal_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("immutable_history_summary_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("locked_signal_audit_card_SIG-BTC-8841").assertIsDisplayed()
        composeTestRule.onNodeWithTag("signal_resolution_badge_SIG-BTC-8841").assertIsDisplayed()

        // Filter to SL Hit signals and verify SL resolution card
        composeTestRule.onNodeWithTag("history_filter_sl_hit").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("locked_signal_audit_card_SIG-SOL-8839").assertIsDisplayed()
        composeTestRule.onNodeWithTag("signal_resolution_badge_SIG-SOL-8839").assertIsDisplayed()

        // Switch to Secondary View: Manual Trade Journal
        composeTestRule.onNodeWithTag("history_journal_subtab_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("manual_journal_summary_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inline_manual_trade_form").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_manual_trade_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, loggedManualCount)
    }

    @Test
    fun `orixVexProLifecycleSchema parses all nested properties and locks non-repainting trade`() {
        val parsed = com.example.domain.ai.GeminiSignalService.parseOrixVexProLifecycleSignalJson(
            rawJson = com.example.domain.ai.GeminiSignalService.SAMPLE_ORIXVEX_PRO_LIFECYCLE_JSON,
            symbol = "BTCUSDT",
            displayPair = "BTC/USD",
            timeframe = "15m",
            tradingStyle = "Intraday SMC + Momentum",
            closedBarTimestampMs = 1762190400000L
        )

        assertEquals("BTCUSDT_15m_1762190400000", parsed.signalId)
        assertTrue(parsed.isNonRepaintingConfirmed)
        assertEquals("STRONG BUY", parsed.signal)

        // aiStrength
        assertEquals(88, parsed.aiStrength.overallScore)
        assertEquals("A+", parsed.aiStrength.grade)
        assertEquals(92, parsed.aiStrength.smcScore)
        assertEquals(86, parsed.aiStrength.technicalScore)
        assertEquals(84, parsed.aiStrength.orderFlowScore)
        assertEquals(89, parsed.aiStrength.newsSentimentScore)

        // bullsVsBears
        assertEquals(68, parsed.bullsVsBears.bullsPowerPct)
        assertEquals(32, parsed.bullsVsBears.bearsPowerPct)
        assertEquals("Institutional Buyers Absorbing Ask Liquidity", parsed.bullsVsBears.dominantForce)
        assertTrue(parsed.bullsVsBears.orderFlowDeltaSummary.contains("61.4%"))

        // futureSwingsProjection
        assertTrue(parsed.futureSwingsProjection.expectedTrajectory.contains("90,750"))
        assertEquals(92850.00, parsed.futureSwingsProjection.projectedNextSwingHigh.price, 0.01)
        assertEquals(74, parsed.futureSwingsProjection.projectedNextSwingHigh.probabilityPct)
        assertEquals("6-12 bars", parsed.futureSwingsProjection.projectedNextSwingHigh.estimatedCandles)
        assertEquals(90680.00, parsed.futureSwingsProjection.projectedNextSwingLow.price, 0.01)
        assertEquals(68, parsed.futureSwingsProjection.projectedNextSwingLow.probabilityPct)
        assertEquals("2-4 bars", parsed.futureSwingsProjection.projectedNextSwingLow.estimatedCandles)

        // tradeLifecycle
        assertEquals(90720.00, parsed.entryLow, 0.01)
        assertEquals(90950.00, parsed.entryHigh, 0.01)
        assertEquals(90835.00, parsed.optimalEntry, 0.01)
        assertEquals(90180.00, parsed.stopLoss, 0.01)
        assertEquals(1.5, parsed.tradeLifecycle.trailingStopConfig.atrMultiplier, 0.01)
        assertEquals(90180.00, parsed.tradeLifecycle.trailingStopConfig.currentRecommendedTSL, 0.01)
        assertEquals(91820.00, parsed.takeProfit1, 0.01)
        assertEquals(40, parsed.tradeLifecycle.targets.tp1.allocationPct)
        assertEquals("Move SL to Break-Even", parsed.tradeLifecycle.targets.tp1.actionOnHit)
        assertEquals(92480.00, parsed.takeProfit2, 0.01)
        assertEquals(40, parsed.tradeLifecycle.targets.tp2.allocationPct)
        assertEquals("Activate 1.5x ATR Trailing Stop", parsed.tradeLifecycle.targets.tp2.actionOnHit)
        assertEquals(93460.00, parsed.takeProfit3, 0.01)
        assertEquals(20, parsed.tradeLifecycle.targets.tp3.allocationPct)
        org.junit.Assert.assertFalse(parsed.tradeLifecycle.earlyExitTrigger.earlyExitNeededNow)
        assertTrue(parsed.tradeLifecycle.earlyExitTrigger.emergencyExitPriceCondition.contains("90,450"))

        // fundamentalAndNewsContext
        assertEquals("BULLISH (+64/100)", parsed.fundamentalAndNewsContext.overallNewsSentiment)
        assertTrue(parsed.fundamentalAndNewsContext.keyCatalystSummary.isNotBlank())

        // Verify locked trade creation preserves deterministic signalId and optimalEntry
        val telemetry = QuantitativeTelemetry.fromInstitutionalJson(
            QuantitativeTelemetry.SAMPLE_BTC_15M_SMC_JSON
        )
        val lockedTrade = TradeLifecycleEngine.createLockedNonRepaintingTrade(
            signalResult = parsed,
            telemetry = telemetry,
            closedBarTimestampMs = 1762190400000L,
            userEmailWatermark = "thesaikot@gmail.com"
        )
        assertEquals("BTCUSDT_15m_1762190400000", lockedTrade.signalId)
        assertEquals(90835.00, lockedTrade.entryMidpoint, 0.01)
        assertEquals(90180.00, lockedTrade.currentTrailingStop, 0.01)
        assertTrue(lockedTrade.isNonRepaintingConfirmed)
    }
}
