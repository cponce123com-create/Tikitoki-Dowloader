package com.tikitoki.downloader.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.ProfileAvatarResolver
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechGradient
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechPrimaryButton
import com.tikitoki.downloader.ui.VideoThumbnail
import com.tikitoki.downloader.data.VideoThumbnailResolver
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen(
    monitored: List<String>,
    failedCount: Int,
    currentUrl: String,
    profileImage: String?,
    avatarResolver: ProfileAvatarResolver,
    message: String,
    onRetryFailed: () -> Unit,
    onClearErrors: () -> Unit,
    onOpenPlayer: () -> Unit,
    onFollowProfile: () -> Unit,
    onUnfollowProfile: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Herramientas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text("Perfiles monitorizados", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            if (monitored.isEmpty()) {
                Text("No hay perfiles en seguimiento", color = TechMuted)
            } else {
                monitored.forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(profile, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        OutlinedButton(onClick = { onUnfollowProfile(profile) }) { Text("Dejar de seguir") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFollowProfile) { Text("Seguir perfil actual") }
                OutlinedButton(onClick = onOpenPlayer) { Text("Reproductor") }
            }

            if (failedCount > 0) {
                Text("Descargas fallidas: $failedCount", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetryFailed) { Text("Reintentar fallidas") }
                    OutlinedButton(onClick = onClearErrors) { Text("Limpiar errores") }
                }
            }

            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}