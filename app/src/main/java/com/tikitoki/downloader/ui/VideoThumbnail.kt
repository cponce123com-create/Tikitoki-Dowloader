package com.tikitoki.downloader.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.ProfileVideo
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoThumbnail(
    video: ProfileVideo,
    thumbnailResolver: VideoThumbnailResolver,
    modifier: Modifier = Modifier,
    deferLookup: Boolean = false,
) {
    val context = LocalContext.current
    val thumbnailUri by thumbnailResolver.thumbnail(video.id).collectAsState(initial = null)
    val platform = MediaLinkParser.parse(video.url)?.platform

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                if (platform == MediaPlatform.TIKTOK) {
                    9f / 16f
                } else {
                    16f / 9f
                },
            )
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUri)
                    .build(),
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        if (platform == MediaPlatform.TIKTOK) {
                            Color(0xFF102B23)
                        } else {
                            Color(0xFF031914)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            }
        }

        if (deferLookup) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
            ) {
                Text("Cargando…", color = Color.White, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
            }
        }
    }
}