package com.loud.amplifier

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.math.*

class AudioProcessor {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    private var isRunning = false
    private var recordingThread: Thread? = null
    private val minBufferSizeIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private val minBufferSizeOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, OUTPUT_CHANNEL_CONFIG, AUDIO_FORMAT)
    var amplification: Float = 8f
    var noiseReduction: Float = 0.7f
    private val bandPass = BandPassFilter(300f, 3400f, SAMPLE_RATE)
    private val notch50Hz = NotchFilter(50f, 30f, SAMPLE_RATE)
    private val noiseSuppressor = NoiseSuppressor(SAMPLE_RATE)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (isRunning) return
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSizeIn * 4)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(OUTPUT_CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSizeOut * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        isRunning = true
        audioRecord?.startRecording()
        audioTrack?.play()
        recordingThread = Thread(::processAudio, "AudioProcessor").apply { start() }
    }

    fun stop() {
        isRunning = false
        recordingThread?.join()
        audioRecord?.apply { stop(); release() }
        audioTrack?.apply { stop(); release() }
        audioRecord = null; audioTrack = null
    }

    private fun processAudio() {
        val buffer = ShortArray(minBufferSizeIn)
        while (isRunning) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read <= 0) continue
            val floatBuffer = FloatArray(read) { i -> buffer[i] / 32768.0f }
            notch50Hz.process(floatBuffer)
            bandPass.process(floatBuffer)
            noiseSuppressor.process(floatBuffer, noiseReduction)
            for (i in floatBuffer.indices) {
                var sample = floatBuffer[i]
                sample *= amplification
                sample = tanh(sample * 1.5f) / 1.5f
                floatBuffer[i] = sample
            }
            for (i in 0 until read) {
                val sample = (floatBuffer[i] * 32767.0f).toInt()
                buffer[i] = sample.coerceIn(-32768, 32767).toShort()
            }
            audioTrack?.write(buffer, 0, read)
        }
    }

    private class BandPassFilter(private val lowCutoff: Float, private val highCutoff: Float, sampleRate: Int) {
        private val hp = HighPassFilter(lowCutoff, sampleRate)
        private val lp = LowPassFilter(highCutoff, sampleRate)
        fun process(buffer: FloatArray) { hp.process(buffer); lp.process(buffer) }
    }

    private class HighPassFilter(cutoff: Float, sampleRate: Int) {
        private val alpha = 1.0f / (1.0f + 2.0f * PI.toFloat() * cutoff / sampleRate)
        private var prevInput = 0f; private var prevOutput = 0f
        fun process(buffer: FloatArray) {
            for (i in buffer.indices) {
                val output = alpha * (prevOutput + buffer[i] - prevInput)
                prevInput = buffer[i]; prevOutput = output; buffer[i] = output
            }
        }
    }

    private class LowPassFilter(cutoff: Float, sampleRate: Int) {
        private val alpha = (2.0f * PI.toFloat() * cutoff / sampleRate) / (1.0f + 2.0f * PI.toFloat() * cutoff / sampleRate)
        private var prevOutput = 0f
        fun process(buffer: FloatArray) {
            for (i in buffer.indices) {
                prevOutput += alpha * (buffer[i] - prevOutput)
                buffer[i] = prevOutput
            }
        }
    }

    private class NotchFilter(private val centerFreq: Float, bandwidth: Float, sampleRate: Int) {
        private val w0 = 2.0f * PI.toFloat() * centerFreq / sampleRate
        private val bw = 2.0f * PI.toFloat() * bandwidth / sampleRate
        private val a1 = -2.0f * cos(w0); private val a2 = 1.0f - bw
        private val b1 = -2.0f * cos(w0); private val b2 = 1.0f - bw
        private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
        fun process(buffer: FloatArray) {
            for (i in buffer.indices) {
                val x0 = buffer[i]
                val y0 = x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x0; y2 = y1; y1 = y0; buffer[i] = y0
            }
        }
    }
}
