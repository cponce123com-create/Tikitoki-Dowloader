# Tikitoki Downloader

Aplicación Android independiente, escrita en **Kotlin y Jetpack Compose**, inspirada en la experiencia de organización de descargas por lotes de 4K Tokkit.

## Estado (v0.1.0)

**Implementado**
- Proyecto Android y pantalla principal oscura.
- Pegado de URL y recepción de enlaces compartidos desde Android.
- Validación del dominio HTTPS de TikTok y reconocimiento de enlaces de perfil, video y URL corta.
- Pantallas de Inicio, Biblioteca y Acerca de.
- Pruebas unitarias del analizador.
- GitHub Actions para compilar y publicar como artefacto un APK de depuración.

**No implementado todavía**
- Consulta real de publicaciones y paginación por perfil.
- Resolución de enlaces cortos.
- Extracción y selección de formatos/resoluciones.
- Descargas masivas, gestión de archivos, cola y biblioteca persistente.
- Versiones sin marca de agua: únicamente cuando estén disponibles y su descarga esté autorizada.

No se muestran videos ficticios ni se anuncia disponibilidad de descargas que no hayan sido verificadas.

## Compilar

Abrir en Android Studio con JDK 17 y Android SDK 35. Requiere Gradle 8.10.2. Si el IDE necesita un wrapper local, ejecutar una vez `gradle wrapper --gradle-version 8.10.2` desde el proyecto y añadir los archivos generados. El CI usa Gradle instalado directamente para no depender del wrapper.

Compilación y pruebas: `gradle testDebugUnitTest assembleDebug`.

El APK debug generado por GitHub Actions puede descargarse desde los **Artifacts** de una ejecución que haya finalizado correctamente.

## Arquitectura y siguiente fase

La app separa el analizador `domain/TikTokLinkParser.kt` de la interfaz `MainActivity.kt`. La siguiente fase introducirá interfaces de proveedor de videos, modelos de formato, un gestor Android DownloadManager, Room para el historial y pruebas con fuentes reales permitidas. Evitaremos depender de APK hackeados, credenciales privadas o servicios no verificados.

## Uso responsable

Descarga únicamente contenido propio, con autorización o cuya descarga esté permitida por la plataforma y el titular.
