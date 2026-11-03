package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CryptoPair
import com.example.data.model.SmcTelemetry
import com.example.ui.screens.formatDecimal
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import java.util.Locale

/**
 * Horizontal scrolling institutional Smart Money Concepts (SMC) chip strip placed directly
 * beneath the TradingView chart. Displays deterministic Market Structure (BOS / CHoCH),
 * 50-bar Dealing Zone (Discount / Premium + Equilibrium 0.50 & OTE 0.618-0.786),
 * nearest unmitigated Order Blocks (Bullish & Bearish OB Mean Thresholds), unmitigated Fair Value
 * Gaps (BISI / SIBI Consequent Encroachment 50%), and Liquidity Sweep (BSL / SSL) telemetry.
 */
@Composable
fun SmcStructureStrip(
    pair: CryptoPair = CryptoPair.BTC,
    smc: SmcTelemetry?,
    onOpenSmcJsonModal: () -> Unit = {},
    onCopyCoordinate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    val context = LocalContext.current
    val dec = pair.priceDecimals

    fun copyCoordinateToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        onCopyCoordinate("$label ($value)")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("smc_structure_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Row: SMC Title + Active OTE Badge + SMC JSON Inspector Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SMART MONEY CONCEPTS (SMC) STRUCTURE STRIP",
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (smc != null && smc.isInOteZone) {
                    Surface(
                        color = colors.bullish.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "IN OTE (0.618-0.786)",
                            style = TabularMonoStyle.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.bullish
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    color = colors.accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .clickable { onOpenSmcJsonModal() }
                        .testTag("open_smc_json_payload_button")
                ) {
                    Text(
                        text = "SMC JSON",
                        style = TabularMonoStyle.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        ),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (smc == null) {
            Text(
                text = "Calculating 3-bar fractal pivots, Order Blocks, and Fair Value Gaps…",
                style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.mutedText)
            )
        } else {
            val structColor = if (smc.isStructureBullish) colors.bullish else colors.bearish
            val zoneColor = if (smc.isDiscountZone) colors.bullish else colors.bearish

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("smc_structure_chip_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Market Structure Chip (BOS / CHoCH + Fractal Swing High / Low)
                val breakPriceFormatted = "$${formatDecimal(smc.structureBreakLevel, dec)}"
                SmcTelemetryChip(
                    icon = Icons.Default.AccountTree,
                    label = "Structure",
                    value = smc.structureState,
                    detailLine1 = "Break $breakPriceFormatted (${smc.structureBreakCandlesAgo}b ago)",
                    detailLine2 = "SH $${formatDecimal(smc.confirmedSwingHigh, dec)} • SL $${formatDecimal(smc.confirmedSwingLow, dec)}",
                    accentColor = structColor,
                    testTag = "smc_chip_structure",
                    onClick = {
                        copyCoordinateToClipboard("SMC ${smc.structureState}", breakPriceFormatted)
                    }
                )

                // 2. Dealing Zone Chip (Discount vs Premium + 0.50 EQ + OTE + Visual Position Bar)
                val eqFormatted = "$${formatDecimal(smc.equilibriumPrice, dec)}"
                SmcTelemetryChip(
                    icon = Icons.Default.TrackChanges,
                    label = "Dealing Zone",
                    value = smc.dealingZoneLabel,
                    detailLine1 = "0.50 EQ: $eqFormatted | [$${formatDecimal(smc.dealingRangeLow50, dec)}-$${formatDecimal(smc.dealingRangeHigh50, dec)}]",
                    detailLine2 = "OTE (0.618-0.786): $${formatDecimal(smc.oteLow, dec)} - $${formatDecimal(smc.oteHigh, dec)}",
                    accentColor = zoneColor,
                    progressRatio = (smc.dealingRangePct / 100.0).toFloat().coerceIn(0f, 1f),
                    testTag = "smc_chip_dealing_zone",
                    onClick = {
                        copyCoordinateToClipboard("Dealing Range Equilibrium", eqFormatted)
                    }
                )

                // 3. Nearest Order Blocks (Bullish Demand OB & Bearish Supply OB)
                val bullOb = smc.nearestBullishOb
                val bearOb = smc.nearestBearishOb
                val orderedObs = listOfNotNull(bullOb, bearOb).sortedBy { it.distancePercent }

                if (orderedObs.isEmpty()) {
                    val fallbackObPrice = "$${formatDecimal(smc.confirmedSwingLow, dec)}"
                    SmcTelemetryChip(
                        icon = Icons.Default.ViewAgenda,
                        label = "Nearest OB",
                        value = smc.nearestObSummary,
                        detailLine1 = "Within 5% proximity filter",
                        detailLine2 = "Support Pivot $fallbackObPrice",
                        accentColor = colors.accent,
                        testTag = "smc_chip_nearest_ob",
                        onClick = {
                            copyCoordinateToClipboard("Order Block Support", fallbackObPrice)
                        }
                    )
                } else {
                    orderedObs.forEachIndexed { idx, ob ->
                        val isPrimary = idx == 0
                        val obColor = if (ob.isBullish) colors.bullish else colors.bearish
                        val chipLabel = if (isPrimary) "Nearest OB" else if (ob.isBullish) "Demand OB" else "Supply OB"
                        val mtPrice = "$${formatDecimal(ob.meanThreshold, dec)}"
                        val chipValue = "${if (ob.isBullish) "Bull OB" else "Bear OB"} $mtPrice"
                        SmcTelemetryChip(
                            icon = Icons.Default.ViewAgenda,
                            label = chipLabel,
                            value = chipValue,
                            detailLine1 = "Zone [$${formatDecimal(ob.low, dec)} - $${formatDecimal(ob.high, dec)}]",
                            detailLine2 = String.format(
                                Locale.US,
                                "50%% Mean Threshold: %s (%.2f%% away)",
                                mtPrice,
                                ob.distancePercent
                            ),
                            accentColor = obColor,
                            testTag = if (isPrimary) "smc_chip_nearest_ob" else "smc_chip_secondary_ob",
                            onClick = {
                                copyCoordinateToClipboard("${ob.label} Mean Threshold", mtPrice)
                            }
                        )
                    }
                }

                // 4. Unmitigated Fair Value Gaps (Bullish BISI & Bearish SIBI + Consequent Encroachment 50%)
                val bullFvg = smc.nearestBullishFvg
                val bearFvg = smc.nearestBearishFvg
                val orderedFvgs = listOfNotNull(bullFvg, bearFvg)

                if (orderedFvgs.isEmpty()) {
                    SmcTelemetryChip(
                        icon = Icons.Default.Layers,
                        label = "Active FVG",
                        value = smc.activeFvgSummary,
                        detailLine1 = "No open 3-candle imbalance",
                        detailLine2 = "Market balanced across 20-bar avg",
                        accentColor = colors.primaryText,
                        testTag = "smc_chip_active_fvg",
                        onClick = {
                            copyCoordinateToClipboard("Active FVG", smc.activeFvgSummary)
                        }
                    )
                } else {
                    orderedFvgs.forEachIndexed { idx, fvg ->
                        val isPrimary = idx == 0
                        val fvgColor = if (fvg.isBullish) colors.bullish else colors.bearish
                        val tag = if (fvg.isBullish) "BISI" else "SIBI"
                        val ceFormatted = "$${formatDecimal(fvg.midpoint, dec)}"
                        val rangeFormatted = "$${formatDecimal(fvg.bottom, dec)}-$${formatDecimal(fvg.top, dec)} ($tag)"
                        SmcTelemetryChip(
                            icon = Icons.Default.Layers,
                            label = if (isPrimary) "Active FVG" else "Secondary FVG",
                            value = rangeFormatted,
                            detailLine1 = "50% C.E. Midpoint: $ceFormatted",
                            detailLine2 = String.format(
                                Locale.US,
                                "%s (%.2fx body displacement)",
                                fvg.typeCode,
                                fvg.displacementRatio
                            ),
                            accentColor = fvgColor,
                            testTag = if (isPrimary) "smc_chip_active_fvg" else "smc_chip_secondary_fvg",
                            onClick = {
                                copyCoordinateToClipboard("FVG Consequent Encroachment (50%)", ceFormatted)
                            }
                        )
                    }
                }

                // 5. Liquidity Sweep Chip (BSL / SSL Stop Hunt & Rejection)
                val sweepAccent = if (smc.hasActiveSweep) colors.neutral else colors.mutedText
                SmcTelemetryChip(
                    icon = Icons.Default.WaterDrop,
                    label = "Liquidity Pool",
                    value = if (smc.hasActiveSweep) smc.sweepType.replace("_", " ") else "BSL / SSL Intact",
                    detailLine1 = smc.liquiditySweepStatus,
                    detailLine2 = "BSL $${formatDecimal(smc.confirmedSwingHigh, dec)} • SSL $${formatDecimal(smc.confirmedSwingLow, dec)}",
                    accentColor = sweepAccent,
                    testTag = "smc_chip_liquidity_sweep",
                    onClick = {
                        copyCoordinateToClipboard(
                            "Liquidity Level",
                            "$${formatDecimal(smc.sweptLevel, dec)}"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SmcTelemetryChip(
    icon: ImageVector,
    label: String,
    value: String,
    detailLine1: String,
    detailLine2: String,
    accentColor: Color,
    progressRatio: Float? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val colors = LocalOrixColors.current
    Surface(
        color = colors.surfaceElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "$label: $value",
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = colors.mutedText.copy(alpha = 0.7f),
                    modifier = Modifier.size(11.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detailLine1,
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primaryText
                )
            )
            Text(
                text = detailLine2,
                style = TabularMonoStyle.copy(
                    fontSize = 9.sp,
                    color = colors.mutedText
                )
            )
            if (progressRatio != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressRatio)
                            .background(accentColor)
                    )
                }
            }
        }
    }
}
