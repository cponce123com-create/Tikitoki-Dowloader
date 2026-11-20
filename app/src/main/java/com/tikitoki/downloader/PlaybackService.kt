package com.tikitoki.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionToken
import com.tikitoki.downloader.audio.PlaybackSpectrum
import com.tikitoki.downloader.audio.SpectrumAudioProcessor
import com.tikitoki.downloader.data.DiagnosticHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MediaSessionService for background audio playback.
 * Implements foreground service with wake lock and WiFi lock for continuous playback
 * when screen is off (Vanced/Revanced style background playback).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "playback_channel"
        private const val CHANNEL_NAME = "Reproducción en segundo plano"
    }

    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var notificationManager: NotificationManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        acquireWifiLock()

        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(SpectrumAudioProcessor()))
                    .build()
        }
        player = ExoPlayer.Builder(this, renderers)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true)
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        updateNotification()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        DiagnosticHistory.record(this@PlaybackService, error.message)
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateNotification()
                    }
                })
            }

        session = MediaSession.Builder(this, player!!)
            .setId("tikitoki_playback_session")
            .build()

        // Start foreground service with initial notification
        startForeground(NOTIFICATION_ID, buildNotification(), FOREGROUND_SERVICE_MEDIA_PLAYBACK)
    }

    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación de reproducción de música en segundo plano"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tikitoki:PlaybackWakeLock")
        wakeLock?.acquire()
    }

    private fun acquireWifiLock() {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "Tikitoki:PlaybackWifiLock")
        wifiLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.run {
            if (isHeld) release()
            wakeLock = null
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.run {
            if (isHeld) release()
            wifiLock = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        stopForeground(true)
        scope.coroutineContext.cancel()
        player?.run { removeListener(); release() }
        player = null
        session?.run { player.release(); release() }
        session = null
        releaseWakeLock()
        releaseWifiLock()
        PlaybackSpectrum.clear()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val session = this.session ?: return buildMinimalNotification()
        val controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync().getOrNull()
        val metadata = controller?.mediaMetadata
        val title = metadata?.title?.toString() ?: "Reproduciendo"
        val artist = metadata?.artist?.toString() ?: "Música local"
        val artwork = metadata?.artworkUri?.let { Uri.parse(it) }
        val isPlaying = controller?.isPlaying == true

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = Intent(this, PlaybackService::class.java).apply {
            action = if (isPlaying) "pause" else "play"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, PlaybackService::class.java).apply { action = "next" }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(this, PlaybackService::class.java).apply { action = "prev" }
        val prevPendingIntent = PendingIntent.getService(
            this, 3, prevIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val closeIntent = Intent(this, PlaybackService::class.java).apply { action = "stop" }
        val closePendingIntent = PendingIntent.getService(
            this, 4, closeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.bajape_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork?.let { androidx.media3.common.util.Util.loadBitmap(this, it, 128, 128) })
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(closePendingIntent))
            .addAction(NotificationCompat.Action.Builder(
                androidx.core.graphics.drawable.IconCompat.createWithResource(this, androidx.media3.ui.R.drawable.exo_controls_previous),
                "Anterior",
                prevPendingIntent
            ).build())
            .addAction(NotificationCompat.Action.Builder(
                androidx.core.graphics.drawable.IconCompat.createWithResource(
                    this,
                    if (isPlaying) androidx.media3.ui.R.drawable.exo_controls_pause else androidx.media3.ui.R.drawable.exo_controls_play
                ),
                if (isPlaying) "Pausar" else "Reproducir",
                playPausePendingIntent
            ).build())
            .addAction(NotificationCompat.Action.Builder(
                androidx.core.graphics.drawable.IconCompat.createWithResource(this, androidx.media3.ui.R.drawable.exo_controls_next),
                "Siguiente",
                nextPendingIntent
            ).build())
            .build()

        return notification
    }

    private fun buildMinimalNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.bajape_launcher)
            .setContentTitle("Tikitoki Downloader")
            .setContentText("Reproduciendo en segundo plano")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    // Handle media button actions from notification
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "play" -> player?.play()
            "pause" -> player?.pause()
            "next" -> player?.seekToNextMediaItem()
            "prev" -> player?.seekToPreviousMediaItem()
            "stop" -> {
                player?.stop()
                stopSelf()
            }
        }
        updateNotification()
        return START_STICKY
    }
}


