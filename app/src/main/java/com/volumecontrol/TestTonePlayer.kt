package com.volumecontrol

import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class TestTonePlayer {

    companion object {
        const val SAMPLE_RATE = 44100
        const val FREQUENCY = 440.0
        const val SWEEP_DURATION_S = 3
        const val CONSTANT_AMPLITUDE = 0.2f
        val ACTIVE_TINT = ColorStateList.valueOf(0xFF44CC44.toInt())
    }

    private var audioTrack: AudioTrack? = null
    @Volatile private var tonePlaying = false
    @Volatile private var sweepPlaying = false
    private var toneThread: Thread? = null
    private var sweepThread: Thread? = null
    private var sweepBuffer: ShortArray? = null
    private var sweepReadPos = 0

    var onWaveform: ((ByteArray) -> Unit)? = null

    fun playConstantTone() {
        stop()
        tonePlaying = true

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 2048)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        toneThread = Thread {
            val samples = ShortArray(bufSize / 2)
            var phase = 0.0
            val phaseInc = 2.0 * PI * FREQUENCY / SAMPLE_RATE
            val amp = (CONSTANT_AMPLITUDE * Short.MAX_VALUE).toInt()

            while (tonePlaying && audioTrack != null) {
                for (i in samples.indices) {
                    samples[i] = (amp * sin(phase)).toInt().toShort()
                    phase += phaseInc
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                audioTrack?.write(samples, 0, samples.size)

                val bytes = ByteArray(samples.size * 2)
                for (i in samples.indices) {
                    val v = samples[i].toInt()
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                onWaveform?.invoke(bytes)
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun playSweepTone() {
        stop()
        tonePlaying = true
        sweepPlaying = true

        val totalSamples = SAMPLE_RATE * SWEEP_DURATION_S
        val bufSize = totalSamples * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val buffer = ShortArray(totalSamples)
        var phase = 0.0
        val phaseInc = 2.0 * PI * FREQUENCY / SAMPLE_RATE

        for (i in buffer.indices) {
            val progress = i.toDouble() / totalSamples
            val amplitude = if (progress < 0.5) {
                (0.001 + 0.999 * (progress * 2.0)).toFloat()
            } else {
                (1.0 - 0.999 * ((progress - 0.5) * 2.0)).toFloat()
            }
            buffer[i] = (amplitude * Short.MAX_VALUE * sin(phase)).toInt().toShort()
            phase += phaseInc
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }

        sweepBuffer = buffer
        sweepReadPos = 0
        audioTrack?.write(buffer, 0, buffer.size)
        audioTrack?.setLoopPoints(0, totalSamples, -1)
        audioTrack?.play()

        sweepThread = Thread {
            try {
                while (sweepPlaying) {
                    val sb = sweepBuffer ?: break
                    val chunkSize = SAMPLE_RATE / 15
                    val chunk = ShortArray(chunkSize)
                    var idx = 0
                    while (idx < chunkSize && sweepPlaying) {
                        chunk[idx] = sb[sweepReadPos]
                        sweepReadPos = (sweepReadPos + 1) % sb.size
                        idx++
                    }
                    val bytes = ByteArray(chunk.size * 2)
                    for (i in chunk.indices) {
                        val v = chunk[i].toInt()
                        bytes[i * 2] = (v and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    onWaveform?.invoke(bytes)
                    Thread.sleep(1000 / 15)
                }
            } catch (_: InterruptedException) {}
        }.apply { priority = Thread.NORM_PRIORITY; start() }
    }

    fun stop() {
        tonePlaying = false
        sweepPlaying = false
        toneThread?.interrupt()
        sweepThread?.interrupt()
        toneThread = null
        sweepThread = null
        sweepBuffer = null
        audioTrack?.let {
            try { it.stop(); it.release() } catch (_: Exception) {}
        }
        audioTrack = null
    }

    fun isPlaying(): Boolean = tonePlaying
    fun getAudioSessionId(): Int = audioTrack?.audioSessionId ?: 0
}
