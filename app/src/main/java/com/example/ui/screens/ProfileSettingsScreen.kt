package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AdsterraAdConfig
import com.example.data.model.GlobalAppSettings
import com.example.data.model.SubscriptionPlan
import com.example.data.model.formatStorageSize
import com.example.ui.SafeVaultViewModel
import com.example.ui.components.AdPlacement
import com.example.ui.components.AdsterraBanner
import com.example.ui.components.TransferProgressBanner
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.MintLight
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeroEnd
import com.example.ui.theme.NavyHeroStart
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusPurple
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileSettingsScreen(
    viewModel: SafeVaultViewModel,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onOpenAdminPanel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentAdmin by viewModel.currentAdmin.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val availablePlans by viewModel.availablePlans.collectAsStateWithLifecycle()
    val myReferrals by viewModel.myReferrals.collectAsStateWithLifecycle()
    val stats by viewModel.storageStats.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val adConfig by viewModel.adConfig.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isPersonalLockerUnlocked.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePassDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showAdConfigDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showVaultLockerDialog by remember { mutableStateOf(false) }
    var showPlansDialog by remember { mutableStateOf(false) }
    var showRedeemReferralDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFD))
            .statusBarsPadding()
    ) {
        // Profile Header Card in Deep Navy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(NavyDeep, NavyHeroStart, NavyHeroEnd)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0x3300E5C9))
                            .border(2.dp, CyanAccent, CircleShape)
                    ) {
                        Text(
                            text = user?.avatarEmoji ?: "🛡️",
                            fontSize = 26.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = user?.fullName ?: "SafeVault Member",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MintLight,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = user?.phoneNumber ?: "+1 555 019 2834",
                                fontSize = 12.sp,
                                color = MintLight
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showEditProfileDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Strictly Visible ONLY to Authorized Active Admins
            if (currentAdmin?.isActive == true) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyDeep),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(CyanAccent, Color(0xFF38BDF8)))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAdminPanel() }
                            .testTag("open_admin_panel_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x2600E5C9))
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, null, tint = CyanAccent, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Admin Control Center",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Role: ${currentAdmin?.role?.displayName} • Dashboard, Users, Plans & Security",
                                        color = MintLight,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = CyanAccent)
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Data Recovery & Cloud Sync Hub (The Most Important Requirement)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFF93C5FD), Color(0xFFBFDBFE)))),
                    modifier = Modifier.fillMaxWidth().testTag("recovery_hub_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BluePrimary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Cloud Data Recovery Hub",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextNavyDark
                                    )
                                    Text(
                                        text = "Recover 100% data on any device or reinstall",
                                        fontSize = 11.sp,
                                        color = TextMutedGray
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.triggerCloudSync() },
                                enabled = !isSyncing && !transferProgress.isActive,
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp).testTag("sync_now_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", fontSize = 13.sp)
                            }

                            Button(
                                onClick = { viewModel.restoreAllCloudData() },
                                enabled = !isSyncing && !transferProgress.isActive,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp).testTag("restore_cloud_data_button")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore Data", color = CyanAccent, fontSize = 13.sp)
                            }
                        }

                        if (transferProgress.isActive) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TransferProgressBanner(progress = transferProgress)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Background Sync",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextNavyDark
                                )
                                Text(
                                    text = "Continuously backup all files, folders & settings",
                                    fontSize = 11.sp,
                                    color = TextMutedGray
                                )
                            }
                            Switch(
                                checked = user?.autoSyncEnabled ?: true,
                                onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Secure Private Vault Quick Access
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF8FF)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFFE9D5FF), Color(0xFFF3E8FF)))),
                    modifier = Modifier.fillMaxWidth().testTag("secure_vault_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVaultLockerDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(StatusPurple)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SafeVault Personal Locker",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextNavyDark
                                )
                                Text(
                                    text = if (isVaultUnlocked) "${vaultItems.size} encrypted items (Unlocked)" else "AES-256 Passwords, Cards, Notes (Locked)",
                                    fontSize = 11.sp,
                                    color = if (isVaultUnlocked) StatusGreen else TextMutedGray
                                )
                            }
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMutedGray)
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Subscription Plan & Quota Card
            item {
                val activePlanName = user?.planName ?: "Free Vault"
                val activePlanTier = user?.planTier ?: "FREE"
                val expiryStr = remember(user?.planExpiresAt) {
                    val exp = user?.planExpiresAt ?: 0L
                    if (exp <= 0L) "No expiry"
                    else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(exp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                        .testTag("user_plan_card")
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEFF6FF))
                                ) {
                                    Icon(Icons.Default.CardMembership, null, tint = BluePrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("$activePlanName ($activePlanTier)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextNavyDark)
                                    Text("Valid until: $expiryStr", fontSize = 11.sp, color = TextMutedGray)
                                }
                            }
                            OutlinedButton(
                                onClick = { showPlansDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("View Plans", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Refer & Earn Card (Unique Referral Link, Code & History)
            item {
                val refCode = user?.referralCode?.ifBlank { "SV" + viewModel.currentUserId.take(6).uppercase() } ?: ""
                val refLink = "https://safevault.app/invite/$refCode"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                        .testTag("user_referral_card")
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFECFDF5))
                            ) {
                                Icon(Icons.Default.Share, null, tint = StatusGreen, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Refer Friends & Earn Cloud Space", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                                Text(
                                    "+${appSettings.referralRewardPoints} pts & +${appSettings.referralRewardBonusMb} MB per verified invite",
                                    fontSize = 11.sp,
                                    color = TextMutedGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFD))
                                .border(1.dp, GrayBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Your Referral Code: $refCode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                                Text(refLink, fontSize = 10.sp, color = TextMutedGray)
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(refLink))
                                    viewModel.showFeedback("Referral link copied!")
                                },
                                modifier = Modifier.testTag("copy_referral_link_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Referral Link", tint = BluePrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Join ${appSettings.appName}")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Protect your photos, documents & secrets with ${appSettings.appName}! Use my referral code $refCode or link: $refLink"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Referral Link"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("share_referral_button")
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Link", fontSize = 12.sp)
                            }

                            if ((user?.referredByUid ?: "").isBlank() && appSettings.referralEnabled) {
                                OutlinedButton(
                                    onClick = { showRedeemReferralDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("redeem_referral_button")
                                ) {
                                    Text("Redeem Code", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Invites: ${user?.referralCount ?: 0} • Bonus Storage: ${formatStorageSize(user?.referralBonusBytes ?: 0L)} • Points: ${user?.referralRewardPoints ?: 0}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusGreen
                        )

                        if (myReferrals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = GrayBorder, thickness = 0.8.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Referral History", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                            myReferrals.take(5).forEach { r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(r.referredName.ifBlank { r.referredUid }, fontSize = 11.sp, color = TextMutedGray)
                                    Text("${r.status} (+${r.rewardPoints} pts)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BluePrimary)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Storage Details Card
            item {
                Text(
                    text = "Storage Information",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavyDark
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "SafeVault Cloud Quota", fontSize = 13.sp, color = TextMutedGray)
                            Text(text = "${stats.usedGbString} / ${formatStorageSize(stats.totalBytes)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Account ID: ${user?.id ?: viewModel.currentUserId}", fontSize = 10.sp, color = TextMutedGray)
                            Text(
                                text = "Copy ID",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary,
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(user?.id ?: viewModel.currentUserId))
                                    viewModel.showFeedback("Account ID copied!")
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { stats.usedPercentage },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = BluePrimary,
                            trackColor = Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Images ${formatStorageSize(stats.imageBytes)} • Videos ${formatStorageSize(stats.videoBytes)} • Audio ${formatStorageSize(stats.audioBytes)} • Docs ${formatStorageSize(stats.docBytes)} • Archives ${formatStorageSize(stats.archiveBytes)}",
                            fontSize = 11.sp,
                            color = TextMutedGray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "App Cache & Temp Files", fontSize = 12.sp, color = TextMutedGray)
                            Button(
                                onClick = { viewModel.clearLocalCache() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = TextNavyDark),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Cache", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Settings Menu Items
            item {
                Text(
                    text = "Account & Security",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavyDark
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                ) {
                    Column {
                        SettingItemRow(
                            icon = Icons.Default.Lock,
                            title = "Change Password",
                            subtitle = "Update your cloud account password",
                            onClick = { showChangePassDialog = true }
                        )
                        HorizontalDivider(color = GrayBorder, thickness = 0.8.dp)
                        SettingItemRow(
                            icon = Icons.Default.Security,
                            title = "Master Vault PIN",
                            subtitle = "Configure 4-digit PIN for sensitive records",
                            onClick = { showPinDialog = true }
                        )
                        HorizontalDivider(color = GrayBorder, thickness = 0.8.dp)
                        SettingItemRow(
                            icon = Icons.Default.AdsClick,
                            title = "Adsterra Advertisement Settings",
                            subtitle = "Configure placements, test mode & custom scripts",
                            onClick = { showAdConfigDialog = true }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Legal & Support
            item {
                Text(
                    text = "Support & Legal",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavyDark
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                ) {
                    Column {
                        SettingItemRow(
                            icon = Icons.Default.Policy,
                            title = "Privacy Policy",
                            subtitle = "Zero-knowledge encryption & data rights",
                            onClick = { showPrivacyDialog = true }
                        )
                        HorizontalDivider(color = GrayBorder, thickness = 0.8.dp)
                        SettingItemRow(
                            icon = Icons.Default.Description,
                            title = "Terms & Conditions",
                            subtitle = "Cloud storage service terms",
                            onClick = { showTermsDialog = true }
                        )
                        HorizontalDivider(color = GrayBorder, thickness = 0.8.dp)
                        SettingItemRow(
                            icon = Icons.AutoMirrored.Filled.Help,
                            title = "Contact Support",
                            subtitle = "24/7 SafeVault cloud recovery assistance",
                            onClick = { showSupportDialog = true }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Bottom Adsterra Banner Placement
            if (adConfig.isEnabled && adConfig.showOnBottomArea) {
                item {
                    AdsterraBanner(config = adConfig, placement = AdPlacement.BOTTOM_AREA)
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Danger Zone: Logout and Delete Account
            item {
                Column {
                    Button(
                        onClick = onSignOutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = TextNavyDark),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out (Cloud Data Stays Safe)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = StatusRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Account & Cloud Data", color = StatusRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Dialogs
    if (showEditProfileDialog) {
        EditProfileDialog(
            currentName = user?.fullName ?: "",
            currentEmail = user?.email ?: "",
            currentEmoji = user?.avatarEmoji ?: "🛡️",
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, email, emoji ->
                showEditProfileDialog = false
                viewModel.updateProfile(name, email, emoji)
            }
        )
    }

    if (showChangePassDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePassDialog = false },
            onSave = { oldPass, newPass ->
                viewModel.changePassword(oldPass, newPass) {
                    showChangePassDialog = false
                }
            }
        )
    }

    if (showPinDialog) {
        SetPinDialog(
            currentPin = user?.masterPin ?: "1234",
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                showPinDialog = false
                viewModel.setMasterPin(pin)
            }
        )
    }

    if (showAdConfigDialog) {
        AdConfigDialog(
            currentConfig = adConfig,
            onDismiss = { showAdConfigDialog = false },
            onSave = { newConfig ->
                showAdConfigDialog = false
                viewModel.updateAdConfig(newConfig)
            }
        )
    }

    if (showPrivacyDialog) {
        SimpleTextDialog(
            title = "Privacy Policy",
            content = appSettings.privacyPolicy,
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showTermsDialog) {
        SimpleTextDialog(
            title = "Terms & Conditions",
            content = appSettings.termsAndConditions,
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showSupportDialog) {
        ContactSupportDialog(
            appSettings = appSettings,
            onDismiss = { showSupportDialog = false }
        )
    }

    if (showPlansDialog) {
        AvailablePlansDialog(
            plans = availablePlans,
            currentPlanId = user?.planId ?: "free",
            supportEmail = appSettings.supportEmail,
            onDismiss = { showPlansDialog = false }
        )
    }

    if (showRedeemReferralDialog) {
        var codeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRedeemReferralDialog = false },
            title = { Text("Apply Referral Code", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter a friend's SafeVault referral code to link your invitation.",
                        fontSize = 12.sp,
                        color = TextMutedGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        label = { Text("Referral Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.applyReferralCode(codeInput) {
                        showRedeemReferralDialog = false
                    }
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showRedeemReferralDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete SafeVault Account?") },
            text = { Text("Warning: This will permanently purge your account, all cloud files, notes, and backup recovery keys. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccountClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVaultLockerDialog) {
        VaultLockerModal(
            viewModel = viewModel,
            onDismiss = { showVaultLockerDialog = false }
        )
    }
}

@Composable
private fun SettingItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextNavyDark)
                Text(text = subtitle, fontSize = 11.sp, color = TextMutedGray)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMutedGray, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun EditProfileDialog(
    currentName: String,
    currentEmail: String,
    currentEmoji: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }
    var selectedEmoji by remember { mutableStateOf(currentEmoji) }
    val emojis = listOf("🛡️", "💼", "🚀", "🌟", "🔒", "⚡")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select Avatar Icon", fontSize = 12.sp, color = TextMutedGray)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.forEach { emoji ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selectedEmoji == emoji) Color(0xFFE2E8F0) else Color.Transparent)
                                .clickable { selectedEmoji = emoji }
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, email, selectedEmoji) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (oldPass.isNotBlank() && newPass.isNotBlank()) onSave(oldPass, newPass) }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SetPinDialog(
    currentPin: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf(currentPin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Master Vault PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("4-digit PIN for encrypting and unlocking confidential passwords and cards in SafeVault.", fontSize = 12.sp, color = TextMutedGray)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("4-digit PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (pin.length == 4) onSave(pin) }) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdConfigDialog(
    currentConfig: AdsterraAdConfig,
    onDismiss: () -> Unit,
    onSave: (AdsterraAdConfig) -> Unit
) {
    var isEnabled by remember { mutableStateOf(currentConfig.isEnabled) }
    var isTestMode by remember { mutableStateOf(currentConfig.isTestMode) }
    var zoneId by remember { mutableStateOf(currentConfig.zoneId) }
    var script by remember { mutableStateOf(currentConfig.customBannerScript) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adsterra Ad Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Advertisements", fontSize = 13.sp)
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Test / Demo Ad Mode", fontSize = 13.sp)
                    Switch(checked = isTestMode, onCheckedChange = { isTestMode = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = zoneId,
                    onValueChange = { zoneId = it },
                    label = { Text("Adsterra Zone ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it },
                    label = { Text("Adsterra Script Code") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    currentConfig.copy(
                        isEnabled = isEnabled,
                        isTestMode = isTestMode,
                        zoneId = zoneId.trim(),
                        customBannerScript = script.trim()
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SimpleTextDialog(title: String, content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(content, fontSize = 13.sp, lineHeight = 18.sp, color = TextNavyDark) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Got It") }
        }
    )
}

@Composable
fun ContactSupportDialog(
    appSettings: GlobalAppSettings = GlobalAppSettings(),
    onDismiss: () -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contact ${appSettings.appName} Support", fontWeight = FontWeight.Bold) },
        text = {
            if (sent) {
                Text("Thank you! Your ticket #SV-${(1000..9999).random()} has been logged. Reach us anytime at ${appSettings.supportEmail}.", color = StatusGreen)
            } else {
                Column {
                    Text(
                        "Email: ${appSettings.supportEmail}\nPhone: ${appSettings.supportPhone}\nWhatsApp: ${appSettings.supportWhatsApp}",
                        fontSize = 12.sp,
                        color = BluePrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        placeholder = { Text("e.g. Restore cloud data") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Description") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!sent) {
                Button(onClick = { if (subject.isNotBlank()) sent = true }) {
                    Text("Submit Ticket")
                }
            } else {
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun AvailablePlansDialog(
    plans: List<SubscriptionPlan>,
    currentPlanId: String,
    supportEmail: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud Storage Plans", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plans.isEmpty()) {
                    Text("You are on the Free Vault tier (15 GB). Contact $supportEmail to request custom enterprise quotas.", fontSize = 12.sp, color = TextMutedGray)
                } else {
                    plans.forEach { plan ->
                        val isCurrent = plan.id == currentPlanId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Color(0xFFEFF6FF) else Color(0xFFF8FAFD))
                                .border(1.dp, if (isCurrent) BluePrimary else GrayBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextNavyDark)
                                Text(
                                    "${plan.currency} ${String.format("%.2f", plan.price)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BluePrimary
                                )
                            }
                            Text(
                                "${plan.storageQuotaGb} GB • ${if (plan.durationDays == 0) "Lifetime" else "${plan.durationDays} days"}${if (plan.adsRemoved) " • Ad-Free" else ""}",
                                fontSize = 11.sp,
                                color = TextMutedGray
                            )
                            if (plan.features.isNotEmpty()) {
                                Text(plan.features.joinToString(" • "), fontSize = 10.sp, color = StatusGreen)
                            }
                        }
                    }
                    Text(
                        "Contact $supportEmail to activate or upgrade your subscription.",
                        fontSize = 11.sp,
                        color = TextMutedGray
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun VaultLockerModal(
    viewModel: SafeVaultViewModel,
    onDismiss: () -> Unit
) {
    val isUnlocked by viewModel.isPersonalLockerUnlocked.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    var pinInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = StatusPurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SafeVault Locker", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (!isUnlocked) {
                Column {
                    Text("Enter your 4-digit Master PIN to decrypt your confidential passwords and credentials. (Default demo PIN: 1234)", fontSize = 12.sp, color = TextMutedGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("Enter PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("vault_pin_input")
                    )
                }
            } else {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Decrypted Items (${vaultItems.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
                        TextButton(onClick = { viewModel.setShowNewVaultDialog(true) }) {
                            Text("+ Add Item", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (vaultItems.isEmpty()) {
                        Text("No sensitive records yet. Tap '+ Add Item' to store passwords or cards.", fontSize = 12.sp, color = TextMutedGray)
                    } else {
                        vaultItems.forEach { item ->
                            val secret = viewModel.decryptVaultSecret(item)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = item.category.displayName, fontSize = 10.sp, color = BluePrimary)
                                    }
                                    Text(text = "ID: ${item.usernameOrId}", fontSize = 11.sp, color = TextMutedGray)
                                    Text(text = "Secret: $secret", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = StatusPurple)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isUnlocked) {
                Button(
                    onClick = { viewModel.unlockPersonalLocker(pinInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusPurple)
                ) {
                    Text("Unlock")
                }
            } else {
                Button(onClick = { viewModel.lockPersonalLocker() }) {
                    Text("Lock Vault")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
