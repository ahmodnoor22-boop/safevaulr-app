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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ReportPeriod
import com.example.data.model.formatStorageSize
import com.example.ui.AdminViewModel
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusPurple
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@Composable
fun AdminDashboardTab(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val metrics by adminViewModel.metrics.collectAsStateWithLifecycle()
    val period by adminViewModel.reportPeriod.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Daily / Weekly / Monthly Report Filter
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PureWhite)
                    .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Analytics & Performance Reports",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavyDark
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReportPeriod.entries.forEach { itemPeriod ->
                        val selected = period == itemPeriod
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) NavyDeep else Color.Transparent)
                                .clickable { adminViewModel.setReportPeriod(itemPeriod) }
                                .padding(vertical = 10.dp)
                                .testTag("report_period_${itemPeriod.name}")
                        ) {
                            Text(
                                text = itemPeriod.label,
                                color = if (selected) CyanAccent else TextMutedGray,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // KPI Grid Row 1: Total Users & Active Users
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminKpiCard(
                    title = "Total Users",
                    value = metrics.totalUsers.toString(),
                    subtitle = "${metrics.blockedUsers} blocked accounts",
                    icon = Icons.Default.Group,
                    accent = BluePrimary,
                    modifier = Modifier.weight(1f)
                )
                AdminKpiCard(
                    title = "Active Users",
                    value = metrics.activeUsers.toString(),
                    subtitle = "${metrics.activeUsersInPeriod} active (${period.label.lowercase()})",
                    icon = Icons.Default.VerifiedUser,
                    accent = StatusGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // KPI Grid Row 2: Inactive Users & New Users
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminKpiCard(
                    title = "Inactive Users",
                    value = metrics.inactiveUsers.toString(),
                    subtitle = "Dormant 30d+ or blocked",
                    icon = Icons.Default.Block,
                    accent = StatusAmber,
                    modifier = Modifier.weight(1f)
                )
                AdminKpiCard(
                    title = "New Users (${period.label})",
                    value = metrics.newUsersInPeriod.toString(),
                    subtitle = "Joined in ${period.label.lowercase()} window",
                    icon = Icons.Default.PersonAdd,
                    accent = StatusPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // KPI Grid Row 3: Premium Users & Free Users
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminKpiCard(
                    title = "Premium Users",
                    value = metrics.premiumUsers.toString(),
                    subtitle = "Active paid vaults",
                    icon = Icons.Default.WorkspacePremium,
                    accent = Color(0xFF0EA5E9),
                    modifier = Modifier.weight(1f)
                )
                AdminKpiCard(
                    title = "Free Users",
                    value = metrics.freeUsers.toString(),
                    subtitle = "Standard free tier",
                    icon = Icons.Default.Star,
                    accent = TextMutedGray,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // KPI Grid Row 4: Total Referrals & Total Revenue
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminKpiCard(
                    title = "Total Referrals",
                    value = metrics.totalReferrals.toString(),
                    subtitle = "${metrics.approvedReferrals} approved • ${metrics.pendingReferrals} pending",
                    icon = Icons.Default.Share,
                    accent = Color(0xFF059669),
                    modifier = Modifier.weight(1f)
                )
                AdminKpiCard(
                    title = "Total Revenue",
                    value = "$" + String.format("%.2f", metrics.totalRevenue),
                    subtitle = "$" + String.format("%.2f", metrics.revenueInPeriod) + " (${period.label})",
                    icon = Icons.Default.AttachMoney,
                    accent = StatusGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Visual Breakdown & Report Card
        item {
            val total = metrics.totalUsers.coerceAtLeast(1)
            val activeRatio = metrics.activeUsers.toFloat() / total
            val premiumRatio = metrics.premiumUsers.toFloat() / total
            val refTotal = metrics.totalReferrals.coerceAtLeast(1)
            val approvedRefRatio = metrics.approvedReferrals.toFloat() / refTotal

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PureWhite)
                    .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${period.label} Summary & Health Breakdown",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavyDark
                )

                MetricProgressRow(
                    label = "Active Accounts (${metrics.activeUsers}/${metrics.totalUsers})",
                    progress = activeRatio,
                    color = StatusGreen
                )
                MetricProgressRow(
                    label = "Premium Adoption (${metrics.premiumUsers}/${metrics.totalUsers})",
                    progress = premiumRatio,
                    color = BluePrimary
                )
                MetricProgressRow(
                    label = "Verified Referrals (${metrics.approvedReferrals}/${metrics.totalReferrals})",
                    progress = approvedRefRatio,
                    color = StatusPurple
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFD))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Period Signups", fontSize = 11.sp, color = TextMutedGray)
                        Text("+${metrics.newUsersInPeriod} users", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                    }
                    Column {
                        Text("Period Referrals", fontSize = 11.sp, color = TextMutedGray)
                        Text("+${metrics.referralsInPeriod} invites", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Allocated Cloud", fontSize = 11.sp, color = TextMutedGray)
                        Text(formatStorageSize(metrics.totalAllocatedQuotaBytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PureWhite)
            .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMutedGray
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.14f))
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextNavyDark
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = TextMutedGray
        )
    }
}

@Composable
private fun MetricProgressRow(
    label: String,
    progress: Float,
    color: Color
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextNavyDark)
            Text(text = "${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color(0xFFE2E8F0)
        )
    }
}
