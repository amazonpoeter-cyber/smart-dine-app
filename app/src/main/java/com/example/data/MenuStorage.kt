package com.example.data

import android.content.Context
import com.example.model.AppLanguage
import com.example.model.CustomerAuthProvider
import com.example.model.CustomerProfile
import com.example.model.KitchenOrder
import com.example.model.MenuArtwork
import com.example.model.MenuCategory
import com.example.model.MenuItem
import com.example.model.OrderLineItem
import com.example.model.OrderStatus
import com.example.model.OrderType
import com.example.model.PaymentMethod
import com.example.model.SpiceLevel
import com.example.model.StaffMember
import com.example.model.StaffPortalRole
import com.example.util.DateUtils
import com.example.util.WeekdaySlot
import org.json.JSONArray
import org.json.JSONObject

data class RegisteredCustomerAccount(
    val email: String,
    val name: String,
    val passwordHash: String
)

interface MenuStorage {
    fun loadMenuItems(): List<MenuItem>?
    fun saveMenuItems(items: List<MenuItem>)
}

interface CredentialAndRosterStorage {
    fun loadAdminPasswordHash(): String?
    fun saveAdminPasswordHash(sha256Hex: String)
    fun loadStaff(): List<StaffMember>?
    fun saveStaff(staff: List<StaffMember>)
    fun loadLoggedInCustomer(): CustomerProfile? = null
    fun saveLoggedInCustomer(profile: CustomerProfile?) {}
    fun loadCustomerEmailAccounts(): List<RegisteredCustomerAccount> = emptyList()
    fun saveCustomerEmailAccounts(accounts: List<RegisteredCustomerAccount>) {}
    fun loadDeliveryOrders(): List<KitchenOrder>? = null
    fun saveDeliveryOrders(orders: List<KitchenOrder>) {}
    fun loadAppLanguage(): AppLanguage? = null
    fun saveAppLanguage(language: AppLanguage) {}
}

class InMemoryMenuStorage(
    private var cached: List<MenuItem>? = null
) : MenuStorage {
    override fun loadMenuItems(): List<MenuItem>? = cached
    override fun saveMenuItems(items: List<MenuItem>) {
        cached = items
    }
}

class InMemoryCredentialStorage(
    private var adminHash: String? = null,
    private var staffList: List<StaffMember>? = null,
    private var loggedInCustomer: CustomerProfile? = null,
    private var customerAccounts: List<RegisteredCustomerAccount> = emptyList(),
    private var deliveryOrders: List<KitchenOrder>? = null,
    private var appLanguage: AppLanguage? = null
) : CredentialAndRosterStorage {
    override fun loadAdminPasswordHash(): String? = adminHash
    override fun saveAdminPasswordHash(sha256Hex: String) {
        adminHash = sha256Hex
    }
    override fun loadStaff(): List<StaffMember>? = staffList
    override fun saveStaff(staff: List<StaffMember>) {
        staffList = staff
    }
    override fun loadLoggedInCustomer(): CustomerProfile? = loggedInCustomer
    override fun saveLoggedInCustomer(profile: CustomerProfile?) {
        loggedInCustomer = profile
    }
    override fun loadCustomerEmailAccounts(): List<RegisteredCustomerAccount> = customerAccounts
    override fun saveCustomerEmailAccounts(accounts: List<RegisteredCustomerAccount>) {
        customerAccounts = accounts
    }
    override fun loadDeliveryOrders(): List<KitchenOrder>? = deliveryOrders
    override fun saveDeliveryOrders(orders: List<KitchenOrder>) {
        deliveryOrders = orders
    }
    override fun loadAppLanguage(): AppLanguage? = appLanguage
    override fun saveAppLanguage(language: AppLanguage) {
        appLanguage = language
    }
}

class SharedPreferencesMenuStorage(context: Context) : MenuStorage, CredentialAndRosterStorage {
    private val prefs = run {
        val current = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (current.all.isEmpty() && legacy.all.isNotEmpty()) {
            val editor = current.edit()
            for ((k, v) in legacy.all) {
                when (v) {
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(k, v as Set<String>)
                }
            }
            editor.apply()
        }
        current
    }

    override fun loadMenuItems(): List<MenuItem>? {
        val raw = prefs.getString(KEY_MENU_ITEMS, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val category = runCatching {
                        MenuCategory.valueOf(obj.getString("category"))
                    }.getOrDefault(MenuCategory.MAINS)
                    val spice = runCatching {
                        SpiceLevel.valueOf(obj.getString("spiceLevel"))
                    }.getOrDefault(SpiceLevel.MILD)
                    val artwork = runCatching {
                        MenuArtwork.valueOf(obj.getString("artwork"))
                    }.getOrDefault(MenuArtwork.CUSTOM_DISH)

                    add(
                        MenuItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            category = category,
                            priceRs = obj.getInt("priceRs"),
                            prepTimeRange = obj.optString("prepTimeRange", "15 min"),
                            spiceLevel = spice,
                            artwork = artwork,
                            imageUrl = sanitizeImageUrl(obj.optString("imageUrl", "")),
                            isAvailable = obj.optBoolean("isAvailable", true),
                            isLowStock = obj.optBoolean("isLowStock", false)
                        )
                    )
                }
            }
        }.getOrNull()
    }

    override fun saveMenuItems(items: List<MenuItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("description", item.description)
                put("category", item.category.name)
                put("priceRs", item.priceRs)
                put("prepTimeRange", item.prepTimeRange)
                put("spiceLevel", item.spiceLevel.name)
                put("artwork", item.artwork.name)
                put("imageUrl", sanitizeImageUrl(item.imageUrl))
                put("isAvailable", item.isAvailable)
                put("isLowStock", item.isLowStock)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_MENU_ITEMS, array.toString()).apply()
    }

    override fun loadAdminPasswordHash(): String? = prefs.getString(KEY_ADMIN_PASS_HASH, null)

    override fun saveAdminPasswordHash(sha256Hex: String) {
        prefs.edit().putString(KEY_ADMIN_PASS_HASH, sha256Hex).apply()
    }

    override fun loadStaff(): List<StaffMember>? {
        val raw = prefs.getString(KEY_STAFF_LIST, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (!obj.has("cnic") || !obj.has("passwordHash") || !obj.has("portalRole")) {
                        return null
                    }
                    val dayOff = runCatching {
                        WeekdaySlot.valueOf(obj.optString("scheduledDayOff", WeekdaySlot.NONE.name))
                    }.getOrDefault(WeekdaySlot.NONE)
                    val portalRole = runCatching {
                        StaffPortalRole.valueOf(obj.optString("portalRole", StaffPortalRole.GENERAL_STAFF.name))
                    }.getOrDefault(StaffPortalRole.GENERAL_STAFF)
                    add(
                        StaffMember(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            cnic = obj.getString("cnic"),
                            passwordHash = obj.getString("passwordHash"),
                            designation = obj.optString("designation", "Staff"),
                            portalRole = portalRole,
                            phone = obj.optString("phone", ""),
                            address = obj.optString("address", ""),
                            email = obj.optString("email", ""),
                            shiftLabel = obj.optString("shiftLabel", "10:00 - 19:00"),
                            isOnDuty = obj.optBoolean("isOnDuty", false),
                            isRelieved = obj.optBoolean("isRelieved", false),
                            scheduledDayOff = dayOff
                        )
                    )
                }
            }
        }.getOrNull()
    }

    override fun saveStaff(staff: List<StaffMember>) {
        val array = JSONArray()
        staff.forEach { member ->
            val obj = JSONObject().apply {
                put("id", member.id)
                put("name", member.name)
                put("cnic", member.cnic)
                put("passwordHash", member.passwordHash)
                put("designation", member.designation)
                put("portalRole", member.portalRole.name)
                put("phone", member.phone)
                put("address", member.address)
                put("email", member.email)
                put("shiftLabel", member.shiftLabel)
                put("isOnDuty", member.isOnDuty)
                put("isRelieved", member.isRelieved)
                put("scheduledDayOff", member.scheduledDayOff.name)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_STAFF_LIST, array.toString()).apply()
    }

    override fun loadLoggedInCustomer(): CustomerProfile? {
        val raw = prefs.getString(KEY_LOGGED_IN_CUSTOMER, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val provider = runCatching {
                CustomerAuthProvider.valueOf(obj.optString("provider", CustomerAuthProvider.GOOGLE.name))
            }.getOrDefault(CustomerAuthProvider.GOOGLE)
            CustomerProfile(
                id = obj.getString("id"),
                name = obj.getString("name"),
                email = obj.optString("email", ""),
                phone = obj.optString("phone", ""),
                provider = provider
            )
        }.getOrNull()
    }

    override fun saveLoggedInCustomer(profile: CustomerProfile?) {
        if (profile == null) {
            prefs.edit().remove(KEY_LOGGED_IN_CUSTOMER).apply()
            return
        }
        val obj = JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("email", profile.email)
            put("phone", profile.phone)
            put("provider", profile.provider.name)
        }
        prefs.edit().putString(KEY_LOGGED_IN_CUSTOMER, obj.toString()).apply()
    }

    override fun loadCustomerEmailAccounts(): List<RegisteredCustomerAccount> {
        val raw = prefs.getString(KEY_CUSTOMER_EMAIL_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        RegisteredCustomerAccount(
                            email = obj.getString("email"),
                            name = obj.optString("name", "Smart Dine Guest"),
                            passwordHash = obj.getString("passwordHash")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun saveCustomerEmailAccounts(accounts: List<RegisteredCustomerAccount>) {
        val array = JSONArray()
        accounts.forEach { acct ->
            val obj = JSONObject().apply {
                put("email", acct.email)
                put("name", acct.name)
                put("passwordHash", acct.passwordHash)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOMER_EMAIL_ACCOUNTS, array.toString()).apply()
    }

    override fun loadDeliveryOrders(): List<KitchenOrder>? {
        val raw = prefs.getString(KEY_DELIVERY_ORDERS, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val status = runCatching {
                        OrderStatus.valueOf(obj.optString("status", OrderStatus.NEW.name))
                    }.getOrDefault(OrderStatus.NEW)
                    val paymentMethod = obj.optString("paymentMethod", "").takeIf { it.isNotBlank() }?.let {
                        runCatching { PaymentMethod.valueOf(it) }.getOrNull()
                    }
                    val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                    val lineItems = buildList {
                        for (j in 0 until itemsArr.length()) {
                            val itemObj = itemsArr.getJSONObject(j)
                            add(
                                OrderLineItem(
                                    menuItemId = itemObj.optString("menuItemId", "item"),
                                    title = itemObj.getString("title"),
                                    priceRs = itemObj.getInt("priceRs"),
                                    quantity = itemObj.getInt("quantity"),
                                    note = itemObj.optString("note", "")
                                )
                            )
                        }
                    }
                    val orderType = runCatching {
                        OrderType.valueOf(obj.optString("orderType", OrderType.DELIVERY.name))
                    }.getOrDefault(OrderType.DELIVERY)
                    val tokenNumber = if (obj.has("tokenNumber") && !obj.isNull("tokenNumber")) {
                        obj.optInt("tokenNumber", -1).takeIf { it > 0 }
                    } else null
                    add(
                        KitchenOrder(
                            orderNumber = obj.getInt("orderNumber"),
                            tableCode = obj.optString("tableCode", if (orderType == OrderType.DELIVERY) "DELIVERY" else "KIOSK"),
                            items = lineItems,
                            status = status,
                            elapsedSeconds = obj.optInt("elapsedSeconds", 0),
                            orderType = orderType,
                            tokenNumber = tokenNumber,
                            customerId = obj.optString("customerId", ""),
                            customerName = obj.optString("customerName", ""),
                            customerPhone = obj.optString("customerPhone", ""),
                            deliveryAddress = obj.optString("deliveryAddress", ""),
                            gpsCoordinatesLabel = obj.optString("gpsCoordinatesLabel", "").takeIf { it.isNotBlank() },
                            gpsMapUrl = obj.optString("gpsMapUrl", "").takeIf { it.isNotBlank() },
                            isGpsFallback = obj.optBoolean("isGpsFallback", false),
                            cancelWindowRemainingSeconds = obj.optInt("cancelWindowRemainingSeconds", 0),
                            isPaid = obj.optBoolean("isPaid", false),
                            paymentMethod = paymentMethod,
                            placedAtLabel = obj.optString("placedAtLabel", "Saved"),
                            orderDateIso = obj.optString("orderDateIso", DateUtils.todayIso()),
                            completedDateIso = obj.optString("completedDateIso", "").takeIf { it.isNotBlank() },
                            completedTimeLabel = obj.optString("completedTimeLabel", "").takeIf { it.isNotBlank() },
                            assignedRiderId = obj.optString("assignedRiderId", "").takeIf { it.isNotBlank() },
                            assignedRiderName = obj.optString("assignedRiderName", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrNull()
    }

    override fun saveDeliveryOrders(orders: List<KitchenOrder>) {
        val array = JSONArray()
        orders.filter { it.isDelivery || it.isKiosk }.forEach { order ->
            val itemsArr = JSONArray()
            order.items.forEach { line ->
                itemsArr.put(
                    JSONObject().apply {
                        put("menuItemId", line.menuItemId)
                        put("title", line.title)
                        put("priceRs", line.priceRs)
                        put("quantity", line.quantity)
                        put("note", line.note)
                    }
                )
            }
            val obj = JSONObject().apply {
                put("orderNumber", order.orderNumber)
                put("tableCode", order.tableCode)
                put("items", itemsArr)
                put("status", order.status.name)
                put("elapsedSeconds", order.elapsedSeconds)
                put("orderType", order.orderType.name)
                if (order.tokenNumber != null) put("tokenNumber", order.tokenNumber)
                put("customerId", order.customerId)
                put("customerName", order.customerName)
                put("customerPhone", order.customerPhone)
                put("deliveryAddress", order.deliveryAddress)
                put("gpsCoordinatesLabel", order.gpsCoordinatesLabel ?: "")
                put("gpsMapUrl", order.gpsMapUrl ?: "")
                put("isGpsFallback", order.isGpsFallback)
                put("cancelWindowRemainingSeconds", order.cancelWindowRemainingSeconds)
                put("isPaid", order.isPaid)
                put("paymentMethod", order.paymentMethod?.name ?: "")
                put("placedAtLabel", order.placedAtLabel)
                put("orderDateIso", order.orderDateIso)
                put("completedDateIso", order.completedDateIso ?: "")
                put("completedTimeLabel", order.completedTimeLabel ?: "")
                put("assignedRiderId", order.assignedRiderId ?: "")
                put("assignedRiderName", order.assignedRiderName ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_DELIVERY_ORDERS, array.toString()).apply()
    }

    override fun loadAppLanguage(): AppLanguage? {
        val raw = prefs.getString(KEY_APP_LANGUAGE, null) ?: return null
        return runCatching { AppLanguage.valueOf(raw) }.getOrNull()
    }

    override fun saveAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "smartdine_local_storage"
        private const val LEGACY_PREFS_NAME = "tasteqr_local_storage"
        private const val KEY_MENU_ITEMS = "menu_items_json_v1"
        private const val KEY_ADMIN_PASS_HASH = "admin_password_sha256_v1"
        private const val KEY_STAFF_LIST = "staff_roster_json_v1"
        private const val KEY_LOGGED_IN_CUSTOMER = "logged_in_customer_v1"
        private const val KEY_CUSTOMER_EMAIL_ACCOUNTS = "customer_email_accounts_v1"
        private const val KEY_DELIVERY_ORDERS = "delivery_orders_history_v1"
        private const val KEY_APP_LANGUAGE = "app_language_v1"

        fun sanitizeImageUrl(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return ""
            val lower = trimmed.lowercase()
            return if (lower.startsWith("https://") || lower.startsWith("http://")) {
                trimmed
            } else {
                ""
            }
        }
    }
}

