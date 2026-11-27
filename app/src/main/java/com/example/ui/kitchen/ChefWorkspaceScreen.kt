package com.example.ui.kitchen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChefTab
import com.example.model.RestaurantUiState
import com.example.model.StaffMember
import com.example.ui.components.EmployeeUnifiedTopBar
import com.example.ui.owner.OwnerAvailabilitySection
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.RestaurantViewModel

@Composable
fun ChefWorkspaceScreen(
    employee: StaffMember,
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    onOpenChangePassword: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = DateUtils.todayIso()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyPrimary)
    ) {
        EmployeeUnifiedTopBar(
            employee = employee,
            todayRecord = uiState.attendanceRecordFor(employee.id, today),
            onMarkPresentToday = viewModel::markAuthenticatedEmployeePresent,
            onOpenChangePassword = onOpenChangePassword,
            onLogout = onLogout
        )

        Surface(
            color = NavyElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChefTab.entries.forEach { tab ->
                    val selected = uiState.chefTab == tab
                    val icon = when (tab) {
                        ChefTab.KITCHEN_KDS -> Icons.Rounded.Restaurant
                        ChefTab.INVENTORY_STOCK -> Icons.Rounded.Inventory2
                    }
                    Surface(
                        onClick = { viewModel.selectChefTab(tab) },
                        color = if (selected) CrispWhite else NavyPrimary,
                        shape = RoundedCornerShape(12.dp),
                        border = if (selected) null else BorderStroke(1.dp, NavyMuted),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("chef_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = if (selected) NavyPrimary else CrispWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) NavyPrimary else CrispWhite
                            )
                            if (tab == ChefTab.KITCHEN_KDS && uiState.activeKdsOrdersCount > 0) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) NavyPrimary else EmeraldSuccess)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = uiState.activeKdsOrdersCount.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMono,
                                            fontSize = 10.sp
                                        ),
                                        color = CrispWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = uiState.chefTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "chefTabTransition"
            ) { tab ->
                when (tab) {
                    ChefTab.KITCHEN_KDS -> KitchenDisplayScreen(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                    ChefTab.INVENTORY_STOCK -> OwnerAvailabilitySection(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
