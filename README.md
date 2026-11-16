# Baja.pe

Aplicación Android nativa en Kotlin y Jetpack Compose para analizar enlaces y guardar o reproducir contenido disponible desde plataformas compatibles. La disponibilidad de cada contenido depende de la plataforma y de sus restricciones.

**Usa la aplicación solo para contenido propio o cuya descarga y uso estén autorizados.** No se promete acceso a contenido privado, protegido o restringido.

## Plataformas

El analizador acepta enlaces HTTPS de TikTok, YouTube, Facebook, Instagram, XVideos, Vimeo, Dailymotion, SoundCloud y Twitch. El soporte real depende del extractor y de cada enlace; aceptar un dominio no garantiza que un contenido o una lista pueda descargarse. Los demás dominios siguen rechazándose; no se admite acceso genérico a redes privadas o direcciones arbitrarias.

## Funciones

- Recepción, validación y análisis de enlaces; selección individual o múltiple y elección de formatos disponibles.
- Cola persistente y adaptativa en paralelo mediante Room y WorkManager, con límites configurables según la red, pausa, reanudación y reintentos.
- Biblioteca local de audio y video, reproducción con Media3 y acceso a los archivos descargados.
- Miniaturas, seguimiento opcional de perfiles públicos y actualización del extractor yt-dlp.

La concurrencia efectiva depende de la configuración, la red, la plataforma y el estado de la cola. El análisis, los formatos y el progreso pueden variar según el origen. La compatibilidad con plataformas y perfiles es experimental y requiere pruebas con contenido autorizado.

## Compilación y comprobaciones

Requisitos: JDK 17, Android SDK con plataforma 37 y herramientas de compilación correspondientes, y conexión a los repositorios configurados. En Linux o macOS:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

En Windows usa `gradlew.bat` con las mismas tareas. El wrapper descarga Gradle 9.3.1 y verifica la suma SHA-256 de su distribución. El APK de depuración queda en `app/build/outputs/apk/debug/`. CI también ejecuta pruebas instrumentadas; consulta sus resultados antes de considerar validado un cambio. Un APK debug no es una versión de producción firmada.

## Distribución

El objetivo del proyecto es distribuir versiones mediante GitHub Releases y, si cumplen los requisitos y son aceptadas por sus respectivos mantenedores, F-Droid e IzzyOnDroid. No se prevé distribuir por Google Play. La disponibilidad en esos catálogos no está garantizada ni implica que la integración o la publicación ya estén completadas.

Antes de publicar se debe preparar una compilación reproducible y firmada, verificar las licencias de todas las dependencias y facilitar el código fuente correspondiente conforme a las licencias aplicables. Consulta [NOTICE.md](NOTICE.md) y [LICENSE](LICENSE).

## Privacidad y seguridad

La app guarda historial, configuración y archivos localmente y se conecta a las plataformas de origen, sus servicios de miniaturas y proveedores de actualización del extractor. Consulta [PRIVACY.md](PRIVACY.md) y [SECURITY.md](SECURITY.md). Se requieren pruebas reales de red, permisos y comportamiento en dispositivos antes de publicar.

## Licencia

El proyecto se distribuye bajo GPL-3.0; el texto completo está en [LICENSE](LICENSE). Los componentes de terceros conservan sus respectivas licencias; véase [NOTICE.md](NOTICE.md).


