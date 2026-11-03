package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserQuotaAuthMode(val label: String, val shortBadge: String) {
    FIREBASE_AI_STUDIO("Google AI Studio Native Quota", "AI Studio Quota"),
    GOOGLE_OAUTH_BEARER("Google Sign-In OAuth Bearer Token", "OAuth User Quota"),
    PERSONAL_API_KEY("Personal Gemini API Key Vault", "Personal Key Vault")
}

data class UserQuotaStatus(
    val mode: UserQuotaAuthMode = UserQuotaAuthMode.FIREBASE_AI_STUDIO,
    val rpmUsed: Int = 0,
    val rpmLimit: Int = 15,
    val rpdUsed: Int = 0,
    val rpdLimit: Int = 1500,
    val cooldownRemainingSec: Int = 0,
    val hasCustomKey: Boolean = false,
    val hasOAuthToken: Boolean = false,
    val deepWebSearchGroundingEnabled: Boolean = false
) {
    val headerBadgeText: String
        get() = "Gemini Quota: Active (${mode.shortBadge}) • $rpmUsed/$rpmLimit RPM • $rpdUsed/$rpdLimit RPD"
}

enum class SentimentClassification(val label: String) {
    BULLISH("BULLISH"),
    BEARISH("BEARISH"),
    HIGH_IMPACT_MACRO("HIGH IMPACT MACRO"),
    NEUTRAL("NEUTRAL")
}

data class NewsArticleItem(
    val id: String,
    val title: String,
    val source: String,
    val publishedAgo: String,
    val url: String,
    val sentiment: SentimentClassification,
    val impactScore: Int // 1 to 100
)

data class FearAndGreedPoint(
    val value: Int,
    val classification: String,
    val timestampSec: Long
)

data class FearAndGreedTelemetry(
    val currentValue: Int = 58,
    val currentClassification: String = "Greed",
    val history7d: List<FearAndGreedPoint> = emptyList()
)

data class OrderBookWall(
    val price: Double,
    val quantity: Double,
    val notionalUsd: Double,
    val isBidSupport: Boolean
)

data class OrderBookDepthTelemetry(
    val symbol: String = "BTCUSDT",
    val totalBidUsd: Double = 0.0,
    val totalAskUsd: Double = 0.0,
    val bidRatioPct: Double = 50.0,
    val askRatioPct: Double = 50.0,
    val bidAskImbalancePct: Double = 0.0, // Positive = Bid heavy, Negative = Ask heavy
    val largestBidWall: OrderBookWall? = null,
    val largestAskWall: OrderBookWall? = null
)

data class FundamentalMacroSnapshot(
    val articles: List<NewsArticleItem> = emptyList(),
    val fearAndGreed: FearAndGreedTelemetry = FearAndGreedTelemetry(),
    val orderBookDepth: OrderBookDepthTelemetry = OrderBookDepthTelemetry(),
    val volumeSurge24hPct: Double = 0.0,
    val takerBuySellRatio: Double = 1.0, // > 1.0 = Aggressive Taker Buying
    val takerBuyPercent: Double = 50.0,
    val netNewsSentimentScore: Double = 50.0, // 0 = Extreme Bearish, 100 = Extreme Bullish
    val hasBreakingAdverseCatalyst: Boolean = false,
    val breakingCatalystHeadline: String = ""
)

data class OrixProAiStrength(
    val overallScore: Int = 88,
    val grade: String = "A+", // S | A+ | A | B | C
    val smcScore: Int = 92,
    val technicalScore: Int = 86,
    val orderFlowScore: Int = 84,
    val newsSentimentScore: Int = 89
)

data class OrixProBullsVsBears(
    val bullsPowerPct: Int = 68,
    val bearsPowerPct: Int = 32,
    val dominantForce: String = "Institutional Buyers Absorbing Ask Liquidity",
    val orderFlowDeltaSummary: String = "Taker Buy ratio at 61.4% with strong bid wall at $90,800"
)

data class ProjectedSwingPoint(
    val price: Double = 0.0,
    val probabilityPct: Int = 70,
    val estimatedCandles: String = "6-12 bars"
)

data class OrixProFutureSwingsProjection(
    val expectedTrajectory: String = "",
    val projectedNextSwingHigh: ProjectedSwingPoint = ProjectedSwingPoint(),
    val projectedNextSwingLow: ProjectedSwingPoint = ProjectedSwingPoint()
)

data class OrixProEntryZone(
    val low: Double = 0.0,
    val high: Double = 0.0,
    val optimalEntry: Double = 0.0
)

data class OrixProTrailingStopConfig(
    val activationRule: String = "Move SL to Break-Even at TP1; trail by 1.5x ATR after TP2",
    val atrMultiplier: Double = 1.5,
    val currentRecommendedTSL: Double = 0.0
)

data class OrixProTargetMilestone(
    val price: Double = 0.0,
    val rr: String = "1 : 1.5",
    val allocationPct: Int = 40,
    val actionOnHit: String = "Move SL to Break-Even"
)

data class OrixProTargetSet(
    val tp1: OrixProTargetMilestone = OrixProTargetMilestone(
        rr = "1 : 1.5",
        allocationPct = 40,
        actionOnHit = "Move SL to Break-Even"
    ),
    val tp2: OrixProTargetMilestone = OrixProTargetMilestone(
        rr = "1 : 2.5",
        allocationPct = 40,
        actionOnHit = "Activate 1.5x ATR Trailing Stop"
    ),
    val tp3: OrixProTargetMilestone = OrixProTargetMilestone(
        rr = "1 : 4.0",
        allocationPct = 20,
        actionOnHit = "Full Close / Trail Under 5m Swing Low"
    )
)

data class OrixProEarlyExitTrigger(
    val earlyExitNeededNow: Boolean = false,
    val emergencyExitPriceCondition: String = "",
    val earlyExitReason: String = "Hold active — structure remains intact above session VWAP."
)

data class OrixProTradeLifecycle(
    val entryZone: OrixProEntryZone = OrixProEntryZone(),
    val initialStopLoss: Double = 0.0,
    val trailingStopConfig: OrixProTrailingStopConfig = OrixProTrailingStopConfig(),
    val targets: OrixProTargetSet = OrixProTargetSet(),
    val earlyExitTrigger: OrixProEarlyExitTrigger = OrixProEarlyExitTrigger()
)

data class OrixProFundamentalAndNewsContext(
    val overallNewsSentiment: String = "BULLISH (+64/100)",
    val keyCatalystSummary: String = ""
)

data class ActiveManagedTrade(
    val signalId: String,
    val symbol: String,
    val displayPair: String,
    val timeframe: String,
    val tradingStyle: String,
    val signal: String, // "STRONG BUY", "BUY", "SELL", "STRONG SELL"
    val isLong: Boolean,
    val confidence: Int,
    val marketRegime: String,
    val orderType: String, // "Limit at OB/FVG" or "Market Execution"
    val lockedAtBarCloseMs: Long,
    val initialEntryLow: Double,
    val initialEntryHigh: Double,
    val entryMidpoint: Double,
    val initialStopLoss: Double,
    val tp1: Double, // 1.5R (Lock 40% & Move SL to Break-Even)
    val tp2: Double, // 2.5R (Lock 40% & Activate 1.5x ATR TSL)
    val tp3: Double, // 4.0R+ (Final 20% Runner)
    val atr14AtLock: Double,
    val currentPrice: Double,
    val highestPriceReached: Double,
    val lowestPriceReached: Double,
    val currentTrailingStop: Double,
    val tslPhaseLabel: String, // "Initial Risk Protected", "Risk-Free: Locked at Break-Even", "ATR Runner Ratchet (1.5x ATR)"
    val tp1Hit: Boolean = false,
    val tp2Hit: Boolean = false,
    val tp3Hit: Boolean = false,
    val isClosed: Boolean = false,
    val status: String = "ACTIVE", // "ACTIVE", "TP1_HIT", "TP2_HIT", "TP3_HIT", "TSL_CLOSED", "EARLY_EXIT", "STOPPED_OUT"
    val earlyExitRecommended: Boolean = false,
    val earlyExitReason: String = "",
    val earlyExitSuggestedPrice: Double = 0.0,
    val mfePct: Double = 0.0, // Max Favorable Excursion %
    val maePct: Double = 0.0, // Max Adverse Excursion %
    val currentRMultiple: Double = 0.0,
    val watermarkId: String = "OrixVex Non-Repainting Verified",
    val isNonRepaintingConfirmed: Boolean = true,
    val trailingStopActivationRule: String = "Move SL to Break-Even at TP1; trail by 1.5x ATR after TP2",
    val trailingStopAtrMultiplier: Double = 1.5,
    val emergencyExitPriceCondition: String = "",
    val tp1ActionOnHit: String = "Move SL to Break-Even",
    val tp2ActionOnHit: String = "Activate 1.5x ATR Trailing Stop",
    val tp3ActionOnHit: String = "Full Close / Trail Under 5m Swing Low"
)

data class SignalStrengthBreakdown(
    val overallScore: Int = 82,
    val institutionalGrade: String = "A+", // S, A+, A, B, C
    val smcStructureScore35: Int = 30, // Out of 35
    val mtfTrendMomentumScore30: Int = 25, // Out of 30
    val orderFlowVolumeScore20: Int = 15, // Out of 20
    val newsMacroSentimentScore15: Int = 12, // Out of 15
    val smcScore100: Int = 86,
    val technicalScore100: Int = 83,
    val orderFlowScore100: Int = 78,
    val newsSentimentScore100: Int = 80
)

data class BullsVsBearsPower(
    val bullsPercent: Double = 55.0,
    val bearsPercent: Double = 45.0,
    val takerBuyRatioPct: Double = 54.0,
    val orderBookBidRatioPct: Double = 56.0,
    val emaVwapBiasPct: Double = 60.0,
    val rsiMacdMomentumPct: Double = 52.0,
    val dominantSideLabel: String = "Bulls Controlling Orderflow",
    val orderFlowDeltaSummary: String = ""
)

data class ProjectedSwingRoadmap(
    val microSwingTargetLow: Double = 0.0,
    val microSwingTargetHigh: Double = 0.0,
    val microWindowLabel: String = "Next 4–8 candles",
    val bullishMagnetPrice: Double = 0.0,
    val bullishMagnetLabel: String = "Upside BSL / Bearish OB",
    val bullishProbabilityPct: Int = 64,
    val bullishEstimatedCandles: String = "6-12 bars",
    val bearishMagnetPrice: Double = 0.0,
    val bearishMagnetLabel: String = "Downside SSL / Bullish OB",
    val bearishProbabilityPct: Int = 36,
    val bearishEstimatedCandles: String = "2-4 bars",
    val expectedSwingPathNarrative: String = "",
    val trajectoryNormalizedPoints: List<Float> = listOf(0.45f, 0.35f, 0.30f, 0.48f, 0.68f, 0.85f)
)

data class MtfTimeframeRow(
    val timeframeLabel: String, // "5m", "15m", "1H", "4H", "1D"
    val trend: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val isBullish: Boolean,
    val rsi14: Double,
    val supertrendBullish: Boolean,
    val vwapAbove: Boolean,
    val smcStructure: String
)

data class AutoFibonacciAndPivots(
    val swingHigh: Double = 0.0,
    val swingLow: Double = 0.0,
    val fib382: Double = 0.0,
    val fib500: Double = 0.0,
    val fib618GoldenPocket: Double = 0.0,
    val fib786: Double = 0.0,
    val ext1272: Double = 0.0,
    val ext1618: Double = 0.0,
    val pivotP: Double = 0.0,
    val pivotR1: Double = 0.0,
    val pivotR2: Double = 0.0,
    val pivotS1: Double = 0.0,
    val pivotS2: Double = 0.0
)

data class VolatilitySqueezeRadar(
    val bbUpper: Double = 0.0,
    val bbMiddle: Double = 0.0,
    val bbLower: Double = 0.0,
    val kcUpper: Double = 0.0,
    val kcLower: Double = 0.0,
    val isSqueezeActive: Boolean = false,
    val atrPercent: Double = 0.0,
    val volatilityRegime: String = "Normal Volatility" // "Low Compression (Squeeze)", "Normal Volatility", "High Expansion"
)

data class DeepQuantSuiteTelemetry(
    val mtfRows: List<MtfTimeframeRow> = emptyList(),
    val fibAndPivots: AutoFibonacciAndPivots = AutoFibonacciAndPivots(),
    val squeezeRadar: VolatilitySqueezeRadar = VolatilitySqueezeRadar()
)

@IgnoreExtraProperties
data class TradeJournalEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "BTCUSDT",
    val displayPair: String = "BTC/USD",
    val timeframe: String = "15m",
    val direction: String = "LONG", // "LONG" or "SHORT"
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val positionSizeUsd: Double = 1000.0,
    val pnlUsd: Double = 0.0,
    val rMultiple: Double = 0.0,
    val setupTag: String = "SMC OB", // "SMC OB", "FVG Pullback", "BOS Breakout"
    val mindsetTag: String = "Disciplined", // "Disciplined", "Early Exit", "Hesitated", "FOMO"
    val notes: String = "",
    val importedFromSignalId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class AiCoachPortfolioAudit(
    val overallGrade: String = "A",
    val summaryHeadline: String = "",
    val strengths: List<String> = emptyList(),
    val behavioralLeaks: List<String> = emptyList(),
    val bestPerformingSetup: String = "",
    val riskAdjustments: List<String> = emptyList()
)

data class DashboardAnalyticsSummary(
    val totalSignalsTracked: Int = 0,
    val winRatePercent: Double = 0.0,
    val netCumulativeR: Double = 0.0,
    val netCumulativeReturnPct: Double = 0.0,
    val profitFactor: Double = 0.0,
    val averageRrAchieved: Double = 0.0,
    val currentWinStreak: Int = 0,
    val equityCurveRPoints: List<Float> = emptyList(),
    val tp3HitCount: Int = 0,
    val tp2HitCount: Int = 0,
    val tp1TslProfitCount: Int = 0,
    val earlyExitSavedCount: Int = 0,
    val slHitCount: Int = 0,
    val winRateByPair: Map<String, Float> = emptyMap()
)
