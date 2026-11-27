package com.example.ui.customer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.FoodArtwork
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRs
import com.example.ui.theme.*
import com.example.util.AppStrings
import com.example.util.LocalizedStrings
import com.example.viewmodel.PasswordChangeResult
import com.example.viewmodel.RestaurantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    if (uiState.isKioskModeActive) {
        KioskModeScreen(
            uiState = uiState,
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    val strings = remember(uiState.appLanguage) { AppStrings.forLanguage(uiState.appLanguage) }
    var showDeliveryCheckout by remember { mutableStateOf(false) }
    var showCustomerAuthModal by remember { mutableStateOf(false) }
    var pendingCheckoutAfterAuth by remember { mutableStateOf(false) }
    var detailMenuItem by remember { mutableStateOf<MenuItem?>(null) }

    BackHandler(enabled = uiState.customerTab != CustomerTab.MENU) {
        viewModel.selectCustomerTab(CustomerTab.MENU)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxSize()
        ) {
            // Home Delivery Status Strip + Sub-Navigation Tabs
            Surface(
                color = CrispWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = NavyPrimary,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("delivery_mode_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeliveryDining,
                                        contentDescription = null,
                                        tint = AmberWarm,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = uiState.loggedInCustomer?.name ?: strings.homeDeliveryLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CrispWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            StatusPill(
                                text = strings.deliveryEta,
                                containerColor = SkySoft,
                                contentColor = SkyDark,
                                icon = Icons.Rounded.Schedule
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val activeDeliveryCount = uiState.homeDeliveryOrders.count {
                                it.status != OrderStatus.SERVED && it.status != OrderStatus.CANCELLED
                            }
                            if (activeDeliveryCount > 0) {
                                Surface(
                                    onClick = { viewModel.selectCustomerTab(CustomerTab.TRACKER) },
                                    color = EmeraldSoft,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("header_active_delivery_pill")
                                ) {
                                    Text(
                                        text = "#$activeDeliveryCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldDark,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (uiState.loggedInCustomer != null) {
                                OutlinedButton(
                                    onClick = { viewModel.logoutCustomer() },
                                    border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("customer_logout_button")
                                ) {
                                    Text(
                                        text = strings.logoutLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoseAlert
                                    )
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        pendingCheckoutAfterAuth = false
                                        showCustomerAuthModal = true
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = SlateSurfaceVariant,
                                        contentColor = NavyPrimary
                                    ),
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("header_sign_in_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = strings.signInLabel,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    // Customer Flow Navigation Tabs
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CustomerTab.entries) { tab ->
                            val selected = uiState.customerTab == tab
                            val tabIcon = when (tab) {
                                CustomerTab.MENU -> Icons.Rounded.RestaurantMenu
                                CustomerTab.TRACKER -> Icons.Rounded.LocalShipping
                                CustomerTab.REVIEWS -> Icons.Rounded.StarRate
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectCustomerTab(tab) },
                                label = {
                                    Text(
                                        text = strings.tabLabel(tab),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = tabIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = CrispWhite,
                                    selectedLeadingIconColor = CrispWhite,
                                    containerColor = SlateSurfaceVariant,
                                    labelColor = NavyMuted,
                                    iconColor = SlateSubtext
                                ),
                                border = null,
                                modifier = Modifier.testTag("customer_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = uiState.customerTab,
                    label = "customerTabContent"
                ) { tab ->
                    when (tab) {
                        CustomerTab.MENU -> AtHomeMenuTab(
                            uiState = uiState,
                            strings = strings,
                            onSearchChange = viewModel::setHomeMenuSearchQuery,
                            onSelectFilter = viewModel::setHomeMenuFilter,
                            onAddItem = { item -> viewModel.addToCart(item) },
                            onDecrementItem = { itemId -> viewModel.decrementFromCart(itemId) },
                            onOpenItemDetail = { item -> detailMenuItem = item }
                        )
                        CustomerTab.TRACKER -> CustomerTrackerTab(
                            uiState = uiState,
                            strings = strings,
                            onCancelOrder = { orderNumber -> viewModel.cancelDeliveryOrder(orderNumber) },
                            onGoToMenu = { viewModel.selectCustomerTab(CustomerTab.MENU) }
                        )
                        CustomerTab.REVIEWS -> CustomerReviewsTab(
                            uiState = uiState,
                            strings = strings,
                            onSubmitReview = viewModel::submitReview,
                            onPublishExisting = viewModel::publishExistingReviewToSite,
                            onGoogleReviewPrompt = {
                                viewModel.emitToast("Opening Smart Dine Google Reviews page...")
                            }
                        )
                    }
                }

                // Floating Bottom Cart Bar
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.cartItemCount > 0 && uiState.customerTab == CustomerTab.MENU,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    FloatingCartBar(
                        itemCount = uiState.cartItemCount,
                        totalRs = uiState.cartTotalRs,
                        ctaLabel = strings.checkoutCta,
                        subtitle = strings.deliveryCartSubtitle,
                        strings = strings,
                        onPlaceOrder = {
                            if (uiState.loggedInCustomer == null) {
                                pendingCheckoutAfterAuth = true
                                showCustomerAuthModal = true
                            } else {
                                showDeliveryCheckout = true
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDeliveryCheckout) {
        HomeDeliveryCheckoutDialog(
            cart = uiState.cart,
            loggedInCustomer = uiState.loggedInCustomer,
            strings = strings,
            onDismiss = { showDeliveryCheckout = false },
            onConfirmOrder = { name, phone, address, coords, mapUrl, isFallback, method ->
                if (viewModel.placeHomeDeliveryOrder(name, phone, address, coords, mapUrl, isFallback, method)) {
                    showDeliveryCheckout = false
                }
            }
        )
    }

    if (showCustomerAuthModal) {
        CustomerAuthModal(
            strings = strings,
            onDismiss = {
                showCustomerAuthModal = false
                pendingCheckoutAfterAuth = false
            },
            onGoogleSignIn = {
                viewModel.loginCustomerWithGoogle()
                showCustomerAuthModal = false
                if (pendingCheckoutAfterAuth) {
                    pendingCheckoutAfterAuth = false
                    showDeliveryCheckout = true
                }
            },
            onEmailAuth = { name, email, pass ->
                val res = viewModel.loginOrSignUpCustomerWithEmail(name, email, pass)
                if (res is PasswordChangeResult.Success) {
                    showCustomerAuthModal = false
                    if (pendingCheckoutAfterAuth) {
                        pendingCheckoutAfterAuth = false
                        showDeliveryCheckout = true
                    }
                }
                res
            },
            onPhoneOtpAuth = { name, phone, otp ->
                val res = viewModel.loginCustomerWithPhoneOtp(name, phone, otp)
                if (res is PasswordChangeResult.Success) {
                    showCustomerAuthModal = false
                    if (pendingCheckoutAfterAuth) {
                        pendingCheckoutAfterAuth = false
                        showDeliveryCheckout = true
                    }
                }
                res
            }
        )
    }

    // Menu Item Detail Modal BottomSheet
    detailMenuItem?.let { item ->
        var modalQty by remember(item.id) { mutableIntStateOf(1) }
        var kitchenNote by remember(item.id) { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { detailMenuItem = null },
            containerColor = CrispWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(18.dp))
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
                                .background(NavyPrimary.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            StatusPill(
                                text = strings.soldOut,
                                containerColor = RoseAlert,
                                contentColor = CrispWhite
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = NavyPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                text = item.prepTimeRange,
                                containerColor = SlateSurfaceVariant,
                                contentColor = NavyMuted,
                                icon = Icons.Rounded.Schedule
                            )
                            StatusPill(
                                text = item.spiceLevel.label,
                                containerColor = if (item.spiceLevel == SpiceLevel.SPICY) RoseSoft else AmberSoft,
                                contentColor = if (item.spiceLevel == SpiceLevel.SPICY) RoseDark else AmberDark,
                                icon = Icons.Rounded.LocalFireDepartment
                            )
                        }
                    }
                    Text(
                        text = formatRs(item.priceRs),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                        color = NavyPrimary
                    )
                }

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SlateSubtext
                )

                OutlinedTextField(
                    value = kitchenNote,
                    onValueChange = { kitchenNote = it },
                    label = { Text(strings.specialNoteLabel) },
                    placeholder = { Text("e.g., Extra sauce, no onions") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateSurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (modalQty > 1) modalQty-- },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease quantity", tint = NavyPrimary)
                        }
                        Text(
                            text = modalQty.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        IconButton(
                            onClick = { modalQty++ },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase quantity", tint = NavyPrimary)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.addToCart(item, modalQty, kitchenNote)
                            detailMenuItem = null
                        },
                        enabled = item.isAvailable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyPrimary,
                            contentColor = CrispWhite,
                            disabledContainerColor = SlateBorder,
                            disabledContentColor = SlateSubtext
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("modal_add_to_cart_button")
                    ) {
                        Text(
                            text = if (!item.isAvailable) strings.soldOut else "${strings.addToCartLabel} • ${formatRs(item.priceRs * modalQty)}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FloatingCartBar(
    itemCount: Int,
    totalRs: Int,
    ctaLabel: String,
    subtitle: String,
    strings: LocalizedStrings,
    onPlaceOrder: () -> Unit
) {
    Surface(
        color = NavyPrimary,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("floating_cart_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CrispWhite.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShoppingBag,
                        contentDescription = null,
                        tint = CrispWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "$itemCount ${if (itemCount == 1) strings.itemLabel else strings.itemsLabel} | ${formatRs(totalRs)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                        color = CrispWhite
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = CrispWhite.copy(alpha = 0.72f)
                    )
                }
            }

            Button(
                onClick = onPlaceOrder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrispWhite,
                    contentColor = NavyPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("place_order_button")
            ) {
                Text(ctaLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
