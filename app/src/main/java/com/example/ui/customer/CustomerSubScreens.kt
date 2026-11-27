package com.example.ui.customer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRs
import com.example.ui.components.formatTimer
import com.example.ui.theme.*
import com.example.util.LocalizedStrings

@Composable
fun CustomerTrackerTab(
    uiState: RestaurantUiState,
    strings: LocalizedStrings,
    onCancelOrder: (Int) -> Unit,
    onGoToMenu: () -> Unit
) {
    val trackedOrders = uiState.homeDeliveryOrders

    if (trackedOrders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SlateSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeliveryDining,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = strings.noActiveDeliveryOrders,
                    style = MaterialTheme.typography.titleMedium,
                    color = NavyPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.browseMenuTip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateSubtext,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onGoToMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(strings.exploreMenuBtn)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = NavyPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_orders_header_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CrispWhite.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = strings.myOrdersTitle,
                            tint = CrispWhite
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.myOrdersTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = CrispWhite
                        )
                        Text(
                            text = strings.myOrdersSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = CrispWhite.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        items(trackedOrders, key = { it.orderNumber }) { order ->
            OrderTrackerCard(
                order = order,
                strings = strings,
                onCancelOrder = onCancelOrder
            )
        }
    }
}

@Composable
private fun OrderTrackerCard(
    order: KitchenOrder,
    strings: LocalizedStrings,
    onCancelOrder: (Int) -> Unit
) {
    val steps = remember(order.isDelivery) {
        if (order.isDelivery) {
            listOf(
                OrderStatus.NEW,
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.ON_THE_WAY,
                OrderStatus.SERVED
            )
        } else {
            listOf(
                OrderStatus.NEW,
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.SERVED
            )
        }
    }
    val stepIndex = steps.indexOf(order.status).coerceAtLeast(0)
    val targetProgress = if (order.isDelivery) {
        when (order.status) {
            OrderStatus.NEW -> 0.20f
            OrderStatus.PREPARING -> 0.42f
            OrderStatus.READY -> 0.65f
            OrderStatus.ON_THE_WAY -> 0.85f
            OrderStatus.SERVED -> 1f
            OrderStatus.CANCELLED -> 0f
        }
    } else {
        when (order.status) {
            OrderStatus.NEW -> 0.25f
            OrderStatus.PREPARING -> 0.58f
            OrderStatus.READY -> 0.88f
            OrderStatus.ON_THE_WAY -> 0.94f
            OrderStatus.SERVED -> 1f
            OrderStatus.CANCELLED -> 0f
        }
    }
    val animatedProgress by animateFloatAsState(targetValue = targetProgress, label = "prepProgress")

    Surface(
        color = CrispWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when (order.status) {
                OrderStatus.ON_THE_WAY -> SkyInfo
                OrderStatus.CANCELLED -> RoseAlert.copy(alpha = 0.4f)
                else -> SlateBorder
            }
        ),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tracker_order_card_${order.orderNumber}")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "#${order.orderNumber}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary
                        )
                        if (order.isDelivery) {
                            StatusPill(
                                text = strings.homeDeliveryLabel,
                                containerColor = AmberSoft,
                                contentColor = AmberDark,
                                icon = Icons.Rounded.DeliveryDining
                            )
                        }
                    }
                    Text(
                        text = if (order.isDelivery) {
                            "${order.customerName} • ${order.deliveryAddress}"
                        } else {
                            "${order.tableCode} • ${order.placedAtLabel}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateSubtext
                    )
                    order.gpsCoordinatesLabel?.let { coords ->
                        Text(
                            text = "GPS Pin: $coords",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = SkyDark
                        )
                    }
                }
                val (pillBg, pillFg) = when (order.status) {
                    OrderStatus.NEW -> SkySoft to SkyDark
                    OrderStatus.PREPARING -> AmberSoft to AmberDark
                    OrderStatus.READY -> EmeraldSoft to EmeraldDark
                    OrderStatus.ON_THE_WAY -> SkySoft to SkyDark
                    OrderStatus.SERVED -> SlateSurfaceVariant to NavyPrimary
                    OrderStatus.CANCELLED -> RoseSoft to RoseDark
                }
                StatusPill(
                    text = strings.orderStepLabel(order.status, order.isDelivery),
                    containerColor = pillBg,
                    contentColor = pillFg,
                    icon = when (order.status) {
                        OrderStatus.ON_THE_WAY -> Icons.Rounded.DeliveryDining
                        OrderStatus.CANCELLED -> Icons.Rounded.Cancel
                        else -> Icons.Rounded.Sync
                    }
                )
            }

            if (order.status == OrderStatus.CANCELLED) {
                Surface(
                    color = RoseSoft,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_cancelled_banner_${order.orderNumber}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cancel,
                            contentDescription = strings.orderCancelledBadge,
                            tint = RoseDark
                        )
                        Text(
                            text = strings.orderStepSubtitle(OrderStatus.CANCELLED, order.isDelivery),
                            style = MaterialTheme.typography.bodySmall,
                            color = RoseDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // On the Way Rider Banner synced from Admin/KDS
                if (order.status == OrderStatus.ON_THE_WAY) {
                    Surface(
                        color = SkySoft,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SkyInfo.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("on_the_way_banner_${order.orderNumber}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SkyInfo),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeliveryDining,
                                    contentDescription = strings.onTheWayBanner,
                                    tint = CrispWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = strings.onTheWayBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SkyDark,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3-Minute Order Cancellation Window (At Home Delivery)
                if (order.isDelivery && order.status != OrderStatus.SERVED) {
                    if (order.canCustomerCancel) {
                        Button(
                            onClick = { onCancelOrder(order.orderNumber) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoseAlert,
                                contentColor = CrispWhite
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .height(48.dp)
                                .testTag("cancel_delivery_order_${order.orderNumber}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${strings.cancelOrderBtn} (${formatTimer(order.cancelWindowRemainingSeconds)} ${strings.leftLabel})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Surface(
                            color = SlateSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_locked_badge_${order.orderNumber}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = strings.orderConfirmedLocked,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.orderConfirmedLocked,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NavyPrimary
                                )
                            }
                        }
                    }
                }

                // Estimated Prep Time Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (order.status == OrderStatus.SERVED) strings.enjoyYourMeal else strings.estimatedPrepTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = NavyMuted
                        )
                        Text(
                            text = formatTimer(order.elapsedSeconds),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                            color = NavyPrimary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = when (order.status) {
                            OrderStatus.READY, OrderStatus.SERVED -> EmeraldSuccess
                            OrderStatus.ON_THE_WAY -> SkyInfo
                            else -> NavyPrimary
                        },
                        trackColor = SlateSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }

                // Live Status Tracker (4 steps Dine-In, 5 steps Home Delivery)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    steps.forEachIndexed { idx, step ->
                        val completed = idx <= stepIndex
                        val isCurrent = idx == stepIndex
                        val stepIcon = when (step) {
                            OrderStatus.NEW -> Icons.Rounded.CheckCircle
                            OrderStatus.PREPARING -> Icons.Rounded.SoupKitchen
                            OrderStatus.READY -> Icons.Rounded.NotificationsActive
                            OrderStatus.ON_THE_WAY -> Icons.Rounded.DeliveryDining
                            OrderStatus.SERVED -> Icons.Rounded.Dining
                            OrderStatus.CANCELLED -> Icons.Rounded.Cancel
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCurrent && step == OrderStatus.ON_THE_WAY -> SkyInfo
                                            isCurrent -> NavyPrimary
                                            completed -> EmeraldSuccess
                                            else -> SlateSurfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = stepIcon,
                                    contentDescription = strings.orderStepLabel(step, order.isDelivery),
                                    tint = if (completed) CrispWhite else SlateHint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.orderStepLabel(step, order.isDelivery),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (completed) NavyPrimary else SlateHint,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isCurrent) {
                                    Text(
                                        text = strings.orderStepSubtitle(step, order.isDelivery),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateSubtext
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Itemized summary
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                order.items.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${line.quantity}x ${line.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NavyPrimary
                        )
                        Text(
                            text = formatRs(line.priceRs * line.quantity),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                            color = NavyMuted
                        )
                    }
                }
            }

            val methodLabel = order.paymentMethod?.let { strings.paymentMethodLabel(it) }
                ?: strings.paymentMethodLabel(PaymentMethod.CASH_ON_DELIVERY)
            StatusPill(
                text = if (order.isPaid) {
                    "${strings.paidVia} $methodLabel"
                } else {
                    "${strings.paymentLabel}: $methodLabel"
                },
                containerColor = if (order.isPaid) EmeraldSoft else SkySoft,
                contentColor = if (order.isPaid) EmeraldDark else SkyDark,
                icon = if (order.isPaid) Icons.Rounded.Verified else Icons.Rounded.Payments
            )
        }
    }
}

@Composable
fun CustomerReviewsTab(
    uiState: RestaurantUiState,
    strings: LocalizedStrings,
    onSubmitReview: (author: String, rating: Int, tags: List<String>, comment: String, publishToSite: Boolean) -> Unit,
    onPublishExisting: (String) -> Unit,
    onGoogleReviewPrompt: () -> Unit
) {
    val complimentTags = listOf("Tasty food", "Fast service", "Great ambience", "Clean tables")
    var rating by remember { mutableIntStateOf(5) }
    var selectedTags by remember { mutableStateOf(setOf("Tasty food")) }
    var comment by remember { mutableStateOf("") }
    var showSubmittedActions by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = strings.feedbackTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyPrimary
                    )
                    Text(
                        text = strings.feedbackPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateSubtext
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        (1..5).forEach { star ->
                            IconButton(
                                onClick = { rating = star },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("review_star_$star")
                            ) {
                                Icon(
                                    imageVector = if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                    contentDescription = "$star stars",
                                    tint = if (star <= rating) AmberWarm else SlateHint,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = strings.whatStoodOut,
                        style = MaterialTheme.typography.labelLarge,
                        color = NavyPrimary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        complimentTags.chunked(2).forEach { rowTags ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowTags.forEach { tag ->
                                    val active = tag in selectedTags
                                    FilterChip(
                                        selected = active,
                                        onClick = {
                                            selectedTags = if (active) selectedTags - tag else selectedTags + tag
                                        },
                                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NavyPrimary,
                                            selectedLabelColor = CrispWhite,
                                            containerColor = SlateSurfaceVariant,
                                            labelColor = NavyMuted
                                        ),
                                        border = null,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text(strings.optionalComment) },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("review_comment_input")
                    )

                    Button(
                        onClick = {
                            val author = uiState.loggedInCustomer?.name ?: "Home Delivery Guest"
                            onSubmitReview(
                                author,
                                rating,
                                selectedTags.toList(),
                                comment,
                                false
                            )
                            comment = ""
                            showSubmittedActions = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_review_button")
                    ) {
                        Text(strings.submitFeedback, style = MaterialTheme.typography.labelLarge)
                    }

                    if (showSubmittedActions) {
                        Surface(
                            color = EmeraldSoft,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Feedback saved! Choose how to share it:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldDark
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            uiState.reviews.firstOrNull()?.let { onPublishExisting(it.id) }
                                            showSubmittedActions = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NavyPrimary,
                                            contentColor = CrispWhite
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("publish_website_reviews_button")
                                    ) {
                                        Text("Website Reviews", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = onGoogleReviewPrompt,
                                        border = BorderStroke(1.dp, NavyPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("review_on_google_button")
                                    ) {
                                        Text("Review on Google", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = strings.websiteReviewsLive,
                style = MaterialTheme.typography.titleMedium,
                color = NavyPrimary
            )
        }

        items(uiState.reviews.filter { it.isPublishedToSite }, key = { it.id }) { rev ->
            Surface(
                color = CrispWhite,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(rev.authorName, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                            Text("${rev.tableCode} • ${rev.timestampLabel}", style = MaterialTheme.typography.labelSmall, color = SlateSubtext)
                        }
                        Row {
                            repeat(rev.rating) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = AmberWarm, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (rev.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rev.tags.take(3).forEach { tag ->
                                StatusPill(text = tag, containerColor = SlateSurfaceVariant, contentColor = NavyMuted)
                            }
                        }
                    }
                    if (rev.comment.isNotBlank()) {
                        Text(rev.comment, style = MaterialTheme.typography.bodyMedium, color = SlateSubtext)
                    }
                }
            }
        }
    }
}
