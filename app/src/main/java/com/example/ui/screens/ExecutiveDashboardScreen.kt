package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DashboardAnalyticsSummary
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExecutiveDashboardScreen(
    summary: DashboardAnalyticsSummary
) {
    val colors = LocalOrixColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("executive_dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = "Executive Performance & Analytics",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Verified Non-Repainting Signal Expectancy, R-Multiple Equity & Pair Attribution",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.mutedText,
                    fontSize = 12.sp
                )
            )
        }

        // Top 6 KPI Summary Cards (2x3 Grid)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2
        ) {
            KpiStatCard(
                title = "TOTAL SIGNALS TRACKED",
                value = "${summary.totalSignalsTracked}",
                subValue = "Bar-Close Verified",
                accent = colors.accent,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = "VERIFIED WIN RATE",
                value = String.format(Locale.US, "%.1f%%", summary.winRatePercent),
                subValue = "TP1+ / TSL Profit",
                accent = colors.bullish,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = "NET CUMULATIVE R",
                value = String.format(Locale.US, "%+.2fR", summary.netCumulativeR),
                subValue = String.format(Locale.US, "%+.2f%% Return", summary.netCumulativeReturnPct),
                accent = if (summary.netCumulativeR >= 0) colors.bullish else colors.bearish,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = "PROFIT FACTOR",
                value = String.format(Locale.US, "%.2f", summary.profitFactor),
                subValue = "Gross Win / Loss",
                accent = colors.accent,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = "AVG R:R ACHIEVED",
                value = String.format(Locale.US, "1 : %.2f", summary.averageRrAchieved),
                subValue = "Realized Expectancy",
                accent = colors.bullish,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = "CURRENT WIN STREAK",
                value = "${summary.currentWinStreak} Trades",
                subValue = "Active Discipline",
                accent = colors.neutral,
                modifier = Modifier.weight(1f)
            )
        }

        // Chart 1: Cumulative Equity & R-Multiple Growth Curve (Area Chart)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUMULATIVE EQUITY & R-MULTIPLE CURVE",
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
                Text(
                    text = String.format(Locale.US, "%+.2fR Net", summary.netCumulativeR),
                    style = TabularMonoStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.bullish
                    )
                )
            }

            EquityAreaCurveCanvas(
                points = summary.equityCurveRPoints.ifEmpty { listOf(0f, 1.5f, 4.0f, 3.0f, 5.5f, 8.6f) },
                lineColor = colors.bullish
            )
        }

        // Chart 2: Outcome Breakdown Donut Chart
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SIGNAL OUTCOME BREAKDOWN (DONUT)",
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutcomeDonutCanvas(
                    tp3 = summary.tp3HitCount,
                    tp2 = summary.tp2HitCount,
                    tp1Tsl = summary.tp1TslProfitCount,
                    earlyExit = summary.earlyExitSavedCount,
                    sl = summary.slHitCount
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DonutLegendItem("TP3 Hit (4.0R)", summary.tp3HitCount, Color(0xFF10B981))
                    DonutLegendItem("TP2 Hit (2.5R)", summary.tp2HitCount, Color(0xFF3B82F6))
                    DonutLegendItem("TP1 / TSL Profit", summary.tp1TslProfitCount, Color(0xFF06B6D4))
                    DonutLegendItem("Early Exit Saved", summary.earlyExitSavedCount, Color(0xFFF59E0B))
                    DonutLegendItem("SL Hit (-1.0R)", summary.slHitCount, Color(0xFFF43F5E))
                }
            }
        }

        // Chart 3: Win Rate by Crypto Pair Bar Chart
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "WIN RATE BY CRYPTO PAIR (%)",
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            )

            summary.winRateByPair.forEach { (pairName, ratePct) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = pairName,
                            style = TabularMonoStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f%%", ratePct),
                            style = TabularMonoStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.bullish
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((ratePct / 100f).coerceIn(0.1f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.bullish)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    subValue: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    Surface(
        color = colors.surfaceCard,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, colors.border, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = TabularMonoStyle.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedText
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = TabularMonoStyle.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            )
            Text(
                text = subValue,
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    color = colors.mutedText
                )
            )
        }
    }
}

@Composable
private fun EquityAreaCurveCanvas(
    points: List<Float>,
    lineColor: Color
) {
    val colors = LocalOrixColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (points.size < 2) return@Canvas
            val minV = points.minOrNull() ?: 0f
            val maxV = (points.maxOrNull() ?: 5f).coerceAtLeast(minV + 1f)
            val span = (maxV - minV).coerceAtLeast(1f)
            val w = size.width
            val h = size.height
            val stepX = w / (points.size - 1).coerceAtLeast(1)

            val strokePath = Path()
            val fillPath = Path()

            points.forEachIndexed { i, v ->
                val x = i * stepX
                val y = h - ((v - minV) / span) * h
                if (i == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.32f), Color.Transparent)
                )
            )
            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            points.forEachIndexed { i, v ->
                val x = i * stepX
                val y = h - ((v - minV) / span) * h
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun OutcomeDonutCanvas(
    tp3: Int,
    tp2: Int,
    tp1Tsl: Int,
    earlyExit: Int,
    sl: Int
) {
    val colors = LocalOrixColors.current
    val total = (tp3 + tp2 + tp1Tsl + earlyExit + sl).coerceAtLeast(1)
    val slices = listOf(
        Pair(tp3, Color(0xFF10B981)),
        Pair(tp2, Color(0xFF3B82F6)),
        Pair(tp1Tsl, Color(0xFF06B6D4)),
        Pair(earlyExit, Color(0xFFF59E0B)),
        Pair(sl, Color(0xFFF43F5E))
    )

    Box(
        modifier = Modifier.size(116.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 14.dp.toPx()
            var startAngle = -90f
            slices.forEach { (count, color) ->
                val sweep = (count.toFloat() / total.toFloat()) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$total",
                style = TabularMonoStyle.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            )
            Text(
                text = "SIGNALS",
                style = TabularMonoStyle.copy(fontSize = 8.sp, color = colors.mutedText)
            )
        }
    }
}

@Composable
private fun DonutLegendItem(label: String, count: Int, color: Color) {
    val colors = LocalOrixColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.mutedText)
            )
        }
        Text(
            text = "$count",
            style = TabularMonoStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
        )
    }
}
