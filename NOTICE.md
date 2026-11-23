# Avisos y licencias de terceros

Baja.pe incorpora o utiliza componentes de terceros. Sus derechos de autor y marcas pertenecen a sus respectivos titulares. Esta lista identifica dependencias principales; no sustituye el inventario de dependencias transitivas ni la revisión de los binarios concretos que se distribuyan.

| Componente | Función | Licencia y fuente |
| --- | --- | --- |
| gallery-dl 1.32.21 | Extracción de imágenes mediante un proceso separado | [GPL-2.0-only](https://github.com/mikf/gallery-dl/blob/v1.32.21/LICENSE). Licencias de sus dependencias incluidas en el archivo del motor. |
| yt-dlp | Extracción de metadatos y medios | [The Unlicense](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE); sus componentes y dependencias pueden tener otras licencias. |
| youtubedl-android | Integración Android del extractor, incluida la variante `io.github.junkfood02` declarada en el proyecto | [GPL-3.0](https://github.com/junkfood02/youtubedl-android/blob/master/LICENSE). |
| FFmpeg | Procesamiento multimedia del módulo FFmpeg | [LGPL-2.1 o posterior; GPL-2.0 o posterior según opciones y componentes habilitados](https://ffmpeg.org/legal.html). Verificar la configuración y licencias exactas de cada binario distribuido. |
| NewPipeExtractor | Extracción alternativa de información | [GPL-3.0](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/LICENSE). |
| Coil | Carga de imágenes y fotogramas | [Apache-2.0](https://github.com/coil-kt/coil/blob/2.7.0/LICENSE.txt). |
| Jetpack Compose | Interfaz de usuario | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt). |
| AndroidX Media3 | Reproducción multimedia | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt). |
| AndroidX Room | Base de datos local | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt). |
| AndroidX WorkManager | Trabajo persistente en segundo plano | [Apache-2.0](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt). |

Para cada publicación hay que conservar los avisos exigidos, revisar licencias y configuración de las dependencias directas y transitivas, y proporcionar el código fuente correspondiente y las instrucciones de instalación cuando sean exigibles. La actualización de yt-dlp puede cambiar el binario efectivo sin cambiar la versión de la biblioteca Android; debe revisarse en el proceso de publicación.
