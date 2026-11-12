package com.tikitoki.downloader.data

import org.json.JSONArray
import org.json.JSONObject

/** Exports metadata, never the media bytes. */
object LibraryExport {
    fun toJson(records: List<DownloadRecord>): String {
        val items = JSONArray()
        records.forEach { record ->
            items.put(JSONObject().apply {
                put("videoId", record.videoId)
                put("url", record.videoUrl)
                put("title", record.title)
                put("status", record.status)
                put("progress", record.progress)
                put("savedUri", record.savedUri ?: JSONObject.NULL)
                put("createdAt", record.createdAt)
                put("updatedAt", record.updatedAt)
            })
        }
        return JSONObject().put("schemaVersion", 1).put("downloads", items).toString(2)
    }

    fun toCsv(records: List<DownloadRecord>): String {
        fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        return buildString {
            appendLine("videoId,url,title,status,progress,savedUri,createdAt,updatedAt")
            records.forEach { r ->
                appendLine(listOf(r.videoId, r.videoUrl, r.title, r.status, r.progress.toString(),
                    r.savedUri.orEmpty(), r.createdAt.toString(), r.updatedAt.toString())
                    .joinToString(",") { quote(it) })
            }
        }
    }
}
