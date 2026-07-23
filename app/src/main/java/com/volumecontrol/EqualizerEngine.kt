package com.volumecontrol

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlin.math.log10

class EqualizerEngine {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var minDb = -40
    private var maxDb = -10
    private var scaleFactor = 1.0f
    private var bandCount = 0
    private var levelRange: ShortArray? = null

    fun attachGlobal() {
        try {
            releaseAll()
            equalizer = Equalizer(0, 0).apply {
                enabled = true
                bandCount = numberOfBands.toInt()
                levelRange = bandLevelRange
            }
            loudness = LoudnessEnhancer(0).apply { enabled = true }
            applySettings()
        } catch (e: Exception) {
            Log.e("EqualizerEngine", "Failed to create global effects", e)
            equalizer = null
            loudness = null
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
        equalizer?.let { try { it.release() } catch (_: Exception) {} }
        loudness?.let { try { it.release() } catch (_: Exception) {} }
        equalizer = null
        loudness = null
    }

    private fun applySettings() {
        try {
            val eq = equalizer ?: return
            val range = levelRange ?: return
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            val loudFraction = (96 + minDb).toFloat() / 96f
            val baseBoostDb = loudFraction * 16f

            val scaleDb = if (scaleFactor > 1f) {
                (20.0 * log10(scaleFactor.toDouble())).toFloat()
            } else 0f

            val totalBoostMb = ((baseBoostDb + scaleDb) * 100f).toInt().coerceIn(minLevel, maxLevel)

            for (i in 0 until bandCount) {
                eq.setBandLevel(i.toShort(), totalBoostMb.toShort())
            }

            val loudGain = (maxOf(0f, (scaleFactor - 1f)) * 5000f).toInt().coerceIn(0, 15000)
            loudness?.setTargetGain(loudGain)
        } catch (e: Exception) {
            Log.e("EqualizerEngine", "Failed to apply settings", e)
        }
    }
}
