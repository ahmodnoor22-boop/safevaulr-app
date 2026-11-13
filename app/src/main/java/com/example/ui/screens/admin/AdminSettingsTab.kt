package com.example.ui.screens.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppNotification
import com.example.ui.AdminViewModel
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@Composable
fun AdminSettingsTab(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val liveSettings by adminViewModel.appSettings.collectAsStateWithLifecycle()
    val notifications by adminViewModel.notifications.collectAsStateWithLifecycle()

    var appName by remember { mutableStateOf(liveSettings.appName) }
    var appTagline by remember { mutableStateOf(liveSettings.appTagline) }
    var appLogoStyle by remember { mutableStateOf(liveSettings.appLogoStyle) }
    var maintenanceMode by remember { mutableStateOf(liveSettings.maintenanceMode) }
    var maintenanceMessage by remember { mutableStateOf(liveSettings.maintenanceMessage) }
    var announcementEnabled by remember { mutableStateOf(liveSettings.announcementEnabled) }
    var announcementTitle by remember { mutableStateOf(liveSettings.announcementTitle) }
    var announcementMessage by remember { mutableStateOf(liveSettings.announcementMessage) }
    var referralEnabled by remember { mutableStateOf(liveSettings.referralEnabled) }
    var referralPointsText by remember { mutableStateOf(liveSettings.referralRewardPoints.toString()) }
    var referralBonusMbText by remember { mutableStateOf(liveSettings.referralRewardBonusMb.toString()) }
    var supportEmail by remember { mutableStateOf(liveSettings.supportEmail) }
    var supportPhone by remember { mutableStateOf(liveSettings.supportPhone) }
    var supportWhatsApp by remember { mutableStateOf(liveSettings.supportWhatsApp) }
    var termsText by remember { mutableStateOf(liveSettings.termsAndConditions) }
    var privacyText by remember { mutableStateOf(liveSettings.privacyPolicy) }

    LaunchedEffect(liveSettings) {
        appName = liveSettings.appName
        appTagline = liveSettings.appTagline
        appLogoStyle = liveSettings.appLogoStyle
        maintenanceMode = liveSettings.maintenanceMode
        maintenanceMessage = liveSettings.maintenanceMessage
        announcementEnabled = liveSettings.announcementEnabled
        announcementTitle = liveSettings.announcementTitle
        announcementMessage = liveSettings.announcementMessage
        referralEnabled = liveSettings.referralEnabled
        referralPointsText = liveSettings.referralRewardPoints.toString()
        referralBonusMbText = liveSettings.referralRewardBonusMb.toString()
        supportEmail = liveSettings.supportEmail
        supportPhone = liveSettings.supportPhone
        supportWhatsApp = liveSettings.supportWhatsApp
        termsText = liveSettings.termsAndConditions
        privacyText = liveSettings.privacyPolicy
    }

    var editingNotification by remember { mutableStateOf<AppNotification?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. App Branding & Logo Style
        item {
            SettingsCard(title = "App Branding & Identity") {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name (Dynamic Top Bar & Gates)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_app_name_input")
                )
                OutlinedTextField(
                    value = appTagline,
                    onValueChange = { appTagline = it },
                    label = { Text("App Tagline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Logo Theme Style", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextNavyDark)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "CYAN_SHIELD" to "Cyan",
                        "ROYAL_BLUE" to "Royal",
                        "EMERALD_VAULT" to "Emerald",
                        "AMETHYST_LOCK" to "Amethyst"
                    ).forEach { (styleKey, label) ->
                        val selected = appLogoStyle == styleKey
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) NavyDeep else Color(0xFFF1F5F9))
                                .clickable { appLogoStyle = styleKey }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(label, color = if (selected) CyanAccent else TextMutedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Maintenance Mode
        item {
            SettingsCard(title = "Maintenance Mode Control") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Build, null, tint = if (maintenanceMode) StatusRed else TextMutedGray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Enable Maintenance Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                            Text("Blocks normal users; authorized Admins bypass", fontSize = 11.sp, color = TextMutedGray)
                        }
                    }
                    Switch(
                        checked = maintenanceMode,
                        onCheckedChange = { maintenanceMode = it },
                        modifier = Modifier.testTag("maintenance_mode_switch")
                    )
                }
                OutlinedTextField(
                    value = maintenanceMessage,
                    onValueChange = { maintenanceMessage = it },
                    label = { Text("Maintenance Notice Message") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Global Announcement & Referral Rewards
        item {
            SettingsCard(title = "Announcement & Referral Rewards") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, null, tint = BluePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dashboard Announcement Banner", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(checked = announcementEnabled, onCheckedChange = { announcementEnabled = it })
                }
                if (announcementEnabled) {
                    OutlinedTextField(
                        value = announcementTitle,
                        onValueChange = { announcementTitle = it },
                        label = { Text("Announcement Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = announcementMessage,
                        onValueChange = { announcementMessage = it },
                        label = { Text("Announcement Message") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Referral & Sharing Program Active", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = referralEnabled, onCheckedChange = { referralEnabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = referralPointsText,
                        onValueChange = { referralPointsText = it },
                        label = { Text("Reward Points") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = referralBonusMbText,
                        onValueChange = { referralBonusMbText = it },
                        label = { Text("Bonus Storage (MB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Support Contact & Legal Policies
        item {
            SettingsCard(title = "Support Contact, Terms & Privacy Policy") {
                OutlinedTextField(
                    value = supportEmail,
                    onValueChange = { supportEmail = it },
                    label = { Text("Support Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = supportPhone,
                        onValueChange = { supportPhone = it },
                        label = { Text("Support Phone") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = supportWhatsApp,
                        onValueChange = { supportWhatsApp = it },
                        label = { Text("WhatsApp") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = termsText,
                    onValueChange = { termsText = it },
                    label = { Text("Terms & Conditions") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = privacyText,
                    onValueChange = { privacyText = it },
                    label = { Text("Privacy Policy") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        adminViewModel.updateAppSettings(
                            liveSettings.copy(
                                appName = appName.trim().ifBlank { "SafeVault" },
                                appTagline = appTagline.trim(),
                                appLogoStyle = appLogoStyle,
                                maintenanceMode = maintenanceMode,
                                maintenanceMessage = maintenanceMessage.trim(),
                                announcementEnabled = announcementEnabled,
                                announcementTitle = announcementTitle.trim(),
                                announcementMessage = announcementMessage.trim(),
                                referralEnabled = referralEnabled,
                                referralRewardPoints = referralPointsText.toLongOrNull()?.coerceAtLeast(0L) ?: 100L,
                                referralRewardBonusMb = referralBonusMbText.toLongOrNull()?.coerceAtLeast(0L) ?: 512L,
                                supportEmail = supportEmail.trim(),
                                supportPhone = supportPhone.trim(),
                                supportWhatsApp = supportWhatsApp.trim(),
                                termsAndConditions = termsText,
                                privacyPolicy = privacyText
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_save_settings_button")
                ) {
                    Icon(Icons.Default.Save, null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Publish App Settings", color = CyanAccent, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Notification Management
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = BluePrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Push / Broadcast Notifications (${notifications.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                }
                Button(
                    onClick = { editingNotification = AppNotification(isActive = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp).testTag("new_notification_button")
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send", fontSize = 12.sp)
                }
            }
        }

        items(notifications, key = { it.id }) { notif ->
            val badgeColor = when (notif.priority) {
                "WARNING" -> StatusRed
                "PROMO" -> StatusAmber
                else -> BluePrimary
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PureWhite)
                    .border(1.dp, GrayBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("${notif.priority} • ${notif.targetAudience}", color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (notif.isActive) "LIVE" else "PAUSED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (notif.isActive) StatusGreen else TextMutedGray
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(notif.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                    Text(notif.message, fontSize = 11.sp, color = TextMutedGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = notif.isActive,
                        onCheckedChange = { checked ->
                            adminViewModel.saveNotification(notif.copy(isActive = checked))
                        }
                    )
                    IconButton(onClick = { adminViewModel.deleteNotification(notif) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Notification", tint = StatusRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    editingNotification?.let { draft ->
        var title by remember { mutableStateOf(draft.title) }
        var message by remember { mutableStateOf(draft.message) }
        var priority by remember { mutableStateOf(draft.priority) }
        var audience by remember { mutableStateOf(draft.targetAudience) }

        AlertDialog(
            onDismissRequest = { editingNotification = null },
            title = { Text("Create Broadcast Notification", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notification Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Priority", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("INFO", "WARNING", "PROMO").forEach { p ->
                            val selected = priority == p
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) NavyDeep else Color(0xFFF1F5F9))
                                    .clickable { priority = p }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(p, color = if (selected) CyanAccent else TextMutedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("Audience", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("ALL", "FREE", "PREMIUM").forEach { aud ->
                            val selected = audience == aud
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) BluePrimary else Color(0xFFF1F5F9))
                                    .clickable { audience = aud }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(aud, color = if (selected) Color.White else TextMutedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && message.isNotBlank()) {
                        adminViewModel.saveNotification(
                            draft.copy(title = title, message = message, priority = priority, targetAudience = audience, isActive = true)
                        ) { editingNotification = null }
                    }
                }) { Text("Publish Notification") }
            },
            dismissButton = {
                TextButton(onClick = { editingNotification = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PureWhite)
            .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
        content()
    }
}
