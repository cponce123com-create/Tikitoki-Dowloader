# Tikitoki Downloader

Aplicación nativa Android en **Kotlin + Jetpack Compose**, inspirada en 4K Tokkit, Seal y YTDLnis. Repositorio: `cponce123com-create/Tikitoki-Dowloader` (el nombre del repositorio conserva la grafía original).

## Funcionalidades implementadas en código (v0.5.0; compatibilidad multiplataforma experimental)

- Compartir o pegar enlaces de TikTok; validar enlaces y rechazar dominios falsificados.
- Usar **yt-dlp mediante youtubedl-android** para consultar videos individuales y probar la enumeración de perfiles públicos.
- Obtener formatos realmente disponibles por video y permitir seleccionar uno; descarga por lotes con la mejor calidad disponible.
- Miniaturas cuando el extractor las proporcione; búsqueda en la lista y selección múltiple.
- **Room** como base de datos local de historial, estados y ubicación de archivos descargados.
- **WorkManager** para una cola persistente, ejecutada de forma serial, con servicio en primer plano y aviso de descarga.
- Pausa, reanudación y reintentos; estados de error y progreso por video.
- Detección de IDs duplicados para no repetir descargas completadas o pendientes.
- Archivos visibles en `Descargas/TikitokiDownloader/`, publicados con MediaStore en Android 10+.
- GitHub Actions compila el APK debug y ejecuta pruebas del reconocedor de enlaces.

## Limitaciones

TikTok puede rechazar la recopilación de perfiles, incluso cuando sus videos son públicos. No se garantiza poder descargar todos los videos, la resolución máxima ni versiones sin marca de agua. No hay APKs hackeados, robo de cookies ni elusión de restricciones de autenticación. Se requieren pruebas en un dispositivo Android real con material propio o autorizado. Las opciones manuales de calidad se ofrecen por video; la cola aplica calidad automática al lote.

## Compilación

Abrir en Android Studio (JDK 17, SDK 35). Desde GitHub, pestaña **Actions > Android CI**: tras una ejecución exitosa, descargar el artifact **Tikitoki-Downloader-debug**.

## Licencias

El motor [youtubedl-android](https://github.com/yausername/youtubedl-android) usa GPL-3.0, con requisitos de divulgación de código fuente al redistribuir aplicaciones que lo incorporan. Evaluar obligaciones legales antes de publicar.

## Próximas validaciones

Pruebas manuales en Android 8-16, extracción en TikTok en tiempo real, compatibilidad con perfiles grandes, medios sin marca cuando sean proporcionados por el origen y tratamiento de restricciones de plataforma.

## Privacidad y seguridad (política del proyecto)

- **Sin anuncios, analítica, SDK de publicidad ni seguimiento de usuarios.** No se requiere cuenta propia ni servidor de Tikitoki para descargar.
- El historial de descargas se guarda localmente mediante Room; los archivos se guardan en el dispositivo. No se envía el historial a un servidor de Tikitoki.
- La aplicación realiza conexiones necesarias a las plataformas de origen, a recursos de miniaturas y, para actualizar el extractor yt-dlp, al proveedor de actualizaciones. Esas conexiones pueden revelar información de red a terceros; **sin telemetría propia** no significa **sin tráfico de red**.
- No se admiten cookies de sesión ni credenciales de usuario. No se ejecutan comandos de shell proporcionados por el usuario.
- Los enlaces compartidos desde otras apps deben tratarse como datos no confiables. La validación de URL no sustituye el endurecimiento pendiente del receptor de Intents ni la validación de formatos y metadatos.
- Solo deben descargarse contenidos propios o autorizados. No se prometen descargas de contenido privado, protegido o restringido.
- Antes de cada lanzamiento, revisar permisos, dependencias, SDKs, destinos de red y declaraciones de privacidad para verificar que esta política continúa cumpliéndose.

## Política de actualización del motor

- La integración Android declarada en `app/build.gradle.kts` utiliza `io.github.junkfood02.youtubedl-android:library:0.18.1` y `ffmpeg:0.18.1` (GPL-3.0). La versión del wrapper **no demuestra** qué versión exacta de yt-dlp está ejecutándose en cada dispositivo.
- `TikTokVideoSource.ensureReady()` intenta actualizar yt-dlp desde el canal `STABLE`, con un intervalo de intento de 24 horas registrado en preferencias. Los fallos no detienen la aplicación; actualmente se registra el intento incluso si falla.
- La actualización del extractor es distinta de la actualización de la biblioteca Android y de FFmpeg; estas últimas requieren actualizar dependencias y generar otro APK.
- Política recomendada antes de producción: registrar versión efectiva y fecha del último éxito; verificar procedencia/integridad del paquete; no sustituir un motor funcional si falla la actualización; permitir diagnóstico y recuperación; probar cambios en CI y dispositivos físicos antes de distribuir.
- No se habilitarán argumentos arbitrarios de yt-dlp, archivos de configuración externos ni ejecutables descargados desde enlaces no verificados.

## Licencias y atribuciones

Tikitoki conserva su obligación de distribución compatible con **GPL-3.0**. Se integra `youtubedl-android` (wrapper Android para yt-dlp) y su módulo FFmpeg; consultar también las licencias de yt-dlp, FFmpeg y bibliotecas transitivas al redistribuir. La inspiración en Seal, YTDLnis, dvd, OmniDownloader, ClipSave y PowerTube no autoriza copiar su código, marcas o recursos sin revisar sus licencias.
