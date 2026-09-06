package com.loud.amplifier

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.*

class AudioProcessor {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile private var isRunning = false
    private var recordingThread: Thread? = null

    // Réglages équilibrés — PUISSANT mais PROPRE
    var amplification: Float = 12f       // ×12 maxi propre
    var noiseGate: Float = 0.015f        // Supprime silence/gros bruit
    var softKnee: Float = 0.85f          // Anti-écrêtage DOUX

    private val minBufIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
    private val minBufOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
    private val bufferSize = maxOf(minBufIn, minBufOut) * 2  // Réduit latence

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var prevHP = 0f
    private var prevOutHP = 0f

    fun start() {
        if (isRunning) return
        isRunning = true

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            bufferSize
        )

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val fmt = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT)
            .setEncoding(AUDIO_FORMAT)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioRecord?.startRecording()
        audioTrack?.play()

        recordingThread = Thread(::loop, "AudioProcessor").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning = false
        recordingThread?.join(500)
        audioRecord?.apply { stop(); release() }
        audioTrack?.apply { stop(); release() }
        audioRecord = null
        audioTrack = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val shortBuf = ShortArray(bufferSize / 2)

        while (isRunning) {
            val read = audioRecord?.read(shortBuf, 0, shortBuf.size) ?: -1
            if (read <= 0) {
                Thread.sleep(5)
                continue
            }

            for (i in 0 until read) {
                var sample = shortBuf[i] / 32768.0f

                // === Filtre passe-haut : supprime bruit grave (vent, fond) ===
                val hp = sample - prevHP
                prevHP = sample
                sample = hp * 0.9f + prevOutHP * 0.95f
                prevOutHP = sample

                // === Noise Gate : coupe le silence ===
                if (abs(sample) < noiseGate) {
                    sample = 0f
                }

                // === Amplification ===
                sample *= amplification

                // === SOFT CLIPPING ULTRA DOUX — ZÉRO GRÉSILLEMENT ===
                if (sample > softKnee) {
                    val excess = sample - softKnee
                    sample = softKnee + excess / (1f + excess * 2f)
                } else if (sample < -softKnee) {
                    val excess = sample + softKnee
                    sample = -softKnee + excess / (1f - excess * 2f)
                }

                // === Limiteur final ===
                sample = sample.coerceIn(-0.95f, 0.95f)

                shortBuf[i] = (sample * 32767f).toInt().toShort()
            }

            audioTrack?.write(shortBuf, 0, read)
        }
    }
}
