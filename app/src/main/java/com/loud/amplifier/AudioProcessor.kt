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
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile private var isRunning = false
    private var recordingThread: Thread? = null
    var amplification: Float = 25f
    var bassBoost: Float = 2.0f
    var compressorEnabled = true
    var limiterEnabled = true

    private val minBufIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
    private val minBufOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
    private val bufferSize = maxOf(minBufIn, minBufOut) * 8

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var prevSampleL = 0f
    private var prevSampleR = 0f
    private var envelope = 0f

    fun start() {
        if (isRunning) return
        isRunning = true

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
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
            .setBufferSizeInBytes(bufferSize * 2)
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
        val shortBufIn = ShortArray(bufferSize / 2)
        val shortBufOut = ShortArray(bufferSize)

        while (isRunning) {
            val read = audioRecord?.read(shortBufIn, 0, shortBufIn.size) ?: -1
            if (read <= 0) {
                Thread.sleep(2)
                continue
            }

            for (i in 0 until read) {
                var sample = shortBufIn[i] / 32768.0f

                // === Filtre passe-haut (supprime bruit grave) ===
                val hpOut = sample - prevSampleL * 0.95f
                prevSampleL = sample
                sample = hpOut

                // === Boost des basses ===
                sample = sample * (1f + bassBoost * 0.3f)

                // === Amplification MAX ===
                sample *= amplification

                // === Compresseur (niveau constant) ===
                if (compressorEnabled) {
                    val absSample = abs(sample)
                    envelope = 0.98f * envelope + 0.02f * absSample
                    val target = 0.7f
                    if (envelope > 0.01f) {
                        val compGain = target / envelope
                        sample *= compGain.coerceAtMost(3f)
                    }
                }

                // === Limiteur anti-écrêtage ===
                if (limiterEnabled) {
                    if (sample > 0.95f) sample = 0.95f + (sample - 0.95f) * 0.3f
                    if (sample < -0.95f) sample = -0.95f + (sample + 0.95f) * 0.3f
                }

                // === Soft clipping (doux, pas de distortion dure) ===
                sample = tanh(sample * 1.2f)

                // === Sortie STÉRÉO ===
                val outputSample = (sample * 32767f).toInt().toShort()
                shortBufOut[i*2] = outputSample
                shortBufOut[i*2 + 1] = outputSample
            }

            audioTrack?.write(shortBufOut, 0, read * 2)
        }
    }
}
