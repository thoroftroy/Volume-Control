package com.volumecontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class VisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val DB_FLOOR = -96f
        private const val DB_CEILING = 0f
        private const val BAR_COUNT = 48
        private const val HISTORY = 3
    }

    private var minDb = -40f
    private var maxDb = -10f
    private val barLevels = FloatArray(BAR_COUNT * HISTORY)
    private var writeIdx = 0

    private val bgPaint = Paint().apply { color = 0xFF141414.toInt() }
    private val redZone = Paint().apply { color = 0x22FF3333.toInt() }
    private val greenZone = Paint().apply { color = 0x2233FF33.toInt() }
    private val barGreen = Paint().apply { color = 0xFF33CC33.toInt(); style = Paint.Style.FILL }
    private val barRed = Paint().apply { color = 0xFFFF4444.toInt(); style = Paint.Style.FILL }
    private val barInactive = Paint().apply { color = 0x22444444.toInt(); style = Paint.Style.FILL }
    private val labelPaint = Paint().apply {
        color = 0x88FFFFFF.toInt(); textSize = 22f; isAntiAlias = true
    }
    private val markerPaint = Paint().apply {
        color = 0x33FFFFFF.toInt(); strokeWidth = 1f
    }

    fun setRange(minDb: Float, maxDb: Float) {
        this.minDb = minDb
        this.maxDb = maxDb
        invalidate()
    }

    fun pushWaveform(data: ByteArray) {
        val len = minOf(data.size / 2, BAR_COUNT)
        val srcStep = maxOf((data.size / 2) / len, 1)
        val base = writeIdx * BAR_COUNT

        for (i in 0 until len) {
            val idx = i * srcStep * 2
            if (idx + 1 < data.size) {
                val sample = ((data[idx].toInt() and 0xFF) or (data[idx + 1].toInt() shl 8)).toShort()
                val level = abs(sample.toFloat()) / 32768f
                barLevels[base + i] = level
            }
        }
        writeIdx = (writeIdx + 1) % HISTORY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padLeft = 12f
        val padRight = 12f
        val padTop = 28f
        val padBottom = 4f
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        fun dbToY(db: Float): Float {
            return padTop + chartH * (1f - (db - DB_FLOOR) / (DB_CEILING - DB_FLOOR))
        }

        val yMin = dbToY(minDb)
        val yMax = dbToY(maxDb)
        val chartRight = padLeft + chartW

        canvas.drawRect(padLeft, padTop, chartRight, yMin, redZone)
        canvas.drawRect(padLeft, yMax, chartRight, padTop + chartH, redZone)
        canvas.drawRect(padLeft, yMin, chartRight, yMax, greenZone)

        listOf(minDb, maxDb, (minDb + maxDb) / 2f).forEach { db ->
            val y = dbToY(db)
            canvas.drawLine(padLeft, y, chartRight, y, markerPaint)
            canvas.drawText("${db.toInt()}", 2f, y + 18f, labelPaint)
        }

        val barW = chartW / BAR_COUNT - 2f
        if (barW < 1f) return

        var active = false
        for (i in 0 until BAR_COUNT) {
            var maxLevel = 0f
            for (hst in 0 until HISTORY) {
                val v = barLevels[hst * BAR_COUNT + i]
                if (v > maxLevel) maxLevel = v
            }
            if (maxLevel > 0.002f) active = true

            val levelDb = if (maxLevel > 0.0001f) {
                (20.0 * log10(maxLevel.coerceAtLeast(0.0001f).toDouble())).toFloat()
            } else {
                DB_FLOOR
            }

            val barH = maxOf(chartH * (maxLevel * 0.95f), 2f)
            val x = padLeft + i * (chartW / BAR_COUNT)
            val yTop = padTop + chartH - barH

            val inRange = levelDb in minDb..maxDb
            val paint = when {
                !active -> barInactive
                inRange -> barGreen
                else -> barRed
            }
            canvas.drawRect(x, yTop, x + barW, padTop + chartH, paint)
        }
    }
}
