package com.volumecontrol

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class EqualizerEngine {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
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

            loudness = LoudnessEnhancer(0).apply {
                enabled = true
            }

            try {
                bassBoost = BassBoost(0, 0).apply {
                    enabled = true
                }
            } catch (_: Exception) {
                bassBoost = null
            }

            applySettings()
        } catch (e: Exception) {
            Log.e("EqualizerEngine", "Failed to create global effects", e)
            releaseAll()
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
        bassBoost?.let { try { it.release() } catch (_: Exception) {} }
        equalizer = null
        loudness = null
        bassBoost = null
    }

    private fun applySettings() {
        try {
            applyEqualizer()
            applyLoudness()
            applyBassBoost()
        } catch (e: Exception) {
            Log.e("EqualizerEngine", "Failed to apply settings", e)
        }
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        val range = levelRange ?: return
        val minLevel = range[0].toInt()
        val maxLevel = range[1].toInt()

        val eqBoostFraction = (96 + minDb).toFloat().coerceIn(0f, 96f) / 96f
        val eqBoostMb = (eqBoostFraction * 1600f).toInt().coerceIn(0, maxLevel)

        val scaleDb = if (scaleFactor > 1f) {
            (20.0 * kotlin.math.log10(scaleFactor.toDouble())).toFloat()
        } else 0f
        val scaleBoostMb = (scaleDb * 80f).toInt().coerceIn(0, maxLevel / 2)

        for (i in 0 until bandCount) {
            val freqHz = eq.getCenterFreq(i.toShort()) / 1000f
            val weight = when {
                freqHz < 0.2f -> 0.65f
                freqHz < 0.5f -> 0.85f
                freqHz < 3.0f -> 1.0f
                freqHz < 8.0f -> 0.9f
                else -> 0.75f
            }
            val total = ((eqBoostMb.toFloat() * weight) + scaleBoostMb).toInt().coerceIn(minLevel, maxLevel)
            eq.setBandLevel(i.toShort(), total.toShort())
        }
    }

    private fun applyLoudness() {
        val le = loudness ?: return
        val baseGain = 5000
        val extraGain = (maxOf(0f, (scaleFactor - 1f)) * 4000f).toInt()
        val totalGain = (baseGain + extraGain).coerceIn(0, 15000)
        le.setTargetGain(totalGain)
    }

    private fun applyBassBoost() {
        val bb = bassBoost ?: return
        val strength: Short = if (scaleFactor > 1f) {
            (700 + (scaleFactor - 1f) * 150).toInt().coerceAtMost(1000).toShort()
        } else {
            (scaleFactor * 300f).toInt().coerceAtMost(1000).toShort()
        }
        bb.setStrength(strength)
    }
}
