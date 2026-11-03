package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.data.local.LocalAppPreferences
import com.example.data.local.ThemePreferencesRepository
import com.example.data.model.UserQuotaStatus
import com.example.data.repository.SignalVaultRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.MyGeminiQuotaDialog
import com.example.ui.screens.ExecutiveDashboardScreen
import com.example.ui.screens.HistoryAndJournalScreen
import com.example.ui.screens.MarketScannerScreen
import com.example.ui.screens.SettingsAccountScreen
import com.example.ui.screens.TerminalHomeScreen
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.OrixVexTheme
import com.example.ui.theme.TabularMonoStyle
import com.example.ui.viewmodel.BinanceStreamViewModel
import com.example.ui.viewmodel.OrixTerminalViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class OrixTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    TERMINAL("terminal", R.string.nav_terminal, Icons.Default.CandlestickChart),
    SCANNER("scanner", R.string.nav_scanner, Icons.Default.GridView),
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Default.Analytics),
    JOURNAL("journal", R.string.nav_journal, Icons.Default.Book),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val appContext = applicationContext
            val themePrefsRepo = remember { ThemePreferencesRepository(appContext) }
            val prefs by themePrefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = LocalAppPreferences()
            )

            OrixVexTheme(darkTheme = prefs.isDarkTheme) {
                OrixVexRootApp(themePrefsRepository = themePrefsRepo)
            }
        }
    }
}

@Composable
fun OrixVexRootApp(
    themePrefsRepository: ThemePreferencesRepository
) {
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    var isPreviewMode by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isPreviewMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null && !isPreviewMode) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
            onEnterPreviewMode = {
                isPreviewMode = true
            }
        )
    } else {
        OrixVexAuthenticatedShell(
            currentUser = currentUser,
            themePrefsRepository = themePrefsRepository,
            onExitPreviewToSignIn = {
                isPreviewMode = false
            }
        )
    }
}

@Composable
fun OrixVexAuthenticatedShell(
    currentUser: FirebaseUser?,
    themePrefsRepository: ThemePreferencesRepository,
    onExitPreviewToSignIn: () -> Unit
) {
    val binanceStreamViewModel: BinanceStreamViewModel = viewModel()

    val userId = currentUser?.uid
    val userEmail = currentUser?.email ?: "institutional@orixvex.ai"
    val viewModel: OrixTerminalViewModel = viewModel(
        key = userId ?: "preview_session",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val vaultRepo = if (userId != null) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    SignalVaultRepository(db)
                } else {
                    null
                }
                OrixTerminalViewModel(
                    binanceStreamViewModel = binanceStreamViewModel,
                    themePrefsRepository = themePrefsRepository,
                    signalVaultRepository = vaultRepo,
                    currentUserId = userId,
                    currentUserEmail = userEmail
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tickers by binanceStreamViewModel.tickers.collectAsStateWithLifecycle()
    val activePairTicker by binanceStreamViewModel.activePairTicker.collectAsStateWithLifecycle()
    val wsConnected by binanceStreamViewModel.wsConnected.collectAsStateWithLifecycle()
    val wsStatus by binanceStreamViewModel.wsStatus.collectAsStateWithLifecycle()

    val colors = LocalOrixColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by rememberSaveable { mutableStateOf(OrixTab.TERMINAL) }

    if (currentTab != OrixTab.TERMINAL) {
        BackHandler {
            currentTab = OrixTab.TERMINAL
        }
    } else if (uiState.isChartFullscreen) {
        BackHandler {
            viewModel.toggleChartFullscreen()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    if (uiState.showQuotaModal) {
        MyGeminiQuotaDialog(
            quotaStatus = uiState.userQuotaStatus,
            initialPersonalKey = "",
            initialOAuthToken = "",
            onDismiss = { viewModel.setShowQuotaModal(false) },
            onSaveConfiguration = { mode, personalKey, oauthToken, deepWeb ->
                viewModel.saveUserQuotaConfiguration(
                    mode = mode,
                    personalApiKey = personalKey,
                    oauthBearerToken = oauthToken,
                    deepWebSearchGrounding = deepWeb
                )
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            OrixTopHeaderBar(
                currentUser = currentUser,
                isDarkTheme = uiState.isDarkTheme,
                strategyLabel = uiState.strategyProfile.label,
                quotaStatus = uiState.userQuotaStatus,
                onOpenQuotaModal = { viewModel.setShowQuotaModal(true) },
                onToggleTheme = { viewModel.toggleTheme(!uiState.isDarkTheme) },
                onAvatarClick = { currentTab = OrixTab.SETTINGS }
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = colors.border)
                NavigationBar(
                    containerColor = colors.surfaceCard,
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    OrixTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.accent,
                                indicatorColor = colors.accent.copy(alpha = 0.15f),
                                unselectedIconColor = colors.mutedText,
                                unselectedTextColor = colors.mutedText
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                OrixTab.TERMINAL -> {
                    TerminalHomeScreen(
                        uiState = uiState,
                        tickers = tickers,
                        activePairTicker = activePairTicker,
                        wsConnected = wsConnected,
                        wsStatus = wsStatus,
                        onSelectPair = binanceStreamViewModel::selectPair,
                        onSelectTimeframe = binanceStreamViewModel::selectTimeframe,
                        onReconnectStream = binanceStreamViewModel::reconnectStreams,
                        onToggleFullscreen = viewModel::toggleChartFullscreen,
                        onGenerateSignal = { viewModel.generateAiSignal(force = false) },
                        onGenerateSignalFromJson = viewModel::generateAiSignalFromJson,
                        onToggleAutoRefresh = viewModel::toggleAutoRefreshAiSignal,
                        onBookmarkSignal = viewModel::bookmarkActiveSignal,
                        onExecuteEarlyExit = viewModel::triggerManualEarlyExit,
                        onImportTradeToJournal = viewModel::importActiveOrClosedTradeToJournal,
                        onRefreshNewsAndFundamentals = viewModel::refreshNewsAndFundamentals,
                        onCopyPriceToast = { copiedLabel ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Copied $copiedLabel")
                            }
                        }
                    )
                }

                OrixTab.SCANNER -> {
                    MarketScannerScreen(
                        scannerRows = uiState.scannerRows,
                        tickers = tickers,
                        fundamentals = uiState.fundamentals,
                        isLoading = uiState.isScannerLoading,
                        onRefresh = binanceStreamViewModel::refreshMarketScanner,
                        onRefreshNews = viewModel::refreshNewsAndFundamentals,
                        onSelectPairForTerminal = { pair ->
                            binanceStreamViewModel.selectPair(pair)
                            currentTab = OrixTab.TERMINAL
                        }
                    )
                }

                OrixTab.DASHBOARD -> {
                    ExecutiveDashboardScreen(
                        summary = uiState.dashboardSummary
                    )
                }

                OrixTab.JOURNAL -> {
                    HistoryAndJournalScreen(
                        activeManagedTrade = uiState.activeManagedTrade,
                        closedManagedTrades = uiState.closedManagedTrades,
                        savedSignals = uiState.savedSignals,
                        journalEntries = uiState.journalEntries,
                        aiCoachAudit = uiState.aiCoachAudit,
                        isRunningCoachAudit = uiState.isRunningCoachAudit,
                        onImportTradeToJournal = viewModel::importActiveOrClosedTradeToJournal,
                        onAddManualJournalEntry = viewModel::addManualJournalEntry,
                        onDeleteJournalEntry = viewModel::deleteJournalEntry,
                        onDeleteSavedSignal = viewModel::deleteSavedSignal,
                        onRunAiCoachAudit = viewModel::runAiCoachPortfolioAudit
                    )
                }

                OrixTab.SETTINGS -> {
                    SettingsAccountScreen(
                        currentUser = currentUser,
                        isDarkTheme = uiState.isDarkTheme,
                        strategyProfile = uiState.strategyProfile,
                        selectedTimeframe = uiState.selectedTimeframe,
                        accountBalanceUsd = uiState.accountBalanceUsd,
                        riskPercentPerTrade = uiState.riskPercentPerTrade,
                        userQuotaStatus = uiState.userQuotaStatus,
                        onOpenQuotaModal = { viewModel.setShowQuotaModal(true) },
                        onUpdateRiskDefaults = viewModel::updateRiskCalculatorInputs,
                        onToggleTheme = viewModel::toggleTheme,
                        onSelectStrategy = { profile ->
                            viewModel.updateStrategyAndPreferences(
                                strategyProfile = profile,
                                defaultTimeframe = uiState.selectedTimeframe,
                                accountBalanceUsd = uiState.accountBalanceUsd,
                                riskPercentPerTrade = uiState.riskPercentPerTrade
                            )
                        },
                        onSelectDefaultTimeframe = { tf ->
                            viewModel.selectTimeframe(tf)
                            viewModel.updateStrategyAndPreferences(
                                strategyProfile = uiState.strategyProfile,
                                defaultTimeframe = tf,
                                accountBalanceUsd = uiState.accountBalanceUsd,
                                riskPercentPerTrade = uiState.riskPercentPerTrade
                            )
                        },
                        onSignOut = {
                            signOutUser(
                                context = context,
                                onSignOutComplete = onExitPreviewToSignIn,
                                scope = scope
                            )
                        },
                        onReturnToGoogleSignIn = onExitPreviewToSignIn
                    )
                }
            }
        }
    }
}

@Composable
private fun OrixTopHeaderBar(
    currentUser: FirebaseUser?,
    isDarkTheme: Boolean,
    strategyLabel: String,
    quotaStatus: UserQuotaStatus,
    onOpenQuotaModal: () -> Unit,
    onToggleTheme: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val colors = LocalOrixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent.copy(alpha = 0.18f))
                        .border(1.dp, colors.accent, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OV",
                        style = TabularMonoStyle.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.primaryText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "$strategyLabel Quant Profile",
                        style = TabularMonoStyle.copy(
                            fontSize = 10.sp,
                            color = colors.mutedText
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Compact My Gemini Quota Button
                Surface(
                    color = colors.surfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenQuotaModal)
                        .testTag("header_quota_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "My Gemini Quota",
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${quotaStatus.rpmUsed}/${quotaStatus.rpmLimit} RPM",
                            style = TabularMonoStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        )
                    }
                }

                // Instant Dual-Theme Toggle Button
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .testTag("header_theme_toggle")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = stringResource(R.string.cd_toggle_theme),
                        tint = if (isDarkTheme) colors.neutral else colors.accent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Authenticated User Profile Pill / Avatar
                Surface(
                    color = colors.surfaceElevated,
                    shape = CircleShape,
                    modifier = Modifier
                        .border(1.dp, colors.border, CircleShape)
                        .clickable(onClick = onAvatarClick)
                        .testTag("header_profile_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        if (currentUser?.photoUrl != null) {
                            AsyncImage(
                                model = currentUser.photoUrl,
                                contentDescription = stringResource(R.string.cd_user_avatar),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (currentUser?.displayName?.firstOrNull() ?: 'D').uppercase(),
                                    style = TabularMonoStyle.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = currentUser?.displayName?.split(" ")?.firstOrNull() ?: "Demo",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 11.sp,
                                color = colors.primaryText
                            )
                        )
                    }
                }
            }
        }

        // Compact Live Quota & Rate-Limit Strip in Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated.copy(alpha = 0.7f))
                .clickable(onClick = onOpenQuotaModal)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = quotaStatus.headerBadgeText,
                style = TabularMonoStyle.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.mutedText
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Manage Quota →",
                style = TabularMonoStyle.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            )
        }
        HorizontalDivider(color = colors.border)
    }
}
