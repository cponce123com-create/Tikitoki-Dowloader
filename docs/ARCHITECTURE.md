# Arquitectura y plan incremental

## Estado actual

La aplicación Android integra Compose, Room, WorkManager y los extractores en :app.
:core:security contiene la validación de entradas y las políticas de descarga. No debe
depender de UI, Room ni de los motores. ProfileViewModel y DownloadsViewModel ya
mantienen parte del estado; MainActivity aún coordina varias responsabilidades.

## Dependencias previstas

- :app: punto de entrada y composición de dependencias.
- :core:security: validación de enlaces y parámetros; módulo Kotlin/JVM.
- :core:database: persistencia Room, como Android Library después de definir sus contratos.
- :core:network: cliente y políticas de red compartidas, solo si existe una necesidad común.
- :feature:downloads y :feature:library: pantallas y ViewModels, como Android Library.
- Una capa de dominio independiente definirá contratos sin depender de implementaciones Android.

Las dependencias deben apuntar desde UI hacia contratos y desde adaptadores de datos hacia
esos contratos. Las features no deben depender de implementaciones concretas de Room ni
de los ejecutables. No se requieren dependencias nuevas para la primera extracción.

## Cambios incrementales

1. Separar exportación de diagnósticos y otros componentes de MainActivity, preservando
   selección del documento, escritura, permisos de URI, cancelación y mensajes.
2. Mover estado y operaciones a ViewModels y contratos, con pruebas de comportamiento.
3. Extraer persistencia y features en módulos pequeños, migrando un consumidor cada vez.
4. Auditar preferencias y cachés; cifrar únicamente credenciales reales si existen.
5. Reducir baseline de estilo, ejecutar pruebas y comprobar flujos en dispositivos.

Cada extracción requiere revisar las diferencias, compilar debug/release, ejecutar lint y
pruebas unitarias. Las pruebas de intents, descargas y MediaStore requieren emulador o
dispositivo; las plataformas externas requieren además red y contenido público autorizado.
