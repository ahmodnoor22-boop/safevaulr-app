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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AdminMember
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.ui.AdminViewModel
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusPurple
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminSecurityTab(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val currentAdmin by adminViewModel.currentAdmin.collectAsStateWithLifecycle()
    val admins by adminViewModel.admins.collectAsStateWithLifecycle()
    val auditLogs by adminViewModel.auditLogs.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(false) }
    var editingAdmin by remember { mutableStateOf<AdminMember?>(null) }
    var revokeTarget by remember { mutableStateOf<AdminMember?>(null) }

    val canManageAdmins = currentAdmin?.hasPermission(AdminPermission.MANAGE_ADMINS) == true

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Admin Security PIN Setup
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PureWhite)
                    .border(1.dp, GrayBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = StatusPurple, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secondary Admin Security PIN", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (currentAdmin?.hasPinConfigured == true) {
                        "Admin PIN protection is ACTIVE. Opening the Admin Panel requires your secondary PIN."
                    } else {
                        "Set a 4-8 digit secondary Admin Security PIN for an extra authentication layer."
                    },
                    fontSize = 12.sp,
                    color = TextMutedGray
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showPinDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("set_admin_pin_button")
                    ) {
                        Text(
                            if (currentAdmin?.hasPinConfigured == true) "Change Admin PIN" else "Configure Admin PIN",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (currentAdmin?.hasPinConfigured == true) {
                        OutlinedButton(
                            onClick = { adminViewModel.resetPinGate() },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("Lock Now", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Authorized Admins Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Authorized Admins (${admins.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                    Text("Zero-trust role & permission registry", fontSize = 11.sp, color = TextMutedGray)
                }
                if (canManageAdmins) {
                    Button(
                        onClick = {
                            editingAdmin = AdminMember(
                                role = AdminRole.MODERATOR,
                                permissions = AdminRole.MODERATOR.defaultPermissions.map { it.name },
                                isActive = true
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp).testTag("add_admin_button")
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Admin", fontSize = 12.sp)
                    }
                }
            }
        }

        items(admins, key = { it.uid }) { member ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PureWhite)
                    .border(1.dp, GrayBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = BluePrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(member.fullName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                            Text("UID: ${member.uid}", fontSize = 10.sp, color = TextMutedGray)
                            if (member.email.isNotBlank() || member.phoneNumber.isNotBlank()) {
                                Text("${member.phoneNumber} ${member.email}".trim(), fontSize = 11.sp, color = TextMutedGray)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (member.isActive) StatusGreen.copy(alpha = 0.14f) else StatusRed.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${member.role.displayName}${if (!member.isActive) " (OFF)" else ""}",
                                color = if (member.isActive) StatusGreen else StatusRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (canManageAdmins) {
                            IconButton(onClick = { editingAdmin = member }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Admin", tint = BluePrimary, modifier = Modifier.size(18.dp))
                            }
                            if (member.uid != currentAdmin?.uid) {
                                IconButton(onClick = { revokeTarget = member }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Revoke Admin", tint = StatusRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                if (member.permissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Permissions: ${member.permissions.joinToString(", ")}",
                        fontSize = 11.sp,
                        color = TextMutedGray
                    )
                }
            }
        }

        // Security Audit Logs
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = NavyDeep, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Immutable Security Audit Logs (${auditLogs.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
            }
        }

        if (auditLogs.isEmpty()) {
            item {
                Text("No audit log entries recorded yet.", fontSize = 12.sp, color = TextMutedGray)
            }
        } else {
            items(auditLogs.take(40), key = { it.id }) { log ->
                val timeStr = remember(log.createdAt) {
                    SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(log.createdAt))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PureWhite)
                        .border(1.dp, GrayBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.action, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                        Text(timeStr, fontSize = 10.sp, color = TextMutedGray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(log.details, fontSize = 12.sp, color = TextNavyDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Actor: ${log.actorName} (${log.actorRole}) • Target: ${log.targetType}/${log.targetId}",
                        fontSize = 10.sp,
                        color = TextMutedGray
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configure Admin Security PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a 4 to 8 digit PIN used solely to unlock the Admin Control Center.", fontSize = 12.sp, color = TextMutedGray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("4-8 Digit Admin PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPin.length in 4..8) {
                        adminViewModel.setOwnAdminPin(newPin) { showPinDialog = false }
                    }
                }) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingAdmin?.let { draft ->
        AdminRoleEditorDialog(
            initial = draft,
            onDismiss = { editingAdmin = null },
            onSave = { uid, name, phone, email, role, perms, active ->
                adminViewModel.saveAdminMember(uid, name, phone, email, role, perms, active) {
                    editingAdmin = null
                }
            }
        )
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke Admin Access?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove all admin privileges from ${target.fullName} (${target.uid})?") },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.revokeAdminMember(target)
                        revokeTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) { Text("Revoke") }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminRoleEditorDialog(
    initial: AdminMember,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, AdminRole, List<AdminPermission>, Boolean) -> Unit
) {
    var uid by remember { mutableStateOf(initial.uid) }
    var fullName by remember { mutableStateOf(initial.fullName) }
    var phone by remember { mutableStateOf(initial.phoneNumber) }
    var email by remember { mutableStateOf(initial.email) }
    var role by remember { mutableStateOf(initial.role) }
    var selectedPerms by remember {
        mutableStateOf(
            if (initial.permissions.isNotEmpty()) {
                initial.permissions.mapNotNull { name ->
                    AdminPermission.entries.find { it.name == name }
                }.toSet()
            } else {
                initial.role.defaultPermissions.toSet()
            }
        )
    }
    var isActive by remember { mutableStateOf(initial.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.uid.isBlank()) "Authorize New Admin" else "Edit Admin Role", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uid,
                    onValueChange = { if (initial.uid.isBlank()) uid = it },
                    enabled = initial.uid.isBlank(),
                    label = { Text("Firebase Auth UID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Admin Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Role", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminRole.entries.forEach { r ->
                        val selected = role == r
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NavyDeep else Color(0xFFF1F5F9))
                                .clickable {
                                    role = r
                                    selectedPerms = r.defaultPermissions.toSet()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(r.displayName, color = if (selected) CyanAccent else TextMutedGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Permissions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextNavyDark)
                AdminPermission.entries.forEach { perm ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPerms = if (perm in selectedPerms) selectedPerms - perm else selectedPerms + perm
                            }
                    ) {
                        Checkbox(
                            checked = role == AdminRole.SUPER_ADMIN || perm in selectedPerms,
                            onCheckedChange = { checked ->
                                selectedPerms = if (checked) selectedPerms + perm else selectedPerms - perm
                            }
                        )
                        Text(perm.label, fontSize = 12.sp, color = TextNavyDark)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Admin Active", fontSize = 13.sp)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (uid.isNotBlank() && fullName.isNotBlank()) {
                    onSave(uid, fullName, phone, email, role, selectedPerms.toList(), isActive)
                }
            }) { Text("Save Admin") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
