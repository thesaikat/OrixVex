package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveManagedTrade
import com.example.data.model.BullsVsBearsPower
import com.example.data.model.CryptoPair
import com.example.data.model.DeepQuantSuiteTelemetry
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.NewsArticleItem
import com.example.data.model.ProjectedSwingRoadmap
import com.example.data.model.SentimentClassification
import com.example.data.model.SignalStrengthBreakdown
import com.example.data.model.UserQuotaAuthMode
import com.example.data.model.UserQuotaStatus
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1. Non-Repainting ("Non-Printable") Locked Signal Lifecycle Manager Banner
 * Shows immutable signalId, bar-close lock badge, dynamic ratcheting TSL, TP1/TP2/TP3 milestones,
 * MFE/MAE, anti-leak user watermark, and live Early Exit warning banner.
 */
@Composable
fun ActiveTradeLifecycleBanner(
    trade: ActiveManagedTrade,
    decimals: Int,
    onExecuteEarlyExit: () -> Unit,
    onImportToJournal: (ActiveManagedTrade) -> Unit,
    onCopyPrice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    val context = LocalContext.current
    val isBull = trade.isLong
    val accentTone = if (isBull) colors.bullish else colors.bearish
    val rColor = if (trade.currentRMultiple >= 0.0) colors.bullish else colors.bearish

    fun fmt(v: Double) = String.format(Locale.US, "%,.${decimals}f", v)
    val lockTimeStr = remember(trade.lockedAtBarCloseMs) {
        SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).format(Date(trade.lockedAtBarCloseMs))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.5.dp, accentTone.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("active_trade_lifecycle_banner"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Non-Repainting Lock Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Non-Repainting Locked Signal",
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NON-REPAINTING LOCKED • ${trade.signalId}",
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
            }

            Surface(
                color = accentTone.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "${trade.status} • ${String.format(Locale.US, "%+.2fR", trade.currentRMultiple)}",
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = rColor
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Subheader: Order Type + Bar-Close Timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${trade.displayPair} (${trade.timeframe}) • ${trade.orderType}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Bar-Close: $lockTimeStr",
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    color = colors.mutedText
                )
            )
        }

        // Early Exit Alert Banner (if triggered)
        if (trade.earlyExitRecommended && !trade.isClosed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bearish.copy(alpha = 0.15f))
                    .border(1.5.dp, colors.bearish, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("early_exit_warning_banner"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Early Exit Warning",
                        tint = colors.bearish,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚠️ EARLY EXIT RECOMMENDED — STRUCTURE SHIFT DETECTED",
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.bearish
                        )
                    )
                }
                Text(
                    text = trade.earlyExitReason,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = colors.primaryText
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suggested Exit: $${fmt(trade.earlyExitSuggestedPrice)}",
                        style = TabularMonoStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.bearish
                        )
                    )
                    Button(
                        onClick = onExecuteEarlyExit,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.bearish),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("execute_early_exit_button")
                    ) {
                        Text(
                            text = "Protect Capital / Exit Now",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // Dynamic Trailing Stop Loss (TSL) Box
        Surface(
            color = colors.surfaceElevated,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .clickable {
                    val priceText = fmt(trade.currentTrailingStop)
                    copyText(context, "Live TSL", priceText)
                    onCopyPrice("Live TSL $$priceText")
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "LIVE TRAILING STOP LOSS (TSL)",
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = trade.tslPhaseLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            color = colors.mutedText
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$${fmt(trade.currentTrailingStop)}",
                            style = TabularMonoStyle.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                        Text(
                            text = "Locked SL: $${fmt(trade.initialStopLoss)}",
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                color = colors.mutedText
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy TSL",
                        tint = colors.mutedText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 3-Tiered Take-Profit Milestones (TP1 40% BE, TP2 40% ATR TSL, TP3 20% Runner)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TpMilestoneChip(
                label = "TP1 (1.5R • 40%)",
                subLabel = "Move SL -> BE",
                priceText = "$${fmt(trade.tp1)}",
                isHit = trade.tp1Hit,
                modifier = Modifier.weight(1f)
            )
            TpMilestoneChip(
                label = "TP2 (2.5R • 40%)",
                subLabel = "1.5x ATR Trail",
                priceText = "$${fmt(trade.tp2)}",
                isHit = trade.tp2Hit,
                modifier = Modifier.weight(1f)
            )
            TpMilestoneChip(
                label = "TP3 (4.0R • 20%)",
                subLabel = "Final Runner",
                priceText = "$${fmt(trade.tp3)}",
                isHit = trade.tp3Hit,
                modifier = Modifier.weight(1f)
            )
        }

        // MFE / MAE Excursion + Import to Journal Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = String.format(Locale.US, "MFE: +%.2f%%", trade.mfePct),
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.bullish
                    )
                )
                Text(
                    text = String.format(Locale.US, "MAE: -%.2f%%", trade.maePct),
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.bearish
                    )
                )
            }

            OutlinedButton(
                onClick = { onImportToJournal(trade) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("import_trade_to_journal_button")
            ) {
                Text(
                    text = "Log in Journal",
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
            }
        }

        // Subtle Anti-Leak Watermark Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = trade.watermarkId,
                style = TabularMonoStyle.copy(
                    fontSize = 9.sp,
                    color = colors.mutedText.copy(alpha = 0.65f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = colors.accent.copy(alpha = 0.65f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun TpMilestoneChip(
    label: String,
    subLabel: String,
    priceText: String,
    isHit: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    val tone = if (isHit) colors.bullish else colors.mutedText
    Surface(
        color = if (isHit) colors.bullish.copy(alpha = 0.14f) else colors.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(
            width = 1.dp,
            color = if (isHit) colors.bullish else colors.border,
            shape = RoundedCornerShape(8.dp)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isHit) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.bullish,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = TabularMonoStyle.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = tone
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = priceText,
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            )
            Text(
                text = subLabel,
                style = TabularMonoStyle.copy(
                    fontSize = 8.sp,
                    color = colors.mutedText
                )
            )
        }
    }
}

/**
 * 2. AI Signal Strength Gauge (0-100% + Grade S/A+/A/B/C), Live Bulls vs. Bears Power Meter,
 * and Possible Future Swings & Trajectory Projection Canvas.
 */
@Composable
fun AiMarketDynamicsPanel(
    pair: CryptoPair,
    strength: SignalStrengthBreakdown,
    bullsVsBears: BullsVsBearsPower,
    swingRoadmap: ProjectedSwingRoadmap,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    val dec = pair.priceDecimals
    fun fmt(v: Double) = String.format(Locale.US, "$%,.${dec}f", v)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("ai_market_dynamics_panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section A: AI Signal Strength & Institutional Grade Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI SIGNAL STRENGTH & CONFLUENCE GRADE",
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
                Text(
                    text = "4-Pillar Institutional Confluence Synthesis",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = colors.mutedText
                    )
                )
            }

            Surface(
                color = colors.accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(1.dp, colors.accent, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strength.overallScore}%",
                        style = TabularMonoStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GRADE ${strength.institutionalGrade}",
                        style = TabularMonoStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                }
            }
        }

        // 4 Weighted Sub-Bars
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WeightedPillarBar(
                label = "SMC Structure & PD Arrays (35%)",
                score = strength.smcStructureScore35,
                maxScore = 35,
                barColor = colors.accent
            )
            WeightedPillarBar(
                label = "Multi-Timeframe Trend & Momentum (30%)",
                score = strength.mtfTrendMomentumScore30,
                maxScore = 30,
                barColor = colors.bullish
            )
            WeightedPillarBar(
                label = "Order Flow & Volume Delta (20%)",
                score = strength.orderFlowVolumeScore20,
                maxScore = 20,
                barColor = colors.neutral
            )
            WeightedPillarBar(
                label = "News & Macro Sentiment (15%)",
                score = strength.newsMacroSentimentScore15,
                maxScore = 15,
                barColor = colors.accent
            )
        }

        HorizontalDivider(color = colors.border)

        // Section B: Live Bulls vs. Bears Power Meter
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "BULLS %.1f%%", bullsVsBears.bullsPercent),
                    style = TabularMonoStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.bullish
                    )
                )
                Text(
                    text = bullsVsBears.dominantSideLabel,
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.mutedText
                    )
                )
                Text(
                    text = String.format(Locale.US, "BEARS %.1f%%", bullsVsBears.bearsPercent),
                    style = TabularMonoStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.bearish
                    )
                )
            }

            // Dual Tug-of-War Bar
            val bullWeight = (bullsVsBears.bullsPercent / 100.0).toFloat().coerceIn(0.12f, 0.88f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.bearish)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bullWeight)
                        .background(colors.bullish)
                )
            }

            // 4 Orderflow Driver Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniMetricPill("Taker Buy", String.format(Locale.US, "%.0f%%", bullsVsBears.takerBuyRatioPct))
                MiniMetricPill("OB Depth Bid", String.format(Locale.US, "%.0f%%", bullsVsBears.orderBookBidRatioPct))
                MiniMetricPill("EMA/VWAP Bias", String.format(Locale.US, "%.0f%%", bullsVsBears.emaVwapBiasPct))
                MiniMetricPill("RSI/MACD", String.format(Locale.US, "%.0f%%", bullsVsBears.rsiMacdMomentumPct))
            }
        }

        HorizontalDivider(color = colors.border)

        // Section C: Possible Future Swings & Trajectory Projection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PROJECTED SWING ROADMAP (${swingRoadmap.microWindowLabel})",
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                }
            }

            Text(
                text = swingRoadmap.expectedSwingPathNarrative,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Medium
                )
            )

            // Trajectory Projection Curve Canvas
            ProjectedTrajectoryCanvas(
                points = swingRoadmap.trajectoryNormalizedPoints,
                isBullish = swingRoadmap.bullishProbabilityPct >= swingRoadmap.bearishProbabilityPct,
                microZoneLabel = "Micro Swing Zone: ${fmt(swingRoadmap.microSwingTargetLow)} – ${fmt(swingRoadmap.microSwingTargetHigh)}"
            )

            // Bullish vs Bearish Liquidity Magnets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = colors.bullish.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, colors.bullish.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "BULLISH MAGNET (${swingRoadmap.bullishProbabilityPct}% Prob)",
                            style = TabularMonoStyle.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.bullish
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fmt(swingRoadmap.bullishMagnetPrice),
                            style = TabularMonoStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                        Text(
                            text = "Upside BSL / Bearish OB",
                            style = TabularMonoStyle.copy(
                                fontSize = 9.sp,
                                color = colors.mutedText
                            )
                        )
                    }
                }

                Surface(
                    color = colors.bearish.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, colors.bearish.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "BEARISH MAGNET (${swingRoadmap.bearishProbabilityPct}% Prob)",
                            style = TabularMonoStyle.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.bearish
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fmt(swingRoadmap.bearishMagnetPrice),
                            style = TabularMonoStyle.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                        Text(
                            text = "Downside SSL / Bullish OB",
                            style = TabularMonoStyle.copy(
                                fontSize = 9.sp,
                                color = colors.mutedText
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightedPillarBar(
    label: String,
    score: Int,
    maxScore: Int,
    barColor: Color
) {
    val colors = LocalOrixColors.current
    val progress = (score.toFloat() / maxScore.toFloat()).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.mutedText)
            )
            Text(
                text = "$score/$maxScore",
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = barColor,
            trackColor = colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun MiniMetricPill(label: String, value: String) {
    val colors = LocalOrixColors.current
    Surface(
        color = colors.surfaceElevated,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.mutedText)
            )
            Text(
                text = value,
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            )
        }
    }
}

@Composable
private fun ProjectedTrajectoryCanvas(
    points: List<Float>,
    isBullish: Boolean,
    microZoneLabel: String
) {
    val colors = LocalOrixColors.current
    val curveColor = if (isBullish) colors.bullish else colors.bearish
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (points.size < 2) return@Canvas
            val w = size.width
            val h = size.height
            val stepX = w / (points.size - 1).coerceAtLeast(1)

            val path = Path()
            points.forEachIndexed { idx, normY ->
                val x = idx * stepX
                val y = h - (normY.coerceIn(0.05f, 0.95f) * h)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = curveColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )

            points.forEachIndexed { idx, normY ->
                val x = idx * stepX
                val y = h - (normY.coerceIn(0.05f, 0.95f) * h)
                drawCircle(
                    color = if (idx == 2) colors.accent else curveColor,
                    radius = if (idx == 2 || idx == points.lastIndex) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        Text(
            text = microZoneLabel,
            style = TabularMonoStyle.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            ),
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

/**
 * 3. Expanded Technical & Fundamental Analysis Suite ("Deep Quant & Fundamentals" Tabbed Drawer)
 * Tabs: MTF Matrix (5m-1D) | Auto-Fib & Pivots | Squeeze Radar | Whale Walls
 */
@Composable
fun DeepQuantAndFundamentalsDrawer(
    pair: CryptoPair,
    deepQuant: DeepQuantSuiteTelemetry,
    fundamentals: FundamentalMacroSnapshot,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("MTF Matrix", "Auto-Fib & Pivots", "Squeeze Radar", "Whale Walls", "News & Fear/Greed")
    val dec = pair.priceDecimals
    fun fmt(v: Double) = String.format(Locale.US, "$%,.${dec}f", v)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("deep_quant_fundamentals_drawer"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "DEEP QUANT & INSTITUTIONAL FUNDAMENTALS SUITE",
            style = TabularMonoStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        )

        // Sub-Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Surface(
                    color = if (isSelected) colors.accent else colors.surfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (isSelected) colors.accent else colors.border,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = index }
                        .testTag("deep_quant_tab_$index")
                ) {
                    Text(
                        text = title,
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else colors.mutedText
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // Multi-Timeframe Alignment Matrix (5m, 15m, 1H, 4H, 1D)
                if (deepQuant.mtfRows.isEmpty()) {
                    Text(
                        text = "Synchronizing 5m / 15m / 1H / 4H / 1D multi-timeframe matrix…",
                        style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.mutedText)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        deepQuant.mtfRows.forEach { row ->
                            val rowTone = when (row.trend) {
                                "BULLISH" -> colors.bullish
                                "BEARISH" -> colors.bearish
                                else -> colors.neutral
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.timeframeLabel,
                                    style = TabularMonoStyle.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText
                                    ),
                                    modifier = Modifier.width(34.dp)
                                )
                                Text(
                                    text = row.trend,
                                    style = TabularMonoStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = rowTone
                                    )
                                )
                                Text(
                                    text = String.format(Locale.US, "RSI %.1f", row.rsi14),
                                    style = TabularMonoStyle.copy(
                                        fontSize = 11.sp,
                                        color = colors.primaryText
                                    )
                                )
                                Text(
                                    text = if (row.vwapAbove) "Above VWAP" else "Below VWAP",
                                    style = TabularMonoStyle.copy(
                                        fontSize = 10.sp,
                                        color = if (row.vwapAbove) colors.bullish else colors.bearish
                                    )
                                )
                                Text(
                                    text = row.smcStructure,
                                    style = TabularMonoStyle.copy(
                                        fontSize = 10.sp,
                                        color = colors.accent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // Auto-Fibonacci & Classic Pivot Points
                val fp = deepQuant.fibAndPivots
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Auto-Fibonacci Retracements & Extensions",
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.mutedText
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMetricPill("0.382", fmt(fp.fib382))
                        MiniMetricPill("0.50 Eq", fmt(fp.fib500))
                        MiniMetricPill("0.618 GP", fmt(fp.fib618GoldenPocket))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMetricPill("0.786 OTE", fmt(fp.fib786))
                        MiniMetricPill("Ext 1.272", fmt(fp.ext1272))
                        MiniMetricPill("Ext 1.618", fmt(fp.ext1618))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Classic Session Pivot Points (P, R1, R2, S1, S2)",
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.mutedText
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniMetricPill("Pivot P", fmt(fp.pivotP))
                        MiniMetricPill("R1", fmt(fp.pivotR1))
                        MiniMetricPill("R2", fmt(fp.pivotR2))
                        MiniMetricPill("S1", fmt(fp.pivotS1))
                        MiniMetricPill("S2", fmt(fp.pivotS2))
                    }
                }
            }

            2 -> {
                // Volatility & Squeeze Radar (Bollinger vs Keltner)
                val sq = deepQuant.squeezeRadar
                val sqTone = if (sq.isSqueezeActive) colors.neutral else colors.bullish
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = sqTone.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, sqTone, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = sq.volatilityRegime,
                            style = TabularMonoStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = sqTone
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniMetricPill("BB Upper", fmt(sq.bbUpper))
                        MiniMetricPill("BB Lower", fmt(sq.bbLower))
                        MiniMetricPill("ATR%", String.format(Locale.US, "%.2f%%", sq.atrPercent))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniMetricPill("Keltner Upper", fmt(sq.kcUpper))
                        MiniMetricPill("Keltner Lower", fmt(sq.kcLower))
                    }
                }
            }

            3 -> {
                // Order Book Whale Wall Scanner (@depth20)
                val depth = fundamentals.orderBookDepth
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Top-20 Order Book Imbalance: %+.1f%% (Bids %.1f%% / Asks %.1f%%)",
                            depth.bidAskImbalancePct,
                            depth.bidRatioPct,
                            depth.askRatioPct
                        ),
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (depth.bidAskImbalancePct >= 0) colors.bullish else colors.bearish
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = colors.bullish.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, colors.bullish.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "LARGEST BID SUPPORT WALL",
                                    style = TabularMonoStyle.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.bullish
                                    )
                                )
                                Text(
                                    text = depth.largestBidWall?.let { fmt(it.price) } ?: "Scanning @depth20…",
                                    style = TabularMonoStyle.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText
                                    )
                                )
                                Text(
                                    text = depth.largestBidWall?.let {
                                        String.format(Locale.US, "Qty %.2f ($%,.0f)", it.quantity, it.notionalUsd)
                                    } ?: "Live Binance Depth",
                                    style = TabularMonoStyle.copy(fontSize = 9.sp, color = colors.mutedText)
                                )
                            }
                        }

                        Surface(
                            color = colors.bearish.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, colors.bearish.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "LARGEST ASK RESISTANCE WALL",
                                    style = TabularMonoStyle.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.bearish
                                    )
                                )
                                Text(
                                    text = depth.largestAskWall?.let { fmt(it.price) } ?: "Scanning @depth20…",
                                    style = TabularMonoStyle.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText
                                    )
                                )
                                Text(
                                    text = depth.largestAskWall?.let {
                                        String.format(Locale.US, "Qty %.2f ($%,.0f)", it.quantity, it.notionalUsd)
                                    } ?: "Live Binance Depth",
                                    style = TabularMonoStyle.copy(fontSize = 9.sp, color = colors.mutedText)
                                )
                            }
                        }
                    }
                }
            }

            4 -> {
                // Fear & Greed Index + Top Parsed Crypto RSS Headlines inside Drawer
                val fg = fundamentals.fearAndGreed
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniMetricPill(
                            "Fear & Greed",
                            "${fg.currentValue}/100 (${fg.currentClassification})"
                        )
                        MiniMetricPill(
                            "News Score",
                            String.format(Locale.US, "%.0f/100", fundamentals.netNewsSentimentScore)
                        )
                    }
                    fundamentals.articles.take(3).forEach { art ->
                        TerminalRssHeadlineRow(article = art)
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Terminal View Card for Crypto Fear & Greed Index (0-100 radial gauge + 7-day trend)
 * and Live Parsed Crypto-Market RSS Feeds (Google News RSS, Cointelegraph RSS, CryptoCompare).
 */
@Composable
fun TerminalFearGreedAndRssCard(
    pair: CryptoPair,
    fundamentals: FundamentalMacroSnapshot,
    onRefreshNews: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    val fg = fundamentals.fearAndGreed
    val fgTone = when {
        fg.currentValue >= 60 -> colors.bullish
        fg.currentValue <= 40 -> colors.bearish
        else -> colors.neutral
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("terminal_fear_greed_rss_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row with Refresh Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FEAR & GREED INDEX + LIVE CRYPTO RSS CATALYSTS",
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
                Text(
                    text = "${pair.baseAsset} RSS Stream (Google News • Cointelegraph • Alternative.me)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = colors.mutedText
                    )
                )
            }

            IconButton(
                onClick = onRefreshNews,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceElevated)
                    .testTag("terminal_refresh_rss_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh RSS & Fear/Greed",
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Fear & Greed Radial Gauge + 7-Day Trend + Net News Score Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceElevated)
                .padding(10.dp)
                .testTag("terminal_fear_greed_gauge"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 5.5.dp.toPx()
                        drawArc(
                            color = colors.border,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = fgTone,
                            startAngle = 135f,
                            sweepAngle = (fg.currentValue.coerceIn(0, 100) / 100f) * 270f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${fg.currentValue}",
                            style = TabularMonoStyle.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                        Text(
                            text = "F&G",
                            style = TabularMonoStyle.copy(fontSize = 7.sp, color = colors.mutedText)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "${fg.currentClassification} (${fg.currentValue}/100)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = fgTone,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                    val historyTrail = fg.history7d.joinToString("→") { "${it.value}" }
                    if (historyTrail.isNotBlank()) {
                        Text(
                            text = "7d: $historyTrail",
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                color = colors.mutedText
                            )
                        )
                    }
                }
            }

            Surface(
                color = (if (fundamentals.netNewsSentimentScore >= 50.0) colors.bullish else colors.bearish).copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "RSS SENTIMENT",
                        style = TabularMonoStyle.copy(fontSize = 8.sp, color = colors.mutedText)
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f/100", fundamentals.netNewsSentimentScore),
                        style = TabularMonoStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (fundamentals.netNewsSentimentScore >= 50.0) colors.bullish else colors.bearish
                        )
                    )
                }
            }
        }

        // Top 4 Parsed RSS Headlines List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("terminal_rss_headline_list"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            fundamentals.articles.take(4).forEach { article ->
                TerminalRssHeadlineRow(article = article)
            }
        }
    }
}

@Composable
private fun TerminalRssHeadlineRow(article: NewsArticleItem) {
    val colors = LocalOrixColors.current
    val tone = when (article.sentiment) {
        SentimentClassification.BULLISH -> colors.bullish
        SentimentClassification.BEARISH -> colors.bearish
        SentimentClassification.HIGH_IMPACT_MACRO -> colors.neutral
        SentimentClassification.NEUTRAL -> colors.accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Newspaper,
            contentDescription = null,
            tint = tone,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = tone.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = article.sentiment.label,
                        style = TabularMonoStyle.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = tone
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "${article.source} • ${article.publishedAgo}",
                    style = TabularMonoStyle.copy(fontSize = 9.sp, color = colors.mutedText)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 4. "My Gemini Quota" 3-Tier User Quota Modal Dialog
 */
@Composable
fun MyGeminiQuotaDialog(
    quotaStatus: UserQuotaStatus,
    initialPersonalKey: String,
    initialOAuthToken: String,
    onDismiss: () -> Unit,
    onSaveConfiguration: (
        mode: UserQuotaAuthMode,
        personalKey: String,
        oauthToken: String,
        deepWebGrounding: Boolean
    ) -> Unit
) {
    val colors = LocalOrixColors.current
    var selectedMode by remember { mutableStateOf(quotaStatus.mode) }
    var personalKey by remember { mutableStateOf(initialPersonalKey) }
    var oauthToken by remember { mutableStateOf(initialOAuthToken) }
    var deepWebEnabled by remember { mutableStateOf(quotaStatus.deepWebSearchGroundingEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "My Gemini Quota & Rate-Limit Vault",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Live Usage Meter
                Surface(
                    color = colors.surfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LIVE USER QUOTA TELEMETRY",
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        )
                        Text(
                            text = "Burst RPM: ${quotaStatus.rpmUsed}/${quotaStatus.rpmLimit} RPM • Daily: ${quotaStatus.rpdUsed}/${quotaStatus.rpdLimit} RPD",
                            style = TabularMonoStyle.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                        if (quotaStatus.cooldownRemainingSec > 0) {
                            Text(
                                text = "Cooldown active: next slot in ${quotaStatus.cooldownRemainingSec}s",
                                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.neutral)
                            )
                        }
                    }
                }

                // 3-Tier Quota Selector
                UserQuotaAuthMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Surface(
                        color = if (isSelected) colors.accent.copy(alpha = 0.15f) else colors.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSelected) colors.accent else colors.border,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedMode = mode }
                            .testTag("quota_mode_${mode.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = mode.label,
                                tint = if (isSelected) colors.accent else colors.mutedText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 12.sp,
                                        color = colors.primaryText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = when (mode) {
                                        UserQuotaAuthMode.FIREBASE_AI_STUDIO -> "1-Tap AI Studio Native / App Check Quota (Default)"
                                        UserQuotaAuthMode.GOOGLE_OAUTH_BEARER -> "Send user OAuth Bearer token to generativelanguage.googleapis.com"
                                        UserQuotaAuthMode.PERSONAL_API_KEY -> "Encrypted per-user Gemini API key (AIza...) stored locally by UID"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 10.sp,
                                        color = colors.mutedText
                                    )
                                )
                            }
                        }
                    }
                }

                if (selectedMode == UserQuotaAuthMode.PERSONAL_API_KEY) {
                    OutlinedTextField(
                        value = personalKey,
                        onValueChange = { personalKey = it },
                        label = { Text("Personal Gemini API Key (AIza...)", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = TabularMonoStyle.copy(fontSize = 12.sp, color = colors.primaryText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personal_gemini_key_input")
                    )
                }

                if (selectedMode == UserQuotaAuthMode.GOOGLE_OAUTH_BEARER) {
                    OutlinedTextField(
                        value = oauthToken,
                        onValueChange = { oauthToken = it },
                        label = { Text("Google OAuth Bearer Access Token (ya29...)", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = TabularMonoStyle.copy(fontSize = 12.sp, color = colors.primaryText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("oauth_bearer_token_input")
                    )
                }

                // Deep Live Web Catalyst Scan Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Deep Live Web Catalyst Scan",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                color = colors.primaryText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Include live Google News RSS & CryptoCompare catalysts in AI prompt",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 10.sp,
                                color = colors.mutedText
                            )
                        )
                    }
                    Switch(
                        checked = deepWebEnabled,
                        onCheckedChange = { deepWebEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveConfiguration(selectedMode, personalKey, oauthToken, deepWebEnabled)
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.testTag("save_quota_config_button")
            ) {
                Text("Save Quota Settings", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.mutedText)
            }
        }
    )
}

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
}
