package com.example.util

import com.example.model.AppLanguage
import com.example.model.CustomerTab
import com.example.model.HomeMenuFilter
import com.example.model.MenuCategory
import com.example.model.OrderStatus
import com.example.model.PaymentMethod

data class LocalizedStrings(
    val isUrdu: Boolean,
    val appTaglineDelivery: String,
    val appTaglineKiosk: String,
    val atRestaurantKioskTitle: String,
    val exitKioskModeTitle: String,
    val kioskModeLockedNote: String,
    val kioskActivationTitle: String,
    val kioskActivationSubtitle: String,
    val kioskIdLabel: String,
    val kioskPasswordLabel: String,
    val unlockKioskBtn: String,
    val kioskExitTitle: String,
    val kioskExitSubtitle: String,
    val exitKioskBtn: String,
    val kioskBannerHeadline: String,
    val kioskBannerSubtitle: String,
    val kioskAllCategories: String,
    val kioskCheckoutTitle: String,
    val kioskCheckoutCta: String,
    val kioskPayAtCounter: String,
    val kioskPayAtCounterSub: String,
    val kioskPayOnline: String,
    val kioskPayOnlineSub: String,
    val kioskPlaceOrderBtn: String,
    val kioskTokenLabel: String,
    val kioskOrderSentTitle: String,
    val kioskOrderSentSubtitle: String,
    val kioskAutoResetPrefix: String,
    val kioskStartNewOrderBtn: String,
    val kioskPrintTokenReceipt: String,
    val appName: String,
    val adminAppName: String,
    val kioskThankYouHeadline: String,
    val kioskThankYouNote: String,
    val receiptThankYouFooter: String,
    val drawerTitle: String,
    val kioskSectionLabel: String,
    val staffManagementSection: String,
    val employeeLogin: String,
    val adminLogin: String,
    val homeDeliveryLabel: String,
    val deliveryEta: String,
    val myOrdersTitle: String,
    val myOrdersSubtitle: String,
    val logoutLabel: String,
    val signInLabel: String,
    val searchMenuPlaceholder: String,
    val resetFilters: String,
    val noDishesMatch: String,
    val addButton: String,
    val soldOut: String,
    val checkoutCta: String,
    val placeOrderCta: String,
    val deliveryCartSubtitle: String,
    val kioskCartSubtitle: String,
    val itemsLabel: String,
    val itemLabel: String,
    val reviewsTitle: String,
    val rateVisit: String,
    val cancelBtn: String,
    val closeBtn: String,
    val authModalTitle: String,
    val authModalSubtitle: String,
    val continueWithGoogle: String,
    val emailTab: String,
    val phoneTab: String,
    val fullNameLabel: String,
    val emailLabel: String,
    val passwordLabel: String,
    val phoneLabel: String,
    val otpLabel: String,
    val sendOtpBtn: String,
    val verifyAndContinueBtn: String,
    val loginWithEmailBtn: String,
    val checkoutTitle: String,
    val customerNameLabel: String,
    val deliveryAddressLabel: String,
    val shareGpsOptional: String,
    val shareGpsBtn: String,
    val gpsPreviewAttached: String,
    val gpsLiveAttached: String,
    val paymentMethodLabel: String,
    val validationRequiredMsg: String,
    val noActiveDeliveryOrders: String,
    val browseMenuTip: String,
    val exploreMenuBtn: String,
    val onTheWayBanner: String,
    val cancelOrderBtn: String,
    val leftLabel: String,
    val orderConfirmedLocked: String,
    val orderCancelledBadge: String,
    val estimatedPrepTime: String,
    val enjoyYourMeal: String,
    val paidVia: String,
    val paymentLabel: String,
    val subtotal: String,
    val taxGst: String,
    val grandTotal: String,
    val feedbackTitle: String,
    val feedbackPrompt: String,
    val whatStoodOut: String,
    val optionalComment: String,
    val submitFeedback: String,
    val websiteReviewsLive: String,
    val addToCartLabel: String,
    val specialNoteLabel: String,
    val printDownloadReceipt: String,
    val printThermalReceipt: String,
    val downloadReceipt: String,
    val thermalReceiptTitle: String,
    val thankYouDining: String,
    val receiptLabel: String,
    val orderTypeLabel: String
) {
    fun tabLabel(tab: CustomerTab): String = when (tab) {
        CustomerTab.MENU -> if (isUrdu) "مینو" else "Menu"
        CustomerTab.TRACKER -> if (isUrdu) "میرے آرڈرز" else "My Orders"
        CustomerTab.REVIEWS -> if (isUrdu) "ریویوز" else "Reviews"
    }

    fun filterLabel(filter: HomeMenuFilter): String = when (filter) {
        HomeMenuFilter.ALL -> if (isUrdu) "سب" else "All"
        HomeMenuFilter.MAINS -> if (isUrdu) "مین ڈشز" else "Mains"
        HomeMenuFilter.DRINKS -> if (isUrdu) "مشروبات" else "Drinks"
        HomeMenuFilter.DESSERTS -> if (isUrdu) "میٹھا" else "Desserts"
        HomeMenuFilter.SPICY -> if (isUrdu) "مصالحے دار" else "Spicy"
        HomeMenuFilter.UNDER_1000 -> if (isUrdu) "Rs 1000 سے کم" else "Under Rs 1000"
    }

    fun categoryLabel(category: MenuCategory): String = when (category) {
        MenuCategory.MAINS -> if (isUrdu) "مین ڈشز" else "Mains"
        MenuCategory.DRINKS -> if (isUrdu) "مشروبات" else "Drinks"
        MenuCategory.DESSERTS -> if (isUrdu) "میٹھا" else "Desserts"
    }

    fun orderStepLabel(status: OrderStatus, isDelivery: Boolean): String = when (status) {
        OrderStatus.NEW -> if (isUrdu) "آرڈر موصول ہوا" else if (isDelivery) "Order Received" else "Order received"
        OrderStatus.PREPARING -> if (isUrdu) "تیار ہو رہا ہے" else "Preparing"
        OrderStatus.READY -> if (isUrdu) "ڈسپیچ کے لیے تیار" else if (isDelivery) "Ready for Dispatch" else "Ready"
        OrderStatus.ON_THE_WAY -> if (isUrdu) "راستے میں ہے" else "On the Way"
        OrderStatus.SERVED -> if (isUrdu) {
            if (isDelivery) "پہنچا دیا گیا" else "پیش کر دیا گیا"
        } else {
            if (isDelivery) "Delivered" else "Served"
        }
        OrderStatus.CANCELLED -> if (isUrdu) "منسوخ شدہ" else "Cancelled"
    }

    fun orderStepSubtitle(status: OrderStatus, isDelivery: Boolean): String = when (status) {
        OrderStatus.NEW -> if (isUrdu) "آرڈر کچن میں بھیج دیا گیا ہے" else "Ticket queued on Kitchen Display"
        OrderStatus.PREPARING -> if (isUrdu) "شیف آپ کا کھانا تیار کر رہے ہیں" else "Chefs are preparing your dishes"
        OrderStatus.READY -> if (isUrdu) {
            if (isDelivery) "آرڈر پیک ہو چکا ہے، رائیڈر کے حوالے کیا جا رہا ہے" else "کاؤنٹر پر تیار ہے"
        } else {
            if (isDelivery) "Packed & ready for rider dispatch" else "Ready at pickup counter"
        }
        OrderStatus.ON_THE_WAY -> onTheWayBanner
        OrderStatus.SERVED -> if (isUrdu) {
            if (isDelivery) "آپ کے پتے پر پہنچا دیا گیا ہے" else "آرڈر مکمل ہو گیا"
        } else {
            if (isDelivery) "Delivered to your doorstep" else "Order completed"
        }
        OrderStatus.CANCELLED -> if (isUrdu) "یہ آرڈر منسوخ کر دیا گیا ہے" else "Order was cancelled within the 3-minute window"
    }

    fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH_ON_DELIVERY -> if (isUrdu) "کیش آن ڈیلیوری (COD)" else "Cash on Delivery (COD)"
        PaymentMethod.ONLINE_DELIVERY -> if (isUrdu) "آن لائن ادائیگی (Card / EasyPaisa / JazzCash)" else "Online Payment"
        PaymentMethod.ONLINE -> if (isUrdu) "آن لائن ادائیگی" else "Online Payment"
        PaymentMethod.CASH_AT_TABLE -> if (isUrdu) "میز پر نقد ادائیگی" else "Cash at Table"
        PaymentMethod.PAY_AT_COUNTER -> if (isUrdu) "کاؤنٹر پر نقد ادائیگی" else "Cash at Counter"
    }
}

object AppStrings {
    private val en = LocalizedStrings(
        isUrdu = false,
        appTaglineDelivery = "At Home • Home Delivery",
        appTaglineKiosk = "At Restaurant • Self-Order Kiosk",
        atRestaurantKioskTitle = "At Restaurant (Kiosk Mode)",
        exitKioskModeTitle = "Exit Kiosk Mode",
        kioskModeLockedNote = "Self-ordering kiosk is locked for dine-in guests. Staff password required to exit.",
        kioskActivationTitle = "Activate Restaurant Kiosk",
        kioskActivationSubtitle = "Enter Kiosk ID & Password to switch this terminal into locked self-ordering Kiosk Mode.",
        kioskIdLabel = "Kiosk ID",
        kioskPasswordLabel = "Kiosk Password",
        unlockKioskBtn = "Unlock Kiosk Mode",
        kioskExitTitle = "Exit Kiosk Mode",
        kioskExitSubtitle = "Enter Kiosk or Admin password to exit locked Kiosk Mode and return to Home Delivery.",
        exitKioskBtn = "Exit Kiosk Mode",
        kioskBannerHeadline = "Order Here • Fresh & Fast",
        kioskBannerSubtitle = "Tap items to build your dine-in tray and get your Kitchen Token #",
        kioskAllCategories = "All Menu",
        kioskCheckoutTitle = "Review Kiosk Order",
        kioskCheckoutCta = "Pay & Get Token",
        kioskPayAtCounter = "Cash at Counter",
        kioskPayAtCounterSub = "Pay cash at the front counter with your Token #",
        kioskPayOnline = "Online Payment",
        kioskPayOnlineSub = "Card / EasyPaisa / JazzCash",
        kioskPlaceOrderBtn = "Confirm & Print Token",
        kioskTokenLabel = "ORDER TOKEN",
        kioskOrderSentTitle = "Order Sent to Kitchen!",
        kioskOrderSentSubtitle = "Please keep your token ready for counter pickup.",
        kioskAutoResetPrefix = "Resetting for next guest in",
        kioskStartNewOrderBtn = "Start New Order",
        kioskPrintTokenReceipt = "Print / Download 80mm Token Receipt",
        appName = "Smart Dine",
        adminAppName = "Smart Dine Admin",
        kioskThankYouHeadline = "Thank you for dining with Smart Dine!",
        kioskThankYouNote = "Thank you for dining with Smart Dine! Your order is being freshly prepared. Please collect your food when your Token Number is called.",
        receiptThankYouFooter = "Thank you for choosing Smart Dine! Come again.",
        drawerTitle = "Smart Dine Menu",
        kioskSectionLabel = "NAVIGATION & KIOSK",
        staffManagementSection = "STAFF & MANAGEMENT",
        employeeLogin = "Employee Login",
        adminLogin = "Admin Login",
        homeDeliveryLabel = "Home Delivery",
        deliveryEta = "25–35 min",
        myOrdersTitle = "My Orders (Live Tracking & History)",
        myOrdersSubtitle = "Track active deliveries or view past orders",
        logoutLabel = "Logout",
        signInLabel = "Sign In",
        searchMenuPlaceholder = "Search dishes, biryani, pizza, drinks...",
        resetFilters = "Reset Filters",
        noDishesMatch = "No dishes match your filter",
        addButton = "Add",
        soldOut = "SOLD OUT",
        checkoutCta = "Checkout",
        placeOrderCta = "Place Order",
        deliveryCartSubtitle = "Add delivery address & GPS pin",
        kioskCartSubtitle = "Tap to pay & generate Token #",
        itemsLabel = "Items",
        itemLabel = "Item",
        reviewsTitle = "Reviews",
        rateVisit = "Rate your visit",
        cancelBtn = "Cancel",
        closeBtn = "Close",
        authModalTitle = "Sign In for Home Delivery",
        authModalSubtitle = "Save your delivery history & track live rider status",
        continueWithGoogle = "Continue with Google",
        emailTab = "Email & Password",
        phoneTab = "Phone & OTP",
        fullNameLabel = "Full Name",
        emailLabel = "Email Address",
        passwordLabel = "Password",
        phoneLabel = "Phone Number",
        otpLabel = "Verification OTP (Demo: 1234)",
        sendOtpBtn = "Send OTP",
        verifyAndContinueBtn = "Verify & Continue",
        loginWithEmailBtn = "Sign Up / Login",
        checkoutTitle = "Home Delivery Checkout",
        customerNameLabel = "Customer Name",
        deliveryAddressLabel = "Full Delivery Address",
        shareGpsOptional = "Share Live GPS Location (Optional)",
        shareGpsBtn = "Share My Current Location (GPS)",
        gpsPreviewAttached = "GPS Preview Pin Attached",
        gpsLiveAttached = "Live GPS Pin Attached",
        paymentMethodLabel = "Payment Method",
        validationRequiredMsg = "Please fill Name, Phone Number, and Full Delivery Address (*).",
        noActiveDeliveryOrders = "No active home delivery orders yet",
        browseMenuTip = "Browse the digital menu to place a fresh order straight to the kitchen.",
        exploreMenuBtn = "Explore Menu",
        onTheWayBanner = "Your order is on the way and will reach you shortly!",
        cancelOrderBtn = "Cancel Order",
        leftLabel = "left",
        orderConfirmedLocked = "Order Confirmed & Locked",
        orderCancelledBadge = "Cancelled",
        estimatedPrepTime = "Estimated prep time: 15-25 min",
        enjoyYourMeal = "Enjoy your meal!",
        paidVia = "Paid via",
        paymentLabel = "Payment",
        subtotal = "Subtotal",
        taxGst = "Tax (5% GST)",
        grandTotal = "Grand Total",
        feedbackTitle = "One-Click Feedback",
        feedbackPrompt = "How was your experience with Smart Dine?",
        whatStoodOut = "What stood out?",
        optionalComment = "Optional comment",
        submitFeedback = "Submit Feedback",
        websiteReviewsLive = "Website Reviews (Live)",
        addToCartLabel = "Add",
        specialNoteLabel = "Special kitchen note (optional)",
        printDownloadReceipt = "Print / Download Receipt",
        printThermalReceipt = "Print Thermal Receipt",
        downloadReceipt = "Download Receipt",
        thermalReceiptTitle = "80mm Thermal Receipt",
        thankYouDining = "Thank you for dining with us!",
        receiptLabel = "Receipt",
        orderTypeLabel = "Order Type"
    )

    private val ur = LocalizedStrings(
        isUrdu = true,
        appTaglineDelivery = "گھر پر منگوائیں • ہوم ڈیلیوری",
        appTaglineKiosk = "ریستوران میں • سیلف آرڈر کیوسک",
        atRestaurantKioskTitle = "ریستوران میں (کیوسک موڈ)",
        exitKioskModeTitle = "کیوسک موڈ بند کریں",
        kioskModeLockedNote = "سیلف آرڈرنگ کیوسک مہمانوں کے لیے لاک ہے۔ باہر نکلنے کے لیے عملے کا پاس ورڈ درکار ہے۔",
        kioskActivationTitle = "ریسٹورانٹ کیوسک موڈ فعال کریں",
        kioskActivationSubtitle = "اس ڈیوائس کو لاک شدہ سیلف آرڈرنگ کیوسک موڈ میں تبدیل کرنے کے لیے کیوسک آئی ڈی اور پاس ورڈ درج کریں۔",
        kioskIdLabel = "کیوسک آئی ڈی",
        kioskPasswordLabel = "کیوسک پاس ورڈ",
        unlockKioskBtn = "کیوسک موڈ کھولیں",
        kioskExitTitle = "کیوسک موڈ بند کریں",
        kioskExitSubtitle = "کیوسک موڈ سے باہر نکلنے اور ہوم ڈیلیوری پر واپس جانے کے لیے کیوسک یا ایڈمن پاس ورڈ درج کریں۔",
        exitKioskBtn = "کیوسک موڈ بند کریں",
        kioskBannerHeadline = "یہاں آرڈر کریں • تازہ اور تیز",
        kioskBannerSubtitle = "اپنی ڈشز منتخب کریں اور کچن ٹوکن نمبر حاصل کریں",
        kioskAllCategories = "مکمل مینو",
        kioskCheckoutTitle = "کیوسک آرڈر چیک آؤٹ",
        kioskCheckoutCta = "ادائیگی اور ٹوکن حاصل کریں",
        kioskPayAtCounter = "کاؤنٹر پر نقد ادائیگی",
        kioskPayAtCounterSub = "اپنے ٹوکن نمبر کے ساتھ کاؤنٹر پر نقد ادائیگی کریں",
        kioskPayOnline = "آن لائن ادائیگی",
        kioskPayOnlineSub = "کارڈ / ایزی پیسہ / جاز کیش",
        kioskPlaceOrderBtn = "آرڈر کنفرم اور ٹوکن پرنٹ کریں",
        kioskTokenLabel = "آرڈر ٹوکن",
        kioskOrderSentTitle = "آرڈر کچن میں بھیج دیا گیا!",
        kioskOrderSentSubtitle = "براہ کرم کاؤنٹر سے آرڈر لینے کے لیے اپنا ٹوکن تیار رکھیں۔",
        kioskAutoResetPrefix = "اگلے مہمان کے لیے اسکرین ری سیٹ ہو رہی ہے",
        kioskStartNewOrderBtn = "نیا آرڈر شروع کریں",
        kioskPrintTokenReceipt = "80mm تھرمل ٹوکن رسید پرنٹ / ڈاؤن لوڈ کریں",
        appName = "اسمارٹ ڈائن",
        adminAppName = "اسمارٹ ڈائن ایڈمن",
        kioskThankYouHeadline = "اسمارٹ ڈائن تشریف لانے کا بے حد شکریہ!",
        kioskThankYouNote = "اسمارٹ ڈائن تشریف لانے کا بے حد شکریہ! آپ کا آرڈر تازہ تیار کیا جا رہا ہے۔ براہِ کرم اپنا ٹوکن نمبر پکارے جانے پر کاؤنٹر سے آرڈر وصول کریں۔",
        receiptThankYouFooter = "اسمارٹ ڈائن کو منتخب کرنے کا شکریہ! دوبارہ ضرور تشریف لائیں۔",
        drawerTitle = "اسمارٹ ڈائن مینو",
        kioskSectionLabel = "نیویگیشن اور کیوسک",
        staffManagementSection = "عملہ اور انتظامیہ",
        employeeLogin = "ملازم لاگ ان",
        adminLogin = "ایڈمن لاگ ان",
        homeDeliveryLabel = "گھر پر منگوائیں",
        deliveryEta = "25–35 منٹ",
        myOrdersTitle = "میرے آرڈرز (لائیو ٹریکنگ اور ہسٹری)",
        myOrdersSubtitle = "جاری ڈیلیوری ٹریک کریں یا پرانے آرڈرز دیکھیں",
        logoutLabel = "لاگ آؤٹ",
        signInLabel = "سائن ان",
        searchMenuPlaceholder = "ڈشز، بریانی، پیزا یا مشروبات تلاش کریں...",
        resetFilters = "فلٹرز صاف کریں",
        noDishesMatch = "کوئی ڈش نہیں ملی",
        addButton = "شامل کریں",
        soldOut = "ختم",
        checkoutCta = "چیک آؤٹ",
        placeOrderCta = "آرڈر بھیجیں",
        deliveryCartSubtitle = "ڈیلیوری پتہ اور GPS پن درج کریں",
        kioskCartSubtitle = "ادائیگی کریں اور ٹوکن حاصل کریں",
        itemsLabel = "آئٹمز",
        itemLabel = "آئٹم",
        reviewsTitle = "ریویوز",
        rateVisit = "اپنی رائے دیں",
        cancelBtn = "منسوخ کریں",
        closeBtn = "بند کریں",
        authModalTitle = "ہوم ڈیلیوری سائن ان",
        authModalSubtitle = "اپنے آرڈرز کی ہسٹری محفوظ کریں اور لائیو ٹریک کریں",
        continueWithGoogle = "Google کے ساتھ جاری رکھیں",
        emailTab = "ای میل اور پاس ورڈ",
        phoneTab = "فون اور OTP",
        fullNameLabel = "پورا نام",
        emailLabel = "ای میل ایڈریس",
        passwordLabel = "پاس ورڈ",
        phoneLabel = "فون نمبر",
        otpLabel = "تصدیقی OTP (ڈیمو: 1234)",
        sendOtpBtn = "OTP بھیجیں",
        verifyAndContinueBtn = "تصدیق کریں اور آگے بڑھیں",
        loginWithEmailBtn = "سائن اپ / لاگ ان",
        checkoutTitle = "ہوم ڈیلیوری چیک آؤٹ",
        customerNameLabel = "کسٹمر کا نام",
        deliveryAddressLabel = "مکمل ڈیلیوری پتہ",
        shareGpsOptional = "لائیو GPS لوکیشن شیئر کریں (اختیاری)",
        shareGpsBtn = "میری موجودہ لوکیشن شیئر کریں (GPS)",
        gpsPreviewAttached = "GPS پری ویو پن منسلک ہو گیا",
        gpsLiveAttached = "لائیو GPS پن منسلک ہو گیا",
        paymentMethodLabel = "ادائیگی کا طریقہ",
        validationRequiredMsg = "براہ کرم نام، فون نمبر اور مکمل ڈیلیوری پتہ (*) درج کریں۔",
        noActiveDeliveryOrders = "ابھی تک کوئی ہوم ڈیلیوری آرڈر نہیں ہے",
        browseMenuTip = "تازہ کھانا آرڈر کرنے کے لیے ڈیجیٹل مینو دیکھیں۔",
        exploreMenuBtn = "مینو دیکھیں",
        onTheWayBanner = "آپ کا آرڈر راستے میں ہے اور جلد پہنچ جائے گا!",
        cancelOrderBtn = "آرڈر منسوخ کریں",
        leftLabel = "باقی",
        orderConfirmedLocked = "آرڈر کنفرم اور لاک ہو چکا ہے",
        orderCancelledBadge = "منسوخ شدہ",
        estimatedPrepTime = "متوقع تیاری کا وقت: 15-25 منٹ",
        enjoyYourMeal = "کھانے سے لطف اندوز ہوں!",
        paidVia = "ادائیگی بذریعہ",
        paymentLabel = "ادائیگی",
        subtotal = "کل رقم",
        taxGst = "ٹیکس (5% GST)",
        grandTotal = "مجموعی بل",
        feedbackTitle = "فوری رائے",
        feedbackPrompt = "اسمارٹ ڈائن کے ساتھ آپ کا تجربہ کیسا رہا؟",
        whatStoodOut = "آپ کو کیا پسند آیا؟",
        optionalComment = "اضافی تبصرہ (اختیاری)",
        submitFeedback = "رائے جمع کروائیں",
        websiteReviewsLive = "لائیو ویب سائٹ ریویوز",
        addToCartLabel = "شامل کریں",
        specialNoteLabel = "کچن کے لیے خاص ہدایت (اختیاری)",
        printDownloadReceipt = "رسید پرنٹ / ڈاؤن لوڈ کریں",
        printThermalReceipt = "تھرمل رسید پرنٹ کریں",
        downloadReceipt = "رسید ڈاؤن لوڈ کریں",
        thermalReceiptTitle = "80mm تھرمل رسید",
        thankYouDining = "ہمارے ساتھ کھانے کا شکریہ!",
        receiptLabel = "رسید",
        orderTypeLabel = "آرڈر کی قسم"
    )

    fun forLanguage(language: AppLanguage): LocalizedStrings = when (language) {
        AppLanguage.EN -> en
        AppLanguage.UR -> ur
    }
}
