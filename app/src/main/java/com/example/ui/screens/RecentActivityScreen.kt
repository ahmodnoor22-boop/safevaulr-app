package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileType
import com.example.ui.SafeVaultViewModel
import com.example.ui.components.FileGridCard
import com.example.ui.components.FileListItem
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeroEnd
import com.example.ui.theme.NavyHeroStart
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextNavyDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentActivityScreen(
    viewModel: SafeVaultViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilterType by viewModel.selectedFilterType.collectAsStateWithLifecycle()
    val selectedTimeFilter by viewModel.selectedTimeFilter.collectAsStateWithLifecycle()
    val filteredFiles by viewModel.filteredFiles.collectAsStateWithLifecycle()

    var showFilterSection by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDeep)
            .statusBarsPadding()
    ) {
        // Top Dark Gradient Header with Search and Filter matching screenshot 3
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NavyDeep,
                            NavyHeroStart,
                            NavyHeroEnd
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Search Input Row with Filter Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search anything here", color = TextMutedGray, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMutedGray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = Color(0x3300E5C9),
                        focusedContainerColor = Color(0xFF13223E),
                        unfocusedContainerColor = Color(0xFF13223E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_text_input")
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Filter toggle icon button matching screenshot 3
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (showFilterSection) CyanAccent else Color(0xFF162848))
                        .border(1.dp, Color(0x4000E5C9), RoundedCornerShape(16.dp))
                        .clickable { showFilterSection = !showFilterSection }
                        .testTag("filter_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter Options",
                        tint = if (showFilterSection) NavyDeep else CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showFilterSection) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                viewModel.setFilterType(null)
                                viewModel.setTimeFilter("all")
                            }
                        ) {
                            Text("Reset", fontSize = 12.sp, color = CyanAccent)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Type", fontSize = 12.sp, color = TextMutedGray)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Type Chips (matching screenshot 3: Image, Video, Audio, File, Document, Note)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            label = "All",
                            isSelected = selectedFilterType == null,
                            onClick = { viewModel.setFilterType(null) }
                        )
                        FilterChip(
                            label = "Image",
                            isSelected = selectedFilterType == FileType.IMAGE,
                            onClick = { viewModel.setFilterType(FileType.IMAGE) }
                        )
                        FilterChip(
                            label = "Video",
                            isSelected = selectedFilterType == FileType.VIDEO,
                            onClick = { viewModel.setFilterType(FileType.VIDEO) }
                        )
                        FilterChip(
                            label = "Audio",
                            isSelected = selectedFilterType == FileType.AUDIO,
                            onClick = { viewModel.setFilterType(FileType.AUDIO) }
                        )
                        FilterChip(
                            label = "Document",
                            isSelected = selectedFilterType == FileType.DOCUMENT,
                            onClick = { viewModel.setFilterType(FileType.DOCUMENT) }
                        )
                        FilterChip(
                            label = "PDF",
                            isSelected = selectedFilterType == FileType.PDF,
                            onClick = { viewModel.setFilterType(FileType.PDF) }
                        )
                        FilterChip(
                            label = "Note",
                            isSelected = selectedFilterType == FileType.NOTE,
                            onClick = { viewModel.setFilterType(FileType.NOTE) }
                        )
                        FilterChip(
                            label = "Archive",
                            isSelected = selectedFilterType == FileType.ARCHIVE,
                            onClick = { viewModel.setFilterType(FileType.ARCHIVE) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Time & Date", fontSize = 12.sp, color = TextMutedGray)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Time Chips (matching screenshot 3: Yesterday, 7 days later, 30 days later)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            label = "Anytime",
                            isSelected = selectedTimeFilter == "all",
                            onClick = { viewModel.setTimeFilter("all") }
                        )
                        FilterChip(
                            label = "Yesterday",
                            isSelected = selectedTimeFilter == "yesterday",
                            onClick = { viewModel.setTimeFilter("yesterday") }
                        )
                        FilterChip(
                            label = "7 days",
                            isSelected = selectedTimeFilter == "7days",
                            onClick = { viewModel.setTimeFilter("7days") }
                        )
                        FilterChip(
                            label = "30 days",
                            isSelected = selectedTimeFilter == "30days",
                            onClick = { viewModel.setTimeFilter("30days") }
                        )
                    }
                }
            }
        }

        // White Results Container Body (matching screenshot 3 bottom section)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Results (${filteredFiles.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavyDark
                        )
                    }
                }

                if (filteredFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextMutedGray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matching files found",
                                    fontSize = 14.sp,
                                    color = TextMutedGray
                                )
                            }
                        }
                    }
                } else {
                    val chunked = filteredFiles.chunked(2)
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

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) CyanAccent else Color(0xFF142442))
            .border(
                1.dp,
                if (isSelected) CyanAccent else Color(0x3344608E),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) NavyDeep else Color(0xFFBAC7DB)
        )
    }
}
