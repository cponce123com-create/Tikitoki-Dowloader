package com.tikitoki.downloader.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class SpectrumAudioProcessorTest {
    @Test fun oppositeStereoPhasesStillProduceRealLevels() {
        val processor = SpectrumAudioProcessor()
        processor.configure(AudioFormat(8000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        repeat(256) { i ->
            val sample = (sin(2 * PI * 14 * i / 256) * 16000).toInt().toShort()
            input.putShort(sample)
            input.putShort((-sample.toInt()).toShort())
        }
        input.flip()
        processor.queueInput(input)
        assertTrue(PlaybackSpectrum.levels.value[7] > 0.5f)
        processor.reset()
    }

    @Test fun analysisDoesNotModifyAudioBytes() {
        val processor = SpectrumAudioProcessor()
        processor.configure(AudioFormat(8000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(512).order(ByteOrder.LITTLE_ENDIAN)
        repeat(256) { input.putShort((it * 40).toShort()) }
        input.flip()
        val original = ByteArray(input.remaining())
        input.duplicate().get(original)
        processor.queueInput(input)
        val output = processor.output
        val actual = ByteArray(output.remaining())
        output.get(actual)
        assertArrayEquals(original, actual)
        assertTrue(PlaybackSpectrum.levels.value.any { it > 0f })
        processor.reset()
        assertTrue(PlaybackSpectrum.levels.value.all { it == 0f })
    }
}
