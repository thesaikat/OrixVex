package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AiSignalResult
import com.example.data.model.CryptoPair
import com.example.data.model.LiveTicker
import com.example.data.model.SavedSignal
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun PositionSizerVaultScreen(
    accountBalanceUsd: Double,
    riskPercentPerTrade: Double,
    activeSignal: AiSignalResult?,
    savedSignals: List<SavedSignal>,
    tickers: Map<CryptoPair, LiveTicker>,
    onUpdateRiskInputs: (Double, Double) -> Unit,
    onDeleteSignal: (String) -> Unit,
    onOpenPairInTerminal: (CryptoPair) -> Unit
) {
    val colors = LocalOrixColors.current

    var balanceText by remember(accountBalanceUsd) {
        mutableStateOf(String.format(Locale.US, "%.0f", accountBalanceUsd))
    }
    var riskText by remember(riskPercentPerTrade) {
        mutableStateOf(String.format(Locale.US, "%.2f", riskPercentPerTrade))
    }

    val defaultEntry = activeSignal?.let { (it.entryLow + it.entryHigh) / 2.0 }
        ?: tickers[CryptoPair.BTC]?.price ?: 90000.0
    val defaultStop = activeSignal?.stopLoss ?: (defaultEntry * 0.985)

    var entryText by remember(activeSignal) {
        mutableStateOf(String.format(Locale.US, "%.4f", defaultEntry))
    }
    var stopLossText by remember(activeSignal) {
        mutableStateOf(String.format(Locale.US, "%.4f", defaultStop))
    }

    val parsedBalance = balanceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: accountBalanceUsd
    val parsedRiskPct = riskText.toDoubleOrNull()?.coerceIn(0.05, 100.0) ?: riskPercentPerTrade
    val parsedEntry = entryText.toDoubleOrNull()?.coerceAtLeast(0.00001) ?: defaultEntry
    val parsedStop = stopLossText.toDoubleOrNull()?.coerceAtLeast(0.00001) ?: defaultStop

    val riskUsd = parsedBalance * (parsedRiskPct / 100.0)
    val stopDistance = abs(parsedEntry - parsedStop).coerceAtLeast(parsedEntry * 0.0001)
    val positionSizeUnits = riskUsd / stopDistance
    val positionNotionalUsd = positionSizeUnits * parsedEntry
    val effectiveLeverage = if (parsedBalance > 0) positionNotionalUsd / parsedBalance else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.vault_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                color = colors.primaryText,
                fontWeight = FontWeight.Bold
            )
        )

        // 1. Built-in Quantitative Risk & Position Sizer Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.vault_calculator_header),
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
            }

            if (activeSignal != null) {
                Surface(
                    color = colors.accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Synced with active ${activeSignal.displayPair} (${activeSignal.signal}) AI Signal",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            color = colors.accent,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = {
                        balanceText = it
                        val b = it.toDoubleOrNull()
                        if (b != null && b >= 0) onUpdateRiskInputs(b, parsedRiskPct)
                    },
                    label = { Text(stringResource(R.string.vault_account_balance)) },
                    textStyle = TabularMonoStyle.copy(color = colors.primaryText, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("risk_balance_input")
                )

                OutlinedTextField(
                    value = riskText,
                    onValueChange = {
                        riskText = it
                        val r = it.toDoubleOrNull()
                        if (r != null && r > 0) onUpdateRiskInputs(parsedBalance, r)
                    },
                    label = { Text(stringResource(R.string.vault_risk_percent)) },
                    textStyle = TabularMonoStyle.copy(color = colors.primaryText, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("risk_percent_input")
                )
            }

            // Quick Risk % Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.5, 1.0, 2.0, 3.0).forEach { preset ->
                    val isSelected = abs(parsedRiskPct - preset) < 0.01
                    Surface(
                        color = if (isSelected) colors.accent else colors.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                riskText = String.format(Locale.US, "%.1f", preset)
                                onUpdateRiskInputs(parsedBalance, preset)
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "${preset}% Risk",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = entryText,
                    onValueChange = { entryText = it },
                    label = { Text("Entry Price ($)") },
                    textStyle = TabularMonoStyle.copy(color = colors.primaryText, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = stopLossText,
                    onValueChange = { stopLossText = it },
                    label = { Text("Stop Loss ($)") },
                    textStyle = TabularMonoStyle.copy(color = colors.primaryText, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = colors.border)

            // Calculated Position Output Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RiskMetricBox(
                    label = "USD Capital at Risk",
                    value = String.format(Locale.US, "$%,.2f", riskUsd),
                    valueColor = colors.bearish,
                    modifier = Modifier.weight(1f)
                )
                RiskMetricBox(
                    label = "Position Size (Units)",
                    value = String.format(Locale.US, "%,.4f", positionSizeUnits),
                    valueColor = colors.accent,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RiskMetricBox(
                    label = "Notional Exposure",
                    value = String.format(Locale.US, "$%,.2f", positionNotionalUsd),
                    valueColor = colors.primaryText,
                    modifier = Modifier.weight(1f)
                )
                RiskMetricBox(
                    label = "Projected 2.5R Profit",
                    value = String.format(Locale.US, "+$%,.2f", riskUsd * 2.5),
                    valueColor = colors.bullish,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = String.format(
                    Locale.US,
                    "Effective Account Leverage: %.2fx • Stop Distance: %.2f%%",
                    effectiveLeverage,
                    (stopDistance / parsedEntry) * 100.0
                ),
                style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.mutedText)
            )
        }

        // 2. Bookmarked AI Signals Vault History
        Text(
            text = stringResource(R.string.vault_saved_signals_header),
            style = TabularMonoStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedText
            )
        )

        if (savedSignals.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vault_empty_state),
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.mutedText)
                )
            }
        } else {
            savedSignals.forEach { saved ->
                val pair = CryptoPair.fromSymbol(saved.symbol)
                val livePrice = tickers[pair]?.price
                SavedSignalVaultCard(
                    saved = saved,
                    pair = pair,
                    livePrice = livePrice,
                    onDelete = { onDeleteSignal(saved.id) },
                    onOpenInTerminal = { onOpenPairInTerminal(pair) }
                )
            }
        }
    }
}

@Composable
private fun RiskMetricBox(
    label: String,
    value: String,
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
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                color = colors.mutedText
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularMonoStyle.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        )
    }
}

@Composable
private fun SavedSignalVaultCard(
    saved: SavedSignal,
    pair: CryptoPair,
    livePrice: Double?,
    onDelete: () -> Unit,
    onOpenInTerminal: () -> Unit
) {
    val colors = LocalOrixColors.current
    val isBuy = saved.signal.contains("BUY")
    val isSell = saved.signal.contains("SELL")
    val signalColor = when {
        isBuy -> colors.bullish
        isSell -> colors.bearish
        else -> colors.neutral
    }

    val statusColor = when (saved.status) {
        "TP1_HIT", "TP2_HIT", "TP3_HIT" -> colors.bullish
        "STOPPED_OUT" -> colors.bearish
        else -> colors.accent
    }

    val entryMid = (saved.entryLow + saved.entryHigh) / 2.0
    val livePnlPct = if (livePrice != null && entryMid > 0.0) {
        val raw = ((livePrice - entryMid) / entryMid) * 100.0
        if (isSell) -raw else raw
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenInTerminal)
            .padding(14.dp)
            .testTag("saved_signal_card_${saved.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = saved.displayPair,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Surface(
                    color = signalColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = saved.signal,
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = signalColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = saved.status.replace("_", " "),
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_signal),
                    tint = colors.mutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val dec = pair.priceDecimals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Entry: $${formatDecimal(entryMid, dec)} • SL: $${formatDecimal(saved.stopLoss, dec)}",
                style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.mutedText)
            )
            if (livePrice != null && livePnlPct != null) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Live $%s (%+.2f%%)",
                        formatDecimal(livePrice, dec),
                        livePnlPct
                    ),
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (livePnlPct >= 0) colors.bullish else colors.bearish
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TargetChip("TP1", formatDecimal(saved.takeProfit1, dec), saved.status in listOf("TP1_HIT", "TP2_HIT", "TP3_HIT"), Modifier.weight(1f))
            TargetChip("TP2", formatDecimal(saved.takeProfit2, dec), saved.status in listOf("TP2_HIT", "TP3_HIT"), Modifier.weight(1f))
            TargetChip("TP3", formatDecimal(saved.takeProfit3, dec), saved.status == "TP3_HIT", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TargetChip(
    label: String,
    price: String,
    isHit: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    val tone = if (isHit) colors.bullish else colors.mutedText
    Surface(
        color = if (isHit) colors.bullish.copy(alpha = 0.16f) else colors.surfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, if (isHit) colors.bullish else colors.border, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = if (isHit) "$label HIT ✓" else label,
                style = TabularMonoStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tone)
            )
            Text(
                text = "$$price",
                style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.primaryText)
            )
        }
    }
}
