package com.example.ui.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.EmployeeUnifiedTopBar
import com.example.ui.components.StatusPill
import com.example.ui.components.ThermalReceiptModal
import com.example.ui.components.formatRs
import com.example.ui.theme.*
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.util.ThermalReceiptData
import com.example.viewmodel.RestaurantViewModel

@Composable
fun RiderPortalScreen(
    employee: StaffMember,
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    onOpenChangePassword: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = DateUtils.todayIso()
    val readyOrders = uiState.readyForPickupDeliveryOrders
    val outForDeliveryOrders = uiState.outForDeliveryOrders
    val completedOrders = uiState.completedDeliveryOrdersForRider
    var receiptData by remember { mutableStateOf<ThermalReceiptData?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .navigationBarsPadding()
    ) {
        EmployeeUnifiedTopBar(
            employee = employee,
            todayRecord = uiState.attendanceRecordFor(employee.id, today),
            onMarkPresentToday = viewModel::markAuthenticatedEmployeePresent,
            onOpenChangePassword = onOpenChangePassword,
            onLogout = onLogout
        )

        // Live Notification Banner when Chef marks a delivery order Ready
        uiState.riderPickupBannerMessage?.let { banner ->
            Surface(
                color = EmeraldSoft,
                border = BorderStroke(1.dp, EmeraldSuccess),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("rider_live_ready_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = "New pickup alert",
                        tint = EmeraldDark
                    )
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.labelLarge,
                        color = NavyPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            viewModel.selectRiderTab(RiderTab.READY_FOR_PICKUP)
                            viewModel.dismissRiderPickupBanner()
                        }
                    ) {
                        Text("View", color = EmeraldDark, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = viewModel::dismissRiderPickupBanner,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss alert", tint = EmeraldDark, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Rider Tabs: Ready for Pickup | Out for Delivery | Completed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CrispWhite)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RiderTab.entries.forEach { tab ->
                val count = when (tab) {
                    RiderTab.READY_FOR_PICKUP -> readyOrders.size
                    RiderTab.OUT_FOR_DELIVERY -> outForDeliveryOrders.size
                    RiderTab.COMPLETED -> completedOrders.size
                }
                val selected = uiState.riderTab == tab
                Surface(
                    onClick = { viewModel.selectRiderTab(tab) },
                    color = if (selected) NavyPrimary else SlateSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("rider_tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${tab.label} ($count)",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CrispWhite else NavyPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = SlateBorder)

        val currentOrders = when (uiState.riderTab) {
            RiderTab.READY_FOR_PICKUP -> readyOrders
            RiderTab.OUT_FOR_DELIVERY -> outForDeliveryOrders
            RiderTab.COMPLETED -> completedOrders
        }

        if (currentOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeliveryDining,
                        contentDescription = null,
                        tint = SlateSubtext,
                        modifier = Modifier.size(46.dp)
                    )
                    Text(
                        text = when (uiState.riderTab) {
                            RiderTab.READY_FOR_PICKUP -> "No delivery orders ready for pickup yet"
                            RiderTab.OUT_FOR_DELIVERY -> "No orders currently out for delivery"
                            RiderTab.COMPLETED -> "No completed deliveries yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Kitchen notifications appear here automatically when a delivery order is marked Ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSubtext
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentOrders, key = { it.orderNumber }) { order ->
                    RiderOrderCard(
                        order = order,
                        tab = uiState.riderTab,
                        onCallCustomer = { phone ->
                            val clean = phone.trim()
                            if (clean.isNotEmpty()) {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
                                runCatching { context.startActivity(dialIntent) }
                                    .onFailure { viewModel.emitToast("Dialer unavailable for $clean") }
                            }
                        },
                        onOpenMapPin = { mapUrl, address ->
                            val targetUri = when {
                                !mapUrl.isNullOrBlank() -> Uri.parse(mapUrl)
                                else -> Uri.parse("geo:0,0?q=${Uri.encode(address)}")
                            }
                            val mapIntent = Intent(Intent.ACTION_VIEW, targetUri)
                            runCatching { context.startActivity(mapIntent) }
                                .onFailure { viewModel.emitToast("Map preview: $address") }
                        },
                        onPickUpDispatch = { viewModel.riderPickUpOrder(order.orderNumber) },
                        onMarkDelivered = { viewModel.riderMarkDelivered(order.orderNumber) },
                        onOpenReceipt = { receiptData = ThermalReceiptData.fromOrder(order) }
                    )
                }
            }
        }
    }

    receiptData?.let { receipt ->
        ThermalReceiptModal(
            receipt = receipt,
            strings = AppStrings.forLanguage(uiState.appLanguage),
            onDismiss = { receiptData = null },
            onFeedbackMessage = viewModel::emitToast
        )
    }
}

@Composable
private fun RiderOrderCard(
    order: KitchenOrder,
    tab: RiderTab,
    onCallCustomer: (String) -> Unit,
    onOpenMapPin: (String?, String) -> Unit,
    onPickUpDispatch: () -> Unit,
    onMarkDelivered: () -> Unit,
    onOpenReceipt: () -> Unit
) {
    val isCod = order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY || !order.isPaid
    Surface(
        color = CrispWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rider_order_card_${order.orderNumber}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AmberSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeliveryDining,
                            contentDescription = "Delivery Order",
                            tint = AmberDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Order #${order.orderNumber}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = order.customerName.ifBlank { "Customer" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NavyPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                StatusPill(
                    text = if (isCod && tab != RiderTab.COMPLETED) {
                        "COD • ${formatRs(order.grandTotalRs)}"
                    } else {
                        "Paid • ${formatRs(order.grandTotalRs)}"
                    },
                    containerColor = if (isCod && tab != RiderTab.COMPLETED) AmberSoft else EmeraldSoft,
                    contentColor = if (isCod && tab != RiderTab.COMPLETED) AmberDark else EmeraldDark
                )
            }

            // Address & Contact Block
            Surface(
                color = SlateSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            text = order.deliveryAddress.ifBlank { "Address pending" },
                            style = MaterialTheme.typography.bodySmall,
                            color = NavyPrimary
                        )
                    }
                    order.gpsCoordinatesLabel?.let { coords ->
                        Text(
                            text = "GPS Pin: $coords",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = SkyDark
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (order.customerPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onCallCustomer(order.customerPhone) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SlateBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("rider_call_${order.orderNumber}")
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = "Call customer", tint = EmeraldDark, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(order.customerPhone, style = MaterialTheme.typography.labelSmall, color = NavyPrimary)
                            }
                        }
                        OutlinedButton(
                            onClick = { onOpenMapPin(order.gpsMapUrl, order.deliveryAddress) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("rider_map_${order.orderNumber}")
                        ) {
                            Icon(Icons.Rounded.Map, contentDescription = "Open GPS Map", tint = SkyDark, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Open GPS Pin", style = MaterialTheme.typography.labelSmall, color = NavyPrimary)
                        }
                    }
                }
            }

            // Items List
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.items.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${line.quantity}x ${line.title}", style = MaterialTheme.typography.bodySmall, color = NavyMuted)
                        Text(formatRs(line.priceRs * line.quantity), style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono), color = SlateSubtext)
                    }
                }
            }

            when (tab) {
                RiderTab.READY_FOR_PICKUP -> {
                    Button(
                        onClick = onPickUpDispatch,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("rider_pickup_${order.orderNumber}")
                    ) {
                        Icon(Icons.Rounded.TwoWheeler, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Up & Dispatch (Out for Delivery)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
                RiderTab.OUT_FOR_DELIVERY -> {
                    Button(
                        onClick = onMarkDelivered,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = NavyPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("rider_deliver_${order.orderNumber}")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark as Delivered", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
                RiderTab.COMPLETED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Delivered • ${order.completedTimeLabel ?: "Done"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = onOpenReceipt,
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("rider_receipt_${order.orderNumber}")
                        ) {
                            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Receipt", style = MaterialTheme.typography.labelSmall, color = NavyPrimary)
                        }
                    }
                }
            }
        }
    }
}
