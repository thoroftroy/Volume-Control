package com.volumecontrol

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.log10

class EqualizerEngine {

    private var dynamics: DynamicsProcessing? = null
    private var minDb = -40
    private var maxDb = -10
    private var scaleFactor = 1.0f

    fun attachGlobal() {
        try {
            releaseAll()
            dynamics = DynamicsProcessing(0)
            dynamics?.enabled = true
            applySettings()
        } catch (e: Exception) {
            Log.e("EqualizerEngine", "Failed to create global dynamics", e)
        }
    }

    fun updateRange(minDb: Int, maxDb: Int) {
        this.minDb = minDb
        this.maxDb = maxDb
        applySettings()
    }

    fun updateScaleFactor(factor: Float) {
        this.scaleFactor = factor
        applySettings()
    }

    fun releaseAll() {
        dynamics?.let {
            try { it.release() } catch (_: Exception) {}
        }
        dynamics = null
    }

    private fun applySettings() {
        val dp = dynamics ?: return
        try {
            val rangeWidth = (maxDb - minDb).toFloat()

            // heavy input boost — quieter settings (lower minDb) = more gain
            val inputGain = (-minDb * 0.9f).coerceIn(6f, 50f)

            // compressor clamps peaks at maxDb level
            val compThreshold = maxDb.toFloat()
            val compRatio = 6f
            val compKnee = (rangeWidth * 0.25f).coerceIn(4f, 14f)

            // volume scale applied via final limiter post-gain
            val scaleGainDb = (20.0 * log10(scaleFactor.toDouble().coerceAtLeast(0.001))).toFloat()

            val mbcBand = DynamicsProcessing.MbcBand(
                true, 1000f,
                3f, 60f,
                compRatio, compThreshold, compKnee,
                -90f, 1f,
                3f, 0f
            )

            val mbc = DynamicsProcessing.Mbc(true, true, 1)
            mbc.setBand(0, mbcBand)

            val limiter = DynamicsProcessing.Limiter(
                true, true, 0, 1f, 40f, 30f, -0.5f, scaleGainDb
            )

            dp.setInputGainAllChannelsTo(inputGain)
            dp.setMbcAllChannelsTo(mbc)
            dp.setMbcBandAllChannelsTo(0, mbcBand)
            dp.setLimiterAllChannelsTo(limiter)
        } catch (e: Exception) {
            Log.e("EqualizerEngine", "Failed to apply dynamics settings", e)
        }
    }
}
