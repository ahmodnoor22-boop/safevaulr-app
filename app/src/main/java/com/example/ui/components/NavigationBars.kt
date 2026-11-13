package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.BottomTab
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentGlow
import com.example.ui.theme.GrayBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TextMutedGray

@Composable
fun SafeVaultTopBar(
    onMenuClick: () -> Unit,
    onRecentClick: () -> Unit,
    onFolderClick: () -> Unit,
    onSearchClick: () -> Unit,
    appName: String = "SafeVault",
    appLogoStyle: String = "CYAN_SHIELD",
    modifier: Modifier = Modifier
) {
    val logoRingColor = when (appLogoStyle) {
        "ROYAL_BLUE" -> Color(0xFF60A5FA)
        "EMERALD_VAULT" -> Color(0xFF34D399)
        "AMETHYST_LOCK" -> Color(0xFFA78BFA)
        else -> CyanAccent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .testTag("topbar_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Image(
                painter = painterResource(id = R.drawable.safevault_logo_model_1794540281473),
                contentDescription = "$appName Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, logoRingColor.copy(alpha = 0.7f), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = appName.ifBlank { "SafeVault" },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onRecentClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .testTag("topbar_recent_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Recent Activity",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            IconButton(
                onClick = onFolderClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .testTag("topbar_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "All Folders",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .testTag("topbar_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Files",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
fun SafeVaultBottomNavBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bottom bar container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFFFFFFFF))
                .border(1.dp, GrayBorder, RoundedCornerShape(26.dp))
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x1F000000))
                .padding(vertical = 6.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTabItem(
                    title = "Home",
                    isSelected = selectedTab == BottomTab.HOME,
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    onClick = { onTabSelected(BottomTab.HOME) },
                    testTag = "nav_home"
                )

                NavTabItem(
                    title = "My Files",
                    isSelected = selectedTab == BottomTab.MY_FILES,
                    selectedIcon = Icons.Filled.Folder,
                    unselectedIcon = Icons.Outlined.Folder,
                    onClick = { onTabSelected(BottomTab.MY_FILES) },
                    testTag = "nav_files"
                )

                // Middle space for Floating Upload button
                Spacer(modifier = Modifier.size(48.dp))

                NavTabItem(
                    title = "Recent",
                    isSelected = selectedTab == BottomTab.RECENT,
                    selectedIcon = Icons.Filled.Schedule,
                    unselectedIcon = Icons.Outlined.Schedule,
                    onClick = { onTabSelected(BottomTab.RECENT) },
                    testTag = "nav_recent"
                )

                NavTabItem(
                    title = "Profile",
                    isSelected = selectedTab == BottomTab.PROFILE,
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    onClick = { onTabSelected(BottomTab.PROFILE) },
                    testTag = "nav_profile"
                )
            }
        }

        // Center Floating Action Button (+) with glowing teal background (matches screenshot)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(y = (-20).dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            CyanAccent,
                            Color(0xFF26E0B5),
                            Color(0xFF00C2A0)
                        )
                    )
                )
                .shadow(elevation = 10.dp, shape = CircleShape, spotColor = CyanAccentGlow)
                .clickable { onUploadClick() }
                .testTag("floating_upload_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Upload or Add",
                tint = NavyDeep,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun NavTabItem(
    title: String,
    isSelected: Boolean,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val tint by animateColorAsState(
        targetValue = if (isSelected) NavyDeep else TextMutedGray,
        label = "nav_tint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}
