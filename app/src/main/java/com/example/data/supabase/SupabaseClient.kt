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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class SupabaseConfig(
    val projectUrl: String = DEFAULT_SUPABASE_URL,
    val publishableKey: String = DEFAULT_SUPABASE_PUBLISHABLE_KEY
) {
    val cleanUrl: String = projectUrl.trim().trimEnd('/')
    val cleanKey: String = publishableKey.trim()

    val isConfigured: Boolean
        get() = cleanUrl.startsWith("https://") &&
            cleanKey.isNotBlank() &&
            cleanKey != PLACEHOLDER_KEY &&
            !cleanKey.startsWith("sb_secret_")

    companion object {
        const val DEFAULT_SUPABASE_URL = "https://ykpuzpzyjhsiwxasessi.supabase.co"
        const val DEFAULT_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_jC8DLmcSt9-VmadpAPCmbw_54hzx2Kg"
        private const val PLACEHOLDER_KEY = "YOUR_SUPABASE_PUBLISHABLE_KEY"

        fun fromBuildConfig(): SupabaseConfig {
            val buildUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
            val buildKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
            val resolvedUrl = if (buildUrl.startsWith("https://")) {
                buildUrl
            } else {
                DEFAULT_SUPABASE_URL
            }
            val resolvedKey = if (
                buildKey.isNotBlank() &&
                buildKey != PLACEHOLDER_KEY &&
                !buildKey.startsWith("sb_secret_")
            ) {
                buildKey
            } else {
                DEFAULT_SUPABASE_PUBLISHABLE_KEY
            }
            return SupabaseConfig(
                projectUrl = resolvedUrl,
                publishableKey = resolvedKey
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

data class SupabaseAuthSession(
    val accessToken: String,
    val userId: String,
    val email: String,
    val role: String,
    val fullName: String = "",
    val avatarUrl: String = ""
)

sealed interface SupabaseAuthResult {
    data class Success(val session: SupabaseAuthSession) : SupabaseAuthResult
    data class Error(val message: String) : SupabaseAuthResult
}

interface SupabaseService {
    val isRemoteEnabled: Boolean
    val realtimeEvents: SharedFlow<SupabaseRealtimeEvent>

    fun startRealtimeSync()
    fun stopRealtimeSync()
    fun refreshRealtimeAuth() {}

    suspend fun signInAdmin(email: String, password: String): SupabaseAuthResult
    fun beginGoogleOAuth(onPersistVerifier: ((String) -> Unit)? = null): Result<String>
    suspend fun completeGoogleOAuth(
        code: String?,
        accessTokenFromFragment: String?,
        errorMessage: String?,
        persistedVerifier: String? = null
    ): SupabaseAuthResult
    suspend fun sendEmailOtp(email: String, fullName: String = ""): Result<Unit>
    suspend fun verifyEmailOtp(email: String, token: String): SupabaseAuthResult
    suspend fun updateUserPassword(newPassword: String): Result<Unit>
    suspend fun signOutAuth()
    suspend fun authenticateEmployee(cnic: String, passwordHash: String): Result<StaffMember?>

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

    override suspend fun signInAdmin(email: String, password: String): SupabaseAuthResult =
        SupabaseAuthResult.Error("Supabase is not configured.")

    override fun beginGoogleOAuth(onPersistVerifier: ((String) -> Unit)?): Result<String> =
        Result.failure(IllegalStateException("Supabase is not configured."))

    override suspend fun completeGoogleOAuth(
        code: String?,
        accessTokenFromFragment: String?,
        errorMessage: String?,
        persistedVerifier: String?
    ): SupabaseAuthResult = SupabaseAuthResult.Error("Supabase is not configured.")

    override suspend fun sendEmailOtp(email: String, fullName: String): Result<Unit> =
        Result.failure(IllegalStateException("Supabase is not configured."))

    override suspend fun verifyEmailOtp(email: String, token: String): SupabaseAuthResult =
        SupabaseAuthResult.Error("Supabase is not configured.")

    override suspend fun updateUserPassword(newPassword: String): Result<Unit> = Result.success(Unit)
    override suspend fun signOutAuth() {}
    override suspend fun authenticateEmployee(cnic: String, passwordHash: String): Result<StaffMember?> =
        Result.success(null)

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

    @Volatile
    private var userAccessToken: String? = null

    @Volatile
    private var currentUserId: String? = null

    @Volatile
    private var pendingPkceVerifier: String? = null

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

    override fun refreshRealtimeAuth() {
        if (!isRemoteEnabled || !realtimeWanted) return
        val ws = webSocket
        val token = userAccessToken
        if (ws != null && !token.isNullOrBlank()) {
            val authMsg = JSONObject().apply {
                put("topic", REALTIME_TOPIC)
                put("event", "access_token")
                put("payload", JSONObject().put("access_token", token))
                put("ref", refCounter.getAndIncrement().toString())
            }
            ws.send(authMsg.toString())
        } else {
            connectWebSocket()
        }
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

    // ---- Supabase Auth & Role Verification ----

    override suspend fun signInAdmin(email: String, password: String): SupabaseAuthResult = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) {
            return@withContext SupabaseAuthResult.Error("Supabase is not configured. Check your Supabase URL and Publishable Key.")
        }
        val cleanEmail = email.trim().lowercase()
        val cleanPassword = password.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext SupabaseAuthResult.Error("Please enter a valid admin email address.")
        }
        if (cleanPassword.isEmpty()) {
            return@withContext SupabaseAuthResult.Error("Please enter your password.")
        }

        val authResult = passwordSignInRequest(cleanEmail, cleanPassword)
        val session = when (authResult) {
            is SupabaseAuthResult.Error -> return@withContext authResult
            is SupabaseAuthResult.Success -> authResult.session
        }

        val verifiedRole = verifyAdminOrOwnerRole(
            accessToken = session.accessToken,
            userId = session.userId,
            email = session.email
        )
        if (verifiedRole == null) {
            userAccessToken = null
            currentUserId = null
            revokeTokenBestEffort(session.accessToken)
            return@withContext SupabaseAuthResult.Error(
                "Access denied. Only accounts with an 'admin' or 'owner' role in Supabase can access the Owner Dashboard."
            )
        }

        userAccessToken = session.accessToken
        currentUserId = session.userId
        refreshRealtimeAuth()
        SupabaseAuthResult.Success(session.copy(role = verifiedRole))
    }

    override fun beginGoogleOAuth(onPersistVerifier: ((String) -> Unit)?): Result<String> {
        if (!isRemoteEnabled) {
            return Result.failure(IllegalStateException("Supabase is not configured. Check your Supabase URL and Publishable Key."))
        }
        val base = baseHttpUrl ?: return Result.failure(IllegalArgumentException("Invalid Supabase URL."))
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val codeVerifier = encodeBase64UrlNoPadding(randomBytes)
        val challengeDigest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = encodeBase64UrlNoPadding(challengeDigest)

        pendingPkceVerifier = codeVerifier
        onPersistVerifier?.invoke(codeVerifier)

        val authorizeUrl = base.newBuilder()
            .addPathSegments("auth/v1/authorize")
            .addQueryParameter("provider", "google")
            .addQueryParameter("redirect_to", GOOGLE_OAUTH_REDIRECT_URI)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "s256")
            .build()
            .toString()

        return Result.success(authorizeUrl)
    }

    override suspend fun completeGoogleOAuth(
        code: String?,
        accessTokenFromFragment: String?,
        errorMessage: String?,
        persistedVerifier: String?
    ): SupabaseAuthResult = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) {
            return@withContext SupabaseAuthResult.Error("Supabase is not configured.")
        }
        val base = baseHttpUrl ?: return@withContext SupabaseAuthResult.Error("Invalid Supabase URL.")
        val cleanError = errorMessage?.trim()?.takeIf { it.isNotBlank() }
        if (cleanError != null) {
            pendingPkceVerifier = null
            return@withContext SupabaseAuthResult.Error(cleanError)
        }

        val cleanCode = code?.trim()?.takeIf { it.isNotBlank() }
        val cleanFragmentToken = accessTokenFromFragment?.trim()?.takeIf { it.isNotBlank() }

        if (cleanCode != null) {
            val verifier = pendingPkceVerifier ?: persistedVerifier?.trim()?.takeIf { it.isNotBlank() }
            pendingPkceVerifier = null
            if (verifier.isNullOrBlank()) {
                return@withContext SupabaseAuthResult.Error("Google sign-in verification expired. Please try again.")
            }
            val tokenUrl = base.newBuilder()
                .addPathSegments("auth/v1/token")
                .addQueryParameter("grant_type", "pkce")
                .build()
            val payload = JSONObject().apply {
                put("auth_code", cleanCode)
                put("code_verifier", verifier)
            }
            val req = Request.Builder()
                .url(tokenUrl)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", config.cleanKey)
                .header("Content-Type", "application/json")
                .build()

            return@withContext executeAuthSessionRequest(
                req = req,
                fallbackEmail = "",
                fallbackRole = "customer",
                defaultError = "Google sign-in could not be completed."
            )
        }

        if (cleanFragmentToken != null) {
            pendingPkceVerifier = null
            val userUrl = base.newBuilder()
                .addPathSegments("auth/v1/user")
                .build()
            val req = Request.Builder()
                .url(userUrl)
                .get()
                .header("apikey", config.cleanKey)
                .header("Authorization", "Bearer $cleanFragmentToken")
                .header("Accept", "application/json")
                .build()

            return@withContext runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@use SupabaseAuthResult.Error(
                            parseSupabaseError(raw, "Could not verify Google session with Supabase.")
                        )
                    }
                    val userObj = JSONObject(raw)
                    val session = buildSessionFromUser(
                        accessToken = cleanFragmentToken,
                        userObj = userObj,
                        fallbackEmail = "",
                        fallbackRole = "customer"
                    ) ?: return@use SupabaseAuthResult.Error("Invalid user session returned by Supabase.")
                    userAccessToken = session.accessToken
                    currentUserId = session.userId
                    refreshRealtimeAuth()
                    SupabaseAuthResult.Success(session)
                }
            }.getOrElse { err ->
                SupabaseAuthResult.Error("Network error completing Google Sign-In: ${err.localizedMessage ?: "Check connection"}")
            }
        }

        SupabaseAuthResult.Error("No authorization code or token received from Google Sign-In.")
    }

    override suspend fun sendEmailOtp(email: String, fullName: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured. Check your Supabase URL and Publishable Key."))
        }
        val cleanEmail = email.trim().lowercase()
        val cleanName = fullName.trim()
        val base = baseHttpUrl
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL."))

        val otpUrl = base.newBuilder()
            .addPathSegments("auth/v1/otp")
            .build()
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("create_user", true)
            if (cleanName.isNotBlank()) {
                put("data", JSONObject().put("full_name", cleanName))
            }
        }
        val req = Request.Builder()
            .url(otpUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", config.cleanKey)
            .header("Content-Type", "application/json")
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error(parseSupabaseError(raw, "Unable to send verification code to $cleanEmail."))
                }
            }
        }
    }

    override suspend fun verifyEmailOtp(email: String, token: String): SupabaseAuthResult = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) {
            return@withContext SupabaseAuthResult.Error("Supabase is not configured.")
        }
        val cleanEmail = email.trim().lowercase()
        val cleanToken = token.trim()
        val base = baseHttpUrl ?: return@withContext SupabaseAuthResult.Error("Invalid Supabase URL.")

        val verifyUrl = base.newBuilder()
            .addPathSegments("auth/v1/verify")
            .build()
        val payload = JSONObject().apply {
            put("type", "email")
            put("email", cleanEmail)
            put("token", cleanToken)
        }
        val req = Request.Builder()
            .url(verifyUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", config.cleanKey)
            .header("Content-Type", "application/json")
            .build()

        executeAuthSessionRequest(
            req = req,
            fallbackEmail = cleanEmail,
            fallbackRole = "customer",
            defaultError = "Invalid or expired verification code."
        )
    }

    private fun executeAuthSessionRequest(
        req: Request,
        fallbackEmail: String,
        fallbackRole: String,
        defaultError: String
    ): SupabaseAuthResult {
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use SupabaseAuthResult.Error(parseSupabaseError(raw, defaultError))
                }
                val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                val accessToken = json.optString("access_token", "")
                val userObj = json.optJSONObject("user")
                val session = buildSessionFromUser(
                    accessToken = accessToken,
                    userObj = userObj,
                    fallbackEmail = fallbackEmail,
                    fallbackRole = fallbackRole
                ) ?: return@use SupabaseAuthResult.Error("Authentication succeeded without a valid session token.")
                userAccessToken = session.accessToken
                currentUserId = session.userId
                refreshRealtimeAuth()
                SupabaseAuthResult.Success(session)
            }
        }.getOrElse { err ->
            SupabaseAuthResult.Error("Network error while authenticating: ${err.localizedMessage ?: "Check connection"}")
        }
    }

    private fun buildSessionFromUser(
        accessToken: String,
        userObj: JSONObject?,
        fallbackEmail: String,
        fallbackRole: String
    ): SupabaseAuthSession? {
        val userId = userObj?.optString("id", "").orEmpty()
        if (accessToken.isBlank() || userId.isBlank()) return null
        val userEmail = userObj?.optString("email", fallbackEmail).orEmpty().ifBlank { fallbackEmail }
        val meta = userObj?.optJSONObject("user_metadata")
        val fullName = meta?.optString("full_name", "")?.takeIf { it.isNotBlank() }
            ?: meta?.optString("name", "")?.takeIf { it.isNotBlank() }
            ?: ""
        val rawAvatar = meta?.optString("avatar_url", "")?.takeIf { it.isNotBlank() }
            ?: meta?.optString("picture", "")?.takeIf { it.isNotBlank() }
            ?: ""
        val safeAvatar = SharedPreferencesMenuStorage.sanitizeImageUrl(rawAvatar)
        return SupabaseAuthSession(
            accessToken = accessToken,
            userId = userId,
            email = userEmail,
            role = fallbackRole,
            fullName = fullName,
            avatarUrl = safeAvatar
        )
    }

    override suspend fun updateUserPassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = userAccessToken
            ?: return@withContext Result.failure(IllegalStateException("Active Supabase admin session required."))
        val base = baseHttpUrl
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))
        val url = base.newBuilder()
            .addPathSegments("auth/v1/user")
            .build()
        val body = JSONObject().put("password", newPassword).toString().toRequestBody(JSON_MEDIA_TYPE)
        val req = Request.Builder()
            .url(url)
            .put(body)
            .header("apikey", config.cleanKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    error(parseSupabaseError(raw, "Failed to update password."))
                }
            }
        }
    }

    override suspend fun signOutAuth() {
        val token = userAccessToken
        userAccessToken = null
        currentUserId = null
        if (!token.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                revokeTokenBestEffort(token)
            }
        }
        refreshRealtimeAuth()
    }

    override suspend fun authenticateEmployee(cnic: String, passwordHash: String): Result<StaffMember?> =
        withContext(Dispatchers.IO) {
            if (!isRemoteEnabled) return@withContext Result.failure(IllegalStateException("Supabase not configured"))
            val url = restUrlBuilder("rpc/authenticate_staff")?.build()
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))
            val payload = JSONObject().apply {
                put("p_cnic", cnic)
                put("p_password_hash", passwordHash)
            }
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("apikey", config.cleanKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("Staff login verification failed (${resp.code})")
                    }
                    val arr = if (raw.isBlank()) JSONArray() else JSONArray(raw)
                    if (arr.length() == 0) null else jsonToStaff(arr.getJSONObject(0))
                }
            }
        }

    private fun passwordSignInRequest(email: String, password: String): SupabaseAuthResult {
        val base = baseHttpUrl ?: return SupabaseAuthResult.Error("Invalid Supabase URL.")
        val url = base.newBuilder()
            .addPathSegments("auth/v1/token")
            .addQueryParameter("grant_type", "password")
            .build()
        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", config.cleanKey)
            .header("Content-Type", "application/json")
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = parseSupabaseError(raw, "Invalid email or password.")
                    return@use SupabaseAuthResult.Error(msg)
                }
                val json = JSONObject(raw)
                val accessToken = json.optString("access_token", "")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id", "").orEmpty()
                val userEmail = userObj?.optString("email", email).orEmpty()
                val fullName = userObj?.optJSONObject("user_metadata")?.optString("full_name", "").orEmpty()
                if (accessToken.isBlank() || userId.isBlank()) {
                    SupabaseAuthResult.Error("Authentication succeeded without a valid session token.")
                } else {
                    SupabaseAuthResult.Success(
                        SupabaseAuthSession(
                            accessToken = accessToken,
                            userId = userId,
                            email = userEmail,
                            role = "",
                            fullName = fullName
                        )
                    )
                }
            }
        }.getOrElse { err ->
            SupabaseAuthResult.Error("Network error while signing in: ${err.localizedMessage ?: "Check internet connection"}")
        }
    }

    private fun verifyAdminOrOwnerRole(
        accessToken: String,
        userId: String,
        email: String
    ): String? {
        // 1. Check public.profiles table for role = 'admin' or 'owner'
        val profilesUrl = restUrlBuilder("profiles")
            ?.addQueryParameter("id", "eq.$userId")
            ?.addQueryParameter("select", "role")
            ?.build()
        if (profilesUrl != null) {
            val req = Request.Builder()
                .url(profilesUrl)
                .get()
                .header("apikey", config.cleanKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            val profileRole = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val raw = resp.body?.string().orEmpty()
                    val arr = if (raw.isBlank()) JSONArray() else JSONArray(raw)
                    if (arr.length() > 0) arr.getJSONObject(0).optString("role", "").trim().lowercase() else null
                }
            }.getOrNull()
            if (profileRole == "admin" || profileRole == "owner") {
                return profileRole
            }
        }

        // 2. Also check public.staff_members table for portal_role in ('admin', 'owner')
        val staffUrl = restUrlBuilder(TABLE_STAFF)
            ?.addQueryParameter("email", "eq.$email")
            ?.addQueryParameter("select", "portal_role,is_relieved")
            ?.build()
        if (staffUrl != null) {
            val req = Request.Builder()
                .url(staffUrl)
                .get()
                .header("apikey", config.cleanKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            val staffRole = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val raw = resp.body?.string().orEmpty()
                    val arr = if (raw.isBlank()) JSONArray() else JSONArray(raw)
                    if (arr.length() > 0) {
                        val row = arr.getJSONObject(0)
                        val relieved = row.optBoolean("is_relieved", false)
                        val role = row.optString("portal_role", "").trim().lowercase()
                        if (!relieved && (role == "admin" || role == "owner")) role else null
                    } else null
                }
            }.getOrNull()
            if (staffRole != null) return staffRole
        }

        return null
    }

    private fun revokeTokenBestEffort(accessToken: String) {
        val base = baseHttpUrl ?: return
        val logoutUrl = base.newBuilder().addPathSegments("auth/v1/logout").build()
        val req = Request.Builder()
            .url(logoutUrl)
            .post("".toRequestBody(JSON_MEDIA_TYPE))
            .header("apikey", config.cleanKey)
            .header("Authorization", "Bearer $accessToken")
            .build()
        runCatching { httpClient.newCall(req).execute().close() }
    }

    private fun parseSupabaseError(raw: String, fallback: String): String {
        return runCatching {
            val obj = JSONObject(raw)
            obj.optString("error_description").takeIf { it.isNotBlank() }
                ?: obj.optString("msg").takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback)
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
                    userAccessToken?.takeIf { it.isNotBlank() }?.let { token ->
                        put("access_token", token)
                    }
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
        val result = upsertRows(
            table = TABLE_ORDERS,
            onConflict = "order_number",
            payload = JSONArray().put(orderToJson(order))
        )
        if (result.isSuccess && order.items.isNotEmpty()) {
            // Best-effort sync to normalized order_items table
            runCatching {
                deleteWhere(table = TABLE_ORDER_ITEMS, column = "order_number", eqValue = order.orderNumber.toString())
                val itemsArray = JSONArray()
                order.items.forEach { line ->
                    itemsArray.put(
                        JSONObject().apply {
                            put("order_number", order.orderNumber)
                            put("menu_item_id", line.menuItemId)
                            put("title", line.title)
                            put("price_rs", line.priceRs)
                            put("quantity", line.quantity)
                            put("note", line.note)
                        }
                    )
                }
                insertRows(table = TABLE_ORDER_ITEMS, payload = itemsArray)
            }
        }
        return result
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

    private fun restRequestBuilder(url: HttpUrl): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("apikey", config.cleanKey)
            .apply {
                val token = userAccessToken
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
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

        val request = restRequestBuilder(url)
            .get()
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
        val request = restRequestBuilder(url)
            .post(body)
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

    private suspend fun insertRows(
        table: String,
        payload: JSONArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isRemoteEnabled) return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        val url = restUrlBuilder(table)?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = restRequestBuilder(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    error("Supabase INSERT $table failed (${resp.code}): $err")
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

        val request = restRequestBuilder(url)
            .delete()
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
        const val GOOGLE_OAUTH_REDIRECT_URI = "smartdine://login-callback"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        internal fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
            val out = StringBuilder((bytes.size * 4 + 2) / 3)
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
                val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
                out.append(BASE64_URL_ALPHABET[b0 ushr 2])
                out.append(BASE64_URL_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                if (i + 1 < bytes.size) {
                    out.append(BASE64_URL_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
                }
                if (i + 2 < bytes.size) {
                    out.append(BASE64_URL_ALPHABET[b2 and 0x3F])
                }
                i += 3
            }
            return out.toString()
        }

        const val TABLE_MENU_ITEMS = "menu_items"
        const val TABLE_ORDERS = "orders"
        const val TABLE_ORDER_ITEMS = "order_items"
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
