package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CredentialAndRosterStorage
import com.example.data.InMemoryCredentialStorage
import com.example.data.InMemoryMenuStorage
import com.example.data.MenuStorage
import com.example.data.SharedPreferencesMenuStorage
import com.example.data.SmartDineRepository
import com.example.data.supabase.NoOpSupabaseService
import com.example.data.supabase.SupabaseAuthResult
import com.example.data.supabase.SupabaseAuthSession
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseRealtimeEvent
import com.example.data.supabase.SupabaseService
import com.example.data.supabase.SupabaseSyncStatus
import com.example.model.*
import com.example.util.DateUtils
import com.example.util.WeekdaySlot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest

sealed interface EmployeeLoginResult {
    data class Success(val employee: StaffMember) : EmployeeLoginResult
    data class Error(val message: String) : EmployeeLoginResult
}

sealed interface PasswordChangeResult {
    data class Success(val message: String) : PasswordChangeResult
    data class Error(val message: String) : PasswordChangeResult
}

class RestaurantViewModel(
    private val menuStorage: MenuStorage = InMemoryMenuStorage(),
    private val credentialStorage: CredentialAndRosterStorage = InMemoryCredentialStorage(),
    supabaseService: SupabaseService = NoOpSupabaseService()
) : ViewModel() {

    private val repository = SmartDineRepository(
        menuStorage = menuStorage,
        credentialStorage = credentialStorage,
        supabaseService = supabaseService
    )

    private val _uiState = MutableStateFlow(
        createInitialState(
            persistedMenu = menuStorage.loadMenuItems(),
            persistedStaff = credentialStorage.loadStaff(),
            persistedCustomer = credentialStorage.loadLoggedInCustomer(),
            persistedDeliveryOrders = credentialStorage.loadDeliveryOrders(),
            persistedLanguage = credentialStorage.loadAppLanguage()
        )
    )
    val uiState: StateFlow<RestaurantUiState> = _uiState.asStateFlow()

    private var nextOrderNumber = (_uiState.value.orders.maxOfOrNull { it.orderNumber } ?: 100) + 1
    private var nextReviewId = 101
    private var nextAlertId = 201
    private var nextMenuId = 109
    private var nextStaffId = 1

    init {
        startKitchenTimerLoop()
        startSupabaseSync()
    }

    override fun onCleared() {
        repository.stopRealtimeSync()
        super.onCleared()
    }

    private fun startSupabaseSync() {
        if (!repository.isRemoteEnabled) {
            _uiState.update { it.copy(supabaseSyncStatus = SupabaseSyncStatus.LOCAL_ONLY) }
            return
        }

        _uiState.update { it.copy(supabaseSyncStatus = SupabaseSyncStatus.CONNECTING) }

        viewModelScope.launch {
            repository.realtimeEvents.collect { event ->
                handleRealtimeEvent(event)
            }
        }

        viewModelScope.launch {
            val current = _uiState.value
            val snapshot = repository.syncInitialDataOrSeed(
                localMenu = current.menuItems,
                localOrders = current.orders,
                localReviews = current.reviews,
                localStaff = current.staff,
                localAttendance = current.attendanceRecords,
                localAlerts = current.alerts,
                localSales = current.salesRecords
            )
            _uiState.update { state ->
                val maxOrderNum = snapshot.orders.maxOfOrNull { it.orderNumber } ?: 100
                if (maxOrderNum >= nextOrderNumber) {
                    nextOrderNumber = maxOrderNum + 1
                }
                val nextToken = maxOf(
                    (snapshot.orders.mapNotNull { it.tokenNumber }.maxOrNull() ?: 0) + 1,
                    state.nextKioskTokenNumber
                )
                state.copy(
                    menuItems = snapshot.menuItems,
                    orders = snapshot.orders,
                    reviews = snapshot.reviews,
                    staff = snapshot.staff,
                    attendanceRecords = snapshot.attendanceRecords,
                    alerts = snapshot.alerts,
                    salesRecords = snapshot.salesRecords,
                    nextKioskTokenNumber = nextToken,
                    supabaseSyncStatus = if (snapshot.syncedWithRemote) {
                        SupabaseSyncStatus.LIVE_SYNCED
                    } else {
                        SupabaseSyncStatus.OFFLINE_FALLBACK
                    }
                )
            }
            repository.startRealtimeSync()
        }
    }

    private suspend fun handleRealtimeEvent(event: SupabaseRealtimeEvent) {
        when (event) {
            is SupabaseRealtimeEvent.ConnectionChanged -> {
                _uiState.update { state ->
                    state.copy(
                        supabaseSyncStatus = if (event.isConnected) {
                            SupabaseSyncStatus.LIVE_SYNCED
                        } else {
                            SupabaseSyncStatus.OFFLINE_FALLBACK
                        }
                    )
                }
            }
            is SupabaseRealtimeEvent.MenuUpserted -> {
                _uiState.update { state ->
                    val exists = state.menuItems.any { it.id == event.item.id }
                    val updatedMenu = if (exists) {
                        state.menuItems.map { if (it.id == event.item.id) event.item else it }
                    } else {
                        listOf(event.item) + state.menuItems
                    }
                    val updatedCart = state.cart.mapNotNull { cartItem ->
                        if (cartItem.menuItem.id == event.item.id) {
                            if (event.item.isAvailable) cartItem.copy(menuItem = event.item) else null
                        } else cartItem
                    }
                    menuStorage.saveMenuItems(updatedMenu)
                    state.copy(menuItems = updatedMenu, cart = updatedCart)
                }
            }
            is SupabaseRealtimeEvent.MenuDeleted -> {
                _uiState.update { state ->
                    val updatedMenu = state.menuItems.filterNot { it.id == event.itemId }
                    val updatedCart = state.cart.filterNot { it.menuItem.id == event.itemId }
                    menuStorage.saveMenuItems(updatedMenu)
                    state.copy(menuItems = updatedMenu, cart = updatedCart)
                }
            }
            is SupabaseRealtimeEvent.MenuRefreshRequested -> {
                repository.refreshMenu()?.let { fresh ->
                    _uiState.update { it.copy(menuItems = fresh) }
                }
            }
            is SupabaseRealtimeEvent.OrderUpserted -> {
                _uiState.update { state ->
                    val exists = state.orders.any { it.orderNumber == event.order.orderNumber }
                    val updatedOrders = if (exists) {
                        state.orders.map { if (it.orderNumber == event.order.orderNumber) event.order else it }
                    } else {
                        (listOf(event.order) + state.orders).sortedByDescending { it.orderNumber }
                    }
                    if (event.order.orderNumber >= nextOrderNumber) {
                        nextOrderNumber = event.order.orderNumber + 1
                    }
                    val nextToken = maxOf(
                        (updatedOrders.mapNotNull { it.tokenNumber }.maxOrNull() ?: 15) + 1,
                        state.nextKioskTokenNumber
                    )
                    credentialStorage.saveDeliveryOrders(updatedOrders)
                    state.copy(orders = updatedOrders, nextKioskTokenNumber = nextToken)
                }
            }
            is SupabaseRealtimeEvent.OrderDeleted -> {
                _uiState.update { state ->
                    val updatedOrders = state.orders.filterNot { it.orderNumber == event.orderNumber }
                    credentialStorage.saveDeliveryOrders(updatedOrders)
                    state.copy(orders = updatedOrders)
                }
            }
            is SupabaseRealtimeEvent.OrdersRefreshRequested -> {
                repository.refreshOrders()?.let { fresh ->
                    _uiState.update { state ->
                        val maxOrderNum = fresh.maxOfOrNull { it.orderNumber } ?: 130
                        if (maxOrderNum >= nextOrderNumber) nextOrderNumber = maxOrderNum + 1
                        state.copy(orders = fresh.sortedByDescending { it.orderNumber })
                    }
                }
            }
            is SupabaseRealtimeEvent.ReviewUpserted -> {
                _uiState.update { state ->
                    val exists = state.reviews.any { it.id == event.review.id }
                    val updated = if (exists) {
                        state.reviews.map { if (it.id == event.review.id) event.review else it }
                    } else {
                        listOf(event.review) + state.reviews
                    }
                    state.copy(reviews = updated)
                }
            }
            is SupabaseRealtimeEvent.ReviewsRefreshRequested -> {
                repository.refreshReviews()?.let { fresh ->
                    _uiState.update { it.copy(reviews = fresh) }
                }
            }
            is SupabaseRealtimeEvent.StaffUpserted -> {
                _uiState.update { state ->
                    val exists = state.staff.any { it.id == event.member.id }
                    val updated = if (exists) {
                        state.staff.map { if (it.id == event.member.id) event.member else it }
                    } else {
                        listOf(event.member) + state.staff
                    }
                    credentialStorage.saveStaff(updated)
                    state.copy(staff = updated)
                }
            }
            is SupabaseRealtimeEvent.StaffRefreshRequested -> {
                repository.refreshStaff()?.let { fresh ->
                    _uiState.update { it.copy(staff = fresh) }
                }
            }
            is SupabaseRealtimeEvent.AttendanceUpserted -> {
                _uiState.update { state ->
                    val updated = state.attendanceRecords
                        .filterNot { it.staffId == event.record.staffId && it.dateIso == event.record.dateIso } +
                        event.record
                    state.copy(attendanceRecords = updated)
                }
            }
            is SupabaseRealtimeEvent.AttendanceRefreshRequested -> {
                repository.refreshAttendance()?.let { fresh ->
                    _uiState.update { it.copy(attendanceRecords = fresh) }
                }
            }
            is SupabaseRealtimeEvent.AlertUpserted -> {
                _uiState.update { state ->
                    val exists = state.alerts.any { it.id == event.alert.id }
                    val updated = if (exists) {
                        state.alerts.map { if (it.id == event.alert.id) event.alert else it }
                    } else {
                        listOf(event.alert) + state.alerts
                    }
                    state.copy(alerts = updated)
                }
            }
            is SupabaseRealtimeEvent.AlertsRefreshRequested -> {
                repository.refreshAlerts()?.let { fresh ->
                    _uiState.update { it.copy(alerts = fresh) }
                }
            }
            is SupabaseRealtimeEvent.SalesRecordUpserted -> {
                _uiState.update { state ->
                    val exists = state.salesRecords.any { it.dateIso == event.record.dateIso }
                    val updated = if (exists) {
                        state.salesRecords.map { if (it.dateIso == event.record.dateIso) event.record else it }
                    } else {
                        (listOf(event.record) + state.salesRecords).sortedByDescending { it.dateIso }
                    }
                    state.copy(salesRecords = updated)
                }
            }
            is SupabaseRealtimeEvent.SalesRefreshRequested -> {
                repository.refreshSales()?.let { fresh ->
                    _uiState.update { it.copy(salesRecords = fresh) }
                }
            }
        }
    }

    private fun startKitchenTimerLoop() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                var shouldPersistDelivery = false
                _uiState.update { state ->
                    val updatedOrders = state.orders.map { order ->
                        if (order.status != OrderStatus.SERVED && order.status != OrderStatus.CANCELLED) {
                            val newCancelSecs = if (order.cancelWindowRemainingSeconds > 0) {
                                val dec = order.cancelWindowRemainingSeconds - 1
                                if (dec == 0 && order.isDelivery) shouldPersistDelivery = true
                                dec
                            } else 0
                            order.copy(
                                elapsedSeconds = order.elapsedSeconds + 1,
                                cancelWindowRemainingSeconds = newCancelSecs
                            )
                        } else {
                            order
                        }
                    }
                    state.copy(orders = updatedOrders)
                }
                if (shouldPersistDelivery) {
                    credentialStorage.saveDeliveryOrders(_uiState.value.orders)
                }
            }
        }
    }

    // Admin / Owner Authentication: strictly verified via Supabase Auth + profiles/staff_members role check
    suspend fun loginAdmin(email: String, password: String): PasswordChangeResult {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            return PasswordChangeResult.Error("Please enter a valid Admin email address.")
        }
        if (cleanPassword.isEmpty()) {
            return PasswordChangeResult.Error("Please enter your password.")
        }

        return when (val result = repository.signInAdmin(cleanEmail, cleanPassword)) {
            is SupabaseAuthResult.Error -> PasswordChangeResult.Error(result.message)
            is SupabaseAuthResult.Success -> {
                _uiState.update {
                    it.copy(
                        isAdminAuthenticated = true,
                        authenticatedEmployeeId = null,
                        adminTab = AdminTab.OWNER_DASHBOARD,
                        toastMessage = "Admin Control Panel unlocked"
                    )
                }
                refreshAuthenticatedWorkspace()
                PasswordChangeResult.Success("Admin Control Panel unlocked")
            }
        }
    }

    private fun refreshAuthenticatedWorkspace() {
        viewModelScope.launch {
            coroutineScope {
                val menuDef = async { repository.refreshMenu() }
                val ordersDef = async { repository.refreshOrders() }
                val staffDef = async { repository.refreshStaff() }
                val attDef = async { repository.refreshAttendance() }
                val revDef = async { repository.refreshReviews() }
                val alertsDef = async { repository.refreshAlerts() }
                val salesDef = async { repository.refreshSales() }

                val menu = menuDef.await()
                val orders = ordersDef.await()
                val staff = staffDef.await()
                val attendance = attDef.await()
                val reviews = revDef.await()
                val alerts = alertsDef.await()
                val sales = salesDef.await()

                _uiState.update { state ->
                    val resolvedOrders = orders ?: state.orders
                    val maxOrderNum = resolvedOrders.maxOfOrNull { it.orderNumber } ?: 100
                    if (maxOrderNum >= nextOrderNumber) {
                        nextOrderNumber = maxOrderNum + 1
                    }
                    val nextToken = maxOf(
                        (resolvedOrders.mapNotNull { it.tokenNumber }.maxOrNull() ?: 0) + 1,
                        state.nextKioskTokenNumber
                    )
                    state.copy(
                        menuItems = menu ?: state.menuItems,
                        orders = resolvedOrders,
                        staff = staff ?: state.staff,
                        attendanceRecords = attendance ?: state.attendanceRecords,
                        reviews = reviews ?: state.reviews,
                        alerts = alerts ?: state.alerts,
                        salesRecords = sales ?: state.salesRecords,
                        nextKioskTokenNumber = nextToken,
                        supabaseSyncStatus = SupabaseSyncStatus.LIVE_SYNCED
                    )
                }
            }
        }
    }

    fun logoutAdmin() {
        _uiState.update {
            it.copy(
                isAdminAuthenticated = false,
                toastMessage = "Returned to Customer View"
            )
        }
        viewModelScope.launch {
            repository.signOutAuth()
        }
    }

    suspend fun changeAdminPassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
    ): PasswordChangeResult {
        if (!_uiState.value.isAdminAuthenticated) {
            return PasswordChangeResult.Error("Admin session required.")
        }
        if (oldPassword.trim().isEmpty()) {
            return PasswordChangeResult.Error("Please enter your current password.")
        }
        val cleanNew = newPassword.trim()
        if (cleanNew.length < 8) {
            return PasswordChangeResult.Error("New password must be at least 8 characters.")
        }
        if (cleanNew != confirmPassword.trim()) {
            return PasswordChangeResult.Error("New password and confirmation do not match.")
        }
        val updateResult = repository.updateAdminPassword(cleanNew)
        return if (updateResult.isSuccess) {
            emitToast("Admin password updated in Supabase")
            PasswordChangeResult.Success("Admin password updated. Use your new password on future logins.")
        } else {
            PasswordChangeResult.Error(
                updateResult.exceptionOrNull()?.message ?: "Failed to update admin password."
            )
        }
    }

    // Unified Employee Authentication (CNIC Login ID + Password verified against live Supabase staff_members)
    suspend fun loginEmployeeWithCnic(cnicInput: String, passwordInput: String): EmployeeLoginResult {
        val cleanCnic = cnicInput.trim()
        val cleanCnicKey = normalizeCnicKey(cleanCnic)
        val cleanPassword = passwordInput.trim()
        if (cleanCnicKey.isEmpty()) {
            return EmployeeLoginResult.Error("Please enter your CNIC Number (Login ID).")
        }
        if (cleanPassword.isEmpty()) {
            return EmployeeLoginResult.Error("Please enter your Password.")
        }

        val inputDigest = sha256Bytes(cleanPassword)
        val passHashHex = bytesToHex(inputDigest)

        val matchedMember: StaffMember? = if (repository.isRemoteEnabled) {
            val remoteMatch = repository.authenticateEmployee(cleanCnic, passHashHex).getOrNull()
                ?: repository.authenticateEmployee(cleanCnicKey, passHashHex).getOrNull()
            remoteMatch ?: _uiState.value.staff.firstOrNull { member ->
                normalizeCnicKey(member.cnic) == cleanCnicKey &&
                    MessageDigest.isEqual(inputDigest, hexToBytes(member.passwordHash))
            }
        } else {
            _uiState.value.staff.firstOrNull { member ->
                normalizeCnicKey(member.cnic) == cleanCnicKey &&
                    MessageDigest.isEqual(inputDigest, hexToBytes(member.passwordHash))
            }
        }

        if (matchedMember == null) {
            return EmployeeLoginResult.Error("Invalid CNIC Number or Password.")
        }
        if (matchedMember.isRelieved) {
            return EmployeeLoginResult.Error("This employee profile is relieved/inactive.")
        }

        _uiState.update { current ->
            val mergedStaff = if (current.staff.any { it.id == matchedMember.id }) {
                current.staff.map { if (it.id == matchedMember.id) matchedMember else it }
            } else {
                listOf(matchedMember) + current.staff
            }
            current.copy(
                isAdminAuthenticated = false,
                authenticatedEmployeeId = matchedMember.id,
                staff = mergedStaff,
                chefTab = ChefTab.KITCHEN_KDS,
                riderTab = RiderTab.READY_FOR_PICKUP,
                toastMessage = "Welcome, ${matchedMember.name} (${matchedMember.portalRole.label})"
            )
        }
        if (repository.isRemoteEnabled) {
            refreshAuthenticatedWorkspace()
        }
        return EmployeeLoginResult.Success(matchedMember)
    }

    fun markAuthenticatedEmployeePresent(): Boolean {
        val target = _uiState.value.authenticatedEmployee ?: return false
        val today = DateUtils.todayIso()
        val timeNow = DateUtils.currentTimeLabel()
        val newRecord = AttendanceRecord(
            staffId = target.id,
            dateIso = today,
            status = AttendanceStatus.PRESENT,
            checkInTimeLabel = timeNow,
            isAdminOverride = false
        )

        var syncedMember: StaffMember? = null
        var createdAlert: WhatsAppAlert? = null
        _uiState.update { current ->
            val updatedRecords = current.attendanceRecords
                .filterNot { it.staffId == target.id && it.dateIso == today } + newRecord
            val updatedStaff = current.staff.map { member ->
                if (member.id == target.id) {
                    member.copy(isOnDuty = true).also { syncedMember = it }
                } else member
            }
            credentialStorage.saveStaff(updatedStaff)
            val alert = WhatsAppAlert(
                id = "alert_${nextAlertId++}",
                title = "Staff Check-In • ${target.name}",
                message = "${target.name} (${target.designation}) marked Present at $timeNow.",
                timeLabel = "Just now",
                type = AlertType.NEW_ORDER
            ).also { createdAlert = it }
            current.copy(
                staff = updatedStaff,
                attendanceRecords = updatedRecords,
                alerts = listOf(alert) + current.alerts,
                toastMessage = "${target.name} marked Present at $timeNow"
            )
        }
        viewModelScope.launch {
            repository.syncUpsertAttendance(newRecord)
            syncedMember?.let { repository.syncUpsertStaffMember(it) }
            createdAlert?.let { repository.syncUpsertAlert(it) }
        }
        return true
    }

    fun logoutEmployeePortal() {
        _uiState.update {
            it.copy(
                authenticatedEmployeeId = null,
                toastMessage = "Signed out of Employee Portal"
            )
        }
    }

    fun changeEmployeePassword(
        staffId: String,
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
    ): PasswordChangeResult {
        val stateSnapshot = _uiState.value
        val canEditSelf = stateSnapshot.authenticatedEmployeeId == staffId || stateSnapshot.isAdminAuthenticated
        if (!canEditSelf) {
            return PasswordChangeResult.Error("Unauthorized employee session.")
        }
        val currentMember = stateSnapshot.staff.firstOrNull { it.id == staffId && !it.isRelieved }
            ?: return PasswordChangeResult.Error("Active employee profile not found.")

        val oldDigest = sha256Bytes(oldPassword.trim())
        if (!MessageDigest.isEqual(oldDigest, hexToBytes(currentMember.passwordHash))) {
            return PasswordChangeResult.Error("Old password is incorrect.")
        }
        val cleanNew = newPassword.trim()
        if (cleanNew.length < 4) {
            return PasswordChangeResult.Error("New password must be at least 4 characters.")
        }
        if (cleanNew != confirmPassword.trim()) {
            return PasswordChangeResult.Error("New password and confirmation do not match.")
        }

        val newHashHex = bytesToHex(sha256Bytes(cleanNew))
        var updatedMember: StaffMember? = null
        _uiState.update { state ->
            val updatedStaff = state.staff.map { member ->
                if (member.id == staffId) {
                    member.copy(passwordHash = newHashHex).also { updatedMember = it }
                } else member
            }
            credentialStorage.saveStaff(updatedStaff)
            state.copy(
                staff = updatedStaff,
                toastMessage = "${currentMember.name}'s password updated!"
            )
        }
        updatedMember?.let { member ->
            viewModelScope.launch { repository.syncUpsertStaffMember(member) }
        }
        return PasswordChangeResult.Success("Your password has been updated.")
    }

    fun selectAdminTab(tab: AdminTab) {
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            state.copy(adminTab = tab)
        }
    }

    fun selectChefTab(tab: ChefTab) {
        _uiState.update { state ->
            if (state.authenticatedEmployee?.portalRole != StaffPortalRole.KITCHEN_CHEF) return@update state
            state.copy(chefTab = tab)
        }
    }

    fun selectRiderTab(tab: RiderTab) {
        _uiState.update { state ->
            if (state.authenticatedEmployee?.portalRole != StaffPortalRole.DELIVERY_RIDER) return@update state
            state.copy(riderTab = tab)
        }
    }

    fun dismissRiderPickupBanner() {
        _uiState.update { it.copy(riderPickupBannerMessage = null) }
    }

    fun toggleAppLanguage() {
        val nextLang = if (_uiState.value.appLanguage == AppLanguage.EN) AppLanguage.UR else AppLanguage.EN
        setAppLanguage(nextLang)
    }

    fun setAppLanguage(language: AppLanguage) {
        credentialStorage.saveAppLanguage(language)
        _uiState.update {
            it.copy(
                appLanguage = language,
                toastMessage = if (language == AppLanguage.UR) "زبان اردو میں تبدیل کر دی گئی" else "Language switched to English"
            )
        }
    }

    // "At Home" Customer Authentication: Real Supabase Google OAuth (PKCE) & Real Supabase Email OTP
    fun startGoogleSignIn(): Result<String> {
        return repository.beginGoogleOAuth { verifier ->
            credentialStorage.savePendingOAuthVerifier(verifier)
        }
    }

    suspend fun handleGoogleOAuthCallback(
        code: String?,
        accessTokenFromFragment: String?,
        errorMessage: String?
    ): PasswordChangeResult {
        val persistedVerifier = credentialStorage.loadPendingOAuthVerifier()
        credentialStorage.savePendingOAuthVerifier(null)
        return when (
            val res = repository.completeGoogleOAuth(
                code = code,
                accessTokenFromFragment = accessTokenFromFragment,
                errorMessage = errorMessage,
                persistedVerifier = persistedVerifier
            )
        ) {
            is SupabaseAuthResult.Error -> {
                emitToast(res.message)
                PasswordChangeResult.Error(res.message)
            }
            is SupabaseAuthResult.Success -> finalizeCustomerAuthSession(
                session = res.session,
                fallbackName = "",
                provider = CustomerAuthProvider.GOOGLE
            )
        }
    }

    suspend fun sendCustomerEmailOtp(
        name: String,
        email: String
    ): PasswordChangeResult {
        val cleanEmail = email.trim().lowercase()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return PasswordChangeResult.Error("Please enter a valid email address.")
        }
        val result = repository.sendEmailOtp(cleanEmail, name.trim())
        return if (result.isSuccess) {
            PasswordChangeResult.Success("Verification code sent to $cleanEmail.")
        } else {
            PasswordChangeResult.Error(
                result.exceptionOrNull()?.message ?: "Failed to send verification code."
            )
        }
    }

    suspend fun verifyCustomerEmailOtp(
        name: String,
        email: String,
        otpCode: String
    ): PasswordChangeResult {
        val cleanEmail = email.trim().lowercase()
        val cleanOtp = otpCode.trim()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return PasswordChangeResult.Error("Please enter a valid email address.")
        }
        if (cleanOtp.length < 4) {
            return PasswordChangeResult.Error("Please enter the verification code from your email.")
        }
        return when (val res = repository.verifyEmailOtp(cleanEmail, cleanOtp)) {
            is SupabaseAuthResult.Error -> PasswordChangeResult.Error(res.message)
            is SupabaseAuthResult.Success -> finalizeCustomerAuthSession(
                session = res.session,
                fallbackName = name.trim(),
                provider = CustomerAuthProvider.EMAIL
            )
        }
    }

    private suspend fun finalizeCustomerAuthSession(
        session: SupabaseAuthSession,
        fallbackName: String,
        provider: CustomerAuthProvider
    ): PasswordChangeResult {
        val resolvedName = session.fullName.ifBlank {
            fallbackName.ifBlank {
                session.email.substringBefore("@").ifBlank { "Smart Dine Guest" }
                    .replaceFirstChar { it.uppercase() }
            }
        }
        val isOwnerOrAdmin = session.role.equals("owner", ignoreCase = true) ||
            session.role.equals("admin", ignoreCase = true) ||
            session.email.trim().equals(com.example.data.supabase.SupabaseClient.PRIMARY_OWNER_EMAIL, ignoreCase = true)
        val profile = CustomerProfile(
            id = session.userId,
            name = resolvedName,
            email = session.email,
            avatarUrl = session.avatarUrl,
            provider = provider
        )
        credentialStorage.saveLoggedInCustomer(profile)
        _uiState.update { state ->
            state.copy(
                loggedInCustomer = profile,
                isAdminAuthenticated = isOwnerOrAdmin || state.isAdminAuthenticated,
                authenticatedEmployeeId = if (isOwnerOrAdmin) null else state.authenticatedEmployeeId,
                adminTab = if (isOwnerOrAdmin) AdminTab.OWNER_DASHBOARD else state.adminTab,
                customerMode = CustomerExperienceMode.HOME_DELIVERY,
                toastMessage = when {
                    isOwnerOrAdmin -> "Owner Access unlocked ($resolvedName)"
                    state.appLanguage == AppLanguage.UR -> "خوش آمدید، $resolvedName"
                    else -> "Welcome, $resolvedName!"
                }
            )
        }
        if (isOwnerOrAdmin) {
            refreshAuthenticatedWorkspace()
        } else if (repository.isRemoteEnabled) {
            repository.refreshOrders()?.let { freshOrders ->
                _uiState.update { state ->
                    val maxOrderNum = freshOrders.maxOfOrNull { it.orderNumber } ?: 100
                    if (maxOrderNum >= nextOrderNumber) nextOrderNumber = maxOrderNum + 1
                    state.copy(orders = freshOrders)
                }
            }
        }
        return PasswordChangeResult.Success(
            if (isOwnerOrAdmin) "Owner Access unlocked for ${session.email}" else "Signed in as $resolvedName"
        )
    }

    fun logoutCustomer() {
        credentialStorage.saveLoggedInCustomer(null)
        _uiState.update { state ->
            state.copy(
                loggedInCustomer = null,
                customerMode = CustomerExperienceMode.HOME_DELIVERY,
                cart = if (state.customerMode == CustomerExperienceMode.HOME_DELIVERY) emptyList() else state.cart,
                toastMessage = if (state.appLanguage == AppLanguage.UR) "کسٹمر اکاؤنٹ لاگ آؤٹ ہو گیا" else "Signed out of Customer account"
            )
        }
        viewModelScope.launch {
            repository.signOutAuth()
        }
    }

    // At Restaurant (Kiosk Mode) — directly accessible for customer ordering without an admin password
    fun activateKioskMode() {
        _uiState.update { state ->
            state.copy(
                isAdminAuthenticated = false,
                authenticatedEmployeeId = null,
                customerMode = CustomerExperienceMode.KIOSK_MODE,
                customerTab = CustomerTab.MENU,
                selectedKioskCategory = null,
                activeKioskTokenOrder = null,
                cart = emptyList(),
                toastMessage = if (state.appLanguage == AppLanguage.UR) {
                    "سیلف آرڈر کیوسک موڈ فعال ہو گیا"
                } else {
                    "Self-Ordering Kiosk Mode activated!"
                }
            )
        }
    }

    fun exitKioskMode() {
        _uiState.update { state ->
            state.copy(
                customerMode = CustomerExperienceMode.HOME_DELIVERY,
                customerTab = CustomerTab.MENU,
                selectedKioskCategory = null,
                activeKioskTokenOrder = null,
                cart = emptyList(),
                toastMessage = if (state.appLanguage == AppLanguage.UR) {
                    "ہوم ڈیلیوری موڈ پر واپس آ گئے"
                } else {
                    "Returned to Home Delivery"
                }
            )
        }
    }

    fun selectCustomerTab(tab: CustomerTab) {
        _uiState.update { state ->
            if (state.isKioskModeActive) return@update state
            state.copy(
                customerTab = tab
            )
        }
    }

    fun selectOwnerSection(section: OwnerSection) {
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            state.copy(ownerSection = section)
        }
    }

    fun setOrderChannelFilter(filter: OrderChannelFilter) {
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            state.copy(orderChannelFilter = filter)
        }
    }

    fun selectKioskCategory(category: MenuCategory?) {
        _uiState.update { it.copy(selectedKioskCategory = category) }
    }

    fun resetKioskForNextCustomer() {
        _uiState.update { state ->
            state.copy(
                activeKioskTokenOrder = null,
                cart = emptyList(),
                selectedKioskCategory = null
            )
        }
    }

    fun setHomeMenuSearchQuery(query: String) {
        _uiState.update { it.copy(homeMenuSearchQuery = query) }
    }

    fun setHomeMenuFilter(filter: HomeMenuFilter) {
        _uiState.update { it.copy(homeMenuFilter = filter) }
    }

    fun addToCart(menuItem: MenuItem, quantityDelta: Int = 1, note: String = "") {
        if (!menuItem.isAvailable) {
            emitToast("${menuItem.title} is currently Sold Out")
            return
        }
        _uiState.update { state ->
            val existing = state.cart.firstOrNull { it.menuItem.id == menuItem.id }
            val updatedCart = if (existing != null) {
                state.cart.map {
                    if (it.menuItem.id == menuItem.id) {
                        it.copy(
                            quantity = (it.quantity + quantityDelta).coerceAtLeast(1),
                            note = note.ifBlank { it.note }
                        )
                    } else it
                }
            } else {
                state.cart + CartItem(menuItem = menuItem, quantity = quantityDelta.coerceAtLeast(1), note = note)
            }
            state.copy(cart = updatedCart)
        }
    }

    fun decrementFromCart(menuItemId: String) {
        _uiState.update { state ->
            val updatedCart = state.cart.mapNotNull { item ->
                if (item.menuItem.id == menuItemId) {
                    if (item.quantity > 1) item.copy(quantity = item.quantity - 1) else null
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun placeKioskOrder(paymentMethod: PaymentMethod): KitchenOrder? {
        var created: KitchenOrder? = null
        var createdAlert: WhatsAppAlert? = null
        var updatedTodaySales: DailySalesRecord? = null
        _uiState.update { state ->
            if (!state.isKioskModeActive || state.cart.isEmpty()) return@update state
            val tokenNum = state.nextKioskTokenNumber
            val orderNum = nextOrderNumber++
            val today = DateUtils.todayIso()
            val lineItems = state.cart.map {
                OrderLineItem(
                    menuItemId = it.menuItem.id,
                    title = it.menuItem.title,
                    priceRs = it.menuItem.priceRs,
                    quantity = it.quantity,
                    note = it.note
                )
            }
            val isOnline = paymentMethod == PaymentMethod.ONLINE || paymentMethod == PaymentMethod.ONLINE_DELIVERY
            val newOrder = KitchenOrder(
                orderNumber = orderNum,
                tableCode = "Token #$tokenNum",
                items = lineItems,
                status = OrderStatus.NEW,
                elapsedSeconds = 0,
                orderType = OrderType.KIOSK,
                tokenNumber = tokenNum,
                isPaid = isOnline,
                paymentMethod = paymentMethod,
                placedAtLabel = "Just now",
                orderDateIso = today
            )
            val newAlert = WhatsAppAlert(
                id = "alert_${nextAlertId++}",
                title = "Kiosk Order • Token #$tokenNum",
                message = "Order #$orderNum (Dine-In / Kiosk) • ${lineItems.sumOf { it.quantity }} item(s) • Rs ${newOrder.grandTotalRs} (${paymentMethod.label})",
                timeLabel = "Just now",
                type = AlertType.KIOSK_ORDER
            ).also { createdAlert = it }
            val existingToday = state.salesRecords.firstOrNull { it.dateIso == today } ?: DailySalesRecord(
                dateIso = today,
                grossRevenueRs = 0,
                onlineRevenueRs = 0,
                cashRevenueRs = 0,
                deliveryRevenueRs = 0,
                kioskRevenueRs = 0,
                deliveryOrderCount = 0,
                kioskOrderCount = 0,
                topSellerLabel = lineItems.firstOrNull()?.title ?: "Kitchen Order",
                orderCount = 0,
                hourlyBars = listOf(
                    SalesBarPoint("10a", 0),
                    SalesBarPoint("12p", 0),
                    SalesBarPoint("2p", 0),
                    SalesBarPoint("4p", 0),
                    SalesBarPoint("6p", 0),
                    SalesBarPoint("8p", 0)
                )
            )
            val updatedToday = existingToday.copy(
                grossRevenueRs = existingToday.grossRevenueRs + newOrder.grandTotalRs,
                onlineRevenueRs = existingToday.onlineRevenueRs + if (isOnline) newOrder.grandTotalRs else 0,
                cashRevenueRs = existingToday.cashRevenueRs + if (!isOnline) newOrder.grandTotalRs else 0,
                kioskRevenueRs = existingToday.kioskRevenueRs + newOrder.grandTotalRs,
                orderCount = existingToday.orderCount + 1,
                kioskOrderCount = existingToday.kioskOrderCount + 1,
                hourlyBars = existingToday.hourlyBars.mapIndexed { idx, pt ->
                    if (idx == existingToday.hourlyBars.lastIndex) pt.copy(amountRs = pt.amountRs + newOrder.grandTotalRs) else pt
                }
            ).also { updatedTodaySales = it }
            val updatedSales = listOf(updatedToday) + state.salesRecords.filterNot { it.dateIso == today }
            val updatedOrders = listOf(newOrder) + state.orders
            credentialStorage.saveDeliveryOrders(updatedOrders)
            created = newOrder
            state.copy(
                orders = updatedOrders,
                salesRecords = updatedSales,
                cart = emptyList(),
                alerts = listOf(newAlert) + state.alerts,
                activeKioskTokenOrder = newOrder,
                nextKioskTokenNumber = tokenNum + 1,
                toastMessage = if (state.appLanguage == AppLanguage.UR) {
                    "آرڈر ٹوکن #$tokenNum کچن میں بھیج دیا گیا!"
                } else {
                    "Token #$tokenNum sent to Kitchen!"
                }
            )
        }
        created?.let { order ->
            viewModelScope.launch {
                repository.syncUpsertOrder(order)
                createdAlert?.let { repository.syncUpsertAlert(it) }
                updatedTodaySales?.let { repository.syncUpsertSalesRecord(it) }
            }
        }
        return created
    }

    fun placeHomeDeliveryOrder(
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        gpsCoordinatesLabel: String?,
        gpsMapUrl: String?,
        isGpsFallback: Boolean,
        paymentMethod: PaymentMethod
    ): Boolean {
        val cleanName = customerName.trim()
        val cleanPhone = customerPhone.trim()
        val cleanAddress = deliveryAddress.trim()
        if (cleanName.isEmpty() || cleanPhone.isEmpty() || cleanAddress.isEmpty()) {
            return false
        }
        var placed = false
        var createdOrder: KitchenOrder? = null
        var createdAlert: WhatsAppAlert? = null
        var updatedTodaySales: DailySalesRecord? = null
        _uiState.update { state ->
            if (state.cart.isEmpty()) return@update state
            val orderNum = nextOrderNumber++
            val lineItems = state.cart.map {
                OrderLineItem(
                    menuItemId = it.menuItem.id,
                    title = it.menuItem.title,
                    priceRs = it.menuItem.priceRs,
                    quantity = it.quantity,
                    note = it.note
                )
            }
            val isOnline = paymentMethod == PaymentMethod.ONLINE_DELIVERY || paymentMethod == PaymentMethod.ONLINE
            val newOrder = KitchenOrder(
                orderNumber = orderNum,
                tableCode = "DELIVERY",
                items = lineItems,
                status = OrderStatus.NEW,
                elapsedSeconds = 0,
                orderType = OrderType.DELIVERY,
                customerId = state.loggedInCustomer?.id ?: "",
                customerName = cleanName,
                customerPhone = cleanPhone,
                deliveryAddress = cleanAddress,
                gpsCoordinatesLabel = gpsCoordinatesLabel?.trim()?.takeIf { it.isNotEmpty() },
                gpsMapUrl = gpsMapUrl?.trim()?.takeIf { it.isNotEmpty() },
                isGpsFallback = isGpsFallback,
                cancelWindowRemainingSeconds = 180,
                isPaid = isOnline,
                paymentMethod = paymentMethod,
                placedAtLabel = "Just now",
                orderDateIso = DateUtils.todayIso()
            ).also { createdOrder = it }
            val newAlert = WhatsAppAlert(
                id = "alert_${nextAlertId++}",
                title = "New DELIVERY Order #$orderNum",
                message = "$cleanName ($cleanPhone) • $cleanAddress • Rs ${newOrder.grandTotalRs} (${paymentMethod.label})",
                timeLabel = "Just now",
                type = AlertType.DELIVERY_ORDER
            ).also { createdAlert = it }
            val today = DateUtils.todayIso()
            val existingToday = state.salesRecords.firstOrNull { it.dateIso == today } ?: DailySalesRecord(
                dateIso = today,
                grossRevenueRs = 0,
                onlineRevenueRs = 0,
                cashRevenueRs = 0,
                deliveryRevenueRs = 0,
                kioskRevenueRs = 0,
                deliveryOrderCount = 0,
                kioskOrderCount = 0,
                topSellerLabel = lineItems.firstOrNull()?.title ?: "Delivery Order",
                orderCount = 0,
                hourlyBars = listOf(
                    SalesBarPoint("10a", 0),
                    SalesBarPoint("12p", 0),
                    SalesBarPoint("2p", 0),
                    SalesBarPoint("4p", 0),
                    SalesBarPoint("6p", 0),
                    SalesBarPoint("8p", 0)
                )
            )
            val updatedToday = existingToday.copy(
                grossRevenueRs = existingToday.grossRevenueRs + newOrder.grandTotalRs,
                onlineRevenueRs = existingToday.onlineRevenueRs + if (isOnline) newOrder.grandTotalRs else 0,
                cashRevenueRs = existingToday.cashRevenueRs + if (!isOnline) newOrder.grandTotalRs else 0,
                deliveryRevenueRs = existingToday.deliveryRevenueRs + newOrder.grandTotalRs,
                orderCount = existingToday.orderCount + 1,
                deliveryOrderCount = existingToday.deliveryOrderCount + 1,
                hourlyBars = existingToday.hourlyBars.mapIndexed { idx, pt ->
                    if (idx == existingToday.hourlyBars.lastIndex) pt.copy(amountRs = pt.amountRs + newOrder.grandTotalRs) else pt
                }
            ).also { updatedTodaySales = it }
            val updatedSales = listOf(updatedToday) + state.salesRecords.filterNot { it.dateIso == today }
            val updatedOrders = listOf(newOrder) + state.orders
            credentialStorage.saveDeliveryOrders(updatedOrders)
            placed = true
            state.copy(
                orders = updatedOrders,
                salesRecords = updatedSales,
                cart = emptyList(),
                alerts = listOf(newAlert) + state.alerts,
                customerMode = CustomerExperienceMode.HOME_DELIVERY,
                customerTab = CustomerTab.TRACKER,
                toastMessage = if (state.appLanguage == AppLanguage.UR) {
                    "ڈیلیوری آرڈر #$orderNum کچن میں بھیج دیا گیا!"
                } else {
                    "Delivery Order #$orderNum sent to Kitchen!"
                }
            )
        }
        createdOrder?.let { order ->
            viewModelScope.launch {
                repository.syncUpsertOrder(order)
                createdAlert?.let { repository.syncUpsertAlert(it) }
                updatedTodaySales?.let { repository.syncUpsertSalesRecord(it) }
            }
        }
        return placed
    }

    fun cancelDeliveryOrder(orderNumber: Int): Boolean {
        var cancelled = false
        var cancelledOrder: KitchenOrder? = null
        var cancelAlertObj: WhatsAppAlert? = null
        var updatedTodaySales: DailySalesRecord? = null
        _uiState.update { state ->
            val target = state.orders.firstOrNull { it.orderNumber == orderNumber } ?: return@update state
            if (!target.canCustomerCancel) {
                return@update state.copy(
                    toastMessage = if (state.appLanguage == AppLanguage.UR) {
                        "3 منٹ کی منسوخی کا وقت ختم ہو چکا ہے۔"
                    } else {
                        "3-minute cancellation window has expired."
                    }
                )
            }
            val isOnline = target.paymentMethod == PaymentMethod.ONLINE_DELIVERY || target.paymentMethod == PaymentMethod.ONLINE
            val today = DateUtils.todayIso()
            val updatedOrders = state.orders.map { order ->
                if (order.orderNumber == orderNumber) {
                    order.copy(
                        status = OrderStatus.CANCELLED,
                        cancelWindowRemainingSeconds = 0,
                        completedDateIso = today,
                        completedTimeLabel = DateUtils.currentTimeLabel()
                    ).also { cancelledOrder = it }
                } else order
            }
            val updatedSales = if (target.orderDateIso == today) {
                state.salesRecords.map { rec ->
                    if (rec.dateIso == today) {
                        rec.copy(
                            grossRevenueRs = (rec.grossRevenueRs - target.grandTotalRs).coerceAtLeast(0),
                            onlineRevenueRs = (rec.onlineRevenueRs - if (isOnline) target.grandTotalRs else 0).coerceAtLeast(0),
                            cashRevenueRs = (rec.cashRevenueRs - if (!isOnline) target.grandTotalRs else 0).coerceAtLeast(0),
                            deliveryRevenueRs = (rec.deliveryRevenueRs - target.grandTotalRs).coerceAtLeast(0),
                            orderCount = (rec.orderCount - 1).coerceAtLeast(0),
                            deliveryOrderCount = (rec.deliveryOrderCount - 1).coerceAtLeast(0)
                        ).also { updatedTodaySales = it }
                    } else rec
                }
            } else state.salesRecords

            val cancelAlert = WhatsAppAlert(
                id = "alert_${nextAlertId++}",
                title = "Order #$orderNumber Cancelled",
                message = "Customer cancelled Delivery Order #$orderNumber within 3-min window. Removed from KDS.",
                timeLabel = "Just now",
                type = AlertType.DELIVERY_ORDER
            ).also { cancelAlertObj = it }
            credentialStorage.saveDeliveryOrders(updatedOrders)
            cancelled = true
            state.copy(
                orders = updatedOrders,
                salesRecords = updatedSales,
                alerts = listOf(cancelAlert) + state.alerts,
                toastMessage = if (state.appLanguage == AppLanguage.UR) {
                    "آرڈر #$orderNumber منسوخ کر دیا گیا"
                } else {
                    "Order #$orderNumber cancelled & removed from kitchen"
                }
            )
        }
        cancelledOrder?.let { order ->
            viewModelScope.launch {
                repository.syncUpsertOrder(order)
                cancelAlertObj?.let { repository.syncUpsertAlert(it) }
                updatedTodaySales?.let { repository.syncUpsertSalesRecord(it) }
            }
        }
        return cancelled
    }

    fun advanceOrderStatus(orderNumber: Int) {
        var updatedOrder: KitchenOrder? = null
        var createdRiderAlert: WhatsAppAlert? = null
        _uiState.update { state ->
            val isChef = state.authenticatedEmployee?.portalRole == StaffPortalRole.KITCHEN_CHEF
            if (!isChef) return@update state
            val today = DateUtils.todayIso()
            val timeNow = DateUtils.currentTimeLabel()
            var movedToCompleted = false
            var deliveryMarkedReady = false
            var blockedDeliveryDispatch = false
            var touchedOrder = false
            val updatedOrders = state.orders.map { order ->
                if (order.orderNumber == orderNumber) {
                    touchedOrder = true
                    val nextStatus = when (order.status) {
                        OrderStatus.NEW -> OrderStatus.PREPARING
                        OrderStatus.PREPARING -> {
                            if (order.isDelivery) deliveryMarkedReady = true
                            OrderStatus.READY
                        }
                        OrderStatus.READY -> {
                            if (order.isDelivery) {
                                blockedDeliveryDispatch = true
                                OrderStatus.READY
                            } else {
                                movedToCompleted = true
                                OrderStatus.SERVED
                            }
                        }
                        OrderStatus.ON_THE_WAY -> OrderStatus.ON_THE_WAY
                        OrderStatus.SERVED -> OrderStatus.SERVED
                        OrderStatus.CANCELLED -> OrderStatus.CANCELLED
                    }
                    order.copy(
                        status = nextStatus,
                        completedDateIso = if (nextStatus == OrderStatus.SERVED) today else order.completedDateIso,
                        completedTimeLabel = if (nextStatus == OrderStatus.SERVED) timeNow else order.completedTimeLabel
                    ).also { updatedOrder = it }
                } else order
            }
            if (touchedOrder) {
                credentialStorage.saveDeliveryOrders(updatedOrders)
            }
            val riderBanner = if (deliveryMarkedReady) {
                "Order #$orderNumber is Ready for Pickup & Dispatch!"
            } else {
                state.riderPickupBannerMessage
            }
            val updatedAlerts = if (deliveryMarkedReady) {
                val riderAlert = WhatsAppAlert(
                    id = "alert_${nextAlertId++}",
                    title = "Rider Pickup Ready • #$orderNumber",
                    message = "Order #$orderNumber is Ready for Pickup & Dispatch!",
                    timeLabel = "Just now",
                    type = AlertType.DELIVERY_ORDER
                ).also { createdRiderAlert = it }
                listOf(riderAlert) + state.alerts
            } else {
                state.alerts
            }
            state.copy(
                orders = updatedOrders,
                alerts = updatedAlerts,
                riderPickupBannerMessage = riderBanner,
                toastMessage = when {
                    deliveryMarkedReady -> "Order #$orderNumber Ready — Rider notified for pickup!"
                    blockedDeliveryDispatch -> "Order #$orderNumber is awaiting Rider pickup in Rider Portal."
                    movedToCompleted -> "Order #$orderNumber moved to Completed ($timeNow)"
                    else -> state.toastMessage
                }
            )
        }
        updatedOrder?.let { order ->
            viewModelScope.launch {
                repository.syncUpsertOrder(order)
                createdRiderAlert?.let { repository.syncUpsertAlert(it) }
            }
        }
    }

    fun riderPickUpOrder(orderNumber: Int): Boolean {
        val rider = _uiState.value.authenticatedEmployee
        if (rider?.portalRole != StaffPortalRole.DELIVERY_RIDER) return false
        var pickedUp = false
        var updatedOrder: KitchenOrder? = null
        _uiState.update { state ->
            val updatedOrders = state.orders.map { order ->
                if (order.orderNumber == orderNumber && order.isDelivery && order.status == OrderStatus.READY) {
                    pickedUp = true
                    order.copy(
                        status = OrderStatus.ON_THE_WAY,
                        assignedRiderId = rider.id,
                        assignedRiderName = rider.name
                    ).also { updatedOrder = it }
                } else order
            }
            if (!pickedUp) return@update state
            credentialStorage.saveDeliveryOrders(updatedOrders)
            state.copy(
                orders = updatedOrders,
                riderPickupBannerMessage = if (state.riderPickupBannerMessage?.contains("#$orderNumber") == true) {
                    null
                } else {
                    state.riderPickupBannerMessage
                },
                toastMessage = "Order #$orderNumber picked up — Out for Delivery!"
            )
        }
        updatedOrder?.let { order ->
            viewModelScope.launch { repository.syncUpsertOrder(order) }
        }
        return pickedUp
    }

    fun riderMarkDelivered(orderNumber: Int): Boolean {
        val rider = _uiState.value.authenticatedEmployee
        if (rider?.portalRole != StaffPortalRole.DELIVERY_RIDER) return false
        var delivered = false
        var updatedOrder: KitchenOrder? = null
        _uiState.update { state ->
            val today = DateUtils.todayIso()
            val timeNow = DateUtils.currentTimeLabel()
            val updatedOrders = state.orders.map { order ->
                if (order.orderNumber == orderNumber && order.isDelivery && order.status == OrderStatus.ON_THE_WAY) {
                    delivered = true
                    order.copy(
                        status = OrderStatus.SERVED,
                        isPaid = true,
                        completedDateIso = today,
                        completedTimeLabel = timeNow,
                        assignedRiderId = order.assignedRiderId ?: rider.id,
                        assignedRiderName = order.assignedRiderName ?: rider.name
                    ).also { updatedOrder = it }
                } else order
            }
            if (!delivered) return@update state
            credentialStorage.saveDeliveryOrders(updatedOrders)
            state.copy(
                orders = updatedOrders,
                toastMessage = "Order #$orderNumber marked as Delivered ($timeNow)"
            )
        }
        updatedOrder?.let { order ->
            viewModelScope.launch { repository.syncUpsertOrder(order) }
        }
        return delivered
    }

    fun submitReview(
        authorName: String,
        rating: Int,
        tags: List<String>,
        comment: String,
        publishToSite: Boolean
    ) {
        var createdReview: ReviewEntry? = null
        _uiState.update { state ->
            val fallbackAuthor = state.loggedInCustomer?.name
                ?: if (state.isKioskModeActive) "Walk-In Kiosk Guest" else "Home Delivery Guest"
            val channelCode = if (state.isKioskModeActive) "KIOSK" else "DELIVERY"
            val entry = ReviewEntry(
                id = "rev_${nextReviewId++}",
                authorName = authorName.trim().ifBlank { fallbackAuthor },
                tableCode = channelCode,
                rating = rating.coerceIn(1, 5),
                tags = tags,
                comment = comment.trim(),
                timestampLabel = "Just now",
                isPublishedToSite = publishToSite
            ).also { createdReview = it }
            state.copy(
                reviews = listOf(entry) + state.reviews,
                toastMessage = if (publishToSite) {
                    "Published live to Website Reviews!"
                } else {
                    "Thank you for your feedback!"
                }
            )
        }
        createdReview?.let { review ->
            viewModelScope.launch { repository.syncUpsertReview(review) }
        }
    }

    fun publishExistingReviewToSite(reviewId: String) {
        var updatedReview: ReviewEntry? = null
        _uiState.update { state ->
            state.copy(
                reviews = state.reviews.map {
                    if (it.id == reviewId) it.copy(isPublishedToSite = true).also { updatedReview = it } else it
                },
                toastMessage = "Review published live to Website Reviews!"
            )
        }
        updatedReview?.let { review ->
            viewModelScope.launch { repository.syncUpsertReview(review) }
        }
    }

    // Sales Filter Controls
    fun setSalesPeriodFilter(period: SalesPeriodFilter, customDateIso: String? = null, customMonthIso: String? = null) {
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            state.copy(
                salesPeriodFilter = period,
                salesCustomDateIso = customDateIso ?: state.salesCustomDateIso,
                salesCustomMonthIso = customMonthIso ?: state.salesCustomMonthIso
            )
        }
    }

    // Completed Order History Filter Controls (Admin Dashboard + Chef KDS)
    fun setCompletedOrderDateFilter(filter: OrderHistoryDateFilter, customDateIso: String? = null) {
        _uiState.update { state ->
            val canViewHistory = state.isAdminAuthenticated ||
                state.authenticatedEmployee?.portalRole == StaffPortalRole.KITCHEN_CHEF
            if (!canViewHistory) return@update state
            state.copy(
                completedOrderDateFilter = filter,
                completedOrderCustomDateIso = customDateIso ?: state.completedOrderCustomDateIso
            )
        }
    }

    fun setCompletedOrderSearchQuery(query: String) {
        _uiState.update { state ->
            val canViewHistory = state.isAdminAuthenticated ||
                state.authenticatedEmployee?.portalRole == StaffPortalRole.KITCHEN_CHEF
            if (!canViewHistory) return@update state
            state.copy(completedOrderSearchQuery = query)
        }
    }

    // Staff Lifecycle (Add / Edit / Relieve), Day-Off Roster & Attendance Override
    fun addEmployee(
        name: String,
        cnic: String,
        password: String,
        phone: String,
        designation: String,
        portalRole: StaffPortalRole,
        address: String,
        email: String,
        shiftLabel: String,
        scheduledDayOff: WeekdaySlot
    ): Boolean {
        val cleanName = name.trim()
        val cleanCnic = cnic.trim()
        val cleanPass = password.trim()
        val cleanPhone = phone.trim()
        val cleanDesignation = designation.trim()
        val cleanAddress = address.trim()
        if (cleanName.isBlank() ||
            cleanCnic.isBlank() ||
            cleanPass.length < 4 ||
            cleanPhone.isBlank() ||
            cleanDesignation.isBlank() ||
            cleanAddress.isBlank()
        ) {
            return false
        }
        val stateSnapshot = _uiState.value
        if (!stateSnapshot.isAdminAuthenticated) return false
        val cnicKey = normalizeCnicKey(cleanCnic)
        if (stateSnapshot.staff.any { normalizeCnicKey(it.cnic) == cnicKey }) {
            emitToast("An employee with CNIC $cleanCnic already exists.")
            return false
        }

        val passHash = bytesToHex(sha256Bytes(cleanPass))
        var createdMember: StaffMember? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            val newId = "st_${nextStaffId++}_${System.currentTimeMillis() % 1000}"
            val member = StaffMember(
                id = newId,
                name = cleanName,
                cnic = cleanCnic,
                passwordHash = passHash,
                designation = cleanDesignation,
                portalRole = portalRole,
                phone = cleanPhone,
                address = cleanAddress,
                email = email.trim(),
                shiftLabel = shiftLabel.trim().ifBlank { "10:00 - 19:00" },
                isOnDuty = false,
                isRelieved = false,
                scheduledDayOff = scheduledDayOff
            ).also { createdMember = it }
            val updatedStaff = listOf(member) + state.staff
            credentialStorage.saveStaff(updatedStaff)
            state.copy(
                staff = updatedStaff,
                toastMessage = "Onboarded ${member.name} (${member.portalRole.label})"
            )
        }
        createdMember?.let { member ->
            viewModelScope.launch { repository.syncUpsertStaffMember(member) }
        }
        return true
    }

    fun updateEmployee(
        staffId: String,
        name: String,
        cnic: String,
        password: String,
        phone: String,
        designation: String,
        portalRole: StaffPortalRole,
        address: String,
        email: String,
        shiftLabel: String,
        scheduledDayOff: WeekdaySlot
    ): Boolean {
        val cleanName = name.trim()
        val cleanCnic = cnic.trim()
        val cleanPass = password.trim()
        val cleanPhone = phone.trim()
        val cleanDesignation = designation.trim()
        val cleanAddress = address.trim()
        if (cleanName.isBlank() ||
            cleanCnic.isBlank() ||
            cleanPhone.isBlank() ||
            cleanDesignation.isBlank() ||
            cleanAddress.isBlank()
        ) {
            return false
        }
        if (cleanPass.isNotEmpty() && cleanPass.length < 4) {
            emitToast("Password must be at least 4 characters.")
            return false
        }
        val stateSnapshot = _uiState.value
        if (!stateSnapshot.isAdminAuthenticated) return false
        val cnicKey = normalizeCnicKey(cleanCnic)
        if (stateSnapshot.staff.any { it.id != staffId && normalizeCnicKey(it.cnic) == cnicKey }) {
            emitToast("Another employee already uses CNIC $cleanCnic.")
            return false
        }

        val updatedHash = if (cleanPass.isNotEmpty()) bytesToHex(sha256Bytes(cleanPass)) else null
        var updatedMember: StaffMember? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            val updatedStaff = state.staff.map { member ->
                if (member.id == staffId) {
                    member.copy(
                        name = cleanName,
                        cnic = cleanCnic,
                        passwordHash = updatedHash ?: member.passwordHash,
                        phone = cleanPhone,
                        designation = cleanDesignation,
                        portalRole = portalRole,
                        address = cleanAddress,
                        email = email.trim(),
                        shiftLabel = shiftLabel.trim().ifBlank { member.shiftLabel },
                        scheduledDayOff = scheduledDayOff
                    ).also { updatedMember = it }
                } else member
            }
            credentialStorage.saveStaff(updatedStaff)
            state.copy(
                staff = updatedStaff,
                toastMessage = "Updated profile for $cleanName"
            )
        }
        updatedMember?.let { member ->
            viewModelScope.launch { repository.syncUpsertStaffMember(member) }
        }
        return true
    }

    fun relieveEmployee(staffId: String) {
        var relievedMember: StaffMember? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            var relievedName = ""
            val updatedStaff = state.staff.map { member ->
                if (member.id == staffId) {
                    relievedName = member.name
                    member.copy(isRelieved = true, isOnDuty = false).also { relievedMember = it }
                } else member
            }
            credentialStorage.saveStaff(updatedStaff)
            state.copy(
                staff = updatedStaff,
                toastMessage = "$relievedName marked Relieved (attendance history preserved)"
            )
        }
        relievedMember?.let { member ->
            viewModelScope.launch { repository.syncUpsertStaffMember(member) }
        }
    }

    fun setEmployeeDayOff(staffId: String, dayOff: WeekdaySlot) {
        var updatedMember: StaffMember? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            val updatedStaff = state.staff.map { member ->
                if (member.id == staffId) {
                    member.copy(scheduledDayOff = dayOff).also { updatedMember = it }
                } else member
            }
            credentialStorage.saveStaff(updatedStaff)
            state.copy(
                staff = updatedStaff,
                toastMessage = "Monthly Day-Off updated to ${dayOff.fullLabel}"
            )
        }
        updatedMember?.let { member ->
            viewModelScope.launch { repository.syncUpsertStaffMember(member) }
        }
    }

    fun setAttendanceDateFilter(dateIso: String) {
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            state.copy(attendanceDateIso = dateIso)
        }
    }

    fun setAttendanceStatusFilter(filter: AttendanceFilterStatus) {
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            state.copy(attendanceStatusFilter = filter)
        }
    }

    fun overrideAttendance(staffId: String, dateIso: String, newStatus: AttendanceStatus) {
        var savedRecord: AttendanceRecord? = null
        var updatedMember: StaffMember? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            val staffName = state.staff.firstOrNull { it.id == staffId }?.name ?: "Staff"
            val existing = state.attendanceRecords.firstOrNull { it.staffId == staffId && it.dateIso == dateIso }
            val record = AttendanceRecord(
                staffId = staffId,
                dateIso = dateIso,
                status = newStatus,
                checkInTimeLabel = if (newStatus == AttendanceStatus.PRESENT) {
                    existing?.checkInTimeLabel ?: DateUtils.currentTimeLabel()
                } else null,
                isAdminOverride = true
            ).also { savedRecord = it }
            val updatedRecords = state.attendanceRecords
                .filterNot { it.staffId == staffId && it.dateIso == dateIso } + record
            val updatedStaff = if (dateIso == DateUtils.todayIso()) {
                state.staff.map { member ->
                    if (member.id == staffId) {
                        member.copy(isOnDuty = newStatus == AttendanceStatus.PRESENT).also { updatedMember = it }
                    } else member
                }
            } else state.staff
            credentialStorage.saveStaff(updatedStaff)

            state.copy(
                staff = updatedStaff,
                attendanceRecords = updatedRecords,
                toastMessage = "Updated $staffName ($dateIso) → ${newStatus.label}"
            )
        }
        viewModelScope.launch {
            savedRecord?.let { repository.syncUpsertAttendance(it) }
            updatedMember?.let { repository.syncUpsertStaffMember(it) }
        }
    }

    // Admin Menu CRUD
    fun addMenuItem(
        title: String,
        description: String,
        category: MenuCategory,
        priceRs: Int,
        prepTimeRange: String,
        spiceLevel: SpiceLevel,
        artwork: MenuArtwork = MenuArtwork.CUSTOM_DISH,
        imageUrl: String = ""
    ) {
        if (title.isBlank()) return
        var createdItem: MenuItem? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            val formattedPrep = normalizePrepTime(prepTimeRange)
            val safeUrl = SharedPreferencesMenuStorage.sanitizeImageUrl(imageUrl)
            val newItem = MenuItem(
                id = "item_${nextMenuId++}_${System.currentTimeMillis() % 10000}",
                title = title.trim(),
                description = description.trim().ifBlank { "Chef's freshly prepared signature dish." },
                category = category,
                priceRs = priceRs.coerceAtLeast(50),
                prepTimeRange = formattedPrep,
                spiceLevel = spiceLevel,
                artwork = artwork,
                imageUrl = safeUrl,
                isAvailable = true
            ).also { createdItem = it }
            val updatedMenu = listOf(newItem) + state.menuItems
            menuStorage.saveMenuItems(updatedMenu)
            state.copy(
                menuItems = updatedMenu,
                toastMessage = "Added ${newItem.title} to menu"
            )
        }
        createdItem?.let { item ->
            viewModelScope.launch { repository.syncUpsertMenuItem(item) }
        }
    }

    fun updateMenuItem(
        itemId: String,
        title: String,
        description: String,
        category: MenuCategory,
        priceRs: Int,
        prepTimeRange: String,
        spiceLevel: SpiceLevel,
        artwork: MenuArtwork,
        imageUrl: String
    ) {
        if (title.isBlank()) return
        var updatedItem: MenuItem? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            val safePrice = priceRs.coerceAtLeast(50)
            val formattedPrep = normalizePrepTime(prepTimeRange)
            val safeUrl = SharedPreferencesMenuStorage.sanitizeImageUrl(imageUrl)
            val updatedMenu = state.menuItems.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        title = title.trim(),
                        description = description.trim().ifBlank { item.description },
                        category = category,
                        priceRs = safePrice,
                        prepTimeRange = formattedPrep,
                        spiceLevel = spiceLevel,
                        artwork = artwork,
                        imageUrl = safeUrl
                    ).also { updatedItem = it }
                } else item
            }
            val updatedCart = state.cart.map { cartItem ->
                val updated = updatedMenu.firstOrNull { it.id == cartItem.menuItem.id }
                if (updated != null) cartItem.copy(menuItem = updated) else cartItem
            }
            menuStorage.saveMenuItems(updatedMenu)
            state.copy(
                menuItems = updatedMenu,
                cart = updatedCart,
                toastMessage = "Updated ${title.trim()}"
            )
        }
        updatedItem?.let { item ->
            viewModelScope.launch { repository.syncUpsertMenuItem(item) }
        }
    }

    fun deleteMenuItem(itemId: String) {
        var didDelete = false
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            val deleted = state.menuItems.firstOrNull { it.id == itemId } ?: return@update state
            didDelete = true
            val updatedMenu = state.menuItems.filterNot { it.id == itemId }
            val updatedCart = state.cart.filterNot { it.menuItem.id == itemId }
            menuStorage.saveMenuItems(updatedMenu)
            state.copy(
                menuItems = updatedMenu,
                cart = updatedCart,
                toastMessage = "Deleted ${deleted.title} from menu"
            )
        }
        if (didDelete) {
            viewModelScope.launch { repository.syncDeleteMenuItem(itemId) }
        }
    }

    fun toggleItemAvailability(itemId: String) {
        var updatedItem: MenuItem? = null
        _uiState.update { state ->
            val canToggleStock = state.isAdminAuthenticated ||
                state.authenticatedEmployee?.portalRole == StaffPortalRole.KITCHEN_CHEF
            if (!canToggleStock) return@update state
            var toggledTitle = ""
            var nowAvailable = true
            val updatedMenu = state.menuItems.map { item ->
                if (item.id == itemId) {
                    nowAvailable = !item.isAvailable
                    toggledTitle = item.title
                    item.copy(isAvailable = nowAvailable).also { updatedItem = it }
                } else item
            }
            val updatedCart = if (!nowAvailable) {
                state.cart.filterNot { it.menuItem.id == itemId }
            } else state.cart
            menuStorage.saveMenuItems(updatedMenu)
            state.copy(
                menuItems = updatedMenu,
                cart = updatedCart,
                toastMessage = "$toggledTitle marked as ${if (nowAvailable) "Available" else "Sold Out"}"
            )
        }
        updatedItem?.let { item ->
            viewModelScope.launch { repository.syncUpsertMenuItem(item) }
        }
    }

    fun acknowledgeAlert(alertId: String) {
        var updatedAlert: WhatsAppAlert? = null
        _uiState.update { state ->
            if (!state.isAdminAuthenticated) return@update state
            state.copy(
                alerts = state.alerts.map {
                    if (it.id == alertId) it.copy(isAcknowledged = true).also { updatedAlert = it } else it
                }
            )
        }
        updatedAlert?.let { alert ->
            viewModelScope.launch { repository.syncUpsertAlert(alert) }
        }
    }

    fun emitToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun normalizeCnicKey(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (digits.length >= 13) digits else trimmed.lowercase()
    }

    private fun normalizePrepTime(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "15 min"
        return if (trimmed.all { it.isDigit() }) "$trimmed min" else trimmed
    }

    private fun sha256Bytes(value: String): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    class Factory(
        private val menuStorage: MenuStorage,
        private val credentialStorage: CredentialAndRosterStorage,
        private val supabaseService: SupabaseService = SupabaseClient()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RestaurantViewModel(
                menuStorage = menuStorage,
                credentialStorage = credentialStorage,
                supabaseService = supabaseService
            ) as T
        }
    }

    private fun createInitialState(
        persistedMenu: List<MenuItem>?,
        persistedStaff: List<StaffMember>?,
        persistedCustomer: CustomerProfile?,
        persistedDeliveryOrders: List<KitchenOrder>?,
        persistedLanguage: AppLanguage?
    ): RestaurantUiState {
        val defaultCatalogMenu = persistedMenu ?: listOf(
            MenuItem(
                id = "item_1",
                title = "Smash Burger",
                description = "Double seared beef patties, aged cheddar, caramelized onions, house pickles & brioche bun.",
                category = MenuCategory.MAINS,
                priceRs = 890,
                prepTimeRange = "15-20 min",
                spiceLevel = SpiceLevel.MILD,
                artwork = MenuArtwork.SMASH_BURGER
            ),
            MenuItem(
                id = "item_2",
                title = "Margherita Pizza",
                description = "Wood-fired sourdough crust, San Marzano tomato, buffalo mozzarella & sweet basil.",
                category = MenuCategory.MAINS,
                priceRs = 1290,
                prepTimeRange = "18-22 min",
                spiceLevel = SpiceLevel.MILD,
                artwork = MenuArtwork.MARGHERITA_PIZZA
            ),
            MenuItem(
                id = "item_3",
                title = "Chicken Biryani",
                description = "Fragrant saffron basmati rice slow-cooked with spiced tender chicken, crispy shallots & mint raita.",
                category = MenuCategory.MAINS,
                priceRs = 790,
                prepTimeRange = "20-25 min",
                spiceLevel = SpiceLevel.SPICY,
                artwork = MenuArtwork.CHICKEN_BIRYANI
            ),
            MenuItem(
                id = "item_4",
                title = "Truffle Parm Fries",
                description = "Crispy hand-cut shoestring fries tossed in white truffle oil, aged parmesan & rosemary sea salt.",
                category = MenuCategory.MAINS,
                priceRs = 490,
                prepTimeRange = "10-12 min",
                spiceLevel = SpiceLevel.MILD,
                artwork = MenuArtwork.FRIES_ART
            ),
            MenuItem(
                id = "item_5",
                title = "Mint Cooler",
                description = "Crushed garden mint, fresh key lime zest, sparkling spring water & raw cane syrup over pebble ice.",
                category = MenuCategory.DRINKS,
                priceRs = 390,
                prepTimeRange = "5-8 min",
                spiceLevel = SpiceLevel.REFRESHING,
                artwork = MenuArtwork.MINT_COOLER_ART
            ),
            MenuItem(
                id = "item_6",
                title = "Iced Caramel Latte",
                description = "Double-shot single-origin espresso over velvety chilled milk & house burnt caramel.",
                category = MenuCategory.DRINKS,
                priceRs = 460,
                prepTimeRange = "5-7 min",
                spiceLevel = SpiceLevel.SWEET,
                artwork = MenuArtwork.COFFEE_ART
            ),
            MenuItem(
                id = "item_7",
                title = "Choco Cake",
                description = "Warm dark Belgian chocolate fudge slice with glossy ganache & flaky sea salt.",
                category = MenuCategory.DESSERTS,
                priceRs = 550,
                prepTimeRange = "8-10 min",
                spiceLevel = SpiceLevel.SWEET,
                artwork = MenuArtwork.CHOCO_CAKE_ART
            ),
            MenuItem(
                id = "item_8",
                title = "Basque Cheesecake",
                description = "Caramelized crust burnt cheesecake with creamy vanilla bean center & berry coulis.",
                category = MenuCategory.DESSERTS,
                priceRs = 680,
                prepTimeRange = "6-8 min",
                spiceLevel = SpiceLevel.SWEET,
                artwork = MenuArtwork.CHEESECAKE_ART
            )
        )

        val persistedOrders = persistedDeliveryOrders.orEmpty()
            .distinctBy { it.orderNumber }
            .sortedByDescending { it.orderNumber }
        val nextToken = maxOf(
            (persistedOrders.mapNotNull { it.tokenNumber }.maxOrNull() ?: 0) + 1,
            1
        )

        return RestaurantUiState(
            appLanguage = persistedLanguage ?: AppLanguage.EN,
            isAdminAuthenticated = false,
            authenticatedEmployeeId = null,
            loggedInCustomer = persistedCustomer,
            customerMode = CustomerExperienceMode.HOME_DELIVERY,
            customerTab = CustomerTab.MENU,
            nextKioskTokenNumber = nextToken,
            menuItems = defaultCatalogMenu,
            orders = persistedOrders,
            staff = persistedStaff.orEmpty(),
            attendanceRecords = emptyList(),
            salesRecords = emptyList(),
            alerts = emptyList(),
            reviews = emptyList()
        )
    }
}
