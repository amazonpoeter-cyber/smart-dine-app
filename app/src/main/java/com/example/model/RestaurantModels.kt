package com.example.model

import com.example.data.supabase.SupabaseSyncStatus
import com.example.util.DateUtils
import com.example.util.WeekdaySlot

enum class AdminTab(val label: String) {
    OWNER_DASHBOARD("Admin Dashboard"),
    MENU_MANAGEMENT("Menu Management")
}

enum class StaffPortalRole(val label: String) {
    KITCHEN_CHEF("Kitchen / Chef"),
    DELIVERY_RIDER("Delivery Rider"),
    GENERAL_STAFF("General Staff / Server")
}

enum class ChefTab(val label: String) {
    KITCHEN_KDS("Kitchen (KDS)"),
    INVENTORY_STOCK("Inventory & Stock")
}

enum class RiderTab(val label: String) {
    READY_FOR_PICKUP("Ready for Pickup"),
    OUT_FOR_DELIVERY("Out for Delivery"),
    COMPLETED("Completed")
}

enum class AppLanguage(val label: String) {
    EN("Eng"),
    UR("اردو")
}

enum class CustomerAuthProvider(val label: String) {
    GOOGLE("Google"),
    EMAIL("Email")
}

data class CustomerProfile(
    val id: String,
    val name: String,
    val email: String = "",
    val avatarUrl: String = "",
    val phone: String = "",
    val provider: CustomerAuthProvider
)

enum class CustomerExperienceMode {
    HOME_DELIVERY,
    KIOSK_MODE
}

enum class CustomerTab(val label: String) {
    MENU("Menu"),
    TRACKER("My Orders"),
    REVIEWS("Reviews")
}

enum class HomeMenuFilter(val label: String) {
    ALL("All"),
    MAINS("Mains"),
    DRINKS("Drinks"),
    DESSERTS("Desserts"),
    SPICY("Spicy"),
    UNDER_1000("Under Rs 1000")
}

enum class OwnerSection(val label: String, val shortLabel: String) {
    OVERVIEW("Overview", "Overview"),
    LIVE_ORDERS("Orders", "Orders"),
    STAFF("Staff", "Staff"),
    DAILY_SALES("Sales", "Sales")
}

enum class OrderChannelFilter(val label: String) {
    ALL("All Channels"),
    DELIVERY("Home Delivery"),
    KIOSK("At Restaurant / Kiosk")
}

enum class MenuCategory(val label: String) {
    MAINS("Mains"),
    DRINKS("Drinks"),
    DESSERTS("Desserts")
}

enum class SpiceLevel(val label: String) {
    MILD("Mild"),
    MEDIUM("Medium"),
    SPICY("Spicy"),
    REFRESHING("Fresh"),
    SWEET("Sweet")
}

enum class MenuArtwork(val label: String) {
    SMASH_BURGER("Smash Burger"),
    MARGHERITA_PIZZA("Wood-Fired Pizza"),
    CHICKEN_BIRYANI("Chicken Biryani"),
    FRIES_ART("Crispy Fries"),
    MINT_COOLER_ART("Mint Cooler"),
    COFFEE_ART("Iced Coffee"),
    CHOCO_CAKE_ART("Chocolate Cake"),
    CHEESECAKE_ART("Cheesecake"),
    CUSTOM_DISH("Chef Special")
}

data class MenuItem(
    val id: String,
    val title: String,
    val description: String,
    val category: MenuCategory,
    val priceRs: Int,
    val prepTimeRange: String,
    val spiceLevel: SpiceLevel,
    val artwork: MenuArtwork = MenuArtwork.CUSTOM_DISH,
    val imageUrl: String = "",
    val isAvailable: Boolean = true,
    val isLowStock: Boolean = false
)

data class CartItem(
    val menuItem: MenuItem,
    val quantity: Int,
    val note: String = ""
)

enum class OrderStatus(val label: String, val customerStepLabel: String) {
    NEW("New", "Order received"),
    PREPARING("Preparing", "Preparing"),
    READY("Ready", "Ready"),
    ON_THE_WAY("On the Way", "On the Way"),
    SERVED("Completed", "Served"),
    CANCELLED("Cancelled", "Cancelled")
}

enum class OrderType(val label: String) {
    KIOSK("DINE-IN / KIOSK"),
    DELIVERY("DELIVERY")
}

data class OrderLineItem(
    val menuItemId: String,
    val title: String,
    val priceRs: Int,
    val quantity: Int,
    val note: String = ""
)

data class KitchenOrder(
    val orderNumber: Int,
    val tableCode: String,
    val items: List<OrderLineItem>,
    val status: OrderStatus,
    val elapsedSeconds: Int,
    val orderType: OrderType = OrderType.KIOSK,
    val tokenNumber: Int? = null,
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val deliveryAddress: String = "",
    val gpsCoordinatesLabel: String? = null,
    val gpsMapUrl: String? = null,
    val isGpsFallback: Boolean = false,
    val cancelWindowRemainingSeconds: Int = 0,
    val isPaid: Boolean = false,
    val paymentMethod: PaymentMethod? = null,
    val placedAtLabel: String = "Just now",
    val orderDateIso: String = DateUtils.todayIso(),
    val completedDateIso: String? = null,
    val completedTimeLabel: String? = null,
    val assignedRiderId: String? = null,
    val assignedRiderName: String? = null
) {
    val subtotalRs: Int get() = items.sumOf { it.priceRs * it.quantity }
    val taxRs: Int get() = (subtotalRs * 0.05).toInt()
    val grandTotalRs: Int get() = subtotalRs + taxRs
    val isDelivery: Boolean get() = orderType == OrderType.DELIVERY
    val isKiosk: Boolean get() = orderType == OrderType.KIOSK
    val kdsBadgeLabel: String
        get() = when {
            isDelivery -> "DELIVERY"
            tokenNumber != null -> "DINE-IN / KIOSK • Token #$tokenNumber"
            else -> "DINE-IN / KIOSK • $tableCode"
        }
    val canCustomerCancel: Boolean
        get() = isDelivery &&
            status != OrderStatus.CANCELLED &&
            status != OrderStatus.SERVED &&
            cancelWindowRemainingSeconds > 0
}

enum class PaymentMethod(val label: String, val subtitle: String, val isDeliveryOption: Boolean = false) {
    ONLINE("Online Payment", "Visa, Mastercard, EasyPaisa & Wallet", false),
    CASH_AT_TABLE("Cash at Table", "Server will collect payment at your table", false),
    PAY_AT_COUNTER("Cash at Counter", "Pay cash at the front counter with your Token #", false),
    CASH_ON_DELIVERY("Cash on Delivery (COD)", "Pay rider in cash upon order arrival", true),
    ONLINE_DELIVERY("Online Payment", "Card / EasyPaisa / JazzCash", true)
}

data class PaymentReceipt(
    val receiptId: String,
    val tableCode: String,
    val totalPaidRs: Int,
    val method: PaymentMethod,
    val orderNumbers: List<Int>
)

data class ReviewEntry(
    val id: String,
    val authorName: String,
    val tableCode: String,
    val rating: Int,
    val tags: List<String>,
    val comment: String,
    val timestampLabel: String,
    val isPublishedToSite: Boolean = true
)

enum class AlertType {
    NEW_ORDER,
    LOW_STOCK,
    PAYMENT,
    DELIVERY_ORDER,
    KIOSK_ORDER
}

data class WhatsAppAlert(
    val id: String,
    val title: String,
    val message: String,
    val timeLabel: String,
    val type: AlertType,
    val isAcknowledged: Boolean = false
)

enum class AttendanceStatus(val label: String) {
    PRESENT("Present"),
    ABSENT("Absent"),
    LEAVE("Leave"),
    DAY_OFF("Scheduled Off")
}

data class StaffMember(
    val id: String,
    val name: String,
    val cnic: String,
    val passwordHash: String,
    val designation: String,
    val portalRole: StaffPortalRole,
    val phone: String,
    val address: String,
    val email: String = "",
    val shiftLabel: String,
    val isOnDuty: Boolean,
    val isRelieved: Boolean = false,
    val scheduledDayOff: WeekdaySlot = WeekdaySlot.NONE
)

data class AttendanceRecord(
    val staffId: String,
    val dateIso: String,
    val status: AttendanceStatus,
    val checkInTimeLabel: String? = null,
    val isAdminOverride: Boolean = false
)

data class SalesBarPoint(
    val label: String,
    val amountRs: Int
)

enum class SalesPeriodFilter(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_MONTH("This Month"),
    CUSTOM_DATE("Custom Date")
}

data class DailySalesRecord(
    val dateIso: String,
    val grossRevenueRs: Int,
    val onlineRevenueRs: Int,
    val cashRevenueRs: Int,
    val deliveryRevenueRs: Int = (grossRevenueRs * 0.55).toInt(),
    val kioskRevenueRs: Int = grossRevenueRs - (grossRevenueRs * 0.55).toInt(),
    val deliveryOrderCount: Int = 0,
    val kioskOrderCount: Int = 0,
    val topSellerLabel: String,
    val orderCount: Int,
    val hourlyBars: List<SalesBarPoint>
)

enum class OrderHistoryDateFilter(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    ALL_DATES("All Dates"),
    CUSTOM_DATE("Pick Date")
}

enum class AttendanceFilterStatus(val label: String) {
    ALL("All"),
    PRESENT("Present"),
    ABSENT("Absent"),
    LEAVE("Leave"),
    DAY_OFF("Day-Off")
}

data class RestaurantUiState(
    val isAdminAuthenticated: Boolean = false,
    val authenticatedEmployeeId: String? = null,
    val appLanguage: AppLanguage = AppLanguage.EN,
    val loggedInCustomer: CustomerProfile? = null,
    val adminTab: AdminTab = AdminTab.OWNER_DASHBOARD,
    val chefTab: ChefTab = ChefTab.KITCHEN_KDS,
    val riderTab: RiderTab = RiderTab.READY_FOR_PICKUP,
    val riderPickupBannerMessage: String? = null,
    val customerMode: CustomerExperienceMode = CustomerExperienceMode.HOME_DELIVERY,
    val customerTab: CustomerTab = CustomerTab.MENU,
    val ownerSection: OwnerSection = OwnerSection.OVERVIEW,
    val orderChannelFilter: OrderChannelFilter = OrderChannelFilter.ALL,
    val selectedKioskCategory: MenuCategory? = null,
    val activeKioskTokenOrder: KitchenOrder? = null,
    val nextKioskTokenNumber: Int = 16,
    val homeMenuSearchQuery: String = "",
    val homeMenuFilter: HomeMenuFilter = HomeMenuFilter.ALL,
    val menuItems: List<MenuItem> = emptyList(),
    val cart: List<CartItem> = emptyList(),
    val orders: List<KitchenOrder> = emptyList(),
    val reviews: List<ReviewEntry> = emptyList(),
    val alerts: List<WhatsAppAlert> = emptyList(),
    val staff: List<StaffMember> = emptyList(),
    val attendanceRecords: List<AttendanceRecord> = emptyList(),
    val attendanceDateIso: String = DateUtils.todayIso(),
    val attendanceStatusFilter: AttendanceFilterStatus = AttendanceFilterStatus.ALL,
    val salesRecords: List<DailySalesRecord> = emptyList(),
    val salesPeriodFilter: SalesPeriodFilter = SalesPeriodFilter.TODAY,
    val salesCustomDateIso: String = DateUtils.todayIso(),
    val salesCustomMonthIso: String = DateUtils.currentMonthIso(),
    val completedOrderDateFilter: OrderHistoryDateFilter = OrderHistoryDateFilter.TODAY,
    val completedOrderCustomDateIso: String = DateUtils.todayIso(),
    val completedOrderSearchQuery: String = "",
    val revenueGrowthPct: Int = 12,
    val supabaseSyncStatus: SupabaseSyncStatus = SupabaseSyncStatus.LOCAL_ONLY,
    val toastMessage: String? = null
) {
    val isKioskModeActive: Boolean
        get() = customerMode == CustomerExperienceMode.KIOSK_MODE

    val authenticatedEmployee: StaffMember?
        get() = authenticatedEmployeeId?.let { id -> staff.firstOrNull { it.id == id && !it.isRelieved } }

    val cartItemCount: Int get() = cart.sumOf { it.quantity }
    val cartTotalRs: Int get() = cart.sumOf { it.menuItem.priceRs * it.quantity }

    val activeKdsOrdersCount: Int
        get() = orders.count { it.status != OrderStatus.SERVED && it.status != OrderStatus.CANCELLED }

    val readyForPickupDeliveryOrders: List<KitchenOrder>
        get() = orders.filter { it.isDelivery && it.status == OrderStatus.READY }

    val outForDeliveryOrders: List<KitchenOrder>
        get() = orders.filter { it.isDelivery && it.status == OrderStatus.ON_THE_WAY }

    val completedDeliveryOrdersForRider: List<KitchenOrder>
        get() {
            val riderId = authenticatedEmployeeId
            return orders.filter {
                it.isDelivery &&
                    it.status == OrderStatus.SERVED &&
                    (riderId == null || it.assignedRiderId == null || it.assignedRiderId == riderId)
            }
        }

    val homeDeliveryOrders: List<KitchenOrder>
        get() {
            val activeCust = loggedInCustomer
            return if (activeCust == null) {
                orders.filter { it.isDelivery }
            } else {
                orders.filter {
                    it.isDelivery && (it.customerId.isBlank() || it.customerId == activeCust.id || it.customerPhone == activeCust.phone)
                }
            }
        }

    val todayGrossRevenueRs: Int
        get() = salesRecords.firstOrNull { it.dateIso == DateUtils.todayIso() }?.grossRevenueRs ?: 0

    val todayHourlyBars: List<SalesBarPoint>
        get() = salesRecords.firstOrNull { it.dateIso == DateUtils.todayIso() }?.hourlyBars ?: emptyList()

    val activeStaffOnDutyCount: Int
        get() {
            val today = DateUtils.todayIso()
            return staff.count { member ->
                !member.isRelieved && effectiveAttendanceStatus(member, today) == AttendanceStatus.PRESENT
            }
        }

    fun effectiveAttendanceStatus(member: StaffMember, dateIso: String): AttendanceStatus {
        val explicit = attendanceRecords.firstOrNull { it.staffId == member.id && it.dateIso == dateIso }
        if (explicit != null) return explicit.status
        val dateWeekday = DateUtils.weekdayForIso(dateIso)
        if (!member.isRelieved && member.scheduledDayOff != WeekdaySlot.NONE && member.scheduledDayOff == dateWeekday) {
            return AttendanceStatus.DAY_OFF
        }
        return if (member.isOnDuty) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT
    }

    fun attendanceRecordFor(memberId: String, dateIso: String): AttendanceRecord? {
        return attendanceRecords.firstOrNull { it.staffId == memberId && it.dateIso == dateIso }
    }
}
