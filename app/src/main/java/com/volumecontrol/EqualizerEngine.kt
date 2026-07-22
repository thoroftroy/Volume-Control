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
            val inputGain = (-minDb).toFloat().coerceIn(0f, 40f)
            val compressorThreshold = maxDb.toFloat()
            val scaleGainDb = (20.0 * log10(scaleFactor.toDouble().coerceAtLeast(0.001))).toFloat()

            val mbcBand = DynamicsProcessing.MbcBand(
                true, 1000f, 5f, 80f, 10f, compressorThreshold, 6f, -90f, 1f, 0f, 0f
            )

            val mbc = DynamicsProcessing.Mbc(true, true, 1)
            mbc.setBand(0, mbcBand)

            val limiter = DynamicsProcessing.Limiter(
                true, true, 0, 1f, 50f, 20f, -1f, scaleGainDb
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
