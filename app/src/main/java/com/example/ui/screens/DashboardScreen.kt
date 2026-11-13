package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BottomTab
import com.example.ui.SafeVaultViewModel
import com.example.ui.ViewMode
import com.example.ui.components.AdPlacement
import com.example.ui.components.AdsterraBanner
import com.example.ui.components.FileGridCard
import com.example.ui.components.FileListItem
import com.example.ui.components.FolderCard
import com.example.ui.components.SafeVaultTopBar
import com.example.ui.components.StorageDonutGauge
import com.example.ui.components.TransferProgressBanner
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@Composable
fun DashboardScreen(
    viewModel: SafeVaultViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.storageStats.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val adConfig by viewModel.adConfig.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val notifications by viewModel.activeNotifications.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDeep)
            .statusBarsPadding()
    ) {
        SafeVaultTopBar(
            onMenuClick = { viewModel.setBottomTab(BottomTab.PROFILE) },
            onRecentClick = { viewModel.setBottomTab(BottomTab.RECENT) },
            onFolderClick = { viewModel.setBottomTab(BottomTab.MY_FILES) },
            onSearchClick = { viewModel.setBottomTab(BottomTab.RECENT) },
            appName = appSettings.appName,
            appLogoStyle = appSettings.appLogoStyle
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    StorageDonutGauge(
                        stats = stats,
                        isSyncing = isSyncing || transferProgress.isActive,
                        onManageClick = { viewModel.setBottomTab(BottomTab.PROFILE) },
                        onSyncClick = { viewModel.triggerCloudSync() }
                    )

                    if (appSettings.announcementEnabled && appSettings.announcementTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF172C4F))
                                .border(1.dp, Color(0x4400E5C9), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, tint = com.example.ui.theme.CyanAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(appSettings.announcementTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (appSettings.announcementMessage.isNotBlank()) {
                                    Text(appSettings.announcementMessage, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (notifications.isNotEmpty()) {
                        val latest = notifications.first()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF142747))
                                .border(1.dp, Color(0x3360A5FA), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(latest.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(latest.message, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                        }
                    }

                    if (transferProgress.isActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TransferProgressBanner(progress = transferProgress)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(Color(0xFFFFFFFF))
                        .padding(top = 20.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cloud Folders (${folders.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextNavyDark
                        )
                        Text(
                            text = "See more",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BluePrimary,
                            modifier = Modifier
                                .clickable { viewModel.setBottomTab(BottomTab.MY_FILES) }
                                .padding(4.dp)
                                .testTag("see_more_folders")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (folders.isEmpty()) {
                        Text(
                            text = "No folders created yet. Tap '+' to create one.",
                            fontSize = 12.sp,
                            color = TextMutedGray,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (adConfig.isEnabled && adConfig.showOnDashboard) {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            AdsterraBanner(config = adConfig, placement = AdPlacement.DASHBOARD_BANNER)
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cloud Backed-Up Files (${files.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextNavyDark
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewMode == ViewMode.GRID) Color.White else Color.Transparent)
                                    .clickable { if (viewMode != ViewMode.GRID) viewModel.toggleViewMode() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Grid View",
                                    tint = if (viewMode == ViewMode.GRID) NavyDeep else TextMutedGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewMode == ViewMode.LIST) Color.White else Color.Transparent)
                                    .clickable { if (viewMode != ViewMode.LIST) viewModel.toggleViewMode() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "List View",
                                    tint = if (viewMode == ViewMode.LIST) NavyDeep else TextMutedGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            if (files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Your SafeVault Cloud is ready. Tap '+' below to upload photos, videos, audio, PDFs, Office docs, archives, or encrypted notes.",
                            fontSize = 13.sp,
                            color = TextMutedGray
                        )
                    }
                }
            } else {
                if (viewMode == ViewMode.LIST) {
                    items(files, key = { it.id }) { file ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 20.dp, vertical = 5.dp)
                        ) {
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
                    val chunked = files.chunked(2)
                    items(chunked) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
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
