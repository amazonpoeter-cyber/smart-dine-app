package com.example

import com.example.data.InMemoryCredentialStorage
import com.example.data.InMemoryMenuStorage
import com.example.model.*
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.util.ThermalReceiptData
import com.example.util.WeekdaySlot
import com.example.viewmodel.EmployeeLoginResult
import com.example.viewmodel.PasswordChangeResult
import com.example.viewmodel.RestaurantViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestaurantViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun roleSeparation_blocksAdminAndChefActionsUntilAuthenticated() {
        val vm = RestaurantViewModel()
        assertFalse(vm.uiState.value.isAdminAuthenticated)

        // AdminPanel only contains Management modules (no KDS / Inventory tabs)
        assertEquals(listOf(AdminTab.OWNER_DASHBOARD, AdminTab.MENU_MANAGEMENT), AdminTab.entries)

        // Unauthenticated cannot advance kitchen tickets
        vm.advanceOrderStatus(130)
        assertEquals(OrderStatus.NEW, vm.uiState.value.orders.first { it.orderNumber == 130 }.status)

        // Admin login succeeds, but KDS ticket execution stays strictly restricted to Kitchen/Chef role
        assertFalse(vm.loginAdmin("admin", "wrongpass"))
        assertTrue(vm.loginAdmin("admin", "admin123"))
        assertTrue(vm.uiState.value.isAdminAuthenticated)
        vm.advanceOrderStatus(130)
        assertEquals(OrderStatus.NEW, vm.uiState.value.orders.first { it.orderNumber == 130 }.status)
        vm.logoutAdmin()
        assertFalse(vm.uiState.value.isAdminAuthenticated)

        // Chef logs in with CNIC + Password -> unlocks Chef Workspace & KDS execution
        val chefLogin = vm.loginEmployeeWithCnic("34601-1111111-1", "chef123")
        assertTrue(chefLogin is EmployeeLoginResult.Success)
        assertEquals(StaffPortalRole.KITCHEN_CHEF, (chefLogin as EmployeeLoginResult.Success).employee.portalRole)

        vm.advanceOrderStatus(130)
        assertEquals(OrderStatus.PREPARING, vm.uiState.value.orders.first { it.orderNumber == 130 }.status)
        vm.logoutEmployeePortal()
        assertEquals(null, vm.uiState.value.authenticatedEmployeeId)
    }

    @Test
    fun kdsCompletedTransition_preservesOrderWithTimestampAndDate() {
        val vm = RestaurantViewModel()
        assertTrue(vm.loginEmployeeWithCnic("34601-1111111-1", "chef123") is EmployeeLoginResult.Success)

        // Kiosk Order #128 starts in PREPARING; Chef advances PREPARING -> READY -> SERVED (Completed)
        vm.advanceOrderStatus(128)
        assertEquals(OrderStatus.READY, vm.uiState.value.orders.first { it.orderNumber == 128 }.status)
        vm.advanceOrderStatus(128)
        val completedOrder = vm.uiState.value.orders.first { it.orderNumber == 128 }
        assertEquals(OrderStatus.SERVED, completedOrder.status)
        assertEquals(DateUtils.todayIso(), completedOrder.completedDateIso)
        assertNotNull(completedOrder.completedTimeLabel)

        // Pre-populated yesterday completed orders exist for history filtering
        val yesterdayIso = DateUtils.daysAgoIso(1)
        assertTrue(
            vm.uiState.value.orders.any {
                it.status == OrderStatus.SERVED && it.completedDateIso == yesterdayIso
            }
        )
    }

    @Test
    fun employeePortalAndStaffLifecycle_tracksAttendanceDayOffValidationEditAndRelieve() {
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(InMemoryMenuStorage(), credStorage)
        val today = DateUtils.todayIso()

        // Employee CNIC + Password login opens personal portal without unlocking Admin Panel
        val res = vm.loginEmployeeWithCnic(cnicInput = "34601-5555555-5", passwordInput = "hamza123")
        assertTrue(res is EmployeeLoginResult.Success)
        assertEquals("st_5", vm.uiState.value.authenticatedEmployeeId)
        assertEquals(StaffPortalRole.GENERAL_STAFF, vm.uiState.value.authenticatedEmployee?.portalRole)
        assertFalse(vm.uiState.value.isAdminAuthenticated)

        // 1-click "Mark Today's Attendance (Present)" updates attendance record
        assertTrue(vm.markAuthenticatedEmployeePresent())
        assertEquals(
            AttendanceStatus.PRESENT,
            vm.uiState.value.effectiveAttendanceStatus(
                vm.uiState.value.staff.first { it.id == "st_5" },
                today
            )
        )
        vm.logoutEmployeePortal()

        // Scheduled Day-Off automatically shows DAY_OFF when no explicit override exists
        val zainab = vm.uiState.value.staff.first { it.id == "st_6" }
        assertEquals(
            AttendanceStatus.DAY_OFF,
            vm.uiState.value.effectiveAttendanceStatus(zainab, today)
        )

        // Admin onboarding validation: missing mandatory fields or duplicate CNIC must fail
        assertTrue(vm.loginAdmin("admin", "admin123"))
        assertFalse(
            vm.addEmployee(
                name = "Nadia Shah",
                cnic = "34601-7777777-7",
                password = "pass123",
                phone = "",
                designation = "Pastry Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Street 9, Model Town",
                email = "",
                shiftLabel = "09:00 - 17:00",
                scheduledDayOff = WeekdaySlot.SUNDAY
            )
        )
        assertFalse(
            vm.addEmployee(
                name = "Duplicate Chef",
                cnic = "34601-1111111-1",
                password = "pass123",
                phone = "+92 300 7777111",
                designation = "Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Street 9, Model Town",
                email = "",
                shiftLabel = "09:00 - 17:00",
                scheduledDayOff = WeekdaySlot.SUNDAY
            )
        )

        // Optional email left blank succeeds when all mandatory fields are provided
        assertTrue(
            vm.addEmployee(
                name = "Nadia Shah",
                cnic = "34601-7777777-7",
                password = "nadia123",
                phone = "+92 300 7777111",
                designation = "Pastry Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Street 9, Model Town",
                email = "",
                shiftLabel = "09:00 - 17:00",
                scheduledDayOff = WeekdaySlot.SUNDAY
            )
        )
        val nadia = vm.uiState.value.staff.first { it.name == "Nadia Shah" }
        assertEquals("Pastry Chef", nadia.designation)
        assertEquals("34601-7777777-7", nadia.cnic)
        assertEquals(StaffPortalRole.KITCHEN_CHEF, nadia.portalRole)

        // Edit employee details & role
        assertTrue(
            vm.updateEmployee(
                staffId = nadia.id,
                name = "Nadia Shah-Malik",
                cnic = "34601-7777777-8",
                password = "nadiaNew123",
                phone = "+92 300 8888222",
                designation = "Delivery Lead",
                portalRole = StaffPortalRole.DELIVERY_RIDER,
                address = "House 21, DHA Phase 5",
                email = "nadia@smartdine.app",
                shiftLabel = "10:00 - 18:00",
                scheduledDayOff = WeekdaySlot.SATURDAY
            )
        )
        val editedNadia = vm.uiState.value.staff.first { it.id == nadia.id }
        assertEquals("Nadia Shah-Malik", editedNadia.name)
        assertEquals("Delivery Lead", editedNadia.designation)
        assertEquals(StaffPortalRole.DELIVERY_RIDER, editedNadia.portalRole)
        assertEquals("34601-7777777-8", editedNadia.cnic)
        assertEquals("+92 300 8888222", editedNadia.phone)
        assertEquals("nadia@smartdine.app", editedNadia.email)
        assertEquals(WeekdaySlot.SATURDAY, editedNadia.scheduledDayOff)

        // Attendance override & relieve
        vm.overrideAttendance(nadia.id, today, AttendanceStatus.LEAVE)
        assertEquals(
            AttendanceStatus.LEAVE,
            vm.uiState.value.effectiveAttendanceStatus(editedNadia, today)
        )

        // Relieve employee removes from active login but keeps historical attendance logs
        vm.relieveEmployee(nadia.id)
        assertTrue(vm.uiState.value.staff.first { it.id == nadia.id }.isRelieved)
        assertNotNull(vm.uiState.value.attendanceRecordFor(nadia.id, today))
        assertTrue(vm.loginEmployeeWithCnic("34601-7777777-8", "nadiaNew123") is EmployeeLoginResult.Error)
    }

    @Test
    fun changeAdminPassword_verifiesOldPasswordAndPersistsAcrossRestarts() {
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage)

        assertTrue(vm.loginAdmin("admin", "admin123"))

        val wrongOld = vm.changeAdminPassword("wrong123", "newpass99", "newpass99")
        assertTrue(wrongOld is PasswordChangeResult.Error)

        val mismatch = vm.changeAdminPassword("admin123", "newpass99", "different99")
        assertTrue(mismatch is PasswordChangeResult.Error)

        val success = vm.changeAdminPassword("admin123", "newpass99", "newpass99")
        assertTrue(success is PasswordChangeResult.Success)
        vm.logoutAdmin()

        val restartedVm = RestaurantViewModel(menuStorage, credStorage)
        assertFalse(restartedVm.loginAdmin("admin", "admin123"))
        assertTrue(restartedVm.loginAdmin("admin", "newpass99"))
    }

    @Test
    fun changeEmployeePassword_verifiesOldPasswordAndPersistsAcrossRestarts() {
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage)

        val login = vm.loginEmployeeWithCnic("34601-1111111-1", "chef123")
        assertTrue(login is EmployeeLoginResult.Success)

        val wrongOld = vm.changeEmployeePassword("st_1", "wrongpass", "tariqNew99", "tariqNew99")
        assertTrue(wrongOld is PasswordChangeResult.Error)

        val ok = vm.changeEmployeePassword("st_1", "chef123", "tariqNew99", "tariqNew99")
        assertTrue(ok is PasswordChangeResult.Success)

        val restartedVm = RestaurantViewModel(menuStorage, credStorage)
        assertTrue(restartedVm.loginEmployeeWithCnic("34601-1111111-1", "chef123") is EmployeeLoginResult.Error)
        assertTrue(restartedVm.loginEmployeeWithCnic("34601-1111111-1", "tariqNew99") is EmployeeLoginResult.Success)
    }

    @Test
    fun salesAnalytics_tracksDeliveryAndKioskMultiChannelRevenue() {
        val vm = RestaurantViewModel()
        val today = DateUtils.todayIso()
        val yesterday = DateUtils.daysAgoIso(1)

        val todayRecBefore = vm.uiState.value.salesRecords.first { it.dateIso == today }
        assertEquals(184500, todayRecBefore.grossRevenueRs)
        assertEquals(106800, todayRecBefore.deliveryRevenueRs)
        assertEquals(77700, todayRecBefore.kioskRevenueRs)
        assertTrue(vm.uiState.value.salesRecords.any { it.dateIso == yesterday && it.grossRevenueRs == 162800 })

        // Place a Home Delivery order -> increments deliveryRevenueRs & deliveryOrderCount
        val burger = vm.uiState.value.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Ali",
                customerPhone = "+92 300 1112233",
                deliveryAddress = "House 1, Model Town",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.ONLINE_DELIVERY
            )
        )
        val afterDelivery = vm.uiState.value.salesRecords.first { it.dateIso == today }
        val delOrder = vm.uiState.value.homeDeliveryOrders.first()
        assertEquals(todayRecBefore.deliveryRevenueRs + delOrder.grandTotalRs, afterDelivery.deliveryRevenueRs)
        assertEquals(todayRecBefore.deliveryOrderCount + 1, afterDelivery.deliveryOrderCount)

        // Cancel within 3 minutes -> rolls back deliveryRevenueRs & deliveryOrderCount
        assertTrue(vm.cancelDeliveryOrder(delOrder.orderNumber))
        val afterCancel = vm.uiState.value.salesRecords.first { it.dateIso == today }
        assertEquals(todayRecBefore.deliveryRevenueRs, afterCancel.deliveryRevenueRs)
        assertEquals(todayRecBefore.deliveryOrderCount, afterCancel.deliveryOrderCount)

        // Activate Kiosk Mode and place Kiosk order -> increments kioskRevenueRs & kioskOrderCount
        assertTrue(vm.activateKioskMode("kiosk01", "kiosk123") is PasswordChangeResult.Success)
        vm.addToCart(burger)
        val kioskOrder = vm.placeKioskOrder(PaymentMethod.PAY_AT_COUNTER)
        assertNotNull(kioskOrder)
        val afterKiosk = vm.uiState.value.salesRecords.first { it.dateIso == today }
        assertEquals(todayRecBefore.kioskRevenueRs + kioskOrder!!.grandTotalRs, afterKiosk.kioskRevenueRs)
        assertEquals(todayRecBefore.kioskOrderCount + 1, afterKiosk.kioskOrderCount)
    }

    @Test
    fun adminMenuCrud_persistsChangesAcrossRestarts() {
        val storage = InMemoryMenuStorage()
        val vm = RestaurantViewModel(storage)
        assertTrue(vm.loginAdmin("admin", "admin123"))

        vm.addMenuItem(
            title = "Garlic Naan",
            description = "Tandoor baked flatbread",
            category = MenuCategory.MAINS,
            priceRs = 180,
            prepTimeRange = "8-10 min",
            spiceLevel = SpiceLevel.MILD,
            artwork = MenuArtwork.CUSTOM_DISH,
            imageUrl = "https://example.com/naan.jpg"
        )
        val added = vm.uiState.value.menuItems.first { it.title == "Garlic Naan" }
        assertEquals(180, added.priceRs)

        vm.updateMenuItem(
            itemId = added.id,
            title = "Butter Garlic Naan",
            description = "Brushed with herb butter",
            category = MenuCategory.MAINS,
            priceRs = 220,
            prepTimeRange = "10 min",
            spiceLevel = SpiceLevel.MILD,
            artwork = MenuArtwork.CUSTOM_DISH,
            imageUrl = ""
        )
        vm.toggleItemAvailability(added.id)

        val restartedVm = RestaurantViewModel(storage)
        val persistedItem = restartedVm.uiState.value.menuItems.firstOrNull { it.id == added.id }
        assertNotNull(persistedItem)
        assertEquals("Butter Garlic Naan", persistedItem!!.title)
        assertFalse(persistedItem.isAvailable)
    }

    @Test
    fun defaultEntry_launchesInHomeDeliveryWithoutLoginLock() {
        val vm = RestaurantViewModel()
        assertEquals(CustomerExperienceMode.HOME_DELIVERY, vm.uiState.value.customerMode)
        assertEquals(CustomerTab.MENU, vm.uiState.value.customerTab)
        assertFalse(vm.uiState.value.isKioskModeActive)
        assertEquals(null, vm.uiState.value.loggedInCustomer)

        // Guests can immediately browse & add dishes to cart without logging in first
        val burger = vm.uiState.value.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        assertEquals(1, vm.uiState.value.cartItemCount)
    }

    @Test
    fun kioskMode_enforcesPasswordActivationExitSequentialTokensAndKdsSync() {
        val vm = RestaurantViewModel()

        // Invalid Kiosk ID or password must fail
        assertTrue(vm.activateKioskMode("wrong01", "kiosk123") is PasswordChangeResult.Error)
        assertTrue(vm.activateKioskMode("kiosk01", "wrongpass") is PasswordChangeResult.Error)
        assertFalse(vm.uiState.value.isKioskModeActive)

        // Valid credentials unlock Self-Ordering Kiosk Mode
        assertTrue(vm.activateKioskMode("kiosk01", "kiosk123") is PasswordChangeResult.Success)
        assertTrue(vm.uiState.value.isKioskModeActive)
        assertEquals(CustomerExperienceMode.KIOSK_MODE, vm.uiState.value.customerMode)

        // Switching to Home Delivery tabs is blocked while Kiosk Mode is locked
        vm.selectCustomerTab(CustomerTab.TRACKER)
        assertEquals(CustomerTab.MENU, vm.uiState.value.customerTab)

        // Place first Kiosk order -> Token #16 + KDS sync
        val burger = vm.uiState.value.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        val order1 = vm.placeKioskOrder(PaymentMethod.PAY_AT_COUNTER)
        assertNotNull(order1)
        assertEquals(16, order1!!.tokenNumber)
        assertEquals(OrderType.KIOSK, order1.orderType)
        assertEquals("DINE-IN / KIOSK • Token #16", order1.kdsBadgeLabel)
        assertFalse(order1.isPaid)
        assertEquals(0, vm.uiState.value.cartItemCount)
        assertEquals(16, vm.uiState.value.activeKioskTokenOrder?.tokenNumber)

        // Reset for next customer & place second Kiosk order -> Token #17 paid online
        vm.resetKioskForNextCustomer()
        assertEquals(null, vm.uiState.value.activeKioskTokenOrder)
        vm.addToCart(burger)
        val order2 = vm.placeKioskOrder(PaymentMethod.ONLINE)
        assertNotNull(order2)
        assertEquals(17, order2!!.tokenNumber)
        assertTrue(order2.isPaid)

        // Exiting Kiosk Mode requires Kiosk or Admin password
        assertTrue(vm.exitKioskMode("badpass") is PasswordChangeResult.Error)
        assertTrue(vm.uiState.value.isKioskModeActive)

        assertTrue(vm.exitKioskMode("kiosk123") is PasswordChangeResult.Success)
        assertFalse(vm.uiState.value.isKioskModeActive)
        assertEquals(CustomerExperienceMode.HOME_DELIVERY, vm.uiState.value.customerMode)

        // Admin password can also exit Kiosk Mode
        assertTrue(vm.activateKioskMode("kiosk01", "kiosk123") is PasswordChangeResult.Success)
        assertTrue(vm.exitKioskMode("admin123") is PasswordChangeResult.Success)
        assertFalse(vm.uiState.value.isKioskModeActive)
    }

    @Test
    fun homeDeliveryOrder_validatesMandatoryFieldsAndSendsDeliveryOrderToKds() {
        val vm = RestaurantViewModel()
        assertEquals(CustomerExperienceMode.HOME_DELIVERY, vm.uiState.value.customerMode)

        val pizza = vm.uiState.value.menuItems.first { it.id == "item_2" }
        vm.addToCart(pizza)
        assertEquals(1, vm.uiState.value.cartItemCount)

        // Blank mandatory address must fail
        assertFalse(
            vm.placeHomeDeliveryOrder(
                customerName = "Bilal Ahmed",
                customerPhone = "+92 300 1234567",
                deliveryAddress = "",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.CASH_ON_DELIVERY
            )
        )

        val revenueBefore = vm.uiState.value.todayGrossRevenueRs
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Bilal Ahmed",
                customerPhone = "+92 300 1234567",
                deliveryAddress = "House 42, Sector F-7/2, Islamabad",
                gpsCoordinatesLabel = "32.5101° N, 74.5361° E",
                gpsMapUrl = "https://maps.google.com/?q=32.5101,74.5361",
                isGpsFallback = true,
                paymentMethod = PaymentMethod.ONLINE_DELIVERY
            )
        )

        val deliveryOrder = vm.uiState.value.homeDeliveryOrders.firstOrNull()
        assertNotNull(deliveryOrder)
        assertEquals(OrderType.DELIVERY, deliveryOrder!!.orderType)
        assertEquals("DELIVERY", deliveryOrder.tableCode)
        assertEquals("Bilal Ahmed", deliveryOrder.customerName)
        assertEquals("32.5101° N, 74.5361° E", deliveryOrder.gpsCoordinatesLabel)
        assertTrue(deliveryOrder.isPaid)
        assertTrue(vm.uiState.value.todayGrossRevenueRs > revenueBefore)
    }

    @Test
    fun customerAuthAndOrderHistory_persistsAcrossRestarts() {
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage)

        // Email sign up & login verification
        val badEmail = vm.loginOrSignUpCustomerWithEmail("Zara", "invalid", "pass123")
        assertTrue(badEmail is PasswordChangeResult.Error)

        val emailOk = vm.loginOrSignUpCustomerWithEmail("Zara Ali", "zara@smartdine.app", "secret123")
        assertTrue(emailOk is PasswordChangeResult.Success)
        assertEquals("Zara Ali", vm.uiState.value.loggedInCustomer?.name)
        assertEquals(CustomerAuthProvider.EMAIL, vm.uiState.value.loggedInCustomer?.provider)

        // Place delivery order while signed in
        val burger = vm.uiState.value.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Zara Ali",
                customerPhone = "+92 301 5554433",
                deliveryAddress = "House 12, Gulberg III, Lahore",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.CASH_ON_DELIVERY
            )
        )
        val placedOrderNum = vm.uiState.value.homeDeliveryOrders.first().orderNumber

        // Restart ViewModel -> customer session & delivery order history persist
        val restartedVm = RestaurantViewModel(menuStorage, credStorage)
        assertEquals("Zara Ali", restartedVm.uiState.value.loggedInCustomer?.name)
        assertTrue(restartedVm.uiState.value.homeDeliveryOrders.any { it.orderNumber == placedOrderNum })

        // Logout & test Google + Phone OTP 1234
        restartedVm.logoutCustomer()
        assertEquals(null, restartedVm.uiState.value.loggedInCustomer)

        val wrongOtp = restartedVm.loginCustomerWithPhoneOtp("Hamza", "+92 321 9998877", "9999")
        assertTrue(wrongOtp is PasswordChangeResult.Error)

        val okOtp = restartedVm.loginCustomerWithPhoneOtp("Hamza", "+92 321 9998877", "1234")
        assertTrue(okOtp is PasswordChangeResult.Success)
        assertEquals(CustomerAuthProvider.PHONE, restartedVm.uiState.value.loggedInCustomer?.provider)

        restartedVm.loginCustomerWithGoogle()
        assertEquals(CustomerAuthProvider.GOOGLE, restartedVm.uiState.value.loggedInCustomer?.provider)
    }

    @Test
    fun deliveryCancellationWindow_allowsCancelWithin3MinsAndLocksAfterExpiry() {
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage)
        vm.loginCustomerWithGoogle("Omar", "omar@gmail.com")

        val pizza = vm.uiState.value.menuItems.first { it.id == "item_2" }
        vm.addToCart(pizza)
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Omar",
                customerPhone = "+92 300 4445566",
                deliveryAddress = "Flat 4B, Clifton Block 2",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.CASH_ON_DELIVERY
            )
        )
        val order1 = vm.uiState.value.homeDeliveryOrders.first()
        assertEquals(180, order1.cancelWindowRemainingSeconds)
        assertTrue(order1.canCustomerCancel)
        val activeBeforeCancel = vm.uiState.value.activeKdsOrdersCount

        // Cancel within 3-minute window -> marks CANCELLED and removes from active KDS count
        assertTrue(vm.cancelDeliveryOrder(order1.orderNumber))
        val cancelledOrder = vm.uiState.value.orders.first { it.orderNumber == order1.orderNumber }
        assertEquals(OrderStatus.CANCELLED, cancelledOrder.status)
        assertFalse(cancelledOrder.canCustomerCancel)
        assertEquals(activeBeforeCancel - 1, vm.uiState.value.activeKdsOrdersCount)

        // Second order: advance virtual clock 181s -> timer expires and order locks
        vm.addToCart(pizza)
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Omar",
                customerPhone = "+92 300 4445566",
                deliveryAddress = "Flat 4B, Clifton Block 2",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.ONLINE_DELIVERY
            )
        )
        val order2Num = vm.uiState.value.homeDeliveryOrders.first().orderNumber

        testDispatcher.scheduler.advanceTimeBy(181_000L)
        testDispatcher.scheduler.runCurrent()

        val lockedOrder = vm.uiState.value.orders.first { it.orderNumber == order2Num }
        assertEquals(0, lockedOrder.cancelWindowRemainingSeconds)
        assertFalse(lockedOrder.canCustomerCancel)
        assertFalse(vm.cancelDeliveryOrder(order2Num))
    }

    @Test
    fun deliveryOrder_syncsFromChefReadyToRiderPickupAndDelivered() {
        val vm = RestaurantViewModel()
        vm.loginCustomerWithGoogle("Hina", "hina@gmail.com")
        val biryani = vm.uiState.value.menuItems.first { it.id == "item_3" }
        vm.addToCart(biryani)
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Hina",
                customerPhone = "+92 333 1112233",
                deliveryAddress = "House 9, DHA Phase 6",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.CASH_ON_DELIVERY
            )
        )
        val orderNum = vm.uiState.value.homeDeliveryOrders.first().orderNumber

        // Chef prepares & marks Ready -> triggers Rider Notification Banner & blocks Chef from dispatching past Ready
        assertTrue(vm.loginEmployeeWithCnic("34601-1111111-1", "chef123") is EmployeeLoginResult.Success)
        vm.advanceOrderStatus(orderNum)
        assertEquals(OrderStatus.PREPARING, vm.uiState.value.orders.first { it.orderNumber == orderNum }.status)

        vm.advanceOrderStatus(orderNum)
        assertEquals(OrderStatus.READY, vm.uiState.value.orders.first { it.orderNumber == orderNum }.status)
        assertTrue(vm.uiState.value.riderPickupBannerMessage?.contains("#$orderNum") == true)

        vm.advanceOrderStatus(orderNum)
        assertEquals(OrderStatus.READY, vm.uiState.value.orders.first { it.orderNumber == orderNum }.status)
        assertFalse(vm.riderPickUpOrder(orderNum))
        vm.logoutEmployeePortal()

        // Rider logs in with CNIC -> picks up order (ON_THE_WAY) and marks Delivered (SERVED)
        val riderLogin = vm.loginEmployeeWithCnic("34601-2222222-2", "rider123")
        assertTrue(riderLogin is EmployeeLoginResult.Success)
        assertEquals(StaffPortalRole.DELIVERY_RIDER, (riderLogin as EmployeeLoginResult.Success).employee.portalRole)
        assertTrue(vm.uiState.value.readyForPickupDeliveryOrders.any { it.orderNumber == orderNum })

        assertTrue(vm.riderPickUpOrder(orderNum))
        val inTransit = vm.uiState.value.orders.first { it.orderNumber == orderNum }
        assertEquals(OrderStatus.ON_THE_WAY, inTransit.status)
        assertEquals("Rider Bilal", inTransit.assignedRiderName)

        assertTrue(vm.riderMarkDelivered(orderNum))
        val delivered = vm.uiState.value.orders.first { it.orderNumber == orderNum }
        assertEquals(OrderStatus.SERVED, delivered.status)
        assertTrue(delivered.isPaid)
        assertNotNull(delivered.completedTimeLabel)
    }

    @Test
    fun bilingualToggle_switchesBetweenEnglishAndUrduAndPersists() {
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage)

        assertEquals(AppLanguage.EN, vm.uiState.value.appLanguage)
        assertFalse(AppStrings.forLanguage(vm.uiState.value.appLanguage).isUrdu)

        vm.toggleAppLanguage()
        assertEquals(AppLanguage.UR, vm.uiState.value.appLanguage)
        val urStrings = AppStrings.forLanguage(vm.uiState.value.appLanguage)
        assertTrue(urStrings.isUrdu)
        assertEquals("آپ کا آرڈر راستے میں ہے اور جلد پہنچ جائے گا!", urStrings.onTheWayBanner)

        val restartedVm = RestaurantViewModel(menuStorage, credStorage)
        assertEquals(AppLanguage.UR, restartedVm.uiState.value.appLanguage)
    }

    @Test
    fun thermalReceiptData_formatsKioskTokenAndDeliveryReceiptsAccurately() {
        val vm = RestaurantViewModel()
        assertTrue(vm.activateKioskMode("kiosk01", "kiosk123") is PasswordChangeResult.Success)
        val burger = vm.uiState.value.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        val kioskOrder = vm.placeKioskOrder(PaymentMethod.ONLINE)!!

        val kioskReceipt = ThermalReceiptData.fromOrder(kioskOrder)
        assertEquals("RCP-${1000 + kioskOrder.orderNumber}", kioskReceipt.receiptId)
        assertEquals("Dine-In / Kiosk • Token #16", kioskReceipt.orderTypeLabel)
        assertTrue(kioskReceipt.isPaid)
        assertEquals("Online Payment", kioskReceipt.paymentMethodLabel)
        assertEquals(kioskOrder.subtotalRs, kioskReceipt.subtotalRs)
        assertEquals(kioskOrder.taxRs, kioskReceipt.taxRs)
        assertEquals(kioskOrder.grandTotalRs, kioskReceipt.grandTotalRs)

        val deliveryOrder = vm.uiState.value.orders.first { it.isDelivery }
        val deliveryReceipt = ThermalReceiptData.fromOrder(deliveryOrder)
        assertEquals("Home Delivery", deliveryReceipt.orderTypeLabel)
        assertNotNull(deliveryReceipt.customerDetail)
    }

    @Test
    fun supabaseSync_hydratesInitialDataPropagatesRealtimeAndSyncsMutations() {
        val fakeSupabase = FakeSupabaseService(
            remoteMenu = listOf(
                MenuItem(
                    id = "remote_1",
                    title = "Supabase Kebab Platter",
                    description = "Synced from remote database",
                    category = MenuCategory.MAINS,
                    priceRs = 1450,
                    prepTimeRange = "18 min",
                    spiceLevel = SpiceLevel.SPICY,
                    artwork = MenuArtwork.CUSTOM_DISH
                )
            )
        )
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)

        testDispatcher.scheduler.advanceUntilIdle()

        // Initial hydration pulled remote menu and seeded empty remote tables (orders, staff, etc.)
        assertEquals(com.example.data.supabase.SupabaseSyncStatus.LIVE_SYNCED, vm.uiState.value.supabaseSyncStatus)
        assertEquals(listOf("remote_1"), vm.uiState.value.menuItems.map { it.id })
        assertTrue(fakeSupabase.upsertedOrdersBatchCount > 0)
        assertTrue(fakeSupabase.upsertedStaffBatchCount > 0)
        assertTrue(fakeSupabase.realtimeStarted)

        // Incoming realtime menu item upsert updates UI state immediately
        val pushedDish = MenuItem(
            id = "remote_2",
            title = "Live Synced Karahi",
            description = "Added from another terminal",
            category = MenuCategory.MAINS,
            priceRs = 1850,
            prepTimeRange = "22 min",
            spiceLevel = SpiceLevel.SPICY,
            artwork = MenuArtwork.CUSTOM_DISH
        )
        fakeSupabase.emitEvent(com.example.data.supabase.SupabaseRealtimeEvent.MenuUpserted(pushedDish))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.menuItems.any { it.id == "remote_2" && it.title == "Live Synced Karahi" })

        // Outgoing customer delivery order syncs to Supabase
        assertTrue(
            vm.addToCart(pushedDish).let {
                vm.placeHomeDeliveryOrder(
                    customerName = "Saad",
                    customerPhone = "+92 300 1230000",
                    deliveryAddress = "House 8, Model Town",
                    gpsCoordinatesLabel = null,
                    gpsMapUrl = null,
                    isGpsFallback = false,
                    paymentMethod = PaymentMethod.CASH_ON_DELIVERY
                )
            }
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeSupabase.singleUpsertedOrders.any { it.customerName == "Saad" })

        // Outgoing Admin menu delete syncs delete call to Supabase
        assertTrue(vm.loginAdmin("admin", "admin123"))
        vm.deleteMenuItem("remote_2")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeSupabase.deletedMenuIds.contains("remote_2"))
        assertFalse(vm.uiState.value.menuItems.any { it.id == "remote_2" })
    }
}

private class FakeSupabaseService(
    private val remoteMenu: List<MenuItem> = emptyList(),
    private val remoteOrders: List<KitchenOrder> = emptyList()
) : com.example.data.supabase.SupabaseService {
    override val isRemoteEnabled: Boolean = true
    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<com.example.data.supabase.SupabaseRealtimeEvent>(
        extraBufferCapacity = 32
    )
    override val realtimeEvents: kotlinx.coroutines.flow.SharedFlow<com.example.data.supabase.SupabaseRealtimeEvent> =
        _events

    var realtimeStarted = false
    var upsertedOrdersBatchCount = 0
    var upsertedStaffBatchCount = 0
    val singleUpsertedOrders = mutableListOf<KitchenOrder>()
    val deletedMenuIds = mutableListOf<String>()

    fun emitEvent(event: com.example.data.supabase.SupabaseRealtimeEvent) {
        _events.tryEmit(event)
    }

    override fun startRealtimeSync() {
        realtimeStarted = true
    }

    override fun stopRealtimeSync() {
        realtimeStarted = false
    }

    override suspend fun fetchMenuItems(): Result<List<MenuItem>> = Result.success(remoteMenu)
    override suspend fun upsertMenuItem(item: MenuItem): Result<Unit> = Result.success(Unit)
    override suspend fun upsertMenuItems(items: List<MenuItem>): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMenuItem(itemId: String): Result<Unit> {
        deletedMenuIds.add(itemId)
        return Result.success(Unit)
    }

    override suspend fun fetchOrders(): Result<List<KitchenOrder>> = Result.success(remoteOrders)
    override suspend fun upsertOrder(order: KitchenOrder): Result<Unit> {
        singleUpsertedOrders.add(order)
        return Result.success(Unit)
    }
    override suspend fun upsertOrders(orders: List<KitchenOrder>): Result<Unit> {
        upsertedOrdersBatchCount += orders.size
        return Result.success(Unit)
    }

    override suspend fun fetchReviews(): Result<List<ReviewEntry>> = Result.success(emptyList())
    override suspend fun upsertReview(review: ReviewEntry): Result<Unit> = Result.success(Unit)
    override suspend fun upsertReviews(reviews: List<ReviewEntry>): Result<Unit> = Result.success(Unit)

    override suspend fun fetchStaff(): Result<List<StaffMember>> = Result.success(emptyList())
    override suspend fun upsertStaffMember(member: StaffMember): Result<Unit> = Result.success(Unit)
    override suspend fun upsertStaffList(staff: List<StaffMember>): Result<Unit> {
        upsertedStaffBatchCount += staff.size
        return Result.success(Unit)
    }

    override suspend fun fetchAttendanceRecords(): Result<List<AttendanceRecord>> = Result.success(emptyList())
    override suspend fun upsertAttendanceRecord(record: AttendanceRecord): Result<Unit> = Result.success(Unit)
    override suspend fun upsertAttendanceRecords(records: List<AttendanceRecord>): Result<Unit> = Result.success(Unit)

    override suspend fun fetchAlerts(): Result<List<WhatsAppAlert>> = Result.success(emptyList())
    override suspend fun upsertAlert(alert: WhatsAppAlert): Result<Unit> = Result.success(Unit)
    override suspend fun upsertAlerts(alerts: List<WhatsAppAlert>): Result<Unit> = Result.success(Unit)

    override suspend fun fetchSalesRecords(): Result<List<DailySalesRecord>> = Result.success(emptyList())
    override suspend fun upsertSalesRecord(record: DailySalesRecord): Result<Unit> = Result.success(Unit)
    override suspend fun upsertSalesRecords(records: List<DailySalesRecord>): Result<Unit> = Result.success(Unit)
}
