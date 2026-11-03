package com.example.data.remote

import android.util.Log
import com.example.data.model.CryptoPair
import com.example.data.model.LiveTicker
import com.example.data.model.OhlcvCandle
import com.example.data.model.OrderBookDepthTelemetry
import com.example.data.model.OrderBookWall
import com.example.data.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class KlineStreamEvent(
    val symbol: String,
    val interval: String,
    val candle: OhlcvCandle,
    val isCandleClosed: Boolean,
    val eventTimeMs: Long
)

data class WebSocketStatus(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val endpointHost: String = "wss://data-stream.binance.vision:9443",
    val subscribedSymbol: String = CryptoPair.BTC.symbol,
    val subscribedInterval: String = Timeframe.M15.binanceInterval,
    val lastMessageTimestampMs: Long = 0L,
    val reconnectAttempt: Int = 0
)

class BinanceMarketService(
    private val scope: CoroutineScope
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val restEndpoints = listOf(
        "https://data-api.binance.vision",
        "https://api.binance.com",
        "https://api.binance.us"
    )

    private val wsEndpoints = listOf(
        "wss://data-stream.binance.vision:9443",
        "wss://stream.binance.com:9443",
        "wss://stream.binance.us:9443"
    )

    private val _tickers = MutableStateFlow<Map<CryptoPair, LiveTicker>>(emptyMap())
    val tickers: StateFlow<Map<CryptoPair, LiveTicker>> = _tickers.asStateFlow()

    private val _tickerEvents = MutableSharedFlow<LiveTicker>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val tickerEvents: SharedFlow<LiveTicker> = _tickerEvents.asSharedFlow()

    private val _klineEvents = MutableSharedFlow<KlineStreamEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val klineEvents: SharedFlow<KlineStreamEvent> = _klineEvents.asSharedFlow()

    private val _latestKline = MutableStateFlow<Pair<String, OhlcvCandle>?>(null)
    val latestKline: StateFlow<Pair<String, OhlcvCandle>?> = _latestKline.asStateFlow()

    private val _wsStatus = MutableStateFlow(WebSocketStatus())
    val wsStatus: StateFlow<WebSocketStatus> = _wsStatus.asStateFlow()

    private val _wsConnected = MutableStateFlow(false)
    val wsConnected: StateFlow<Boolean> = _wsConnected.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var wsReconnectJob: Job? = null
    private var currentSubscribedSymbol: String = CryptoPair.BTC.symbol
    private var currentSubscribedInterval: String = Timeframe.M15.binanceInterval
    private var wsEndpointIndex = 0
    private var reconnectCount = 0

    fun initializeStreams(selectedPair: CryptoPair, selectedTimeframe: Timeframe) {
        currentSubscribedSymbol = selectedPair.symbol
        currentSubscribedInterval = selectedTimeframe.binanceInterval
        scope.launch {
            fetchInitial24hTickers()
        }
        connectWebSocket()
    }

    fun updateSubscription(selectedPair: CryptoPair, selectedTimeframe: Timeframe) {
        val changed = currentSubscribedSymbol != selectedPair.symbol ||
            currentSubscribedInterval != selectedTimeframe.binanceInterval
        currentSubscribedSymbol = selectedPair.symbol
        currentSubscribedInterval = selectedTimeframe.binanceInterval
        if (changed) {
            reconnectCount = 0
            connectWebSocket()
        }
    }

    fun forceReconnect() {
        reconnectCount = 0
        wsEndpointIndex = (wsEndpointIndex + 1) % wsEndpoints.size
        scope.launch {
            fetchInitial24hTickers()
        }
        connectWebSocket()
    }

    suspend fun fetchInitial24hTickers() = withContext(Dispatchers.IO) {
        val symbolsJson = CryptoPair.entries.joinToString(
            prefix = "[\"",
            separator = "\",\"",
            postfix = "\"]"
        ) { it.symbol }

        for (baseUrl in restEndpoints) {
            try {
                val url = "$baseUrl/api/v3/ticker/24hr?symbols=$symbolsJson"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val arr = JSONArray(body)
                        val updatedMap = _tickers.value.toMutableMap()
                        val now = System.currentTimeMillis()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val sym = obj.optString("symbol")
                            val pair = CryptoPair.entries.find { it.symbol == sym } ?: continue
                            val price = obj.optString("lastPrice").toDoubleOrNull() ?: continue
                            val changePct = obj.optString("priceChangePercent").toDoubleOrNull() ?: 0.0
                            val high = obj.optString("highPrice").toDoubleOrNull() ?: price
                            val low = obj.optString("lowPrice").toDoubleOrNull() ?: price
                            val quoteVol = obj.optString("quoteVolume").toDoubleOrNull() ?: 0.0
                            val ticker = LiveTicker(
                                pair = pair,
                                price = price,
                                priceChangePercent24h = changePct,
                                high24h = high,
                                low24h = low,
                                volumeUsd24h = quoteVol,
                                lastTickDirection = 0,
                                lastUpdatedMs = now
                            )
                            updatedMap[pair] = ticker
                            _tickerEvents.tryEmit(ticker)
                        }
                        if (updatedMap.isNotEmpty()) {
                            _tickers.value = updatedMap
                            return@withContext
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ticker fetch failed on $baseUrl: ${e.message}")
            }
        }
    }

    suspend fun fetchHistoricalKlines(
        pair: CryptoPair,
        timeframe: Timeframe,
        limit: Int = 200
    ): Result<List<OhlcvCandle>> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (baseUrl in restEndpoints) {
            try {
                val url = "$baseUrl/api/v3/klines?symbol=${pair.symbol}&interval=${timeframe.binanceInterval}&limit=$limit"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val jsonArray = JSONArray(body)
                            val candles = ArrayList<OhlcvCandle>(jsonArray.length())
                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONArray(i)
                                val vol = item.getString(5).toDouble()
                                val quoteVol = item.getString(7).toDouble()
                                val takerBase = if (item.length() > 9) item.optString(9).toDoubleOrNull() ?: (vol * 0.5) else vol * 0.5
                                val takerQuote = if (item.length() > 10) item.optString(10).toDoubleOrNull() ?: (quoteVol * 0.5) else quoteVol * 0.5
                                candles.add(
                                    OhlcvCandle(
                                        openTime = item.getLong(0),
                                        open = item.getString(1).toDouble(),
                                        high = item.getString(2).toDouble(),
                                        low = item.getString(3).toDouble(),
                                        close = item.getString(4).toDouble(),
                                        volume = vol,
                                        closeTime = item.getLong(6),
                                        quoteVolume = quoteVol,
                                        takerBuyBaseVolume = takerBase,
                                        takerBuyQuoteVolume = takerQuote,
                                        isClosed = i < jsonArray.length() - 1
                                    )
                                )
                            }
                            if (candles.isNotEmpty()) {
                                updateTickerFromCandlesIfMissing(pair, candles)
                                return@withContext Result.success(candles)
                            }
                        }
                    } else {
                        lastError = IllegalStateException("HTTP ${response.code} from $baseUrl")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Klines fail-safe switching from $baseUrl: ${e.message}")
                lastError = e
            }
        }
        Result.failure(lastError ?: IllegalStateException("Unable to reach Binance market data endpoints"))
    }

    suspend fun fetchOrderBookDepth(
        pair: CryptoPair,
        limit: Int = 20
    ): Result<OrderBookDepthTelemetry> = withContext(Dispatchers.IO) {
        var lastErr: Exception? = null
        for (baseUrl in restEndpoints) {
            try {
                val url = "$baseUrl/api/v3/depth?symbol=${pair.symbol}&limit=$limit"
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JSONObject(body)
                            val bidsArr = root.optJSONArray("bids") ?: JSONArray()
                            val asksArr = root.optJSONArray("asks") ?: JSONArray()

                            var totalBidUsd = 0.0
                            var totalAskUsd = 0.0
                            var largestBid: OrderBookWall? = null
                            var largestAsk: OrderBookWall? = null

                            for (i in 0 until bidsArr.length()) {
                                val row = bidsArr.getJSONArray(i)
                                val price = row.optString(0).toDoubleOrNull() ?: continue
                                val qty = row.optString(1).toDoubleOrNull() ?: continue
                                val notional = price * qty
                                totalBidUsd += notional
                                if (largestBid == null || notional > largestBid.notionalUsd) {
                                    largestBid = OrderBookWall(price, qty, notional, isBidSupport = true)
                                }
                            }
                            for (i in 0 until asksArr.length()) {
                                val row = asksArr.getJSONArray(i)
                                val price = row.optString(0).toDoubleOrNull() ?: continue
                                val qty = row.optString(1).toDoubleOrNull() ?: continue
                                val notional = price * qty
                                totalAskUsd += notional
                                if (largestAsk == null || notional > largestAsk.notionalUsd) {
                                    largestAsk = OrderBookWall(price, qty, notional, isBidSupport = false)
                                }
                            }

                            val totalBook = (totalBidUsd + totalAskUsd).coerceAtLeast(1.0)
                            val bidPct = (totalBidUsd / totalBook) * 100.0
                            val askPct = (totalAskUsd / totalBook) * 100.0
                            val imbalancePct = ((totalBidUsd - totalAskUsd) / totalBook) * 100.0

                            return@withContext Result.success(
                                OrderBookDepthTelemetry(
                                    symbol = pair.symbol,
                                    totalBidUsd = totalBidUsd,
                                    totalAskUsd = totalAskUsd,
                                    bidRatioPct = bidPct,
                                    askRatioPct = askPct,
                                    bidAskImbalancePct = imbalancePct,
                                    largestBidWall = largestBid,
                                    largestAskWall = largestAsk
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        Result.failure(lastErr ?: IllegalStateException("Order book depth unavailable"))
    }

    private fun updateTickerFromCandlesIfMissing(pair: CryptoPair, candles: List<OhlcvCandle>) {
        val last = candles.lastOrNull() ?: return
        val first = candles.firstOrNull() ?: last
        _tickers.update { current ->
            if (current.containsKey(pair)) {
                current
            } else {
                val pct = if (first.open > 0) ((last.close - first.open) / first.open) * 100.0 else 0.0
                val fallbackTicker = LiveTicker(
                    pair = pair,
                    price = last.close,
                    priceChangePercent24h = pct,
                    high24h = candles.maxOf { it.high },
                    low24h = candles.minOf { it.low },
                    volumeUsd24h = candles.sumOf { it.quoteVolume },
                    lastTickDirection = 0
                )
                _tickerEvents.tryEmit(fallbackTicker)
                current + (pair to fallbackTicker)
            }
        }
    }

    private fun connectWebSocket() {
        wsReconnectJob?.cancel()
        activeWebSocket?.cancel()

        val tickerStreams = CryptoPair.entries.joinToString("/") { "${it.symbol.lowercase()}@ticker" }
        val klineStream = "${currentSubscribedSymbol.lowercase()}@kline_${currentSubscribedInterval}"
        val baseWs = wsEndpoints[wsEndpointIndex % wsEndpoints.size]
        val wsUrl = "$baseWs/stream?streams=$tickerStreams/$klineStream"

        _wsStatus.update {
            it.copy(
                isConnected = false,
                isConnecting = true,
                endpointHost = baseWs,
                subscribedSymbol = currentSubscribedSymbol,
                subscribedInterval = currentSubscribedInterval,
                reconnectAttempt = reconnectCount
            )
        }

        val request = Request.Builder().url(wsUrl).build()
        activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectCount = 0
                _wsConnected.value = true
                _wsStatus.update {
                    it.copy(
                        isConnected = true,
                        isConnecting = false,
                        endpointHost = baseWs,
                        subscribedSymbol = currentSubscribedSymbol,
                        subscribedInterval = currentSubscribedInterval,
                        lastMessageTimestampMs = System.currentTimeMillis(),
                        reconnectAttempt = 0
                    )
                }
                Log.d(TAG, "Connected to Binance WebSocket: $baseWs ($currentSubscribedSymbol @ $currentSubscribedInterval)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val now = System.currentTimeMillis()
                    _wsStatus.update { it.copy(lastMessageTimestampMs = now) }
                    val root = JSONObject(text)
                    val data = root.optJSONObject("data") ?: root
                    when (data.optString("e")) {
                        "24hrTicker" -> handleTickerEvent(data, now)
                        "kline" -> handleKlineEvent(data, now)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "WS parse warning: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _wsConnected.value = false
                _wsStatus.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false
                    )
                }
                Log.w(TAG, "WebSocket failure on $baseWs: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _wsConnected.value = false
                _wsStatus.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false
                    )
                }
            }
        })
    }

    private fun handleTickerEvent(data: JSONObject, timestampMs: Long) {
        val symbol = data.optString("s")
        val pair = CryptoPair.entries.find { it.symbol == symbol } ?: return
        val newPrice = data.optString("c").toDoubleOrNull() ?: return
        val changePct = data.optString("P").toDoubleOrNull() ?: 0.0
        val high = data.optString("h").toDoubleOrNull() ?: newPrice
        val low = data.optString("l").toDoubleOrNull() ?: newPrice
        val quoteVolume = data.optString("q").toDoubleOrNull() ?: 0.0

        var emittedTicker: LiveTicker? = null
        _tickers.update { map ->
            val prevPrice = map[pair]?.price
            val direction = when {
                prevPrice == null -> 0
                newPrice > prevPrice -> 1
                newPrice < prevPrice -> -1
                else -> map[pair]?.lastTickDirection ?: 0
            }
            val updated = LiveTicker(
                pair = pair,
                price = newPrice,
                priceChangePercent24h = changePct,
                high24h = high,
                low24h = low,
                volumeUsd24h = quoteVolume,
                lastTickDirection = direction,
                lastUpdatedMs = timestampMs
            )
            emittedTicker = updated
            map + (pair to updated)
        }
        emittedTicker?.let { _tickerEvents.tryEmit(it) }
    }

    private fun handleKlineEvent(data: JSONObject, timestampMs: Long) {
        val symbol = data.optString("s")
        val k = data.optJSONObject("k") ?: return
        val interval = k.optString("i", currentSubscribedInterval)
        val isClosed = k.optBoolean("x", false)
        val vol = k.optString("v").toDoubleOrNull() ?: 0.0
        val quoteVol = k.optString("q").toDoubleOrNull() ?: 0.0
        val takerBase = k.optString("V").toDoubleOrNull() ?: (vol * 0.5)
        val takerQuote = k.optString("Q").toDoubleOrNull() ?: (quoteVol * 0.5)
        val candle = OhlcvCandle(
            openTime = k.optLong("t"),
            open = k.optString("o").toDoubleOrNull() ?: return,
            high = k.optString("h").toDoubleOrNull() ?: return,
            low = k.optString("l").toDoubleOrNull() ?: return,
            close = k.optString("c").toDoubleOrNull() ?: return,
            volume = vol,
            closeTime = k.optLong("T"),
            quoteVolume = quoteVol,
            takerBuyBaseVolume = takerBase,
            takerBuyQuoteVolume = takerQuote,
            isClosed = isClosed
        )
        _latestKline.value = symbol to candle
        _klineEvents.tryEmit(
            KlineStreamEvent(
                symbol = symbol,
                interval = interval,
                candle = candle,
                isCandleClosed = isClosed,
                eventTimeMs = timestampMs
            )
        )
    }

    private fun scheduleReconnect() {
        wsReconnectJob?.cancel()
        wsReconnectJob = scope.launch {
            reconnectCount += 1
            val backoffMs = (2500L * reconnectCount.coerceAtMost(4))
            delay(backoffMs)
            if (isActive) {
                wsEndpointIndex = (wsEndpointIndex + 1) % wsEndpoints.size
                connectWebSocket()
            }
        }
    }

    fun close() {
        wsReconnectJob?.cancel()
        activeWebSocket?.cancel()
    }

    private companion object {
        const val TAG = "BinanceMarketService"
    }
}
