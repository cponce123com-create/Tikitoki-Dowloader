# Tikitoki Downloader

Aplicación nativa Android en **Kotlin + Jetpack Compose**, inspirada en 4K Tokkit, Seal y YTDLnis. Repositorio: `cponce123com-create/Tikitoki-Dowloader` (el nombre del repositorio conserva la grafía original).

## Funcionalidades implementadas en código (v0.3.0)

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
