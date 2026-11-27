package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import com.example.model.KitchenOrder
import com.example.model.OrderLineItem
import com.example.model.PaymentMethod
import com.example.model.PaymentReceipt
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

data class ThermalReceiptData(
    val receiptId: String,
    val orderNumberLabel: String,
    val dateLabel: String,
    val timeLabel: String,
    val orderTypeLabel: String,
    val customerDetail: String? = null,
    val items: List<OrderLineItem>,
    val subtotalRs: Int,
    val taxRs: Int,
    val grandTotalRs: Int,
    val paymentMethodLabel: String,
    val isPaid: Boolean
) {
    companion object {
        fun fromOrder(order: KitchenOrder): ThermalReceiptData {
            val typeLabel = when {
                order.isDelivery -> "Home Delivery"
                order.tokenNumber != null -> "Dine-In / Kiosk • Token #${order.tokenNumber}"
                else -> "Dine-In / Kiosk • ${order.tableCode}"
            }
            val custDetail = if (order.isDelivery && order.customerName.isNotBlank()) {
                "${order.customerName} (${order.customerPhone}) • ${order.deliveryAddress}"
            } else null
            val payLabel = order.paymentMethod?.label ?: if (order.isPaid) "Online Payment" else "Cash at Counter"
            return ThermalReceiptData(
                receiptId = "RCP-${1000 + order.orderNumber}",
                orderNumberLabel = if (order.tokenNumber != null) "Token #${order.tokenNumber} (#${order.orderNumber})" else "#${order.orderNumber}",
                dateLabel = DateUtils.formatFriendlyDate(order.completedDateIso ?: order.orderDateIso),
                timeLabel = order.completedTimeLabel ?: order.placedAtLabel,
                orderTypeLabel = typeLabel,
                customerDetail = custDetail,
                items = order.items,
                subtotalRs = order.subtotalRs,
                taxRs = order.taxRs,
                grandTotalRs = order.grandTotalRs,
                paymentMethodLabel = payLabel,
                isPaid = order.isPaid
            )
        }

        fun fromPaymentReceipt(receipt: PaymentReceipt, allOrders: List<KitchenOrder>): ThermalReceiptData {
            val matchedOrders = allOrders.filter { it.orderNumber in receipt.orderNumbers }
            val mergedItems = matchedOrders.flatMap { it.items }
            val subtotal = mergedItems.sumOf { it.priceRs * it.quantity }
            val tax = (subtotal * 0.05).toInt()
            val grandTotal = if (matchedOrders.isNotEmpty()) subtotal + tax else receipt.totalPaidRs
            return ThermalReceiptData(
                receiptId = receipt.receiptId,
                orderNumberLabel = receipt.orderNumbers.joinToString(", ") { "#$it" },
                dateLabel = DateUtils.formatFriendlyDate(DateUtils.todayIso()),
                timeLabel = DateUtils.currentTimeLabel(),
                orderTypeLabel = "Dine-In / Kiosk • ${receipt.tableCode}",
                customerDetail = null,
                items = mergedItems,
                subtotalRs = subtotal,
                taxRs = tax,
                grandTotalRs = grandTotal,
                paymentMethodLabel = receipt.method.label,
                isPaid = true
            )
        }
    }
}

object QrAndThermalHelper {

    private fun formatRsPlain(amount: Int): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return "Rs ${formatter.format(amount)}"
    }

    fun renderThermalReceiptBitmap(receipt: ThermalReceiptData): Bitmap {
        val width = 576 // 80mm thermal paper @ 203 DPI
        val headerHeight = 285
        val itemRowHeight = 46
        val extraCustomerHeight = if (receipt.customerDetail != null) 66 else 0
        val footerHeight = 290
        val height = (headerHeight + extraCustomerHeight + (receipt.items.size.coerceAtLeast(1) * itemRowHeight) + footerHeight)
            .coerceAtLeast(560)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawThermalReceiptOnCanvas(canvas, width, receipt)
        return bmp
    }

    private fun drawThermalReceiptOnCanvas(canvas: Canvas, width: Int, receipt: ThermalReceiptData) {
        canvas.drawColor(Color.WHITE)
        val pad = 28f
        val rightEdge = width - pad
        var y = 52f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 38f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.LEFT
        }
        val leftBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }
        val totalLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val totalRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        canvas.drawText("Smart Dine", width / 2f, y, titlePaint)
        y += 30f
        canvas.drawText("80mm POS THERMAL RECEIPT", width / 2f, y, subCenterPaint)
        y += 22f
        canvas.drawLine(pad, y, rightEdge, y, dashPaint)
        y += 32f

        canvas.drawText("Receipt: ${receipt.receiptId}", pad, y, leftBoldPaint)
        canvas.drawText(receipt.orderNumberLabel, rightEdge, y, rightPaint)
        y += 28f
        canvas.drawText("${receipt.dateLabel} • ${receipt.timeLabel}", pad, y, leftPaint)
        y += 28f
        canvas.drawText("Type: ${receipt.orderTypeLabel}", pad, y, leftBoldPaint)
        y += 26f

        receipt.customerDetail?.let { detail ->
            val clipped = if (detail.length > 42) detail.take(40) + ".." else detail
            canvas.drawText(clipped, pad, y, leftPaint)
            y += 26f
        }

        canvas.drawLine(pad, y, rightEdge, y, dashPaint)
        y += 30f

        receipt.items.forEach { item ->
            val label = "${item.quantity}x ${item.title}"
            val maxChars = 25
            val displayTitle = if (label.length > maxChars) label.take(maxChars - 1) + "…" else label
            canvas.drawText(displayTitle, pad, y, leftPaint)
            canvas.drawText(formatRsPlain(item.priceRs * item.quantity), rightEdge, y, rightPaint)
            y += 34f
        }

        canvas.drawLine(pad, y, rightEdge, y, dashPaint)
        y += 30f

        canvas.drawText("Subtotal", pad, y, leftPaint)
        canvas.drawText(formatRsPlain(receipt.subtotalRs), rightEdge, y, rightPaint)
        y += 28f
        canvas.drawText("Tax (5%)", pad, y, leftPaint)
        canvas.drawText(formatRsPlain(receipt.taxRs), rightEdge, y, rightPaint)
        y += 34f

        canvas.drawText("GRAND TOTAL", pad, y, totalLeftPaint)
        canvas.drawText(formatRsPlain(receipt.grandTotalRs), rightEdge, y, totalRightPaint)
        y += 24f
        canvas.drawLine(pad, y, rightEdge, y, dashPaint)
        y += 30f

        val payStatus = if (receipt.isPaid) "PAID" else "DUE"
        canvas.drawText("Payment: ${receipt.paymentMethodLabel} [$payStatus]", pad, y, leftBoldPaint)
        y += 42f
        canvas.drawText("Thank you for choosing Smart Dine! Come again.", width / 2f, y, subCenterPaint)
    }

    private fun drawKotSlipOnCanvas(canvas: Canvas, width: Int, order: KitchenOrder) {
        canvas.drawColor(Color.WHITE)
        val pad = 20f
        val rightEdge = width - pad
        var y = 36f

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val orderNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.LEFT
        }
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
            textAlign = Paint.Align.LEFT
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        canvas.drawText("KITCHEN ORDER TICKET (KOT)", width / 2f, y, headerPaint)
        y += 16f
        canvas.drawLine(pad, y, rightEdge, y, linePaint)
        y += 34f

        val badge = when {
            order.isDelivery -> "DELIVERY"
            order.tokenNumber != null -> "KIOSK #${order.tokenNumber}"
            else -> "DINE-IN / KIOSK"
        }
        canvas.drawText("ORDER #${order.orderNumber}", pad, y, orderNumPaint)
        canvas.drawText(badge, rightEdge, y - 4f, badgePaint)
        y += 22f
        val channelSub = if (order.isDelivery) "HOME DELIVERY" else "DINE-IN / KIOSK"
        canvas.drawText("$channelSub • ${DateUtils.formatFriendlyDate(order.orderDateIso)} • ${order.placedAtLabel}", pad, y, metaPaint)
        y += 14f
        canvas.drawLine(pad, y, rightEdge, y, linePaint)
        y += 30f

        order.items.forEach { line ->
            canvas.drawText("${line.quantity}x  ${line.title}", pad, y, itemPaint)
            y += 26f
            if (line.note.isNotBlank()) {
                canvas.drawText("   NOTE: ${line.note}", pad, y, notePaint)
                y += 20f
            }
        }

        y += 8f
        canvas.drawLine(pad, y, rightEdge, y, linePaint)
    }

    fun savePngToDevice(context: Context, bitmap: Bitmap, fileName: String): Result<String> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SmartDine")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create MediaStore image entry")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("Unable to open MediaStore output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Saved $fileName to Pictures/SmartDine"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                "Saved $fileName"
            }
        }
    }

    fun printThermalReceipt(context: Context, receipt: ThermalReceiptData): Boolean {
        val receiptBmp = renderThermalReceiptBitmap(receipt)
        val thermal80mm = PrintAttributes.MediaSize(
            "SMARTDINE_80MM",
            "80mm Thermal POS",
            3150,
            (3150f * receiptBmp.height / receiptBmp.width).toInt().coerceAtLeast(3500)
        )
        return printBitmapDocument(
            context = context,
            jobName = "SmartDine_Receipt_${receipt.receiptId}",
            mediaSize = thermal80mm,
            bitmap = receiptBmp
        )
    }

    fun printKotSlip(context: Context, order: KitchenOrder): Boolean {
        val width = 420
        val extraNotes = order.items.count { it.note.isNotBlank() }
        val height = (190 + order.items.size * 30 + extraNotes * 22).coerceAtLeast(240)
        val kotBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawKotSlipOnCanvas(Canvas(kotBmp), width, order)
        val thermal80mm = PrintAttributes.MediaSize(
            "SMARTDINE_KOT_80MM",
            "80mm Thermal KOT",
            3150,
            (3150f * height / width).toInt().coerceAtLeast(2800)
        )
        return printBitmapDocument(
            context = context,
            jobName = "SmartDine_KOT_${order.orderNumber}",
            mediaSize = thermal80mm,
            bitmap = kotBmp
        )
    }

    private fun printBitmapDocument(
        context: Context,
        jobName: String,
        mediaSize: PrintAttributes.MediaSize,
        bitmap: Bitmap
    ): Boolean {
        return runCatching {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
            val attributes = PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            val adapter = object : PrintDocumentAdapter() {
                private var pdfDocument: PrintedPdfDocument? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    pdfDocument = PrintedPdfDocument(context, newAttributes)
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val doc = pdfDocument ?: return
                    val page = doc.startPage(0)
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        doc.close()
                        pdfDocument = null
                        return
                    }
                    val contentWidth = page.info.pageWidth.toFloat()
                    val contentHeight = page.info.pageHeight.toFloat()
                    val scale = minOf(contentWidth / bitmap.width, contentHeight / bitmap.height)
                    val destW = bitmap.width * scale
                    val destH = bitmap.height * scale
                    val left = (contentWidth - destW) / 2f
                    page.canvas.drawBitmap(bitmap, null, RectF(left, 8f, left + destW, 8f + destH), null)
                    doc.finishPage(page)

                    try {
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            doc.writeTo(out)
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.toString())
                    } finally {
                        doc.close()
                        pdfDocument = null
                    }
                }
            }

            printManager.print(jobName, adapter, attributes)
            true
        }.getOrElse { false }
    }
}
