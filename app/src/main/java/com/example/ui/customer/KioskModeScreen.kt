package com.example.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.FoodArtwork
import com.example.ui.components.StatusPill
import com.example.ui.components.ThermalReceiptModal
import com.example.ui.components.formatRs
import com.example.ui.theme.*
import com.example.util.AppStrings
import com.example.util.LocalizedStrings
import com.example.util.ThermalReceiptData
import com.example.viewmodel.RestaurantViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskModeScreen(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    val strings = remember(uiState.appLanguage) { AppStrings.forLanguage(uiState.appLanguage) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var detailMenuItem by remember { mutableStateOf<MenuItem?>(null) }
    var receiptModalData by remember { mutableStateOf<ThermalReceiptData?>(null) }

    val activeTokenOrder = uiState.activeKioskTokenOrder

    if (activeTokenOrder != null) {
        KioskTokenConfirmationView(
            order = activeTokenOrder,
            strings = strings,
            onOpenReceiptModal = {
                receiptModalData = ThermalReceiptData.fromOrder(activeTokenOrder)
            },
            onResetForNextGuest = {
                receiptModalData = null
                viewModel.resetKioskForNextCustomer()
            },
            modifier = modifier
        )
    } else {
        val visibleItems = remember(uiState.menuItems, uiState.selectedKioskCategory) {
            val cat = uiState.selectedKioskCategory
            if (cat == null) uiState.menuItems else uiState.menuItems.filter { it.category == cat }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SlateBackground),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
            ) {
                // Touch Category Filter Bar
                Surface(
                    color = CrispWhite,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = uiState.selectedKioskCategory == null
                        Surface(
                            onClick = { viewModel.selectKioskCategory(null) },
                            color = if (allSelected) NavyPrimary else SlateSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("kiosk_cat_all")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestaurantMenu,
                                    contentDescription = null,
                                    tint = if (allSelected) CrispWhite else NavyPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${strings.kioskAllCategories} (${uiState.menuItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (allSelected) CrispWhite else NavyPrimary
                                )
                            }
                        }

                        MenuCategory.entries.forEach { category ->
                            val selected = uiState.selectedKioskCategory == category
                            val count = uiState.menuItems.count { it.category == category }
                            val catIcon = when (category) {
                                MenuCategory.MAINS -> Icons.Rounded.LunchDining
                                MenuCategory.DRINKS -> Icons.Rounded.LocalCafe
                                MenuCategory.DESSERTS -> Icons.Rounded.Cake
                            }
                            Surface(
                                onClick = { viewModel.selectKioskCategory(category) },
                                color = if (selected) NavyPrimary else SlateSurfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("kiosk_cat_${category.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = catIcon,
                                        contentDescription = null,
                                        tint = if (selected) CrispWhite else NavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${strings.categoryLabel(category)} ($count)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) CrispWhite else NavyPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Touch-Friendly Kiosk Menu Grid + Hero Banner
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 172.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = if (uiState.cartItemCount > 0) 110.dp else 28.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            color = NavyPrimary,
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kiosk_hero_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = strings.atRestaurantKioskTitle,
                                        containerColor = EmeraldSuccess.copy(alpha = 0.22f),
                                        contentColor = EmeraldSuccess,
                                        icon = Icons.Rounded.TouchApp
                                    )
                                    Surface(
                                        onClick = { viewModel.toggleAppLanguage() },
                                        color = CrispWhite.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("kiosk_banner_language_pill")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Translate, contentDescription = null, tint = CrispWhite, modifier = Modifier.size(15.dp))
                                            Text(
                                                text = if (uiState.appLanguage == AppLanguage.EN) "اردو / Eng" else "Eng / اردو",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CrispWhite,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = strings.kioskBannerHeadline,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = CrispWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.kioskBannerSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CrispWhite.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    items(visibleItems, key = { it.id }) { item ->
                        val cartQty = uiState.cart.firstOrNull { it.menuItem.id == item.id }?.quantity ?: 0
                        KioskMenuItemCard(
                            item = item,
                            cartQty = cartQty,
                            strings = strings,
                            onCardClick = { detailMenuItem = item },
                            onAdd = { viewModel.addToCart(item) },
                            onDecrement = { viewModel.decrementFromCart(item.id) }
                        )
                    }
                }
            }

            // Floating Touch Checkout Cart Bar
            AnimatedVisibility(
                visible = uiState.cartItemCount > 0,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Surface(
                    color = NavyPrimary,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth()
                        .testTag("kiosk_floating_cart_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CrispWhite.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.ShoppingBag, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = "${uiState.cartItemCount} ${if (uiState.cartItemCount == 1) strings.itemLabel else strings.itemsLabel} • ${formatRs(uiState.cartTotalRs)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                    color = CrispWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.kioskCartSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = { showCheckoutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CrispWhite, contentColor = NavyPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("kiosk_checkout_button")
                        ) {
                            Text(strings.checkoutCta, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCheckoutDialog && uiState.cart.isNotEmpty()) {
        KioskCheckoutDialog(
            cart = uiState.cart,
            nextTokenNumber = uiState.nextKioskTokenNumber,
            strings = strings,
            onDismiss = { showCheckoutDialog = false },
            onPlaceOrder = { method ->
                val created = viewModel.placeKioskOrder(method)
                if (created != null) {
                    showCheckoutDialog = false
                }
            }
        )
    }

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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(item.prepTimeRange, SlateSurfaceVariant, NavyMuted, Icons.Rounded.Schedule)
                            StatusPill(
                                item.spiceLevel.label,
                                if (item.spiceLevel == SpiceLevel.SPICY) RoseSoft else AmberSoft,
                                if (item.spiceLevel == SpiceLevel.SPICY) RoseDark else AmberDark,
                                Icons.Rounded.LocalFireDepartment
                            )
                        }
                    }
                    Text(
                        text = formatRs(item.priceRs),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                        color = NavyPrimary
                    )
                }

                Text(item.description, style = MaterialTheme.typography.bodyLarge, color = SlateSubtext)

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
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("kiosk_modal_add_button")
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

    receiptModalData?.let { modalReceipt ->
        ThermalReceiptModal(
            receipt = modalReceipt,
            strings = strings,
            onDismiss = { receiptModalData = null },
            onFeedbackMessage = viewModel::emitToast
        )
    }
}

@Composable
private fun KioskMenuItemCard(
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
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, SlateBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kiosk_item_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
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
                        StatusPill(strings.soldOut, RoseAlert, CrispWhite)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.isAvailable) NavyPrimary else SlateSubtext,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(item.prepTimeRange, SlateSurfaceVariant, SlateSubtext)
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
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )

                if (!item.isAvailable) {
                    Text(strings.soldOut, style = MaterialTheme.typography.labelMedium, color = RoseDark)
                } else if (cartQty > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavyPrimary)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier.size(44.dp).testTag("kiosk_dec_${item.id}")
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Remove one", tint = CrispWhite, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = cartQty.toString(),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = onAdd,
                            modifier = Modifier.size(44.dp).testTag("kiosk_inc_${item.id}")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add one", tint = CrispWhite, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("kiosk_add_item_${item.id}")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addButton, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun KioskCheckoutDialog(
    cart: List<CartItem>,
    nextTokenNumber: Int,
    strings: LocalizedStrings,
    onDismiss: () -> Unit,
    onPlaceOrder: (PaymentMethod) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.PAY_AT_COUNTER) }
    val subtotal = cart.sumOf { it.menuItem.priceRs * it.quantity }
    val tax = (subtotal * 0.05).toInt()
    val grandTotal = subtotal + tax

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(
                    text = "${strings.kioskTokenLabel} #$nextTokenNumber",
                    containerColor = EmeraldSoft,
                    contentColor = EmeraldDark,
                    icon = Icons.Rounded.ConfirmationNumber
                )
                Text(strings.kioskCheckoutTitle, style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cart.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${line.quantity}x ${line.menuItem.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NavyPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatRs(line.menuItem.priceRs * line.quantity),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                                    color = NavyMuted
                                )
                            }
                        }
                        HorizontalDivider(color = SlateBorder)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.subtotal, style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
                            Text(formatRs(subtotal), style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono), color = NavyMuted)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.taxGst, style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
                            Text(formatRs(tax), style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono), color = NavyMuted)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.grandTotal, style = MaterialTheme.typography.titleSmall, color = NavyPrimary, fontWeight = FontWeight.Bold)
                            Text(formatRs(grandTotal), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono), color = NavyPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(strings.paymentMethodLabel, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)

                listOf(PaymentMethod.PAY_AT_COUNTER, PaymentMethod.ONLINE).forEach { method ->
                    val selected = selectedMethod == method
                    val icon = if (method == PaymentMethod.PAY_AT_COUNTER) Icons.Rounded.PointOfSale else Icons.Rounded.CreditCard
                    Surface(
                        onClick = { selectedMethod = method },
                        color = if (selected) NavyPrimary else CrispWhite,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, if (selected) NavyPrimary else SlateBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("kiosk_payment_${method.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = if (selected) CrispWhite else NavyPrimary, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.paymentMethodLabel(method),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) CrispWhite else NavyPrimary
                                )
                                Text(
                                    text = method.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) CrispWhite.copy(alpha = 0.78f) else SlateSubtext
                                )
                            }
                            RadioButton(
                                selected = selected,
                                onClick = { selectedMethod = method },
                                colors = RadioButtonDefaults.colors(selectedColor = CrispWhite, unselectedColor = SlateHint)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPlaceOrder(selectedMethod) },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("kiosk_confirm_order_button")
            ) {
                Text("${strings.kioskPlaceOrderBtn} • ${formatRs(grandTotal)}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelBtn, color = SlateSubtext)
            }
        }
    )
}

@Composable
private fun KioskTokenConfirmationView(
    order: KitchenOrder,
    strings: LocalizedStrings,
    onOpenReceiptModal: () -> Unit,
    onResetForNextGuest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var secondsLeft by remember(order.orderNumber) { mutableIntStateOf(10) }

    LaunchedEffect(order.orderNumber) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        onResetForNextGuest()
    }

    val tokenNumber = order.tokenNumber ?: order.orderNumber

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CrispWhite,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .testTag("kiosk_token_confirmation_card")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(EmeraldSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = strings.kioskOrderSentTitle,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Text(
                    text = strings.kioskOrderSentTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Large KFC-style Order Token Box
                Surface(
                    color = NavyPrimary,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kiosk_token_badge")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.kioskTokenLabel.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldSuccess,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Token #$tokenNumber",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = CrispWhite
                        )
                        Text(
                            text = "Order #${order.orderNumber} • Dine-In / Kiosk",
                            style = MaterialTheme.typography.bodySmall,
                            color = CrispWhite.copy(alpha = 0.78f)
                        )
                    }
                }

                // Order Summary Box
                Surface(
                    color = SlateSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        order.items.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${line.quantity}x ${line.title}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                                Text(formatRs(line.priceRs * line.quantity), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono), color = NavyMuted)
                            }
                        }
                        HorizontalDivider(color = SlateBorder)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.grandTotal, style = MaterialTheme.typography.titleSmall, color = NavyPrimary, fontWeight = FontWeight.Bold)
                            Text(formatRs(order.grandTotalRs), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono), color = NavyPrimary, fontWeight = FontWeight.Bold)
                        }
                        order.paymentMethod?.let { method ->
                            StatusPill(
                                text = "${strings.paymentLabel}: ${strings.paymentMethodLabel(method)}",
                                containerColor = if (order.isPaid) EmeraldSoft else SkySoft,
                                contentColor = if (order.isPaid) EmeraldDark else SkyDark,
                                icon = if (order.isPaid) Icons.Rounded.Verified else Icons.Rounded.PointOfSale
                            )
                        }
                    }
                }

                // Prominent Thanking Note Card
                Surface(
                    color = EmeraldSoft,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.2.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kiosk_thank_you_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CrispWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Celebration,
                                contentDescription = null,
                                tint = EmeraldDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.kioskThankYouHeadline,
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldDark,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.kioskThankYouNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = NavyPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // 10s Auto-Reset Countdown Pill
                Surface(
                    color = SkySoft,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kiosk_countdown_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = SkyDark, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${strings.kioskAutoResetPrefix} ${secondsLeft}s...",
                                style = MaterialTheme.typography.labelLarge,
                                color = SkyDark
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (secondsLeft / 10f).coerceIn(0f, 1f) },
                            color = SkyDark,
                            trackColor = CrispWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }

                // Print / Download 80mm Token Receipt
                OutlinedButton(
                    onClick = onOpenReceiptModal,
                    border = BorderStroke(1.dp, NavyPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("kiosk_print_receipt_button")
                ) {
                    Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.kioskPrintTokenReceipt, color = NavyPrimary, style = MaterialTheme.typography.labelLarge)
                }

                // Immediate Start New Order Button
                Button(
                    onClick = onResetForNextGuest,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("kiosk_new_order_button")
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.kioskStartNewOrderBtn, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
