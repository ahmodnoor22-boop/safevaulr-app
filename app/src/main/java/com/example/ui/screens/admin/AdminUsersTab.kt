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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserAccount
import com.example.data.model.UserFilterMode
import com.example.data.model.formatStorageSize
import com.example.ui.AdminViewModel
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminUsersTab(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val users by adminViewModel.filteredUsers.collectAsStateWithLifecycle()
    val plans by adminViewModel.plans.collectAsStateWithLifecycle()
    val searchQuery by adminViewModel.userSearchQuery.collectAsStateWithLifecycle()
    val filterMode by adminViewModel.userFilterMode.collectAsStateWithLifecycle()

    var blockTarget by remember { mutableStateOf<UserAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<UserAccount?>(null) }
    var planTarget by remember { mutableStateOf<UserAccount?>(null) }
    var expiryTarget by remember { mutableStateOf<UserAccount?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Search & Filter Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { adminViewModel.setUserSearchQuery(it) },
                placeholder = { Text("Search name, phone number, UID, or plan...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_search")
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UserFilterMode.entries) { mode ->
                    val selected = filterMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) NavyDeep else Color(0xFFF1F5F9))
                            .clickable { adminViewModel.setUserFilterMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("user_filter_${mode.name}")
                    ) {
                        Text(
                            text = mode.label,
                            color = if (selected) Color.White else TextMutedGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Showing ${users.size} Users",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMutedGray
                )
            }

            items(users, key = { it.id }) { user ->
                AdminUserCard(
                    user = user,
                    onBlockClick = { blockTarget = user },
                    onUnblockClick = { adminViewModel.setUserBlockStatus(user, block = false, reason = "") },
                    onChangePlanClick = { planTarget = user },
                    onAdjustExpiryClick = { expiryTarget = user },
                    onDeleteClick = { deleteTarget = user }
                )
            }
        }
    }

    blockTarget?.let { target ->
        var reason by remember { mutableStateOf("Security / Policy enforcement") }
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Block ${target.fullName}?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Blocked users will immediately lose access to their vault until unblocked.",
                        fontSize = 12.sp,
                        color = TextMutedGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Block Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.setUserBlockStatus(target, block = true, reason = reason)
                        blockTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Block User")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Confirm User Deletion", fontWeight = FontWeight.Bold, color = StatusRed) },
            text = {
                Text(
                    "Permanently delete ${target.fullName} (${target.phoneNumber}) and ALL of their encrypted files, folders, and vault items? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteUser(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    modifier = Modifier.testTag("confirm_delete_user_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    planTarget?.let { target ->
        val defaultFree = SubscriptionPlan(
            id = "free",
            name = "Free Vault",
            tier = "FREE",
            price = 0.0,
            durationDays = 0,
            storageQuotaGb = 15L,
            maxFileSizeMb = 14L,
            adsRemoved = false
        )
        val selectablePlans = listOf(defaultFree) + plans.filter { it.id != "free" }
        AlertDialog(
            onDismissRequest = { planTarget = null },
            title = { Text("Assign Plan to ${target.fullName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectablePlans.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (target.planId == p.id) Color(0xFFEFF6FF) else Color(0xFFF8FAFD))
                                .border(
                                    1.dp,
                                    if (target.planId == p.id) BluePrimary else GrayBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    adminViewModel.assignPlanToUser(target, p)
                                    planTarget = null
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextNavyDark)
                                Text(
                                    "${p.tier} • ${p.storageQuotaGb} GB • ${if (p.durationDays > 0) "${p.durationDays} days" else "Lifetime"}",
                                    fontSize = 11.sp,
                                    color = TextMutedGray
                                )
                            }
                            Text(
                                text = "${p.currency} ${String.format("%.2f", p.price)}",
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { planTarget = null }) { Text("Close") }
            }
        )
    }

    expiryTarget?.let { target ->
        var customDaysText by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { expiryTarget = null },
            title = { Text("Extend / Reduce Plan Expiry", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Current Plan: ${target.planName}\nExpiry: ${formatExpiry(target.planExpiresAt)}",
                        fontSize = 12.sp,
                        color = TextMutedGray
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { adminViewModel.adjustUserPlanExpiry(target, 7); expiryTarget = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("+7d", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { adminViewModel.adjustUserPlanExpiry(target, 30); expiryTarget = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("+30d", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { adminViewModel.adjustUserPlanExpiry(target, -7); expiryTarget = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("-7d", fontSize = 12.sp) }
                    }
                    OutlinedTextField(
                        value = customDaysText,
                        onValueChange = { customDaysText = it },
                        label = { Text("Custom Days (+/- e.g. 90 or -15)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val days = customDaysText.trim().toIntOrNull()
                    if (days != null && days != 0) {
                        adminViewModel.adjustUserPlanExpiry(target, days)
                        expiryTarget = null
                    }
                }) {
                    Text("Apply Days")
                }
            },
            dismissButton = {
                TextButton(onClick = { expiryTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminUserCard(
    user: UserAccount,
    onBlockClick: () -> Unit,
    onUnblockClick: () -> Unit,
    onChangePlanClick: () -> Unit,
    onAdjustExpiryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PureWhite)
            .border(1.dp, if (user.isBlocked) StatusRed.copy(alpha = 0.4f) else GrayBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("admin_user_card_${user.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(user.avatarEmoji.ifBlank { "🛡️" }, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(user.fullName.ifBlank { "Unnamed User" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = TextMutedGray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(user.phoneNumber.ifBlank { user.id }, fontSize = 12.sp, color = TextMutedGray)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (user.isBlocked) StatusRed.copy(alpha = 0.14f) else StatusGreen.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (user.isBlocked) "BLOCKED" else "ACTIVE",
                    color = if (user.isBlocked) StatusRed else StatusGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF8FAFD))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Plan", fontSize = 10.sp, color = TextMutedGray)
                Text(
                    "${user.planName} (${user.planTier})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (user.isPremium) BluePrimary else TextNavyDark
                )
                Text("Exp: ${formatExpiry(user.planExpiresAt)}", fontSize = 10.sp, color = TextMutedGray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Quota & Referrals", fontSize = 10.sp, color = TextMutedGray)
                Text(formatStorageSize(user.effectiveQuotaBytes), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                Text("${user.referralCount} invites • ${user.referralRewardPoints} pts", fontSize = 10.sp, color = TextMutedGray)
            }
        }

        if (user.isBlocked && user.blockReason.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Reason: ${user.blockReason}", fontSize = 11.sp, color = StatusRed)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (user.isBlocked) {
                Button(
                    onClick = onUnblockClick,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(40.dp).testTag("unblock_user_${user.id}")
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unblock", fontSize = 11.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onBlockClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(40.dp).testTag("block_user_${user.id}")
                ) {
                    Icon(Icons.Default.Block, null, tint = StatusRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Block", fontSize = 11.sp, color = StatusRed)
                }
            }

            OutlinedButton(
                onClick = onChangePlanClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(40.dp).testTag("change_plan_${user.id}")
            ) {
                Icon(Icons.Default.CardMembership, null, tint = BluePrimary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Plan", fontSize = 11.sp, color = BluePrimary)
            }

            OutlinedButton(
                onClick = onAdjustExpiryClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(40.dp).testTag("adjust_expiry_${user.id}")
            ) {
                Icon(Icons.Default.Schedule, null, tint = TextNavyDark, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Expiry", fontSize = 11.sp, color = TextNavyDark)
            }

            OutlinedButton(
                onClick = onDeleteClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.height(40.dp).testTag("delete_user_${user.id}")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete User", tint = StatusRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatExpiry(epochMs: Long): String {
    if (epochMs <= 0L) return "No Expiry"
    val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val formatted = df.format(Date(epochMs))
    return if (epochMs < System.currentTimeMillis()) "$formatted (Expired)" else formatted
}
