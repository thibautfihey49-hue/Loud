package com.loud.amplifier
import kotlin.math.*

class NoiseSuppressor(private val sampleRate: Int) {
    private var noiseFloor = FloatArray(32) { 0.01f }
    private var noiseEstimated = false

    fun process(buffer: FloatArray, strength: Float) {
        val blockSize = 256
        val numBlocks = buffer.size / blockSize
        for (b in 0 until numBlocks) {
            val start = b * blockSize
            val energy = FloatArray(32) { bin ->
                val from = bin * blockSize / 32
                val to = (bin + 1) * blockSize / 32
                var sum = 0f
                for (i in from until to) sum += buffer[start + i] * buffer[start + i]
                sqrt(sum / (to - from))
            }
            val alpha = if (noiseEstimated) 0.95f else 0.3f
            for (bin in energy.indices) noiseFloor[bin] = alpha * noiseFloor[bin] + (1 - alpha) * energy[bin]
            noiseEstimated = true
            for (i in 0 until blockSize step 4) {
                val bin = (i * 32 / blockSize).coerceIn(0, 31)
                val snr = energy[bin] / (noiseFloor[bin] + 1e-6f)
                val gain = if (snr < 1.5f) (1f - strength * (1f - snr / 1.5f)).coerceIn(0.1f, 1f) else 1f
                for (j in 0 until 4) if (start + i + j < buffer.size) buffer[start + i + j] *= gain
            }
        }
    }
}
