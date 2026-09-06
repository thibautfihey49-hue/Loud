package com.loud.amplifier

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.*

class AudioProcessor {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile private var isRunning = false
    private var recordingThread: Thread? = null
    var amplification: Float = 8f
    var noiseReduction: Float = 0.7f

    private val minBufIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
    private val minBufOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
    private val bufferSize = maxOf(minBufIn, minBufOut) * 4

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            bufferSize
        )

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val fmt = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG_OUT)
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

            val floatBuf = FloatArray(read) { i -> shortBuf[i] / 32768.0f }

            // === Amplification directe ===
            for (i in 0 until read) {
                var sample = floatBuf[i]
                sample *= amplification
                sample = sample.coerceIn(-1f, 1f)
                floatBuf[i] = sample
            }

            // Réécrit dans le buffer court
            for (i in 0 until read) {
                shortBuf[i] = (floatBuf[i] * 32767f).toInt().toShort()
            }

            audioTrack?.write(shortBuf, 0, read)
        }
    }
}
