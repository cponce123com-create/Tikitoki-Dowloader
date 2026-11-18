package com.tikitoki.downloader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPurple

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun MainNavigationBar(
    selectedTab: Int,
    onMusic: () -> Unit,
    onNavigate: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF09231B))) {
        MiniPlayer(musicScreenOpen = selectedTab == 6, onMusic = onMusic)
        NavigationBar(containerColor = Color(0xFF09231B)) {
            val names =
                listOf(
                    stringResource(R.string.navigation_home),
                    stringResource(R.string.navigation_downloads),
                    stringResource(R.string.navigation_library),
                    stringResource(R.string.navigation_tools),
                    stringResource(R.string.navigation_files),
                )
            val icons =
                listOf(
                    Icons.Outlined.Home,
                    Icons.Outlined.Download,
                    Icons.Outlined.VideoLibrary,
                    Icons.Outlined.Build,
                    Icons.Outlined.Folder,
                )
            names.forEachIndexed { index, name ->
                NavigationBarItem(
                    selected = selectedTab == index || (selectedTab == 6 && index == 3) || (selectedTab == 7 && index == 2),
                    onClick = { onNavigate(index) },
                    icon = { Icon(icons[index], contentDescription = name) },
                    label = {
                        Text(
                            name,
                            maxLines = 1,
                            fontSize = if (index == 2 || index == 3) 10.sp else 12.sp,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = TechPurple,
                            unselectedIconColor = TechMuted,
                            unselectedTextColor = TechMuted,
                        ),
                )
            }
        }
    }
}
