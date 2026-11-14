package com.tikitoki.downloader.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** In-process display state only; no audio samples or microphone data are stored. */
internal object PlaybackSpectrum {
    private val mutable = MutableStateFlow(List(16) { 0f })
    val levels: StateFlow<List<Float>> = mutable
    fun publish(value: List<Float>) { mutable.value = value }
    fun clear() { mutable.value = List(16) { 0f } }
}

@OptIn(UnstableApi::class)
internal class SpectrumAudioProcessor : BaseAudioProcessor() {
    private var lastCapture = 0L
    private var capturedFrames = 0
    private var channelWindows: Array<FloatArray> = emptyArray()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount > 0)
            inputAudioFormat else AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val bytesPerFrame = channels * 2
        val now = System.nanoTime()
        if (channels > 0 && now - lastCapture >= 80_000_000L) {
            if (channelWindows.size != channels) {
                channelWindows = Array(channels) { FloatArray(SpectrumAnalyzer.WINDOW) }
                capturedFrames = 0
            }
            val read = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val start = read.position()
            val available = inputBuffer.remaining() / bytesPerFrame
            val count = minOf(available, SpectrumAnalyzer.WINDOW - capturedFrames)
            for (frame in 0 until count) {
                for (channel in 0 until channels) {
                    channelWindows[channel][capturedFrames + frame] =
                        read.getShort(start + frame * bytesPerFrame + channel * 2).toFloat() / 32768f
                }
            }
            capturedFrames += count
            if (capturedFrames == SpectrumAnalyzer.WINDOW) {
                // Use the loudest channel so opposite stereo phases do not cancel.
                val loudest = channelWindows.maxByOrNull { samples ->
                    samples.sumOf { sample -> (sample * sample).toDouble() }
                }!!
                PlaybackSpectrum.publish(SpectrumAnalyzer.analyze(loudest))
                capturedFrames = 0
                lastCapture = now
            }
        }
        // Forward the input unchanged; the analyzer reads a separate view.
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    override fun onFlush() {
        lastCapture = 0L
        capturedFrames = 0
        channelWindows = emptyArray()
        PlaybackSpectrum.clear()
    }

    override fun onReset() { capturedFrames = 0; channelWindows = emptyArray(); PlaybackSpectrum.clear() }
}
