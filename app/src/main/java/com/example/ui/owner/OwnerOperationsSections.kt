package com.example.ui.owner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.util.DateUtils
import com.example.util.ThermalReceiptData
import com.example.viewmodel.RestaurantViewModel

@Composable
fun OwnerMenuManagementSection(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<MenuItem?>(null) }
    var filterCategory by remember { mutableStateOf<MenuCategory?>(null) }

    val filteredItems = remember(uiState.menuItems, filterCategory) {
        if (filterCategory == null) uiState.menuItems
        else uiState.menuItems.filter { it.category == filterCategory }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Menu Management", style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
                    Text(
                        "Add, edit, delete & toggle stock — saves locally & syncs live",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSubtext
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("admin_add_menu_item_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Item", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterCategory == null,
                    onClick = { filterCategory = null },
                    label = { Text("All (${uiState.menuItems.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NavyPrimary,
                        selectedLabelColor = CrispWhite,
                        containerColor = CrispWhite,
                        labelColor = NavyMuted
                    )
                )
                MenuCategory.entries.forEach { cat ->
                    val count = uiState.menuItems.count { it.category == cat }
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { filterCategory = cat },
                        label = { Text("${cat.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = CrispWhite,
                            containerColor = CrispWhite,
                            labelColor = NavyMuted
                        )
                    )
                }
            }
        }

        items(filteredItems, key = { it.id }) { item ->
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_menu_card_${item.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
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
                                    Text(
                                        text = "SOLD OUT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = CrispWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NavyPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatRs(item.priceRs),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                    color = NavyPrimary
                                )
                            }

                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateSubtext,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(text = item.category.label, containerColor = SlateSurfaceVariant, contentColor = NavyMuted)
                                StatusPill(text = item.prepTimeRange, containerColor = SlateSurfaceVariant, contentColor = SlateSubtext)
                                StatusPill(text = item.spiceLevel.label, containerColor = AmberSoft, contentColor = AmberDark)
                            }
                        }
                    }

                    HorizontalDivider(color = SlateBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = item.isAvailable,
                                onCheckedChange = { viewModel.toggleItemAvailability(item.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CrispWhite,
                                    checkedTrackColor = EmeraldSuccess,
                                    uncheckedThumbColor = CrispWhite,
                                    uncheckedTrackColor = RoseAlert.copy(alpha = 0.65f)
                                ),
                                modifier = Modifier.testTag("crud_toggle_stock_${item.id}")
                            )
                            Text(
                                text = if (item.isAvailable) "Available" else "Sold Out",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item.isAvailable) EmeraldDark else RoseDark
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { editingItem = item },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SlateBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(38.dp).testTag("edit_menu_item_${item.id}")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${item.title}", tint = NavyPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit", color = NavyPrimary, style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = { pendingDeleteItem = item },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(38.dp).testTag("delete_menu_item_${item.id}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${item.title}", tint = RoseDark, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", color = RoseDark, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MenuItemEditorDialog(
            existingItem = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, cat, price, prep, spice, artwork, url ->
                viewModel.addMenuItem(title, desc, cat, price, prep, spice, artwork, url)
                showAddDialog = false
            }
        )
    }

    editingItem?.let { target ->
        MenuItemEditorDialog(
            existingItem = target,
            onDismiss = { editingItem = null },
            onSave = { title, desc, cat, price, prep, spice, artwork, url ->
                viewModel.updateMenuItem(target.id, title, desc, cat, price, prep, spice, artwork, url)
                editingItem = null
            }
        )
    }

    pendingDeleteItem?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            containerColor = CrispWhite,
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = RoseAlert, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete ${target.title}?", style = MaterialTheme.typography.titleLarge, color = NavyPrimary) },
            text = { Text("This removes ${target.title} from the Customer Digital Menu immediately.", style = MaterialTheme.typography.bodyMedium, color = SlateSubtext) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMenuItem(target.id)
                        pendingDeleteItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAlert, contentColor = CrispWhite)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) { Text("Cancel", color = SlateSubtext) }
            }
        )
    }
}

@Composable
private fun MenuItemEditorDialog(
    existingItem: MenuItem?,
    onDismiss: () -> Unit,
    onSave: (String, String, MenuCategory, Int, String, SpiceLevel, MenuArtwork, String) -> Unit
) {
    var title by remember(existingItem) { mutableStateOf(existingItem?.title ?: "") }
    var description by remember(existingItem) { mutableStateOf(existingItem?.description ?: "") }
    var priceText by remember(existingItem) { mutableStateOf(existingItem?.priceRs?.toString() ?: "750") }
    var prepTime by remember(existingItem) { mutableStateOf(existingItem?.prepTimeRange ?: "15-20 min") }
    var category by remember(existingItem) { mutableStateOf(existingItem?.category ?: MenuCategory.MAINS) }
    var spiceLevel by remember(existingItem) { mutableStateOf(existingItem?.spiceLevel ?: SpiceLevel.MILD) }
    var artwork by remember(existingItem) { mutableStateOf(existingItem?.artwork ?: MenuArtwork.CUSTOM_DISH) }
    var imageUrl by remember(existingItem) { mutableStateOf(existingItem?.imageUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CrispWhite,
        title = {
            Text(
                text = if (existingItem == null) "Add New Menu Item" else "Edit ${existingItem.title}",
                style = MaterialTheme.typography.titleLarge,
                color = NavyPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("menu_form_title_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Price (Rs) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("menu_form_price_input")
                    )
                    OutlinedTextField(
                        value = prepTime,
                        onValueChange = { prepTime = it },
                        label = { Text("Prep Time (mins)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("menu_form_prep_input")
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("menu_form_desc_input")
                )
                Text("Category", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MenuCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = CrispWhite)
                        )
                    }
                }
                Text("Spice / Profile", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpiceLevel.entries.forEach { spice ->
                        FilterChip(
                            selected = spiceLevel == spice,
                            onClick = { spiceLevel = spice },
                            label = { Text(spice.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NavyPrimary, selectedLabelColor = CrispWhite)
                        )
                    }
                }
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Image URL (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Preset Food Photo / Icon", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MenuArtwork.entries.forEach { option ->
                        val selected = artwork == option
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(if (selected) 2.dp else 1.dp, if (selected) NavyPrimary else SlateBorder, RoundedCornerShape(12.dp))
                                .clickable { artwork = option }
                                .padding(4.dp)
                        ) {
                            FoodArtwork(
                                artwork = option,
                                contentDescription = option.label,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                text = option.label.split(" ").first(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (selected) NavyPrimary else SlateSubtext
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description, category, priceText.toIntOrNull() ?: 650, prepTime, spiceLevel, artwork, imageUrl) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                modifier = Modifier.testTag("menu_form_save_button")
            ) { Text(if (existingItem == null) "Add Item" else "Save Changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SlateSubtext) }
        }
    )
}

@Composable
fun OwnerAvailabilitySection(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Inventory & Stock", style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
            Text("Toggle Available / Sold Out — reflects immediately on Customer Menu", style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
        }

        items(uiState.menuItems, key = { it.id }) { item ->
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(
                                text = if (item.isAvailable) "Available" else "Sold Out",
                                containerColor = if (item.isAvailable) EmeraldSoft else RoseSoft,
                                contentColor = if (item.isAvailable) EmeraldDark else RoseDark
                            )
                            if (item.isLowStock && item.isAvailable) {
                                StatusPill(text = "Low Stock", containerColor = AmberSoft, contentColor = AmberDark)
                            }
                        }
                    }

                    Switch(
                        checked = item.isAvailable,
                        onCheckedChange = { viewModel.toggleItemAvailability(item.id) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CrispWhite,
                            checkedTrackColor = NavyPrimary,
                            uncheckedThumbColor = CrispWhite,
                            uncheckedTrackColor = RoseAlert.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("toggle_availability_${item.id}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerLiveOrdersSection(
    uiState: RestaurantUiState,
    viewModel: RestaurantViewModel
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var receiptModalData by remember { mutableStateOf<ThermalReceiptData?>(null) }

    val filteredOrders = remember(
        uiState.orders,
        uiState.orderChannelFilter,
        uiState.completedOrderDateFilter,
        uiState.completedOrderCustomDateIso,
        uiState.completedOrderSearchQuery
    ) {
        val today = DateUtils.todayIso()
        val yesterday = DateUtils.daysAgoIso(1)
        val query = uiState.completedOrderSearchQuery.trim().lowercase()
        uiState.orders.filter { order ->
            val channelMatch = when (uiState.orderChannelFilter) {
                OrderChannelFilter.ALL -> true
                OrderChannelFilter.DELIVERY -> order.isDelivery
                OrderChannelFilter.KIOSK -> order.isKiosk
            }
            val effectiveDate = order.completedDateIso ?: order.orderDateIso
            val dateMatch = when (uiState.completedOrderDateFilter) {
                OrderHistoryDateFilter.TODAY -> effectiveDate == today
                OrderHistoryDateFilter.YESTERDAY -> effectiveDate == yesterday
                OrderHistoryDateFilter.CUSTOM_DATE -> effectiveDate == uiState.completedOrderCustomDateIso
                OrderHistoryDateFilter.ALL_DATES -> true
            }
            val searchMatch = query.isEmpty() ||
                order.orderNumber.toString().contains(query) ||
                order.tableCode.lowercase().contains(query) ||
                order.kdsBadgeLabel.lowercase().contains(query) ||
                (order.tokenNumber?.toString()?.contains(query) == true) ||
                order.customerName.lowercase().contains(query) ||
                order.items.any { it.title.lowercase().contains(query) }
            channelMatch && dateMatch && searchMatch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Orders & Audit Log", style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
            Text("Compiled from Home Delivery & At-Restaurant Kiosk • Synced with KDS", style = MaterialTheme.typography.bodySmall, color = SlateSubtext)
        }

        // Channel Filter Pills + Date Filter & Search
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrderChannelFilter.entries.forEach { channel ->
                        val selected = uiState.orderChannelFilter == channel
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setOrderChannelFilter(channel) },
                            label = { Text(channel.label, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (channel) {
                                        OrderChannelFilter.ALL -> Icons.Rounded.AllInclusive
                                        OrderChannelFilter.DELIVERY -> Icons.Rounded.DeliveryDining
                                        OrderChannelFilter.KIOSK -> Icons.Rounded.TouchApp
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = CrispWhite,
                                selectedLeadingIconColor = CrispWhite,
                                containerColor = CrispWhite,
                                labelColor = NavyMuted
                            ),
                            modifier = Modifier.testTag("owner_channel_filter_${channel.name.lowercase()}")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrderHistoryDateFilter.entries.forEach { filter ->
                        val selected = uiState.completedOrderDateFilter == filter
                        val label = if (filter == OrderHistoryDateFilter.CUSTOM_DATE && selected) {
                            DateUtils.formatFriendlyDate(uiState.completedOrderCustomDateIso)
                        } else filter.label
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (filter == OrderHistoryDateFilter.CUSTOM_DATE) showDatePicker = true
                                else viewModel.setCompletedOrderDateFilter(filter, null)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = CrispWhite,
                                containerColor = CrispWhite,
                                labelColor = NavyMuted
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.completedOrderSearchQuery,
                    onValueChange = viewModel::setCompletedOrderSearchQuery,
                    placeholder = { Text("Search order #, Token #16, customer, or dish...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SlateSubtext) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(filteredOrders, key = { it.orderNumber }) { order ->
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("owner_order_card_${order.orderNumber}")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "#${order.orderNumber}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                color = NavyPrimary
                            )
                            StatusPill(
                                text = order.kdsBadgeLabel,
                                containerColor = if (order.isDelivery) AmberSoft else EmeraldSoft,
                                contentColor = if (order.isDelivery) AmberDark else EmeraldDark,
                                icon = if (order.isDelivery) Icons.Rounded.DeliveryDining else Icons.Rounded.TouchApp
                            )
                        }
                        StatusPill(
                            text = when (order.status) {
                                OrderStatus.SERVED -> "Completed • ${order.completedTimeLabel ?: "Done"}"
                                OrderStatus.CANCELLED -> "Cancelled • ${order.completedTimeLabel ?: "Void"}"
                                else -> order.status.label
                            },
                            containerColor = when (order.status) {
                                OrderStatus.NEW -> SkySoft
                                OrderStatus.PREPARING -> AmberSoft
                                OrderStatus.READY -> EmeraldSoft
                                OrderStatus.ON_THE_WAY -> SkySoft
                                OrderStatus.SERVED -> SlateSurfaceVariant
                                OrderStatus.CANCELLED -> RoseSoft
                            },
                            contentColor = when (order.status) {
                                OrderStatus.NEW -> SkyDark
                                OrderStatus.PREPARING -> AmberDark
                                OrderStatus.READY -> EmeraldDark
                                OrderStatus.ON_THE_WAY -> SkyDark
                                OrderStatus.SERVED -> NavyPrimary
                                OrderStatus.CANCELLED -> RoseDark
                            }
                        )
                    }
                    if (order.isDelivery) {
                        Text(
                            text = "${order.customerName} (${order.customerPhone}) • ${order.deliveryAddress}" +
                                (order.gpsCoordinatesLabel?.let { " • GPS: $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyDark
                        )
                    } else if (order.isKiosk) {
                        Text(
                            text = "Self-Ordering Kiosk • ${order.paymentMethod?.label ?: "Cash at Counter"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldDark
                        )
                    }
                    Text(
                        text = order.items.joinToString { "${it.quantity}x ${it.title}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSubtext
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatRs(order.grandTotalRs)} • ${if (order.isPaid) "Paid" else "Unpaid"}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { receiptModalData = ThermalReceiptData.fromOrder(order) },
                                border = BorderStroke(1.dp, SlateBorder),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("owner_order_receipt_${order.orderNumber}")
                            ) {
                                Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Receipt", color = NavyPrimary, style = MaterialTheme.typography.labelMedium)
                            }
                            if (order.status != OrderStatus.SERVED && order.status != OrderStatus.CANCELLED) {
                                TextButton(onClick = { viewModel.advanceOrderStatus(order.orderNumber) }) {
                                    Text("Advance Step", color = NavyPrimary)
                                }
                            } else {
                                Text(
                                    text = DateUtils.formatFriendlyDate(order.completedDateIso ?: order.orderDateIso),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = SlateSubtext
                                )
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
                            viewModel.setCompletedOrderDateFilter(OrderHistoryDateFilter.CUSTOM_DATE, DateUtils.millisToIsoDate(millis))
                        }
                        showDatePicker = false
                    }
                ) { Text("Apply", color = NavyPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = SlateSubtext) }
            }
        ) { DatePicker(state = datePickerState) }
    }
}
