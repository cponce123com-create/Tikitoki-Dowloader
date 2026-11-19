# Seguridad

## Versiones

Hasta que exista una política de soporte por versión, solo se investigarán correcciones sobre la rama principal actual. Una compilación de depuración no se considera un lanzamiento de producción.

## Informar una vulnerabilidad

No publiques credenciales, enlaces privados, archivos personales ni instrucciones de explotación en incidencias públicas. Usa el canal de aviso privado de seguridad del repositorio en GitHub, si está habilitado. Si no aparece, solicita al mantenedor un canal privado antes de compartir detalles sensibles mediante una incidencia pública sin información confidencial.

Incluye la versión o commit, dispositivo y versión de Android, pasos mínimos de reproducción, impacto y registros anonimizados. Nunca adjuntes cookies, tokens o archivos personales. El mantenedor debe confirmar la recepción por el mismo canal y coordinar la divulgación cuando exista una corrección o mitigación; no se promete un plazo específico.

## Para mantenedores

Revisar permisos y enlaces entrantes, integridad y origen de dependencias y actualizaciones del extractor, límites de datos, almacenamiento y licencias antes de cada publicación. No incluir claves de firma, tokens ni secretos en el repositorio o en los registros de CI.

## Actualizaciones de yt-dlp

La aplicación verifica el SHA-256 del ejecutable instalado contra un manifiesto versionado en `ExtractorVerifier`. La versión incluida por `youtubedl-android:0.18.1` es `2025.11.12` y su hash aprobado es `89a0d9058ea9018e380b7771898ff46e393a1986dcd13fef331693c87ce1fca4`. Las versiones desconocidas o con hash distinto se rechazan y activan el rollback.

Esta verificación autentica el artefacto que el proyecto ha aprobado, pero no sustituye la revisión del origen de una nueva release. Toda actualización de `youtubedl-android` debe añadir primero el hash exacto del binario empaquetado, sus pruebas de regresión y una revisión de seguridad antes de publicarse.
