# NewPipe usa modelos y extractores cargados dinámicamente.
-keep class org.schabi.newpipe.extractor.** { *; }

# youtubedl-android y FFmpeg cargan componentes nativos y se comunican con procesos externos.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# Room utiliza implementaciones generadas y acceso reflexivo a la base de datos.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }
-keep class androidx.room.** { *; }

# Media3 descubre componentes y servicios de reproducción a través de sus APIs.
-keep class androidx.media3.** { *; }

# Coil descubre decodificadores y componentes registrados por la aplicación.
-keep class coil.** { *; }

# Android proporciona org.json; conserva nombres usados al procesar metadatos.
-keep class org.json.** { *; }

# Rhino incluye una conversión opcional de JavaBeans no disponible en Android.
# Debe verificarse en dispositivo que los extractores usados no requieran esa ruta.
-dontwarn java.beans.**
