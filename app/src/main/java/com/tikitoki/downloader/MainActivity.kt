package com.tikitoki.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
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
        setContent { TikitokiApp(sharedLink) }
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
private fun TikitokiApp(incomingLink: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { TikTokVideoSource(context.applicationContext) }
    val dao = remember(context) { DownloadDatabase.get(context).downloads() }
    val records by remember(dao) { dao.observeAll() }.collectAsState(initial = emptyList())

    val defaultFormat = remember {
        FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, "Máxima calidad disponible")
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf(incomingLink) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyUndownloaded by rememberSaveable { mutableStateOf(false) }
    var historyFilter by rememberSaveable { mutableStateOf("TODOS") }
    var message by remember { mutableStateOf("Pega un enlace de TikTok para analizarlo.") }
    var inspecting by remember { mutableStateOf(false) }
    var forceRefresh by rememberSaveable { mutableStateOf(false) }
    var formatsBusy by remember { mutableStateOf(false) }
    var queueBusy by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<VideoEntry>>(emptyList()) }
    val selected = remember { mutableStateListOf<String>() }
    var formats by remember { mutableStateOf(listOf(defaultFormat)) }
    var chosenFormat by remember { mutableStateOf(defaultFormat) }

    LaunchedEffect(incomingLink) {
        if (incomingLink.isNotBlank() && url != incomingLink) {
            url = incomingLink
            videos = emptyList()
            selected.clear()
            tab = 0
            message = "Enlace compartido recibido. Pulsa Analizar."
        }
    }

    val addToQueue: () -> Unit = {
        if (!queueBusy && selected.isNotEmpty()) {
            queueBusy = true
            val batch = videos.filter { it.id in selected }
            val formatId = if (batch.size == 1) chosenFormat.id else defaultFormat.id
            scope.launch {
                try {
                    val result = DownloadQueue.enqueue(context, batch, formatId)
                    message = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                    tab = 1
                } catch (e: Exception) {
                    message = "No se pudo crear la cola: ${e.message ?: "error desconocido"}"
                } finally {
                    queueBusy = false
                }
            }
        }
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) addToQueue()
        else message = "Android necesita permiso para guardar en Descargas."
    }
    val notificationsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notificaciones opcionales: las descargas siguen aunque se denieguen. */ }

    MaterialTheme(colorScheme = TikitokiColors) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Buscar", "Descargas", "Acerca de").forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌕", "↓", "ⓘ")[index]) },
                            label = { Text(name) },
                        )
                    }
                }
            }
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = Color(0xFF20202B), shape = MaterialTheme.shapes.large) {
                        Text("♪", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFF59E4DE), style = MaterialTheme.typography.headlineMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tikitoki", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("DOWNLOADER", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "Kotlin · yt-dlp · v0.3",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                )

                when (tab) {
                    0 -> {
                        Text("Analizar perfil o video", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                videos = emptyList()
                                selected.clear()
                                formats = listOf(defaultFormat)
                                chosenFormat = defaultFormat
                            },
                            label = { Text("Enlace de TikTok") },
                            placeholder = { Text("https://www.tiktok.com/@usuario") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !inspecting && url.isNotBlank(),
                            onClick = {
                                when (val parsed = TikTokLinkParser.parse(url)) {
                                    is LinkParseResult.Error -> message = parsed.message
                                    is LinkParseResult.Success -> {
                                        inspecting = true
                                        videos = emptyList()
                                        selected.clear()
                                        message = "Consultando TikTok. La disponibilidad depende del extractor."
                                        scope.launch {
                                            try {
                                                val found = source.inspect(parsed.link, forceRefresh = forceRefresh)
                                                forceRefresh = false
                                                videos = found
                                                selected.addAll(found.map { it.id })
                                                message = if (found.isEmpty()) {
                                                    "El extractor no devolvió videos; TikTok puede haber limitado el acceso."
                                                } else "${found.size} videos detectados por el extractor."
                                            } catch (e: Exception) {
                                                message = "Error al analizar: ${e.message ?: "desconocido"}"
                                            } finally {
                                                inspecting = false
                                            }
                                        }
                                    }
                                }
                            },
                        ) { Text(if (inspecting) "Analizando..." else "Analizar enlace") }

                        FilterChip(selected = forceRefresh, onClick = { forceRefresh = !forceRefresh }, label = { Text("Actualizar desde TikTok (ignorar caché)") })
                        if (inspecting || formatsBusy || queueBusy) CircularProgressIndicator()
                        Text(message)

                        if (videos.isNotEmpty()) {
                            val downloadedIds = remember(records) { records.asSequence().filter { it.status == DownloadState.COMPLETED }.map { it.videoId }.toSet() }
                            val filteredVideos = remember(videos, search, onlyUndownloaded, downloadedIds) { videos.filter {
                                (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.id.contains(search)) &&
                                    (!onlyUndownloaded || it.id !in downloadedIds)
                            } }
                            val allSelected = selected.size == videos.size
                            Text("${videos.size} publicaciones detectadas · ${selected.size} seleccionadas", style = MaterialTheme.typography.titleSmall)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                    selected.clear()
                                    if (!allSelected) selected.addAll(videos.map { it.id })
                                    formats = listOf(defaultFormat)
                                    chosenFormat = defaultFormat
                                }) { Text(if (allSelected) "Quitar selección" else "Seleccionar todos", maxLines = 1) }
                            }
                            Text("La lista del perfil no confirma si cada video sigue siendo público. Los videos privados que TikTok no expone no pueden identificarse desde aquí.", style = MaterialTheme.typography.bodySmall)
                            FilterChip(selected = onlyUndownloaded, onClick = { onlyUndownloaded = !onlyUndownloaded }, label = { Text("Solo pendientes de descargar") })
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Buscar entre los videos") },
                                singleLine = true,
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(360.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(filteredVideos, key = { it.id }) { video ->
                                    val checked = video.id in selected
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { value ->
                                                    if (value && video.id !in selected) selected.add(video.id)
                                                    else if (!value) selected.remove(video.id)
                                                    formats = listOf(defaultFormat)
                                                    chosenFormat = defaultFormat
                                                },
                                            )
                                            if (!video.thumbnail.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = video.thumbnail,
                                                    contentDescription = "Miniatura",
                                                    modifier = Modifier.size(66.dp),
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    video.id,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                )
                                                Text(if (video.id in downloadedIds) "✓ Descargado" else "Sin verificar · listado público", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                    }
                                }
                            }

                            if (selected.size == 1) {
                                OutlinedButton(
                                    enabled = !formatsBusy && !inspecting,
                                    onClick = {
                                        val video = videos.firstOrNull { it.id == selected.first() }
                                        if (video != null) {
                                            formatsBusy = true
                                            scope.launch {
                                                try {
                                                    formats = source.formats(video)
                                                    chosenFormat = formats.first()
                                                    message = "Formatos disponibles consultados."
                                                } catch (e: Exception) {
                                                    message = "No se pudieron consultar los formatos: ${e.message}"
                                                } finally {
                                                    formatsBusy = false
                                                }
                                            }
                                        }
                                    },
                                ) { Text("Consultar resoluciones") }

                                if (formats.size > 1) {
                                    formats.forEach { option ->
                                        FilterChip(
                                            selected = chosenFormat.id == option.id,
                                            onClick = { chosenFormat = option },
                                            label = { Text(option.label) },
                                        )
                                    }
                                }
                            } else if (selected.size > 1) {
                                Text("Descarga por lotes: se elegirá automáticamente la mejor calidad de cada video.")
                            }

                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selected.isNotEmpty() && !queueBusy && !inspecting,
                                onClick = {
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        if (Build.VERSION.SDK_INT >= 33 &&
                                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                            != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        addToQueue()
                                    }
                                }
                            ) { Text("Añadir ${selected.size} a descargas") }
                        }
                    }

                    1 -> {
                        val finished = records.count { it.status == DownloadState.COMPLETED }
                        val pending = records.count { it.status == DownloadState.QUEUED || it.status == DownloadState.DOWNLOADING }
                        val paused = records.count { it.status == DownloadState.PAUSED }
                        val failed = records.count { it.status == DownloadState.FAILED }

                        Text("Gestor de descargas", style = MaterialTheme.typography.titleLarge)
                        Text("$finished completadas · $pending pendientes · $failed errores")
                        Text("Los videos se guardan en Descargas/TikitokiDownloader.")

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = pending > 0,
                                onClick = {
                                    scope.launch {
                                        DownloadQueue.pause(context)
                                        message = "Cola pausada."
                                    }
                                },
                            ) { Text("Pausar") }
                            Button(
                                enabled = paused > 0 || pending > 0,
                                onClick = {
                                    scope.launch {
                                        DownloadQueue.resume(context)
                                        message = "Cola reanudada."
                                    }
                                },
                            ) { Text("Continuar") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = failed > 0,
                                onClick = {
                                    scope.launch {
                                        DownloadQueue.retryFailed(context)
                                        message = "Reintentando descargas fallidas."
                                    }
                                },
                            ) { Text("Reintentar errores") }
                            OutlinedButton(
                                enabled = failed > 0,
                                onClick = { scope.launch { dao.clearFailed() } },
                            ) { Text("Limpiar errores") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TODOS", "COMPLETED", "FAILED").forEach { filter ->
                                FilterChip(
                                    selected = historyFilter == filter,
                                    onClick = { historyFilter = filter },
                                    label = {
                                        Text(when (filter) {
                                            "COMPLETED" -> "Listos"
                                            "FAILED" -> "Errores"
                                            else -> "Todos"
                                        })
                                    },
                                )
                            }
                        }

                        val displayed = records.filter {
                            historyFilter == "TODOS" || it.status == historyFilter
                        }
                        if (displayed.isEmpty()) Text("Todavía no hay descargas en esta categoría.")
                        else LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(440.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(displayed, key = { it.videoId }) { record ->
                                DownloadHistoryCard(record, onOpen = {
                                    val saved = record.savedUri ?: return@DownloadHistoryCard
                                    try {
                                        val uri = Uri.parse(saved)
                                        if (uri.scheme != "content") {
                                            message = "Archivo disponible en Descargas/TikitokiDownloader."
                                        } else {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "video/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        }
                                    } catch (e: Exception) {
                                        message = "No se pudo abrir el archivo: ${e.message}"
                                    }
                                })
                            }
                        }
                        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                    }

                    else -> {
                        Text("Acerca del proyecto", style = MaterialTheme.typography.titleLarge)
                        Text("Versión 0.3: extracción yt-dlp, formatos, descargas por lotes, historial Room, control de duplicados y cola WorkManager.")
                        Text("Las descargas usan el servicio de primer plano Android; la carpeta final es visible en Descargas.")
                        Text("TikTok puede bloquear el acceso a publicaciones o no proporcionar formatos sin marca de agua. No se garantiza descargar todos los videos de un perfil.")
                        Text("Solo debe utilizarse con contenido propio o autorizado. El motor yt-dlp Android emplea componentes GPL-3.0.")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadHistoryCard(record: DownloadRecord, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!record.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = record.thumbnail,
                        contentDescription = "Miniatura",
                        modifier = Modifier.size(64.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (record.status) {
                            DownloadState.QUEUED -> "En cola"
                            DownloadState.DOWNLOADING -> "Descargando ${record.progress}%"
                            DownloadState.PAUSED -> "Pausado"
                            DownloadState.COMPLETED -> "Completado"
                            DownloadState.FAILED -> "Error"
                            else -> record.status
                        },
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (record.status == DownloadState.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { record.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!record.error.isNullOrBlank()) {
                Text(record.error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (record.status == DownloadState.COMPLETED && record.savedUri != null) {
                OutlinedButton(onClick = onOpen) { Text("Abrir video") }
            }
        }
    }
}
