package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VaultCategory
import com.example.ui.SafeVaultViewModel
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusPurple
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadModalSheet(
    viewModel: SafeVaultViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Android Photo & Video Picker (zero-permission Play-compliant)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadUri(uri)
            onDismiss()
        }
    }

    // 2. Android Document / Audio / Archive / Any File Picker (OpenDocument)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadUri(uri)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.testTag("upload_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Backup to SafeVault Cloud",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavyDark
            )
            Text(
                text = "All uploads are AES-256 encrypted and synced permanently",
                fontSize = 12.sp,
                color = TextMutedGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            UploadActionItem(
                icon = Icons.Default.Image,
                iconColor = CyanAccent,
                title = "Upload Photos & Images",
                subtitle = "JPG, PNG, HEIC, WEBP via secure Android Photo Picker",
                onClick = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            UploadActionItem(
                icon = Icons.Default.VideoFile,
                iconColor = Color(0xFFEC4899),
                title = "Upload Videos",
                subtitle = "MP4, MOV, MKV backed up with encrypted chunks",
                onClick = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            )

            UploadActionItem(
                icon = Icons.Default.AudioFile,
                iconColor = StatusGreen,
                title = "Upload Audio & Music",
                subtitle = "MP3, WAV, M4A, FLAC audio recordings & music",
                onClick = {
                    documentPickerLauncher.launch(arrayOf("audio/*"))
                }
            )

            UploadActionItem(
                icon = Icons.Default.PictureAsPdf,
                iconColor = StatusRed,
                title = "Upload PDF Files",
                subtitle = "Contracts, invoices, books, receipts",
                onClick = {
                    documentPickerLauncher.launch(arrayOf("application/pdf"))
                }
            )

            UploadActionItem(
                icon = Icons.Default.Description,
                iconColor = BluePrimary,
                title = "Word, Excel & PowerPoint Docs",
                subtitle = "DOC, DOCX, XLS, XLSX, PPT, PPTX",
                onClick = {
                    documentPickerLauncher.launch(
                        arrayOf(
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-powerpoint",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        )
                    )
                }
            )

            UploadActionItem(
                icon = Icons.Default.Archive,
                iconColor = StatusAmber,
                title = "ZIP, RAR & Any Other File",
                subtitle = "ZIP, RAR, 7Z archives and all file formats",
                onClick = {
                    documentPickerLauncher.launch(arrayOf("*/*"))
                }
            )

            UploadActionItem(
                icon = Icons.AutoMirrored.Filled.Article,
                iconColor = StatusPurple,
                title = "Create Encrypted Note",
                subtitle = "AES-256 encrypted text notes synced to cloud",
                onClick = {
                    onDismiss()
                    viewModel.setShowNewNoteDialog(true)
                }
            )

            UploadActionItem(
                icon = Icons.Default.CreateNewFolder,
                iconColor = BluePrimary,
                title = "Create Cloud Folder",
                subtitle = "Organize files & preserve folder structure",
                onClick = {
                    onDismiss()
                    viewModel.setShowNewFolderDialog(true)
                }
            )

            UploadActionItem(
                icon = Icons.Default.Password,
                iconColor = StatusAmber,
                title = "Store Vault Secret / Record",
                subtitle = "Passwords, bank cards, IDs & recovery keys",
                onClick = {
                    onDismiss()
                    viewModel.setShowNewVaultDialog(true)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UploadActionItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconColor.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavyDark
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMutedGray
            )
        }
    }
}

@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, isEncrypted: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isEncrypted by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create SafeVault Note", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    placeholder = { Text("e.g. Master seed phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("note_title_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    placeholder = { Text("Enter confidential notes here...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("note_content_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isEncrypted = !isEncrypted }
                ) {
                    Checkbox(checked = isEncrypted, onCheckedChange = { isEncrypted = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Encrypt with AES-256", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() || content.isNotBlank()) {
                        onSave(title, content, isEncrypted)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
            ) {
                Text("Save to Cloud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateVaultItemDialog(
    onDismiss: () -> Unit,
    onSave: (category: VaultCategory, title: String, username: String, secret: String, notes: String) -> Unit
) {
    var category by remember { mutableStateOf(VaultCategory.PASSWORD) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Secure Vault", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Google Login") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Account Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Password / PIN / Secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Confidential Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && secret.isNotBlank()) {
                        onSave(category, title, username, secret, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusPurple)
            ) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
