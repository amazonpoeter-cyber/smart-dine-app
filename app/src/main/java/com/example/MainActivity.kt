package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SharedPreferencesMenuStorage
import com.example.data.supabase.SupabaseClient
import com.example.model.AdminTab
import com.example.model.AppLanguage
import com.example.model.StaffPortalRole
import com.example.ui.components.AdminLoginDialog
import com.example.ui.components.AdminTopBar
import com.example.ui.components.ChangePasswordDialog
import com.example.ui.components.CustomerSlideDrawer
import com.example.ui.components.CustomerTopHeader
import com.example.ui.components.EmployeeLoginDialog
import com.example.ui.components.EmployeePortalScreen
import com.example.ui.components.KioskActivationDialog
import com.example.ui.components.KioskExitDialog
import com.example.ui.customer.CustomerScreen
import com.example.ui.kitchen.ChefWorkspaceScreen
import com.example.ui.owner.OwnerDashboardScreen
import com.example.ui.owner.OwnerMenuManagementSection
import com.example.ui.rider.RiderPortalScreen
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SmartDineTheme
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.viewmodel.RestaurantViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartDineTheme {
                SmartDineRootApp()
            }
        }
    }
}

@Composable
fun TasteQrRootApp() = SmartDineRootApp()

@Composable
fun SmartDineRootApp() {
    val context = LocalContext.current.applicationContext
    val storage = remember(context) { SharedPreferencesMenuStorage(context) }
    val supabaseClient = remember { SupabaseClient() }
    val viewModel: RestaurantViewModel = viewModel(
        factory = remember(storage, supabaseClient) {
            RestaurantViewModel.Factory(
                menuStorage = storage,
                credentialStorage = storage,
                supabaseService = supabaseClient
            )
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = remember(uiState.appLanguage) { AppStrings.forLanguage(uiState.appLanguage) }

    var showDrawer by remember { mutableStateOf(false) }
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var showEmployeeLoginDialog by remember { mutableStateOf(false) }
    var showKioskActivationDialog by remember { mutableStateOf(false) }
    var showKioskExitDialog by remember { mutableStateOf(false) }
    var showAdminChangePasswordDialog by remember { mutableStateOf(false) }
    var showEmployeeChangePinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        val message = uiState.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
        viewModel.clearToast()
    }

    if (showDrawer) {
        BackHandler { showDrawer = false }
    } else if (uiState.isAdminAuthenticated) {
        BackHandler {
            if (uiState.adminTab != AdminTab.OWNER_DASHBOARD) {
                viewModel.selectAdminTab(AdminTab.OWNER_DASHBOARD)
            } else {
                viewModel.logoutAdmin()
            }
        }
    } else if (uiState.authenticatedEmployee != null) {
        BackHandler {
            viewModel.logoutEmployeePortal()
        }
    } else if (uiState.isKioskModeActive) {
        // Kiosk Lock: system Back prompts for Kiosk or Admin password instead of exiting
        BackHandler {
            showKioskExitDialog = true
        }
    }

    val loggedInEmployee = uiState.authenticatedEmployee

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(
                            bottom = if (!uiState.isAdminAuthenticated && loggedInEmployee == null && uiState.cartItemCount > 0) 76.dp else 8.dp
                        )
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = NavyPrimary,
                        contentColor = CrispWhite,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isAdminAuthenticated -> {
                        AdminTopBar(
                            activeTab = uiState.adminTab,
                            onSelectTab = viewModel::selectAdminTab,
                            onOpenChangePassword = { showAdminChangePasswordDialog = true },
                            onLogout = viewModel::logoutAdmin
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            AnimatedContent(
                                targetState = uiState.adminTab,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "adminTabTransition"
                            ) { tab ->
                                when (tab) {
                                    AdminTab.OWNER_DASHBOARD -> OwnerDashboardScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                    AdminTab.MENU_MANAGEMENT -> OwnerMenuManagementSection(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }

                    loggedInEmployee != null -> {
                        when (loggedInEmployee.portalRole) {
                            StaffPortalRole.KITCHEN_CHEF -> ChefWorkspaceScreen(
                                employee = loggedInEmployee,
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenChangePassword = { showEmployeeChangePinDialog = true },
                                onLogout = viewModel::logoutEmployeePortal
                            )
                            StaffPortalRole.DELIVERY_RIDER -> RiderPortalScreen(
                                employee = loggedInEmployee,
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenChangePassword = { showEmployeeChangePinDialog = true },
                                onLogout = viewModel::logoutEmployeePortal
                            )
                            StaffPortalRole.GENERAL_STAFF -> {
                                val today = DateUtils.todayIso()
                                EmployeePortalScreen(
                                    employee = loggedInEmployee,
                                    todayRecord = uiState.attendanceRecordFor(loggedInEmployee.id, today),
                                    allRecordsForEmployee = uiState.attendanceRecords.filter { it.staffId == loggedInEmployee.id },
                                    onMarkPresentToday = viewModel::markAuthenticatedEmployeePresent,
                                    onOpenChangePassword = { showEmployeeChangePinDialog = true },
                                    onExitPortal = viewModel::logoutEmployeePortal
                                )
                            }
                        }
                    }

                    else -> {
                        CustomerTopHeader(
                            customerMode = uiState.customerMode,
                            appLanguage = uiState.appLanguage,
                            onToggleLanguage = viewModel::toggleAppLanguage,
                            onOpenMenuDrawer = { showDrawer = true }
                        )
                        val customerDirection = if (uiState.appLanguage == AppLanguage.UR) {
                            LayoutDirection.Rtl
                        } else {
                            LayoutDirection.Ltr
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides customerDirection) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            ) {
                                CustomerScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        // Slide-over Drawer (only when on public Customer or Kiosk screen)
        if (!uiState.isAdminAuthenticated && loggedInEmployee == null) {
            CustomerSlideDrawer(
                visible = showDrawer,
                customerMode = uiState.customerMode,
                activeTab = uiState.customerTab,
                appLanguage = uiState.appLanguage,
                loggedInCustomer = uiState.loggedInCustomer,
                onDismiss = { showDrawer = false },
                onSelectCustomerTab = viewModel::selectCustomerTab,
                onLogoutCustomer = viewModel::logoutCustomer,
                onOpenKioskActivation = { showKioskActivationDialog = true },
                onOpenKioskExit = { showKioskExitDialog = true },
                onOpenEmployeeLogin = { showEmployeeLoginDialog = true },
                onOpenAdminLogin = { showAdminLoginDialog = true }
            )
        }
    }

    if (showKioskActivationDialog) {
        KioskActivationDialog(
            strings = strings,
            onDismiss = { showKioskActivationDialog = false },
            onActivate = viewModel::activateKioskMode
        )
    }

    if (showKioskExitDialog) {
        KioskExitDialog(
            strings = strings,
            onDismiss = { showKioskExitDialog = false },
            onExit = viewModel::exitKioskMode
        )
    }

    if (showEmployeeLoginDialog) {
        EmployeeLoginDialog(
            onDismiss = { showEmployeeLoginDialog = false },
            onLoginWithCnic = viewModel::loginEmployeeWithCnic
        )
    }

    if (showAdminLoginDialog) {
        AdminLoginDialog(
            onDismiss = { showAdminLoginDialog = false },
            onLoginAttempt = viewModel::loginAdmin
        )
    }

    if (showAdminChangePasswordDialog) {
        ChangePasswordDialog(
            title = "Change Admin Password",
            oldLabel = "Old Password",
            newLabel = "New Password",
            confirmLabel = "Confirm New Password",
            isPinKeyboard = false,
            onDismiss = { showAdminChangePasswordDialog = false },
            onSubmit = viewModel::changeAdminPassword
        )
    }

    if (showEmployeeChangePinDialog && loggedInEmployee != null) {
        ChangePasswordDialog(
            title = "Change Password",
            oldLabel = "Old Password",
            newLabel = "New Password",
            confirmLabel = "Confirm New Password",
            isPinKeyboard = false,
            onDismiss = { showEmployeeChangePinDialog = false },
            onSubmit = { oldPass, newPass, confirmPass ->
                viewModel.changeEmployeePassword(loggedInEmployee.id, oldPass, newPass, confirmPass)
            }
        )
    }
}
