package com.biexi.pandaled.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeHelper {

    fun generateQrCode(content: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                )
            }
        }
        return bitmap
    }

    /** Generate QR code with a text label baked into the bitmap below it. */
    fun generateQrCodeWithLabel(content: String, qrSize: Int, label: String, labelTextSize: Float = 28f): Bitmap {
        val qrBitmap = generateQrCode(content, qrSize)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = labelTextSize
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = paint.fontMetrics
        val lineSpacing = 6f
        val lineHeight = fontMetrics.descent - fontMetrics.ascent
        val textPadding = 16
        val lines = label.split("\n")
        val totalTextHeight = (lineHeight * lines.size + lineSpacing * (lines.size - 1)).toInt()
        val totalHeight = qrSize + textPadding + totalTextHeight + textPadding

        val result = Bitmap.createBitmap(qrSize, totalHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(qrBitmap, 0f, 0f, null)

        var y = qrSize + textPadding - fontMetrics.ascent
        for (line in lines) {
            canvas.drawText(line, qrSize / 2f, y, paint)
            y += lineHeight + lineSpacing
        }

        qrBitmap.recycle()
        return result
    }
}
