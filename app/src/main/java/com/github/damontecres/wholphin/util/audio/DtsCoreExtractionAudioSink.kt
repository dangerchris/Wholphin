@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util.audio

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * An [AudioSink] wrapper that extracts DTS Core from DTS-HD Master Audio streams.
 *
 * DTS-HD MA contains an embedded backwards-compatible DTS Core that many devices can
 * passthrough even when they cannot handle the full DTS-HD extension. This sink
 * intercepts the encoded audio data and strips the HD extension, outputting only
 * the DTS Core for passthrough.
 *
 * This works at the AudioSink level because passthrough audio bypasses the AudioProcessor chain.
 */
@UnstableApi
class DtsCoreExtractionAudioSink(
    private val delegate: AudioSink,
) : AudioSink by delegate {

    companion object {
        private const val DTS_SYNC_WORD = 0x7FFE8001
        private const val DTS_HD_SYNC_WORD = 0x64582025
        private const val MIN_DTS_FRAME_SIZE = 96
        private const val MAX_DTS_CORE_FRAME_SIZE = 16384
        private const val TAG = "DtsCoreExtraction"
    }

    private var isDtsHdPassthrough = false
    private var extractedCoreFrames = 0L
    private var skippedHdExtensions = 0L

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        val isDtsHd = inputFormat.sampleMimeType == "audio/vnd.dts.hd"
        isDtsHdPassthrough = isDtsHd

        extractedCoreFrames = 0
        skippedHdExtensions = 0

        if (isDtsHd) {
            // DTS Core is limited to 5.1 / 48kHz, even if the DTS-HD stream advertises more.
            val dtsChannelCount = minOf(inputFormat.channelCount, 6)
            val dtsSampleRate = if (inputFormat.sampleRate > 48000) 48000 else inputFormat.sampleRate
            val dtsFormat =
                inputFormat
                    .buildUpon()
                    .setSampleMimeType("audio/vnd.dts")
                    .setChannelCount(dtsChannelCount)
                    .setSampleRate(dtsSampleRate)
                    .build()
            Timber.i(
                "$TAG: Converting DTS-HD to DTS format for passthrough - " +
                    "original=${inputFormat.sampleMimeType}, converted=${dtsFormat.sampleMimeType}, " +
                    "channels=${inputFormat.channelCount}->${dtsChannelCount}, " +
                    "sampleRate=${inputFormat.sampleRate}->${dtsSampleRate}",
            )
            delegate.configure(dtsFormat, specifiedBufferSize, outputChannels)
        } else {
            Timber.v("$TAG: Not DTS-HD, passthrough without modification - mimeType=${inputFormat.sampleMimeType}")
            delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
        }
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!isDtsHdPassthrough || !buffer.hasRemaining()) {
            return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        val startPos = buffer.position()
        val originalLimit = buffer.limit()

        if (originalLimit - startPos >= 8) {
            val syncWord = readInt32BE(buffer, startPos)

            if (syncWord == DTS_SYNC_WORD) {
                val coreFrameSize = extractCoreFrameSize(buffer, startPos)

                if (coreFrameSize in MIN_DTS_FRAME_SIZE..MAX_DTS_CORE_FRAME_SIZE) {
                    val coreEnd = startPos + coreFrameSize

                    if (coreEnd <= originalLimit && coreEnd + 4 <= originalLimit) {
                        val nextSync = readInt32BE(buffer, coreEnd)
                        if (nextSync == DTS_HD_SYNC_WORD) {
                            buffer.limit(coreEnd)
                            val result =
                                delegate.handleBuffer(
                                    buffer,
                                    presentationTimeUs,
                                    encodedAccessUnitCount,
                                )
                            buffer.limit(originalLimit)

                            if (result) {
                                buffer.position(originalLimit)
                                extractedCoreFrames++
                                skippedHdExtensions++

                                if (extractedCoreFrames == 1L || extractedCoreFrames % 500 == 0L) {
                                    Timber.i("$TAG: Extracted core #$extractedCoreFrames, size=$coreFrameSize")
                                }
                                if (extractedCoreFrames % 1000 == 0L) {
                                    Timber.i(
                                        "$TAG: Stats - frames=$extractedCoreFrames, hdSkipped=$skippedHdExtensions, " +
                                            "presentationTimeUs=$presentationTimeUs",
                                    )
                                }
                            }

                            return result
                        }
                    }
                }
            }
        }

        return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    private fun readInt32BE(
        buffer: ByteBuffer,
        position: Int,
    ): Int {
        if (position + 4 > buffer.limit()) return 0
        val b0 = buffer.get(position).toInt() and 0xFF
        val b1 = buffer.get(position + 1).toInt() and 0xFF
        val b2 = buffer.get(position + 2).toInt() and 0xFF
        val b3 = buffer.get(position + 3).toInt() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun extractCoreFrameSize(
        buffer: ByteBuffer,
        offset: Int,
    ): Int {
        if (offset + 8 > buffer.limit()) return -1

        val byte5 = buffer.get(offset + 5).toInt() and 0xFF
        val byte6 = buffer.get(offset + 6).toInt() and 0xFF
        val byte7 = buffer.get(offset + 7).toInt() and 0xFF
        val fsize = ((byte5 and 0x03) shl 12) or (byte6 shl 4) or ((byte7 and 0xF0) shr 4)
        return fsize + 1
    }

    override fun flush() {
        delegate.flush()
    }

    override fun reset() {
        isDtsHdPassthrough = false
        extractedCoreFrames = 0
        skippedHdExtensions = 0
        delegate.reset()
        Timber.d("$TAG: Reset")
    }

    override fun release() {
        Timber.i(
            "$TAG: Release - extracted $extractedCoreFrames core frames, " +
                "skipped $skippedHdExtensions HD extensions",
        )
        delegate.release()
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes) = delegate.setAudioAttributes(audioAttributes)

    override fun setAudioSessionId(audioSessionId: Int) = delegate.setAudioSessionId(audioSessionId)

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = delegate.setPreferredDevice(audioDeviceInfo)

    override fun enableTunnelingV21() = delegate.enableTunnelingV21()

    override fun disableTunneling() = delegate.disableTunneling()

    override fun play() = delegate.play()

    override fun pause() = delegate.pause()

    override fun handleDiscontinuity() = delegate.handleDiscontinuity()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = delegate.setPlaybackParameters(playbackParameters)

    override fun getPlaybackParameters(): PlaybackParameters = delegate.playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = delegate.setSkipSilenceEnabled(skipSilenceEnabled)

    override fun getSkipSilenceEnabled(): Boolean = delegate.skipSilenceEnabled

    override fun setVolume(volume: Float) = delegate.setVolume(volume)

    override fun getFormatSupport(format: Format): @C.FormatSupport Int = delegate.getFormatSupport(format)

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = delegate.getCurrentPositionUs(sourceEnded)

    override fun hasPendingData(): Boolean = delegate.hasPendingData()

    override fun isEnded(): Boolean = delegate.isEnded

    override fun playToEndOfStream() = delegate.playToEndOfStream()

    override fun setListener(listener: AudioSink.Listener) = delegate.setListener(listener)
}
