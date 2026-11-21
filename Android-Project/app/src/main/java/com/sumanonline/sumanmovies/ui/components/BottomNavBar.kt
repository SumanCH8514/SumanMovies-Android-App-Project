package com.sumanonline.sumanmovies.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumanonline.sumanmovies.ui.navigation.Screen
import com.sumanonline.sumanmovies.ui.theme.BorderDark
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent

sealed class NavTab(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
) {
    object Home : NavTab(Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home, "Home")
    object Catalog : NavTab(Screen.Catalog.route, Icons.Filled.Search, Icons.Outlined.Search, "Search")
    object Activity : NavTab(Screen.Activity.route, Icons.Filled.History, Icons.Outlined.History, "Activity")
    object Library : NavTab(Screen.Library.route, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "Library")
    object Profile : NavTab(Screen.Profile.route, Icons.Filled.Person, Icons.Outlined.PersonOutline, "Profile")
}

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    downloadsCount: Int = 0
) {
    val tabs = listOf(
        NavTab.Home,
        NavTab.Catalog,
        NavTab.Activity,
        NavTab.Library,
        NavTab.Profile
    )
    val accent = LocalAppAccent.current.color
    val topBorderColor = BorderDark

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = topBorderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(58.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route
                val interactionSource = remember { MutableInteractionSource() }

                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "navPillBg"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    animationSpec = tween(durationMillis = 180),
                    label = "navIconTint"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 1.0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "navIconScale"
                )
                val dotAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "navDotAlpha"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (!isSelected) {
                                onNavigate(tab.route)
                            }
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 50.dp, height = 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(pillBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tab == NavTab.Library && downloadsCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = accent,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (downloadsCount > 99) "99+" else downloadsCount.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .scale(iconScale)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = iconTint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .alpha(dotAlpha)
                            .background(accent)
                    )
                }
            }
        }
    }
}
