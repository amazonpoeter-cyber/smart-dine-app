package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.example.util.LocalizedStrings
import com.example.util.QrAndThermalHelper
import com.example.util.ThermalReceiptData

@Composable
fun ThermalReceiptModal(
    receipt: ThermalReceiptData,
    strings: LocalizedStrings,
    onDismiss: () -> Unit,
    onFeedbackMessage: (String) -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = CrispWhite,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .testTag("thermal_receipt_modal")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                        Text(
                            text = strings.thermalReceiptTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp).testTag("close_thermal_receipt_modal")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn, tint = SlateSubtext)
                    }
                }

                // 80mm Monochrome Thermal Paper Preview
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .testTag("thermal_receipt_paper_preview")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartDineBrandLogo(size = 36.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Smart Dine",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color.Black
                            )
                        }
                        Text(
                            text = "80MM POS THERMAL BILL",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ThermalDashedRule()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${strings.receiptLabel}: ${receipt.receiptId}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.Black
                            )
                            Text(
                                text = receipt.orderNumberLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                color = Color.Black
                            )
                        }
                        Text(
                            text = "${receipt.dateLabel} • ${receipt.timeLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = Color.Black
                        )
                        Text(
                            text = "${strings.orderTypeLabel}: ${receipt.orderTypeLabel}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black
                        )
                        receipt.customerDetail?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = Color.Black
                            )
                        }

                        ThermalDashedRule()

                        receipt.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.title}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = Color.Black,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(
                                    text = formatRs(item.priceRs * item.quantity),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = Color.Black
                                )
                            }
                        }

                        ThermalDashedRule()

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = strings.subtotal,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = Color.Black
                            )
                            Text(
                                text = formatRs(receipt.subtotalRs),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = Color.Black
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = strings.taxGst,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = Color.Black
                            )
                            Text(
                                text = formatRs(receipt.taxRs),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = Color.Black
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = strings.grandTotal.uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                color = Color.Black
                            )
                            Text(
                                text = formatRs(receipt.grandTotalRs),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                color = Color.Black
                            )
                        }

                        ThermalDashedRule()

                        Text(
                            text = "${strings.paymentLabel}: ${receipt.paymentMethodLabel} (${if (receipt.isPaid) "PAID" else "DUE"})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.receiptThankYouFooter,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Print Thermal Receipt + Download Receipt buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val ok = QrAndThermalHelper.printThermalReceipt(context, receipt)
                            onFeedbackMessage(
                                if (ok) "80mm Thermal Receipt sent to Print Manager"
                                else "Printer service unavailable on this device"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = CrispWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .height(48.dp)
                            .testTag("print_thermal_receipt_button")
                    ) {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.printThermalReceipt, style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            val bmp = QrAndThermalHelper.renderThermalReceiptBitmap(receipt)
                            val res = QrAndThermalHelper.savePngToDevice(
                                context = context,
                                bitmap = bmp,
                                fileName = "SmartDine_${receipt.receiptId}.png"
                            )
                            onFeedbackMessage(res.getOrElse { "Unable to save receipt: ${it.message}" })
                        },
                        border = BorderStroke(1.dp, NavyPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .height(48.dp)
                            .testTag("download_thermal_receipt_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.downloadReceipt, color = NavyPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThermalDashedRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Color.Black.copy(alpha = 0.55f))
    )
}
