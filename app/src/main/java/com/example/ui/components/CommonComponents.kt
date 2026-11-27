package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.model.AdminTab
import com.example.model.AppLanguage
import com.example.model.AttendanceRecord
import com.example.model.AttendanceStatus
import com.example.model.CustomerExperienceMode
import com.example.model.CustomerProfile
import com.example.model.CustomerTab
import com.example.model.MenuArtwork
import com.example.model.StaffMember
import com.example.ui.theme.*
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.util.LocalizedStrings
import com.example.viewmodel.EmployeeLoginResult
import com.example.viewmodel.PasswordChangeResult
import java.text.NumberFormat
import java.util.Locale

fun formatRs(amount: Int): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    return "Rs ${formatter.format(amount)}"
}

fun formatTimer(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

@Composable
fun SmartDineBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.28f).dp))
            .background(CrispWhite),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.smart_dine_icon),
            contentDescription = "Smart Dine Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun CustomerTopHeader(
    customerMode: CustomerExperienceMode,
    appLanguage: AppLanguage,
    onToggleLanguage: () -> Unit,
    onOpenMenuDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = remember(appLanguage) { AppStrings.forLanguage(appLanguage) }
    val subtitle = when (customerMode) {
        CustomerExperienceMode.HOME_DELIVERY -> strings.appTaglineDelivery
        CustomerExperienceMode.KIOSK_MODE -> strings.appTaglineKiosk
    }
    Surface(
        color = CrispWhite,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SmartDineBrandLogo(size = 38.dp)
                    Column {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateSubtext,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Bilingual Language Toggle Pill (Eng / اردو) right next to Hamburger Menu (☰)
                    Surface(
                        onClick = onToggleLanguage,
                        color = SlateSurfaceVariant,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .height(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("language_toggle_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                val active = appLanguage == lang
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (active) NavyPrimary else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) CrispWhite else SlateSubtext,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Clean Hamburger Menu (☰)
                    IconButton(
                        onClick = onOpenMenuDrawer,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("customer_hamburger_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Open menu",
                            tint = NavyPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = SlateBorder, thickness = 1.dp)
        }
    }
}

@Composable
fun CustomerSlideDrawer(
    visible: Boolean,
    customerMode: CustomerExperienceMode,
    activeTab: CustomerTab,
    appLanguage: AppLanguage,
    loggedInCustomer: CustomerProfile?,
    onDismiss: () -> Unit,
    onSelectCustomerTab: (CustomerTab) -> Unit,
    onLogoutCustomer: () -> Unit,
    onOpenKioskActivation: () -> Unit,
    onOpenKioskExit: () -> Unit,
    onOpenEmployeeLogin: () -> Unit,
    onOpenAdminLogin: () -> Unit
) {
    val strings = remember(appLanguage) { AppStrings.forLanguage(appLanguage) }
    val direction = if (appLanguage == AppLanguage.UR) LayoutDirection.Rtl else LayoutDirection.Ltr
    val isKiosk = customerMode == CustomerExperienceMode.KIOSK_MODE

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyPrimary.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .width(296.dp)
                    .fillMaxHeight()
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .navigationBarsPadding()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.drawerTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NavyPrimary
                                )
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn, tint = SlateSubtext)
                                }
                            }

                            HorizontalDivider(color = SlateBorder)

                            if (isKiosk) {
                                Surface(
                                    color = EmeraldSoft,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                                            Text(
                                                text = strings.atRestaurantKioskTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = EmeraldDark,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = strings.kioskModeLockedNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NavyPrimary
                                        )
                                    }
                                }
                            } else {
                                if (loggedInCustomer != null) {
                                    Surface(
                                        color = SlateSurfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(loggedInCustomer.name, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                                Text(
                                                    text = loggedInCustomer.email.ifBlank { loggedInCustomer.phone },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SlateSubtext,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    onLogoutCustomer()
                                                    onDismiss()
                                                }
                                            ) {
                                                Text(strings.logoutLabel, style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                                            }
                                        }
                                    }
                                }

                                CustomerTab.entries.forEach { tab ->
                                    val selected = tab == activeTab
                                    val icon = when (tab) {
                                        CustomerTab.MENU -> Icons.Rounded.RestaurantMenu
                                        CustomerTab.TRACKER -> Icons.Rounded.LocalShipping
                                        CustomerTab.REVIEWS -> Icons.Rounded.StarRate
                                    }
                                    val tabText = strings.tabLabel(tab)
                                    Surface(
                                        onClick = {
                                            onSelectCustomerTab(tab)
                                            onDismiss()
                                        },
                                        color = if (selected) NavyPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("drawer_nav_${tab.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = tabText,
                                                tint = if (selected) CrispWhite else NavyMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = tabText,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (selected) CrispWhite else NavyPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom section: Kiosk Lock / Exit + Staff & Admin Login
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(color = SlateBorder)

                            if (isKiosk) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenKioskExit()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("drawer_exit_kiosk_button")
                                ) {
                                    Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.exitKioskModeTitle, style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                Text(
                                    text = strings.staffManagementSection,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateHint,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenKioskActivation()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("drawer_kiosk_mode_button")
                                ) {
                                    Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.atRestaurantKioskTitle, style = MaterialTheme.typography.labelLarge)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenEmployeeLogin()
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = SlateSurfaceVariant,
                                        contentColor = NavyPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("drawer_employee_login_button")
                                ) {
                                    Icon(Icons.Rounded.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.employeeLogin, style = MaterialTheme.typography.labelLarge)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenAdminLogin()
                                    },
                                    border = BorderStroke(1.dp, SlateBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("drawer_admin_login_button")
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.adminLogin, style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTopBar(
    activeTab: AdminTab,
    onSelectTab: (AdminTab) -> Unit,
    onOpenChangePassword: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NavyPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmartDineBrandLogo(size = 36.dp)
                    Column {
                        Text(
                            text = "Smart Dine Admin",
                            style = MaterialTheme.typography.titleSmall,
                            color = CrispWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Management & Analytics",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateHint
                        )
                    }
                }

                // Top-Right Corner: Change Password + Customer View
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenChangePassword,
                        border = BorderStroke(1.dp, NavyMuted),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_change_password_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = "Change Password",
                            tint = CrispWhite,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Change Password",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite
                        )
                    }

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrispWhite,
                            contentColor = NavyPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Logout to Customer View",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Customer View",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    val bgColor by animateColorAsState(
                        targetValue = if (selected) CrispWhite else NavyElevated,
                        label = "adminTabBg"
                    )
                    val fgColor by animateColorAsState(
                        targetValue = if (selected) NavyPrimary else CrispWhite.copy(alpha = 0.85f),
                        label = "adminTabFg"
                    )
                    val icon = when (tab) {
                        AdminTab.OWNER_DASHBOARD -> Icons.Rounded.Dashboard
                        AdminTab.MENU_MANAGEMENT -> Icons.Rounded.MenuBook
                    }
                    Surface(
                        onClick = { onSelectTab(tab) },
                        color = bgColor,
                        shape = RoundedCornerShape(12.dp),
                        border = if (selected) null else BorderStroke(1.dp, NavyMuted),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = fgColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = fgColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmployeePortalScreen(
    employee: StaffMember,
    todayRecord: AttendanceRecord?,
    allRecordsForEmployee: List<AttendanceRecord>,
    onMarkPresentToday: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onExitPortal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPresentToday = todayRecord?.status == AttendanceStatus.PRESENT
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .navigationBarsPadding()
    ) {
        EmployeeUnifiedTopBar(
            employee = employee,
            todayRecord = todayRecord,
            onMarkPresentToday = onMarkPresentToday,
            onOpenChangePassword = onOpenChangePassword,
            onLogout = onExitPortal
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Staff Duty & Attendance Card
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isPresentToday) EmeraldSoft else AmberSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPresentToday) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = if (isPresentToday) EmeraldSuccess else AmberDark,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isPresentToday) "Checked In • Present" else "Attendance Pending Today",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = if (isPresentToday) {
                                        "Timestamp: ${todayRecord?.checkInTimeLabel ?: "Today"} • ${DateUtils.formatFriendlyDate(DateUtils.todayIso())}"
                                    } else {
                                        "Tap 'Mark Today's Attendance (Present)' above when on shift"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateSubtext
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = SlateBorder)

                    PortalInfoRow("CNIC (Login ID)", employee.cnic)
                    PortalInfoRow("Portal Role", employee.portalRole.label)
                    PortalInfoRow("Shift Timing", employee.shiftLabel)
                    PortalInfoRow("Monthly Day-Off", employee.scheduledDayOff.fullLabel)
                    PortalInfoRow("Phone", employee.phone)
                    PortalInfoRow("Address", employee.address)
                    if (employee.email.isNotBlank()) {
                        PortalInfoRow("Email", employee.email)
                    }
                }
            }

            // Recent Attendance History
            Text("Recent Attendance Logs", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
            allRecordsForEmployee.sortedByDescending { it.dateIso }.forEach { rec ->
                Surface(
                    color = CrispWhite,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(DateUtils.formatFriendlyDate(rec.dateIso), style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                        StatusPill(
                            text = if (rec.checkInTimeLabel != null) "${rec.status.label} • ${rec.checkInTimeLabel}" else rec.status.label,
                            containerColor = EmeraldSoft,
                            contentColor = EmeraldDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
        Text(value, style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
    }
}

@Composable
fun ChangePasswordDialog(
    title: String,
    oldLabel: String,
    newLabel: String,
    confirmLabel: String,
    isPinKeyboard: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (oldVal: String, newVal: String, confirmVal: String) -> PasswordChangeResult
) {
    var oldVal by remember { mutableStateOf("") }
    var newVal by remember { mutableStateOf("") }
    var confirmVal by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NavyPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = CrispWhite)
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = NavyPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val kbType = if (isPinKeyboard) KeyboardType.Password else KeyboardType.Password
                OutlinedTextField(
                    value = oldVal,
                    onValueChange = { oldVal = it; errorMsg = null },
                    label = { Text(oldLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = kbType),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("change_pwd_old_input")
                )
                OutlinedTextField(
                    value = newVal,
                    onValueChange = { newVal = it; errorMsg = null },
                    label = { Text(newLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = kbType),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("change_pwd_new_input")
                )
                OutlinedTextField(
                    value = confirmVal,
                    onValueChange = { confirmVal = it; errorMsg = null },
                    label = { Text(confirmLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = kbType),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("change_pwd_confirm_input")
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (val result = onSubmit(oldVal, newVal, confirmVal)) {
                        is PasswordChangeResult.Success -> onDismiss()
                        is PasswordChangeResult.Error -> errorMsg = result.message
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("change_pwd_submit_button")
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SlateSubtext) }
        }
    )
}

@Composable
fun EmployeeLoginDialog(
    onDismiss: () -> Unit,
    onLoginWithCnic: (cnicInput: String, passwordInput: String) -> EmployeeLoginResult
) {
    var cnicInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun submitLogin() {
        when (val result = onLoginWithCnic(cnicInput, passwordInput)) {
            is EmployeeLoginResult.Success -> onDismiss()
            is EmployeeLoginResult.Error -> errorMsg = result.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        icon = {
            SmartDineBrandLogo(size = 48.dp)
        },
        title = {
            Text("Smart Dine Staff Login", style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Sign in with your CNIC Number and Password to open your Kitchen, Delivery Rider, or Staff Duty portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateSubtext
                )

                OutlinedTextField(
                    value = cnicInput,
                    onValueChange = { cnicInput = it; errorMsg = null },
                    label = { Text("CNIC Number (Login ID)") },
                    placeholder = { Text("34601-1234567-1") },
                    leadingIcon = { Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = SlateSubtext) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    isError = errorMsg != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("employee_cnic_input")
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it; errorMsg = null },
                    label = { Text("Password") },
                    placeholder = { Text("Enter alphanumeric password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = SlateSubtext) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = SlateSubtext
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                    isError = errorMsg != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("employee_password_input")
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                }

                Surface(
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Demo Staff Logins:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Text(
                            text = "Chef Tariq: 34601-1111111-1 / chef123",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = NavyMuted
                        )
                        Text(
                            text = "Rider Bilal: 34601-2222222-2 / rider123",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = NavyMuted
                        )
                        Text(
                            text = "Floor Host: 34601-4444444-4 / staff123",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = NavyMuted
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitLogin() },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("employee_checkin_submit_button")
            ) {
                Text("Login to Portal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SlateSubtext) }
        }
    )
}

@Composable
fun AdminLoginDialog(
    onDismiss: () -> Unit,
    onLoginAttempt: (username: String, password: String) -> Boolean
) {
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val ok = onLoginAttempt(username, password)
        if (ok) {
            onDismiss()
        } else {
            errorMessage = "Incorrect password. Please try again."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        icon = {
            SmartDineBrandLogo(size = 48.dp)
        },
        title = {
            Text(
                text = "Smart Dine Admin Login",
                style = MaterialTheme.typography.titleLarge,
                color = NavyPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Sign in to access Owner Dashboard, Multi-Channel Sales Analytics, Staff Roster, Orders, and Menu Management.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateSubtext
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Username") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = SlateSubtext)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_login_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password / PIN") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = SlateSubtext)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = SlateSubtext
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = errorMessage != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_login_password_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAlert
                    )
                }

                Surface(
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = NavyMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.demo_password_hint),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("admin_login_submit_button")
            ) {
                Text("Unlock Panel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SlateSubtext)
            }
        }
    )
}

@Composable
fun FoodArtwork(
    artwork: MenuArtwork,
    contentDescription: String,
    modifier: Modifier = Modifier,
    imageUrl: String = ""
) {
    val cleanUrl = imageUrl.trim()
    val hasValidRemoteUrl = cleanUrl.startsWith("https://", ignoreCase = true) ||
        cleanUrl.startsWith("http://", ignoreCase = true)

    if (hasValidRemoteUrl) {
        SubcomposeAsyncImage(
            model = cleanUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = {
                PresetFoodArtwork(artwork = artwork, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
            },
            error = {
                PresetFoodArtwork(artwork = artwork, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
            }
        )
    } else {
        PresetFoodArtwork(artwork = artwork, contentDescription = contentDescription, modifier = modifier)
    }
}

@Composable
private fun PresetFoodArtwork(
    artwork: MenuArtwork,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val photoRes = when (artwork) {
        MenuArtwork.SMASH_BURGER -> R.drawable.img_smash_burger
        MenuArtwork.MARGHERITA_PIZZA -> R.drawable.img_margherita_pizza
        MenuArtwork.CHICKEN_BIRYANI -> R.drawable.img_chicken_biryani
        else -> null
    }

    if (photoRes != null) {
        Image(
            painter = painterResource(id = photoRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        val (gradientColors, icon, accentTint) = when (artwork) {
            MenuArtwork.MINT_COOLER_ART -> Triple(
                listOf(Color(0xFF064E3B), Color(0xFF10B981)),
                Icons.Rounded.LocalBar,
                Color(0xFFD1FAE5)
            )
            MenuArtwork.COFFEE_ART -> Triple(
                listOf(Color(0xFF1E293B), Color(0xFF475569)),
                Icons.Rounded.Coffee,
                Color(0xFFFDE68A)
            )
            MenuArtwork.CHOCO_CAKE_ART -> Triple(
                listOf(Color(0xFF271811), Color(0xFF5C3A21)),
                Icons.Rounded.Cake,
                Color(0xFFFED7AA)
            )
            MenuArtwork.CHEESECAKE_ART -> Triple(
                listOf(Color(0xFF7C2D12), Color(0xFFD97706)),
                Icons.Rounded.BakeryDining,
                Color(0xFFFEF3C7)
            )
            MenuArtwork.FRIES_ART -> Triple(
                listOf(Color(0xFF78350F), Color(0xFFF59E0B)),
                Icons.Rounded.Fastfood,
                Color(0xFFFEF3C7)
            )
            else -> Triple(
                listOf(NavyPrimary, NavyMuted),
                Icons.Rounded.RestaurantMenu,
                Color(0xFFE2E8F0)
            )
        }
        Box(
            modifier = modifier.background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accentTint,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
fun KioskActivationDialog(
    strings: LocalizedStrings,
    onDismiss: () -> Unit,
    onActivate: (kioskId: String, password: String) -> PasswordChangeResult
) {
    var kioskId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun submit() {
        when (val res = onActivate(kioskId, password)) {
            is PasswordChangeResult.Success -> onDismiss()
            is PasswordChangeResult.Error -> errorMsg = res.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        icon = {
            SmartDineBrandLogo(size = 48.dp)
        },
        title = {
            Text(strings.kioskActivationTitle, style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.kioskActivationSubtitle, style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
                OutlinedTextField(
                    value = kioskId,
                    onValueChange = { kioskId = it; errorMsg = null },
                    label = { Text(strings.kioskIdLabel) },
                    placeholder = { Text("kiosk01") },
                    leadingIcon = { Icon(Icons.Rounded.TabletAndroid, contentDescription = null, tint = SlateSubtext) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("kiosk_id_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text(strings.kioskPasswordLabel) },
                    placeholder = { Text("Enter Kiosk password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = SlateSubtext) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = SlateSubtext
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = errorMsg != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("kiosk_password_input")
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                }
                Surface(
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = NavyMuted, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Demo: Kiosk ID: kiosk01 • Password: kiosk123",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp).testTag("kiosk_activate_submit_button")
            ) {
                Text(strings.unlockKioskBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelBtn, color = SlateSubtext) }
        }
    )
}

@Composable
fun KioskExitDialog(
    strings: LocalizedStrings,
    onDismiss: () -> Unit,
    onExit: (password: String) -> PasswordChangeResult
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun submit() {
        when (val res = onExit(password)) {
            is PasswordChangeResult.Success -> onDismiss()
            is PasswordChangeResult.Error -> errorMsg = res.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        icon = {
            SmartDineBrandLogo(size = 48.dp)
        },
        title = {
            Text(strings.kioskExitTitle, style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.kioskExitSubtitle, style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text(strings.kioskPasswordLabel) },
                    placeholder = { Text("kiosk123 or Admin password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = SlateSubtext) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = SlateSubtext
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = errorMsg != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("kiosk_exit_password_input")
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp).testTag("kiosk_exit_submit_button")
            ) {
                Text(strings.exitKioskBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelBtn, color = SlateSubtext) }
        }
    )
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
