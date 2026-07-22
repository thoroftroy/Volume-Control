package com.volumecontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        private const val BAR_COUNT = 64
    }

    private var minDb = -40f
    private var maxDb = -10f
    private val levels = FloatArray(BAR_COUNT)

    private val bg = Paint().apply { color = 0xFF101010.toInt() }
    private val redZone = Paint().apply { color = 0x18FF3333.toInt() }
    private val greenZone = Paint().apply { color = 0x1833CC33.toInt() }
    private val barGreen = Paint().apply { color = 0xFF33CC33.toInt(); style = Paint.Style.FILL }
    private val barRed = Paint().apply { color = 0xFFFF4444.toInt(); style = Paint.Style.FILL }
    private val barIdle = Paint().apply { color = 0x22333333.toInt(); style = Paint.Style.FILL }
    private val labelPaint = Paint().apply {
        color = 0x66FFFFFF.toInt(); textSize = 20f; isAntiAlias = true
    }

    fun setRange(minDb: Float, maxDb: Float) {
        this.minDb = minDb
        this.maxDb = maxDb
        invalidate()
    }

    fun pushWaveform(data: ByteArray) {
        if (data.size < 4) return
        val step = (data.size / 2) / BAR_COUNT
        if (step < 1) return
        var any = false
        for (i in 0 until BAR_COUNT) {
            var maxVal = 0f
            for (j in 0 until step) {
                val idx = (i * step + j) * 2
                if (idx + 1 < data.size) {
                    val s = ((data[idx].toInt() and 0xFF) or (data[idx + 1].toInt() shl 8)).toShort()
                    val v = abs(s.toFloat()) / 32768f
                    if (v > maxVal) maxVal = v
                }
            }
            levels[i] = levels[i] * 0.6f + maxVal * 0.4f
            if (levels[i] > 0.002f) any = true
        }
        if (any) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padL = 44f; val padR = 8f; val padT = 24f; val padB = 4f
        val cw = w - padL - padR
        val ch = h - padT - padB

        canvas.drawRect(0f, 0f, w, h, bg)

        fun dbToY(db: Float) = padT + ch * (1f - (db - DB_FLOOR) / (DB_CEILING - DB_FLOOR))

        val yMin = dbToY(minDb)
        val yMax = dbToY(maxDb)

        canvas.drawRect(padL, padT, padL + cw, yMin, redZone)
        canvas.drawRect(padL, yMin, padL + cw, yMax, greenZone)
        canvas.drawRect(padL, yMax, padL + cw, padT + ch, redZone)

        listOf(minDb, (minDb + maxDb) / 2f, maxDb).forEach { db ->
            val y = dbToY(db)
            canvas.drawLine(padL, y, padL + cw, y,
                Paint().apply { color = 0x22FFFFFF.toInt(); strokeWidth = 1f })
            canvas.drawText("${db.toInt()}", 2f, y + 16f, labelPaint)
        }

        val barW = cw / BAR_COUNT - 1.5f
        if (barW < 1f) return

        var hasSignal = false
        for (v in levels) if (v > 0.002f) { hasSignal = true; break }

        for (i in levels.indices) {
            val peak = levels[i]
            val db = if (peak > 0.0001f) {
                (20.0 * log10(peak.coerceAtLeast(0.0001f).toDouble())).toFloat()
            } else DB_FLOOR
            val barH = (peak * ch * 0.9f).coerceAtLeast(1.5f)
            val x = padL + i * (cw / BAR_COUNT)
            val yTop = padT + ch - barH

            val paint = when {
                !hasSignal -> barIdle
                db in minDb..maxDb -> barGreen
                else -> barRed
            }
            canvas.drawRect(x, yTop, x + barW, padT + ch, paint)
        }
    }
}
