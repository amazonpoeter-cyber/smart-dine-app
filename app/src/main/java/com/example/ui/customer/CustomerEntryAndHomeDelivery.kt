package com.example.ui.customer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.*
import com.example.ui.components.FoodArtwork
import com.example.ui.components.SmartDineBrandLogo
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRs
import com.example.ui.theme.*
import com.example.util.LocalizedStrings
import com.example.viewmodel.PasswordChangeResult
import java.util.Locale
import kotlin.math.abs

@Composable
fun AtHomeMenuTab(
    uiState: RestaurantUiState,
    strings: LocalizedStrings,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (HomeMenuFilter) -> Unit,
    onAddItem: (MenuItem) -> Unit,
    onDecrementItem: (String) -> Unit,
    onOpenItemDetail: (MenuItem) -> Unit
) {
    val query = uiState.homeMenuSearchQuery.trim().lowercase()
    val filteredItems = remember(uiState.menuItems, query, uiState.homeMenuFilter) {
        uiState.menuItems.filter { item ->
            val matchesQuery = query.isEmpty() ||
                item.title.lowercase().contains(query) ||
                item.description.lowercase().contains(query)
            val matchesFilter = when (uiState.homeMenuFilter) {
                HomeMenuFilter.ALL -> true
                HomeMenuFilter.MAINS -> item.category == MenuCategory.MAINS
                HomeMenuFilter.DRINKS -> item.category == MenuCategory.DRINKS
                HomeMenuFilter.DESSERTS -> item.category == MenuCategory.DESSERTS
                HomeMenuFilter.SPICY -> item.spiceLevel == SpiceLevel.SPICY || item.spiceLevel == SpiceLevel.MEDIUM
                HomeMenuFilter.UNDER_1000 -> item.priceRs < 1000
            }
            matchesQuery && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar & Category/Dietary Filter Pills
        Surface(
            color = CrispWhite,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.homeMenuSearchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(strings.searchMenuPlaceholder) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SlateSubtext) },
                    trailingIcon = {
                        if (uiState.homeMenuSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn, tint = SlateSubtext)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_menu_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeMenuFilter.entries.forEach { filter ->
                        val selected = uiState.homeMenuFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectFilter(filter) },
                            label = {
                                Text(strings.filterLabel(filter), style = MaterialTheme.typography.labelMedium)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = CrispWhite,
                                containerColor = SlateSurfaceVariant,
                                labelColor = NavyMuted
                            ),
                            border = null,
                            modifier = Modifier.testTag("home_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = SlateHint, modifier = Modifier.size(44.dp))
                    Text(strings.noDishesMatch, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                    TextButton(
                        onClick = {
                            onSearchChange("")
                            onSelectFilter(HomeMenuFilter.ALL)
                        }
                    ) {
                        Text(strings.resetFilters, color = NavyPrimary)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = if (uiState.cartItemCount > 0) 98.dp else 28.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val cartQty = uiState.cart.firstOrNull { it.menuItem.id == item.id }?.quantity ?: 0
                    GridMenuItemCard(
                        item = item,
                        cartQty = cartQty,
                        strings = strings,
                        onCardClick = { onOpenItemDetail(item) },
                        onAdd = { onAddItem(item) },
                        onDecrement = { onDecrementItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GridMenuItemCard(
    item: MenuItem,
    cartQty: Int,
    strings: LocalizedStrings,
    onCardClick: () -> Unit,
    onAdd: () -> Unit,
    onDecrement: () -> Unit
) {
    Surface(
        onClick = onCardClick,
        color = CrispWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SlateBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_grid_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                FoodArtwork(
                    artwork = item.artwork,
                    contentDescription = item.title,
                    imageUrl = item.imageUrl,
                    modifier = Modifier.fillMaxSize()
                )
                if (!item.isAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NavyPrimary.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.soldOut, style = MaterialTheme.typography.labelSmall, color = CrispWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.isAvailable) NavyPrimary else SlateSubtext,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = item.prepTimeRange,
                        containerColor = SlateSurfaceVariant,
                        contentColor = SlateSubtext
                    )
                    if (item.spiceLevel == SpiceLevel.SPICY) {
                        StatusPill(
                            text = if (strings.isUrdu) "مصالحے دار" else "Spicy",
                            containerColor = RoseSoft,
                            contentColor = RoseDark
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRs(item.priceRs),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                    color = NavyPrimary
                )

                if (!item.isAvailable) {
                    Text(strings.soldOut, style = MaterialTheme.typography.labelSmall, color = RoseDark)
                } else if (cartQty > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(NavyPrimary)
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDecrement, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Remove one", tint = CrispWhite, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = cartQty.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add one more", tint = CrispWhite, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = onAdd,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NavyPrimary,
                            contentColor = CrispWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("home_add_item_${item.id}")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(strings.addButton, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RequiredFieldLabel(label: String) {
    Text(
        buildAnnotatedString {
            append(label)
            append(" ")
            withStyle(SpanStyle(color = RoseAlert, fontWeight = FontWeight.Bold)) {
                append("*")
            }
        }
    )
}

@Composable
fun HomeDeliveryCheckoutDialog(
    cart: List<CartItem>,
    loggedInCustomer: CustomerProfile?,
    strings: LocalizedStrings,
    onDismiss: () -> Unit,
    onConfirmOrder: (
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        gpsCoordinatesLabel: String?,
        gpsMapUrl: String?,
        isGpsFallback: Boolean,
        paymentMethod: PaymentMethod
    ) -> Unit
) {
    val context = LocalContext.current
    var customerName by remember(loggedInCustomer) { mutableStateOf(loggedInCustomer?.name ?: "") }
    var customerPhone by remember(loggedInCustomer) { mutableStateOf(loggedInCustomer?.phone ?: "") }
    var deliveryAddress by remember { mutableStateOf("") }
    var gpsCoords by remember { mutableStateOf<String?>(null) }
    var gpsMapUrl by remember { mutableStateOf<String?>(null) }
    var isGpsFallback by remember { mutableStateOf(false) }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.CASH_ON_DELIVERY) }
    var showValidationError by remember { mutableStateOf(false) }

    val subtotal = cart.sumOf { it.menuItem.priceRs * it.quantity }
    val tax = (subtotal * 0.05).toInt()
    val grandTotal = subtotal + tax

    val applyLocationResult: (Boolean) -> Unit = { hasPermission ->
        val deviceLocation = if (hasPermission) resolveLastKnownCoarseLocation(context) else null
        if (deviceLocation != null) {
            val (label, url) = formatCoordinates(deviceLocation.latitude, deviceLocation.longitude)
            gpsCoords = label
            gpsMapUrl = url
            isGpsFallback = false
        } else {
            gpsCoords = "32.5101° N, 74.5361° E"
            gpsMapUrl = "https://maps.google.com/?q=32.5101,74.5361"
            isGpsFallback = true
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        applyLocationResult(granted)
    }

    val isFormValid = customerName.isNotBlank() &&
        customerPhone.isNotBlank() &&
        deliveryAddress.isNotBlank() &&
        cart.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        title = {
            Column {
                Text(strings.checkoutTitle, style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
                Text("${cart.sumOf { it.quantity }} ${strings.itemsLabel} • ${formatRs(grandTotal)}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono), color = SlateSubtext)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { RequiredFieldLabel(strings.customerNameLabel) },
                    placeholder = { Text("e.g., Bilal Ahmed") },
                    singleLine = true,
                    isError = showValidationError && customerName.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("delivery_name_input")
                )

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { RequiredFieldLabel(strings.phoneLabel) },
                    placeholder = { Text("+92 300 1234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = showValidationError && customerPhone.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("delivery_phone_input")
                )

                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { RequiredFieldLabel(strings.deliveryAddressLabel) },
                    placeholder = { Text("House #, Street, Sector / Block, City") },
                    minLines = 2,
                    maxLines = 4,
                    isError = showValidationError && deliveryAddress.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("delivery_address_input")
                )

                // Optional Share Live GPS Location
                Surface(
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.shareGpsOptional,
                            style = MaterialTheme.typography.labelMedium,
                            color = NavyPrimary
                        )
                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    applyLocationResult(true)
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NavyPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("share_gps_location_button")
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.shareGpsBtn, style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                        }

                        gpsCoords?.let { coords ->
                            Surface(
                                color = EmeraldSoft,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("attached_gps_pin_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isGpsFallback) strings.gpsPreviewAttached else strings.gpsLiveAttached,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = coords,
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                            color = NavyPrimary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            gpsCoords = null
                                            gpsMapUrl = null
                                            isGpsFallback = false
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove GPS pin", tint = EmeraldDark, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Text(strings.paymentMethodLabel, style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                listOf(PaymentMethod.CASH_ON_DELIVERY, PaymentMethod.ONLINE_DELIVERY).forEach { method ->
                    val selected = selectedPayment == method
                    Surface(
                        onClick = { selectedPayment = method },
                        color = if (selected) NavyPrimary else SlateSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selected) NavyPrimary else SlateBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delivery_payment_${method.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (method == PaymentMethod.CASH_ON_DELIVERY) Icons.Rounded.Payments else Icons.Rounded.CreditCard,
                                contentDescription = null,
                                tint = if (selected) CrispWhite else NavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.paymentMethodLabel(method), style = MaterialTheme.typography.titleSmall, color = if (selected) CrispWhite else NavyPrimary)
                                Text(method.subtitle, style = MaterialTheme.typography.labelSmall, color = if (selected) CrispWhite.copy(alpha = 0.78f) else SlateSubtext)
                            }
                            RadioButton(
                                selected = selected,
                                onClick = { selectedPayment = method },
                                colors = RadioButtonDefaults.colors(selectedColor = CrispWhite, unselectedColor = SlateHint)
                            )
                        }
                    }
                }

                if (showValidationError && !isFormValid) {
                    Text(
                        text = strings.validationRequiredMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAlert
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isFormValid) {
                        showValidationError = true
                    } else {
                        onConfirmOrder(
                            customerName,
                            customerPhone,
                            deliveryAddress,
                            gpsCoords,
                            gpsMapUrl,
                            isGpsFallback,
                            selectedPayment
                        )
                    }
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyPrimary,
                    contentColor = CrispWhite,
                    disabledContainerColor = SlateBorder,
                    disabledContentColor = SlateSubtext
                ),
                modifier = Modifier.testTag("confirm_delivery_order_button")
            ) {
                Text("${strings.placeOrderCta} • ${formatRs(grandTotal)}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelBtn, color = SlateSubtext) }
        }
    )
}

@Composable
fun CustomerAuthModal(
    strings: LocalizedStrings,
    onDismiss: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onEmailAuth: (name: String, email: String, password: String) -> PasswordChangeResult,
    onPhoneOtpAuth: (name: String, phone: String, otp: String) -> PasswordChangeResult
) {
    var selectedAuthTab by remember { mutableIntStateOf(0) } // 0 = Email & Password, 1 = Phone & OTP
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmartDineBrandLogo(size = 40.dp)
                Column {
                    Text(strings.authModalTitle, style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
                    Text(strings.authModalSubtitle, style = MaterialTheme.typography.labelSmall, color = SlateSubtext)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Continue with Google (Daraz-style prominent button)
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    border = BorderStroke(1.2.dp, SlateBorder),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CrispWhite, contentColor = NavyPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_continue_google_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA4335).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF4285F4),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(strings.continueWithGoogle, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = SlateBorder)
                    Text("OR", style = MaterialTheme.typography.labelSmall, color = SlateHint)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = SlateBorder)
                }

                // Auth Method Toggle: Email & Password vs Phone & OTP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedAuthTab == 0,
                        onClick = { selectedAuthTab = 0; errorMsg = null },
                        label = { Text(strings.emailTab, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = CrispWhite),
                        modifier = Modifier.weight(1f).testTag("auth_tab_email")
                    )
                    FilterChip(
                        selected = selectedAuthTab == 1,
                        onClick = { selectedAuthTab = 1; errorMsg = null },
                        label = { Text(strings.phoneTab, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = CrispWhite),
                        modifier = Modifier.weight(1f).testTag("auth_tab_phone")
                    )
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; errorMsg = null },
                    label = { Text(strings.fullNameLabel) },
                    placeholder = { Text("e.g., Ayesha Khan") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("auth_name_input")
                )

                if (selectedAuthTab == 0) {
                    // 2. Email & Password
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMsg = null },
                        label = { Text(strings.emailLabel) },
                        placeholder = { Text("name@example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        label = { Text(strings.passwordLabel) },
                        placeholder = { Text("Min 4 characters") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                    )

                    if (errorMsg != null) {
                        Text(errorMsg!!, style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                    }

                    Button(
                        onClick = {
                            when (val res = onEmailAuth(fullName, email, password)) {
                                is PasswordChangeResult.Success -> Unit
                                is PasswordChangeResult.Error -> errorMsg = res.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_email_submit_button")
                    ) {
                        Text(strings.loginWithEmailBtn, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    // 3. Phone Number + Quick Simulated OTP (e.g. 1234)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it; errorMsg = null },
                            label = { Text(strings.phoneLabel) },
                            placeholder = { Text("+92 300 1234567") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("auth_phone_input")
                        )
                        FilledTonalButton(
                            onClick = {
                                if (phone.filter { it.isDigit() }.length >= 7) {
                                    otpSent = true
                                    errorMsg = null
                                } else {
                                    errorMsg = "Enter a valid phone number first."
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp).testTag("auth_send_otp_button")
                        ) {
                            Text(strings.sendOtpBtn, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (otpSent) {
                        StatusPill(
                            text = "Simulated SMS OTP sent: 1234",
                            containerColor = EmeraldSoft,
                            contentColor = EmeraldDark,
                            icon = Icons.Rounded.Sms
                        )
                    }

                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }; errorMsg = null },
                        label = { Text(strings.otpLabel) },
                        placeholder = { Text("1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_otp_input")
                    )

                    if (errorMsg != null) {
                        Text(errorMsg!!, style = MaterialTheme.typography.labelSmall, color = RoseAlert)
                    }

                    Button(
                        onClick = {
                            when (val res = onPhoneOtpAuth(fullName, phone, otpCode)) {
                                is PasswordChangeResult.Success -> Unit
                                is PasswordChangeResult.Error -> errorMsg = res.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_phone_submit_button")
                    ) {
                        Text(strings.verifyAndContinueBtn, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelBtn, color = SlateSubtext)
            }
        }
    )
}

@SuppressLint("MissingPermission")
private fun resolveLastKnownCoarseLocation(context: Context): Location? {
    return runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        providers.firstNotNullOfOrNull { provider ->
            if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
        }
    }.getOrNull()
}

private fun formatCoordinates(lat: Double, lng: Double): Pair<String, String> {
    val latDir = if (lat >= 0) "N" else "S"
    val lngDir = if (lng >= 0) "E" else "W"
    val label = String.format(Locale.US, "%.4f° %s, %.4f° %s", abs(lat), latDir, abs(lng), lngDir)
    val url = String.format(Locale.US, "https://maps.google.com/?q=%.4f,%.4f", lat, lng)
    return label to url
}
