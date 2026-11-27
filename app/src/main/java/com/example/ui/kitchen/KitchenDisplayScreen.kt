package com.example.ui.kitchen

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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.KitchenOrder
import com.example.model.OrderHistoryDateFilter
import com.example.model.OrderStatus
import com.example.model.RestaurantUiState
import com.example.ui.components.StatusPill
import com.example.ui.components.ThermalReceiptModal
import com.example.ui.components.formatRs
import com.example.ui.components.formatTimer
import com.example.ui.theme.*
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.util.QrAndThermalHelper
import com.example.util.ThermalReceiptData
import com.example.viewmodel.RestaurantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenDisplayScreen(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    val newOrders = uiState.orders.filter { it.status == OrderStatus.NEW }
    val preparingOrders = uiState.orders.filter { it.status == OrderStatus.PREPARING }
    val readyOrders = uiState.orders.filter { it.status == OrderStatus.READY }
    val onTheWayOrders = uiState.orders.filter { it.status == OrderStatus.ON_THE_WAY }
    val allCompletedOrders = uiState.orders.filter {
        it.status == OrderStatus.SERVED || it.status == OrderStatus.CANCELLED
    }

    var selectedTab by remember { mutableStateOf(OrderStatus.NEW) }
    var showDatePicker by remember { mutableStateOf(false) }
    var receiptModalData by remember { mutableStateOf<ThermalReceiptData?>(null) }
    val context = LocalContext.current
    val onPrintKot: (KitchenOrder) -> Unit = { order ->
        val ok = QrAndThermalHelper.printKotSlip(context, order)
        viewModel.emitToast(
            if (ok) "80mm KOT slip sent to printer for Order #${order.orderNumber}"
            else "Printer service unavailable for KOT #${order.orderNumber}"
        )
    }

    val filteredCompleted = remember(
        allCompletedOrders,
        uiState.completedOrderDateFilter,
        uiState.completedOrderCustomDateIso,
        uiState.completedOrderSearchQuery
    ) {
        val today = DateUtils.todayIso()
        val yesterday = DateUtils.daysAgoIso(1)
        val query = uiState.completedOrderSearchQuery.trim().lowercase()
        allCompletedOrders.filter { order ->
            val dateMatch = when (uiState.completedOrderDateFilter) {
                OrderHistoryDateFilter.TODAY -> (order.completedDateIso ?: order.orderDateIso) == today
                OrderHistoryDateFilter.YESTERDAY -> (order.completedDateIso ?: order.orderDateIso) == yesterday
                OrderHistoryDateFilter.CUSTOM_DATE -> (order.completedDateIso ?: order.orderDateIso) == uiState.completedOrderCustomDateIso
                OrderHistoryDateFilter.ALL_DATES -> true
            }
            val searchMatch = query.isEmpty() ||
                order.orderNumber.toString().contains(query) ||
                order.tableCode.lowercase().contains(query) ||
                order.kdsBadgeLabel.lowercase().contains(query) ||
                (order.tokenNumber?.toString()?.contains(query) == true) ||
                order.customerName.lowercase().contains(query) ||
                order.deliveryAddress.lowercase().contains(query) ||
                order.items.any { it.title.lowercase().contains(query) }
            dateMatch && searchMatch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyPrimary)
            .navigationBarsPadding()
    ) {
        // KDS Status Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyElevated)
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
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(EmeraldSuccess)
                )
                Column {
                    Text(
                        text = "Kitchen Display System",
                        style = MaterialTheme.typography.titleMedium,
                        color = CrispWhite
                    )
                    Text(
                        text = "Live station tickets & completed audit log",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateHint
                    )
                }
            }

            Surface(
                color = NavyPrimary,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NavyMuted)
            ) {
                Text(
                    text = "${uiState.activeKdsOrdersCount} ACTIVE",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                    color = EmeraldSuccess,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWide = maxWidth >= 840.dp

            if (isWide) {
                // 4 Columns side-by-side on large tablets
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KdsColumn(
                        title = "New",
                        accent = SkyInfo,
                        orders = newOrders,
                        onAdvanceOrder = viewModel::advanceOrderStatus,
                        onPrintKot = onPrintKot,
                        modifier = Modifier.weight(1f)
                    )
                    KdsColumn(
                        title = "Preparing",
                        accent = AmberWarm,
                        orders = preparingOrders,
                        onAdvanceOrder = viewModel::advanceOrderStatus,
                        onPrintKot = onPrintKot,
                        modifier = Modifier.weight(1f)
                    )
                    KdsColumn(
                        title = "Ready",
                        accent = EmeraldSuccess,
                        orders = readyOrders,
                        onAdvanceOrder = viewModel::advanceOrderStatus,
                        onPrintKot = onPrintKot,
                        modifier = Modifier.weight(1f)
                    )
                    KdsColumn(
                        title = "On the Way",
                        accent = SkyInfo,
                        orders = onTheWayOrders,
                        onAdvanceOrder = viewModel::advanceOrderStatus,
                        onPrintKot = onPrintKot,
                        modifier = Modifier.weight(1f)
                    )
                    KdsCompletedColumn(
                        orders = filteredCompleted,
                        totalCompletedCount = allCompletedOrders.size,
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenDatePicker = { showDatePicker = true },
                        onOpenReceipt = { order -> receiptModalData = ThermalReceiptData.fromOrder(order) },
                        modifier = Modifier.weight(1.15f)
                    )
                }
            } else {
                // Live Tabs (New, Preparing, Ready, On the Way, Completed)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabs = listOf(
                            Triple(OrderStatus.NEW, newOrders.size, SkyInfo),
                            Triple(OrderStatus.PREPARING, preparingOrders.size, AmberWarm),
                            Triple(OrderStatus.READY, readyOrders.size, EmeraldSuccess),
                            Triple(OrderStatus.ON_THE_WAY, onTheWayOrders.size, SkyInfo),
                            Triple(OrderStatus.SERVED, allCompletedOrders.size, SlateHint)
                        )
                        tabs.forEach { (status, count, accent) ->
                            val selected = selectedTab == status
                            Surface(
                                onClick = { selectedTab = status },
                                color = if (selected) accent.copy(alpha = 0.24f) else NavyElevated,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (selected) accent else NavyMuted),
                                modifier = Modifier.testTag("kds_column_tab_${status.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${status.label} ($count)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CrispWhite
                                    )
                                }
                            }
                        }
                    }

                    if (selectedTab == OrderStatus.SERVED) {
                        CompletedFilterHeader(
                            uiState = uiState,
                            onSelectFilter = viewModel::setCompletedOrderDateFilter,
                            onSearchChange = viewModel::setCompletedOrderSearchQuery,
                            onOpenDatePicker = { showDatePicker = true },
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        if (filteredCompleted.isEmpty()) {
                            EmptyKdsState("No completed orders match this date/search")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredCompleted, key = { it.orderNumber }) { order ->
                                    CompletedTicketCard(
                                        order = order,
                                        onOpenReceipt = { receiptModalData = ThermalReceiptData.fromOrder(order) }
                                    )
                                }
                            }
                        }
                    } else {
                        val activeList = when (selectedTab) {
                            OrderStatus.NEW -> newOrders
                            OrderStatus.PREPARING -> preparingOrders
                            OrderStatus.READY -> readyOrders
                            OrderStatus.ON_THE_WAY -> onTheWayOrders
                            OrderStatus.SERVED, OrderStatus.CANCELLED -> emptyList()
                        }
                        val columnAccent = when (selectedTab) {
                            OrderStatus.NEW -> SkyInfo
                            OrderStatus.PREPARING -> AmberWarm
                            OrderStatus.READY -> EmeraldSuccess
                            OrderStatus.ON_THE_WAY -> SkyInfo
                            OrderStatus.SERVED, OrderStatus.CANCELLED -> SlateHint
                        }

                        if (activeList.isEmpty()) {
                            EmptyKdsState("No tickets in ${selectedTab.label}")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(activeList, key = { it.orderNumber }) { order ->
                                    KdsTicketCard(
                                        order = order,
                                        accent = columnAccent,
                                        onAdvance = { viewModel.advanceOrderStatus(order.orderNumber) },
                                        onPrintKot = { onPrintKot(order) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    receiptModalData?.let { receipt ->
        ThermalReceiptModal(
            receipt = receipt,
            strings = AppStrings.forLanguage(uiState.appLanguage),
            onDismiss = { receiptModalData = null },
            onFeedbackMessage = viewModel::emitToast
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val pickedIso = DateUtils.millisToIsoDate(millis)
                            viewModel.setCompletedOrderDateFilter(OrderHistoryDateFilter.CUSTOM_DATE, pickedIso)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Apply", color = NavyPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SlateSubtext)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun EmptyKdsState(title: String) {
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
            Icon(
                imageVector = Icons.Rounded.DoneAll,
                contentDescription = null,
                tint = SlateHint,
                modifier = Modifier.size(42.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
        }
    }
}

@Composable
private fun CompletedFilterHeader(
    uiState: RestaurantUiState,
    onSelectFilter: (OrderHistoryDateFilter, String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OrderHistoryDateFilter.entries.forEach { filter ->
                val selected = uiState.completedOrderDateFilter == filter
                val label = if (filter == OrderHistoryDateFilter.CUSTOM_DATE && selected) {
                    DateUtils.formatFriendlyDate(uiState.completedOrderCustomDateIso)
                } else {
                    filter.label
                }
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (filter == OrderHistoryDateFilter.CUSTOM_DATE) {
                            onOpenDatePicker()
                        } else {
                            onSelectFilter(filter, null)
                        }
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (filter == OrderHistoryDateFilter.CUSTOM_DATE) {
                        { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldSuccess,
                        selectedLabelColor = NavyPrimary,
                        selectedLeadingIconColor = NavyPrimary,
                        containerColor = NavyElevated,
                        labelColor = CrispWhite,
                        iconColor = SlateHint
                    ),
                    border = BorderStroke(1.dp, NavyMuted),
                    modifier = Modifier.testTag("kds_completed_filter_${filter.name.lowercase()}")
                )
            }
        }

        OutlinedTextField(
            value = uiState.completedOrderSearchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search order #, Token #16, or dish...", color = SlateHint) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SlateHint) },
            trailingIcon = {
                if (uiState.completedOrderSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = SlateHint)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
                focusedContainerColor = NavyElevated,
                unfocusedContainerColor = NavyElevated,
                focusedBorderColor = EmeraldSuccess,
                unfocusedBorderColor = NavyMuted
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kds_completed_search_input")
        )
    }
}

@Composable
private fun KdsCompletedColumn(
    orders: List<KitchenOrder>,
    totalCompletedCount: Int,
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    onOpenDatePicker: () -> Unit,
    onOpenReceipt: (KitchenOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NavyElevated.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NavyMuted),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(SlateHint))
                    Text("Completed", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                }
                Surface(color = SlateHint.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                    Text(
                        text = "$totalCompletedCount",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = CrispWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            HorizontalDivider(color = NavyMuted)
            CompletedFilterHeader(
                uiState = uiState,
                onSelectFilter = viewModel::setCompletedOrderDateFilter,
                onSearchChange = viewModel::setCompletedOrderSearchQuery,
                onOpenDatePicker = onOpenDatePicker,
                modifier = Modifier.padding(10.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(orders, key = { it.orderNumber }) { order ->
                    CompletedTicketCard(
                        order = order,
                        onOpenReceipt = { onOpenReceipt(order) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTicketCard(
    order: KitchenOrder,
    onOpenReceipt: () -> Unit
) {
    Surface(
        color = NavyElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NavyMuted),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kds_completed_ticket_${order.orderNumber}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "#${order.orderNumber}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                        color = CrispWhite
                    )
                    Surface(
                        color = if (order.isDelivery) AmberWarm else NavyPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = order.kdsBadgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = if (order.isDelivery) NavyPrimary else EmeraldSuccess,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                StatusPill(
                    text = if (order.status == OrderStatus.CANCELLED) {
                        "Cancelled • ${order.completedTimeLabel ?: "Void"}"
                    } else {
                        "Served • ${order.completedTimeLabel ?: "Done"}"
                    },
                    containerColor = if (order.status == OrderStatus.CANCELLED) {
                        RoseAlert.copy(alpha = 0.2f)
                    } else {
                        EmeraldSuccess.copy(alpha = 0.2f)
                    },
                    contentColor = if (order.status == OrderStatus.CANCELLED) RoseAlert else EmeraldSuccess,
                    icon = if (order.status == OrderStatus.CANCELLED) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle
                )
            }

            if (order.isDelivery) {
                Text(
                    text = "${order.customerName} (${order.customerPhone}) • ${order.deliveryAddress}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarm
                )
            }

            Text(
                text = "Date: ${DateUtils.formatFriendlyDate(order.completedDateIso ?: order.orderDateIso)}",
                style = MaterialTheme.typography.labelSmall,
                color = SlateHint
            )

            HorizontalDivider(color = NavyMuted)

            order.items.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${line.quantity}x ${line.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrispWhite
                    )
                    Text(
                        text = formatRs(line.priceRs * line.quantity),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = SlateHint
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenReceipt,
                border = BorderStroke(1.dp, NavyMuted),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("kds_completed_receipt_${order.orderNumber}")
            ) {
                Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = CrispWhite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Print / Download Receipt", color = CrispWhite, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun KdsColumn(
    title: String,
    accent: Color,
    orders: List<KitchenOrder>,
    onAdvanceOrder: (Int) -> Unit,
    onPrintKot: (KitchenOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NavyElevated.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NavyMuted),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CrispWhite
                    )
                }
                Surface(
                    color = accent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = orders.size.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            HorizontalDivider(color = NavyMuted)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.orderNumber }) { order ->
                    KdsTicketCard(
                        order = order,
                        accent = accent,
                        onAdvance = { onAdvanceOrder(order.orderNumber) },
                        onPrintKot = { onPrintKot(order) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KdsTicketCard(
    order: KitchenOrder,
    accent: Color,
    onAdvance: () -> Unit,
    onPrintKot: () -> Unit
) {
    val isOverdue = order.elapsedSeconds >= 600
    val timerColor = when {
        isOverdue -> RoseAlert
        order.elapsedSeconds >= 300 -> AmberWarm
        else -> EmeraldSuccess
    }

    val isAwaitingRider = order.isDelivery && order.status == OrderStatus.READY
    val isOutWithRider = order.isDelivery && order.status == OrderStatus.ON_THE_WAY
    val buttonLabel = when (order.status) {
        OrderStatus.NEW -> "Start Preparing"
        OrderStatus.PREPARING -> "Mark Ready"
        OrderStatus.READY -> if (order.isDelivery) "Awaiting Rider Pickup" else "Complete / Served"
        OrderStatus.ON_THE_WAY -> if (order.assignedRiderName != null) {
            "With ${order.assignedRiderName} (On the Way)"
        } else {
            "Rider Out for Delivery"
        }
        OrderStatus.SERVED -> "Served"
        OrderStatus.CANCELLED -> "Cancelled"
    }

    Surface(
        color = NavyElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kds_ticket_${order.orderNumber}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                    Text(
                        text = "#${order.orderNumber}",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                        color = CrispWhite
                    )
                    Surface(
                        color = if (order.isDelivery) AmberWarm else accent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (order.isDelivery) Icons.Rounded.DeliveryDining else Icons.Rounded.TouchApp,
                                contentDescription = if (order.isDelivery) "Delivery Order" else "Kiosk Order",
                                tint = NavyPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = order.kdsBadgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    color = timerColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, timerColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Elapsed time",
                            tint = timerColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formatTimer(order.elapsedSeconds),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = timerColor
                        )
                    }
                }
            }

            if (order.isDelivery) {
                Surface(
                    color = NavyPrimary.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberWarm.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${order.customerName} • ${order.customerPhone}",
                                style = MaterialTheme.typography.labelLarge,
                                color = CrispWhite
                            )
                            Text(
                                text = order.paymentMethod?.label ?: "COD",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                        Text(
                            text = "Address: ${order.deliveryAddress}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateBorder
                        )
                        order.gpsCoordinatesLabel?.let { coords ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MyLocation,
                                    contentDescription = "GPS Pin",
                                    tint = SkyInfo,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "GPS Pin: $coords${if (order.isGpsFallback) " (Preview Pin)" else ""}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = SkyInfo
                                )
                            }
                        }
                    }
                }
            } else if (order.isKiosk) {
                Surface(
                    color = NavyPrimary.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Self-Ordering Kiosk • ${order.tableCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CrispWhite
                        )
                        Text(
                            text = if (order.isPaid) "Paid Online" else (order.paymentMethod?.label ?: "Cash at Counter"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (order.isPaid) EmeraldSuccess else AmberWarm
                        )
                    }
                }
            }

            HorizontalDivider(color = NavyMuted)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                order.items.forEach { line ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = NavyPrimary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${line.quantity}x",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = line.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = CrispWhite
                            )
                        }
                        if (line.note.isNotBlank()) {
                            Text(
                                text = "Note: ${line.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberWarm,
                                modifier = Modifier.padding(start = 38.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (order.status == OrderStatus.NEW || order.status == OrderStatus.PREPARING) {
                    OutlinedIconButton(
                        onClick = onPrintKot,
                        border = BorderStroke(1.dp, NavyMuted),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("kds_print_kot_${order.orderNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Print,
                            contentDescription = "Print KOT",
                            tint = CrispWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (isAwaitingRider || isOutWithRider) {
                    Surface(
                        color = NavyPrimary,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("kds_rider_status_${order.orderNumber}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeliveryDining,
                                contentDescription = buttonLabel,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = buttonLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onAdvance,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = NavyPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("kds_advance_${order.orderNumber}")
                    ) {
                        Text(
                            text = buttonLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
