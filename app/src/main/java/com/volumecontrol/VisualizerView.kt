package com.volumecontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.log10

class VisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val DB_FLOOR = -96f
        private const val DB_CEILING = 0f
        private const val WAVEFORM_SIZE = 256
    }

    private var minDb = -40f
    private var maxDb = -10f
    private val waveform = FloatArray(WAVEFORM_SIZE)

    private val bgPaint = Paint().apply { color = 0xFF1A1A1A.toInt() }
    private val redPaint = Paint().apply { color = 0x44FF4444.toInt() }
    private val greenPaint = Paint().apply { color = 0x4444FF44.toInt() }
    private val wavePaint = Paint().apply {
        color = Color.WHITE; strokeWidth = 2.5f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val waveRedPaint = Paint().apply {
        color = 0xFFFF4444.toInt(); strokeWidth = 2.5f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = 0xAAFFFFFF.toInt(); textSize = 26f; isAntiAlias = true
    }
    private val markerPaint = Paint().apply {
        color = 0x66FFFFFF.toInt(); strokeWidth = 1f
    }

    fun setRange(minDb: Float, maxDb: Float) {
        this.minDb = minDb
        this.maxDb = maxDb
        invalidate()
    }

    fun pushWaveform(data: ByteArray) {
        if (waveform.isEmpty()) return
        val len = minOf(data.size / 2, WAVEFORM_SIZE)
        val srcStep = (data.size / 2) / len
        for (i in 0 until len) {
            val idx = i * srcStep * 2
            if (idx + 1 < data.size) {
                val sample = ((data[idx].toInt() and 0xFF) or (data[idx + 1].toInt() shl 8)).toShort()
                waveform[i] = waveform[i] * 0.7f + abs(sample.toFloat() / 32768f) * 0.3f
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padLeft = 80f
        val padRight = 16f
        val padTop = 12f
        val padBottom = 12f
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        fun dbToY(db: Float): Float {
            return padTop + chartH * (1f - (db - DB_FLOOR) / (DB_CEILING - DB_FLOOR))
        }

        val yMin = dbToY(minDb)
        val yMax = dbToY(maxDb)
        val chartRight = padLeft + chartW

        canvas.drawRect(padLeft, padTop, chartRight, yMin, redPaint)
        canvas.drawRect(padLeft, yMax, chartRight, padTop + chartH, redPaint)
        canvas.drawRect(padLeft, yMin, chartRight, yMax, greenPaint)

        val avgDb = (minDb + maxDb) / 2f
        listOf(minDb, avgDb, maxDb).forEach { db ->
            val y = dbToY(db)
            canvas.drawLine(padLeft, y, chartRight, y, markerPaint)
            val label = "${db.toInt()} dB"
            canvas.drawText(label, 4f, y + 8f, labelPaint)
        }

        // live waveform
        var activeCount = 0
        for (v in waveform) if (v > 0.001f) activeCount++
        if (activeCount > 2) {
            val xStep = chartW / WAVEFORM_SIZE
            val pathGreen = Path()
            val pathRed = Path()
            var greenStarted = false
            var redStarted = false

            for (i in waveform.indices) {
                val levelDb = if (waveform[i] > 0.0001f) {
                    20f * log10(waveform[i].coerceAtLeast(0.0001f).toDouble()).toFloat()
                } else {
                    DB_FLOOR
                }
                val x = padLeft + i * xStep
                val y = dbToY(levelDb)
                val inRange = levelDb in minDb..maxDb

                if (inRange) {
                    if (redStarted) { pathRed.lineTo(x, y); redStarted = false }
                    if (!greenStarted) { pathGreen.moveTo(x, y); greenStarted = true }
                    else pathGreen.lineTo(x, y)
                } else {
                    if (greenStarted) { pathGreen.lineTo(x, y); greenStarted = false }
                    if (!redStarted) { pathRed.moveTo(x, y); redStarted = true }
                    else pathRed.lineTo(x, y)
                }
            }

            canvas.drawPath(pathGreen, wavePaint)
            canvas.drawPath(pathRed, waveRedPaint)
        }
    }
}
