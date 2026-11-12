# Tikitoki Downloader

Aplicación Android nativa en **Kotlin + Jetpack Compose** para gestionar enlaces de perfiles y videos de TikTok y preparar descargas de contenido autorizado.

> Estado: prototipo inicial. La interfaz y validación de enlaces funcionan. La extracción automática de listas, las resoluciones disponibles y la descarga de videos de TikTok aún **no** están implementadas. No se simulan videos ni se promete descargar sin marca de agua.

## Objetivos

- Pegar o compartir un enlace de perfil/video de TikTok.
- Identificar tipo, usuario y video cuando sea posible.
- Mostrar resultados reales cuando un proveedor de extracción compatible esté integrado.
- Seleccionar formatos realmente disponibles y descargar contenido autorizado.
- Mantener historial y gestionar una cola con reintentos en versiones siguientes.

## Desarrollo

Android Studio reciente, JDK 17, Android SDK 35 y Gradle 8.11.1. Abre el directorio raíz como proyecto Android.

## Estructura

- `app/`: aplicación Android y pantalla Compose.
- `app/src/main/java/com/tikitoki/downloader/domain/`: reconocimiento de enlaces.
- `app/src/test/`: pruebas unitarias del parser.
- `.github/workflows/`: compilación automática de APK debug.

## Ética técnica

No incluye APK modificados de TikTok, extracción clandestina de credenciales ni promesas de eliminar marcas de agua. Respetar los derechos de los titulares y las reglas de cada servicio.
