package com.example

import com.example.data.InMemoryCredentialStorage
import com.example.data.InMemoryMenuStorage
import com.example.data.supabase.SupabaseAuthResult
import com.example.data.supabase.SupabaseAuthSession
import com.example.data.supabase.SupabaseRealtimeEvent
import com.example.data.supabase.SupabaseService
import com.example.data.supabase.SupabaseSyncStatus
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
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
    fun freshInstall_startsLoggedOutWithNoMockOperationalDataAndMenuAccessible() {
        val vm = RestaurantViewModel()
        val state = vm.uiState.value

        assertFalse(state.isAdminAuthenticated)
        assertEquals(null, state.authenticatedEmployeeId)
        assertEquals(null, state.loggedInCustomer)
        assertEquals(CustomerExperienceMode.HOME_DELIVERY, state.customerMode)
        assertEquals(CustomerTab.MENU, state.customerTab)
        assertFalse(state.isKioskModeActive)
        assertTrue(state.staff.isEmpty())
        assertTrue(state.orders.isEmpty())
        assertTrue(state.attendanceRecords.isEmpty())
        assertTrue(state.alerts.isEmpty())
        assertTrue(state.reviews.isEmpty())
        assertTrue(state.salesRecords.isEmpty())
        assertTrue(state.menuItems.isNotEmpty())

        // Customers can browse dishes & add to cart without admin login
        val burger = state.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        assertEquals(1, vm.uiState.value.cartItemCount)
    }

    @Test
    fun adminAuth_enforcesSupabaseRoleVerificationAndBlocksNonAdmins() = runBlocking {
        val fakeSupabase = FakeSupabaseService(
            validAdminEmail = "owner@smartdine.test",
            validAdminPassword = "StrongOwnerPassword99",
            validAdminRole = "admin"
        )
        val vm = RestaurantViewModel(InMemoryMenuStorage(), InMemoryCredentialStorage(), fakeSupabase)

        // Blank or invalid input rejected locally before hitting auth
        assertTrue(vm.loginAdmin("", "StrongOwnerPassword99") is PasswordChangeResult.Error)
        assertTrue(vm.loginAdmin("not-an-email", "StrongOwnerPassword99") is PasswordChangeResult.Error)
        assertTrue(vm.loginAdmin("owner@smartdine.test", "") is PasswordChangeResult.Error)

        // Wrong password fails
        assertTrue(vm.loginAdmin("owner@smartdine.test", "WrongPass123") is PasswordChangeResult.Error)
        assertFalse(vm.uiState.value.isAdminAuthenticated)

        // Non-admin role (e.g., customer) rejected
        val customerRoleSupabase = FakeSupabaseService(
            validAdminEmail = "guest@smartdine.test",
            validAdminPassword = "GuestPassword99",
            validAdminRole = "customer"
        )
        val nonAdminVm = RestaurantViewModel(InMemoryMenuStorage(), InMemoryCredentialStorage(), customerRoleSupabase)
        assertTrue(nonAdminVm.loginAdmin("guest@smartdine.test", "GuestPassword99") is PasswordChangeResult.Error)
        assertFalse(nonAdminVm.uiState.value.isAdminAuthenticated)

        // Owner role succeeds
        val ownerRoleSupabase = FakeSupabaseService(
            validAdminEmail = "proprietor@smartdine.test",
            validAdminPassword = "OwnerPassword99",
            validAdminRole = "owner"
        )
        val ownerVm = RestaurantViewModel(InMemoryMenuStorage(), InMemoryCredentialStorage(), ownerRoleSupabase)
        assertTrue(ownerVm.loginAdmin("proprietor@smartdine.test", "OwnerPassword99") is PasswordChangeResult.Success)
        assertTrue(ownerVm.uiState.value.isAdminAuthenticated)

        // Valid admin succeeds
        assertTrue(vm.loginAdmin("owner@smartdine.test", "StrongOwnerPassword99") is PasswordChangeResult.Success)
        assertTrue(vm.uiState.value.isAdminAuthenticated)

        // Admin cannot execute Chef KDS actions
        vm.activateKioskMode()
        vm.logoutAdmin()
        assertFalse(vm.uiState.value.isAdminAuthenticated)
        assertEquals(1, fakeSupabase.signOutCallCount)
    }

    @Test
    fun changeAdminPassword_validatesRulesAndUpdatesViaSupabase() = runBlocking {
        val fakeSupabase = FakeSupabaseService(
            validAdminEmail = "owner@smartdine.test",
            validAdminPassword = "OldOwnerPassword99",
            validAdminRole = "admin"
        )
        val vm = RestaurantViewModel(InMemoryMenuStorage(), InMemoryCredentialStorage(), fakeSupabase)

        // Cannot change password while logged out
        assertTrue(
            vm.changeAdminPassword("OldOwnerPassword99", "NewOwnerPassword99", "NewOwnerPassword99")
                is PasswordChangeResult.Error
        )

        assertTrue(vm.loginAdmin("owner@smartdine.test", "OldOwnerPassword99") is PasswordChangeResult.Success)

        // Too short (< 8 chars) or mismatch fails
        assertTrue(
            vm.changeAdminPassword("OldOwnerPassword99", "short", "short")
                is PasswordChangeResult.Error
        )
        assertTrue(
            vm.changeAdminPassword("OldOwnerPassword99", "NewOwnerPassword99", "DifferentPassword99")
                is PasswordChangeResult.Error
        )

        // Valid update sends new password to Supabase Auth
        assertTrue(
            vm.changeAdminPassword("OldOwnerPassword99", "NewOwnerPassword99", "NewOwnerPassword99")
                is PasswordChangeResult.Success
        )
        assertEquals("NewOwnerPassword99", fakeSupabase.lastUpdatedPassword)
    }

    @Test
    fun staffLifecycleAndCnicAuth_tracksAttendanceRoleRoutingEditAndRelieve() = runBlocking {
        val fakeSupabase = FakeSupabaseService(
            validAdminEmail = "owner@smartdine.test",
            validAdminPassword = "OwnerPassword99",
            validAdminRole = "admin"
        )
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)
        val today = DateUtils.todayIso()

        assertTrue(vm.loginAdmin("owner@smartdine.test", "OwnerPassword99") is PasswordChangeResult.Success)

        // Validation: missing phone must fail
        assertFalse(
            vm.addEmployee(
                name = "Nadia Shah",
                cnic = "34601-7777777-7",
                password = "NadiaPass99",
                phone = "",
                designation = "Pastry Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Street 9, Model Town",
                email = "",
                shiftLabel = "09:00 - 17:00",
                scheduledDayOff = WeekdaySlot.SUNDAY
            )
        )

        // Onboard Chef & General Staff
        assertTrue(
            vm.addEmployee(
                name = "Nadia Shah",
                cnic = "34601-7777777-7",
                password = "NadiaPass99",
                phone = "+92 300 7777111",
                designation = "Pastry Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Street 9, Model Town",
                email = "",
                shiftLabel = "09:00 - 17:00",
                scheduledDayOff = WeekdaySlot.SUNDAY
            )
        )
        assertTrue(
            vm.addEmployee(
                name = "Hamza Ali",
                cnic = "34601-5555555-5",
                password = "HamzaPass99",
                phone = "+92 300 5555111",
                designation = "Floor Host",
                portalRole = StaffPortalRole.GENERAL_STAFF,
                address = "Block C, Gulberg",
                email = "",
                shiftLabel = "12:00 - 20:00",
                scheduledDayOff = WeekdaySlot.NONE
            )
        )

        // Duplicate CNIC rejected
        assertFalse(
            vm.addEmployee(
                name = "Duplicate Chef",
                cnic = "34601-7777777-7",
                password = "AnotherPass99",
                phone = "+92 300 7777111",
                designation = "Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Street 9, Model Town",
                email = "",
                shiftLabel = "09:00 - 17:00",
                scheduledDayOff = WeekdaySlot.SUNDAY
            )
        )

        val nadia = vm.uiState.value.staff.first { it.name == "Nadia Shah" }
        val hamza = vm.uiState.value.staff.first { it.name == "Hamza Ali" }
        vm.logoutAdmin()

        // General Staff signs in via CNIC + Password and marks Present
        val staffLogin = vm.loginEmployeeWithCnic("34601-5555555-5", "HamzaPass99")
        assertTrue(staffLogin is EmployeeLoginResult.Success)
        assertEquals(StaffPortalRole.GENERAL_STAFF, (staffLogin as EmployeeLoginResult.Success).employee.portalRole)
        assertFalse(vm.uiState.value.isAdminAuthenticated)
        assertTrue(vm.markAuthenticatedEmployeePresent())
        assertEquals(
            AttendanceStatus.PRESENT,
            vm.uiState.value.effectiveAttendanceStatus(hamza, today)
        )
        vm.logoutEmployeePortal()

        // Admin edits Nadia and relieves her -> relieved login must fail
        assertTrue(vm.loginAdmin("owner@smartdine.test", "OwnerPassword99") is PasswordChangeResult.Success)
        assertTrue(
            vm.updateEmployee(
                staffId = nadia.id,
                name = "Nadia Shah-Malik",
                cnic = "34601-7777777-8",
                password = "NadiaNewPass99",
                phone = "+92 300 8888222",
                designation = "Delivery Lead",
                portalRole = StaffPortalRole.DELIVERY_RIDER,
                address = "House 21, DHA Phase 5",
                email = "nadia@smartdine.app",
                shiftLabel = "10:00 - 18:00",
                scheduledDayOff = WeekdaySlot.SATURDAY
            )
        )
        vm.overrideAttendance(nadia.id, today, AttendanceStatus.LEAVE)
        assertEquals(
            AttendanceStatus.LEAVE,
            vm.uiState.value.effectiveAttendanceStatus(vm.uiState.value.staff.first { it.id == nadia.id }, today)
        )
        vm.relieveEmployee(nadia.id)
        vm.logoutAdmin()

        assertTrue(vm.loginEmployeeWithCnic("34601-7777777-8", "NadiaNewPass99") is EmployeeLoginResult.Error)
    }

    @Test
    fun changeEmployeePassword_verifiesOldPasswordAndPersistsAcrossRestarts() = runBlocking {
        val fakeSupabase = FakeSupabaseService(
            validAdminEmail = "owner@smartdine.test",
            validAdminPassword = "OwnerPassword99",
            validAdminRole = "admin"
        )
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)

        assertTrue(vm.loginAdmin("owner@smartdine.test", "OwnerPassword99") is PasswordChangeResult.Success)
        assertTrue(
            vm.addEmployee(
                name = "Chef Tariq",
                cnic = "34601-1111111-1",
                password = "TariqPass1",
                phone = "+92 300 1111111",
                designation = "Head Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Kitchen HQ",
                email = "",
                shiftLabel = "11:00 - 20:00",
                scheduledDayOff = WeekdaySlot.NONE
            )
        )
        val chef = vm.uiState.value.staff.first { it.cnic == "34601-1111111-1" }
        vm.logoutAdmin()

        assertTrue(vm.loginEmployeeWithCnic("34601-1111111-1", "TariqPass1") is EmployeeLoginResult.Success)

        val wrongOld = vm.changeEmployeePassword(chef.id, "WrongOld", "TariqNew99", "TariqNew99")
        assertTrue(wrongOld is PasswordChangeResult.Error)

        val ok = vm.changeEmployeePassword(chef.id, "TariqPass1", "TariqNew99", "TariqNew99")
        assertTrue(ok is PasswordChangeResult.Success)

        val restartedVm = RestaurantViewModel(menuStorage, credStorage)
        assertTrue(restartedVm.loginEmployeeWithCnic("34601-1111111-1", "TariqPass1") is EmployeeLoginResult.Error)
        assertTrue(restartedVm.loginEmployeeWithCnic("34601-1111111-1", "TariqNew99") is EmployeeLoginResult.Success)
    }

    @Test
    fun salesAnalytics_tracksDeliveryAndKioskMultiChannelRevenue() {
        val vm = RestaurantViewModel()
        val today = DateUtils.todayIso()
        assertTrue(vm.uiState.value.salesRecords.isEmpty())

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
        assertEquals(delOrder.grandTotalRs, afterDelivery.deliveryRevenueRs)
        assertEquals(1, afterDelivery.deliveryOrderCount)

        // Cancel within 3 minutes -> rolls back deliveryRevenueRs & deliveryOrderCount
        assertTrue(vm.cancelDeliveryOrder(delOrder.orderNumber))
        val afterCancel = vm.uiState.value.salesRecords.first { it.dateIso == today }
        assertEquals(0, afterCancel.deliveryRevenueRs)
        assertEquals(0, afterCancel.deliveryOrderCount)

        // Activate Kiosk Mode without admin password and place Kiosk order
        vm.activateKioskMode()
        vm.addToCart(burger)
        val kioskOrder = vm.placeKioskOrder(PaymentMethod.PAY_AT_COUNTER)
        assertNotNull(kioskOrder)
        val afterKiosk = vm.uiState.value.salesRecords.first { it.dateIso == today }
        assertEquals(kioskOrder!!.grandTotalRs, afterKiosk.kioskRevenueRs)
        assertEquals(1, afterKiosk.kioskOrderCount)
    }

    @Test
    fun adminMenuCrud_persistsChangesAcrossRestarts() = runBlocking {
        val fakeSupabase = FakeSupabaseService(
            validAdminEmail = "owner@smartdine.test",
            validAdminPassword = "OwnerPassword99",
            validAdminRole = "admin"
        )
        val storage = InMemoryMenuStorage()
        val vm = RestaurantViewModel(storage, InMemoryCredentialStorage(), fakeSupabase)
        assertTrue(vm.loginAdmin("owner@smartdine.test", "OwnerPassword99") is PasswordChangeResult.Success)

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
    fun kioskMode_opensAndExitsWithoutAdminPasswordAndIssuesSequentialTokens() {
        val vm = RestaurantViewModel()

        vm.activateKioskMode()
        assertTrue(vm.uiState.value.isKioskModeActive)
        assertEquals(CustomerExperienceMode.KIOSK_MODE, vm.uiState.value.customerMode)

        // Switching to Home Delivery tabs is ignored while Kiosk Mode is active
        vm.selectCustomerTab(CustomerTab.TRACKER)
        assertEquals(CustomerTab.MENU, vm.uiState.value.customerTab)

        // Place first Kiosk order -> Token #1
        val burger = vm.uiState.value.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        val order1 = vm.placeKioskOrder(PaymentMethod.PAY_AT_COUNTER)
        assertNotNull(order1)
        assertEquals(1, order1!!.tokenNumber)
        assertEquals(OrderType.KIOSK, order1.orderType)
        assertEquals("DINE-IN / KIOSK • Token #1", order1.kdsBadgeLabel)
        assertFalse(order1.isPaid)
        assertEquals(0, vm.uiState.value.cartItemCount)
        assertEquals(1, vm.uiState.value.activeKioskTokenOrder?.tokenNumber)

        // Reset for next customer & place second Kiosk order -> Token #2 paid online
        vm.resetKioskForNextCustomer()
        assertEquals(null, vm.uiState.value.activeKioskTokenOrder)
        vm.addToCart(burger)
        val order2 = vm.placeKioskOrder(PaymentMethod.ONLINE)
        assertNotNull(order2)
        assertEquals(2, order2!!.tokenNumber)
        assertTrue(order2.isPaid)

        // Exit back to Home Delivery without requiring admin password
        vm.exitKioskMode()
        assertFalse(vm.uiState.value.isKioskModeActive)
        assertEquals(CustomerExperienceMode.HOME_DELIVERY, vm.uiState.value.customerMode)
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
    fun customerAuthAndOrderHistory_persistsAcrossRestarts() = runBlocking {
        val fakeSupabase = FakeSupabaseService(validEmailOtp = "654321")
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)

        val badEmail = vm.sendCustomerEmailOtp("Zara", "invalid")
        assertTrue(badEmail is PasswordChangeResult.Error)

        val sendOk = vm.sendCustomerEmailOtp("Zara Ali", "zara@smartdine.app")
        assertTrue(sendOk is PasswordChangeResult.Success)
        assertTrue(fakeSupabase.sentOtpEmails.contains("zara@smartdine.app"))

        // Hardcoded legacy demo OTP "1234" or any wrong code must fail strict verification
        val wrongOtp = vm.verifyCustomerEmailOtp("Zara Ali", "zara@smartdine.app", "1234")
        assertTrue(wrongOtp is PasswordChangeResult.Error)
        assertEquals(null, vm.uiState.value.loggedInCustomer)

        val emailOk = vm.verifyCustomerEmailOtp("Zara Ali", "zara@smartdine.app", "654321")
        assertTrue(emailOk is PasswordChangeResult.Success)
        assertEquals("Zara Ali", vm.uiState.value.loggedInCustomer?.name)
        assertEquals("zara@smartdine.app", vm.uiState.value.loggedInCustomer?.email)
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
        val restartedVm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)
        assertEquals("Zara Ali", restartedVm.uiState.value.loggedInCustomer?.name)
        assertTrue(restartedVm.uiState.value.homeDeliveryOrders.any { it.orderNumber == placedOrderNum })

        restartedVm.logoutCustomer()
        assertEquals(null, restartedVm.uiState.value.loggedInCustomer)
    }

    @Test
    fun googleOAuthPkce_authenticatesRealProfileAndPersistsAvatarAndSession() = runBlocking {
        val fakeSupabase = FakeSupabaseService()
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)

        val authUrlResult = vm.startGoogleSignIn()
        assertTrue(authUrlResult.isSuccess)
        val authUrl = authUrlResult.getOrThrow()
        assertTrue(authUrl.contains("provider=google"))
        assertTrue(authUrl.contains("smartdine%3A%2F%2Flogin-callback"))
        assertTrue(authUrl.contains("code_challenge_method=s256"))
        assertNotNull(credStorage.loadPendingOAuthVerifier())

        // Cancelled or failed OAuth callback must report error and clear verifier
        val failedCallback = vm.handleGoogleOAuthCallback(
            code = null,
            accessTokenFromFragment = null,
            errorMessage = "access_denied"
        )
        assertTrue(failedCallback is PasswordChangeResult.Error)
        assertEquals(null, credStorage.loadPendingOAuthVerifier())
        assertEquals(null, vm.uiState.value.loggedInCustomer)

        // Start again and simulate valid PKCE callback from smartdine://login-callback?code=...
        assertTrue(vm.startGoogleSignIn().isSuccess)
        val successCallback = vm.handleGoogleOAuthCallback(
            code = "valid-google-code",
            accessTokenFromFragment = null,
            errorMessage = null
        )
        assertTrue(successCallback is PasswordChangeResult.Success)
        val profile = vm.uiState.value.loggedInCustomer
        assertNotNull(profile)
        assertEquals("Bilal Google", profile!!.name)
        assertEquals("bilal.google@gmail.com", profile.email)
        assertEquals("https://lh3.googleusercontent.com/a/avatar.jpg", profile.avatarUrl)
        assertEquals(CustomerAuthProvider.GOOGLE, profile.provider)

        // Persists across app restarts
        val restartedVm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)
        assertEquals("https://lh3.googleusercontent.com/a/avatar.jpg", restartedVm.uiState.value.loggedInCustomer?.avatarUrl)
        assertEquals(CustomerAuthProvider.GOOGLE, restartedVm.uiState.value.loggedInCustomer?.provider)
    }

    @Test
    fun deliveryCancellationWindow_allowsCancelWithin3MinsAndLocksAfterExpiry() = runBlocking {
        val fakeSupabase = FakeSupabaseService(validEmailOtp = "654321")
        val menuStorage = InMemoryMenuStorage()
        val credStorage = InMemoryCredentialStorage()
        val vm = RestaurantViewModel(menuStorage, credStorage, fakeSupabase)
        assertTrue(vm.sendCustomerEmailOtp("Omar", "omar@example.com") is PasswordChangeResult.Success)
        assertTrue(
            vm.verifyCustomerEmailOtp("Omar", "omar@example.com", "654321")
                is PasswordChangeResult.Success
        )

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
    fun deliveryOrder_syncsFromChefReadyToRiderPickupAndDelivered() = runBlocking {
        val fakeSupabase = FakeSupabaseService(
            validAdminEmail = "owner@smartdine.test",
            validAdminPassword = "OwnerPassword99",
            validAdminRole = "admin",
            validEmailOtp = "654321"
        )
        val vm = RestaurantViewModel(InMemoryMenuStorage(), InMemoryCredentialStorage(), fakeSupabase)

        // Admin creates Chef and Rider records
        assertTrue(vm.loginAdmin("owner@smartdine.test", "OwnerPassword99") is PasswordChangeResult.Success)
        assertTrue(
            vm.addEmployee(
                name = "Chef Tariq",
                cnic = "34601-1111111-1",
                password = "TariqPass1",
                phone = "+92 300 1111111",
                designation = "Head Chef",
                portalRole = StaffPortalRole.KITCHEN_CHEF,
                address = "Kitchen HQ",
                email = "",
                shiftLabel = "11:00 - 20:00",
                scheduledDayOff = WeekdaySlot.NONE
            )
        )
        assertTrue(
            vm.addEmployee(
                name = "Rider Bilal",
                cnic = "34601-2222222-2",
                password = "BilalPass2",
                phone = "+92 300 2222222",
                designation = "Delivery Rider",
                portalRole = StaffPortalRole.DELIVERY_RIDER,
                address = "Dispatch Desk",
                email = "",
                shiftLabel = "12:00 - 22:00",
                scheduledDayOff = WeekdaySlot.NONE
            )
        )
        vm.logoutAdmin()

        assertTrue(vm.sendCustomerEmailOtp("Hina", "hina@example.com") is PasswordChangeResult.Success)
        assertTrue(
            vm.verifyCustomerEmailOtp("Hina", "hina@example.com", "654321")
                is PasswordChangeResult.Success
        )
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
        assertTrue(vm.loginEmployeeWithCnic("34601-1111111-1", "TariqPass1") is EmployeeLoginResult.Success)
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
        val riderLogin = vm.loginEmployeeWithCnic("34601-2222222-2", "BilalPass2")
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
        vm.activateKioskMode()
        val burger = vm.uiState.value.menuItems.first { it.id == "item_1" }
        vm.addToCart(burger)
        val kioskOrder = vm.placeKioskOrder(PaymentMethod.ONLINE)!!

        val kioskReceipt = ThermalReceiptData.fromOrder(kioskOrder)
        assertEquals("RCP-${1000 + kioskOrder.orderNumber}", kioskReceipt.receiptId)
        assertEquals("Dine-In / Kiosk • Token #1", kioskReceipt.orderTypeLabel)
        assertTrue(kioskReceipt.isPaid)
        assertEquals("Online Payment", kioskReceipt.paymentMethodLabel)
        assertEquals(kioskOrder.subtotalRs, kioskReceipt.subtotalRs)
        assertEquals(kioskOrder.taxRs, kioskReceipt.taxRs)
        assertEquals(kioskOrder.grandTotalRs, kioskReceipt.grandTotalRs)

        vm.exitKioskMode()
        vm.addToCart(burger)
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Amna",
                customerPhone = "+92 300 1234567",
                deliveryAddress = "House 5, Sector G-10",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.CASH_ON_DELIVERY
            )
        )
        val deliveryOrder = vm.uiState.value.orders.first { it.isDelivery }
        val deliveryReceipt = ThermalReceiptData.fromOrder(deliveryOrder)
        assertEquals("Home Delivery", deliveryReceipt.orderTypeLabel)
        assertNotNull(deliveryReceipt.customerDetail)
    }

    @Test
    fun supabaseSync_hydratesRemoteDataWithoutSeedingDummyOperationalData() = runBlocking {
        val fakeSupabase = FakeSupabaseService(
            validAdminEmail = "owner@smartdine.test",
            validAdminPassword = "OwnerPassword99",
            validAdminRole = "admin",
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

        // Initial hydration pulled remote menu and NEVER seeded fake local orders/staff into Supabase
        assertEquals(SupabaseSyncStatus.LIVE_SYNCED, vm.uiState.value.supabaseSyncStatus)
        assertEquals(listOf("remote_1"), vm.uiState.value.menuItems.map { it.id })
        assertEquals(0, fakeSupabase.upsertedOrdersBatchCount)
        assertEquals(0, fakeSupabase.upsertedStaffBatchCount)
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
        fakeSupabase.emitEvent(SupabaseRealtimeEvent.MenuUpserted(pushedDish))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.menuItems.any { it.id == "remote_2" && it.title == "Live Synced Karahi" })

        // Outgoing customer delivery order syncs to Supabase
        vm.addToCart(pushedDish)
        assertTrue(
            vm.placeHomeDeliveryOrder(
                customerName = "Saad",
                customerPhone = "+92 300 1230000",
                deliveryAddress = "House 8, Model Town",
                gpsCoordinatesLabel = null,
                gpsMapUrl = null,
                isGpsFallback = false,
                paymentMethod = PaymentMethod.CASH_ON_DELIVERY
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeSupabase.singleUpsertedOrders.any { it.customerName == "Saad" })

        // Outgoing Admin menu delete syncs delete call to Supabase
        assertTrue(vm.loginAdmin("owner@smartdine.test", "OwnerPassword99") is PasswordChangeResult.Success)
        vm.deleteMenuItem("remote_2")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeSupabase.deletedMenuIds.contains("remote_2"))
        assertFalse(vm.uiState.value.menuItems.any { it.id == "remote_2" })
    }
}

private class FakeSupabaseService(
    private val validAdminEmail: String = "owner@smartdine.test",
    private val validAdminPassword: String = "OwnerPassword99",
    private val validAdminRole: String = "admin",
    private val validEmailOtp: String = "654321",
    private val remoteMenu: List<MenuItem> = emptyList(),
    private val remoteOrders: List<KitchenOrder> = emptyList(),
    private val remoteStaff: List<StaffMember> = emptyList()
) : SupabaseService {
    override val isRemoteEnabled: Boolean = true
    private val _events = MutableSharedFlow<SupabaseRealtimeEvent>(extraBufferCapacity = 32)
    override val realtimeEvents: SharedFlow<SupabaseRealtimeEvent> = _events

    var realtimeStarted = false
    var upsertedOrdersBatchCount = 0
    var upsertedStaffBatchCount = 0
    var signOutCallCount = 0
    var lastUpdatedPassword: String? = null
    var lastGoogleVerifier: String? = null
    val sentOtpEmails = mutableMapOf<String, String>()
    val singleUpsertedOrders = mutableListOf<KitchenOrder>()
    val deletedMenuIds = mutableListOf<String>()

    fun emitEvent(event: SupabaseRealtimeEvent) {
        _events.tryEmit(event)
    }

    override fun startRealtimeSync() {
        realtimeStarted = true
    }

    override fun stopRealtimeSync() {
        realtimeStarted = false
    }

    override suspend fun signInAdmin(email: String, password: String): SupabaseAuthResult {
        if (!email.equals(validAdminEmail, ignoreCase = true) || password != validAdminPassword) {
            return SupabaseAuthResult.Error("Invalid email or password.")
        }
        val normalizedRole = validAdminRole.trim().lowercase()
        if (normalizedRole != "admin" && normalizedRole != "owner") {
            return SupabaseAuthResult.Error("Access denied: admin or owner privileges are required.")
        }
        return SupabaseAuthResult.Success(
            SupabaseAuthSession(
                accessToken = "fake-jwt-token",
                userId = "admin-uid-1",
                email = email,
                role = normalizedRole,
                fullName = "Owner"
            )
        )
    }

    override fun beginGoogleOAuth(onPersistVerifier: ((String) -> Unit)?): Result<String> {
        val verifier = "test-pkce-verifier"
        lastGoogleVerifier = verifier
        onPersistVerifier?.invoke(verifier)
        return Result.success(
            "https://example.supabase.co/auth/v1/authorize?provider=google&redirect_to=smartdine%3A%2F%2Flogin-callback&code_challenge=test&code_challenge_method=s256"
        )
    }

    override suspend fun completeGoogleOAuth(
        code: String?,
        accessTokenFromFragment: String?,
        errorMessage: String?,
        persistedVerifier: String?
    ): SupabaseAuthResult {
        if (!errorMessage.isNullOrBlank()) {
            return SupabaseAuthResult.Error(errorMessage)
        }
        val validCode = code == "valid-google-code" && persistedVerifier == lastGoogleVerifier
        val validFragment = accessTokenFromFragment == "valid-fragment-token"
        if (!validCode && !validFragment) {
            return SupabaseAuthResult.Error("Google sign-in could not be completed.")
        }
        return SupabaseAuthResult.Success(
            SupabaseAuthSession(
                accessToken = "fake-google-jwt",
                userId = "google-uid-1",
                email = "bilal.google@gmail.com",
                role = "customer",
                fullName = "Bilal Google",
                avatarUrl = "https://lh3.googleusercontent.com/a/avatar.jpg"
            )
        )
    }

    override suspend fun sendEmailOtp(email: String, fullName: String): Result<Unit> {
        sentOtpEmails[email.trim().lowercase()] = fullName.trim()
        return Result.success(Unit)
    }

    override suspend fun verifyEmailOtp(email: String, token: String): SupabaseAuthResult {
        val cleanEmail = email.trim().lowercase()
        if (!sentOtpEmails.containsKey(cleanEmail) || token.trim() != validEmailOtp) {
            return SupabaseAuthResult.Error("Invalid or expired verification code.")
        }
        return SupabaseAuthResult.Success(
            SupabaseAuthSession(
                accessToken = "fake-customer-jwt",
                userId = "cust-uid-$cleanEmail",
                email = cleanEmail,
                role = "customer",
                fullName = sentOtpEmails[cleanEmail].orEmpty()
            )
        )
    }

    override suspend fun updateUserPassword(newPassword: String): Result<Unit> {
        lastUpdatedPassword = newPassword
        return Result.success(Unit)
    }

    override suspend fun signOutAuth() {
        signOutCallCount++
    }

    override suspend fun authenticateEmployee(cnic: String, passwordHash: String): Result<StaffMember?> {
        val cleanKey = cnic.filter { it.isLetterOrDigit() }
        val match = remoteStaff.firstOrNull {
            it.cnic.filter { ch -> ch.isLetterOrDigit() } == cleanKey && it.passwordHash == passwordHash
        }
        return Result.success(match)
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

    override suspend fun fetchStaff(): Result<List<StaffMember>> = Result.success(remoteStaff)
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
