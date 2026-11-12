package com.tikitoki.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var sharedLink by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLink = receivedText(intent)
        setContent { TikitokiScreen(sharedLink) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedLink = receivedText(intent)
    }

    private fun receivedText(intent: Intent?): String =
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else ""
}

private val TikitokiColors = darkColorScheme(
    primary = Color(0xFFFF426F),
    onPrimary = Color.White,
    secondary = Color(0xFF59E4DE),
    background = Color(0xFF101014),
    surface = Color(0xFF1B1B22),
    onSurface = Color(0xFFF4F4F7),
)

@Composable
private fun TikitokiScreen(incomingLink: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { TikTokVideoSource(context.applicationContext) }
    val defaultFormat = FormatChoice("best[ext=mp4]/best", "Máxima disponible")
    var tab by rememberSaveable { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf(incomingLink) }
    var status by remember { mutableStateOf("Pega un enlace de perfil o video para comenzar.") }
    var inspecting by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var checkingFormats by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<VideoEntry>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var formats by remember { mutableStateOf(listOf(defaultFormat)) }
    var selectedFormat by remember { mutableStateOf(defaultFormat) }
    var progress by remember { mutableFloatStateOf(0f) }
    var completed by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(0) }

    LaunchedEffect(incomingLink) {
        if (incomingLink.isNotBlank() && incomingLink != input) {
            input = incomingLink
            videos = emptyList()
            selectedIds.clear()
            status = "Enlace compartido recibido."
            tab = 0
        }
    }

    val runDownloads: () -> Unit = {
        if (selectedIds.isNotEmpty() && !downloading && !inspecting) {
            val batch = videos.filter { it.id in selectedIds }
            val formatToUse = if (batch.size == 1) selectedFormat.id else defaultFormat.id
            downloading = true
            progress = 0f
            completed = 0
            failed = 0
            scope.launch {
                for ((index, video) in batch.withIndex()) {
                    status = "Descargando ${index + 1} de ${batch.size}: ${video.title}"
                    progress = 0f
                    try {
                        val output = source.download(video, formatToUse) { value ->
                            scope.launch { progress = value.coerceIn(0f, 100f) / 100f }
                        }
                        completed += 1
                        status = "Guardado: ${output.name}"
                    } catch (e: Exception) {
                        failed += 1
                        status = "Error en ${video.id}: ${e.message ?: "desconocido"}"
                    }
                }
                downloading = false
                status = "Lote finalizado. Guardados: $completed. Errores: $failed. Carpeta: Descargas/TikitokiDownloader."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runDownloads() else status = "Se necesita permiso para guardar archivos en esta versión de Android."
    }

    MaterialTheme(colorScheme = TikitokiColors) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Inicio", "Descargas", "Información").forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌂", "▤", "ⓘ")[index]) },
                            label = { Text(name) },
                        )
                    }
                }
            },
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets)
                    .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Tikitoki Downloader", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Kotlin · yt-dlp · v0.2",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium)

                when (tab) {
                    0 -> {
                        Text("Enlace de TikTok", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                videos = emptyList()
                                selectedIds.clear()
                                formats = listOf(defaultFormat)
                                selectedFormat = defaultFormat
                            },
                            label = { Text("Perfil o video") },
                            placeholder = { Text("https://www.tiktok.com/@usuario") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = input.isNotBlank() && !inspecting && !downloading,
                            onClick = {
                                when (val result = TikTokLinkParser.parse(input)) {
                                    is LinkParseResult.Error -> status = result.message
                                    is LinkParseResult.Success -> {
                                        inspecting = true
                                        videos = emptyList()
                                        selectedIds.clear()
                                        status = "Consultando TikTok. Esto depende de la disponibilidad del perfil y del extractor."
                                        scope.launch {
                                            try {
                                                val results = source.inspect(result.link)
                                                videos = results
                                                selectedIds.addAll(results.map { it.id })
                                                selectedFormat = defaultFormat
                                                formats = listOf(defaultFormat)
                                                status = if (results.isEmpty()) {
                                                    "El extractor no devolvió videos. TikTok puede haber limitado el acceso."
                                                } else {
                                                    "${results.size} videos devueltos por el extractor."
                                                }
                                            } catch (e: Exception) {
                                                status = "No se pudo analizar: ${e.message ?: "error de extracción"}"
                                            } finally {
                                                inspecting = false
                                            }
                                        }
                                    }
                                }
                            },
                        ) { Text(if (inspecting) "Analizando..." else "Analizar enlace") }

                        if (inspecting || checkingFormats) CircularProgressIndicator()
                        Text(status)
                        if (videos.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${videos.size} videos", style = MaterialTheme.typography.titleMedium)
                                Button(
                                    enabled = !downloading,
                                    onClick = {
                                        selectedIds.clear()
                                        if (selectedIds.size != videos.size) selectedIds.addAll(videos.map { it.id })
                                    },
                                ) {
                                    Text(if (selectedIds.size == videos.size) "Quitar selección" else "Seleccionar todos")
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(330.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                items(videos, key = { it.id }) { video ->
                                    val checked = video.id in selectedIds
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(9.dp),
                                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                enabled = !downloading,
                                                onCheckedChange = { isChecked ->
                                                    if (isChecked && video.id !in selectedIds) {
                                                        selectedIds.add(video.id)
                                                    } else if (!isChecked) {
                                                        selectedIds.remove(video.id)
                                                    }
                                                    formats = listOf(defaultFormat)
                                                    selectedFormat = defaultFormat
                                                },
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(video.title, maxLines = 2)
                                                Text(video.id, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedIds.size == 1) {
                                Button(
                                    enabled = !inspecting && !downloading && !checkingFormats,
                                    onClick = {
                                        val video = videos.firstOrNull { it.id == selectedIds.first() }
                                        if (video != null) {
                                            checkingFormats = true
                                            status = "Consultando formatos disponibles..."
                                            scope.launch {
                                                try {
                                                    formats = source.formats(video)
                                                    selectedFormat = formats.first()
                                                    status = "${formats.size} opciones de calidad devueltas."
                                                } catch (e: Exception) {
                                                    status = "No fue posible obtener formatos: ${e.message}"
                                                } finally {
                                                    checkingFormats = false
                                                }
                                            }
                                        }
                                    },
                                ) { Text("Consultar resoluciones") }
                            }

                            if (selectedIds.size == 1 && formats.size > 1) {
                                Text("Formato", fontWeight = FontWeight.Bold)
                                formats.forEach { option ->
                                    FilterChip(
                                        selected = option.id == selectedFormat.id,
                                        onClick = { selectedFormat = option },
                                        label = { Text(option.label) },
                                    )
                                }
                            } else if (selectedIds.size > 1) {
                                Text("Para los lotes se utilizará la máxima calidad disponible de cada video.")
                            }

                            Button(
                                enabled = selectedIds.isNotEmpty() && !downloading && !inspecting,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        runDownloads()
                                    }
                                },
                            ) {
                                Text(if (downloading) "Descargando..." else "Descargar seleccionados (${selectedIds.size})")
                            }
                            if (downloading) LinearProgressIndicator(progress = { progress },
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    1 -> {
                        Text("Descargas", style = MaterialTheme.typography.titleLarge)
                        Text("Guardados en esta sesión: $completed. Errores: $failed.")
                        Text("Destino: Descargas/TikitokiDownloader.")
                        Text("El historial persistente y las descargas con la app cerrada aún están pendientes.")
                    }
                    else -> {
                        Text("Información", style = MaterialTheme.typography.titleLarge)
                        Text("Motor de extracción: yt-dlp vía youtubedl-android, licencia GPL-3.0.")
                        Text("TikTok puede restringir la enumeración de perfiles, las calidades o la descarga. No se garantiza ausencia de marcas de agua.")
                        Text("Úsalo solamente para contenido propio o con autorización de descarga.")
                    }
                }
            }
        }
    }
}
