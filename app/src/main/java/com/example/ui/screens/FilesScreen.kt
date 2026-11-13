package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.SafeVaultViewModel
import com.example.ui.ViewMode
import com.example.ui.components.AdPlacement
import com.example.ui.components.AdsterraBanner
import com.example.ui.components.FileGridCard
import com.example.ui.components.FileListItem
import com.example.ui.components.FolderCard
import com.example.ui.components.TransferProgressBanner
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@Composable
fun FilesScreen(
    viewModel: SafeVaultViewModel,
    modifier: Modifier = Modifier
) {
    val activeFolderId by viewModel.activeFolderId.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val allFiles by viewModel.files.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val adConfig by viewModel.adConfig.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = activeFolderId != null) {
        viewModel.openFolder(null)
    }

    val currentFolder = folders.find { it.id == activeFolderId }
    val displayedFiles = if (activeFolderId == null) {
        allFiles
    } else {
        allFiles.filter { it.folderId == activeFolderId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFD))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (activeFolderId != null) {
                    IconButton(
                        onClick = { viewModel.openFolder(null) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to root",
                            tint = TextNavyDark
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column {
                    Text(
                        text = currentFolder?.name ?: "All Files",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavyDark
                    )
                    Text(
                        text = if (currentFolder != null) {
                            "Cloud Storage / All Folder / ${currentFolder.name} /"
                        } else {
                            "Cloud Storage / All Folder /"
                        },
                        fontSize = 11.sp,
                        color = TextMutedGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (viewMode == ViewMode.GRID) Color.White else Color.Transparent)
                            .clickable { if (viewMode != ViewMode.GRID) viewModel.toggleViewMode() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Grid",
                            tint = if (viewMode == ViewMode.GRID) NavyDeep else TextMutedGray,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (viewMode == ViewMode.LIST) Color.White else Color.Transparent)
                            .clickable { if (viewMode != ViewMode.LIST) viewModel.toggleViewMode() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "List",
                            tint = if (viewMode == ViewMode.LIST) NavyDeep else TextMutedGray,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextNavyDark
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Folder") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.setShowNewFolderDialog(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Upload File") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.setShowUploadSheet(true)
                            }
                        )
                    }
                }
            }
        }

        if (transferProgress.isActive) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                TransferProgressBanner(progress = transferProgress)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (activeFolderId == null && folders.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Folders (${folders.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMutedGray
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders, key = { it.id }) { folder ->
                                FolderCard(
                                    folder = folder,
                                    onClick = { viewModel.openFolder(folder.id) },
                                    onRename = { newName -> viewModel.renameFolder(folder.id, newName) },
                                    onDelete = { viewModel.deleteFolder(folder.id) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }

            if (adConfig.isEnabled && adConfig.showOnStoragePage) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        AdsterraBanner(config = adConfig, placement = AdPlacement.STORAGE_BANNER)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activeFolderId == null) "All Files (${displayedFiles.size})" else "Files (${displayedFiles.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavyDark
                    )
                }
            }

            if (displayedFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = GrayBorder,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No files in this folder yet",
                                fontSize = 14.sp,
                                color = TextMutedGray
                            )
                        }
                    }
                }
            } else {
                if (viewMode == ViewMode.LIST) {
                    items(displayedFiles, key = { it.id }) { file ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                            FileListItem(
                                file = file,
                                onClick = { viewModel.selectFile(file) },
                                onRename = { newName -> viewModel.renameFile(file.id, newName) },
                                onDelete = { viewModel.deleteFile(file) },
                                onToggleFavorite = { viewModel.toggleFavorite(file) }
                            )
                        }
                    }
                } else {
                    val chunked = displayedFiles.chunked(2)
                    items(chunked) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FileGridCard(
                                    file = pair[0],
                                    onClick = { viewModel.selectFile(pair[0]) },
                                    onRename = { newName -> viewModel.renameFile(pair[0].id, newName) },
                                    onDelete = { viewModel.deleteFile(pair[0]) }
                                )
                            }
                            if (pair.size > 1) {
                                Box(modifier = Modifier.weight(1f)) {
                                    FileGridCard(
                                        file = pair[1],
                                        onClick = { viewModel.selectFile(pair[1]) },
                                        onRename = { newName -> viewModel.renameFile(pair[1].id, newName) },
                                        onDelete = { viewModel.deleteFile(pair[1]) }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
