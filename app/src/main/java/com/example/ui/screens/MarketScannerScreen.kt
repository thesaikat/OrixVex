package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CryptoPair
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.LiveTicker
import com.example.data.model.NewsArticleItem
import com.example.data.model.ScannerPairRow
import com.example.data.model.SentimentClassification
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import java.util.Locale

private enum class ScannerFilter(val label: String) {
    ALL("All 10 Pairs"),
    BULLISH("Supertrend Bullish"),
    BEARISH("Supertrend Bearish"),
    SMC_DISCOUNT("SMC Discount (<50%)"),
    RSI_EXTREME("RSI Extremes (<35 / >65)")
}

@Composable
fun MarketScannerScreen(
    scannerRows: List<ScannerPairRow>,
    tickers: Map<CryptoPair, LiveTicker>,
    fundamentals: FundamentalMacroSnapshot = FundamentalMacroSnapshot(),
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onRefreshNews: () -> Unit = {},
    onSelectPairForTerminal: (CryptoPair) -> Unit
) {
    val colors = LocalOrixColors.current
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = 10-Pair Matrix, 1 = Live News & Sentiment
    var activeFilter by remember { mutableStateOf(ScannerFilter.ALL) }

    val filteredRows = remember(scannerRows, activeFilter) {
        when (activeFilter) {
            ScannerFilter.ALL -> scannerRows
            ScannerFilter.BULLISH -> scannerRows.filter { it.supertrendBullish }
            ScannerFilter.BEARISH -> scannerRows.filter { !it.supertrendBullish }
            ScannerFilter.SMC_DISCOUNT -> scannerRows.filter { it.smcDealingZone.startsWith("Discount", ignoreCase = true) }
            ScannerFilter.RSI_EXTREME -> scannerRows.filter { it.rsi14 <= 35.0 || it.rsi14 >= 65.0 }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (activeSubTab == 0) stringResource(R.string.scanner_title) else "Live Trading News & Macro Feed",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = if (activeSubTab == 0) {
                        stringResource(R.string.scanner_subtitle)
                    } else {
                        "Google News RSS + CryptoCompare + Alternative.me Fear & Greed"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.mutedText,
                        fontSize = 12.sp
                    )
                )
            }
            OutlinedButton(
                onClick = {
                    if (activeSubTab == 0) onRefresh() else onRefreshNews()
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("scanner_refresh_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.scanner_refresh),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.scanner_refresh),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Top Sub-Tab Switcher: [10-Pair Live Matrix] | [Live News & Sentiment]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("10-Pair Live Matrix", "Live News & Sentiment (${fundamentals.articles.size})").forEachIndexed { idx, label ->
                val isSelected = activeSubTab == idx
                Surface(
                    color = if (isSelected) colors.accent else colors.surfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(10.dp))
                        .clickable { activeSubTab = idx }
                        .testTag("scanner_subtab_$idx")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 9.dp)
                    ) {
                        Text(
                            text = label,
                            style = TabularMonoStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else colors.mutedText
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeSubTab == 0) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScannerFilter.entries.forEach { filter ->
                    val isSelected = filter == activeFilter
                    Surface(
                        color = if (isSelected) colors.accent else colors.surfaceCard,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(8.dp))
                            .clickable { activeFilter = filter }
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else colors.mutedText
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRows, key = { it.pair.symbol }) { row ->
                    val liveTicker = tickers[row.pair]
                    val displayPrice = liveTicker?.price ?: row.price
                    val displayChange = liveTicker?.priceChangePercent24h ?: row.change24hPercent
                    val displayVol = liveTicker?.volumeUsd24h ?: row.volumeUsd24h

                    ScannerMatrixCard(
                        row = row,
                        livePrice = displayPrice,
                        change24hPercent = displayChange,
                        volumeUsd24h = displayVol,
                        onClick = { onSelectPairForTerminal(row.pair) }
                    )
                }
            }
        } else {
            // Sub-Tab 1: Fear & Greed Gauge + Order Flow + Live News Stream
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FearGreedAndOrderflowOverviewCard(fundamentals = fundamentals)
                }
                items(fundamentals.articles, key = { it.id }) { article ->
                    NewsArticleCard(article = article)
                }
            }
        }
    }
}

@Composable
private fun FearGreedAndOrderflowOverviewCard(
    fundamentals: FundamentalMacroSnapshot
) {
    val colors = LocalOrixColors.current
    val fg = fundamentals.fearAndGreed
    val fgTone = when {
        fg.currentValue >= 60 -> colors.bullish
        fg.currentValue <= 40 -> colors.bearish
        else -> colors.neutral
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("fear_greed_overview_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(62.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 6.dp.toPx()
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                        Text(
                            text = "F&G",
                            style = TabularMonoStyle.copy(
                                fontSize = 8.sp,
                                color = colors.mutedText
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "CRYPTO FEAR & GREED INDEX",
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                    Text(
                        text = "${fg.currentClassification} (${fg.currentValue}/100)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = fgTone,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val trendText = fg.history7d.joinToString(" → ") { "${it.value}" }
                    if (trendText.isNotBlank()) {
                        Text(
                            text = "7d Trend: $trendText",
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                color = colors.mutedText
                            )
                        )
                    }
                }
            }
        }

        // Order Book Imbalance + Taker Buy/Sell Volume + Net News Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MacroStatTile(
                label = "Taker Buy/Sell",
                value = String.format(Locale.US, "%.2fx (%.0f%%)", fundamentals.takerBuySellRatio, fundamentals.takerBuyPercent),
                color = if (fundamentals.takerBuySellRatio >= 1.0) colors.bullish else colors.bearish,
                modifier = Modifier.weight(1f)
            )
            MacroStatTile(
                label = "OB Depth Imb.",
                value = String.format(Locale.US, "%+.1f%%", fundamentals.orderBookDepth.bidAskImbalancePct),
                color = if (fundamentals.orderBookDepth.bidAskImbalancePct >= 0) colors.bullish else colors.bearish,
                modifier = Modifier.weight(1f)
            )
            MacroStatTile(
                label = "News Score",
                value = String.format(Locale.US, "%.0f/100", fundamentals.netNewsSentimentScore),
                color = if (fundamentals.netNewsSentimentScore >= 50) colors.bullish else colors.bearish,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MacroStatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    Surface(
        color = colors.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, colors.border, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label,
                style = TabularMonoStyle.copy(fontSize = 9.sp, color = colors.mutedText)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}

@Composable
private fun NewsArticleCard(article: NewsArticleItem) {
    val colors = LocalOrixColors.current
    val badgeTone = when (article.sentiment) {
        SentimentClassification.BULLISH -> colors.bullish
        SentimentClassification.BEARISH -> colors.bearish
        SentimentClassification.HIGH_IMPACT_MACRO -> colors.neutral
        SentimentClassification.NEUTRAL -> colors.accent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("news_article_card_${article.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = badgeTone.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = article.sentiment.label,
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTone
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(
                text = "${article.source} • ${article.publishedAgo} • Impact ${article.impactScore}",
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    color = colors.mutedText
                )
            )
        }

        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Newspaper,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Composable
private fun ScannerMatrixCard(
    row: ScannerPairRow,
    livePrice: Double,
    change24hPercent: Double,
    volumeUsd24h: Double,
    onClick: () -> Unit
) {
    val colors = LocalOrixColors.current
    val isBullChange = change24hPercent >= 0.0
    val rsiColor = when {
        row.rsi14 >= 70.0 -> colors.bearish
        row.rsi14 <= 30.0 -> colors.bullish
        row.rsi14 >= 53.0 -> colors.bullish
        row.rsi14 <= 47.0 -> colors.bearish
        else -> colors.neutral
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("scanner_row_${row.pair.symbol}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = row.pair.baseAsset,
                        style = TabularMonoStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = row.pair.displayPair,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = colors.primaryText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Vol ${formatCompactUsd(volumeUsd24h)}",
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            color = colors.mutedText
                        )
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${formatDecimal(livePrice, row.pair.priceDecimals)}",
                    style = TabularMonoStyle.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText
                    )
                )
                Text(
                    text = String.format(Locale.US, "%+.2f%%", change24hPercent),
                    style = TabularMonoStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBullChange) colors.bullish else colors.bearish
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quantitative Indicators Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = rsiColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "RSI %.1f", row.rsi14),
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = rsiColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                val stColor = if (row.supertrendBullish) colors.bullish else colors.bearish
                Surface(
                    color = stColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (row.supertrendBullish) "ST BULL" else "ST BEAR",
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = stColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = colors.surfaceElevated,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "VWAP %+.2f%%", row.vwapDeltaPercent),
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            color = colors.mutedText
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open in Terminal",
                tint = colors.accent,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val smcBull = row.smcStructure.contains("Bullish", ignoreCase = true)
        val smcBear = row.smcStructure.contains("Bearish", ignoreCase = true)
        val smcColor = when {
            smcBull -> colors.bullish
            smcBear -> colors.bearish
            else -> colors.accent
        }
        val isDiscount = row.smcDealingZone.startsWith("Discount", ignoreCase = true)
        val zoneColor = if (isDiscount) colors.bullish else colors.bearish

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = smcColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "SMC: ${row.smcStructure}",
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = smcColor
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Surface(
                color = zoneColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Zone: ${row.smcDealingZone}",
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = zoneColor
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
