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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf(VIDEO_ID_PATTERN::matches)
        if (videoId == null) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "YouTube" }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(3, 21, 15))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val heading = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !isYouTubeNavigation(request.url)
            }
        }
        root.addView(heading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(webView)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(dp(10) + bars.left, dp(8) + bars.top, dp(10) + bars.right, dp(8) + bars.bottom)
            insets
        }
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

    private fun embedHtml(videoId: String): String = """
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

        private val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")
        private val YOUTUBE_DOMAINS = setOf("youtube.com", "googlevideo.com")
    }
}
