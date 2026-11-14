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

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount > 0)
            inputAudioFormat else AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val bytesPerFrame = channels * 2
        val now = System.nanoTime()
        if (channels > 0 && inputBuffer.remaining() / bytesPerFrame >= SpectrumAnalyzer.WINDOW &&
            now - lastCapture >= 80_000_000L) {
            val read = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val start = read.position()
            // Use the loudest channel so stereo signals with opposite phases do not cancel.
            var chosenChannel = 0
            var highestEnergy = -1.0
            for (channel in 0 until channels) {
                var energy = 0.0
                for (frame in 0 until SpectrumAnalyzer.WINDOW) {
                    val sample = read.getShort(start + frame * bytesPerFrame + channel * 2).toDouble()
                    energy += sample * sample
                }
                if (energy > highestEnergy) { highestEnergy = energy; chosenChannel = channel }
            }
            val samples = FloatArray(SpectrumAnalyzer.WINDOW) { frame ->
                read.getShort(start + frame * bytesPerFrame + chosenChannel * 2).toFloat() / 32768f
            }
            PlaybackSpectrum.publish(SpectrumAnalyzer.analyze(samples))
            lastCapture = now
        }
        // Forward the input unchanged; the analyzer reads a separate view.
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    override fun onFlush() {
        lastCapture = 0L
        PlaybackSpectrum.clear()
    }

    override fun onReset() { PlaybackSpectrum.clear() }
}
