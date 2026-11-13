package com.tikitoki.downloader.data

/** Distinguishes extraction from file transfer and processing. */
enum class EngineCapability { EXTRACT, TRANSFER, PROCESS }

data class EngineDescriptor(
    val id: String,
    val name: String,
    val capabilities: Set<EngineCapability>,
    val installed: Boolean,
    val description: String,
)

object EngineRegistry {
    val engines = listOf(
        EngineDescriptor("yt-dlp", "yt-dlp", setOf(EngineCapability.EXTRACT, EngineCapability.TRANSFER),
            true, "Extractor y descargador principal"),
        EngineDescriptor("ffmpeg", "FFmpeg", setOf(EngineCapability.PROCESS, EngineCapability.TRANSFER),
            true, "Procesamiento multimedia; requiere URL multimedia válida"),
        EngineDescriptor("gallery-dl", "gallery-dl", setOf(EngineCapability.EXTRACT, EngineCapability.TRANSFER),
            false, "Requiere integración Python Android"),
        EngineDescriptor("newpipe", "NewPipe Extractor", setOf(EngineCapability.EXTRACT),
            true, "Extractor de metadatos para videos individuales de YouTube; no descarga archivos"),
        EngineDescriptor("aria2", "aria2", setOf(EngineCapability.TRANSFER),
            false, "Requiere binarios Android")
    )
    fun available(capability: EngineCapability) =
        engines.filter { it.installed && capability in it.capabilities }

    fun fallbackExtractors(exclude: String = "yt-dlp"): List<EngineDescriptor> =
        available(EngineCapability.EXTRACT).filterNot { it.id == exclude }

    fun statusMessage(): String =
        if (fallbackExtractors().isEmpty()) {
            "No hay extractores alternativos instalados. FFmpeg no extrae páginas web."
        } else {
            "Extractores alternativos: " + fallbackExtractors().joinToString { it.name }
        }
}
