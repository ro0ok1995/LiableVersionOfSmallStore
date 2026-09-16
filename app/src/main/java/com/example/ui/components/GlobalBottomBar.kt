package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanguageMode
import com.example.model.NavDestination
import com.example.model.StoreStrings

/**
 * Geometric Balance Bottom Navigation Bar:
 * Grid of 5 items, h-20 (78dp), border-t border-neutral-200,
 * Active tab has rounded-full pill background with theme-aware text & icon,
 * Central elevated button has primary background, -top-6 elevation, 62dp circular shadow.
 */
@Composable
fun GlobalBottomBar(
    currentDestination: NavDestination,
    languageMode: LanguageMode,
    onNavigate: (NavDestination) -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = languageMode == LanguageMode.ARABIC
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidth
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // 1. Home
            GeometricBottomNavItem(
                label = if (isArabic) StoreStrings.HOME_AR else StoreStrings.HOME_EN,
                icon = Icons.Default.Home,
                isSelected = currentDestination == NavDestination.HOME,
                testTag = "bottom_nav_home",
                onClick = { onNavigate(NavDestination.HOME) },
                modifier = Modifier.weight(1f)
            )

            // 2. Accounts
            GeometricBottomNavItem(
                label = if (isArabic) StoreStrings.ACCOUNTS_AR else StoreStrings.ACCOUNTS_EN,
                icon = Icons.Default.AccountBalanceWallet,
                isSelected = currentDestination == NavDestination.ACCOUNTS,
                testTag = "bottom_nav_accounts",
                onClick = { onNavigate(NavDestination.ACCOUNTS) },
                modifier = Modifier.weight(1f)
            )

            // 3. Central Prominent Action Button (+) - Visually elevated -top-6
            Box(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-16).dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlusClick)
                        .testTag("bottom_nav_plus_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (isArabic) "إجراء جديد" else "New Action",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 4. Analysis Center
            GeometricBottomNavItem(
                label = if (isArabic) StoreStrings.ANALYSIS_CENTER_AR else StoreStrings.ANALYSIS_CENTER_EN,
                icon = Icons.Default.BarChart,
                isSelected = currentDestination == NavDestination.ANALYSIS_CENTER,
                testTag = "bottom_nav_analysis_center",
                onClick = { onNavigate(NavDestination.ANALYSIS_CENTER) },
                modifier = Modifier.weight(1f)
            )

            // 5. More
            GeometricBottomNavItem(
                label = if (isArabic) StoreStrings.MORE_AR else StoreStrings.MORE_EN,
                icon = Icons.Default.MoreHoriz,
                isSelected = currentDestination == NavDestination.MORE || currentDestination == NavDestination.MORE_SETTINGS,
                testTag = "bottom_nav_more",
                onClick = { onNavigate(NavDestination.MORE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GeometricBottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
