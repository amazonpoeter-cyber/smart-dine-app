package com.example

import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.model.AlertType
import com.example.model.AttendanceRecord
import com.example.model.AttendanceStatus
import com.example.model.DailySalesRecord
import com.example.model.KitchenOrder
import com.example.model.MenuArtwork
import com.example.model.MenuCategory
import com.example.model.MenuItem
import com.example.model.OrderLineItem
import com.example.model.OrderStatus
import com.example.model.OrderType
import com.example.model.PaymentMethod
import com.example.model.ReviewEntry
import com.example.model.SalesBarPoint
import com.example.model.SpiceLevel
import com.example.model.StaffMember
import com.example.model.StaffPortalRole
import com.example.model.WhatsAppAlert
import com.example.util.WeekdaySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseSyncTest {

    @Test
    fun supabaseConfig_validatesHttpsAndRejectsPlaceholderOrSecretKeys() {
        val valid = SupabaseConfig(
            projectUrl = "https://exampleproject.supabase.co/",
            publishableKey = "sb_publishable_demo_key_12345"
        )
        assertTrue(valid.isConfigured)
        assertEquals("https://exampleproject.supabase.co", valid.cleanUrl)

        val placeholder = SupabaseConfig(
            projectUrl = "https://exampleproject.supabase.co",
            publishableKey = "YOUR_SUPABASE_PUBLISHABLE_KEY"
        )
        assertFalse(placeholder.isConfigured)

        val secretKey = SupabaseConfig(
            projectUrl = "https://exampleproject.supabase.co",
            publishableKey = "sb_secret_forbidden_key"
        )
        assertFalse(secretKey.isConfigured)

        val insecureHttp = SupabaseConfig(
            projectUrl = "http://exampleproject.supabase.co",
            publishableKey = "sb_publishable_demo_key_12345"
        )
        assertFalse(insecureHttp.isConfigured)
    }

    @Test
    fun jsonRoundTrip_preservesAllSupabaseDomainModels() {
        val item = MenuItem(
            id = "item_99",
            title = "Lahori Karahi",
            description = "Wok-seared chicken with fresh ginger",
            category = MenuCategory.MAINS,
            priceRs = 1650,
            prepTimeRange = "20-25 min",
            spiceLevel = SpiceLevel.SPICY,
            artwork = MenuArtwork.CUSTOM_DISH,
            imageUrl = "https://cdn.example.com/karahi.jpg",
            isAvailable = true,
            isLowStock = true
        )
        val parsedItem = SupabaseClient.jsonToMenuItem(SupabaseClient.menuItemToJson(item))
        assertEquals(item, parsedItem)

        val order = KitchenOrder(
            orderNumber = 205,
            tableCode = "DELIVERY",
            items = listOf(OrderLineItem("item_99", "Lahori Karahi", 1650, 2, "Extra ginger")),
            status = OrderStatus.ON_THE_WAY,
            elapsedSeconds = 320,
            orderType = OrderType.DELIVERY,
            tokenNumber = null,
            customerId = "cust_1",
            customerName = "Usman",
            customerPhone = "+92 300 7788990",
            deliveryAddress = "House 19, DHA Phase 4",
            gpsCoordinatesLabel = "31.4697, 74.3849",
            gpsMapUrl = "https://maps.google.com/?q=31.4697,74.3849",
            isGpsFallback = false,
            cancelWindowRemainingSeconds = 0,
            isPaid = true,
            paymentMethod = PaymentMethod.ONLINE_DELIVERY,
            placedAtLabel = "5m ago",
            orderDateIso = "2026-11-27",
            completedDateIso = null,
            completedTimeLabel = null,
            assignedRiderId = "st_3",
            assignedRiderName = "Rider Bilal"
        )
        val parsedOrder = SupabaseClient.jsonToOrder(SupabaseClient.orderToJson(order))
        assertEquals(order, parsedOrder)

        val review = ReviewEntry(
            id = "rev_88",
            authorName = "Ayesha",
            tableCode = "DELIVERY",
            rating = 5,
            tags = listOf("Hot & Fresh", "Fast Delivery"),
            comment = "Delicious food!",
            timestampLabel = "Just now",
            isPublishedToSite = true
        )
        val parsedReview = SupabaseClient.jsonToReview(SupabaseClient.reviewToJson(review))
        assertEquals(review, parsedReview)

        val staff = StaffMember(
            id = "st_9",
            name = "Chef Kamran",
            cnic = "34601-9999999-9",
            passwordHash = "abcd1234",
            designation = "Sous Chef",
            portalRole = StaffPortalRole.KITCHEN_CHEF,
            phone = "+92 301 1112223",
            address = "Gulberg II, Lahore",
            email = "kamran@smartdine.app",
            shiftLabel = "12:00 - 21:00",
            isOnDuty = true,
            isRelieved = false,
            scheduledDayOff = WeekdaySlot.FRIDAY
        )
        val parsedStaff = SupabaseClient.jsonToStaff(SupabaseClient.staffToJson(staff))
        assertEquals(staff, parsedStaff)

        val attendance = AttendanceRecord(
            staffId = "st_9",
            dateIso = "2026-11-27",
            status = AttendanceStatus.PRESENT,
            checkInTimeLabel = "12:02 PM",
            isAdminOverride = false
        )
        val parsedAttendance = SupabaseClient.jsonToAttendance(SupabaseClient.attendanceToJson(attendance))
        assertEquals(attendance, parsedAttendance)

        val alert = WhatsAppAlert(
            id = "al_77",
            title = "New Delivery #205",
            message = "Karahi x2",
            timeLabel = "Just now",
            type = AlertType.DELIVERY_ORDER,
            isAcknowledged = false
        )
        val parsedAlert = SupabaseClient.jsonToAlert(SupabaseClient.alertToJson(alert))
        assertEquals(alert, parsedAlert)

        val sales = DailySalesRecord(
            dateIso = "2026-11-27",
            grossRevenueRs = 184500,
            onlineRevenueRs = 112000,
            cashRevenueRs = 72500,
            deliveryRevenueRs = 106800,
            kioskRevenueRs = 77700,
            deliveryOrderCount = 34,
            kioskOrderCount = 26,
            topSellerLabel = "Chicken Biryani",
            orderCount = 60,
            hourlyBars = listOf(SalesBarPoint("12p", 24000), SalesBarPoint("6p", 45000))
        )
        val parsedSales = SupabaseClient.jsonToSalesRecord(SupabaseClient.salesRecordToJson(sales))
        assertNotNull(parsedSales)
        assertEquals(sales, parsedSales)
    }

    @Test
    fun beginGoogleOAuth_generatesPkceS256AuthorizeUrlAndPersistsVerifier() {
        val client = SupabaseClient(
            config = SupabaseConfig(
                projectUrl = "https://exampleproject.supabase.co",
                publishableKey = "sb_publishable_demo_key_12345"
            )
        )
        var capturedVerifier: String? = null
        val urlResult = client.beginGoogleOAuth { verifier -> capturedVerifier = verifier }
        assertTrue(urlResult.isSuccess)
        val url = urlResult.getOrThrow()
        assertTrue(url.startsWith("https://exampleproject.supabase.co/auth/v1/authorize"))
        assertTrue(url.contains("provider=google"))
        assertTrue(url.contains("redirect_to=smartdine%3A%2F%2Flogin-callback"))
        assertTrue(url.contains("code_challenge_method=s256"))
        assertTrue(url.contains("code_challenge="))
        assertNotNull(capturedVerifier)
        assertTrue(capturedVerifier!!.length >= 43)
    }
}
