package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.components.StreamLabel
import com.github.damontecres.wholphin.ui.data.calculateAspectRatio
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import com.github.damontecres.wholphin.ui.util.StreamFormatting.concatWithSpace
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatAudioCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatVideoRange
import com.github.damontecres.wholphin.ui.util.StreamFormatting.resolutionString
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod

@Composable
fun PlaybackStreamBadges(
    currentPlayback: CurrentPlayback?,
    modifier: Modifier = Modifier,
) {
    if (currentPlayback == null) return

    val context = LocalContext.current
    val mediaStreams = currentPlayback.mediaSourceInfo.mediaStreams.orEmpty()

    val videoStream = remember(mediaStreams) {
        mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
    }
    val audioStream = remember(mediaStreams) {
        mediaStreams.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault }
            ?: mediaStreams.firstOrNull { it.type == MediaStreamType.AUDIO }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val playMethodLabel = remember(currentPlayback.playMethod) {
            when (currentPlayback.playMethod) {
                PlayMethod.DIRECT_PLAY -> "Direct"
                PlayMethod.DIRECT_STREAM -> "Direct Stream"
                PlayMethod.TRANSCODE -> "Transcode"
            }
        }
        StreamLabel(text = playMethodLabel)

        val resolutionWithHdr = remember(videoStream) {
            videoStream?.let { stream ->
                val width = stream.width
                val height = stream.height
                val resName =
                    if (width != null && height != null) {
                        resolutionString(width, height, stream.isInterlaced)
                    } else {
                        null
                    }
                val range =
                    formatVideoRange(
                        context,
                        stream.videoRange,
                        stream.videoRangeType,
                        stream.videoDoViTitle,
                    )
                resName.concatWithSpace(range)
            }
        }
        resolutionWithHdr?.let {
            StreamLabel(text = it)
        }

        videoStream?.codec?.uppercase()?.let {
            StreamLabel(text = it)
        }

        val aspectRatio = remember(videoStream) {
            videoStream?.let { stream ->
                val width = stream.width
                val height = stream.height
                if (width != null && height != null) {
                    calculateAspectRatio(width, height)
                } else {
                    null
                }
            }
        }
        aspectRatio?.let {
            StreamLabel(text = it)
        }

        val audioInfo = remember(audioStream) {
            audioStream?.let { stream ->
                formatAudioCodecWithChannels(context, stream)
            }
        }
        audioInfo?.let {
            StreamLabel(text = it)
        }
    }
}

private fun formatAudioCodecWithChannels(
    context: android.content.Context,
    stream: MediaStream,
): String? {
    val codec = formatAudioCodec(context, stream.codec, stream.profile)
    val channels = stream.channelLayout
    return listOfNotNull(codec, channels).takeIf { it.isNotEmpty() }?.joinToString(" ")
}
