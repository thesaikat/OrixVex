package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveManagedTrade
import com.example.data.model.AiCoachPortfolioAudit
import com.example.data.model.SavedSignal
import com.example.data.model.TradeJournalEntry
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class HistoryStatusFilter(val label: String) {
    ALL("All"),
    TP_HIT("TP Hit"),
    SL_HIT("SL Hit"),
    TSL_CLOSED("TSL Closed"),
    EARLY_EXIT("Early Exit"),
    ACTIVE("Active")
}

@Composable
fun HistoryAndJournalScreen(
    activeManagedTrade: ActiveManagedTrade?,
    closedManagedTrades: List<ActiveManagedTrade>,
    savedSignals: List<SavedSignal>,
    journalEntries: List<TradeJournalEntry>,
    aiCoachAudit: AiCoachPortfolioAudit?,
    isRunningCoachAudit: Boolean,
    onImportTradeToJournal: (ActiveManagedTrade) -> Unit,
    onAddManualJournalEntry: (
        displayPair: String,
        timeframe: String,
        direction: String,
        entryPrice: Double,
        exitPrice: Double,
        positionSizeUsd: Double,
        setupTag: String,
        mindsetTag: String,
        notes: String
    ) -> Unit,
    onDeleteJournalEntry: (String) -> Unit,
    onDeleteSavedSignal: (String) -> Unit,
    onRunAiCoachAudit: () -> Unit
) {
    val colors = LocalOrixColors.current
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Immutable Signal History, 1 = Manual Trade Journal
    var activeFilter by remember { mutableStateOf(HistoryStatusFilter.ALL) }
    var showAddJournalDialog by remember { mutableStateOf(false) }
    var showInlineManualForm by remember { mutableStateOf(true) }

    val allLifecycleTrades = remember(activeManagedTrade, closedManagedTrades) {
        buildList {
            if (activeManagedTrade != null) add(activeManagedTrade)
            addAll(closedManagedTrades)
        }
    }

    val tpWinsCount = remember(allLifecycleTrades) {
        allLifecycleTrades.count {
            it.status.startsWith("TP") || (it.status == "TSL_CLOSED" && it.currentRMultiple > 0.0) || it.tp1Hit
        }
    }
    val slLossesCount = remember(allLifecycleTrades) {
        allLifecycleTrades.count { it.status == "STOPPED_OUT" }
    }
    val resolvedTradesCount = remember(allLifecycleTrades) {
        allLifecycleTrades.count { it.isClosed }.coerceAtLeast(1)
    }
    val signalWinRatePct = remember(tpWinsCount, resolvedTradesCount) {
        (tpWinsCount.toDouble() / resolvedTradesCount.toDouble()) * 100.0
    }

    val filteredTrades = remember(allLifecycleTrades, activeFilter) {
        when (activeFilter) {
            HistoryStatusFilter.ALL -> allLifecycleTrades
            HistoryStatusFilter.ACTIVE -> allLifecycleTrades.filter { !it.isClosed }
            HistoryStatusFilter.TP_HIT -> allLifecycleTrades.filter { it.status.startsWith("TP") || it.tp1Hit }
            HistoryStatusFilter.TSL_CLOSED -> allLifecycleTrades.filter { it.status == "TSL_CLOSED" }
            HistoryStatusFilter.EARLY_EXIT -> allLifecycleTrades.filter { it.status == "EARLY_EXIT" }
            HistoryStatusFilter.SL_HIT -> allLifecycleTrades.filter { it.status == "STOPPED_OUT" }
        }
    }

    if (showAddJournalDialog) {
        AddJournalEntryDialog(
            onDismiss = { showAddJournalDialog = false },
            onSave = { pair, tf, dir, entry, exit, size, setup, mindset, notes ->
                showAddJournalDialog = false
                onAddManualJournalEntry(pair, tf, dir, entry, exit, size, setup, mindset, notes)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("history_and_journal_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "History & Journal",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Immutable Bar-Close AI Signal Audit (TP/SL) & Manual Trade Journal",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.mutedText,
                        fontSize = 12.sp
                    )
                )
            }
            Surface(
                color = colors.accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "NON-REPAINTING",
                        style = TabularMonoStyle.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Top Sub-Tab Selector: Primary = Immutable Signal History, Secondary = Manual Trade Journal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Immutable Signal History (${allLifecycleTrades.size})",
                "Manual Trade Journal (${journalEntries.size})"
            ).forEachIndexed { index, title ->
                val isSelected = selectedSubTab == index
                Surface(
                    color = if (isSelected) colors.accent else colors.surfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(10.dp))
                        .clickable { selectedSubTab = index }
                        .testTag("history_journal_subtab_$index")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = title,
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

        if (selectedSubTab == 0) {
            // PRIMARY VIEW: Immutable Signal History with TP/SL Resolution Status
            ImmutableSignalResolutionSummaryStrip(
                totalSignals = allLifecycleTrades.size,
                tpWins = tpWinsCount,
                slLosses = slLossesCount,
                winRatePct = signalWinRatePct
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryStatusFilter.entries.forEach { filter ->
                    val isSelected = activeFilter == filter
                    Surface(
                        color = if (isSelected) colors.accent else colors.surfaceCard,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(8.dp))
                            .clickable { activeFilter = filter }
                            .testTag("history_filter_${filter.name.lowercase()}")
                    ) {
                        Text(
                            text = filter.label,
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

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("immutable_signal_history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTrades, key = { it.signalId }) { trade ->
                    LockedSignalAuditCard(
                        trade = trade,
                        onImportToJournal = { onImportTradeToJournal(trade) }
                    )
                }

                if (savedSignals.isNotEmpty()) {
                    item {
                        Text(
                            text = "CLOUD BOOKMARKED SIGNAL VAULT (${savedSignals.size})",
                            style = TabularMonoStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(savedSignals, key = { it.id }) { saved ->
                        SavedVaultSignalRow(
                            saved = saved,
                            onDelete = { onDeleteSavedSignal(saved.id) }
                        )
                    }
                }
            }
        } else {
            // SECONDARY VIEW: Manual Trade Journal & AI Coach Portfolio Audit
            ManualJournalPerformanceSummaryStrip(entries = journalEntries)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunAiCoachAudit,
                    enabled = !isRunningCoachAudit,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_coach_audit_button")
                ) {
                    if (isRunningCoachAudit) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunningCoachAudit) "Auditing Journal…" else "AI Coach Audit",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                OutlinedButton(
                    onClick = { showInlineManualForm = !showInlineManualForm },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("toggle_inline_manual_form_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Quick Log Form",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showInlineManualForm) "Hide Form" else "Quick Form",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
                    )
                }

                OutlinedButton(
                    onClick = { showAddJournalDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("add_journal_entry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Log Trade",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Log Trade",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("manual_trade_journal_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AnimatedVisibility(visible = showInlineManualForm) {
                        InlineManualTradeLoggerCard(
                            onSaveManualTrade = onAddManualJournalEntry
                        )
                    }
                }

                if (aiCoachAudit != null) {
                    item {
                        AiCoachPortfolioAuditCard(audit = aiCoachAudit)
                    }
                }

                items(journalEntries, key = { it.id }) { entry ->
                    JournalEntryCard(
                        entry = entry,
                        onDelete = { onDeleteJournalEntry(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmutableSignalResolutionSummaryStrip(
    totalSignals: Int,
    tpWins: Int,
    slLosses: Int,
    winRatePct: Double
) {
    val colors = LocalOrixColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("immutable_history_summary_strip"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryStatColumn(
            label = "LOCKED SIGNALS",
            value = totalSignals.toString(),
            valueColor = colors.primaryText
        )
        SummaryStatColumn(
            label = "TP / TSL WINS",
            value = "$tpWins TP",
            valueColor = colors.bullish
        )
        SummaryStatColumn(
            label = "SL HITS",
            value = "$slLosses SL",
            valueColor = colors.bearish
        )
        SummaryStatColumn(
            label = "VERIFIED WIN %",
            value = String.format(Locale.US, "%.1f%%", winRatePct),
            valueColor = if (winRatePct >= 55.0) colors.bullish else colors.accent
        )
    }
}

@Composable
private fun ManualJournalPerformanceSummaryStrip(
    entries: List<TradeJournalEntry>
) {
    val colors = LocalOrixColors.current
    val totalLogged = entries.size
    val netPnlUsd = entries.sumOf { it.pnlUsd }
    val winCount = entries.count { it.pnlUsd > 0.0 }
    val winRatePct = if (totalLogged > 0) (winCount.toDouble() / totalLogged.toDouble()) * 100.0 else 0.0
    val avgR = if (totalLogged > 0) entries.map { it.rMultiple }.average() else 0.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("manual_journal_summary_strip"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryStatColumn(
            label = "LOGGED TRADES",
            value = totalLogged.toString(),
            valueColor = colors.primaryText
        )
        SummaryStatColumn(
            label = "NET PNL (USD)",
            value = String.format(
                Locale.US,
                "%s$%,.2f",
                if (netPnlUsd >= 0.0) "+" else "-",
                kotlin.math.abs(netPnlUsd)
            ),
            valueColor = if (netPnlUsd >= 0.0) colors.bullish else colors.bearish
        )
        SummaryStatColumn(
            label = "JOURNAL WIN %",
            value = String.format(Locale.US, "%.1f%%", winRatePct),
            valueColor = if (winRatePct >= 50.0) colors.bullish else colors.bearish
        )
        SummaryStatColumn(
            label = "AVG R-MULTIPLE",
            value = String.format(Locale.US, "%+.2fR", avgR),
            valueColor = if (avgR >= 0.0) colors.bullish else colors.bearish
        )
    }
}

@Composable
private fun SummaryStatColumn(
    label: String,
    value: String,
    valueColor: Color
) {
    val colors = LocalOrixColors.current
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = TabularMonoStyle.copy(
                fontSize = 9.sp,
                color = colors.mutedText
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TabularMonoStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        )
    }
}

@Composable
private fun LockedSignalAuditCard(
    trade: ActiveManagedTrade,
    onImportToJournal: () -> Unit
) {
    val colors = LocalOrixColors.current
    val isSlHit = trade.status == "STOPPED_OUT"
    val isTpHit = trade.status.startsWith("TP") || trade.tp1Hit
    val statusTone = when {
        isSlHit -> colors.bearish
        isTpHit || trade.currentRMultiple > 0.0 -> colors.bullish
        else -> colors.accent
    }

    val humanResolutionBadge = when (trade.status) {
        "TP3_HIT" -> "TP3 HIT • FULL TARGET"
        "TP2_HIT" -> "TP2 HIT • PARTIAL LOCKED"
        "TP1_HIT" -> "TP1 HIT • BREAKEVEN TSL"
        "TSL_CLOSED" -> "TSL CLOSED • PROFIT LOCKED"
        "STOPPED_OUT" -> "SL HIT • STOPPED OUT"
        "EARLY_EXIT" -> "EARLY EXIT • INVALIDATED"
        else -> "ACTIVE • LIVE TRACKING"
    }

    val lockTimestampLabel = remember(trade.lockedAtBarCloseMs) {
        val sdf = SimpleDateFormat("MMM dd HH:mm 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        sdf.format(Date(trade.lockedAtBarCloseMs))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("locked_signal_audit_card_${trade.signalId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Immutable Bar-Close Lock",
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "${trade.signalId} • ${trade.displayPair} (${trade.timeframe})",
                        style = TabularMonoStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                    )
                    Text(
                        text = "Locked @ $lockTimestampLabel • ${trade.orderType}",
                        style = TabularMonoStyle.copy(
                            fontSize = 9.sp,
                            color = colors.mutedText
                        )
                    )
                }
            }
            Surface(
                color = statusTone.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .border(1.dp, statusTone.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .testTag("signal_resolution_badge_${trade.signalId}")
            ) {
                Text(
                    text = "$humanResolutionBadge (${String.format(Locale.US, "%+.2fR", trade.currentRMultiple)})",
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTone
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Locked Price Coordinates Row
        Text(
            text = "${trade.signal} • Entry $${String.format(Locale.US, "%,.2f", trade.entryMidpoint)} • Initial SL $${String.format(Locale.US, "%,.2f", trade.initialStopLoss)} • Final/Active TSL $${String.format(Locale.US, "%,.2f", trade.currentTrailingStop)}",
            style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.mutedText)
        )

        // Explicit TP1 / TP2 / TP3 / SL Resolution Milestone Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ResolutionMilestonePill(
                label = "TP1 (1.5R): $${String.format(Locale.US, "%,.2f", trade.tp1)}",
                isHit = trade.tp1Hit,
                isStopLoss = false
            )
            ResolutionMilestonePill(
                label = "TP2 (2.5R): $${String.format(Locale.US, "%,.2f", trade.tp2)}",
                isHit = trade.tp2Hit,
                isStopLoss = false
            )
            ResolutionMilestonePill(
                label = "TP3 (4.0R): $${String.format(Locale.US, "%,.2f", trade.tp3)}",
                isHit = trade.tp3Hit,
                isStopLoss = false
            )
            ResolutionMilestonePill(
                label = "SL (-1.0R): $${String.format(Locale.US, "%,.2f", trade.initialStopLoss)}",
                isHit = isSlHit,
                isStopLoss = true
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "MFE: +%.2f%% • MAE: -%.2f%% • %s",
                    trade.mfePct,
                    trade.maePct,
                    trade.tslPhaseLabel
                ),
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.accent),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onImportToJournal,
                modifier = Modifier.testTag("import_signal_to_journal_${trade.signalId}")
            ) {
                Text(
                    text = "Import to Journal",
                    style = TabularMonoStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
            }
        }
    }
}

@Composable
private fun ResolutionMilestonePill(
    label: String,
    isHit: Boolean,
    isStopLoss: Boolean
) {
    val colors = LocalOrixColors.current
    val pillColor = when {
        isStopLoss && isHit -> colors.bearish
        !isStopLoss && isHit -> colors.bullish
        else -> colors.mutedText
    }
    val bgAlpha = if (isHit) 0.16f else 0.06f

    Surface(
        color = pillColor.copy(alpha = bgAlpha),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.border(
            1.dp,
            if (isHit) pillColor.copy(alpha = 0.5f) else colors.border,
            RoundedCornerShape(6.dp)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            if (isHit) {
                Icon(
                    imageVector = if (isStopLoss) Icons.Default.Cancel else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = pillColor,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = TabularMonoStyle.copy(
                    fontSize = 9.sp,
                    fontWeight = if (isHit) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHit) pillColor else colors.mutedText
                )
            )
        }
    }
}

@Composable
private fun InlineManualTradeLoggerCard(
    onSaveManualTrade: (
        displayPair: String,
        timeframe: String,
        direction: String,
        entryPrice: Double,
        exitPrice: Double,
        positionSizeUsd: Double,
        setupTag: String,
        mindsetTag: String,
        notes: String
    ) -> Unit
) {
    val colors = LocalOrixColors.current
    var pair by remember { mutableStateOf("BTC/USD") }
    var timeframe by remember { mutableStateOf("15m") }
    var direction by remember { mutableStateOf("LONG") }
    var entryStr by remember { mutableStateOf("91000") }
    var exitStr by remember { mutableStateOf("92400") }
    var sizeStr by remember { mutableStateOf("5000") }
    var setupTag by remember { mutableStateOf("SMC OB") }
    var mindsetTag by remember { mutableStateOf("Disciplined") }
    var notes by remember { mutableStateOf("Executed after 15m bullish displacement & FVG test.") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("inline_manual_trade_form"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MANUAL TRADE LOGGER",
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("LONG", "SHORT").forEach { dir ->
                    val isSelected = direction == dir
                    val dirColor = if (dir == "LONG") colors.bullish else colors.bearish
                    Surface(
                        color = if (isSelected) dirColor.copy(alpha = 0.2f) else colors.surfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (isSelected) dirColor else colors.border,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { direction = dir }
                            .testTag("manual_direction_${dir.lowercase()}")
                    ) {
                        Text(
                            text = dir,
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) dirColor else colors.mutedText
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Button(
                    onClick = {
                        onSaveManualTrade(
                            pair.ifBlank { "BTC/USD" },
                            timeframe.ifBlank { "15m" },
                            direction,
                            entryStr.toDoubleOrNull() ?: 91000.0,
                            exitStr.toDoubleOrNull() ?: 92400.0,
                            sizeStr.toDoubleOrNull() ?: 5000.0,
                            setupTag,
                            mindsetTag,
                            notes.ifBlank { "Manual journal entry logged." }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("submit_manual_trade_button")
                ) {
                    Text(
                        text = "Save Manual Trade",
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pair,
                onValueChange = { pair = it },
                label = { Text("Pair", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("manual_pair_input")
            )
            OutlinedTextField(
                value = timeframe,
                onValueChange = { timeframe = it },
                label = { Text("Timeframe", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(0.8f)
                    .testTag("manual_timeframe_input")
            )
            OutlinedTextField(
                value = sizeStr,
                onValueChange = { sizeStr = it },
                label = { Text("Size ($)", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("manual_size_input")
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entryStr,
                onValueChange = { entryStr = it },
                label = { Text("Entry Price ($)", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("manual_entry_input")
            )
            OutlinedTextField(
                value = exitStr,
                onValueChange = { exitStr = it },
                label = { Text("Exit Price ($)", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("manual_exit_input")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("SMC OB", "FVG Pullback", "BOS Breakout", "Liquidity Sweep").forEach { tag ->
                val sel = setupTag == tag
                Surface(
                    color = if (sel) colors.accent else colors.surfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { setupTag = tag }
                ) {
                    Text(
                        text = tag,
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            color = if (sel) Color.White else colors.mutedText
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Disciplined", "Early Exit", "Hesitated", "FOMO").forEach { m ->
                val sel = mindsetTag == m
                Surface(
                    color = if (sel) colors.accent else colors.surfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { mindsetTag = m }
                ) {
                    Text(
                        text = m,
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            color = if (sel) Color.White else colors.mutedText
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Trade Execution Notes", fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_notes_input")
        )
    }
}

@Composable
private fun SavedVaultSignalRow(
    saved: SavedSignal,
    onDelete: () -> Unit
) {
    val colors = LocalOrixColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${saved.displayPair} (${saved.timeframe}) • ${saved.signal}",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            Text(
                text = "Status: ${saved.status} • R:R ${saved.riskRewardRatio}",
                style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.mutedText)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete",
                tint = colors.mutedText
            )
        }
    }
}

@Composable
private fun AiCoachPortfolioAuditCard(audit: AiCoachPortfolioAudit) {
    val colors = LocalOrixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.5.dp, colors.accent, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("ai_coach_audit_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GEMINI AI COACH PORTFOLIO AUDIT",
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            )
            Surface(
                color = colors.accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "GRADE ${audit.overallGrade}",
                    style = TabularMonoStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = audit.summaryHeadline,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )

        Text(
            text = "Best Setup: ${audit.bestPerformingSetup}",
            style = TabularMonoStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.bullish
            )
        )

        HorizontalDivider(color = colors.border)

        if (audit.behavioralLeaks.isNotEmpty()) {
            Text(
                text = "Behavioral & Execution Leaks Detected:",
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.bearish)
            )
            audit.behavioralLeaks.forEach { leak ->
                Text(
                    text = "• $leak",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = colors.primaryText)
                )
            }
        }

        if (audit.riskAdjustments.isNotEmpty()) {
            Text(
                text = "Recommended Quantitative Adjustments:",
                style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.accent)
            )
            audit.riskAdjustments.forEach { adj ->
                Text(
                    text = "• $adj",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = colors.primaryText)
                )
            }
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: TradeJournalEntry,
    onDelete: () -> Unit
) {
    val colors = LocalOrixColors.current
    val isProfit = entry.pnlUsd >= 0.0
    val pnlTone = if (isProfit) colors.bullish else colors.bearish

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("journal_entry_card_${entry.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.displayPair} (${entry.timeframe}) • ${entry.direction}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%s$%,.2f (%+.2fR)",
                        if (entry.pnlUsd >= 0.0) "+" else "-",
                        kotlin.math.abs(entry.pnlUsd),
                        entry.rMultiple
                    ),
                    style = TabularMonoStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = pnlTone
                    )
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Journal Entry",
                        tint = colors.mutedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Text(
            text = "Entry $${String.format(Locale.US, "%,.2f", entry.entryPrice)} → Exit $${String.format(Locale.US, "%,.2f", entry.exitPrice)} • Size $${String.format(Locale.US, "%,.0f", entry.positionSizeUsd)}",
            style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.mutedText)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                color = colors.accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Setup: ${entry.setupTag}",
                    style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.accent),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Surface(
                color = colors.surfaceElevated,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Mindset: ${entry.mindsetTag}",
                    style = TabularMonoStyle.copy(fontSize = 10.sp, color = colors.primaryText),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Text(
            text = entry.notes,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                color = colors.mutedText
            )
        )
    }
}

@Composable
private fun AddJournalEntryDialog(
    onDismiss: () -> Unit,
    onSave: (
        displayPair: String,
        timeframe: String,
        direction: String,
        entryPrice: Double,
        exitPrice: Double,
        positionSizeUsd: Double,
        setupTag: String,
        mindsetTag: String,
        notes: String
    ) -> Unit
) {
    val colors = LocalOrixColors.current
    var pair by remember { mutableStateOf("BTC/USD") }
    var timeframe by remember { mutableStateOf("15m") }
    var direction by remember { mutableStateOf("LONG") }
    var entryStr by remember { mutableStateOf("91000") }
    var exitStr by remember { mutableStateOf("92400") }
    var sizeStr by remember { mutableStateOf("5000") }
    var setupTag by remember { mutableStateOf("SMC OB") }
    var mindsetTag by remember { mutableStateOf("Disciplined") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        title = {
            Text(
                text = "Log Institutional Trade in Journal",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pair,
                        onValueChange = { pair = it },
                        label = { Text("Pair", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = direction,
                        onValueChange = { direction = it },
                        label = { Text("LONG/SHORT", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entryStr,
                        onValueChange = { entryStr = it },
                        label = { Text("Entry ($)", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = exitStr,
                        onValueChange = { exitStr = it },
                        label = { Text("Exit ($)", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SMC OB", "FVG Pullback", "BOS Breakout").forEach { tag ->
                        val sel = setupTag == tag
                        Surface(
                            color = if (sel) colors.accent else colors.surfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { setupTag = tag }
                        ) {
                            Text(
                                text = tag,
                                style = TabularMonoStyle.copy(
                                    fontSize = 10.sp,
                                    color = if (sel) Color.White else colors.mutedText
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Disciplined", "Early Exit", "Hesitated", "FOMO").forEach { m ->
                        val sel = mindsetTag == m
                        Surface(
                            color = if (sel) colors.accent else colors.surfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { mindsetTag = m }
                        ) {
                            Text(
                                text = m,
                                style = TabularMonoStyle.copy(
                                    fontSize = 10.sp,
                                    color = if (sel) Color.White else colors.mutedText
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Execution & Psychology Notes", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        pair,
                        timeframe,
                        direction,
                        entryStr.toDoubleOrNull() ?: 91000.0,
                        exitStr.toDoubleOrNull() ?: 92400.0,
                        sizeStr.toDoubleOrNull() ?: 5000.0,
                        setupTag,
                        mindsetTag,
                        notes
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Text("Save Trade", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.mutedText)
            }
        }
    )
}
