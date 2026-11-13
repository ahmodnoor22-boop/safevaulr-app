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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SubscriptionPlan
import com.example.ui.AdminViewModel
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@Composable
fun AdminPlansTab(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val plans by adminViewModel.plans.collectAsStateWithLifecycle()
    val users by adminViewModel.users.collectAsStateWithLifecycle()

    var editingPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var deletePlanTarget by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var assignPlanTarget by remember { mutableStateOf<SubscriptionPlan?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Subscription Plans (${plans.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                    Text("Configure pricing, duration, storage limits & ads", fontSize = 12.sp, color = TextMutedGray)
                }
                Button(
                    onClick = {
                        editingPlan = SubscriptionPlan(
                            name = "",
                            tier = "PREMIUM",
                            price = 4.99,
                            currency = "USD",
                            durationDays = 30,
                            storageQuotaGb = 100L,
                            maxFileSizeMb = 50L,
                            features = listOf("100 GB Cloud Vault", "Ad-Free Experience", "Priority Cloud Restore"),
                            adsRemoved = true,
                            isActive = true
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp).testTag("admin_create_plan_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Plan", color = CyanAccent, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (plans.isEmpty()) {
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
                    Text(
                        "No custom plans created yet. Tap 'New Plan' to create Free or Premium cloud storage plans.",
                        fontSize = 13.sp,
                        color = TextMutedGray
                    )
                }
            }
        } else {
            items(plans, key = { it.id }) { plan ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PureWhite)
                        .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plan.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (plan.tier == "PREMIUM") Color(0xFFDBEAFE) else Color(0xFFF1F5F9))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = plan.tier,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (plan.tier == "PREMIUM") BluePrimary else TextMutedGray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${plan.currency} ${String.format("%.2f", plan.price)} / ${if (plan.durationDays == 0) "Lifetime" else "${plan.durationDays} days"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                        }

                        Row {
                            IconButton(onClick = { editingPlan = plan }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Plan", tint = BluePrimary)
                            }
                            IconButton(onClick = { deletePlanTarget = plan }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Plan", tint = StatusRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Quota: ${plan.storageQuotaGb} GB • Max File: ${plan.maxFileSizeMb} MB • Ads: ${if (plan.adsRemoved) "Removed" else "Shown"} • ${if (plan.isActive) "Active" else "Hidden"}",
                        fontSize = 12.sp,
                        color = TextMutedGray
                    )

                    if (plan.features.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        plan.features.forEach { feature ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, null, tint = StatusGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(feature, fontSize = 12.sp, color = TextNavyDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { assignPlanTarget = plan },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Assign Plan to User", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    editingPlan?.let { draft ->
        PlanEditorDialog(
            initialPlan = draft,
            onDismiss = { editingPlan = null },
            onSave = { updated ->
                adminViewModel.savePlan(updated) { editingPlan = null }
            }
        )
    }

    deletePlanTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deletePlanTarget = null },
            title = { Text("Delete '${target.name}'?", fontWeight = FontWeight.Bold) },
            text = { Text("Removing this plan prevents future assignments. Existing users keep their current quota until changed.") },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deletePlan(target)
                        deletePlanTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletePlanTarget = null }) { Text("Cancel") }
            }
        )
    }

    assignPlanTarget?.let { plan ->
        AlertDialog(
            onDismissRequest = { assignPlanTarget = null },
            title = { Text("Assign '${plan.name}' to User", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(users, key = { it.id }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF8FAFD))
                                .border(1.dp, GrayBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    adminViewModel.assignPlanToUser(user, plan)
                                    assignPlanTarget = null
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(user.fullName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                                Text(user.phoneNumber, fontSize = 11.sp, color = TextMutedGray)
                            }
                            Text(user.planName, fontSize = 11.sp, color = BluePrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { assignPlanTarget = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun PlanEditorDialog(
    initialPlan: SubscriptionPlan,
    onDismiss: () -> Unit,
    onSave: (SubscriptionPlan) -> Unit
) {
    var name by remember { mutableStateOf(initialPlan.name) }
    var tier by remember { mutableStateOf(initialPlan.tier) }
    var priceText by remember { mutableStateOf(initialPlan.price.toString()) }
    var currency by remember { mutableStateOf(initialPlan.currency) }
    var durationText by remember { mutableStateOf(initialPlan.durationDays.toString()) }
    var quotaGbText by remember { mutableStateOf(initialPlan.storageQuotaGb.toString()) }
    var maxFileMbText by remember { mutableStateOf(initialPlan.maxFileSizeMb.toString()) }
    var featuresText by remember { mutableStateOf(initialPlan.features.joinToString("\n")) }
    var adsRemoved by remember { mutableStateOf(initialPlan.adsRemoved) }
    var isActive by remember { mutableStateOf(initialPlan.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPlan.id.isBlank()) "Create New Plan" else "Edit Plan", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plan_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("FREE", "PREMIUM").forEach { option ->
                        val selected = tier == option
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) NavyDeep else Color(0xFFF1F5F9))
                                .clickable { tier = option }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(option, color = if (selected) CyanAccent else TextMutedGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text("Days (0=Life)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quotaGbText,
                        onValueChange = { quotaGbText = it },
                        label = { Text("Quota (GB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = maxFileMbText,
                    onValueChange = { maxFileMbText = it },
                    label = { Text("Max File Size (MB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = featuresText,
                    onValueChange = { featuresText = it },
                    label = { Text("Features (one per line)") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Remove Adsterra Ads", fontSize = 13.sp)
                    Switch(checked = adsRemoved, onCheckedChange = { adsRemoved = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible & Active", fontSize = 13.sp)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialPlan.copy(
                            name = name.trim().ifBlank { "New Plan" },
                            tier = tier,
                            price = priceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            currency = currency.trim().ifBlank { "USD" },
                            durationDays = durationText.toIntOrNull()?.coerceAtLeast(0) ?: 30,
                            storageQuotaGb = quotaGbText.toLongOrNull()?.coerceAtLeast(1L) ?: 15L,
                            maxFileSizeMb = maxFileMbText.toLongOrNull()?.coerceAtLeast(1L) ?: 14L,
                            features = featuresText.lines().map { it.trim() }.filter { it.isNotBlank() },
                            adsRemoved = adsRemoved,
                            isActive = isActive
                        )
                    )
                },
                modifier = Modifier.testTag("save_plan_confirm_button")
            ) {
                Text("Save Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
