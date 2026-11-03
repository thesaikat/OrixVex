package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ActiveManagedTrade
import com.example.data.model.AiSignalResult
import com.example.data.model.CryptoPair
import com.example.data.model.LiveTicker
import com.example.data.model.QuantitativeTelemetry
import com.example.data.model.SmcTelemetry
import com.example.data.model.Timeframe
import com.example.data.remote.WebSocketStatus
import com.example.ui.components.ActiveTradeLifecycleBanner
import com.example.ui.components.AiMarketDynamicsPanel
import com.example.ui.components.DeepQuantAndFundamentalsDrawer
import com.example.ui.components.InteractiveTradingChartSection
import com.example.ui.components.SmcStructureStrip
import com.example.ui.components.TerminalFearGreedAndRssCard
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import com.example.ui.viewmodel.SignalUiState
import com.example.ui.viewmodel.TerminalUiState
import java.util.Locale

@Composable
fun TerminalHomeScreen(
    uiState: TerminalUiState,
    tickers: Map<CryptoPair, LiveTicker>,
    activePairTicker: LiveTicker?,
    wsConnected: Boolean,
    wsStatus: WebSocketStatus,
    onSelectPair: (CryptoPair) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onReconnectStream: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onGenerateSignal: () -> Unit,
    onGenerateSignalFromJson: (String) -> Unit = {},
    onToggleAutoRefresh: (Boolean) -> Unit,
    onBookmarkSignal: () -> Unit,
    onExecuteEarlyExit: () -> Unit = {},
    onImportTradeToJournal: (ActiveManagedTrade) -> Unit = {},
    onRefreshNewsAndFundamentals: () -> Unit = {},
    onCopyPriceToast: (String) -> Unit
) {
    val activeTicker = activePairTicker ?: tickers[uiState.selectedPair]
    val livePrice = activeTicker?.price ?: uiState.telemetry?.currentPrice ?: 0.0
    var showSmcJsonDialog by remember { mutableStateOf(false) }

    if (showSmcJsonDialog) {
        val initialJson = uiState.telemetry?.toInstitutionalJsonString(
            displayPair = uiState.selectedPair.displayPair,
            tradingStyle = uiState.selectedTimeframe.defaultTradingStyle,
            decimals = uiState.selectedPair.priceDecimals
        ) ?: QuantitativeTelemetry.SAMPLE_BTC_15M_SMC_JSON

        SmcJsonPayloadDialog(
            initialJson = initialJson,
            onDismiss = { showSmcJsonDialog = false },
            onEvaluateJson = { jsonPayload ->
                showSmcJsonDialog = false
                onGenerateSignalFromJson(jsonPayload)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Horizontal Pair Selector Ribbon
        PairSelectorRibbon(
            selectedPair = uiState.selectedPair,
            tickers = tickers,
            onSelectPair = onSelectPair
        )

        // 2. Active Pair Live Price & 24h Stats Banner (Hidden when chart is fullscreen)
        if (!uiState.isChartFullscreen) {
            ActivePairHeaderCard(
                pair = uiState.selectedPair,
                ticker = activeTicker,
                fallbackPrice = livePrice,
                wsConnected = wsConnected,
                wsStatus = wsStatus,
                onReconnectStream = onReconnectStream
            )
        }

        // 3. Full Interactive TradingView Chart + QuantCanvas
        InteractiveTradingChartSection(
            selectedPair = uiState.selectedPair,
            selectedTimeframe = uiState.selectedTimeframe,
            isDarkTheme = uiState.isDarkTheme,
            candles = uiState.candles,
            telemetry = uiState.telemetry,
            isFullscreen = uiState.isChartFullscreen,
            onTimeframeSelect = onSelectTimeframe,
            onToggleFullscreen = onToggleFullscreen
        )

        // 4. Institutional SMC Structure Strip (Directly Beneath Chart)
        SmcStructureStrip(
            pair = uiState.selectedPair,
            smc = uiState.telemetry?.smc,
            onOpenSmcJsonModal = { showSmcJsonDialog = true },
            onCopyCoordinate = onCopyPriceToast
        )

        // 5. Deep Quant & Fundamentals Tabbed Drawer (MTF 5m-1D, Auto-Fib & Pivots, Squeeze Radar, Whale Walls, News & Fear/Greed)
        DeepQuantAndFundamentalsDrawer(
            pair = uiState.selectedPair,
            deepQuant = uiState.deepQuantSuite,
            fundamentals = uiState.fundamentals
        )

        // 6. Terminal View Fear & Greed Index (0-100 Gauge + 7d Trend) & Live Crypto RSS Feed
        TerminalFearGreedAndRssCard(
            pair = uiState.selectedPair,
            fundamentals = uiState.fundamentals,
            onRefreshNews = onRefreshNewsAndFundamentals
        )

        // 7. AI Signal Strength (Grade S/A+/A/B/C), Live Bulls vs Bears Power Meter & Projected Swing Roadmap
        AiMarketDynamicsPanel(
            pair = uiState.selectedPair,
            strength = uiState.signalStrengthBreakdown,
            bullsVsBears = uiState.bullsVsBearsPower,
            swingRoadmap = uiState.projectedSwingRoadmap
        )

        // 8. Non-Repainting Locked Signal Lifecycle Manager (Live TSL & Early Exit Monitor)
        val managedTrade = uiState.activeManagedTrade
        if (managedTrade != null && managedTrade.symbol == uiState.selectedPair.symbol) {
            ActiveTradeLifecycleBanner(
                trade = managedTrade,
                decimals = uiState.selectedPair.priceDecimals,
                onExecuteEarlyExit = onExecuteEarlyExit,
                onImportToJournal = onImportTradeToJournal,
                onCopyPrice = onCopyPriceToast
            )
        }

        // 9. Live Technical Telemetry Bar
        LiveTelemetrySection(
            pair = uiState.selectedPair,
            telemetry = uiState.telemetry,
            isLoading = uiState.isLoadingCandles
        )

        // 9. Generate AI Signal CTA + Auto-Refresh Bar
        AiSignalActionRow(
            signalState = uiState.signalState,
            autoRefresh = uiState.autoRefreshAiSignal,
            onGenerateSignal = onGenerateSignal,
            onToggleAutoRefresh = onToggleAutoRefresh
        )

        // 10. Structured OrixVex AI Signal Card (with Collapsible Smart Money Blueprint)
        when (val sState = uiState.signalState) {
            is SignalUiState.Idle -> {
                IdleSignalPromptCard(
                    pair = uiState.selectedPair,
                    timeframe = uiState.selectedTimeframe,
                    onOpenSmcJsonSimulator = { showSmcJsonDialog = true }
                )
            }
            is SignalUiState.Loading -> {
                LoadingSignalSkeletonCard()
            }
            is SignalUiState.Error -> {
                ErrorSignalCard(
                    message = sState.message,
                    onRetry = onGenerateSignal
                )
            }
            is SignalUiState.Success -> {
                OrixVexAiSignalCard(
                    signalResult = sState.result,
                    decimals = uiState.selectedPair.priceDecimals,
                    onBookmark = onBookmarkSignal,
                    onCopyPrice = onCopyPriceToast
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}


@Composable
private fun PairSelectorRibbon(
    selectedPair: CryptoPair,
    tickers: Map<CryptoPair, LiveTicker>,
    onSelectPair: (CryptoPair) -> Unit
) {
    val colors = LocalOrixColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CryptoPair.entries.forEach { pair ->
            val isSelected = pair == selectedPair
            val ticker = tickers[pair]
            val pct = ticker?.priceChangePercent24h ?: 0.0
            val isPositive = pct >= 0.0

            Surface(
                color = if (isSelected) colors.accent.copy(alpha = 0.16f) else colors.surfaceCard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) colors.accent else colors.border,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectPair(pair) }
                    .testTag("pair_chip_${pair.symbol}")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = pair.displayPair,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (isSelected) colors.accent else colors.primaryText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Surface(
                            color = (if (isPositive) colors.bullish else colors.bearish).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%+.2f%%", pct),
                                style = TabularMonoStyle.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isPositive) colors.bullish else colors.bearish
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (ticker != null) {
                            "$${formatDecimal(ticker.price, pair.priceDecimals)}"
                        } else {
                            pair.fullName
                        },
                        style = TabularMonoStyle.copy(
                            fontSize = 12.sp,
                            color = colors.mutedText
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePairHeaderCard(
    pair: CryptoPair,
    ticker: LiveTicker?,
    fallbackPrice: Double,
    wsConnected: Boolean,
    wsStatus: WebSocketStatus,
    onReconnectStream: () -> Unit
) {
    val colors = LocalOrixColors.current
    val tickDir = ticker?.lastTickDirection ?: 0
    val flashColor by animateColorAsState(
        targetValue = when (tickDir) {
            1 -> colors.bullish.copy(alpha = 0.14f)
            -1 -> colors.bearish.copy(alpha = 0.14f)
            else -> colors.surfaceCard
        },
        animationSpec = tween(durationMillis = 350),
        label = "pricePulse"
    )

    val displayPrice = ticker?.price ?: fallbackPrice
    val pct = ticker?.priceChangePercent24h ?: 0.0
    val isBull = pct >= 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(flashColor)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = pair.displayPair,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = pair.fullName,
                        style = MaterialTheme.typography.bodyMedium.copy(color = colors.mutedText)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (displayPrice > 0) "$${formatDecimal(displayPrice, pair.priceDecimals)}" else "—",
                        style = TabularMonoStyle.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (tickDir) {
                                1 -> colors.bullish
                                -1 -> colors.bearish
                                else -> colors.primaryText
                            }
                        ),
                        modifier = Modifier.testTag("live_price_display")
                    )
                    Surface(
                        color = (if (isBull) colors.bullish else colors.bearish).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", pct),
                            style = TabularMonoStyle.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBull) colors.bullish else colors.bearish
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Stream status indicator pill (one-tap reconnect)
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = (if (wsConnected) colors.bullish else colors.neutral).copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier
                        .clickable(onClick = onReconnectStream)
                        .testTag("ws_status_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (wsConnected) colors.bullish else colors.neutral)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (wsConnected) {
                                stringResource(R.string.terminal_live_stream)
                            } else if (wsStatus.isConnecting) {
                                "CONNECTING"
                            } else {
                                stringResource(R.string.terminal_reconnecting)
                            },
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (wsConnected) colors.bullish else colors.neutral
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${wsStatus.subscribedSymbol.lowercase()}@kline_${wsStatus.subscribedInterval}",
                    style = TabularMonoStyle.copy(
                        fontSize = 9.sp,
                        color = colors.mutedText
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.border)
        Spacer(modifier = Modifier.height(10.dp))

        // 24h High / Low / Volume bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MiniStatColumn(
                label = stringResource(R.string.terminal_high_24h),
                value = if (ticker != null) "$${formatDecimal(ticker.high24h, pair.priceDecimals)}" else "—"
            )
            MiniStatColumn(
                label = stringResource(R.string.terminal_low_24h),
                value = if (ticker != null) "$${formatDecimal(ticker.low24h, pair.priceDecimals)}" else "—"
            )
            MiniStatColumn(
                label = stringResource(R.string.terminal_vol_24h),
                value = if (ticker != null) formatCompactUsd(ticker.volumeUsd24h) else "—"
            )
        }
    }
}

@Composable
private fun MiniStatColumn(label: String, value: String) {
    val colors = LocalOrixColors.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                color = colors.mutedText
            )
        )
        Text(
            text = value,
            style = TabularMonoStyle.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveTelemetrySection(
    pair: CryptoPair,
    telemetry: QuantitativeTelemetry?,
    isLoading: Boolean
) {
    val colors = LocalOrixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.terminal_telemetry_header),
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedText
                )
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (telemetry == null) {
            Text(
                text = stringResource(R.string.loading_chart_telemetry),
                style = MaterialTheme.typography.bodyMedium.copy(color = colors.mutedText)
            )
        } else {
            val rsiColor = when {
                telemetry.rsi14 >= 70.0 -> colors.bearish
                telemetry.rsi14 <= 30.0 -> colors.bullish
                telemetry.rsi14 >= 52.0 -> colors.bullish
                telemetry.rsi14 <= 48.0 -> colors.bearish
                else -> colors.neutral
            }
            val vwapPositive = telemetry.vwapDeltaPercent >= 0.0

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                TelemetryPillCard(
                    title = "RSI (14)",
                    value = String.format(Locale.US, "%.1f", telemetry.rsi14),
                    subLabel = telemetry.rsiState,
                    valueColor = rsiColor,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPillCard(
                    title = "EMA Alignment",
                    value = if (telemetry.ema20 > telemetry.ema50) "BULLISH" else "BEARISH",
                    subLabel = telemetry.emaAlignment,
                    valueColor = if (telemetry.ema20 > telemetry.ema50) colors.bullish else colors.bearish,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPillCard(
                    title = "VWAP Delta",
                    value = String.format(Locale.US, "%+.2f%%", telemetry.vwapDeltaPercent),
                    subLabel = "VWAP $${formatDecimal(telemetry.vwap, pair.priceDecimals)}",
                    valueColor = if (vwapPositive) colors.bullish else colors.bearish,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPillCard(
                    title = "Supertrend (10,3)",
                    value = if (telemetry.supertrendBullish) "UPTREND" else "DOWNTREND",
                    subLabel = "Lvl $${formatDecimal(telemetry.supertrendValue, pair.priceDecimals)}",
                    valueColor = if (telemetry.supertrendBullish) colors.bullish else colors.bearish,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPillCard(
                    title = "ATR (14) Volatility",
                    value = "$${formatDecimal(telemetry.atr14, pair.priceDecimals)}",
                    subLabel = "Vol Spike: ${String.format(Locale.US, "%.2fx", telemetry.volumeSpikeRatio)}",
                    valueColor = colors.accent,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPillCard(
                    title = "Liquidity S / R",
                    value = "S: $${formatDecimal(telemetry.nearestSupport, pair.priceDecimals)}",
                    subLabel = "R: $${formatDecimal(telemetry.nearestResistance, pair.priceDecimals)}",
                    valueColor = colors.primaryText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryPillCard(
    title: String,
    value: String,
    subLabel: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                color = colors.mutedText
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularMonoStyle.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        )
        Text(
            text = subLabel,
            style = TabularMonoStyle.copy(
                fontSize = 10.sp,
                color = colors.mutedText
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun AiSignalActionRow(
    signalState: SignalUiState,
    autoRefresh: Boolean,
    onGenerateSignal: () -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit
) {
    val colors = LocalOrixColors.current
    val isLoading = signalState is SignalUiState.Loading

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onGenerateSignal,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.White
            ),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("generate_ai_signal_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.terminal_generating_ai_signal),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.terminal_generate_ai_signal),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.terminal_generate_ai_signal),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Auto-Refresh Toggle Pill
        Surface(
            color = colors.surfaceCard,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .height(52.dp)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.terminal_auto_refresh),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.sp,
                        color = colors.primaryText
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = autoRefresh,
                    onCheckedChange = onToggleAutoRefresh,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.bullish
                    ),
                    modifier = Modifier.testTag("auto_refresh_switch")
                )
            }
        }
    }
}

@Composable
private fun IdleSignalPromptCard(
    pair: CryptoPair,
    timeframe: Timeframe,
    onOpenSmcJsonSimulator: () -> Unit
) {
    val colors = LocalOrixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "OrixVex Institutional SMC + Quant Engine Ready",
            style = MaterialTheme.typography.titleMedium.copy(
                color = colors.primaryText,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap 'Generate AI Signal' to synthesize Smart Money Concepts (BOS/CHoCH, Order Blocks, BISI/SIBI FVGs, Premium/Discount OTE) with 200-candle EMA, RSI(14), Session VWAP & Supertrend for ${pair.displayPair} (${timeframe.label}).",
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.mutedText),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenSmcJsonSimulator,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("idle_open_smc_json_button")
        ) {
            Text(
                text = "Inspect / Run Custom SMC Telemetry JSON",
                style = MaterialTheme.typography.labelLarge.copy(color = colors.accent)
            )
        }
    }
}

@Composable
private fun LoadingSignalSkeletonCard() {
    val colors = LocalOrixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = colors.accent,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Synthesizing Institutional SMC & Quant Confluence…",
                style = MaterialTheme.typography.titleMedium.copy(color = colors.primaryText)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
        )
    }
}

@Composable
private fun ErrorSignalCard(
    message: String,
    onRetry: () -> Unit
) {
    val colors = LocalOrixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.bearish.copy(alpha = 0.1f))
            .border(1.dp, colors.bearish.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = colors.bearish
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI Signal Synthesis Notice",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.bearish,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryText)
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onRetry) {
            Text("Retry Analysis", color = colors.primaryText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrixVexAiSignalCard(
    signalResult: AiSignalResult,
    decimals: Int,
    onBookmark: () -> Unit,
    onCopyPrice: (String) -> Unit
) {
    val colors = LocalOrixColors.current
    val context = LocalContext.current
    var isSmcBlueprintExpanded by remember { mutableStateOf(true) }

    val signalTone = when {
        signalResult.signal.contains("BUY") -> colors.bullish
        signalResult.signal.contains("SELL") -> colors.bearish
        else -> colors.neutral
    }
    val signalIcon = when {
        signalResult.signal.contains("BUY") -> Icons.AutoMirrored.Filled.TrendingUp
        signalResult.signal.contains("SELL") -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    fun copyToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        onCopyPrice("$label ($value)")
    }

    val lifecycle = signalResult.tradeLifecycle
    val strength = signalResult.aiStrength
    val bvb = signalResult.bullsVsBears
    val swings = signalResult.futureSwingsProjection
    val newsCtx = signalResult.fundamentalAndNewsContext

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceCard)
            .border(1.5.dp, signalTone.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(16.dp)
            .testTag("ai_signal_result_card"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Non-Repainting Bar-Close Confirmation Strip + Deterministic Signal ID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = colors.accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (signalResult.isNonRepaintingConfirmed) {
                        "NON-REPAINTING CONFIRMED (kline.x = true)"
                    } else {
                        "LIVE EVALUATION"
                    },
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    ),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("signal_non_repainting_badge")
                )
            }
            Text(
                text = signalResult.signalId,
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    color = colors.mutedText
                ),
                modifier = Modifier.testTag("signal_deterministic_id_text")
            )
        }

        // Header: Signal Badge + AI Grade + Market Regime + Radial Confidence Gauge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = signalTone.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, signalTone.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = signalIcon,
                                contentDescription = signalResult.signal,
                                tint = signalTone,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = signalResult.signal,
                                style = TabularMonoStyle.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = signalTone
                                )
                            )
                        }
                    }

                    Surface(
                        color = colors.accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "GRADE ${strength.grade} (${strength.overallScore}/100)",
                            style = TabularMonoStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("signal_ai_grade_badge")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${signalResult.displayPair} • ${signalResult.marketRegime}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Text(
                    text = "Horizon: ${signalResult.tradingStyle} • R:R ${signalResult.riskRewardRatio}",
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        color = colors.mutedText
                    )
                )
            }

            // Radial Confidence Gauge
            RadialConfidenceGauge(
                confidence = signalResult.confidence,
                color = signalTone
            )
        }

        HorizontalDivider(color = colors.border)

        // Copyable Entry / Optimal Entry / Stop Loss / TP1 / TP2 / TP3 Price Pills
        val entryFormatted = "$${formatDecimal(signalResult.entryLow, decimals)} - $${formatDecimal(signalResult.entryHigh, decimals)}"
        val optimalEntryFormatted = "$${formatDecimal(signalResult.optimalEntry, decimals)}"
        val slFormatted = "$${formatDecimal(signalResult.stopLoss, decimals)}"
        val tp1Formatted = "$${formatDecimal(signalResult.takeProfit1, decimals)}"
        val tp2Formatted = "$${formatDecimal(signalResult.takeProfit2, decimals)}"
        val tp3Formatted = "$${formatDecimal(signalResult.takeProfit3, decimals)}"

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            CopyablePricePill(
                label = "ENTRY ZONE (OPT $optimalEntryFormatted)",
                value = entryFormatted,
                accentColor = colors.accent,
                onClick = { copyToClipboard("Optimal Entry", optimalEntryFormatted) },
                modifier = Modifier.weight(1f)
            )
            CopyablePricePill(
                label = "INITIAL STOP LOSS (TSL)",
                value = slFormatted,
                accentColor = colors.bearish,
                onClick = { copyToClipboard("Stop Loss", slFormatted) },
                modifier = Modifier.weight(1f)
            )
            CopyablePricePill(
                label = "TP 1 (${lifecycle.targets.tp1.rr} • ${lifecycle.targets.tp1.allocationPct}%)",
                value = "$tp1Formatted (${lifecycle.targets.tp1.actionOnHit})",
                accentColor = colors.bullish,
                onClick = { copyToClipboard("Take Profit 1", tp1Formatted) },
                modifier = Modifier.weight(1f)
            )
            CopyablePricePill(
                label = "TP 2 (${lifecycle.targets.tp2.rr} • ${lifecycle.targets.tp2.allocationPct}%)",
                value = "$tp2Formatted (${lifecycle.targets.tp2.actionOnHit})",
                accentColor = colors.bullish,
                onClick = { copyToClipboard("Take Profit 2", tp2Formatted) },
                modifier = Modifier.weight(1f)
            )
        }

        CopyablePricePill(
            label = "EXTENDED TARGET TP 3 (${lifecycle.targets.tp3.rr} • ${lifecycle.targets.tp3.allocationPct}% • ${lifecycle.targets.tp3.actionOnHit})",
            value = tp3Formatted,
            accentColor = colors.bullish,
            onClick = { copyToClipboard("Take Profit 3", tp3Formatted) },
            modifier = Modifier.fillMaxWidth()
        )

        // Trailing Stop Config & Early Exit Trigger Summary Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("orixvex_pro_lifecycle_summary_box"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "NON-REPAINTING TRADE LIFECYCLE (TSL & EARLY EXIT ENGINE)",
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            )
            SmcBlueprintRow(
                label = "Trailing Stop Config (${lifecycle.trailingStopConfig.atrMultiplier}x ATR • Rec TSL $${formatDecimal(lifecycle.trailingStopConfig.currentRecommendedTSL, decimals)})",
                value = lifecycle.trailingStopConfig.activationRule
            )
            SmcBlueprintRow(
                label = "Emergency Early Exit Condition (Needed Now: ${if (lifecycle.earlyExitTrigger.earlyExitNeededNow) "YES" else "NO"})",
                value = "${lifecycle.earlyExitTrigger.emergencyExitPriceCondition} — ${lifecycle.earlyExitTrigger.earlyExitReason}"
            )
            SmcBlueprintRow(
                label = "Bulls vs. Bears Order Flow (${bvb.bullsPowerPct}% / ${bvb.bearsPowerPct}%)",
                value = "${bvb.dominantForce} • ${bvb.orderFlowDeltaSummary}"
            )
            SmcBlueprintRow(
                label = "Projected Next Swings (SH $${formatDecimal(swings.projectedNextSwingHigh.price, decimals)} [${swings.projectedNextSwingHigh.probabilityPct}%, ${swings.projectedNextSwingHigh.estimatedCandles}] | SL $${formatDecimal(swings.projectedNextSwingLow.price, decimals)} [${swings.projectedNextSwingLow.probabilityPct}%, ${swings.projectedNextSwingLow.estimatedCandles}])",
                value = swings.expectedTrajectory
            )
            SmcBlueprintRow(
                label = "Fundamental & News Sentiment (${newsCtx.overallNewsSentiment})",
                value = newsCtx.keyCatalystSummary
            )
        }

        // Dedicated Collapsible "Smart Money Blueprint" Section
        val bp = signalResult.smartMoneyBlueprint
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .testTag("smart_money_blueprint_section")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isSmcBlueprintExpanded = !isSmcBlueprintExpanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("smart_money_blueprint_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SMART MONEY BLUEPRINT (SMC)",
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                }
                Icon(
                    imageVector = if (isSmcBlueprintExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Smart Money Blueprint",
                    tint = colors.mutedText,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isSmcBlueprintExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = colors.border)
                    if (bp.institutionalNarrative.isNotBlank()) {
                        Text(
                            text = bp.institutionalNarrative,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.primaryText,
                                fontSize = 12.sp
                            )
                        )
                    }
                    SmcBlueprintRow(
                        label = "Order Block (OB) & Mean Threshold",
                        value = bp.orderBlockCoordinates.ifBlank { "Aligned with Entry Zone" }
                    )
                    SmcBlueprintRow(
                        label = "Unmitigated FVG (BISI / SIBI)",
                        value = bp.fairValueGapCoordinates.ifBlank { "3-Candle Imbalance Monitored" }
                    )
                    SmcBlueprintRow(
                        label = "Liquidity Sweep (BSL / SSL)",
                        value = bp.liquiditySweepStatus.ifBlank { "External Swing Liquidity Targeted" }
                    )
                    SmcBlueprintRow(
                        label = "Dealing Range & OTE (0.618-0.786)",
                        value = bp.dealingRangeAndOte.ifBlank { "50-Bar Equilibrium Evaluated" }
                    )
                    SmcBlueprintRow(
                        label = "ATR-Protected Invalidation",
                        value = signalResult.invalidationCondition
                    )
                }
            }
        }

        // Indicator Confluence Breakdown
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceElevated)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "SMC + QUANTITATIVE INDICATOR CONFLUENCE",
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            )
            signalResult.indicatorConfluence.forEach { bullet ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = signalTone,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.primaryText,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        // Invalidation Warning Banner
        Surface(
            color = colors.neutral.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.neutral.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = colors.neutral,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Invalidation: ${signalResult.invalidationCondition}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = colors.primaryText
                    )
                )
            }
        }

        // Bookmark to Signal Vault Button
        OutlinedButton(
            onClick = onBookmark,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("bookmark_signal_button")
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkAdd,
                contentDescription = stringResource(R.string.terminal_save_to_vault),
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.terminal_save_to_vault),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun SmcBlueprintRow(
    label: String,
    value: String
) {
    val colors = LocalOrixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceCard.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = TabularMonoStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.mutedText
            )
        )
        Text(
            text = value,
            style = TabularMonoStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
        )
    }
}

@Composable
private fun RadialConfidenceGauge(
    confidence: Int,
    color: Color
) {
    val colors = LocalOrixColors.current
    Box(
        modifier = Modifier.size(68.dp),
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
            val sweep = (confidence.coerceIn(0, 100) / 100f) * 270f
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$confidence%",
                style = TabularMonoStyle.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            )
            Text(
                text = "CONF",
                style = TabularMonoStyle.copy(
                    fontSize = 8.sp,
                    color = colors.mutedText
                )
            )
        }
    }
}

@Composable
private fun CopyablePricePill(
    label: String,
    value: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    Surface(
        color = colors.surfaceElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = TabularMonoStyle.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = TabularMonoStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText
                    )
                )
            }
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.cd_copy_price),
                tint = colors.mutedText,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

fun formatDecimal(value: Double, decimals: Int): String {
    return String.format(Locale.US, "%,.${decimals}f", value)
}

fun formatCompactUsd(value: Double): String {
    return when {
        value >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", value / 1_000_000_000.0)
        value >= 1_000_000 -> String.format(Locale.US, "$%.2fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "$%.1fK", value / 1_000.0)
        else -> String.format(Locale.US, "$%.2f", value)
    }
}

@Composable
private fun SmcJsonPayloadDialog(
    initialJson: String,
    onDismiss: () -> Unit,
    onEvaluateJson: (String) -> Unit
) {
    val colors = LocalOrixColors.current
    var jsonInput by remember { mutableStateOf(initialJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        title = {
            Column {
                Text(
                    text = "Institutional SMC + OrixVex Pro Lifecycle JSON",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Inspect the live 200-candle SMC JSON, load the BTC/USD 15m SMC preset, or load the OrixVex Pro Lifecycle Schema preset to synthesize.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = colors.mutedText
                    )
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { jsonInput = QuantitativeTelemetry.SAMPLE_BTC_15M_SMC_JSON },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load_btc_15m_preset_button")
                    ) {
                        Text(
                            text = "SMC 15m",
                            style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.accent)
                        )
                    }
                    OutlinedButton(
                        onClick = { jsonInput = com.example.domain.ai.GeminiSignalService.SAMPLE_ORIXVEX_PRO_LIFECYCLE_JSON },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load_pro_lifecycle_preset_button")
                    ) {
                        Text(
                            text = "Pro Lifecycle",
                            style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.bullish)
                        )
                    }
                    OutlinedButton(
                        onClick = { jsonInput = initialJson },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Live JSON",
                            style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.primaryText)
                        )
                    }
                }
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    textStyle = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.primaryText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .testTag("smc_json_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvaluateJson(jsonInput) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.testTag("synthesize_smc_json_button")
            ) {
                Text("Synthesize with Gemini AI", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.mutedText)
            }
        }
    )
}

