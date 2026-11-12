# Tikitoki Downloader

Aplicación Android nativa con **Kotlin + Jetpack Compose**. Proyecto en desarrollo inspirado en la experiencia de 4K Tokkit y otras herramientas libres.

## Estado v0.2 (motor yt-dlp integrado, pendiente de prueba en teléfono)

- Reconocimiento y recepción de enlaces de TikTok.
- Integración directa del motor `youtubedl-android` (yt-dlp + FFmpeg).
- Intento de listar videos de un perfil usando `--flat-playlist --dump-single-json`.
- Consulta real de formatos MP4 progresivos de videos individuales.
- Descarga secuencial por lotes de videos seleccionados, con progreso y errores.
- Carpeta `Downloads/TikitokiDownloader/`.
- Compilación en GitHub Actions (`Android CI`).

**Limitaciones importantes:** TikTok puede bloquear el listado completo de un perfil, aplicar limitaciones o no ofrecer una versión descargable sin marca de agua. La aplicación no garantiza obtener todos los videos de cualquier cuenta. No altera archivos para eliminar marcas de agua. La disponibilidad de formatos viene del extractor y no de valores inventados. Las descargas en segundo plano, la reanudación y la biblioteca persistente todavía no están implementadas. No se han realizado pruebas en dispositivos reales.

## Compilación

Abre el repositorio en Android Studio con Java 17 y Android SDK 35. Alternativamente, en `Actions > Android CI` descarga el APK debug desde Artifacts de una ejecución **exitosa**.

## Dependencia externa

Usamos [youtubedl-android](https://github.com/yausername/youtubedl-android) bajo GPL-3.0. Su integración trae obligaciones de distribución de código fuente y licencias que deben cumplirse antes de publicar la aplicación.

## Uso permitido

Descarga únicamente publicaciones propias, autorizadas o cuya descarga esté permitida por el titular y la plataforma. Nunca introducir credenciales en bots de terceros ni utilizar APK modificados de origen desconocido.
