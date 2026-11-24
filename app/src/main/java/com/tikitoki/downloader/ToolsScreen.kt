package com.tikitoki.downloader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.ProfileAvatarResolver
import com.tikitoki.downloader.data.ProfileIdentity
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechGradient
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ToolsScreen(
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
    Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.tools_subtitle),
        color = TechMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    if (failedCount > 0) {
        Text(stringResource(R.string.tools_failed_count, failedCount), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetryFailed) { Text(stringResource(R.string.tools_retry)) }
            OutlinedButton(onClick = onClearErrors) { Text(stringResource(R.string.tools_clear_errors)) }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors =
            androidx.compose.material3.CardDefaults
                .cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tools_player), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.tools_player_subtitle),
                color = TechMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenPlayer) { Text(stringResource(R.string.tools_open_player)) }
        }
    }
    Text(stringResource(R.string.tools_profile_monitor), style = MaterialTheme.typography.titleLarge)

    OutlinedButton(onClick = onFollowProfile) { Text(stringResource(R.string.tools_follow_profile)) }
    monitored.forEach { profile ->
        val parsedProfile = TikTokLinkParser.parse(profile) as? LinkParseResult.Success
        val username = parsedProfile?.link?.username.orEmpty()
        val activeUsername = (TikTokLinkParser.parse(currentUrl) as? LinkParseResult.Success)?.link?.username
        val initialAvatar =
            profileImage.takeIf {
                username.isNotBlank() &&
                    username.equals(activeUsername, ignoreCase = true)
            }

        // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
        @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
        val identity by produceState<ProfileIdentity?>(
            initialValue = ProfileIdentity(null, initialAvatar),
            key1 = profile,
        ) {
            val found = avatarResolver.resolveIdentity(profile)
            if (found != null) value = found.copy(avatar = found.avatar ?: value?.avatar)
        }
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(TechGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (identity?.avatar != null) {
                            coil.compose.SubcomposeAsyncImage(
                                model =
                                    coil.request.ImageRequest
                                        .Builder(LocalContext.current)
                                        .data(identity?.avatar)
                                        .addHeader("Referer", "https://www.tiktok.com/")
                                        .build(),
                                contentDescription = stringResource(R.string.tools_profile_photo),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = { Text("@", color = Color.White) },
                                loading = { Text("@", color = Color.White) },
                            )
                        } else {
                            Text("@", color = Color.White)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(identity?.displayName ?: "@$username", fontWeight = FontWeight.Bold, maxLines = 1)
                        if (identity?.displayName != null) Text("@$username", color = TechMuted, maxLines = 1)
                    }
                }
                OutlinedButton(onClick = { onUnfollowProfile(profile) }) { Text(stringResource(R.string.tools_unfollow)) }
            }
        }
    }
    if (monitored.isEmpty()) Text(stringResource(R.string.tools_empty_profiles))
    if (message.contains(
            "perfil",
            ignoreCase = true,
        )
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = TechMuted)
    }
    FacebookAccountEntry()
}
