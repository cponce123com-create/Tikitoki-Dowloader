# Estado técnico de referencia

El proyecto es un módulo Android `:app`. La configuración observada en esta rama usa:

- Android Gradle Plugin 9.1.1, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Gradle 9.3.1 y JDK 17.
- `compileSdk` y `targetSdk` 36, `minSdk` 26, versión de aplicación 0.5.0 y `versionCode` 5.
- Room 2.7.2, WorkManager 2.12.0, Compose BOM 2024.12.01, Media3 1.5.1 y Coil 2.7.0.
- youtubedl-android y FFmpeg 0.18.1, y NewPipeExtractor v0.27.1.
- Existe configuración de Dependabot y documentos `LICENSE`, `NOTICE.md`, `PRIVACY.md` y `SECURITY.md`.

## Comprobaciones

El job de CI verifica pruebas unitarias, lint, APK debug y cobertura M1. La cobertura M1 se limita a clases seleccionadas y no representa cobertura global. El gate global actual tiene un piso de 10% mientras crece hacia la meta documentada de 70%; no se debe presentar aún esa meta como un requisito de CI. El proyecto incluye pruebas instrumentadas; consulta el resultado del commit correspondiente antes de dar por aprobada una ejecución.

La base de datos está en versión 2 y tiene una migración 1→2. La exportación de esquemas y las pruebas de migración deben verificarse en CI y dispositivo; no se debe usar migración destructiva como sustituto.

## Pendientes para la beta

- Verificar el APK release firmado y reducido en dispositivos reales de ambas arquitecturas ARM.
- Probar la actualización de instalaciones existentes, cola, reproducción y extractor.
- Comprobar el flujo de firma y publicación con los secretos reales sin exponerlos.
- Revisar licencias, SBOM y estado de seguridad de dependencias y binarios.
- Comprobar timeout de servicios y comportamiento en Android 15/16.

La actualización del extractor no tiene verificación criptográfica independiente en la aplicación; consulta `SECURITY.md` para sus limitaciones.
