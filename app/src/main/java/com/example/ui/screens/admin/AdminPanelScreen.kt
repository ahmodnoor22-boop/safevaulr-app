package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AdminTab
import com.example.ui.AdminViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintLight
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeroEnd
import com.example.ui.theme.NavyHeroStart
import com.example.ui.theme.NavySurface
import com.example.ui.theme.OffWhiteBg
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray

@Composable
fun AdminPanelScreen(
    adminViewModel: AdminViewModel,
    onExitAdminPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentAdmin by adminViewModel.currentAdmin.collectAsStateWithLifecycle()
    val selectedTab by adminViewModel.selectedTab.collectAsStateWithLifecycle()
    val isPinVerified by adminViewModel.isAdminPinVerified.collectAsStateWithLifecycle()
    val isBusy by adminViewModel.isBusy.collectAsStateWithLifecycle()
    val feedback by adminViewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            adminViewModel.clearFeedback()
        }
    }

    BackHandler {
        onExitAdminPanel()
    }

    val admin = currentAdmin
    if (admin == null || !admin.isActive) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NavyDeep)
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Access Denied",
                    tint = StatusRed,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Unauthorized Admin Access",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This panel is strictly restricted to verified SafeVault administrators.",
                    color = TextMutedGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onExitAdminPanel,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Return to SafeVault", color = NavyDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Secondary Admin PIN Verification Gate if configured
    if (admin.hasPinConfigured && !isPinVerified) {
        var pinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = NavyDeep,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(NavyDeep, NavyHeroStart, NavySurface)))
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(NavyCard)
                        .border(1.dp, Color(0x3300E5C9), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0x2200E5C9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Admin Security PIN",
                            tint = CyanAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Admin Security Verification",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${admin.fullName} (${admin.role.displayName})",
                        color = MintLight,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("Enter Admin PIN", color = TextMutedGray) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color(0xFF2A3D5D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_pin_gate_input")
                    )
                    if (pinError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = pinError!!, color = StatusRed, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (!adminViewModel.unlockAdminPin(pinInput)) {
                                pinError = "Invalid Admin Security PIN."
                            } else {
                                pinError = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_pin_unlock_button")
                    ) {
                        Text("Unlock Admin Console", color = NavyDeep, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onExitAdminPanel) {
                        Text("Back to User Vault", color = TextMutedGray)
                    }
                }
            }
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OffWhiteBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Admin Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(NavyDeep, NavyHeroStart, NavyHeroEnd)))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = onExitAdminPanel,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .testTag("admin_exit_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Admin Panel",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Admin Control Center",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x3300E5C9))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = admin.role.displayName,
                                        color = CyanAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Signed in as ${admin.fullName}",
                                color = MintLight,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (admin.hasPinConfigured) {
                        IconButton(
                            onClick = { adminViewModel.resetPinGate() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x18FFFFFF))
                                .testTag("admin_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Admin Panel",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (isBusy) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = CyanAccent,
                        trackColor = Color(0x33FFFFFF)
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 600.dp

                if (isWide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = NavyDeep,
                            contentColor = Color.White,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AdminTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = selectedTab == tab,
                                    onClick = { adminViewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = adminTabIcon(tab),
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, fontSize = 11.sp) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = NavyDeep,
                                        selectedTextColor = CyanAccent,
                                        indicatorColor = CyanAccent,
                                        unselectedIconColor = Color(0xFF94A3B8),
                                        unselectedTextColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.testTag("admin_tab_${tab.name}")
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            AdminTabContent(selectedTab = selectedTab, adminViewModel = adminViewModel)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Compact Phone Pill Tab Bar
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyHeroEnd)
                                .padding(vertical = 10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(AdminTab.entries) { tab ->
                                val isSelected = selectedTab == tab
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) CyanAccent else Color(0x22FFFFFF))
                                        .clickable { adminViewModel.selectTab(tab) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                        .testTag("admin_tab_${tab.name}")
                                ) {
                                    Icon(
                                        imageVector = adminTabIcon(tab),
                                        contentDescription = null,
                                        tint = if (isSelected) NavyDeep else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.label,
                                        color = if (isSelected) NavyDeep else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AdminTabContent(selectedTab = selectedTab, adminViewModel = adminViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTabContent(
    selectedTab: AdminTab,
    adminViewModel: AdminViewModel
) {
    when (selectedTab) {
        AdminTab.DASHBOARD -> AdminDashboardTab(adminViewModel = adminViewModel)
        AdminTab.USERS -> AdminUsersTab(adminViewModel = adminViewModel)
        AdminTab.PLANS -> AdminPlansTab(adminViewModel = adminViewModel)
        AdminTab.REFERRALS -> AdminReferralsTab(adminViewModel = adminViewModel)
        AdminTab.SECURITY -> AdminSecurityTab(adminViewModel = adminViewModel)
        AdminTab.SETTINGS -> AdminSettingsTab(adminViewModel = adminViewModel)
    }
}

private fun adminTabIcon(tab: AdminTab): ImageVector = when (tab) {
    AdminTab.DASHBOARD -> Icons.Default.Analytics
    AdminTab.USERS -> Icons.Default.Group
    AdminTab.PLANS -> Icons.Default.CardMembership
    AdminTab.REFERRALS -> Icons.Default.Share
    AdminTab.SECURITY -> Icons.Default.Security
    AdminTab.SETTINGS -> Icons.Default.Settings
}
