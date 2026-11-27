package com.example.data.supabase

import android.util.Log
import com.example.BuildConfig
import com.example.data.SharedPreferencesMenuStorage
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
import com.example.util.DateUtils
import com.example.util.WeekdaySlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class SupabaseConfig(
    val projectUrl: String,
    val publishableKey: String
) {
    val cleanUrl: String = projectUrl.trim().trimEnd('/')
    val cleanKey: String = publishableKey.trim()

    val isConfigured: Boolean
        get() = cleanUrl.startsWith("https://") &&
            cleanKey.isNotBlank() &&
            cleanKey != PLACEHOLDER_KEY &&
            !cleanKey.startsWith("sb_secret_")

    companion object {
        private const val PLACEHOLDER_KEY = "YOUR_SUPABASE_PUBLISHABLE_KEY"

        fun fromBuildConfig(): SupabaseConfig {
            return SupabaseConfig(
                projectUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
            )
        }
    }
}

enum class SupabaseSyncStatus {
    LOCAL_ONLY,
    CONNECTING,
    LIVE_SYNCED,
    OFFLINE_FALLBACK
}

sealed interface SupabaseRealtimeEvent {
    data class ConnectionChanged(val isConnected: Boolean) : SupabaseRealtimeEvent
    data class MenuUpserted(val item: MenuItem) : SupabaseRealtimeEvent
    data class MenuDeleted(val itemId: String) : SupabaseRealtimeEvent
    data object MenuRefreshRequested : SupabaseRealtimeEvent
    data class OrderUpserted(val order: KitchenOrder) : SupabaseRealtimeEvent
    data class OrderDeleted(val orderNumber: Int) : SupabaseRealtimeEvent
    data object OrdersRefreshRequested : SupabaseRealtimeEvent
    data class ReviewUpserted(val review: ReviewEntry) : SupabaseRealtimeEvent
    data object ReviewsRefreshRequested : SupabaseRealtimeEvent
    data class StaffUpserted(val member: StaffMember) : SupabaseRealtimeEvent
    data object StaffRefreshRequested : SupabaseRealtimeEvent
    data class AttendanceUpserted(val record: AttendanceRecord) : SupabaseRealtimeEvent
    data object AttendanceRefreshRequested : SupabaseRealtimeEvent
    data class AlertUpserted(val alert: WhatsAppAlert) : SupabaseRealtimeEvent
    data object AlertsRefreshRequested : SupabaseRealtimeEvent
    data class SalesRecordUpserted(val record: DailySalesRecord) : SupabaseRealtimeEvent
    data object SalesRefreshRequested : SupabaseRealtimeEvent
}

interface SupabaseService {
    val isRemoteEnabled: Boolean
    val realtimeEvents: SharedFlow<SupabaseRealtimeEvent>

    fun startRealtimeSync()
    fun stopRealtimeSync()

    suspend fun fetchMenuItems(): Result<List<MenuItem>>
    suspend fun upsertMenuItem(item: MenuItem): Result<Unit>
    suspend fun upsertMenuItems(items: List<MenuItem>): Result<Unit>
    suspend fun deleteMenuItem(itemId: String): Result<Unit>

    suspend fun fetchOrders(): Result<List<KitchenOrder>>
    suspend fun upsertOrder(order: KitchenOrder): Result<Unit>
    suspend fun upsertOrders(orders: List<KitchenOrder>): Result<Unit>

    suspend fun fetchReviews(): Result<List<ReviewEntry>>
    suspend fun upsertReview(review: ReviewEntry): Result<Unit>
    suspend fun upsertReviews(reviews: List<ReviewEntry>): Result<Unit>

    suspend fun fetchStaff(): Result<List<StaffMember>>
    suspend fun upsertStaffMember(member: StaffMember): Result<Unit>
    suspend fun upsertStaffList(staff: List<StaffMember>): Result<Unit>

    suspend fun fetchAttendanceRecords(): Result<List<AttendanceRecord>>
    suspend fun upsertAttendanceRecord(record: AttendanceRecord): Result<Unit>
    suspend fun upsertAttendanceRecords(records: List<AttendanceRecord>): Result<Unit>

    suspend fun fetchAlerts(): Result<List<WhatsAppAlert>>
    suspend fun upsertAlert(alert: WhatsAppAlert): Result<Unit>
    suspend fun upsertAlerts(alerts: List<WhatsAppAlert>): Result<Unit>

    suspend fun fetchSalesRecords(): Result<List<DailySalesRecord>>
    suspend fun upsertSalesRecord(record: DailySalesRecord): Result<Unit>
    suspend fun upsertSalesRecords(records: List<DailySalesRecord>): Result<Unit>
}

class NoOpSupabaseService : SupabaseService {
    override val isRemoteEnabled: Boolean = false
    private val _events = MutableSharedFlow<SupabaseRealtimeEvent>(extraBufferCapacity = 16)
    override val realtimeEvents: SharedFlow<SupabaseRealtimeEvent> = _events.asSharedFlow()

    override fun startRealtimeSync() {}
    override fun stopRealtimeSync() {}

    override suspend fun fetchMenuItems(): Result<List<MenuItem>> = Result.success(emptyList())
    override suspend fun upsertMenuItem(item: MenuItem): Result<Unit> = Result.success(Unit)
    override suspend fun upsertMenuItems(items: List<MenuItem>): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMenuItem(itemId: String): Result<Unit> = Result.success(Unit)

    override suspend fun fetchOrders(): Result<List<KitchenOrder>> = Result.success(emptyList())
    override suspend fun upsertOrder(order: KitchenOrder): Result<Unit> = Result.success(Unit)
    override suspend fun upsertOrders(orders: List<KitchenOrder>): Result<Unit> = Result.success(Unit)

    override suspend fun fetchReviews(): Result<List<ReviewEntry>> = Result.success(emptyList())
    override suspend fun upsertReview(review: ReviewEntry): Result<Unit> = Result.success(Unit)
    override suspend fun upsertReviews(reviews: List<ReviewEntry>): Result<Unit> = Result.success(Unit)

    override suspend fun fetchStaff(): Result<List<StaffMember>> = Result.success(emptyList())
    override suspend fun upsertStaffMember(member: StaffMember): Result<Unit> = Result.success(Unit)
    override suspend fun upsertStaffList(staff: List<StaffMember>): Result<Unit> = Result.success(Unit)

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

class SupabaseClient(
    private val config: SupabaseConfig = SupabaseConfig.fromBuildConfig(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
) : SupabaseService {

    override val isRemoteEnabled: Boolean = config.isConfigured

    private val _realtimeEvents = MutableSharedFlow<SupabaseRealtimeEvent>(extraBufferCapacity = 64)
    override val realtimeEvents: SharedFlow<SupabaseRealtimeEvent> = _realtimeEvents.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val baseHttpUrl: HttpUrl? = config.cleanUrl.toHttpUrlOrNull()
    private val refCounter = AtomicInteger(1)

    @Volatile
    private var realtimeWanted = false

    @Volatile
    private var webSocket: WebSocket? = null

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    init {
        if (config.cleanKey.startsWith("sb_secret_")) {
            Log.e(TAG, "Refusing to initialize SupabaseClient with a secret key. Use the Publishable Key only.")
        } else if (!config.isConfigured) {
            Log.w(TAG, "Supabase credentials missing or placeholder; running in local-first mode.")
        }
    }

    override fun startRealtimeSync() {
        if (!isRemoteEnabled) return
        if (realtimeWanted && webSocket != null) return
        realtimeWanted = true
        connectWebSocket()
    }

    override fun stopRealtimeSync() {
        realtimeWanted = false
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "Client stopped")
        webSocket = null
    }

    private fun connectWebSocket() {
        val base = baseHttpUrl ?: return
        val wsUrl = HttpUrl.Builder()
            .scheme("https")
            .host(base.host)
            .apply { if (base.port != 443) port(base.port) }
            .addPathSegments("realtime/v1/websocket")
            .addQueryParameter("apikey", config.cleanKey)
            .addQueryParameter("vsn", "1.0.0")
            .build()
            .toString()
            .replaceFirst("https://", "wss://")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket?.cancel()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.ConnectionChanged(true))
                joinPostgresChangesChannel(webSocket)
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleRealtimeMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.ConnectionChanged(false))
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.ConnectionChanged(false))
                Log.w(TAG, "Supabase Realtime disconnected: ${t.javaClass.simpleName}")
                scheduleReconnect()
            }
        })
    }

    private fun joinPostgresChangesChannel(ws: WebSocket) {
        val tables = listOf(
            TABLE_MENU_ITEMS,
            TABLE_ORDERS,
            TABLE_REVIEWS,
            TABLE_STAFF,
            TABLE_ATTENDANCE,
            TABLE_ALERTS,
            TABLE_SALES
        )
        val postgresChanges = JSONArray()
        tables.forEach { table ->
            postgresChanges.put(
                JSONObject().apply {
                    put("event", "*")
                    put("schema", "public")
                    put("table", table)
                }
            )
        }
        val joinMessage = JSONObject().apply {
            put("topic", REALTIME_TOPIC)
            put("event", "phx_join")
            put(
                "payload",
                JSONObject().apply {
                    put(
                        "config",
                        JSONObject().apply {
                            put("broadcast", JSONObject().put("self", false))
                            put("presence", JSONObject().put("key", ""))
                            put("postgres_changes", postgresChanges)
                        }
                    )
                }
            )
            put("ref", refCounter.getAndIncrement().toString())
        }
        ws.send(joinMessage.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && realtimeWanted) {
                delay(25_000L)
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                ws.send(hb.toString())
            }
        }
    }

    private fun scheduleReconnect() {
        if (!realtimeWanted) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val backoffMs = (3_000L * (1 shl reconnectAttempt.coerceAtMost(3))).coerceAtMost(30_000L)
            reconnectAttempt++
            delay(backoffMs)
            if (realtimeWanted) {
                connectWebSocket()
            }
        }
    }

    private fun handleRealtimeMessage(raw: String) {
        runCatching {
            val root = JSONObject(raw)
            val event = root.optString("event", "")
            if (event != "postgres_changes") return

            val payload = root.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return
            val table = data.optString("table", "")
            val changeType = data.optString("type", "")
            val record = data.optJSONObject("record")
            val oldRecord = data.optJSONObject("old_record")

            when (table) {
                TABLE_MENU_ITEMS -> {
                    when (changeType) {
                        "DELETE" -> {
                            val id = oldRecord?.optString("id", "").orEmpty()
                            if (id.isNotBlank()) {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.MenuDeleted(id))
                            } else {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.MenuRefreshRequested)
                            }
                        }
                        else -> {
                            val parsed = record?.let { jsonToMenuItem(it) }
                            if (parsed != null) {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.MenuUpserted(parsed))
                            } else {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.MenuRefreshRequested)
                            }
                        }
                    }
                }
                TABLE_ORDERS -> {
                    when (changeType) {
                        "DELETE" -> {
                            val orderNum = oldRecord?.optInt("order_number", -1) ?: -1
                            if (orderNum > 0) {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.OrderDeleted(orderNum))
                            } else {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.OrdersRefreshRequested)
                            }
                        }
                        else -> {
                            val parsed = record?.let { jsonToOrder(it) }
                            if (parsed != null) {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.OrderUpserted(parsed))
                            } else {
                                _realtimeEvents.tryEmit(SupabaseRealtimeEvent.OrdersRefreshRequested)
                            }
                        }
                    }
                }
                TABLE_REVIEWS -> {
                    val parsed = record?.let { jsonToReview(it) }
                    if (parsed != null && changeType != "DELETE") {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.ReviewUpserted(parsed))
                    } else {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.ReviewsRefreshRequested)
                    }
                }
                TABLE_STAFF -> {
                    val parsed = record?.let { jsonToStaff(it) }
                    if (parsed != null && changeType != "DELETE") {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.StaffUpserted(parsed))
                    } else {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.StaffRefreshRequested)
                    }
                }
                TABLE_ATTENDANCE -> {
                    val parsed = record?.let { jsonToAttendance(it) }
                    if (parsed != null && changeType != "DELETE") {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.AttendanceUpserted(parsed))
                    } else {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.AttendanceRefreshRequested)
                    }
                }
                TABLE_ALERTS -> {
                    val parsed = record?.let { jsonToAlert(it) }
                    if (parsed != null && changeType != "DELETE") {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.AlertUpserted(parsed))
                    } else {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.AlertsRefreshRequested)
                    }
                }
                TABLE_SALES -> {
                    val parsed = record?.let { jsonToSalesRecord(it) }
                    if (parsed != null && changeType != "DELETE") {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.SalesRecordUpserted(parsed))
                    } else {
                        _realtimeEvents.tryEmit(SupabaseRealtimeEvent.SalesRefreshRequested)
                    }
                }
            }
        }
    }

    // ---- Menu Items ----

    override suspend fun fetchMenuItems(): Result<List<MenuItem>> {
        return getTableRows(
            table = TABLE_MENU_ITEMS,
            orderQuery = "id.asc"
        ) { array ->
            buildList {
                for (i in 0 until array.length()) {
                    jsonToMenuItem(array.getJSONObject(i))?.let(::add)
                }
            }
        }
    }

    override suspend fun upsertMenuItem(item: MenuItem): Result<Unit> {
        return upsertRows(
            table = TABLE_MENU_ITEMS,
            onConflict = "id",
            payload = JSONArray().put(menuItemToJson(item))
        )
    }

    override suspend fun upsertMenuItems(items: List<MenuItem>): Result<Unit> {
        if (items.isEmpty()) return Result.success(Unit)
        val array = JSONArray()
        items.forEach { array.put(menuItemToJson(it)) }
        return upsertRows(table = TABLE_MENU_ITEMS, onConflict = "id", payload = array)
    }

    override suspend fun deleteMenuItem(itemId: String): Result<Unit> {
        return deleteWhere(table = TABLE_MENU_ITEMS, column = "id", eqValue = itemId)
    }

    // ---- Orders ----

    override suspend fun fetchOrders(): Result<List<KitchenOrder>> {
        return getTableRows(
            table = TABLE_ORDERS,
            orderQuery = "order_number.desc"
        ) { array ->
            buildList {
                for (i in 0 until array.length()) {
                    jsonToOrder(array.getJSONObject(i))?.let(::add)
                }
            }
        }
    }

    override suspend fun upsertOrder(order: KitchenOrder): Result<Unit> {
        return upsertRows(
            table = TABLE_ORDERS,
            onConflict = "order_number",
            payload = JSONArray().put(orderToJson(order))
        )
    }

    override suspend fun upsertOrders(orders: List<KitchenOrder>): Result<Unit> {
        if (orders.isEmpty()) return Result.success(Unit)
        val array = JSONArray()
        orders.forEach { array.put(orderToJson(it)) }
        return upsertRows(table = TABLE_ORDERS, onConflict = "order_number", payload = array)
    }

    // ---- Reviews ----

    override suspend fun fetchReviews(): Result<List<ReviewEntry>> {
        return getTableRows(
            table = TABLE_REVIEWS,
            orderQuery = "updated_at.desc"
        ) { array ->
            buildList {
                for (i in 0 until array.length()) {
                    jsonToReview(array.getJSONObject(i))?.let(::add)
                }
            }
        }
    }

    override suspend fun upsertReview(review: ReviewEntry): Result<Unit> {
        return upsertRows(
            table = TABLE_REVIEWS,
            onConflict = "id",
            payload = JSONArray().put(reviewToJson(review))
        )
    }

    override suspend fun upsertReviews(reviews: List<ReviewEntry>): Result<Unit> {
        if (reviews.isEmpty()) return Result.success(Unit)
        val array = JSONArray()
        reviews.forEach { array.put(reviewToJson(it)) }
        return upsertRows(table = TABLE_REVIEWS, onConflict = "id", payload = array)
    }

    // ---- Staff ----

    override suspend fun fetchStaff(): Result<List<StaffMember>> {
        return getTableRows(
            table = TABLE_STAFF,
            orderQuery = "id.asc"
        ) { array ->
            buildList {
                for (i in 0 until array.length()) {
                    jsonToStaff(array.getJSONObject(i))?.let(::add)
                }
            }
        }
    }

    override suspend fun upsertStaffMember(member: StaffMember): Result<Unit> {
        return upsertRows(
            table = TABLE_STAFF,
            onConflict = "id",
            payload = JSONArray().put(staffToJson(member))
        )
    }

    override suspend fun upsertStaffList(staff: List<StaffMember>): Result<Unit> {
        if (staff.isEmpty()) return Result.success(Unit)
        val array = JSONArray()
        staff.forEach { array.put(staffToJson(it)) }
        return upsertRows(table = TABLE_STAFF, onConflict = "id", payload = array)
    }

    // ---- Attendance ----

    override suspend fun fetchAttendanceRecords(): Result<List<AttendanceRecord>> {
        return getTableRows(
            table = TABLE_ATTENDANCE,
            orderQuery = "date_iso.desc"
        ) { array ->
            buildList {
                for (i in 0 until array.length()) {
                    jsonToAttendance(array.getJSONObject(i))?.let(::add)
                }
            }
        }
    }

    override suspend fun upsertAttendanceRecord(record: AttendanceRecord): Result<Unit> {
        return upsertRows(
            table = TABLE_ATTENDANCE,
            onConflict = "staff_id,date_iso",
            payload = JSONArray().put(attendanceToJson(record))
        )
    }

    override suspend fun upsertAttendanceRecords(records: List<AttendanceRecord>): Result<Unit> {
        if (records.isEmpty()) return Result.success(Unit)
        val array = JSONArray()
        records.forEach { array.put(attendanceToJson(it)) }
        return upsertRows(table = TABLE_ATTENDANCE, onConflict = "staff_id,date_iso", payload = array)
    }

    // ---- Alerts ----

    override suspend fun fetchAlerts(): Result<List<WhatsAppAlert>> {
        return getTableRows(
            table = TABLE_ALERTS,
            orderQuery = "updated_at.desc"
        ) { array ->
            buildList {
                for (i in 0 until array.length()) {
                    jsonToAlert(array.getJSONObject(i))?.let(::add)
                }
            }
        }
    }

    override suspend fun upsertAlert(alert: WhatsAppAlert): Result<Unit> {
        return upsertRows(
            table = TABLE_ALERTS,
            onConflict = "id",
            payload = JSONArray().put(alertToJson(alert))
        )
    }

    override suspend fun upsertAlerts(alerts: List<WhatsAppAlert>): Result<Unit> {
        if (alerts.isEmpty()) return Result.success(Unit)
        val array = JSONArray()
        alerts.forEach { array.put(alertToJson(it)) }
        return upsertRows(table = TABLE_ALERTS, onConflict = "id", payload = array)
    }

    // ---- Sales Records ----

    override suspend fun fetchSalesRecords(): Result<List<DailySalesRecord>> {
        return getTableRows(
            table = TABLE_SALES,
            orderQuery = "date_iso.desc"
        ) { array ->
            buildList {
                for (i in 0 until array.length()) {
                    jsonToSalesRecord(array.getJSONObject(i))?.let(::add)
                }
            }
        }
    }

    override suspend fun upsertSalesRecord(record: DailySalesRecord): Result<Unit> {
        return upsertRows(
            table = TABLE_SALES,
            onConflict = "date_iso",
            payload = JSONArray().put(salesRecordToJson(record))
        )
    }

    override suspend fun upsertSalesRecords(records: List<DailySalesRecord>): Result<Unit> {
        if (records.isEmpty()) return Result.success(Unit)
        val array = JSONArray()
        records.forEach { array.put(salesRecordToJson(it)) }
        return upsertRows(table = TABLE_SALES, onConflict = "date_iso", payload = array)
    }

    // ---- Generic PostgREST HTTP Helpers ----

    private fun restUrlBuilder(table: String): HttpUrl.Builder? {
        val base = baseHttpUrl ?: return null
        return base.newBuilder()
            .addPathSegment("rest")
            .addPathSegment("v1")
            .addPathSegment(table)
    }

    private suspend fun <T> getTableRows(
        table: String,
        orderQuery: String,
        parser: (JSONArray) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val url = restUrlBuilder(table)
            ?.addQueryParameter("select", "*")
            ?.addQueryParameter("order", orderQuery)
            ?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        val request = Request.Builder()
            .url(url)
            .get()
            .header("apikey", config.cleanKey)
            .header("Accept", "application/json")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Supabase GET $table failed (${resp.code}): $bodyStr")
                }
                val jsonArray = if (bodyStr.isBlank()) JSONArray() else JSONArray(bodyStr)
                parser(jsonArray)
            }
        }
    }

    private suspend fun upsertRows(
        table: String,
        onConflict: String,
        payload: JSONArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val url = restUrlBuilder(table)
            ?.addQueryParameter("on_conflict", onConflict)
            ?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("apikey", config.cleanKey)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    error("Supabase UPSERT $table failed (${resp.code}): $err")
                }
            }
        }
    }

    private suspend fun deleteWhere(
        table: String,
        column: String,
        eqValue: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val url = restUrlBuilder(table)
            ?.addQueryParameter(column, "eq.$eqValue")
            ?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        val request = Request.Builder()
            .url(url)
            .delete()
            .header("apikey", config.cleanKey)
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    error("Supabase DELETE $table failed (${resp.code}): $err")
                }
            }
        }
    }

    companion object {
        private const val TAG = "SupabaseClient"
        private const val REALTIME_TOPIC = "realtime:smartdine-sync"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val TABLE_MENU_ITEMS = "menu_items"
        const val TABLE_ORDERS = "orders"
        const val TABLE_REVIEWS = "reviews"
        const val TABLE_STAFF = "staff_members"
        const val TABLE_ATTENDANCE = "attendance_records"
        const val TABLE_ALERTS = "app_alerts"
        const val TABLE_SALES = "daily_sales_records"

        internal fun menuItemToJson(item: MenuItem): JSONObject = JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("description", item.description)
            put("category", item.category.name)
            put("price_rs", item.priceRs)
            put("prep_time_range", item.prepTimeRange)
            put("spice_level", item.spiceLevel.name)
            put("artwork", item.artwork.name)
            put("image_url", SharedPreferencesMenuStorage.sanitizeImageUrl(item.imageUrl))
            put("is_available", item.isAvailable)
            put("is_low_stock", item.isLowStock)
        }

        internal fun jsonToMenuItem(obj: JSONObject): MenuItem? = runCatching {
            MenuItem(
                id = obj.getString("id"),
                title = obj.getString("title"),
                description = obj.optString("description", ""),
                category = runCatching { MenuCategory.valueOf(obj.optString("category", MenuCategory.MAINS.name)) }
                    .getOrDefault(MenuCategory.MAINS),
                priceRs = obj.getInt("price_rs"),
                prepTimeRange = obj.optString("prep_time_range", "15 min"),
                spiceLevel = runCatching { SpiceLevel.valueOf(obj.optString("spice_level", SpiceLevel.MILD.name)) }
                    .getOrDefault(SpiceLevel.MILD),
                artwork = runCatching { MenuArtwork.valueOf(obj.optString("artwork", MenuArtwork.CUSTOM_DISH.name)) }
                    .getOrDefault(MenuArtwork.CUSTOM_DISH),
                imageUrl = SharedPreferencesMenuStorage.sanitizeImageUrl(obj.optString("image_url", "")),
                isAvailable = obj.optBoolean("is_available", true),
                isLowStock = obj.optBoolean("is_low_stock", false)
            )
        }.getOrNull()

        internal fun orderToJson(order: KitchenOrder): JSONObject = JSONObject().apply {
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
            put("order_number", order.orderNumber)
            put("table_code", order.tableCode)
            put("items", itemsArr)
            put("status", order.status.name)
            put("elapsed_seconds", order.elapsedSeconds)
            put("order_type", order.orderType.name)
            put("token_number", order.tokenNumber ?: JSONObject.NULL)
            put("customer_id", order.customerId)
            put("customer_name", order.customerName)
            put("customer_phone", order.customerPhone)
            put("delivery_address", order.deliveryAddress)
            put("gps_coordinates_label", order.gpsCoordinatesLabel ?: JSONObject.NULL)
            put("gps_map_url", order.gpsMapUrl ?: JSONObject.NULL)
            put("is_gps_fallback", order.isGpsFallback)
            put("cancel_window_remaining_seconds", order.cancelWindowRemainingSeconds)
            put("is_paid", order.isPaid)
            put("payment_method", order.paymentMethod?.name ?: JSONObject.NULL)
            put("placed_at_label", order.placedAtLabel)
            put("order_date_iso", order.orderDateIso)
            put("completed_date_iso", order.completedDateIso ?: JSONObject.NULL)
            put("completed_time_label", order.completedTimeLabel ?: JSONObject.NULL)
            put("assigned_rider_id", order.assignedRiderId ?: JSONObject.NULL)
            put("assigned_rider_name", order.assignedRiderName ?: JSONObject.NULL)
        }

        internal fun jsonToOrder(obj: JSONObject): KitchenOrder? = runCatching {
            val rawItems = obj.opt("items")
            val itemsArr = when (rawItems) {
                is JSONArray -> rawItems
                is String -> runCatching { JSONArray(rawItems) }.getOrDefault(JSONArray())
                else -> JSONArray()
            }
            val lineItems = buildList {
                for (i in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(i)
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
            val orderType = runCatching { OrderType.valueOf(obj.optString("order_type", OrderType.KIOSK.name)) }
                .getOrDefault(OrderType.KIOSK)
            val status = runCatching { OrderStatus.valueOf(obj.optString("status", OrderStatus.NEW.name)) }
                .getOrDefault(OrderStatus.NEW)
            val tokenNumber = if (obj.has("token_number") && !obj.isNull("token_number")) {
                obj.optInt("token_number", -1).takeIf { it > 0 }
            } else null
            val paymentMethod = obj.optString("payment_method", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }

            KitchenOrder(
                orderNumber = obj.getInt("order_number"),
                tableCode = obj.optString("table_code", if (orderType == OrderType.DELIVERY) "DELIVERY" else "KIOSK"),
                items = lineItems,
                status = status,
                elapsedSeconds = obj.optInt("elapsed_seconds", 0),
                orderType = orderType,
                tokenNumber = tokenNumber,
                customerId = obj.optString("customer_id", ""),
                customerName = obj.optString("customer_name", ""),
                customerPhone = obj.optString("customer_phone", ""),
                deliveryAddress = obj.optString("delivery_address", ""),
                gpsCoordinatesLabel = obj.optString("gps_coordinates_label", "").takeIf { it.isNotBlank() && it != "null" },
                gpsMapUrl = obj.optString("gps_map_url", "").takeIf { it.isNotBlank() && it != "null" },
                isGpsFallback = obj.optBoolean("is_gps_fallback", false),
                cancelWindowRemainingSeconds = obj.optInt("cancel_window_remaining_seconds", 0),
                isPaid = obj.optBoolean("is_paid", false),
                paymentMethod = paymentMethod,
                placedAtLabel = obj.optString("placed_at_label", "Just now"),
                orderDateIso = obj.optString("order_date_iso", DateUtils.todayIso()),
                completedDateIso = obj.optString("completed_date_iso", "").takeIf { it.isNotBlank() && it != "null" },
                completedTimeLabel = obj.optString("completed_time_label", "").takeIf { it.isNotBlank() && it != "null" },
                assignedRiderId = obj.optString("assigned_rider_id", "").takeIf { it.isNotBlank() && it != "null" },
                assignedRiderName = obj.optString("assigned_rider_name", "").takeIf { it.isNotBlank() && it != "null" }
            )
        }.getOrNull()

        internal fun reviewToJson(review: ReviewEntry): JSONObject = JSONObject().apply {
            val tagsArr = JSONArray()
            review.tags.forEach { tagsArr.put(it) }
            put("id", review.id)
            put("author_name", review.authorName)
            put("table_code", review.tableCode)
            put("rating", review.rating)
            put("tags", tagsArr)
            put("comment", review.comment)
            put("timestamp_label", review.timestampLabel)
            put("is_published_to_site", review.isPublishedToSite)
        }

        internal fun jsonToReview(obj: JSONObject): ReviewEntry? = runCatching {
            val rawTags = obj.opt("tags")
            val tagsArr = when (rawTags) {
                is JSONArray -> rawTags
                is String -> runCatching { JSONArray(rawTags) }.getOrDefault(JSONArray())
                else -> JSONArray()
            }
            val tags = buildList {
                for (i in 0 until tagsArr.length()) {
                    add(tagsArr.getString(i))
                }
            }
            ReviewEntry(
                id = obj.getString("id"),
                authorName = obj.optString("author_name", "Guest"),
                tableCode = obj.optString("table_code", "DELIVERY"),
                rating = obj.optInt("rating", 5).coerceIn(1, 5),
                tags = tags,
                comment = obj.optString("comment", ""),
                timestampLabel = obj.optString("timestamp_label", "Just now"),
                isPublishedToSite = obj.optBoolean("is_published_to_site", true)
            )
        }.getOrNull()

        internal fun staffToJson(member: StaffMember): JSONObject = JSONObject().apply {
            put("id", member.id)
            put("name", member.name)
            put("cnic", member.cnic)
            put("password_hash", member.passwordHash)
            put("designation", member.designation)
            put("portal_role", member.portalRole.name)
            put("phone", member.phone)
            put("address", member.address)
            put("email", member.email)
            put("shift_label", member.shiftLabel)
            put("is_on_duty", member.isOnDuty)
            put("is_relieved", member.isRelieved)
            put("scheduled_day_off", member.scheduledDayOff.name)
        }

        internal fun jsonToStaff(obj: JSONObject): StaffMember? = runCatching {
            StaffMember(
                id = obj.getString("id"),
                name = obj.getString("name"),
                cnic = obj.getString("cnic"),
                passwordHash = obj.getString("password_hash"),
                designation = obj.optString("designation", "Staff"),
                portalRole = runCatching {
                    StaffPortalRole.valueOf(obj.optString("portal_role", StaffPortalRole.GENERAL_STAFF.name))
                }.getOrDefault(StaffPortalRole.GENERAL_STAFF),
                phone = obj.optString("phone", ""),
                address = obj.optString("address", ""),
                email = obj.optString("email", ""),
                shiftLabel = obj.optString("shift_label", "10:00 - 19:00"),
                isOnDuty = obj.optBoolean("is_on_duty", false),
                isRelieved = obj.optBoolean("is_relieved", false),
                scheduledDayOff = runCatching {
                    WeekdaySlot.valueOf(obj.optString("scheduled_day_off", WeekdaySlot.NONE.name))
                }.getOrDefault(WeekdaySlot.NONE)
            )
        }.getOrNull()

        internal fun attendanceToJson(record: AttendanceRecord): JSONObject = JSONObject().apply {
            put("staff_id", record.staffId)
            put("date_iso", record.dateIso)
            put("status", record.status.name)
            put("check_in_time_label", record.checkInTimeLabel ?: JSONObject.NULL)
            put("is_admin_override", record.isAdminOverride)
        }

        internal fun jsonToAttendance(obj: JSONObject): AttendanceRecord? = runCatching {
            AttendanceRecord(
                staffId = obj.getString("staff_id"),
                dateIso = obj.getString("date_iso"),
                status = runCatching {
                    AttendanceStatus.valueOf(obj.optString("status", AttendanceStatus.PRESENT.name))
                }.getOrDefault(AttendanceStatus.PRESENT),
                checkInTimeLabel = obj.optString("check_in_time_label", "").takeIf { it.isNotBlank() && it != "null" },
                isAdminOverride = obj.optBoolean("is_admin_override", false)
            )
        }.getOrNull()

        internal fun alertToJson(alert: WhatsAppAlert): JSONObject = JSONObject().apply {
            put("id", alert.id)
            put("title", alert.title)
            put("message", alert.message)
            put("time_label", alert.timeLabel)
            put("type", alert.type.name)
            put("is_acknowledged", alert.isAcknowledged)
        }

        internal fun jsonToAlert(obj: JSONObject): WhatsAppAlert? = runCatching {
            WhatsAppAlert(
                id = obj.getString("id"),
                title = obj.getString("title"),
                message = obj.optString("message", ""),
                timeLabel = obj.optString("time_label", "Just now"),
                type = runCatching {
                    AlertType.valueOf(obj.optString("type", AlertType.NEW_ORDER.name))
                }.getOrDefault(AlertType.NEW_ORDER),
                isAcknowledged = obj.optBoolean("is_acknowledged", false)
            )
        }.getOrNull()

        internal fun salesRecordToJson(record: DailySalesRecord): JSONObject = JSONObject().apply {
            val barsArr = JSONArray()
            record.hourlyBars.forEach { pt ->
                barsArr.put(
                    JSONObject().apply {
                        put("label", pt.label)
                        put("amountRs", pt.amountRs)
                    }
                )
            }
            put("date_iso", record.dateIso)
            put("gross_revenue_rs", record.grossRevenueRs)
            put("online_revenue_rs", record.onlineRevenueRs)
            put("cash_revenue_rs", record.cashRevenueRs)
            put("delivery_revenue_rs", record.deliveryRevenueRs)
            put("kiosk_revenue_rs", record.kioskRevenueRs)
            put("delivery_order_count", record.deliveryOrderCount)
            put("kiosk_order_count", record.kioskOrderCount)
            put("top_seller_label", record.topSellerLabel)
            put("order_count", record.orderCount)
            put("hourly_bars", barsArr)
        }

        internal fun jsonToSalesRecord(obj: JSONObject): DailySalesRecord? = runCatching {
            val rawBars = obj.opt("hourly_bars")
            val barsArr = when (rawBars) {
                is JSONArray -> rawBars
                is String -> runCatching { JSONArray(rawBars) }.getOrDefault(JSONArray())
                else -> JSONArray()
            }
            val bars = buildList {
                for (i in 0 until barsArr.length()) {
                    val b = barsArr.getJSONObject(i)
                    add(
                        SalesBarPoint(
                            label = b.getString("label"),
                            amountRs = b.getInt("amountRs")
                        )
                    )
                }
            }
            DailySalesRecord(
                dateIso = obj.getString("date_iso"),
                grossRevenueRs = obj.optInt("gross_revenue_rs", 0),
                onlineRevenueRs = obj.optInt("online_revenue_rs", 0),
                cashRevenueRs = obj.optInt("cash_revenue_rs", 0),
                deliveryRevenueRs = obj.optInt("delivery_revenue_rs", 0),
                kioskRevenueRs = obj.optInt("kiosk_revenue_rs", 0),
                deliveryOrderCount = obj.optInt("delivery_order_count", 0),
                kioskOrderCount = obj.optInt("kiosk_order_count", 0),
                topSellerLabel = obj.optString("top_seller_label", ""),
                orderCount = obj.optInt("order_count", 0),
                hourlyBars = bars
            )
        }.getOrNull()
    }
}
