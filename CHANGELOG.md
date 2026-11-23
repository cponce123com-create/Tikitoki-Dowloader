# Registro de cambios

## 0.5.1

- Cifrado de las sesiones de fotos con Android Keystore y migración de sesiones importadas anteriormente.
- Aviso de privacidad antes de importar cookies, con indicaciones para eliminar la sesión y revocarla.
- Validación del hash de yt-dlp antes de ejecutar una actualización descargada.
- Firma de release mediante ejecución manual. El flujo exige CI exitoso en el commit del tag, verifica bibliotecas nativas y genera un borrador de publicación.

## 0.5.0

- Analizador de enlaces para plataformas compatibles y selección de formatos.
- Cola de descargas persistente y adaptativa con Room y WorkManager.
- Biblioteca local, reproducción de audio y video y seguimiento opcional de perfiles.
- Actualización del extractor con recuperación y diagnósticos locales.

Esta entrada describe la versión declarada en el proyecto; no certifica por sí sola un release publicado o firmado.
