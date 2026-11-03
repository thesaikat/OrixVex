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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.StrategyProfile
import com.example.data.model.Timeframe
import com.example.data.model.UserQuotaStatus
import com.example.ui.theme.LocalOrixColors
import com.example.ui.theme.TabularMonoStyle
import com.google.firebase.auth.FirebaseUser
import java.util.Locale

@Composable
fun SettingsAccountScreen(
    currentUser: FirebaseUser?,
    isDarkTheme: Boolean,
    strategyProfile: StrategyProfile,
    selectedTimeframe: Timeframe,
    accountBalanceUsd: Double,
    riskPercentPerTrade: Double,
    userQuotaStatus: UserQuotaStatus = UserQuotaStatus(),
    onOpenQuotaModal: () -> Unit = {},
    onUpdateRiskDefaults: (Double, Double) -> Unit = { _, _ -> },
    onToggleTheme: (Boolean) -> Unit,
    onSelectStrategy: (StrategyProfile) -> Unit,
    onSelectDefaultTimeframe: (Timeframe) -> Unit,
    onSignOut: () -> Unit,
    onReturnToGoogleSignIn: () -> Unit
) {
    val colors = LocalOrixColors.current
    var balanceText by remember(accountBalanceUsd) { mutableStateOf(accountBalanceUsd.toInt().toString()) }
    var riskText by remember(riskPercentPerTrade) { mutableStateOf(riskPercentPerTrade.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                color = colors.primaryText,
                fontWeight = FontWeight.Bold
            )
        )

        // 1. User Gemini Quota Manager & Live Usage Meter Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.5.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .testTag("settings_quota_manager_card"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "USER GEMINI QUOTA ENGINE & RATE-LIMIT VAULT",
                        style = TabularMonoStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    )
                }
            }

            Text(
                text = "Active Mode: ${userQuotaStatus.mode.label}",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Burst Rate: ${userQuotaStatus.rpmUsed}/${userQuotaStatus.rpmLimit} RPM",
                    style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.bullish)
                )
                Text(
                    text = "Daily Quota: ${userQuotaStatus.rpdUsed}/${userQuotaStatus.rpdLimit} RPD",
                    style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.primaryText)
                )
            }

            LinearProgressIndicator(
                progress = { (userQuotaStatus.rpmUsed.toFloat() / userQuotaStatus.rpmLimit.toFloat()).coerceIn(0.05f, 1f) },
                color = colors.accent,
                trackColor = colors.surfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Button(
                onClick = onOpenQuotaModal,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_my_gemini_quota_button")
            ) {
                Text(
                    text = "Configure My Gemini Quota / Personal Key Vault",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // 2. Google Profile Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_account_section),
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedText
                )
            )

            if (currentUser != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentUser.photoUrl != null) {
                        AsyncImage(
                            model = currentUser.photoUrl,
                            contentDescription = stringResource(R.string.cd_user_avatar),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, colors.accent, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (currentUser.displayName?.firstOrNull() ?: 'O').uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.displayName ?: "OrixVex Quantitative Trader",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.primaryText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = currentUser.email ?: "Authenticated via Google Identity",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.mutedText
                            )
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.settings_sign_out),
                        tint = colors.bearish,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_sign_out),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.bearish,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_preview_mode_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = stringResource(R.string.settings_preview_mode_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.mutedText)
                )
                Button(
                    onClick = onReturnToGoogleSignIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_google_sign_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = stringResource(R.string.cd_google_sign_in),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auth_continue_with_google),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // 3. Dual-Theme Engine Card (Obsidian Dark vs Alabaster White)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_section),
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedText
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeOptionCard(
                    title = stringResource(R.string.settings_dark_mode_title),
                    subtitle = "#090D16 Institutional",
                    icon = Icons.Default.DarkMode,
                    isSelected = isDarkTheme,
                    onClick = { onToggleTheme(true) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_dark_button")
                )

                ThemeOptionCard(
                    title = stringResource(R.string.settings_white_mode_title),
                    subtitle = "#F8FAFC Daylight",
                    icon = Icons.Default.LightMode,
                    isSelected = !isDarkTheme,
                    onClick = { onToggleTheme(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_white_button")
                )
            }
        }

        // 4. Position Risk Calculator Defaults
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "POSITION RISK CALCULATOR DEFAULTS",
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedText
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = {
                        balanceText = it
                        val b = it.toDoubleOrNull()
                        val r = riskText.toDoubleOrNull()
                        if (b != null && r != null) onUpdateRiskDefaults(b, r)
                    },
                    label = { Text("Account Balance ($)", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TabularMonoStyle.copy(fontSize = 13.sp, color = colors.primaryText),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = riskText,
                    onValueChange = {
                        riskText = it
                        val b = balanceText.toDoubleOrNull()
                        val r = it.toDoubleOrNull()
                        if (b != null && r != null) onUpdateRiskDefaults(b, r)
                    },
                    label = { Text("Risk per Trade (%)", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TabularMonoStyle.copy(fontSize = 13.sp, color = colors.primaryText),
                    modifier = Modifier.weight(1f)
                )
            }
            val riskUsd = (balanceText.toDoubleOrNull() ?: accountBalanceUsd) * ((riskText.toDoubleOrNull() ?: riskPercentPerTrade) / 100.0)
            Text(
                text = String.format(Locale.US, "1.0R Dollar Risk per Trade: $%,.2f USD", riskUsd),
                style = TabularMonoStyle.copy(fontSize = 11.sp, color = colors.accent)
            )
        }

        // 5. AI Strategy Profile & Default Timeframe Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_strategy_section),
                style = TabularMonoStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedText
                )
            )

            StrategyProfile.entries.forEach { profile ->
                val isSelected = profile == strategyProfile
                Surface(
                    color = if (isSelected) colors.accent.copy(alpha = 0.14f) else colors.surfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isSelected) colors.accent else colors.border,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            onSelectStrategy(profile)
                        }
                        .testTag("strategy_option_${profile.label}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = profile.label,
                            tint = if (isSelected) colors.accent else colors.mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.label,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = colors.primaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = profile.description,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.mutedText,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Default Chart & Quant Timeframe",
                style = MaterialTheme.typography.labelLarge.copy(color = colors.primaryText)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    val isSelected = tf == selectedTimeframe
                    Surface(
                        color = if (isSelected) colors.accent else colors.surfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectDefaultTimeframe(tf) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = tf.label,
                                style = TabularMonoStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else colors.mutedText
                                )
                            )
                        }
                    }
                }
            }
        }

        // 6. Legal & Financial Risk Disclaimer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_disclaimer_header),
                    style = TabularMonoStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.mutedText
                    )
                )
            }
            Text(
                text = stringResource(R.string.settings_disclaimer_body),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.mutedText,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOrixColors.current
    Surface(
        color = if (isSelected) colors.accent.copy(alpha = 0.16f) else colors.surfaceElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.accent else colors.border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) colors.accent else colors.mutedText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            Text(
                text = subtitle,
                style = TabularMonoStyle.copy(
                    fontSize = 10.sp,
                    color = colors.mutedText
                )
            )
        }
    }
}
