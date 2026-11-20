package com.tikitoki.downloader

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Plays a YouTube video through YouTube's official embedded player. */
class YouTubePlayerActivity : ComponentActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf(VIDEO_ID_PATTERN::matches)
        if (videoId == null) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "YouTube" }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(PLAYER_BACKGROUND)
                setPadding(
                    dp(HORIZONTAL_PADDING_DP),
                    dp(VERTICAL_PADDING_DP),
                    dp(HORIZONTAL_PADDING_DP),
                    dp(VERTICAL_PADDING_DP),
                )
            }
        val heading =
            TextView(this).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = TITLE_SIZE_SP
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(
                    dp(VERTICAL_PADDING_DP),
                    dp(VERTICAL_PADDING_DP),
                    dp(VERTICAL_PADDING_DP),
                    dp(VERTICAL_PADDING_DP),
                )
            }
        webView = createPlayerView()
        root.addView(
            heading,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(webView)
        applyPlayerInsets(root)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        webView.loadDataWithBaseURL(
            "https://$packageName/",
            embedHtml(videoId),
            "text/html",
            "UTF-8",
            null,
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun applyPlayerInsets(root: LinearLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val insetTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(insetTypes)
            view.setPadding(
                dp(HORIZONTAL_PADDING_DP) + bars.left,
                dp(VERTICAL_PADDING_DP) + bars.top,
                dp(HORIZONTAL_PADDING_DP) + bars.right,
                dp(VERTICAL_PADDING_DP) + bars.bottom,
            )
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPlayerView(): WebView =
        WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = !isYouTubeNavigation(request.url)
                }
        }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun isYouTubeNavigation(uri: Uri): Boolean {
        val host = uri.host.orEmpty()
        return uri.scheme == "https" &&
            YOUTUBE_DOMAINS.any { domain ->
                host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)
            }
    }

    private fun embedHtml(videoId: String): String =
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
        <body style="margin:0;background:#000;overflow:hidden">
        <iframe style="position:absolute;inset:0;width:100%;height:100%;border:0"
          src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
          title="YouTube video player"
          allow="autoplay; encrypted-media; picture-in-picture; web-share"
          allowfullscreen></iframe>
        </body></html>
        """.trimIndent()

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "title"

        private const val HORIZONTAL_PADDING_DP = 10
        private const val VERTICAL_PADDING_DP = 8
        private const val TITLE_SIZE_SP = 16f
        private val PLAYER_BACKGROUND = Color.rgb(3, 21, 15)
        private val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")
        private val YOUTUBE_DOMAINS = setOf("youtube.com", "googlevideo.com")
    }
}
