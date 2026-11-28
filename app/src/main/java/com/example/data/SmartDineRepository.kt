package com.example.data

import com.example.data.supabase.NoOpSupabaseService
import com.example.data.supabase.SupabaseAuthResult
import com.example.data.supabase.SupabaseRealtimeEvent
import com.example.data.supabase.SupabaseService
import com.example.model.AttendanceRecord
import com.example.model.DailySalesRecord
import com.example.model.KitchenOrder
import com.example.model.MenuItem
import com.example.model.ReviewEntry
import com.example.model.StaffMember
import com.example.model.WhatsAppAlert
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow

data class RemoteSyncSnapshot(
    val menuItems: List<MenuItem>,
    val orders: List<KitchenOrder>,
    val reviews: List<ReviewEntry>,
    val staff: List<StaffMember>,
    val attendanceRecords: List<AttendanceRecord>,
    val alerts: List<WhatsAppAlert>,
    val salesRecords: List<DailySalesRecord>,
    val syncedWithRemote: Boolean
)

class SmartDineRepository(
    val menuStorage: MenuStorage,
    val credentialStorage: CredentialAndRosterStorage,
    private val supabaseService: SupabaseService = NoOpSupabaseService()
) {
    val isRemoteEnabled: Boolean
        get() = supabaseService.isRemoteEnabled

    val realtimeEvents: SharedFlow<SupabaseRealtimeEvent>
        get() = supabaseService.realtimeEvents

    fun startRealtimeSync() {
        supabaseService.startRealtimeSync()
    }

    fun stopRealtimeSync() {
        supabaseService.stopRealtimeSync()
    }

    fun refreshRealtimeAuth() {
        supabaseService.refreshRealtimeAuth()
    }

    suspend fun signInAdmin(email: String, password: String): SupabaseAuthResult =
        supabaseService.signInAdmin(email, password)

    fun beginGoogleOAuth(onPersistVerifier: (String) -> Unit): Result<String> =
        supabaseService.beginGoogleOAuth(onPersistVerifier)

    suspend fun completeGoogleOAuth(
        code: String?,
        accessTokenFromFragment: String?,
        errorMessage: String?,
        persistedVerifier: String?
    ): SupabaseAuthResult =
        supabaseService.completeGoogleOAuth(code, accessTokenFromFragment, errorMessage, persistedVerifier)

    suspend fun sendEmailOtp(email: String, fullName: String = ""): Result<Unit> =
        supabaseService.sendEmailOtp(email, fullName)

    suspend fun verifyEmailOtp(email: String, token: String): SupabaseAuthResult =
        supabaseService.verifyEmailOtp(email, token)

    suspend fun updateAdminPassword(newPassword: String): Result<Unit> =
        supabaseService.updateUserPassword(newPassword)

    suspend fun signOutAuth() {
        supabaseService.signOutAuth()
    }

    suspend fun authenticateEmployee(cnic: String, passwordHash: String): Result<StaffMember?> =
        supabaseService.authenticateEmployee(cnic, passwordHash)

    suspend fun syncInitialDataOrSeed(
        localMenu: List<MenuItem>,
        localOrders: List<KitchenOrder>,
        localReviews: List<ReviewEntry>,
        localStaff: List<StaffMember>,
        localAttendance: List<AttendanceRecord>,
        localAlerts: List<WhatsAppAlert>,
        localSales: List<DailySalesRecord>
    ): RemoteSyncSnapshot = coroutineScope {
        if (!supabaseService.isRemoteEnabled) {
            return@coroutineScope RemoteSyncSnapshot(
                menuItems = localMenu,
                orders = localOrders,
                reviews = localReviews,
                staff = localStaff,
                attendanceRecords = localAttendance,
                alerts = localAlerts,
                salesRecords = localSales,
                syncedWithRemote = false
            )
        }

        val menuDef = async { supabaseService.fetchMenuItems() }
        val ordersDef = async { supabaseService.fetchOrders() }
        val reviewsDef = async { supabaseService.fetchReviews() }
        val staffDef = async { supabaseService.fetchStaff() }
        val attendanceDef = async { supabaseService.fetchAttendanceRecords() }
        val alertsDef = async { supabaseService.fetchAlerts() }
        val salesDef = async { supabaseService.fetchSalesRecords() }

        val menuRes = menuDef.await()
        val ordersRes = ordersDef.await()
        val reviewsRes = reviewsDef.await()
        val staffRes = staffDef.await()
        val attendanceRes = attendanceDef.await()
        val alertsRes = alertsDef.await()
        val salesRes = salesDef.await()

        val anyRemoteSuccess = listOf(
            menuRes.isSuccess,
            ordersRes.isSuccess,
            reviewsRes.isSuccess,
            staffRes.isSuccess,
            attendanceRes.isSuccess,
            alertsRes.isSuccess,
            salesRes.isSuccess
        ).any { it }

        val resolvedMenu = menuRes.getOrNull()?.let { remote ->
            if (remote.isNotEmpty()) {
                menuStorage.saveMenuItems(remote)
                remote
            } else {
                localMenu
            }
        } ?: localMenu

        val resolvedOrders = ordersRes.getOrNull()?.let { remote ->
            val sorted = remote.sortedByDescending { it.orderNumber }
            credentialStorage.saveDeliveryOrders(sorted)
            sorted
        } ?: localOrders

        val resolvedReviews = reviewsRes.getOrNull() ?: localReviews

        val resolvedStaff = staffRes.getOrNull()?.let { remote ->
            credentialStorage.saveStaff(remote)
            remote
        } ?: localStaff

        val resolvedAttendance = attendanceRes.getOrNull() ?: localAttendance
        val resolvedAlerts = alertsRes.getOrNull() ?: localAlerts
        val resolvedSales = salesRes.getOrNull()?.sortedByDescending { it.dateIso } ?: localSales

        RemoteSyncSnapshot(
            menuItems = resolvedMenu,
            orders = resolvedOrders,
            reviews = resolvedReviews,
            staff = resolvedStaff,
            attendanceRecords = resolvedAttendance,
            alerts = resolvedAlerts,
            salesRecords = resolvedSales,
            syncedWithRemote = anyRemoteSuccess
        )
    }

    suspend fun refreshMenu(): List<MenuItem>? {
        val fetched = supabaseService.fetchMenuItems().getOrNull() ?: return null
        menuStorage.saveMenuItems(fetched)
        return fetched
    }

    suspend fun refreshOrders(): List<KitchenOrder>? {
        val fetched = supabaseService.fetchOrders().getOrNull()?.sortedByDescending { it.orderNumber } ?: return null
        credentialStorage.saveDeliveryOrders(fetched)
        return fetched
    }

    suspend fun refreshReviews(): List<ReviewEntry>? = supabaseService.fetchReviews().getOrNull()

    suspend fun refreshStaff(): List<StaffMember>? {
        val fetched = supabaseService.fetchStaff().getOrNull() ?: return null
        credentialStorage.saveStaff(fetched)
        return fetched
    }

    suspend fun refreshAttendance(): List<AttendanceRecord>? =
        supabaseService.fetchAttendanceRecords().getOrNull()

    suspend fun refreshAlerts(): List<WhatsAppAlert>? = supabaseService.fetchAlerts().getOrNull()

    suspend fun refreshSales(): List<DailySalesRecord>? =
        supabaseService.fetchSalesRecords().getOrNull()?.sortedByDescending { it.dateIso }

    suspend fun syncUpsertMenuItem(item: MenuItem): Result<Unit> =
        supabaseService.upsertMenuItem(item)

    suspend fun syncDeleteMenuItem(itemId: String): Result<Unit> =
        supabaseService.deleteMenuItem(itemId)

    suspend fun syncUpsertOrder(order: KitchenOrder): Result<Unit> =
        supabaseService.upsertOrder(order)

    suspend fun syncUpsertReview(review: ReviewEntry): Result<Unit> =
        supabaseService.upsertReview(review)

    suspend fun syncUpsertStaffMember(member: StaffMember): Result<Unit> =
        supabaseService.upsertStaffMember(member)

    suspend fun syncUpsertAttendance(record: AttendanceRecord): Result<Unit> =
        supabaseService.upsertAttendanceRecord(record)

    suspend fun syncUpsertAlert(alert: WhatsAppAlert): Result<Unit> =
        supabaseService.upsertAlert(alert)

    suspend fun syncUpsertSalesRecord(record: DailySalesRecord): Result<Unit> =
        supabaseService.upsertSalesRecord(record)
}
