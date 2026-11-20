package com.tikitoki.downloader.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_QUERY = "música popular Perú rock cumbia salsa"
private const val RECOMMENDATION_LIMIT = 12

private val rockolaSearchLock = Mutex()

private val rockolaGenres =
    listOf("Salsa", "Cumbia", "Reguetón", "Rock", "Pop", "Bachata", "Criolla", "Huayno", "Electrónica")

private enum class RockolaTab(
    val label: String,
) {
    EXPLORE("Explorar"),
    QUEUE("En cola"),
    LISTS("Mis listas"),
}

private enum class RockolaCategory(
    val label: String,
    val query: String,
) {
    ALL("Todo", DEFAULT_QUERY),
    MUSIC("Música", "música popular salsa cumbia rock Perú"),
    CHRISTIAN("Cristiana", "música cristiana alabanzas devocionales"),
    REFLECTIONS("Reflexiones", "reflexiones y mensajes para la vida"),
    NEWS("Noticias", "noticias del día Perú"),
}

private data class RockolaSearch(
    val text: String,
    val revision: Int = 0,
    val limit: Int = RECOMMENDATION_LIMIT,
)

@Suppress("ktlint:standard:function-naming", "LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
@Composable
internal fun RockolaScreen(
    source: UniversalMediaSource,
    thumbnailResolver: VideoThumbnailResolver,
    onDownload: (VideoEntry) -> Unit,
    onOpenYouTube: (VideoEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("rockola", Context.MODE_PRIVATE) }
    val library = remember(prefs) { RockolaLibrary(prefs) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val outerScroll = rememberScrollState()
    val listScroll = rememberScrollState()
    var tab by remember { mutableStateOf(RockolaTab.EXPLORE) }
    var category by remember { mutableStateOf(RockolaCategory.ALL) }
    var genre by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var search by remember { mutableStateOf(RockolaSearch(DEFAULT_QUERY)) }
    var results by remember { mutableStateOf(emptyList<VideoEntry>()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var openedList by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<RockolaSelection?>(null) }
    var requestId by remember { mutableIntStateOf(0) }
    var popup by remember { mutableStateOf<RockolaPopup?>(null) }

    fun play(video: VideoEntry) {
        val videoId = video.rockolaVideoId()
        if (videoId != null) {
            selection = RockolaSelection(video, videoId, ++requestId)
            library.remove(video)
        }
    }

    fun next() {
        library.queue.firstOrNull { it.rockolaVideoId() != null }?.let(::play)
    }

    fun submitSearch() {
        val clean = query.trim()
        if (clean.isEmpty()) return
        genre = null
        category = RockolaCategory.ALL
        tab = RockolaTab.EXPLORE
        search = RockolaSearch(clean, search.revision + 1, limit = 30)
        focusManager.clearFocus()
        keyboard?.hide()
    }

    BackHandler(enabled = tab == RockolaTab.LISTS && openedList != null) {
        openedList = null
    }
    LaunchedEffect(tab, search, openedList) { listScroll.scrollTo(0) }
    LaunchedEffect(search) {
        loading = true
        message = ""
        results = emptyList()
        try {
            results =
                rockolaSearchLock.withLock {
                    findRockolaVideos(source, search).filter { it.rockolaVideoId() != null }.distinctBy { it.id }
                }
            if (results.isEmpty()) message = "No se encontraron videos."
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalArgumentException) {
            message = "Pega un enlace válido de YouTube."
        } catch (e: Exception) {
            DiagnosticHistory.record(context, e.message)
            message = "No se pudo consultar YouTube. Comprueba tu conexión."
        } finally {
            loading = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(RockolaColors.Background)) {
        // Keep the same player instance when landscape or the keyboard reduces the space.
        val compact = maxHeight < 590.dp
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(outerScroll) else Modifier)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RockolaHeader()
            RockolaSearchField(query, { query = it }, ::submitSearch)
            RockolaPlayer(
                selection = selection,
                hasNext = library.queue.isNotEmpty(),
                onNext = ::next,
                onEnded = ::next,
                onStarted = library::recordPlayed,
                onRetry = { selection?.track?.let(::play) },
                onOpenInYouTube = { selection?.track?.let(onOpenYouTube) },
            )
            RockolaTabs(tab, library.queue.size) { tab = it }
            Column(
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f).verticalScroll(listScroll),
            ) {
                when (tab) {
                    RockolaTab.EXPLORE -> {
                        RockolaCategories(
                            category = category,
                            genre = genre,
                            onCategory = {
                                category = it
                                genre = null
                                query = ""
                                search = RockolaSearch(it.query, search.revision + 1)
                            },
                            onGenre = {
                                genre = it
                                query = ""
                                search = RockolaSearch("$it música popular", search.revision + 1)
                            },
                        )
                        RockolaSearchResults(
                            loading = loading,
                            message = message,
                            results = results,
                            onRetry = { search = search.copy(revision = search.revision + 1) },
                        ) { track ->
                            RockolaTrackRow(track, thumbnailResolver, onPlay = { play(track) }) {
                                RockolaAddButton(track, library, selection)
                                RockolaMoreButton { popup = RockolaPopup.TrackActions(track) }
                            }
                        }
                    }
                    RockolaTab.QUEUE -> {
                        if (library.queue.isEmpty()) RockolaMessage("Agrega videos desde Explorar o Mis listas.")
                        library.queue.forEachIndexed { index, track ->
                            RockolaTrackRow(track, thumbnailResolver, onPlay = { play(track) }) {
                                IconButton(onClick = { library.moveUp(track) }, enabled = index > 0) {
                                    Icon(
                                        Icons.Default.ExpandLess,
                                        contentDescription = "Subir ${track.title}",
                                        tint = if (index > 0) RockolaColors.Text else RockolaColors.Edge,
                                    )
                                }
                                IconButton(onClick = { library.remove(track) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar ${track.title}",
                                        tint = RockolaColors.Muted,
                                    )
                                }
                            }
                        }
                    }
                    RockolaTab.LISTS -> {
                        RockolaLists(library, openedList, { openedList = it }) { track ->
                            RockolaTrackRow(track, thumbnailResolver, onPlay = { play(track) }) {
                                RockolaAddButton(track, library, selection)
                                RockolaMoreButton { popup = RockolaPopup.TrackActions(track) }
                            }
                        }
                    }
                }
            }
        }
    }

    RockolaDialogs(
        popup = popup,
        preferences = prefs,
        library = library,
        onPopup = { popup = it },
        onOpenYouTube = onOpenYouTube,
        onDownload = onDownload,
    )
}

private suspend fun findRockolaVideos(
    source: UniversalMediaSource,
    search: RockolaSearch,
): List<VideoEntry> {
    val link = IncomingLinkValidator.validate(search.text) ?: IncomingLinkValidator.extractFirstHttps(search.text)
    if (link != null) {
        require(link.platform == MediaPlatform.YOUTUBE)
        return source.inspect(link, count = search.limit)
    }
    require(!search.text.contains("://"))
    return source.searchYouTube(search.text, limit = search.limit)
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(RockolaColors.Green, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = RockolaColors.Ink)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("Baja.pe", color = RockolaColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("Rockola", color = RockolaColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.size(width = 22.dp, height = 16.dp).background(RockolaColors.YouTube, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
        Text("YouTube", Modifier.padding(start = 6.dp), color = RockolaColors.Muted, fontSize = 12.sp)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaSearchField(
    query: String,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Canción, artista o enlace", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        leadingIcon = {
            IconButton(onClick = onSearch, enabled = query.isNotBlank()) {
                Icon(Icons.Default.Search, contentDescription = "Buscar en YouTube", tint = RockolaColors.Muted)
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = RockolaColors.Text,
                unfocusedTextColor = RockolaColors.Text,
                cursorColor = RockolaColors.Green,
                focusedPlaceholderColor = RockolaColors.Muted,
                unfocusedPlaceholderColor = RockolaColors.Muted,
                focusedBorderColor = RockolaColors.Green,
                unfocusedBorderColor = RockolaColors.Edge,
                focusedContainerColor = RockolaColors.Surface,
                unfocusedContainerColor = RockolaColors.Surface,
            ),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaTabs(
    selected: RockolaTab,
    count: Int,
    onSelect: (RockolaTab) -> Unit,
) {
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        RockolaTab.entries.forEach { tab ->
            val active = selected == tab
            Column(
                Modifier
                    .weight(1f)
                    .selectable(selected = active, onClick = { onSelect(tab) }, role = Role.Tab),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.heightIn(min = 42.dp).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (tab == RockolaTab.QUEUE) "${tab.label} · $count" else tab.label,
                        color = if (active) RockolaColors.Green else RockolaColors.Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider(thickness = 2.dp, color = if (active) RockolaColors.Green else RockolaColors.Edge)
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RockolaCategories(
    category: RockolaCategory,
    genre: String?,
    onCategory: (RockolaCategory) -> Unit,
    onGenre: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        RockolaCategory.entries.forEach {
            RockolaPill(it.label, it == category) { onCategory(it) }
        }
    }
    if (category == RockolaCategory.MUSIC) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rockolaGenres.forEach {
                RockolaPill(it, it == genre) { onGenre(it) }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.heightIn(min = 42.dp).selectable(selected, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            Modifier
                .background(if (selected) RockolaColors.Green else RockolaColors.Surface, shape)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            color = if (selected) RockolaColors.Ink else RockolaColors.Muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
