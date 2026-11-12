# F0 — Baseline técnico (2026-11-12)

Repositorio: `cponce123com-create/Tikitoki-Dowloader`, rama `main`.
Referencia de verificación: commit `e763dfcaf7bbfaea1ba682cdbbe9cf1f3cebbde2`; [CI #43225884541](https://github.com/cponce123com-create/Tikitoki-Dowloader/actions/runs/43225884541).

## Inventario comprobado

- Proyecto Gradle de un módulo: `:app`; no existe `gradle/libs.versions.toml` en el árbol inspeccionado.
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Gradle 8.10.2 en Actions, JDK 17.
- `compileSdk=35`, `targetSdk=35`, `minSdk=26`; versión de app 0.5.0 (versionCode 5).
- `youtubedl-android:library:0.18.1` y `ffmpeg:0.18.1` declarados; versiones internas de yt-dlp/FFmpeg, CVEs y tamaño del APK: **no medidos**.
- Compose BOM 2024.12.01, Coil 2.7.0, Room 2.6.1, WorkManager 2.9.1.
- Room: esquema versión 1, `exportSchema=false`; no migración versionada comprobada.
- `AndroidManifest.xml`: `MainActivity` exported, App Links HTTPS para TikTok, ACTION_SEND text/plain, FGS dataSync, backups desactivados y cleartext desactivado.
- `IncomingLinkValidator` y `MediaLinkParser`: validación de enlaces y puerta de entrada; JaCoCo de líneas mínimo 95 % sobre clases seleccionadas.
- CI actual: dos jobs `build` (JUnit, cobertura M1, APK) y `android-instrumented` (emulador API 29).

## Estado de la evidencia

- El job `build` de la ejecución enlazada terminó **success** al momento de inspección.
- `android-instrumented` estaba **in_progress** en esa consulta: **no certificado**.
- El umbral M1 >=95 % es de un conjunto seleccionado de clases, **no equivale a cobertura global >=85 %**.
- No se encontró en el árbol actual configuración de Detekt, Ktlint, CycloneDX, Dependabot, SECURITY.md o NOTICE.
- No hay evidencia de escaneo de CVEs de binarios nativos, firma de release, checksum de release ni actualización autenticada de yt-dlp con rollback.
- La presencia de `android:autoVerify=true` no implica que TikTok haya publicado Digital Asset Links para esta aplicación.
- No se ha demostrado la recuperación de miniaturas en un dispositivo físico ni cobertura UI del 80 %.

## Riesgos priorizados

1. **Alto:** fallos de instrumentación/emulador o de compilación que bloqueen evidencia reproducible.
2. **Alto:** actualización del motor y suministro de binarios sin verificación autenticada y rollback demostrado.
3. **Alto:** superficie de entrada externa, redirecciones y parámetros pasados a yt-dlp; completar pruebas de seguridad.
4. **Medio:** Room sin exportación de esquema; impedir cambios sin migración instrumentada.
5. **Medio:** CI carece de cobertura global, lint/detekt/ktlint y SBOM.
6. **Medio:** Compose con geometría adaptable y miniaturas aún pendientes de validación real.

## Puerta de salida F0

- [x] Inventario de archivos, stack y permisos.
- [x] Resultado del job `build` registrado.
- [ ] Job instrumentado aprobado en el mismo commit.
- [ ] Reporte baseline de lint y cobertura global (no confundir con M1).
- [ ] SBOM y evaluación de CVEs de dependencias y binarios.
- [ ] Tamaño del APK y versión real del ejecutable yt-dlp registrados.

No se asigna puntuación 9.5 a ningún módulo sin evidencias. F1 no debe certificarse hasta satisfacer su matriz de ataques y sus tests. Ningún cambio de esquema Room se autoriza sin migración y test.

## Rollback

Este documento no modifica código de producción ni el esquema de datos. Puede revertirse su commit sin impacto funcional.
