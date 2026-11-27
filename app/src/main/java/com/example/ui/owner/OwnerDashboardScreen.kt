package com.example.ui.owner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OwnerSection
import com.example.model.RestaurantUiState
import com.example.ui.theme.*
import com.example.viewmodel.RestaurantViewModel

@Composable
fun OwnerDashboardScreen(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    if (uiState.ownerSection != OwnerSection.OVERVIEW) {
        BackHandler {
            viewModel.selectOwnerSection(OwnerSection.OVERVIEW)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .navigationBarsPadding()
    ) {
        val expandedSidebar = maxWidth >= 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Sidebar Navigation inside Owner Dashboard
            Surface(
                color = CrispWhite,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .width(if (expandedSidebar) 210.dp else 76.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (expandedSidebar) {
                        Text(
                            text = "OWNER ANALYTICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateSubtext,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    OwnerSection.entries.forEach { section ->
                        val selected = uiState.ownerSection == section
                        val icon = when (section) {
                            OwnerSection.OVERVIEW -> Icons.Rounded.SpaceDashboard
                            OwnerSection.LIVE_ORDERS -> Icons.Rounded.ReceiptLong
                            OwnerSection.STAFF -> Icons.Rounded.Badge
                            OwnerSection.DAILY_SALES -> Icons.Rounded.BarChart
                        }
                        SidebarNavItem(
                            label = if (expandedSidebar) section.label else section.shortLabel,
                            icon = icon,
                            selected = selected,
                            expanded = expandedSidebar,
                            onClick = { viewModel.selectOwnerSection(section) },
                            modifier = Modifier.testTag("owner_nav_${section.name.lowercase()}")
                        )
                    }
                }
            }

            // Main Section Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (uiState.ownerSection) {
                    OwnerSection.OVERVIEW -> OwnerOverviewSection(uiState, viewModel)
                    OwnerSection.LIVE_ORDERS -> OwnerLiveOrdersSection(uiState, viewModel)
                    OwnerSection.STAFF -> OwnerStaffSection(uiState, viewModel)
                    OwnerSection.DAILY_SALES -> OwnerDailySalesSection(uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SidebarNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) NavyPrimary else Color.Transparent
    val fg = if (selected) CrispWhite else SlateSubtext

    if (expanded) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .minimumInteractiveComponentSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .minimumInteractiveComponentSize()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = fg,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
