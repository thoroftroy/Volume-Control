package com.volumecontrol

import android.content.Context

class VolumeScaler(private val context: Context) {

    companion object {
        const val SCALE_MIN = 50
        const val SCALE_MAX = 500
        const val SCALE_DEFAULT = 100
        const val SCALE_STEP = 10
    }

    private var currentScale = SCALE_DEFAULT

    fun getScalePercent(): Int = currentScale

    fun getScaleFactor(): Float = currentScale / 100f

    fun increase() {
        currentScale = (currentScale + SCALE_STEP).coerceAtMost(SCALE_MAX)
        save()
    }

    fun decrease() {
        currentScale = (currentScale - SCALE_STEP).coerceAtLeast(SCALE_MIN)
        save()
    }

    fun reset() {
        currentScale = SCALE_DEFAULT
        save()
    }

    fun setScale(scale: Int) {
        currentScale = scale.coerceIn(SCALE_MIN, SCALE_MAX)
        save()
    }

    fun loadScale() {
        val prefs = context.getSharedPreferences("volume_control", Context.MODE_PRIVATE)
        currentScale = prefs.getInt("volume_scale", SCALE_DEFAULT)
    }

    private fun save() {
        val prefs = context.getSharedPreferences("volume_control", Context.MODE_PRIVATE)
        prefs.edit().putInt("volume_scale", currentScale).apply()
    }
}
