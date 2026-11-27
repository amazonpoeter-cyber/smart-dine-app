package com.example.data

import com.example.data.supabase.NoOpSupabaseService
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
            if (remote.isEmpty() && localMenu.isNotEmpty()) {
                supabaseService.upsertMenuItems(localMenu)
                localMenu
            } else {
                menuStorage.saveMenuItems(remote)
                remote
            }
        } ?: localMenu

        val resolvedOrders = ordersRes.getOrNull()?.let { remote ->
            if (remote.isEmpty() && localOrders.isNotEmpty()) {
                supabaseService.upsertOrders(localOrders)
                localOrders
            } else {
                val merged = (remote + localOrders.filter { local ->
                    remote.none { it.orderNumber == local.orderNumber }
                }).sortedByDescending { it.orderNumber }
                credentialStorage.saveDeliveryOrders(merged)
                merged
            }
        } ?: localOrders

        val resolvedReviews = reviewsRes.getOrNull()?.let { remote ->
            if (remote.isEmpty() && localReviews.isNotEmpty()) {
                supabaseService.upsertReviews(localReviews)
                localReviews
            } else {
                remote
            }
        } ?: localReviews

        val resolvedStaff = staffRes.getOrNull()?.let { remote ->
            if (remote.isEmpty() && localStaff.isNotEmpty()) {
                supabaseService.upsertStaffList(localStaff)
                localStaff
            } else {
                credentialStorage.saveStaff(remote)
                remote
            }
        } ?: localStaff

        val resolvedAttendance = attendanceRes.getOrNull()?.let { remote ->
            if (remote.isEmpty() && localAttendance.isNotEmpty()) {
                supabaseService.upsertAttendanceRecords(localAttendance)
                localAttendance
            } else {
                remote
            }
        } ?: localAttendance

        val resolvedAlerts = alertsRes.getOrNull()?.let { remote ->
            if (remote.isEmpty() && localAlerts.isNotEmpty()) {
                supabaseService.upsertAlerts(localAlerts)
                localAlerts
            } else {
                remote
            }
        } ?: localAlerts

        val resolvedSales = salesRes.getOrNull()?.let { remote ->
            if (remote.isEmpty() && localSales.isNotEmpty()) {
                supabaseService.upsertSalesRecords(localSales)
                localSales
            } else {
                val mergedSales = (remote + localSales.filter { local ->
                    remote.none { it.dateIso == local.dateIso }
                }).sortedByDescending { it.dateIso }
                mergedSales
            }
        } ?: localSales

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
        val fetched = supabaseService.fetchOrders().getOrNull() ?: return null
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

    suspend fun refreshSales(): List<DailySalesRecord>? = supabaseService.fetchSalesRecords().getOrNull()

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
