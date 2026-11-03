package com.example.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.CryptoPair
import com.example.data.model.OhlcvCandle
import com.example.data.model.QuantitativeTelemetry
import com.example.data.model.Timeframe
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun InteractiveTradingChartSection(
    selectedPair: CryptoPair,
    selectedTimeframe: Timeframe,
    isDarkTheme: Boolean,
    candles: List<OhlcvCandle>,
    telemetry: QuantitativeTelemetry?,
    isFullscreen: Boolean,
    onTimeframeSelect: (Timeframe) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    var useTradingViewWidget by remember { mutableStateOf(true) }
    var showDrawingToolbar by remember { mutableStateOf(false) }
    var chartStyleCandles by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Top Chart Controls Bar: Timeframe Pills + Mode & Toolbar Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Timeframe.entries.forEach { tf ->
                    val isSelected = tf == selectedTimeframe
                    Surface(
                        color = if (isSelected) colors.accent else colors.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { onTimeframeSelect(tf) }
                            .testTag("timeframe_pill_${tf.binanceInterval}")
                    ) {
                        Text(
                            text = tf.label,
                            style = TabularMonoStyle.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else colors.mutedText
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Toggle TradingView Widget vs Native High-FPS Canvas Chart
                Surface(
                    color = if (useTradingViewWidget) colors.accent.copy(alpha = 0.16f) else colors.surfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { useTradingViewWidget = !useTradingViewWidget }
                        .testTag("chart_engine_toggle")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(R.string.cd_toggle_tv_widget),
                            tint = if (useTradingViewWidget) colors.accent else colors.mutedText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (useTradingViewWidget) "TV.js" else "QuantCanvas",
                            style = TabularMonoStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (useTradingViewWidget) colors.accent else colors.mutedText
                            )
                        )
                    }
                }

                // Drawing Toolbar Toggle
                IconButton(
                    onClick = { showDrawingToolbar = !showDrawingToolbar },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("chart_drawing_toolbar_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_toggle_drawing_toolbar),
                        tint = if (showDrawingToolbar) colors.accent else colors.mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Candlestick vs Line Style Toggle
                IconButton(
                    onClick = { chartStyleCandles = !chartStyleCandles },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("chart_style_toggle")
                ) {
                    Icon(
                        imageVector = if (chartStyleCandles) Icons.Default.CandlestickChart else Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = stringResource(R.string.cd_toggle_chart_style),
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Fullscreen Expansion Toggle
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("chart_fullscreen_toggle")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(R.string.cd_toggle_fullscreen),
                        tint = colors.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Studies Legend Ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudyLegendItem(color = Color(0xFF3B82F6), label = "EMA 20")
            StudyLegendItem(color = Color(0xFFF59E0B), label = "EMA 50")
            StudyLegendItem(color = Color(0xFFA855F7), label = "EMA 200")
            StudyLegendItem(color = Color(0xFF06B6D4), label = "VWAP")
            StudyLegendItem(
                color = if (telemetry?.supertrendBullish != false) colors.bullish else colors.bearish,
                label = "Supertrend(10,3)"
            )
            StudyLegendItem(color = colors.mutedText, label = "RSI(14)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        val chartHeight = if (isFullscreen) 500.dp else 310.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            if (useTradingViewWidget) {
                TradingViewWebView(
                    symbol = selectedPair.tradingViewSymbol,
                    interval = selectedTimeframe.tvInterval,
                    isDarkTheme = isDarkTheme,
                    showDrawingToolbar = showDrawingToolbar,
                    chartStyle = if (chartStyleCandles) "1" else "2",
                    backgroundHex = if (isDarkTheme) "#090D16" else "#F8FAFC"
                )
            } else {
                NativeQuantCandlestickCanvas(
                    pair = selectedPair,
                    candles = candles,
                    telemetry = telemetry,
                    showCandles = chartStyleCandles,
                    showFibLevels = showDrawingToolbar
                )
            }
        }
    }
}

@Composable
private fun StudyLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = TabularMonoStyle.copy(
                fontSize = 10.sp,
                color = LocalOrixColors.current.mutedText
            )
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TradingViewWebView(
    symbol: String,
    interval: String,
    isDarkTheme: Boolean,
    showDrawingToolbar: Boolean,
    chartStyle: String,
    backgroundHex: String
) {
    val configKey = "$symbol|$interval|$isDarkTheme|$showDrawingToolbar|$chartStyle"
    val themeStr = if (isDarkTheme) "dark" else "light"
    val gridColor = if (isDarkTheme) "rgba(255, 255, 255, 0.05)" else "rgba(15, 23, 42, 0.06)"

    val htmlContent = remember(configKey) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background-color: $backgroundHex;
                    overflow: hidden;
                }
                #tv_chart_container {
                    width: 100%;
                    height: 100%;
                }
            </style>
        </head>
        <body>
            <div id="tv_chart_container"></div>
            <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
            <script type="text/javascript">
                new TradingView.widget({
                    "autosize": true,
                    "symbol": "$symbol",
                    "interval": "$interval",
                    "timezone": "Etc/UTC",
                    "theme": "$themeStr",
                    "style": "$chartStyle",
                    "locale": "en",
                    "backgroundColor": "$backgroundHex",
                    "gridColor": "$gridColor",
                    "hide_top_toolbar": false,
                    "hide_side_toolbar": ${!showDrawingToolbar},
                    "allow_symbol_change": false,
                    "save_image": false,
                    "studies": [
                        "MAExp@tv-basicstudies",
                        "VWAP@tv-basicstudies",
                        "RSI@tv-basicstudies"
                    ],
                    "container_id": "tv_chart_container"
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tradingview_webview"),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                tag = configKey
                loadDataWithBaseURL("https://s3.tradingview.com", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != configKey) {
                webView.tag = configKey
                webView.loadDataWithBaseURL("https://s3.tradingview.com", htmlContent, "text/html", "UTF-8", null)
            }
        }
    )
}

@Composable
private fun NativeQuantCandlestickCanvas(
    pair: CryptoPair,
    candles: List<OhlcvCandle>,
    telemetry: QuantitativeTelemetry?,
    showCandles: Boolean,
    showFibLevels: Boolean
) {
    val colors = LocalOrixColors.current
    if (candles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.loading_chart_telemetry),
                style = TabularMonoStyle.copy(color = colors.mutedText, fontSize = 12.sp)
            )
        }
        return
    }

    var visibleCount by remember { mutableIntStateOf(60) }
    var inspectedIndex by remember(candles.size) { mutableIntStateOf(-1) }

    val slice = candles.takeLast(visibleCount.coerceIn(20, candles.size))
    val maxPrice = slice.maxOf { it.high }
    val minPrice = slice.minOf { it.low }
    val priceRange = (maxPrice - minPrice).coerceAtLeast(maxPrice * 0.001)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val updated = (visibleCount / zoom).roundToInt().coerceIn(20, candles.size)
                        visibleCount = updated
                    }
                }
                .pointerInput(slice) {
                    detectTapGestures { offset ->
                        val idx = ((offset.x / size.width) * slice.size).toInt().coerceIn(0, slice.lastIndex)
                        inspectedIndex = idx
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val chartH = h * 0.82f
            val volTop = h * 0.84f
            val volH = h * 0.14f

            fun priceToY(p: Double): Float {
                val norm = ((p - minPrice) / priceRange).toFloat().coerceIn(0f, 1f)
                return chartH - (norm * (chartH - 24f)) - 12f
            }

            // Grid lines
            for (i in 1..4) {
                val y = (chartH / 5f) * i
                drawLine(
                    color = colors.border,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            // Optional SMC + Fib Drawing Overlay (Equilibrium, Order Blocks, Fair Value Gaps)
            if (showFibLevels && telemetry != null) {
                val smc = telemetry.smc
                val eqY = priceToY(smc.equilibriumPrice)
                val vwapY = priceToY(telemetry.vwap)

                // Dealing Range 0.50 Equilibrium Line
                drawLine(
                    color = colors.neutral.copy(alpha = 0.75f),
                    start = Offset(0f, eqY),
                    end = Offset(w, eqY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )

                // Active Bullish / Bearish Order Block Zone
                val activeOb = smc.nearestBullishOb ?: smc.nearestBearishOb
                if (activeOb != null) {
                    val obTopY = priceToY(activeOb.high)
                    val obBotY = priceToY(activeOb.low)
                    val obColor = if (activeOb.isBullish) colors.bullish else colors.bearish
                    drawRect(
                        color = obColor.copy(alpha = 0.16f),
                        topLeft = Offset(0f, min(obTopY, obBotY)),
                        size = Size(w, max(kotlin.math.abs(obBotY - obTopY), 3f))
                    )
                }

                // Active Unmitigated Fair Value Gap (BISI / SIBI)
                val activeFvg = smc.nearestBullishFvg ?: smc.nearestBearishFvg
                if (activeFvg != null) {
                    val fvgTopY = priceToY(activeFvg.top)
                    val fvgBotY = priceToY(activeFvg.bottom)
                    drawRect(
                        color = colors.accent.copy(alpha = 0.18f),
                        topLeft = Offset(w * 0.25f, min(fvgTopY, fvgBotY)),
                        size = Size(w * 0.75f, max(kotlin.math.abs(fvgBotY - fvgTopY), 3f))
                    )
                }

                drawLine(
                    color = Color(0xFF06B6D4).copy(alpha = 0.7f),
                    start = Offset(0f, vwapY),
                    end = Offset(w, vwapY),
                    strokeWidth = 2f
                )
            }

            val stepX = w / slice.size.toFloat()
            val candleW = (stepX * 0.68f).coerceAtLeast(2f)
            val maxVol = slice.maxOf { it.volume }.coerceAtLeast(1.0)

            val closePath = Path()
            slice.forEachIndexed { idx, c ->
                val cx = idx * stepX + stepX / 2f
                val highY = priceToY(c.high)
                val lowY = priceToY(c.low)
                val openY = priceToY(c.open)
                val closeY = priceToY(c.close)
                val isBull = c.close >= c.open
                val cColor = if (isBull) colors.bullish else colors.bearish

                if (showCandles) {
                    drawLine(
                        color = cColor,
                        start = Offset(cx, highY),
                        end = Offset(cx, lowY),
                        strokeWidth = 1.4.dp.toPx()
                    )
                    val top = min(openY, closeY)
                    val bodyH = max(kotlin.math.abs(closeY - openY), 2f)
                    drawRect(
                        color = cColor,
                        topLeft = Offset(cx - candleW / 2f, top),
                        size = Size(candleW, bodyH)
                    )
                } else {
                    if (idx == 0) closePath.moveTo(cx, closeY) else closePath.lineTo(cx, closeY)
                }

                // Volume histogram at bottom
                val vRatio = (c.volume / maxVol).toFloat().coerceIn(0f, 1f)
                val barH = vRatio * volH
                drawRect(
                    color = cColor.copy(alpha = 0.32f),
                    topLeft = Offset(cx - candleW / 2f, volTop + (volH - barH)),
                    size = Size(candleW, barH)
                )
            }

            if (!showCandles) {
                drawPath(
                    path = closePath,
                    color = colors.accent,
                    style = Stroke(width = 2.2.dp.toPx())
                )
            }

            // Crosshair if inspected
            if (inspectedIndex in slice.indices) {
                val cx = inspectedIndex * stepX + stepX / 2f
                val cy = priceToY(slice[inspectedIndex].close)
                drawLine(
                    color = colors.primaryText.copy(alpha = 0.5f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                drawLine(
                    color = colors.primaryText.copy(alpha = 0.5f),
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
        }

        // Top-left OHLC overlay for inspected or latest candle
        val activeCandle = if (inspectedIndex in slice.indices) slice[inspectedIndex] else slice.last()
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceCard.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dec = pair.priceDecimals
            Text(
                text = String.format(Locale.US, "O: %,.${dec}f", activeCandle.open),
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.mutedText)
            )
            Text(
                text = String.format(Locale.US, "H: %,.${dec}f", activeCandle.high),
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.bullish)
            )
            Text(
                text = String.format(Locale.US, "L: %,.${dec}f", activeCandle.low),
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.bearish)
            )
            Text(
                text = String.format(Locale.US, "C: %,.${dec}f", activeCandle.close),
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.primaryText, fontWeight = FontWeight.Bold)
            )
        }
    }
}
