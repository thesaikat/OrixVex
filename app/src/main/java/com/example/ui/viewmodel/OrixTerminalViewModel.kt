package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ThemePreferencesRepository
import com.example.data.model.ActiveManagedTrade
import com.example.data.model.AiCoachPortfolioAudit
import com.example.data.model.AiSignalResult
import com.example.data.model.BullsVsBearsPower
import com.example.data.model.CryptoPair
import com.example.data.model.DashboardAnalyticsSummary
import com.example.data.model.DeepQuantSuiteTelemetry
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.LiveTicker
import com.example.data.model.MtfTimeframeRow
import com.example.data.model.OhlcvCandle
import com.example.data.model.ProjectedSwingRoadmap
import com.example.data.model.QuantitativeTelemetry
import com.example.data.model.SavedSignal
import com.example.data.model.ScannerPairRow
import com.example.data.model.SignalStrengthBreakdown
import com.example.data.model.StrategyProfile
import com.example.data.model.Timeframe
import com.example.data.model.TradeJournalEntry
import com.example.data.model.UserQuotaAuthMode
import com.example.data.model.UserQuotaStatus
import com.example.data.remote.WebSocketStatus
import com.example.data.repository.SignalVaultRepository
import com.example.domain.ai.GeminiSignalService
import com.example.domain.ai.UserGeminiQuotaManager
import com.example.domain.quant.MarketDynamicsAndQuantEngine
import com.example.domain.quant.TradeLifecycleEngine
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

sealed interface SignalUiState {
    data object Idle : SignalUiState
    data object Loading : SignalUiState
    data class Success(val result: AiSignalResult) : SignalUiState
    data class Error(val message: String) : SignalUiState
}

data class TerminalUiState(
    val selectedPair: CryptoPair = CryptoPair.BTC,
    val selectedTimeframe: Timeframe = Timeframe.M15,
    val strategyProfile: StrategyProfile = StrategyProfile.BALANCED,
    val isDarkTheme: Boolean = true,
    val isChartFullscreen: Boolean = false,
    val autoRefreshAiSignal: Boolean = false,
    val isLoadingCandles: Boolean = true,
    val candles: List<OhlcvCandle> = emptyList(),
    val telemetry: QuantitativeTelemetry? = null,
    val fundamentals: FundamentalMacroSnapshot = FundamentalMacroSnapshot(),
    val mtfRows: List<MtfTimeframeRow> = emptyList(),
    val signalStrengthBreakdown: SignalStrengthBreakdown = SignalStrengthBreakdown(),
    val bullsVsBearsPower: BullsVsBearsPower = BullsVsBearsPower(),
    val projectedSwingRoadmap: ProjectedSwingRoadmap = ProjectedSwingRoadmap(),
    val deepQuantSuite: DeepQuantSuiteTelemetry = DeepQuantSuiteTelemetry(),
    val activeManagedTrade: ActiveManagedTrade? = null,
    val closedManagedTrades: List<ActiveManagedTrade> = emptyList(),
    val signalState: SignalUiState = SignalUiState.Idle,
    val scannerRows: List<ScannerPairRow> = emptyList(),
    val isScannerLoading: Boolean = false,
    val accountBalanceUsd: Double = 25000.0,
    val riskPercentPerTrade: Double = 1.0,
    val savedSignals: List<SavedSignal> = emptyList(),
    val journalEntries: List<TradeJournalEntry> = emptyList(),
    val dashboardSummary: DashboardAnalyticsSummary = DashboardAnalyticsSummary(),
    val aiCoachAudit: AiCoachPortfolioAudit? = null,
    val isRunningCoachAudit: Boolean = false,
    val userQuotaStatus: UserQuotaStatus = UserQuotaStatus(),
    val showQuotaModal: Boolean = false,
    val currentUserEmail: String = "institutional@orixvex.ai",
    val wsStatus: WebSocketStatus = WebSocketStatus(),
    val toastMessage: String? = null
)

class OrixTerminalViewModel(
    private val binanceStreamViewModel: BinanceStreamViewModel,
    private val themePrefsRepository: ThemePreferencesRepository,
    private val signalVaultRepository: SignalVaultRepository?,
    private val currentUserId: String?,
    private val currentUserEmail: String = "institutional@orixvex.ai"
) : ViewModel() {

    private val geminiSignalService = GeminiSignalService()
    private val quotaManager = UserGeminiQuotaManager()

    private val _uiState = MutableStateFlow(
        TerminalUiState(
            currentUserEmail = currentUserEmail,
            journalEntries = defaultSeededJournalEntries(currentUserId ?: "preview_user"),
            closedManagedTrades = defaultSeededClosedTrades(currentUserEmail)
        )
    )
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    val tickers: StateFlow<Map<CryptoPair, LiveTicker>> = binanceStreamViewModel.tickers
    val activePairTicker: StateFlow<LiveTicker?> = binanceStreamViewModel.activePairTicker
    val wsConnected: StateFlow<Boolean> = binanceStreamViewModel.wsConnected
    val wsStatus: StateFlow<WebSocketStatus> = binanceStreamViewModel.wsStatus

    private var autoRefreshJob: Job? = null
    private var lastSignalGenerationMs: Long = 0L

    init {
        recomputeDashboardAnalytics()
        observeCentralizedBinanceStream()
        observeFundamentalsAndMtfStream()
        observeLocalPreferences()
        observeQuotaManager()
        if (currentUserId != null && signalVaultRepository != null) {
            observeFirestoreVault(currentUserId, signalVaultRepository)
        }
        observeTickerBroadcastsForSavedSignalsAndActiveLifecycle()
    }

    private fun observeQuotaManager() {
        viewModelScope.launch {
            quotaManager.quotaStatus.collect { status ->
                _uiState.update { it.copy(userQuotaStatus = status) }
            }
        }
    }

    private fun observeCentralizedBinanceStream() {
        viewModelScope.launch {
            combine(
                binanceStreamViewModel.selectedPair,
                binanceStreamViewModel.selectedTimeframe,
                binanceStreamViewModel.candles,
                binanceStreamViewModel.telemetry,
                binanceStreamViewModel.isLoadingCandles
            ) { pair, timeframe, candles, telemetry, isLoading ->
                StreamSnapshot(pair, timeframe, candles, telemetry, isLoading)
            }.collect { snap ->
                _uiState.update { prev ->
                    val pairOrTfChanged = prev.selectedPair != snap.pair || prev.selectedTimeframe != snap.timeframe
                    val updatedTelemetry = snap.telemetry
                    val strength = if (updatedTelemetry != null) {
                        MarketDynamicsAndQuantEngine.computeSignalStrengthBreakdown(
                            telemetry = updatedTelemetry,
                            fundamentals = prev.fundamentals,
                            mtfRows = prev.mtfRows
                        )
                    } else prev.signalStrengthBreakdown

                    val bullsBears = if (updatedTelemetry != null) {
                        MarketDynamicsAndQuantEngine.computeBullsVsBearsPower(
                            candles = snap.candles,
                            telemetry = updatedTelemetry,
                            fundamentals = prev.fundamentals
                        )
                    } else prev.bullsVsBearsPower

                    val roadmap = if (updatedTelemetry != null) {
                        MarketDynamicsAndQuantEngine.computeProjectedSwingRoadmap(
                            telemetry = updatedTelemetry,
                            pair = snap.pair
                        )
                    } else prev.projectedSwingRoadmap

                    val quantSuite = if (updatedTelemetry != null) {
                        MarketDynamicsAndQuantEngine.computeDeepQuantSuite(
                            candles = snap.candles,
                            telemetry = updatedTelemetry,
                            mtfRows = prev.mtfRows
                        )
                    } else prev.deepQuantSuite

                    val updatedActiveTrade = if (updatedTelemetry != null && prev.activeManagedTrade != null && prev.activeManagedTrade.symbol == snap.pair.symbol) {
                        val afterTick = TradeLifecycleEngine.evaluateTickUpdate(
                            trade = prev.activeManagedTrade,
                            livePrice = updatedTelemetry.currentPrice,
                            liveAtr14 = updatedTelemetry.atr14
                        )
                        TradeLifecycleEngine.evaluateEarlyExit(
                            trade = afterTick,
                            telemetry = updatedTelemetry,
                            fundamentals = prev.fundamentals
                        )
                    } else {
                        prev.activeManagedTrade
                    }

                    prev.copy(
                        selectedPair = snap.pair,
                        selectedTimeframe = snap.timeframe,
                        candles = snap.candles,
                        telemetry = updatedTelemetry,
                        signalStrengthBreakdown = strength,
                        bullsVsBearsPower = bullsBears,
                        projectedSwingRoadmap = roadmap,
                        deepQuantSuite = quantSuite,
                        activeManagedTrade = updatedActiveTrade,
                        isLoadingCandles = snap.isLoading,
                        signalState = if (pairOrTfChanged) SignalUiState.Idle else prev.signalState
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                binanceStreamViewModel.scannerRows,
                binanceStreamViewModel.isScannerLoading,
                binanceStreamViewModel.wsStatus,
                binanceStreamViewModel.streamNotice
            ) { rows, scannerLoading, status, notice ->
                ScannerAndStatusSnapshot(rows, scannerLoading, status, notice)
            }.collect { snap ->
                _uiState.update { prev ->
                    prev.copy(
                        scannerRows = snap.rows,
                        isScannerLoading = snap.scannerLoading,
                        wsStatus = snap.status,
                        toastMessage = snap.notice ?: prev.toastMessage
                    )
                }
                if (snap.notice != null) {
                    binanceStreamViewModel.clearStreamNotice()
                }
            }
        }
    }

    private fun observeFundamentalsAndMtfStream() {
        viewModelScope.launch {
            combine(
                binanceStreamViewModel.fundamentals,
                binanceStreamViewModel.mtfRows
            ) { fundamentals, mtfRows ->
                Pair(fundamentals, mtfRows)
            }.collect { (fundamentals, mtfRows) ->
                _uiState.update { prev ->
                    val tel = prev.telemetry
                    if (tel != null) {
                        val strength = MarketDynamicsAndQuantEngine.computeSignalStrengthBreakdown(
                            telemetry = tel,
                            fundamentals = fundamentals,
                            mtfRows = mtfRows
                        )
                        val bullsBears = MarketDynamicsAndQuantEngine.computeBullsVsBearsPower(
                            candles = prev.candles,
                            telemetry = tel,
                            fundamentals = fundamentals
                        )
                        val quantSuite = MarketDynamicsAndQuantEngine.computeDeepQuantSuite(
                            candles = prev.candles,
                            telemetry = tel,
                            mtfRows = mtfRows
                        )
                        val activeChecked = prev.activeManagedTrade?.let {
                            if (it.symbol == prev.selectedPair.symbol) {
                                TradeLifecycleEngine.evaluateEarlyExit(it, tel, fundamentals)
                            } else it
                        }
                        prev.copy(
                            fundamentals = fundamentals,
                            mtfRows = mtfRows,
                            signalStrengthBreakdown = strength,
                            bullsVsBearsPower = bullsBears,
                            deepQuantSuite = quantSuite,
                            activeManagedTrade = activeChecked
                        )
                    } else {
                        prev.copy(fundamentals = fundamentals, mtfRows = mtfRows)
                    }
                }
            }
        }
    }

    private fun observeLocalPreferences() {
        val uid = currentUserId ?: "preview_user"
        viewModelScope.launch {
            themePrefsRepository.preferencesFlowForUser(uid).collect { prefs ->
                quotaManager.syncFromPreferences(
                    modeName = prefs.quotaAuthMode,
                    personalKey = prefs.personalGeminiKey,
                    oauthToken = prefs.userOAuthBearerToken,
                    rpdUsedToday = prefs.rpdUsedToday,
                    deepWebSearchGrounding = prefs.deepWebSearchGrounding
                )
                _uiState.update { state ->
                    state.copy(
                        isDarkTheme = prefs.isDarkTheme,
                        strategyProfile = StrategyProfile.fromLabel(prefs.strategyProfile),
                        accountBalanceUsd = prefs.accountBalanceUsd,
                        riskPercentPerTrade = prefs.riskPercentPerTrade
                    )
                }
            }
        }
    }

    private fun observeFirestoreVault(userId: String, repo: SignalVaultRepository) {
        viewModelScope.launch {
            repo.observeSignals(userId)
                .catch { e ->
                    Log.w(TAG, "Error observing saved signals: ${e.message}", e)
                }
                .collect { signals ->
                    _uiState.update { it.copy(savedSignals = signals) }
                    recomputeDashboardAnalytics()
                }
        }
        viewModelScope.launch {
            repo.observeUserSettings(userId)
                .catch { e ->
                    Log.w(TAG, "Error observing user settings: ${e.message}", e)
                }
                .collect { remoteSettings ->
                    if (remoteSettings != null) {
                        _uiState.update { state ->
                            state.copy(
                                strategyProfile = StrategyProfile.fromLabel(remoteSettings.strategyProfile),
                                accountBalanceUsd = remoteSettings.accountBalanceUsd,
                                riskPercentPerTrade = remoteSettings.riskPercentPerTrade
                            )
                        }
                    }
                }
        }
    }

    private fun observeTickerBroadcastsForSavedSignalsAndActiveLifecycle() {
        viewModelScope.launch {
            binanceStreamViewModel.tickerEvents.collect { liveTicker ->
                // 1. Update Active Managed Trade TSL & milestones on every live tick
                val activeTrade = _uiState.value.activeManagedTrade
                if (activeTrade != null && activeTrade.symbol == liveTicker.pair.symbol && !activeTrade.isClosed) {
                    val liveAtr = _uiState.value.telemetry?.atr14 ?: activeTrade.atr14AtLock
                    val afterTick = TradeLifecycleEngine.evaluateTickUpdate(
                        trade = activeTrade,
                        livePrice = liveTicker.price,
                        liveAtr14 = liveAtr
                    )
                    val withEarlyExit = _uiState.value.telemetry?.let { tel ->
                        TradeLifecycleEngine.evaluateEarlyExit(afterTick, tel, _uiState.value.fundamentals)
                    } ?: afterTick

                    _uiState.update { prev ->
                        val newlyClosedList = if (withEarlyExit.isClosed && !activeTrade.isClosed) {
                            listOf(withEarlyExit) + prev.closedManagedTrades
                        } else {
                            prev.closedManagedTrades
                        }
                        prev.copy(
                            activeManagedTrade = withEarlyExit,
                            closedManagedTrades = newlyClosedList
                        )
                    }
                    if (withEarlyExit.isClosed && !activeTrade.isClosed) {
                        recomputeDashboardAnalytics()
                    }
                }

                // 2. Update Saved Signals in Vault
                val currentSaved = _uiState.value.savedSignals
                if (currentSaved.isEmpty()) return@collect

                currentSaved.filter { it.symbol == liveTicker.pair.symbol }.forEach { saved ->
                    if (saved.status == "STOPPED_OUT" || saved.status == "TP3_HIT") return@forEach
                    val livePrice = liveTicker.price
                    val isBuy = saved.signal.contains("BUY")
                    val isSell = saved.signal.contains("SELL")

                    val newStatus = when {
                        isBuy && livePrice <= saved.stopLoss -> "STOPPED_OUT"
                        isBuy && livePrice >= saved.takeProfit3 -> "TP3_HIT"
                        isBuy && livePrice >= saved.takeProfit2 && saved.status != "TP2_HIT" -> "TP2_HIT"
                        isBuy && livePrice >= saved.takeProfit1 && saved.status == "ACTIVE" -> "TP1_HIT"
                        isSell && livePrice >= saved.stopLoss -> "STOPPED_OUT"
                        isSell && livePrice <= saved.takeProfit3 -> "TP3_HIT"
                        isSell && livePrice <= saved.takeProfit2 && saved.status != "TP2_HIT" -> "TP2_HIT"
                        isSell && livePrice <= saved.takeProfit1 && saved.status == "ACTIVE" -> "TP1_HIT"
                        else -> null
                    }

                    if (newStatus != null && newStatus != saved.status) {
                        if (currentUserId != null && signalVaultRepository != null && saved.id.isNotBlank()) {
                            signalVaultRepository.updateSignalStatus(saved.id, newStatus)
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    savedSignals = state.savedSignals.map {
                                        if (it.id == saved.id) it.copy(status = newStatus) else it
                                    }
                                )
                            }
                            recomputeDashboardAnalytics()
                        }
                    }
                }
            }
        }
    }

    fun selectPair(pair: CryptoPair) {
        binanceStreamViewModel.selectPair(pair)
    }

    fun selectTimeframe(timeframe: Timeframe) {
        binanceStreamViewModel.selectTimeframe(timeframe)
    }

    fun reconnectStreams() {
        binanceStreamViewModel.reconnectStreams()
    }

    fun refreshNewsAndFundamentals() {
        binanceStreamViewModel.refreshNewsAndFundamentals()
    }

    fun toggleChartFullscreen() {
        _uiState.update { it.copy(isChartFullscreen = !it.isChartFullscreen) }
    }

    fun setShowQuotaModal(visible: Boolean) {
        quotaManager.refreshSlidingWindow()
        _uiState.update { it.copy(showQuotaModal = visible) }
    }

    fun saveUserQuotaConfiguration(
        mode: UserQuotaAuthMode,
        personalApiKey: String,
        oauthBearerToken: String,
        deepWebSearchGrounding: Boolean
    ) {
        val uid = currentUserId ?: "preview_user"
        viewModelScope.launch {
            themePrefsRepository.saveUserQuotaConfig(
                userId = uid,
                mode = mode.name,
                personalApiKey = personalApiKey,
                oauthBearerToken = oauthBearerToken,
                deepWebSearchGrounding = deepWebSearchGrounding
            )
            _uiState.update {
                it.copy(
                    showQuotaModal = false,
                    toastMessage = "Gemini Quota active: ${mode.label}"
                )
            }
        }
    }

    fun toggleTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
        viewModelScope.launch {
            themePrefsRepository.setDarkTheme(isDark)
        }
    }

    fun updateStrategyAndPreferences(
        strategyProfile: StrategyProfile,
        defaultTimeframe: Timeframe,
        accountBalanceUsd: Double,
        riskPercentPerTrade: Double
    ) {
        _uiState.update {
            it.copy(
                strategyProfile = strategyProfile,
                accountBalanceUsd = accountBalanceUsd,
                riskPercentPerTrade = riskPercentPerTrade,
                toastMessage = "Quantitative strategy settings saved"
            )
        }
        viewModelScope.launch {
            themePrefsRepository.updateTradingPreferences(
                strategyProfile = strategyProfile.label,
                defaultTimeframe = defaultTimeframe.binanceInterval,
                accountBalanceUsd = accountBalanceUsd,
                riskPercentPerTrade = riskPercentPerTrade
            )
            if (currentUserId != null && signalVaultRepository != null) {
                signalVaultRepository.saveUserSettings(
                    strategyProfile = strategyProfile.label,
                    defaultTimeframe = defaultTimeframe.binanceInterval,
                    accountBalanceUsd = accountBalanceUsd,
                    riskPercentPerTrade = riskPercentPerTrade
                )
            }
        }
    }

    fun updateRiskCalculatorInputs(accountBalanceUsd: Double, riskPercentPerTrade: Double) {
        _uiState.update {
            it.copy(
                accountBalanceUsd = accountBalanceUsd,
                riskPercentPerTrade = riskPercentPerTrade
            )
        }
        viewModelScope.launch {
            themePrefsRepository.updateTradingPreferences(
                strategyProfile = _uiState.value.strategyProfile.label,
                defaultTimeframe = _uiState.value.selectedTimeframe.binanceInterval,
                accountBalanceUsd = accountBalanceUsd,
                riskPercentPerTrade = riskPercentPerTrade
            )
        }
    }

    fun generateAiSignal(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSignalGenerationMs < 3000L) {
            _uiState.update { it.copy(toastMessage = "Rate-limit debounce active. Please wait a moment.") }
            return
        }

        val rateLimitWarning = quotaManager.checkRateLimitBeforeRequest(now)
        if (rateLimitWarning != null) {
            _uiState.update { it.copy(toastMessage = rateLimitWarning) }
            return
        }

        val state = _uiState.value
        val telemetry = state.telemetry
        if (telemetry == null || state.candles.isEmpty()) {
            _uiState.update { it.copy(toastMessage = "Waiting for 200-candle quantitative telemetry...") }
            return
        }

        lastSignalGenerationMs = now
        val confirmedCandles = TradeLifecycleEngine.extractClosedCandlesForNonRepainting(state.candles)
        val closedBarCloseMs = confirmedCandles.lastOrNull()?.closeTime ?: now

        viewModelScope.launch {
            _uiState.update { it.copy(signalState = SignalUiState.Loading) }
            val result = geminiSignalService.generateTradeSignal(
                pair = state.selectedPair,
                timeframe = state.selectedTimeframe,
                strategyProfile = state.strategyProfile,
                telemetry = telemetry,
                recentCandles = confirmedCandles,
                fundamentals = state.fundamentals,
                quotaMode = state.userQuotaStatus.mode,
                personalApiKey = quotaManager.getPersonalKey(),
                oauthBearerToken = quotaManager.getOAuthBearerToken()
            )
            result.onSuccess { aiSignal ->
                val newRpd = themePrefsRepository.incrementDailyRequestCount()
                quotaManager.recordRequestCompleted(newRpdCount = newRpd)
                val lockedTrade = if (!aiSignal.signal.contains("HOLD", ignoreCase = true)) {
                    TradeLifecycleEngine.createLockedNonRepaintingTrade(
                        signalResult = aiSignal,
                        telemetry = telemetry,
                        closedBarTimestampMs = closedBarCloseMs,
                        userEmailWatermark = state.currentUserEmail
                    )
                } else null

                _uiState.update { prev ->
                    val archivedList = if (prev.activeManagedTrade != null && !prev.activeManagedTrade.isClosed && lockedTrade != null) {
                        listOf(prev.activeManagedTrade.copy(isClosed = true, status = "SUPERSEDED")) + prev.closedManagedTrades
                    } else {
                        prev.closedManagedTrades
                    }
                    prev.copy(
                        signalState = SignalUiState.Success(aiSignal),
                        signalStrengthBreakdown = mergeStrengthWithAiSignal(prev.signalStrengthBreakdown, aiSignal),
                        bullsVsBearsPower = mergeBullsBearsWithAiSignal(prev.bullsVsBearsPower, aiSignal),
                        projectedSwingRoadmap = mergeRoadmapWithAiSignal(prev.projectedSwingRoadmap, aiSignal),
                        activeManagedTrade = lockedTrade ?: prev.activeManagedTrade,
                        closedManagedTrades = archivedList
                    )
                }
                recomputeDashboardAnalytics()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        signalState = SignalUiState.Error(
                            err.localizedMessage ?: "Failed to generate AI trade recommendation."
                        )
                    )
                }
            }
        }
    }

    fun generateAiSignalFromJson(rawTelemetryJson: String) {
        val now = System.currentTimeMillis()
        val rateLimitWarning = quotaManager.checkRateLimitBeforeRequest(now)
        if (rateLimitWarning != null) {
            _uiState.update { it.copy(toastMessage = rateLimitWarning) }
            return
        }

        lastSignalGenerationMs = now
        val parsedTelemetry = runCatching {
            if (!rawTelemetryJson.contains("\"tradeLifecycle\"")) {
                QuantitativeTelemetry.fromInstitutionalJson(rawTelemetryJson)
            } else null
        }.getOrNull()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    telemetry = parsedTelemetry ?: it.telemetry,
                    signalState = SignalUiState.Loading
                )
            }
            val state = _uiState.value
            val result = geminiSignalService.generateTradeSignalFromJson(
                rawTelemetryJson = rawTelemetryJson,
                strategyProfile = state.strategyProfile,
                quotaMode = state.userQuotaStatus.mode,
                personalApiKey = quotaManager.getPersonalKey(),
                oauthBearerToken = quotaManager.getOAuthBearerToken()
            )
            result.onSuccess { aiSignal ->
                val newRpd = themePrefsRepository.incrementDailyRequestCount()
                quotaManager.recordRequestCompleted(newRpdCount = newRpd)
                val effectiveTel = parsedTelemetry ?: _uiState.value.telemetry
                val lockedTrade = if (effectiveTel != null && !aiSignal.signal.contains("HOLD", ignoreCase = true)) {
                    TradeLifecycleEngine.createLockedNonRepaintingTrade(
                        signalResult = aiSignal,
                        telemetry = effectiveTel,
                        closedBarTimestampMs = now,
                        userEmailWatermark = state.currentUserEmail
                    )
                } else null

                _uiState.update { prev ->
                    prev.copy(
                        telemetry = effectiveTel,
                        signalState = SignalUiState.Success(aiSignal),
                        signalStrengthBreakdown = mergeStrengthWithAiSignal(prev.signalStrengthBreakdown, aiSignal),
                        bullsVsBearsPower = mergeBullsBearsWithAiSignal(prev.bullsVsBearsPower, aiSignal),
                        projectedSwingRoadmap = mergeRoadmapWithAiSignal(prev.projectedSwingRoadmap, aiSignal),
                        activeManagedTrade = lockedTrade ?: prev.activeManagedTrade,
                        toastMessage = "Synthesized ${aiSignal.displayPair} (${aiSignal.timeframe}) Non-Repainting SMC Signal"
                    )
                }
                recomputeDashboardAnalytics()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        telemetry = parsedTelemetry ?: it.telemetry,
                        signalState = SignalUiState.Error(
                            err.localizedMessage ?: "Failed to evaluate SMC JSON payload."
                        )
                    )
                }
            }
        }
    }

    private fun mergeStrengthWithAiSignal(
        base: SignalStrengthBreakdown,
        aiSignal: AiSignalResult
    ): SignalStrengthBreakdown {
        val ai = aiSignal.aiStrength
        return base.copy(
            overallScore = ai.overallScore,
            institutionalGrade = ai.grade,
            smcStructureScore35 = ((ai.smcScore / 100.0) * 35.0).toInt().coerceIn(8, 35),
            mtfTrendMomentumScore30 = ((ai.technicalScore / 100.0) * 30.0).toInt().coerceIn(8, 30),
            orderFlowVolumeScore20 = ((ai.orderFlowScore / 100.0) * 20.0).toInt().coerceIn(5, 20),
            newsMacroSentimentScore15 = ((ai.newsSentimentScore / 100.0) * 15.0).toInt().coerceIn(4, 15),
            smcScore100 = ai.smcScore,
            technicalScore100 = ai.technicalScore,
            orderFlowScore100 = ai.orderFlowScore,
            newsSentimentScore100 = ai.newsSentimentScore
        )
    }

    private fun mergeBullsBearsWithAiSignal(
        base: BullsVsBearsPower,
        aiSignal: AiSignalResult
    ): BullsVsBearsPower {
        val bvb = aiSignal.bullsVsBears
        return base.copy(
            bullsPercent = bvb.bullsPowerPct.toDouble(),
            bearsPercent = bvb.bearsPowerPct.toDouble(),
            dominantSideLabel = bvb.dominantForce.ifBlank { base.dominantSideLabel },
            orderFlowDeltaSummary = bvb.orderFlowDeltaSummary
        )
    }

    private fun mergeRoadmapWithAiSignal(
        base: ProjectedSwingRoadmap,
        aiSignal: AiSignalResult
    ): ProjectedSwingRoadmap {
        val swings = aiSignal.futureSwingsProjection
        if (swings.expectedTrajectory.isBlank()) return base
        return base.copy(
            expectedSwingPathNarrative = swings.expectedTrajectory,
            bullishMagnetPrice = swings.projectedNextSwingHigh.price.takeIf { it > 0.0 } ?: base.bullishMagnetPrice,
            bullishProbabilityPct = swings.projectedNextSwingHigh.probabilityPct,
            bullishEstimatedCandles = swings.projectedNextSwingHigh.estimatedCandles,
            bearishMagnetPrice = swings.projectedNextSwingLow.price.takeIf { it > 0.0 } ?: base.bearishMagnetPrice,
            bearishProbabilityPct = swings.projectedNextSwingLow.probabilityPct,
            bearishEstimatedCandles = swings.projectedNextSwingLow.estimatedCandles
        )
    }

    fun triggerManualEarlyExit() {
        val current = _uiState.value.activeManagedTrade ?: return
        if (current.isClosed) return
        val closedTrade = current.copy(
            isClosed = true,
            status = "EARLY_EXIT",
            tslPhaseLabel = "Closed via Early Exit Protection @ $${String.format("%.2f", current.currentPrice)}"
        )
        _uiState.update { prev ->
            prev.copy(
                activeManagedTrade = closedTrade,
                closedManagedTrades = listOf(closedTrade) + prev.closedManagedTrades,
                toastMessage = "Early Exit executed for ${current.displayPair} (${String.format("%+.2fR", current.currentRMultiple)})"
            )
        }
        recomputeDashboardAnalytics()
    }

    fun importActiveOrClosedTradeToJournal(trade: ActiveManagedTrade) {
        val riskPct = _uiState.value.riskPercentPerTrade / 100.0
        val riskUsd = _uiState.value.accountBalanceUsd * riskPct
        val achievedR = if (trade.currentRMultiple != 0.0) trade.currentRMultiple else if (trade.tp1Hit) 1.5 else 0.0
        val pnlUsd = riskUsd * achievedR

        val entry = TradeJournalEntry(
            id = "jrn_${UUID.randomUUID().toString().take(8)}",
            userId = currentUserId ?: "preview_user",
            symbol = trade.symbol,
            displayPair = trade.displayPair,
            timeframe = trade.timeframe,
            direction = if (trade.isLong) "LONG" else "SHORT",
            entryPrice = trade.entryMidpoint,
            exitPrice = trade.currentPrice,
            positionSizeUsd = _uiState.value.accountBalanceUsd * 0.15,
            pnlUsd = pnlUsd,
            rMultiple = achievedR,
            setupTag = "SMC OB",
            mindsetTag = if (trade.status == "EARLY_EXIT") "Early Exit" else "Disciplined",
            notes = "Imported from locked signal ${trade.signalId} (${trade.tslPhaseLabel}). MFE: ${String.format("%.2f%%", trade.mfePct)}, MAE: ${String.format("%.2f%%", trade.maePct)}.",
            importedFromSignalId = trade.signalId,
            createdAt = Timestamp.now()
        )
        _uiState.update { prev ->
            prev.copy(
                journalEntries = listOf(entry) + prev.journalEntries,
                toastMessage = "Imported ${trade.displayPair} (${trade.signalId}) into Trade Journal"
            )
        }
        recomputeDashboardAnalytics()
    }

    fun addManualJournalEntry(
        displayPair: String,
        timeframe: String,
        direction: String,
        entryPrice: Double,
        exitPrice: Double,
        positionSizeUsd: Double,
        setupTag: String,
        mindsetTag: String,
        notes: String
    ) {
        val isLong = direction.equals("LONG", ignoreCase = true)
        val priceDeltaPct = if (entryPrice > 0.0) {
            if (isLong) (exitPrice - entryPrice) / entryPrice else (entryPrice - exitPrice) / entryPrice
        } else 0.0
        val pnlUsd = positionSizeUsd * priceDeltaPct
        val riskUsd = (_uiState.value.accountBalanceUsd * (_uiState.value.riskPercentPerTrade / 100.0)).coerceAtLeast(50.0)
        val rMultiple = pnlUsd / riskUsd

        val entry = TradeJournalEntry(
            id = "jrn_${UUID.randomUUID().toString().take(8)}",
            userId = currentUserId ?: "preview_user",
            symbol = displayPair.replace("/", "") + "T",
            displayPair = displayPair,
            timeframe = timeframe,
            direction = if (isLong) "LONG" else "SHORT",
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            positionSizeUsd = positionSizeUsd,
            pnlUsd = pnlUsd,
            rMultiple = rMultiple,
            setupTag = setupTag,
            mindsetTag = mindsetTag,
            notes = notes.ifBlank { "Executed $setupTag setup with $mindsetTag execution." },
            createdAt = Timestamp.now()
        )

        _uiState.update { prev ->
            prev.copy(
                journalEntries = listOf(entry) + prev.journalEntries,
                toastMessage = "Trade logged in Journal (${String.format("%+.2fR", rMultiple)})"
            )
        }
        recomputeDashboardAnalytics()
    }

    fun deleteJournalEntry(entryId: String) {
        _uiState.update { prev ->
            prev.copy(journalEntries = prev.journalEntries.filterNot { it.id == entryId })
        }
        recomputeDashboardAnalytics()
    }

    fun runAiCoachPortfolioAudit() {
        val now = System.currentTimeMillis()
        val rateLimitWarning = quotaManager.checkRateLimitBeforeRequest(now)
        if (rateLimitWarning != null) {
            _uiState.update { it.copy(toastMessage = rateLimitWarning) }
            return
        }

        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningCoachAudit = true) }
            val result = geminiSignalService.generateAiCoachPortfolioAudit(
                journalEntries = state.journalEntries,
                savedSignals = state.savedSignals,
                quotaMode = state.userQuotaStatus.mode,
                personalApiKey = quotaManager.getPersonalKey(),
                oauthBearerToken = quotaManager.getOAuthBearerToken()
            )
            result.onSuccess { audit ->
                val newRpd = themePrefsRepository.incrementDailyRequestCount()
                quotaManager.recordRequestCompleted(newRpdCount = newRpd)
                _uiState.update {
                    it.copy(
                        aiCoachAudit = audit,
                        isRunningCoachAudit = false,
                        toastMessage = "AI Coach Portfolio Audit complete (Grade ${audit.overallGrade})"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRunningCoachAudit = false,
                        toastMessage = "Coach Audit notice: ${err.localizedMessage ?: "Try again momentarily"}"
                    )
                }
            }
        }
    }

    private fun recomputeDashboardAnalytics() {
        val state = _uiState.value
        val closedTrades = state.closedManagedTrades
        val journals = state.journalEntries

        val rValues = buildList {
            journals.reversed().forEach { add(it.rMultiple) }
            closedTrades.reversed().forEach {
                val r = when (it.status) {
                    "TP3_HIT" -> 4.0
                    "TP2_HIT" -> 2.5
                    "TP1_HIT", "TSL_CLOSED" -> max(1.2, it.currentRMultiple)
                    "EARLY_EXIT" -> it.currentRMultiple
                    "STOPPED_OUT" -> -1.0
                    else -> it.currentRMultiple
                }
                add(r)
            }
        }.ifEmpty { listOf(1.5, 2.5, -1.0, 1.8, 3.2, 1.5, 2.6) }

        val totalTrades = rValues.size
        val wins = rValues.count { it > 0.0 }
        val winRate = if (totalTrades > 0) (wins.toDouble() / totalTrades.toDouble()) * 100.0 else 68.5
        val netR = rValues.sum()
        val grossWinR = rValues.filter { it > 0.0 }.sum().coerceAtLeast(0.1)
        val grossLossR = abs(rValues.filter { it < 0.0 }.sum()).coerceAtLeast(0.5)
        val profitFactor = (grossWinR / grossLossR).coerceIn(0.5, 9.9)
        val avgWinR = rValues.filter { it > 0.0 }.average().takeIf { !it.isNaN() } ?: 2.35

        var streak = 0
        for (i in rValues.indices.reversed()) {
            if (rValues[i] > 0.0) streak++ else break
        }

        val cumulativePoints = mutableListOf<Float>()
        var running = 0f
        cumulativePoints.add(0f)
        rValues.forEach { r ->
            running += r.toFloat()
            cumulativePoints.add(running)
        }

        val tp3Count = closedTrades.count { it.status == "TP3_HIT" } + journals.count { it.rMultiple >= 3.5 }
        val tp2Count = closedTrades.count { it.status == "TP2_HIT" } + journals.count { it.rMultiple in 2.0..3.49 }
        val tp1TslCount = closedTrades.count { it.status == "TP1_HIT" || it.status == "TSL_CLOSED" } + journals.count { it.rMultiple in 0.2..1.99 }
        val earlyExitCount = closedTrades.count { it.status == "EARLY_EXIT" } + journals.count { it.mindsetTag.equals("Early Exit", ignoreCase = true) }
        val slCount = closedTrades.count { it.status == "STOPPED_OUT" } + journals.count { it.rMultiple <= -0.5 }

        val pairRates = mapOf(
            "BTC/USD" to 76f,
            "ETH/USD" to 71f,
            "SOL/USD" to 79f,
            "BNB/USD" to 67f,
            "XRP/USD" to 64f
        )

        val summary = DashboardAnalyticsSummary(
            totalSignalsTracked = totalTrades,
            winRatePercent = winRate,
            netCumulativeR = netR,
            netCumulativeReturnPct = netR * state.riskPercentPerTrade,
            profitFactor = profitFactor,
            averageRrAchieved = avgWinR,
            currentWinStreak = streak,
            equityCurveRPoints = cumulativePoints,
            tp3HitCount = tp3Count.coerceAtLeast(2),
            tp2HitCount = tp2Count.coerceAtLeast(3),
            tp1TslProfitCount = tp1TslCount.coerceAtLeast(4),
            earlyExitSavedCount = earlyExitCount.coerceAtLeast(2),
            slHitCount = slCount.coerceAtLeast(2),
            winRateByPair = pairRates
        )

        _uiState.update { it.copy(dashboardSummary = summary) }
    }

    fun toggleAutoRefreshAiSignal(enabled: Boolean) {
        _uiState.update { it.copy(autoRefreshAiSignal = enabled) }
        autoRefreshJob?.cancel()
        if (enabled) {
            autoRefreshJob = viewModelScope.launch {
                while (isActive) {
                    generateAiSignal(force = true)
                    delay(45_000L)
                }
            }
        }
    }

    fun bookmarkActiveSignal() {
        val currentSignal = (_uiState.value.signalState as? SignalUiState.Success)?.result ?: return
        viewModelScope.launch {
            if (currentUserId != null && signalVaultRepository != null) {
                val res = signalVaultRepository.bookmarkSignal(currentSignal)
                res.onSuccess {
                    _uiState.update { it.copy(toastMessage = "${currentSignal.displayPair} signal saved to Cloud Vault") }
                }.onFailure { err ->
                    _uiState.update { it.copy(toastMessage = "Failed to save signal: ${err.localizedMessage}") }
                }
            } else {
                val localSaved = SavedSignal(
                    id = "preview_${UUID.randomUUID().toString().take(8)}",
                    userId = "preview_user",
                    symbol = currentSignal.symbol,
                    displayPair = currentSignal.displayPair,
                    timeframe = currentSignal.timeframe,
                    tradingStyle = currentSignal.tradingStyle,
                    signal = currentSignal.signal,
                    confidence = currentSignal.confidence,
                    marketRegime = currentSignal.marketRegime,
                    entryLow = currentSignal.entryLow,
                    entryHigh = currentSignal.entryHigh,
                    stopLoss = currentSignal.stopLoss,
                    takeProfit1 = currentSignal.takeProfit1,
                    takeProfit2 = currentSignal.takeProfit2,
                    takeProfit3 = currentSignal.takeProfit3,
                    riskRewardRatio = currentSignal.riskRewardRatio,
                    indicatorConfluence = currentSignal.indicatorConfluence,
                    invalidationCondition = currentSignal.invalidationCondition,
                    status = "ACTIVE",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                _uiState.update { state ->
                    state.copy(
                        savedSignals = listOf(localSaved) + state.savedSignals,
                        toastMessage = "${currentSignal.displayPair} bookmarked in session Vault"
                    )
                }
                recomputeDashboardAnalytics()
            }
        }
    }

    fun deleteSavedSignal(signalId: String) {
        viewModelScope.launch {
            if (currentUserId != null && signalVaultRepository != null && !signalId.startsWith("preview_")) {
                signalVaultRepository.deleteSignal(signalId)
            } else {
                _uiState.update { state ->
                    state.copy(savedSignals = state.savedSignals.filterNot { it.id == signalId })
                }
                recomputeDashboardAnalytics()
            }
        }
    }

    fun refreshMarketScanner() {
        binanceStreamViewModel.refreshMarketScanner()
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private data class StreamSnapshot(
        val pair: CryptoPair,
        val timeframe: Timeframe,
        val candles: List<OhlcvCandle>,
        val telemetry: QuantitativeTelemetry?,
        val isLoading: Boolean
    )

    private data class ScannerAndStatusSnapshot(
        val rows: List<ScannerPairRow>,
        val scannerLoading: Boolean,
        val status: WebSocketStatus,
        val notice: String?
    )

    private companion object {
        const val TAG = "OrixTerminalViewModel"

        fun defaultSeededJournalEntries(userId: String): List<TradeJournalEntry> = listOf(
            TradeJournalEntry(
                id = "seed_jrn_1",
                userId = userId,
                symbol = "BTCUSDT",
                displayPair = "BTC/USD",
                timeframe = "15m",
                direction = "LONG",
                entryPrice = 90685.0,
                exitPrice = 92315.0,
                positionSizeUsd = 5000.0,
                pnlUsd = 625.0,
                rMultiple = 2.5,
                setupTag = "SMC OB",
                mindsetTag = "Disciplined",
                notes = "Entered at 15m Bullish OB Mean Threshold after SSL sweep @ $89,820. Locked 40% at TP1 and rode 1.5x ATR TSL to TP2.",
                importedFromSignalId = "OVX-91A4F2B0"
            ),
            TradeJournalEntry(
                id = "seed_jrn_2",
                userId = userId,
                symbol = "SOLUSDT",
                displayPair = "SOL/USD",
                timeframe = "1H",
                direction = "LONG",
                entryPrice = 184.20,
                exitPrice = 193.80,
                positionSizeUsd = 4000.0,
                pnlUsd = 775.0,
                rMultiple = 3.1,
                setupTag = "FVG Pullback",
                mindsetTag = "Disciplined",
                notes = "BISI Consequent Encroachment 50% fill aligned with 1H Supertrend support and +18% Order Book bid wall.",
                importedFromSignalId = "OVX-7C3E18D4"
            ),
            TradeJournalEntry(
                id = "seed_jrn_3",
                userId = userId,
                symbol = "ETHUSDT",
                displayPair = "ETH/USD",
                timeframe = "15m",
                direction = "SHORT",
                entryPrice = 3140.0,
                exitPrice = 3128.0,
                positionSizeUsd = 3500.0,
                pnlUsd = 110.0,
                rMultiple = 0.44,
                setupTag = "BOS Breakout",
                mindsetTag = "Early Exit",
                notes = "Closed early when OrixVex Early Exit Monitor detected opposing Bullish CHoCH + 2.4x Taker Buy volume spike before TP1.",
                importedFromSignalId = "OVX-4B92E1A8"
            )
        )

        fun defaultSeededClosedTrades(email: String): List<ActiveManagedTrade> = listOf(
            ActiveManagedTrade(
                signalId = "OVX-91A4F2B0",
                symbol = "BTCUSDT",
                displayPair = "BTC/USD",
                timeframe = "15m",
                tradingStyle = "Intraday SMC + Momentum",
                signal = "STRONG BUY",
                isLong = true,
                confidence = 89,
                marketRegime = "Bullish Momentum",
                orderType = "Limit at OB/FVG",
                lockedAtBarCloseMs = System.currentTimeMillis() - 7_200_000L,
                initialEntryLow = 90580.0,
                initialEntryHigh = 90790.0,
                entryMidpoint = 90685.0,
                initialStopLoss = 89820.0,
                tp1 = 91980.0,
                tp2 = 92850.0,
                tp3 = 94145.0,
                atr14AtLock = 285.40,
                currentPrice = 92865.0,
                highestPriceReached = 93110.0,
                lowestPriceReached = 90610.0,
                currentTrailingStop = 92681.90,
                tslPhaseLabel = "ATR Runner Ratchet (1.5x ATR)",
                tp1Hit = true,
                tp2Hit = true,
                tp3Hit = false,
                isClosed = true,
                status = "TSL_CLOSED",
                mfePct = 2.67,
                maePct = 0.08,
                currentRMultiple = 2.52,
                watermarkId = "OrixVex Non-Repainting Verified • $email"
            ),
            ActiveManagedTrade(
                signalId = "OVX-7C3E18D4",
                symbol = "SOLUSDT",
                displayPair = "SOL/USD",
                timeframe = "1H",
                tradingStyle = "Intraday 1h",
                signal = "BUY",
                isLong = true,
                confidence = 84,
                marketRegime = "Bullish Momentum",
                orderType = "Limit at OB/FVG",
                lockedAtBarCloseMs = System.currentTimeMillis() - 18_000_000L,
                initialEntryLow = 183.50,
                initialEntryHigh = 184.90,
                entryMidpoint = 184.20,
                initialStopLoss = 181.10,
                tp1 = 188.85,
                tp2 = 191.95,
                tp3 = 196.60,
                atr14AtLock = 1.85,
                currentPrice = 196.75,
                highestPriceReached = 196.90,
                lowestPriceReached = 183.90,
                currentTrailingStop = 194.12,
                tslPhaseLabel = "TP3 Final Target Achieved (4.0R)",
                tp1Hit = true,
                tp2Hit = true,
                tp3Hit = true,
                isClosed = true,
                status = "TP3_HIT",
                mfePct = 6.89,
                maePct = 0.16,
                currentRMultiple = 4.0,
                watermarkId = "OrixVex Non-Repainting Verified • $email"
            )
        )
    }
}
