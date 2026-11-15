# Migración de identidad de Baja.pe

Estado: diseño pendiente de implementación y validación; no ejecutar como procedimiento de migración todavía.

## Identidad

- Instalación existente: `com.tikitoki.downloader`.
- Identidad elegida: Baja.pe, `com.cponce.bajape`.
- Directorio propuesto para nuevas descargas: `Descargas/Baja.pe/`.

Android considera cada applicationId una aplicación distinta. La aplicación nueva no puede abrir directamente la base de datos privada ni las preferencias de la anterior. El manifiesto actual desactiva el backup automático; cambiar solamente nombres de paquete, preferencias o base de datos no transfiere datos.

## Datos existentes y riesgos

- Base Room privada: `tikitoki_downloads.db` (actualmente versión 2), que contiene descargas, estados y lotes.
- Preferencias privadas: entre otras, `tikitoki_settings`, preferencias del reproductor, seguimiento y actualización del extractor. Deben inventariarse todas antes de definir el formato.
- Los URI guardados y permisos persistentes de carpetas seleccionadas pertenecen a la instalación que recibió el permiso. Importar la cadena URI no concede acceso a la nueva app; el usuario puede necesitar volver a seleccionar la carpeta o los archivos.
- Los archivos en almacenamiento compartido pueden permanecer en la carpeta antigua. No se deben mover ni borrar automáticamente; acceso y propiedad en MediaStore varían según versión de Android.
- Las descargas incompletas, trabajos de WorkManager y archivos temporales privados no se pueden transferir de manera transparente. No deben representarse como reanudables sin validar los archivos y el acceso.

## Secuencia prevista

1. Entregar y probar una actualización puente firmada compatible con la app antigua. El usuario debe instalarla antes de desinstalar la app antigua.
2. Exportar voluntariamente un archivo versionado mediante el selector de Android. Incluir únicamente datos identificados, necesarios y validados; no exportar secretos ni permisos como si fueran transferibles.
3. En la nueva app, importar el archivo elegido, validar versión y contenido, prevenir sobreescritura involuntaria y mostrar un resumen de lo que pudo restaurarse. Solicitar de nuevo acceso a las carpetas y archivos cuando sea necesario.
4. Confirmar el acceso a los archivos y los resultados en dispositivos antes de ofrecer retirar la app anterior. Nunca desinstalar ni borrar datos automáticamente.

La firma de la actualización puente debe ser compatible con la instalación anterior. Si el usuario ya desinstaló la app antigua o instaló una compilación con firma incompatible, no debe prometerse recuperación de sus datos privados. La migración requiere pruebas en dispositivos reales y validación de permisos y formatos.
