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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.LibraryExport
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.VideoPagination
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.IncomingIntentReader
import com.tikitoki.downloader.data.UniversalMediaSource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechPlatformStrip
import com.tikitoki.downloader.ui.TechShortcut
import com.tikitoki.downloader.ui.TechPrimaryButton
import com.tikitoki.downloader.ui.TechPurple
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechGradient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as videoGridItems
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.tikitoki.downloader.ui.ProfileViewModel
import com.tikitoki.downloader.ui.DownloadsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    private var sharedLink by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLink = receivedText(intent)
        ExtractorUpdateWorker.schedule(applicationContext)
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

    private fun receivedText(intent: Intent?): String = IncomingIntentReader.read(intent)

}

private val TikitokiColors = darkColorScheme(
    primary = TechPurple,
    onPrimary = Color.White,
    secondary = TechPink,
    background = TechBackground,
    surface = TechPanel,
    onSurface = Color(0xFFF4F4F7),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TikitokiApp(incomingLink: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { TikTokVideoSource(context.applicationContext) }
    val thumbnailResolver = remember(context) { VideoThumbnailResolver(context.applicationContext) }
    val universalSource = remember(context) { UniversalMediaSource(context.applicationContext) }
    val profileViewModel: ProfileViewModel = viewModel()
    val profileState by profileViewModel.state.collectAsState()
    val dao = remember(context) { DownloadDatabase.get(context).downloads() }
    val downloadsViewModel: DownloadsViewModel = viewModel()
    val records by downloadsViewModel.records.collectAsState()

    val defaultFormat = remember {
        FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, "Máxima calidad disponible")
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var selectedPlatform by remember { mutableStateOf<String?>("Auto") }
    var exportFormat by rememberSaveable { mutableStateOf("json") }
    var monitorRevision by remember { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf(incomingLink) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyUndownloaded by rememberSaveable { mutableStateOf(false) }
    var historyFilter by rememberSaveable { mutableStateOf("TODOS") }
    var message by remember { mutableStateOf("") }
    var inspecting by remember { mutableStateOf(false) }
    var forceRefresh by rememberSaveable { mutableStateOf(false) }
    var formatsBusy by remember { mutableStateOf(false) }
    var queueBusy by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
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

    val addToQueue: () -> Unit = {
        if (!queueBusy && selected.isNotEmpty()) {
            queueBusy = true
            val batch = videos.filter { it.id in selected }.take(60)
            val formatId = if (batch.size == 1) chosenFormat.id else defaultFormat.id
            scope.launch {
                try {
                    val result = DownloadQueue.enqueue(context, batch, formatId)
                    message = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                    if (result.added > 0 || result.duplicates > 0) batch.forEach { selected.remove(it.id) }
                    if (selected.isEmpty()) tab = 1
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
        if (showQualitySheet && selected.size == 1) {
            val preview = videos.firstOrNull { it.id == selected.first() }
            ModalBottomSheet(onDismissRequest = { showQualitySheet = false },
                containerColor = TechBackground) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Opciones de descarga", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    if (preview != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            VideoThumbnail(preview, thumbnailResolver,
                                Modifier.width(144.dp).aspectRatio(16f / 10f))
                            Column(Modifier.weight(1f)) {
                                Text(preview.title, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${preview.durationSeconds}s", color = TechMuted)
                            }
                        }
                    }
                    Text("Calidad", style = MaterialTheme.typography.titleMedium)
                    if (formatsBusy) CircularProgressIndicator(color = TechPurple)
                    Column(Modifier.heightIn(max = 290.dp).verticalScroll(rememberScrollState())) {
                        formats.forEach { option ->
                            Row(Modifier.fillMaxWidth().clickable { chosenFormat = option }
                                .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosenFormat.id == option.id,
                                    onClick = { chosenFormat = option })
                                Text(option.label, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    TechPrimaryButton("↓ Descargar", enabled = !queueBusy && !formatsBusy,
                        modifier = Modifier.fillMaxWidth(), onClick = {
                            showQualitySheet = false
                            addToQueue()
                        })
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
        Scaffold(
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(Color(0xFF110D20))) {
                    if (tab == 0 && selected.isNotEmpty() && videos.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected.size == 1) {
                                TechPrimaryButton(
                                    text = "⚙ Calidad",
                                    enabled = !formatsBusy,
                                    modifier = Modifier.weight(0.85f),
                                    onClick = {
                                        showQualitySheet = true
                                        val video = videos.firstOrNull { it.id == selected.first() }
                                        if (video != null) {
                                            formatsBusy = true
                                            scope.launch {
                                                try {
                                                    formats = if (video.id.contains(":")) universalSource.formats(video) else source.formats(video)
                                                    chosenFormat = formats.first()
                                                } catch (e: Exception) {
                                                    message = "No se pudieron cargar formatos: ${e.message}"
                                                } finally { formatsBusy = false }
                                            }
                                        }
                                    }
                                )
                            }
                            Button(
                                modifier = Modifier.weight(1.15f),
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
                            ) { Text("↓ Descargar ${minOf(selected.size, 60)}${if (selected.size > 60) " de ${selected.size}" else ""}", fontWeight = FontWeight.Bold) }
                        }
                    }
                NavigationBar(containerColor = Color(0xFF110D20)) {
                    listOf("Inicio", "Descargas", "Explorar", "Herramientas", "Archivos").forEachIndexed { index, name ->
                        val target = listOf(0, 1, 2, 3, 4)[index]
                        NavigationBarItem(
                            selected = tab == target,
                            onClick = { tab = target },
                            icon = { Text(listOf("⌂", "↓", "◈", "▦", "▤")[index], fontSize = 23.sp) },
                            label = { Text(name, maxLines = 1) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White, selectedTextColor = Color.White,
                                indicatorColor = TechPurple, unselectedIconColor = TechMuted,
                                unselectedTextColor = TechMuted)
                        )
                    }
                }
                }
            }
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().background(TechBackground).padding(insets)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TechHeader(onSettings = { tab = 4 })

                when (tab) {
                    0 -> {
                        TechPlatformStrip(selectedPlatform) { platform -> selectedPlatform = platform }
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                profileViewModel.reset()
                                
                                selected.clear()
                                formats = listOf(defaultFormat)
                                chosenFormat = defaultFormat
                            },
                            placeholder = { Text("Pega un enlace") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(0.75f).height(44.dp),
                                onClick = {
                                    val pasted = clipboard.getText()?.text.orEmpty()
                                    if (IncomingLinkValidator.validate(pasted) != null) {
                                        url = pasted
                                        profileViewModel.reset()
                                        selected.clear()
                                        formats = listOf(defaultFormat)
                                        chosenFormat = defaultFormat
                                        message = ""
                                    } else message = "El portapapeles no contiene un enlace válido."
                                },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, TechBorder)
                            ) { Text("▣ Pegar") }
                        TechPrimaryButton(
                            modifier = Modifier.weight(1f),
                            enabled = !profileState.loading && !profileState.loadingMore && url.isNotBlank(),
                            onClick = {
                                when (val parsed = IncomingLinkValidator.validate(url)) {
                                    null -> message = "Pega un enlace HTTPS válido de TikTok, YouTube, Facebook o Instagram."
                                    else -> {

                                        
                                        selected.clear()
                                        profileViewModel.inspect(url, forceRefresh)
                                        forceRefresh = false
                                        message = ""

                                    }
                                }
                            },
                            text = if (profileState.loading) "Analizando…" else "⌕ Analizar",
                        )
                        }

                        // Force refresh remains available from the analysis action on repeat requests.
                        if (profileState.loading || profileState.loadingMore || formatsBusy || queueBusy) CircularProgressIndicator()
                        if (profileState.countingTotal && videos.isNotEmpty()) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = TechPurple)
                            Text("Detectando publicaciones… ${videos.size} cargadas. Puedes seguir explorando.", color = TechMuted, fontSize = 12.sp)
                        }
                        profileState.error?.let { error ->
                            Column {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                if (videos.isNotEmpty() && profileState.hasMore && !profileState.countingTotal) {
                                    OutlinedButton(onClick = { profileViewModel.resumeScan() }) { Text("↻ Reanudar escaneo") }
                                }
                            }
                        }
                        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)


                        if (videos.isNotEmpty()) {
                            val downloadedIds = remember(records) { records.asSequence().filter { it.status == DownloadState.COMPLETED }.map { it.videoId }.toSet() }
                            val filteredVideos = remember(videos, search, onlyUndownloaded, downloadedIds) { videos.filter {
                                (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.id.contains(search)) &&
                                    (!onlyUndownloaded || it.id !in downloadedIds)
                            } }
                            val visibleVideos = filteredVideos
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
                                border = BorderStroke(1.dp, TechBorder)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(52.dp).background(TechGradient, RoundedCornerShape(26.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Text("@", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(url.substringAfter("tiktok.com/").substringBefore("?").substringBefore("/video/").take(35)
                                            .ifBlank { selectedPlatform ?: "Perfil" }, fontWeight = FontWeight.Bold,
                                            color = Color.White, maxLines = 1)
                                        Text("${videos.size} videos detectados", color = TechMuted, fontSize = 12.sp)
                                    }
                                    OutlinedButton(onClick = { profileViewModel.resumeScan() },
                                        enabled = !profileState.countingTotal && profileState.hasMore) { Text("↻") }
                                }
                            }
                            val allSelected = videos.isNotEmpty() && videos.all { it.id in selected }
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), border = BorderStroke(1.dp, TechBorder)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    listOf(
                                        "Total videos" to (profileState.totalVideos?.toString() ?: if (profileState.countingTotal) "…" else "—"),
                                        "Cargados" to videos.size.toString(),
                                        "Seleccionados" to selected.size.toString()
                                    ).forEach { (label, value) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(label, fontSize = 11.sp, color = TechMuted)
                                            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = {
                                    selected.clear()
                                    if (!allSelected) selected.addAll(videos.map { it.id })
                                    formats = listOf(defaultFormat)
                                    chosenFormat = defaultFormat
                                }) { Text(if (allSelected) "Quitar selección" else "Seleccionar todos", maxLines = 1) }
                            }
                            
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Buscar videos") },
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = onlyUndownloaded, onClick = { onlyUndownloaded = !onlyUndownloaded }, label = { Text("Pendientes") })
                                FilterChip(selected = gridMode, onClick = { gridMode = true }, label = { Text("Cuadrícula") })
                                FilterChip(selected = !gridMode, onClick = { gridMode = false }, label = { Text("Lista") })
                            }
                            if (gridMode) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 112.dp),
                                    modifier = Modifier.fillMaxWidth().height((if (visibleVideos.size == 1) 190 else ((visibleVideos.size + 1) / 2 * 145).coerceAtMost(580)).dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    gridItems(visibleVideos, key = { it.id }) { video ->
                                        Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, TechBorder), onClick = {
                                            if (video.id in selected) selected.remove(video.id) else selected.add(video.id)
                                            formats = listOf(defaultFormat)
                                            chosenFormat = defaultFormat
                                        }) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                VideoThumbnail(video, thumbnailResolver, Modifier.fillMaxWidth().height(if (visibleVideos.size == 1) 110.dp else 105.dp))
                                                Text(video.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(if (video.id in selected) "☑" else "□",
                                                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            } else LazyColumn(
                                modifier = Modifier.fillMaxWidth().height((visibleVideos.size * 100).coerceAtMost(500).coerceAtLeast(100).dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(visibleVideos, key = { it.id }) { video ->
                                    val checked = video.id in selected
                                    Card(modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, TechBorder)) {
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
                                            VideoThumbnail(video, thumbnailResolver, Modifier.width(116.dp).aspectRatio(16f / 10f))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    video.id,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                )
                                                Text(if (video.id in downloadedIds) "✓ Descargado" else "Disponible", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                    }
                                }
                            }

                            if (profileState.countingTotal) {
                                Text("Cargando más videos… ${videos.size} encontrados", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                            }


                        }
                    }

                    1 -> {
                        val finished = records.count { it.status == DownloadState.COMPLETED }
                        val pending = records.count { it.status == DownloadState.QUEUED || it.status == DownloadState.DOWNLOADING }
                        val paused = records.count { it.status == DownloadState.PAUSED }
                        val failed = records.count { it.status == DownloadState.FAILED }

                        Text("Cola de descargas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("$finished completadas · $pending pendientes · $failed errores")
                        

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
                        Text("Biblioteca", style = MaterialTheme.typography.titleLarge)
                        Text("${completed.size} descargados")
                        
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
                        Text("Monitoreo de perfiles", style = MaterialTheme.typography.titleLarge)
                        
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
                        Text("Acerca de", style = MaterialTheme.typography.titleLarge)
                        Text("Tikitoki Downloader · v0.5")
                        Text("Motor yt-dlp", style = MaterialTheme.typography.titleMedium)
                        val updateStatus = remember { context.getSharedPreferences("extractor_status", android.content.Context.MODE_PRIVATE) }
                        val lastResult = updateStatus.getString("last_result", "Actualización aún no ejecutada.")
                        Text(lastResult ?: "", color = TechMuted)
                        val lastSuccess = updateStatus.getLong("last_success", 0L)
                        if (lastSuccess > 0L) Text("Última actualización: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSuccess)))
                        OutlinedButton(onClick = {
                            ExtractorUpdateWorker.updateNow(context)
                            message = "Actualización solicitada. Se ejecutará sin descargas pendientes."
                        }) { Text("↻ Actualizar yt-dlp ahora") }
                        Text(message, style = MaterialTheme.typography.bodySmall, color = TechMuted)
                        Text("Archivos: Descargas/TikitokiDownloader")
                        
                        Text("Uso de contenido autorizado · GPL-3.0")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadHistoryCard(record: DownloadRecord, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, TechBorder)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!record.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = record.thumbnail,
                        contentDescription = "Miniatura",
                        modifier = Modifier.width(106.dp).aspectRatio(16f / 10f),
                        contentScale = ContentScale.Crop,
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

@Composable
private fun VideoThumbnail(
    video: VideoEntry,
    resolver: VideoThumbnailResolver,
    modifier: Modifier = Modifier,
) {
    val resolved by produceState<String?>(initialValue = video.thumbnail, key1 = video.url, key2 = video.thumbnail) {
        value = video.thumbnail ?: resolver.resolve(video)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!resolved.isNullOrBlank()) {
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(resolved)
                            .crossfade(true)
                            .apply { if (video.url.contains("tiktok.com")) addHeader("Referer", "https://www.tiktok.com/") }
                            .build(),
                        contentDescription = "Miniatura del video: ${video.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = TechPurple,
                                strokeWidth = 2.dp
                            )
                        },
                        error = {
                            Text("▶", style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
            } else {
                Text("▶", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
