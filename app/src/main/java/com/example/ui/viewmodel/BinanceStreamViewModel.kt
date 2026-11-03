package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CryptoPair
import com.example.data.model.FundamentalMacroSnapshot
import com.example.data.model.LiveTicker
import com.example.data.model.MtfTimeframeRow
import com.example.data.model.OhlcvCandle
import com.example.data.model.QuantitativeTelemetry
import com.example.data.model.ScannerPairRow
import com.example.data.model.Timeframe
import com.example.data.remote.BinanceMarketService
import com.example.data.remote.KlineStreamEvent
import com.example.data.remote.NewsAndFundamentalsService
import com.example.data.remote.WebSocketStatus
import com.example.domain.quant.QuantitativeIndicatorEngine
import com.example.domain.quant.TradeLifecycleEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Centralized ViewModel managing the Binance WebSocket connection, shared state for the
 * currently selected cryptocurrency pair and timeframe, real-time broadcasts of
 * 24h ticker updates, historical + live Kline candles, Order Book depth, Multi-Timeframe
 * alignment matrix, and live Trading News & Fundamental Sentiment.
 */
class BinanceStreamViewModel : ViewModel() {

    private val marketService = BinanceMarketService(viewModelScope)
    private val newsService = NewsAndFundamentalsService()

    private val _selectedPair = MutableStateFlow(CryptoPair.BTC)
    val selectedPair: StateFlow<CryptoPair> = _selectedPair.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.M15)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    val tickers: StateFlow<Map<CryptoPair, LiveTicker>> = marketService.tickers
    val tickerEvents: SharedFlow<LiveTicker> = marketService.tickerEvents
    val klineEvents: SharedFlow<KlineStreamEvent> = marketService.klineEvents
    val wsStatus: StateFlow<WebSocketStatus> = marketService.wsStatus
    val wsConnected: StateFlow<Boolean> = marketService.wsConnected

    val activePairTicker: StateFlow<LiveTicker?> = combine(
        _selectedPair,
        marketService.tickers
    ) { pair, tickerMap ->
        tickerMap[pair]
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null
    )

    private val _candles = MutableStateFlow<List<OhlcvCandle>>(emptyList())
    val candles: StateFlow<List<OhlcvCandle>> = _candles.asStateFlow()

    private val _telemetry = MutableStateFlow<QuantitativeTelemetry?>(null)
    val telemetry: StateFlow<QuantitativeTelemetry?> = _telemetry.asStateFlow()

    private val _fundamentals = MutableStateFlow(FundamentalMacroSnapshot())
    val fundamentals: StateFlow<FundamentalMacroSnapshot> = _fundamentals.asStateFlow()

    private val _mtfRows = MutableStateFlow<List<MtfTimeframeRow>>(emptyList())
    val mtfRows: StateFlow<List<MtfTimeframeRow>> = _mtfRows.asStateFlow()

    private val _isLoadingCandles = MutableStateFlow(true)
    val isLoadingCandles: StateFlow<Boolean> = _isLoadingCandles.asStateFlow()

    private val _scannerRows = MutableStateFlow<List<ScannerPairRow>>(emptyList())
    val scannerRows: StateFlow<List<ScannerPairRow>> = _scannerRows.asStateFlow()

    private val _isScannerLoading = MutableStateFlow(false)
    val isScannerLoading: StateFlow<Boolean> = _isScannerLoading.asStateFlow()

    private val _streamNotice = MutableStateFlow<String?>(null)
    val streamNotice: StateFlow<String?> = _streamNotice.asStateFlow()

    private var klineHistoryJob: Job? = null
    private var fundamentalsJob: Job? = null
    private var mtfJob: Job? = null

    init {
        marketService.initializeStreams(
            selectedPair = _selectedPair.value,
            selectedTimeframe = _selectedTimeframe.value
        )
        loadHistoricalCandles(_selectedPair.value, _selectedTimeframe.value)
        collectLiveKlineBroadcasts()
        refreshMarketScanner()
    }

    private fun collectLiveKlineBroadcasts() {
        viewModelScope.launch {
            marketService.klineEvents.collect { event ->
                val activePair = _selectedPair.value
                val activeTf = _selectedTimeframe.value
                if (event.symbol != activePair.symbol || event.interval != activeTf.binanceInterval) {
                    return@collect
                }
                val currentList = _candles.value
                if (currentList.isEmpty()) return@collect

                val merged = mergeLiveKlineCandle(currentList, event.candle, maxCandles = 200)
                _candles.value = merged
                val confirmedClosed = TradeLifecycleEngine.extractClosedCandlesForNonRepainting(merged)
                val baseTelemetry = QuantitativeIndicatorEngine.computeTelemetry(
                    symbol = activePair.symbol,
                    timeframe = activeTf.binanceInterval,
                    candles = confirmedClosed
                )
                _telemetry.value = baseTelemetry.copy(currentPrice = event.candle.close)
            }
        }
    }

    fun selectPair(pair: CryptoPair) {
        if (_selectedPair.value == pair) return
        _selectedPair.value = pair
        marketService.updateSubscription(pair, _selectedTimeframe.value)
        loadHistoricalCandles(pair, _selectedTimeframe.value)
    }

    fun selectTimeframe(timeframe: Timeframe) {
        if (_selectedTimeframe.value == timeframe) return
        _selectedTimeframe.value = timeframe
        marketService.updateSubscription(_selectedPair.value, timeframe)
        loadHistoricalCandles(_selectedPair.value, timeframe)
    }

    fun reconnectStreams() {
        marketService.forceReconnect()
        loadHistoricalCandles(_selectedPair.value, _selectedTimeframe.value)
    }

    fun loadHistoricalCandles(
        pair: CryptoPair = _selectedPair.value,
        timeframe: Timeframe = _selectedTimeframe.value
    ) {
        klineHistoryJob?.cancel()
        klineHistoryJob = viewModelScope.launch {
            _isLoadingCandles.value = true
            val result = marketService.fetchHistoricalKlines(pair, timeframe, limit = 200)
            result.onSuccess { fetchedCandles ->
                _candles.value = fetchedCandles
                val confirmedClosed = TradeLifecycleEngine.extractClosedCandlesForNonRepainting(fetchedCandles)
                val computed = QuantitativeIndicatorEngine.computeTelemetry(
                    symbol = pair.symbol,
                    timeframe = timeframe.binanceInterval,
                    candles = confirmedClosed
                )
                val latestPrice = fetchedCandles.lastOrNull()?.close ?: computed.currentPrice
                _telemetry.value = computed.copy(currentPrice = latestPrice)
                _isLoadingCandles.value = false
                refreshNewsAndFundamentals(pair, fetchedCandles)
                refreshMtfAlignment(pair)
            }.onFailure { err ->
                _isLoadingCandles.value = false
                _streamNotice.value = "Market data notice: ${err.localizedMessage ?: "Retrying Binance endpoint"}"
            }
        }
    }

    fun refreshNewsAndFundamentals(
        pair: CryptoPair = _selectedPair.value,
        candlesList: List<OhlcvCandle> = _candles.value
    ) {
        fundamentalsJob?.cancel()
        fundamentalsJob = viewModelScope.launch {
            val depth = marketService.fetchOrderBookDepth(pair, limit = 20).getOrNull()
            val snapshot = newsService.fetchFundamentalSnapshot(
                pair = pair,
                candles = candlesList,
                orderBookDepth = depth
            )
            _fundamentals.value = snapshot
        }
    }

    fun refreshMtfAlignment(pair: CryptoPair = _selectedPair.value) {
        mtfJob?.cancel()
        mtfJob = viewModelScope.launch {
            val mtfTimeframes = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1)
            val deferred = mtfTimeframes.map { tf ->
                async {
                    val klines = marketService.fetchHistoricalKlines(pair, tf, limit = 90).getOrNull()
                    if (!klines.isNullOrEmpty()) {
                        val closed = TradeLifecycleEngine.extractClosedCandlesForNonRepainting(klines)
                        val tel = QuantitativeIndicatorEngine.computeTelemetry(
                            symbol = pair.symbol,
                            timeframe = tf.binanceInterval,
                            candles = closed
                        )
                        val isBull = tel.ema20 >= tel.ema50 && tel.supertrendBullish
                        val trendStr = when {
                            tel.ema20 > tel.ema50 && tel.supertrendBullish -> "BULLISH"
                            tel.ema20 < tel.ema50 && !tel.supertrendBullish -> "BEARISH"
                            else -> "NEUTRAL"
                        }
                        MtfTimeframeRow(
                            timeframeLabel = tf.label,
                            trend = trendStr,
                            isBullish = isBull,
                            rsi14 = tel.rsi14,
                            supertrendBullish = tel.supertrendBullish,
                            vwapAbove = tel.currentPrice >= tel.vwap,
                            smcStructure = tel.smc.structureState
                        )
                    } else null
                }
            }
            val rows = deferred.awaitAll().filterNotNull()
            if (rows.isNotEmpty()) {
                _mtfRows.value = rows
            }
        }
    }

    fun refreshMarketScanner() {
        val tf = _selectedTimeframe.value
        viewModelScope.launch {
            _isScannerLoading.value = true
            val deferredRows = CryptoPair.entries.map { pair ->
                async {
                    val klinesRes = marketService.fetchHistoricalKlines(pair, tf, limit = 200)
                    val pairCandles = klinesRes.getOrNull()
                    if (!pairCandles.isNullOrEmpty()) {
                        val tel = QuantitativeIndicatorEngine.computeTelemetry(
                            symbol = pair.symbol,
                            timeframe = tf.binanceInterval,
                            candles = pairCandles
                        )
                        val ticker = tickers.value[pair]
                        val changePct = ticker?.priceChangePercent24h ?: run {
                            val first = pairCandles.first().open
                            if (first > 0) ((pairCandles.last().close - first) / first) * 100.0 else 0.0
                        }
                        val volUsd = ticker?.volumeUsd24h ?: pairCandles.sumOf { it.quoteVolume }
                        ScannerPairRow(
                            pair = pair,
                            price = tel.currentPrice,
                            change24hPercent = changePct,
                            volumeUsd24h = volUsd,
                            rsi14 = tel.rsi14,
                            emaTrend = tel.emaAlignment,
                            supertrendBullish = tel.supertrendBullish,
                            vwapDeltaPercent = tel.vwapDeltaPercent,
                            smcStructure = tel.smc.structureState,
                            smcDealingZone = tel.smc.dealingZoneLabel
                        )
                    } else {
                        null
                    }
                }
            }
            val rows = deferredRows.awaitAll().filterNotNull()
            _scannerRows.update { rows }
            _isScannerLoading.value = false
        }
    }

    fun clearStreamNotice() {
        _streamNotice.value = null
    }

    override fun onCleared() {
        super.onCleared()
        marketService.close()
    }

    companion object {
        fun mergeLiveKlineCandle(
            existingCandles: List<OhlcvCandle>,
            incomingCandle: OhlcvCandle,
            maxCandles: Int = 200
        ): List<OhlcvCandle> {
            if (existingCandles.isEmpty()) return listOf(incomingCandle)
            val mutable = existingCandles.toMutableList()
            val lastIdx = mutable.lastIndex
            when {
                mutable[lastIdx].openTime == incomingCandle.openTime -> {
                    mutable[lastIdx] = incomingCandle
                }
                incomingCandle.openTime > mutable[lastIdx].openTime -> {
                    mutable.add(incomingCandle)
                    while (mutable.size > maxCandles) {
                        mutable.removeAt(0)
                    }
                }
            }
            return mutable
        }
    }
}
