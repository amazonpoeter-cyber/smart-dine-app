package com.example.ui.owner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRs
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.RestaurantViewModel

@Composable
fun OwnerOverviewSection(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel
) {
    val activeStaffTotal = uiState.staff.count { !it.isRelieved }
    val todayIso = DateUtils.todayIso()
    val todayRec = uiState.salesRecords.firstOrNull { it.dateIso == todayIso }
    val totalTodayOrders = todayRec?.orderCount ?: uiState.orders.count { it.orderDateIso == todayIso }
    val deliveryCount = todayRec?.deliveryOrderCount ?: uiState.orders.count { it.isDelivery && it.orderDateIso == todayIso }
    val kioskCount = todayRec?.kioskOrderCount ?: uiState.orders.count { it.isKiosk && it.orderDateIso == todayIso }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Restaurant Overview", style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
            Text("Unified Home Delivery & Kiosk operations pulse", style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
        }

        // 4 KPI Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        label = "Total Orders",
                        value = "$totalTodayOrders",
                        icon = Icons.Rounded.ReceiptLong,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        label = "Channel Split",
                        value = "${deliveryCount}D • ${kioskCount}K",
                        icon = Icons.Rounded.Storefront,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        label = "Staff On-Duty",
                        value = "${uiState.activeStaffOnDutyCount}/$activeStaffTotal",
                        icon = Icons.Rounded.Badge,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        label = "Menu Items",
                        value = "${uiState.menuItems.size}",
                        icon = Icons.Rounded.RestaurantMenu,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Multi-Channel Live Pulse Card
        item {
            ChannelRevenueSplitCard(
                title = "Today's Channel Breakdown",
                deliveryRevenueRs = todayRec?.deliveryRevenueRs ?: 0,
                kioskRevenueRs = todayRec?.kioskRevenueRs ?: 0,
                deliveryOrderCount = deliveryCount,
                kioskOrderCount = kioskCount
            )
        }

        // Today's Sales Bar Chart Card
        item {
            SalesBarChartCard(
                title = "Daily Sales (Today)",
                revenueRs = uiState.todayGrossRevenueRs,
                badgeLabel = "+${uiState.revenueGrowthPct}%",
                bars = uiState.todayHourlyBars
            )
        }

        // Simulated WhatsApp Notification Alerts
        item {
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
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
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Chat,
                                    contentDescription = "WhatsApp Alerts",
                                    tint = WhatsAppGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column {
                                Text("WhatsApp Alerts", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Text("Automated owner notifications", style = MaterialTheme.typography.labelSmall, color = SlateSubtext)
                            }
                        }
                        StatusPill(text = "Connected", containerColor = WhatsAppSoft, contentColor = WhatsAppGreen)
                    }

                    uiState.alerts.take(4).forEach { alert ->
                        val (bg, badgeColor) = when (alert.type) {
                            AlertType.LOW_STOCK -> AmberSoft to AmberDark
                            AlertType.PAYMENT -> EmeraldSoft to EmeraldDark
                            AlertType.NEW_ORDER -> WhatsAppSoft to WhatsAppGreen
                            AlertType.DELIVERY_ORDER -> SkySoft to SkyDark
                            AlertType.KIOSK_ORDER -> EmeraldSoft to EmeraldDark
                        }
                        Surface(
                            onClick = { viewModel.acknowledgeAlert(alert.id) },
                            color = if (alert.isAcknowledged) SlateSurfaceVariant else bg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = alert.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (alert.isAcknowledged) SlateSubtext else badgeColor
                                    )
                                    Text(
                                        text = alert.timeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateSubtext
                                    )
                                }
                                Text(
                                    text = alert.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NavyMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Auto-Generated Daily Sales Report Card
        item {
            val onlinePct = if (todayRec != null && todayRec.grossRevenueRs > 0) {
                (todayRec.onlineRevenueRs * 100) / todayRec.grossRevenueRs
            } else 64
            SalesSummaryReportCard(
                reportTitle = "Auto-Generated Daily Sales Report",
                periodSubtitle = "Today • ${DateUtils.formatFriendlyDate(todayIso)}",
                grossRevenueRs = uiState.todayGrossRevenueRs,
                deliveryRevenueRs = todayRec?.deliveryRevenueRs ?: 0,
                kioskRevenueRs = todayRec?.kioskRevenueRs ?: 0,
                deliveryOrderCount = deliveryCount,
                kioskOrderCount = kioskCount,
                topSeller = todayRec?.topSellerLabel ?: "Smash Burger (48 sold)",
                onlineSplitPct = onlinePct,
                onExportClick = { viewModel.emitToast("Daily Sales Report exported for accounting") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDailySalesSection(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val todayIso = DateUtils.todayIso()
    val yesterdayIso = DateUtils.daysAgoIso(1)
    val currentMonthIso = DateUtils.currentMonthIso()

    // Compute filtered metrics dynamically across Delivery & Kiosk channels
    val filteredSummary = remember(
        uiState.salesRecords,
        uiState.salesPeriodFilter,
        uiState.salesCustomDateIso,
        uiState.salesCustomMonthIso
    ) {
        when (uiState.salesPeriodFilter) {
            SalesPeriodFilter.TODAY -> {
                val rec = uiState.salesRecords.firstOrNull { it.dateIso == todayIso }
                FilteredSalesView(
                    periodLabel = "Today • ${DateUtils.formatFriendlyDate(todayIso)}",
                    grossRevenueRs = rec?.grossRevenueRs ?: 0,
                    onlineRevenueRs = rec?.onlineRevenueRs ?: 0,
                    cashRevenueRs = rec?.cashRevenueRs ?: 0,
                    deliveryRevenueRs = rec?.deliveryRevenueRs ?: 0,
                    kioskRevenueRs = rec?.kioskRevenueRs ?: 0,
                    deliveryOrderCount = rec?.deliveryOrderCount ?: 0,
                    kioskOrderCount = rec?.kioskOrderCount ?: 0,
                    topSeller = rec?.topSellerLabel ?: "Smash Burger (48 sold)",
                    orderCount = rec?.orderCount ?: 0,
                    badgeLabel = "+${uiState.revenueGrowthPct}%",
                    bars = rec?.hourlyBars ?: emptyList()
                )
            }
            SalesPeriodFilter.YESTERDAY -> {
                val rec = uiState.salesRecords.firstOrNull { it.dateIso == yesterdayIso }
                FilteredSalesView(
                    periodLabel = "Yesterday • ${DateUtils.formatFriendlyDate(yesterdayIso)}",
                    grossRevenueRs = rec?.grossRevenueRs ?: 0,
                    onlineRevenueRs = rec?.onlineRevenueRs ?: 0,
                    cashRevenueRs = rec?.cashRevenueRs ?: 0,
                    deliveryRevenueRs = rec?.deliveryRevenueRs ?: 0,
                    kioskRevenueRs = rec?.kioskRevenueRs ?: 0,
                    deliveryOrderCount = rec?.deliveryOrderCount ?: 0,
                    kioskOrderCount = rec?.kioskOrderCount ?: 0,
                    topSeller = rec?.topSellerLabel ?: "No sales logged",
                    orderCount = rec?.orderCount ?: 0,
                    badgeLabel = "Closed",
                    bars = rec?.hourlyBars ?: emptyList()
                )
            }
            SalesPeriodFilter.THIS_MONTH -> {
                val monthRecords = uiState.salesRecords
                    .filter { it.dateIso.startsWith(uiState.salesCustomMonthIso) }
                    .sortedBy { it.dateIso }
                val totalGross = monthRecords.sumOf { it.grossRevenueRs }
                val totalOnline = monthRecords.sumOf { it.onlineRevenueRs }
                val totalCash = monthRecords.sumOf { it.cashRevenueRs }
                val totalDelivery = monthRecords.sumOf { it.deliveryRevenueRs }
                val totalKiosk = monthRecords.sumOf { it.kioskRevenueRs }
                val totalDelOrders = monthRecords.sumOf { it.deliveryOrderCount }
                val totalKioskOrders = monthRecords.sumOf { it.kioskOrderCount }
                val totalOrders = monthRecords.sumOf { it.orderCount }
                val monthlyBars = monthRecords.takeLast(7).map { rec ->
                    SalesBarPoint(label = rec.dateIso.takeLast(2), amountRs = rec.grossRevenueRs)
                }
                FilteredSalesView(
                    periodLabel = "${DateUtils.formatFriendlyMonth(uiState.salesCustomMonthIso)} (${monthRecords.size} days)",
                    grossRevenueRs = totalGross,
                    onlineRevenueRs = totalOnline,
                    cashRevenueRs = totalCash,
                    deliveryRevenueRs = totalDelivery,
                    kioskRevenueRs = totalKiosk,
                    deliveryOrderCount = totalDelOrders,
                    kioskOrderCount = totalKioskOrders,
                    topSeller = "Smash Burger & Chicken Biryani",
                    orderCount = totalOrders,
                    badgeLabel = "${monthRecords.size}d",
                    bars = monthlyBars
                )
            }
            SalesPeriodFilter.CUSTOM_DATE -> {
                val rec = uiState.salesRecords.firstOrNull { it.dateIso == uiState.salesCustomDateIso }
                FilteredSalesView(
                    periodLabel = DateUtils.formatFriendlyDate(uiState.salesCustomDateIso),
                    grossRevenueRs = rec?.grossRevenueRs ?: 0,
                    onlineRevenueRs = rec?.onlineRevenueRs ?: 0,
                    cashRevenueRs = rec?.cashRevenueRs ?: 0,
                    deliveryRevenueRs = rec?.deliveryRevenueRs ?: 0,
                    kioskRevenueRs = rec?.kioskRevenueRs ?: 0,
                    deliveryOrderCount = rec?.deliveryOrderCount ?: 0,
                    kioskOrderCount = rec?.kioskOrderCount ?: 0,
                    topSeller = rec?.topSellerLabel ?: "No orders recorded on this date",
                    orderCount = rec?.orderCount ?: 0,
                    badgeLabel = if (rec != null) "${rec.orderCount} orders" else "0 orders",
                    bars = rec?.hourlyBars ?: emptyList()
                )
            }
        }
    }

    val onlinePct = if (filteredSummary.grossRevenueRs > 0) {
        (filteredSummary.onlineRevenueRs * 100) / filteredSummary.grossRevenueRs
    } else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Sales Analytics", style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
            Text("Compiled Home Delivery & At-Restaurant Kiosk revenue", style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
        }

        // Date & Period Filter Bar
        item {
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Date & Period Filter", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SalesPeriodFilter.entries.forEach { period ->
                            val selected = uiState.salesPeriodFilter == period
                            val label = if (period == SalesPeriodFilter.CUSTOM_DATE && selected) {
                                DateUtils.formatFriendlyDate(uiState.salesCustomDateIso)
                            } else {
                                period.label
                            }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (period == SalesPeriodFilter.CUSTOM_DATE) {
                                        showDatePicker = true
                                    } else {
                                        viewModel.setSalesPeriodFilter(
                                            period = period,
                                            customMonthIso = currentMonthIso
                                        )
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (period == SalesPeriodFilter.CUSTOM_DATE) {
                                    { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = CrispWhite,
                                    selectedLeadingIconColor = CrispWhite,
                                    containerColor = SlateSurfaceVariant,
                                    labelColor = NavyMuted
                                ),
                                border = null,
                                modifier = Modifier.testTag("sales_filter_${period.name.lowercase()}")
                            )
                        }
                    }

                    // Quick Month/Recent-Day Selector Pills for rapid inspection
                    if (uiState.salesPeriodFilter == SalesPeriodFilter.CUSTOM_DATE || uiState.salesPeriodFilter == SalesPeriodFilter.THIS_MONTH) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.salesRecords.take(7).forEach { rec ->
                                val isChosenDate = uiState.salesPeriodFilter == SalesPeriodFilter.CUSTOM_DATE &&
                                    uiState.salesCustomDateIso == rec.dateIso
                                AssistChip(
                                    onClick = {
                                        viewModel.setSalesPeriodFilter(
                                            SalesPeriodFilter.CUSTOM_DATE,
                                            customDateIso = rec.dateIso
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = DateUtils.formatFriendlyDate(rec.dateIso).substringBefore(","),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isChosenDate) NavyPrimary else SlateSurfaceVariant,
                                        labelColor = if (isChosenDate) CrispWhite else NavyMuted
                                    ),
                                    border = null
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dynamic Multi-Channel Revenue Breakdown Card
        item {
            ChannelRevenueSplitCard(
                title = "Channel Revenue Split • ${filteredSummary.periodLabel}",
                deliveryRevenueRs = filteredSummary.deliveryRevenueRs,
                kioskRevenueRs = filteredSummary.kioskRevenueRs,
                deliveryOrderCount = filteredSummary.deliveryOrderCount,
                kioskOrderCount = filteredSummary.kioskOrderCount
            )
        }

        // Dynamic Bar Chart Card
        item {
            SalesBarChartCard(
                title = filteredSummary.periodLabel,
                revenueRs = filteredSummary.grossRevenueRs,
                badgeLabel = filteredSummary.badgeLabel,
                bars = filteredSummary.bars
            )
        }

        // Dynamic Summary Report Card
        item {
            SalesSummaryReportCard(
                reportTitle = if (uiState.salesPeriodFilter == SalesPeriodFilter.THIS_MONTH) {
                    "Monthly Sales Report"
                } else {
                    "Daily Sales Report"
                },
                periodSubtitle = "${filteredSummary.periodLabel} • ${filteredSummary.orderCount} orders",
                grossRevenueRs = filteredSummary.grossRevenueRs,
                deliveryRevenueRs = filteredSummary.deliveryRevenueRs,
                kioskRevenueRs = filteredSummary.kioskRevenueRs,
                deliveryOrderCount = filteredSummary.deliveryOrderCount,
                kioskOrderCount = filteredSummary.kioskOrderCount,
                topSeller = filteredSummary.topSeller,
                onlineSplitPct = onlinePct,
                onExportClick = {
                    viewModel.emitToast("Exported ${filteredSummary.periodLabel} summary (${formatRs(filteredSummary.grossRevenueRs)})")
                }
            )
        }
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
                            viewModel.setSalesPeriodFilter(
                                SalesPeriodFilter.CUSTOM_DATE,
                                customDateIso = pickedIso
                            )
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

private data class FilteredSalesView(
    val periodLabel: String,
    val grossRevenueRs: Int,
    val onlineRevenueRs: Int,
    val cashRevenueRs: Int,
    val deliveryRevenueRs: Int,
    val kioskRevenueRs: Int,
    val deliveryOrderCount: Int,
    val kioskOrderCount: Int,
    val topSeller: String,
    val orderCount: Int,
    val badgeLabel: String,
    val bars: List<SalesBarPoint>
)

@Composable
private fun ChannelRevenueSplitCard(
    title: String,
    deliveryRevenueRs: Int,
    kioskRevenueRs: Int,
    deliveryOrderCount: Int,
    kioskOrderCount: Int
) {
    val total = (deliveryRevenueRs + kioskRevenueRs).coerceAtLeast(1)
    val deliveryPct = if (deliveryRevenueRs + kioskRevenueRs > 0) (deliveryRevenueRs * 100) / total else 0
    val kioskPct = if (deliveryRevenueRs + kioskRevenueRs > 0) (100 - deliveryPct).coerceIn(0, 100) else 0
    val deliveryWeight = (deliveryRevenueRs.toFloat() / total.toFloat()).coerceIn(0.05f, 0.95f)

    Surface(
        color = CrispWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_channel_breakdown_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)

            if (deliveryRevenueRs + kioskRevenueRs > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(deliveryWeight)
                            .fillMaxHeight()
                            .background(SkyInfo)
                    )
                    Box(
                        modifier = Modifier
                            .weight((1f - deliveryWeight).coerceAtLeast(0.05f))
                            .fillMaxHeight()
                            .background(EmeraldSuccess)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = SkySoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.DeliveryDining, contentDescription = null, tint = SkyDark, modifier = Modifier.size(16.dp))
                            Text("Home Delivery", style = MaterialTheme.typography.labelMedium, color = SkyDark)
                        }
                        Text(
                            text = formatRs(deliveryRevenueRs),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary
                        )
                        Text(
                            text = "$deliveryOrderCount orders • $deliveryPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateSubtext
                        )
                    }
                }

                Surface(
                    color = EmeraldSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(16.dp))
                            Text("At Restaurant / Kiosk", style = MaterialTheme.typography.labelMedium, color = EmeraldDark)
                        }
                        Text(
                            text = formatRs(kioskRevenueRs),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary
                        )
                        Text(
                            text = "$kioskOrderCount orders • $kioskPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateSubtext
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CrispWhite,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = modifier
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
                Text(label, style = MaterialTheme.typography.labelMedium, color = SlateSubtext)
                Icon(icon, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMono),
                color = NavyPrimary
            )
        }
    }
}

@Composable
fun SalesBarChartCard(
    title: String,
    revenueRs: Int,
    badgeLabel: String,
    bars: List<SalesBarPoint>
) {
    Surface(
        color = CrispWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = SlateSubtext)
                    Text(
                        text = formatRs(revenueRs),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMono),
                        color = NavyPrimary
                    )
                }
                StatusPill(
                    text = badgeLabel,
                    containerColor = EmeraldSoft,
                    contentColor = EmeraldDark,
                    icon = Icons.Rounded.TrendingUp
                )
            }

            if (bars.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sales bars for this date", style = MaterialTheme.typography.bodySmall, color = SlateHint)
                }
            } else {
                val maxVal = (bars.maxOfOrNull { it.amountRs } ?: 1).coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val count = bars.size.coerceAtLeast(1)
                    val slotW = size.width / count
                    val barW = slotW * 0.56f
                    bars.forEachIndexed { idx, point ->
                        val ratio = point.amountRs.toFloat() / maxVal.toFloat()
                        val barH = (size.height - 8.dp.toPx()) * ratio
                        val x = idx * slotW + (slotW - barW) / 2f
                        val y = size.height - barH
                        val isPeak = point.amountRs == maxVal
                        drawRoundRect(
                            color = if (isPeak) NavyPrimary else NavyPrimary.copy(alpha = 0.28f),
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    bars.forEach { pt ->
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
                            color = SlateSubtext
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesSummaryReportCard(
    reportTitle: String,
    periodSubtitle: String,
    grossRevenueRs: Int,
    deliveryRevenueRs: Int,
    kioskRevenueRs: Int,
    deliveryOrderCount: Int,
    kioskOrderCount: Int,
    topSeller: String,
    onlineSplitPct: Int,
    onExportClick: () -> Unit
) {
    val cashSplitPct = (100 - onlineSplitPct).coerceIn(0, 100)
    Surface(
        color = NavyPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(reportTitle, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                    Text(periodSubtitle, style = MaterialTheme.typography.labelSmall, color = CrispWhite.copy(alpha = 0.72f))
                }
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = EmeraldSuccess)
            }

            HorizontalDivider(color = NavyMuted)

            ReportRow("Gross Revenue", formatRs(grossRevenueRs))
            ReportRow("Home Delivery", "${formatRs(deliveryRevenueRs)} ($deliveryOrderCount orders)")
            ReportRow("At Restaurant / Kiosk", "${formatRs(kioskRevenueRs)} ($kioskOrderCount orders)")
            ReportRow("5% Tax Collected", formatRs((grossRevenueRs * 0.05).toInt()))
            ReportRow("Top Seller", topSeller)
            ReportRow(
                "Online / Cash Split",
                if (grossRevenueRs > 0) "$onlineSplitPct% Online • $cashSplitPct% Cash" else "0% Online • 0% Cash"
            )

            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(containerColor = CrispWhite, contentColor = NavyPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("download_sales_summary_button")
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download Summary", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CrispWhite.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono), color = CrispWhite)
    }
}
