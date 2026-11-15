package com.tikitoki.downloader.data

/** Extract actual percentages from yt-dlp progress lines, including lines without ETA. */
internal object DownloadProgressParser {
    private val ytDlp = Regex("""\[download\]\s+(\d+(?:\.\d+)?)%""", RegexOption.IGNORE_CASE)
    private val aria = Regex("""\[#\w{6}.*?\((\d+(?:\.\d+)?)%\)""")
    private val rate = Regex("""(?:\bat\s+|\bDL:)(\d+(?:\.\d+)?\s*(?:[KMGT]i?B|B)/s)""", RegexOption.IGNORE_CASE)
    fun speed(line: String): String? = rate.find(line)?.groupValues?.get(1)?.replace(" ", "")

    fun percent(line: String): Int? {
        val number = ytDlp.find(line)?.groupValues?.get(1) ?: aria.find(line)?.groupValues?.get(1) ?: return null
        val value = number.toFloatOrNull() ?: return null
        return value.toInt().coerceIn(0, 100)
    }
}

