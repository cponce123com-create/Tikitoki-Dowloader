package com.tikitoki.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import com.tikitoki.downloader.data.LibraryExport
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.VideoPagination
import com.tikitoki.downloader.domain.TikTokLinkParser
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.tikitoki.downloader.ui.ProfileViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    private var sharedLink by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLink = receivedText(intent)
        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) {
                DownloadDatabase.get(applicationContext).downloads().pendingRecoveryCount()
            }
            if (pending > 0) DownloadQueue.start(applicationContext)
        }
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
    val profileViewModel: ProfileViewModel = viewModel()
    val profileState by profileViewModel.state.collectAsState()
    val dao = remember(context) { DownloadDatabase.get(context).downloads() }
    val records by remember(dao) { dao.observeAll() }.collectAsState(initial = emptyList())

    val defaultFormat = remember {
        FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, "Máxima calidad disponible")
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var visiblePages by remember { mutableStateOf(1) }
    var exportFormat by rememberSaveable { mutableStateOf("json") }
    var monitorRevision by remember { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf(incomingLink) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyUndownloaded by rememberSaveable { mutableStateOf(false) }
    var historyFilter by rememberSaveable { mutableStateOf("TODOS") }
    var message by remember { mutableStateOf("Pega un enlace de TikTok para analizarlo.") }
    var inspecting by remember { mutableStateOf(false) }
    var forceRefresh by rememberSaveable { mutableStateOf(false) }
    var formatsBusy by remember { mutableStateOf(false) }
    var queueBusy by remember { mutableStateOf(false) }
    val videos = profileState.videos
    val selected = remember { mutableStateListOf<String>() }
    var formats by remember { mutableStateOf(listOf(defaultFormat)) }
    var chosenFormat by remember { mutableStateOf(defaultFormat) }

    LaunchedEffect(incomingLink) {
        if (incomingLink.isNotBlank() && url != incomingLink) {
            url = incomingLink
            profileViewModel.reset()
            selected.clear()
            tab = 0
            message = "Enlace compartido recibido. Pulsa Analizar."
        }
    }

    LaunchedEffect(profileState.videos) {
        val existing = selected.toSet()
        profileState.videos.forEach { if (it.id !in existing && it.id !in selected) selected.add(it.id) }
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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val snapshot = records.toList()
            val format = exportFormat
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                    val content = if (format == "csv") LibraryExport.toCsv(snapshot) else LibraryExport.toJson(snapshot)
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("No se pudo abrir el destino")
                    }
                    message = "Respaldo de metadatos exportado."
                } catch (e: Exception) {
                    message = "No se pudo exportar: ${e.message}"
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
                    listOf("Buscar", "Descargas", "Biblioteca", "Perfiles", "Acerca de").forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌕", "↓", "▦", "◎", "ⓘ")[index]) },
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
                        Image(painter = painterResource(R.drawable.tikitoki_brand), contentDescription = "Logo Tikitoki Downloader", modifier = Modifier.size(64.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tikitoki", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("DOWNLOADER", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "Kotlin · yt-dlp · v0.4",
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
                                profileViewModel.reset()
                                visiblePages = 1
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
                            enabled = !profileState.loading && !profileState.loadingMore && url.isNotBlank(),
                            onClick = {
                                when (val parsed = TikTokLinkParser.parse(url)) {
                                    is LinkParseResult.Error -> message = parsed.message
                                    is LinkParseResult.Success -> {
                                        visiblePages = 1
                                        selected.clear()
                                        profileViewModel.inspect(url, forceRefresh)
                                        forceRefresh = false
                                        message = "Consultando TikTok por bloques de 60."

                                    }
                                }
                            },
                        ) { Text(if (profileState.loading) "Analizando..." else "Analizar enlace") }

                        FilterChip(selected = forceRefresh, onClick = { forceRefresh = !forceRefresh }, label = { Text("Actualizar desde TikTok (ignorar caché)") })
                        if (profileState.loading || profileState.loadingMore || formatsBusy || queueBusy) CircularProgressIndicator()
                        profileState.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
                        Text(message)

                        if (videos.isNotEmpty()) {
                            val downloadedIds = remember(records) { records.asSequence().filter { it.status == DownloadState.COMPLETED }.map { it.videoId }.toSet() }
                            val filteredVideos = remember(videos, search, onlyUndownloaded, downloadedIds) { videos.filter {
                                (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.id.contains(search)) &&
                                    (!onlyUndownloaded || it.id !in downloadedIds)
                            } }
                            val visibleVideos = remember(filteredVideos, visiblePages) {
                                filteredVideos.take(VideoPagination.visibleCount(filteredVideos.size, visiblePages))
                            }
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
                            FilterChip(selected = onlyUndownloaded, onClick = { onlyUndownloaded = !onlyUndownloaded; visiblePages = 1 }, label = { Text("Solo pendientes de descargar") })
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Buscar entre los videos") },
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = gridMode, onClick = { gridMode = true }, label = { Text("Cuadrícula") })
                                FilterChip(selected = !gridMode, onClick = { gridMode = false }, label = { Text("Lista") })
                            }
                            if (gridMode) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxWidth().height(380.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    gridItems(visibleVideos, key = { it.id }) { video ->
                                        Card(onClick = {
                                            if (video.id in selected) selected.remove(video.id) else selected.add(video.id)
                                            formats = listOf(defaultFormat)
                                            chosenFormat = defaultFormat
                                        }) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                if (video.thumbnail != null) {
                                                    AsyncImage(model = video.thumbnail, contentDescription = "Miniatura", modifier = Modifier.fillMaxWidth().height(110.dp))
                                                }
                                                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(if (video.id in selected) "✓ Seleccionado" else "Tocar para seleccionar",
                                                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            } else LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(360.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(visibleVideos, key = { it.id }) { video ->
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

                            if (profileState.hasMore || visibleVideos.size < filteredVideos.size) {
                                Text("Mostrando ${visibleVideos.size} de ${filteredVideos.size} publicaciones")
                                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !profileState.loadingMore, onClick = {
                                    if (visibleVideos.size < filteredVideos.size) visiblePages++ else profileViewModel.loadMore()
                                }) {
                                    Text(if (profileState.loadingMore) "Cargando desde TikTok..." else "Cargar 60 publicaciones más")
                                }
                            }

                            if (selected.size == 1) {
                                OutlinedButton(
                                    enabled = !formatsBusy && !profileState.loading,
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

                    2 -> {
                        val completed = records.filter { it.status == DownloadState.COMPLETED }
                        Text("Biblioteca multimedia", style = MaterialTheme.typography.titleLarge)
                        Text("${completed.size} videos registrados · ${records.size} entradas en historial")
                        Text("Abre los videos guardados desde la pestaña Descargas. Esta biblioteca conserva los metadatos incluso sin conexión.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { exportFormat = "json"; exportLauncher.launch("tikitoki-respaldo.json") }) { Text("Exportar JSON") }
                            OutlinedButton(onClick = { exportFormat = "csv"; exportLauncher.launch("tikitoki-respaldo.csv") }) { Text("Exportar CSV") }
                        }
                        Text("Estadísticas locales", style = MaterialTheme.typography.titleMedium)
                        Text("Completados: ${completed.size} · Fallidos: ${records.count { it.status == DownloadState.FAILED }} · En cola: ${records.count { it.status == DownloadState.QUEUED }}")
                        if (completed.isEmpty()) Text("Aún no hay videos descargados.")
                        else LazyColumn(modifier = Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(completed, key = { it.videoId }) { record ->
                                DownloadHistoryCard(record, onOpen = {
                                    val saved = record.savedUri
                                    if (saved.isNullOrBlank()) {
                                        message = "No se encontró la ubicación del archivo."
                                        return@DownloadHistoryCard
                                    }
                                    val uri = Uri.parse(saved)
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "video/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        })
                                    } catch (e: Exception) { message = "No se pudo abrir el archivo: ${e.message}" }
                                })
                            }
                        }
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                    3 -> {
                        val monitored = remember(monitorRevision) { ProfileMonitor.profiles(context).sorted() }
                        Text("Monitoreo de perfiles públicos", style = MaterialTheme.typography.titleLarge)
                        Text("Comprobación periódica aproximada cada 12 horas, sujeta a restricciones de Android y TikTok. No descubre videos privados.")
                        OutlinedButton(onClick = {
                            if (ProfileMonitor.add(context, url.trim())) {
                                monitorRevision++
                                message = "Perfil agregado al monitoreo."
                            } else message = "Pega un enlace válido de perfil en Buscar."
                        }) { Text("Seguir perfil del enlace actual") }
                        monitored.forEach { profile ->
                            Card {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(profile, maxLines = 2)
                                    Text("Nuevos desde la última revisión: ${ProfileMonitor.newCount(context, profile)}")
                                    val checked = ProfileMonitor.lastChecked(context, profile)
                                    Text(if (checked > 0) "Última revisión: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(checked))}" else "Pendiente de primera revisión")
                                    ProfileMonitor.lastError(context, profile)?.let { Text("Error reciente: $it", color = MaterialTheme.colorScheme.error) }
                                    OutlinedButton(onClick = { ProfileMonitor.remove(context, profile); monitorRevision++ }) { Text("Dejar de seguir") }
                                }
                            }
                        }
                        if (monitored.isEmpty()) Text("Todavía no sigues perfiles.")
                        Text(message, style = MaterialTheme.typography.bodySmall)
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
