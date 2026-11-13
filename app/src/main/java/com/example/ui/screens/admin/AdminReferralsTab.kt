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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ReferralRecord
import com.example.data.model.formatStorageSize
import com.example.ui.AdminViewModel
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@Composable
fun AdminReferralsTab(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val allReferrals by adminViewModel.referrals.collectAsStateWithLifecycle()
    val filteredReferrals by adminViewModel.filteredReferrals.collectAsStateWithLifecycle()
    val filterStatus by adminViewModel.referralStatusFilter.collectAsStateWithLifecycle()
    val users by adminViewModel.users.collectAsStateWithLifecycle()
    val settings by adminViewModel.appSettings.collectAsStateWithLifecycle()

    var rejectTarget by remember { mutableStateOf<ReferralRecord?>(null) }

    // Detect duplicate device fingerprint hashes across multiple referral claims for anti-fraud alerting
    val deviceHashCounts = remember(allReferrals) {
        allReferrals.groupingBy { it.deviceFingerprintHash }.eachCount()
    }
    val topReferrers = remember(users) {
        users.filter { it.referralCount > 0 }.sortedByDescending { it.referralCount }.take(5)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary & Reward Config Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PureWhite)
                    .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text("Referral & Anti-Fraud Control", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reward Policy: +${settings.referralRewardPoints} pts & +${settings.referralRewardBonusMb} MB cloud bonus per approved invite • Program: ${if (settings.referralEnabled) "ACTIVE" else "PAUSED"}",
                    fontSize = 12.sp,
                    color = TextMutedGray
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("ALL", "PENDING", "APPROVED", "REJECTED")) { status ->
                        val selected = filterStatus == status
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) NavyDeep else Color(0xFFF1F5F9))
                                .clickable { adminViewModel.setReferralStatusFilter(status) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = status,
                                color = if (selected) Color.White else TextMutedGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Top Referrers Leaderboard
        if (topReferrers.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PureWhite)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Top Referrers", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                    topReferrers.forEach { u ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${u.fullName} (${u.referralCode})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextNavyDark)
                                Text(u.phoneNumber, fontSize = 11.sp, color = TextMutedGray)
                            }
                            Text(
                                "${u.referralCount} invites • ${u.referralRewardPoints} pts",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Referral History (${filteredReferrals.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextMutedGray
            )
        }

        if (filteredReferrals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PureWhite)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No referral records matching '$filterStatus'.", fontSize = 13.sp, color = TextMutedGray)
                }
            }
        } else {
            items(filteredReferrals, key = { it.id }) { ref ->
                val isDuplicateDevice = (deviceHashCounts[ref.deviceFingerprintHash] ?: 0) > 1
                val statusColor = when (ref.status) {
                    "APPROVED" -> StatusGreen
                    "REJECTED" -> StatusRed
                    else -> StatusAmber
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PureWhite)
                        .border(1.dp, if (isDuplicateDevice) StatusAmber else GrayBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Code: ${ref.referralCode}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(ref.status, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inviter (Referrer)", fontSize = 10.sp, color = TextMutedGray)
                            Text(ref.referrerName.ifBlank { ref.referrerUid }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextMutedGray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Invited User", fontSize = 10.sp, color = TextMutedGray)
                            Text(ref.referredName.ifBlank { ref.referredUid }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                            if (ref.referredPhone.isNotBlank()) {
                                Text(ref.referredPhone, fontSize = 10.sp, color = TextMutedGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Device Hash: ${ref.deviceFingerprintHash.take(14)}... • Reward: ${ref.rewardPoints} pts / ${formatStorageSize(ref.rewardStorageBytes)}",
                        fontSize = 11.sp,
                        color = TextMutedGray
                    )

                    if (isDuplicateDevice) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = StatusAmber, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Fraud Alert: Multiple referral claims from identical device hash",
                                fontSize = 11.sp,
                                color = StatusAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (ref.fraudReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Note: ${ref.fraudReason}", fontSize = 11.sp, color = StatusRed)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (ref.status != "APPROVED") {
                            Button(
                                onClick = { adminViewModel.reviewReferral(ref, approve = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("approve_ref_${ref.id}")
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve Reward", fontSize = 11.sp)
                            }
                        }
                        if (ref.status != "REJECTED") {
                            OutlinedButton(
                                onClick = { rejectTarget = ref },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("reject_ref_${ref.id}")
                            ) {
                                Icon(Icons.Default.Close, null, tint = StatusRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reject / Fraud", fontSize = 11.sp, color = StatusRed)
                            }
                        }
                    }
                }
            }
        }
    }

    rejectTarget?.let { target ->
        var reason by remember { mutableStateOf("Suspicious / Duplicate device referral") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Reject Referral Claim?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Any previously granted referral rewards for this claim will be reversed.", fontSize = 12.sp, color = TextMutedGray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Fraud / Rejection Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.reviewReferral(target, approve = false, fraudReason = reason)
                        rejectTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) { Text("Reject Claim") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("Cancel") }
            }
        )
    }
}
