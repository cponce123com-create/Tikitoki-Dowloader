package com.tikitoki.downloader.audio

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class SpectrumAnalyzerTest {
    @Test fun silenceHasNoBars() {
        assertTrue(SpectrumAnalyzer.analyze(FloatArray(SpectrumAnalyzer.WINDOW)).all { it == 0f })
    }

    @Test fun aToneEnergizesItsActualFrequencyBand() {
        val samples = FloatArray(SpectrumAnalyzer.WINDOW) { i ->
            (0.5 * sin(2 * PI * 14 * i / SpectrumAnalyzer.WINDOW)).toFloat()
        }
        val levels = SpectrumAnalyzer.analyze(samples)
        assertEquals(16, levels.size)
        assertEquals(7, levels.indices.maxByOrNull { levels[it] })
        assertTrue(levels[7] > 0.5f)
        assertTrue(levels.all { it in 0f..1f })
    }
}
