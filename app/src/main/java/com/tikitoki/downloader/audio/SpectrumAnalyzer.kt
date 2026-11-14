package com.tikitoki.downloader.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small windowed DFT of real mono PCM samples. No generated or interpolated levels. */
internal object SpectrumAnalyzer {
    const val WINDOW = 256
    private val bins = intArrayOf(1, 2, 3, 4, 6, 8, 11, 14, 18, 22, 28, 34, 40, 48, 56, 64)
    private val window = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2.0 * PI * it / (WINDOW - 1)) }
    private val normalization = 2.0 / window.sum()
    private val cosine = Array(bins.size) { band -> DoubleArray(WINDOW) { i -> cos(2.0 * PI * bins[band] * i / WINDOW) } }
    private val sine = Array(bins.size) { band -> DoubleArray(WINDOW) { i -> sin(2.0 * PI * bins[band] * i / WINDOW) } }

    fun analyze(samples: FloatArray): List<Float> {
        require(samples.size == WINDOW)
        return bins.indices.map { band ->
            var real = 0.0
            var imaginary = 0.0
            for (i in 0 until WINDOW) {
                val sample = samples[i].coerceIn(-1f, 1f) * window[i]
                real += sample * cosine[band][i]
                imaginary -= sample * sine[band][i]
            }
            sqrt((sqrt(real * real + imaginary * imaginary) * normalization * 3.0).coerceIn(0.0, 1.0)).toFloat()
        }
    }
}
