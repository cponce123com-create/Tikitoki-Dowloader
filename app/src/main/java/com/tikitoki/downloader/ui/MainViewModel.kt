package com.tikitoki.downloader.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.*
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.PlaybackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainViewModel holds all UI state for the app, replacing the mutableStateOf
 * variables that were previously in AppContent. This enables better testability,
 * separation of concerns, and reactive state management.
 */
class MainViewModel(
    application: Application,
    private val profileViewModel: ProfileViewModel,
    private val downloadsViewModel: DownloadsViewModel,
    private val source: TikTokVideoSource,
    private val universalSource: UniversalMediaSource,
    private val thumbnailResolver: VideoThumbnailResolver,
    private val avatarResolver: ProfileAvatarResolver,
    private val galleryImageLoader: ImageLoader,
    private val dao: DownloadDao,
    private val settings: SharedPreferences,
) : AndroidViewModel(application) {

    // Settings state
    private val _preferredQuality = MutableStateFlow(settings.getString("quality", "Máxima") ?: "Máxima")
    val preferredQuality: StateFlow<String> = _preferredQuality

    private val _themeChoice = MutableStateFlow(settings.getString("theme", "Oscuro") ?: "Oscuro")
    val themeChoice: StateFlow<String> = _themeChoice

    private val _wifiParallel = MutableStateFlow(DownloadSettings.wifiLimit(getApplication<Context>()))
    val wifiParallel: StateFlow<Int> = _wifiParallel

    private val _meteredParallel = MutableStateFlow(DownloadSettings.meteredLimit(getApplication<Context>()))
    val meteredParallel: StateFlow<Int> = _meteredParallel

    private val _allowMetered = MutableStateFlow(DownloadSettings.allowMetered(getApplication<Context>()))
    val allowMetered: StateFlow<Boolean> = _allowMetered

    private val _showProgressNotice = MutableStateFlow(DownloadSettings.showProgress(getApplication<Context>()))
    val showProgressNotice: StateFlow<Boolean> = _showProgressNotice

    private val _completionNotice = MutableStateFlow(DownloadSettings.notifyCompletion(getApplication<Context>()))
    val completionNotice: StateFlow<Boolean> = _completionNotice

    private val _rateKiB = MutableStateFlow(DownloadSettings.rateKiB(getApplication<Context>()))
    val rateKiB: StateFlow<Int> = _rateKiB

    private val _monitorHours = MutableStateFlow(ProfileMonitor.interval(getApplication<Context>()))
    val monitorHours: StateFlow<Int> = _monitorHours

    private val _monitorPages = MutableStateFlow(ProfileMonitor.pageLimit(getApplication<Context>()))
    val monitorPages: StateFlow<Int> = _monitorPages

    private val _networkSettingsBusy = MutableStateFlow(false)
    val networkSettingsBusy: StateFlow<Boolean> = _networkSettingsBusy

    // Dialog state
    private val _showClearTemp = MutableStateFlow(false)
    val showClearTemp: StateFlow<Boolean> = _showClearTemp

    private val _showPlayerChoice = MutableStateFlow(false)
    val showPlayerChoice: StateFlow<Boolean> = _showPlayerChoice

    private val _showAbout = MutableStateFlow(false)
    val showAbout: StateFlow<Boolean> = _showAbout

    // Download folder state
    private val _downloadFolder = MutableStateFlow(settings.getString("download_tree", null))
    val downloadFolder: StateFlow<String?> = _downloadFolder

    // Folder description - computed from downloadFolder
    private val _folderDescription = MutableStateFlow("Comprobando carpeta…")
    val folderDescription: StateFlow<String> = _folderDescription

    // Navigation state
    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab

    private val _musicRequestUri = MutableStateFlow<String?>(null)
    val musicRequestUri: StateFlow<String?> = _musicRequestUri

    private val _musicRequestTitle = MutableStateFlow("")
    val musicRequestTitle: StateFlow<String> = _musicRequestTitle

    // Home/Analysis state
    private val _gridMode = MutableStateFlow(true)
    val gridMode: StateFlow<Boolean> = _gridMode

    private val _selectedPlatform = MutableStateFlow<String?>("Auto")
    val selectedPlatform: StateFlow<String?> = _selectedPlatform

    private val _deleteCandidate = MutableStateFlow<DownloadRecord?>(null)
    val deleteCandidate: StateFlow<DownloadRecord?> = _deleteCandidate

    private val _monitorRevision = MutableStateFlow(0)
    val monitorRevision: StateFlow<Int> = _monitorRevision

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search

    private val _onlyUndownloaded = MutableStateFlow(false)
    val onlyUndownloaded: StateFlow<Boolean> = _onlyUndownloaded

    private val _libraryKind = MutableStateFlow("Videos")
    val libraryKind: StateFlow<String> = _libraryKind

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _inspecting = MutableStateFlow(false)
    val inspecting: StateFlow<Boolean> = _inspecting

    private val _forceRefresh = MutableStateFlow(false)
    val forceRefresh: StateFlow<Boolean> = _forceRefresh

    private val _queueBusy = MutableStateFlow(false)
    val queueBusy: StateFlow<Boolean> = _queueBusy

    private val _qualityVideos = MutableStateFlow(emptyList<VideoEntry>())
    val qualityVideos: StateFlow<List<VideoEntry>> = _qualityVideos

    private val _pendingDownloads = MutableStateFlow(emptyList<DownloadSelection>())
    val pendingDownloads: StateFlow<List<DownloadSelection>> = _pendingDownloads

    private val _analysisRevision = MutableStateFlow(0)
    val analysisRevision: StateFlow<Int> = _analysisRevision

    // Computed state from other ViewModels
    val profileState = profileViewModel.state
    val records = downloadsViewModel.records
    val downloadSpeeds = DownloadTelemetry.speeds
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap<String, String>())

    val finishedBatches = dao.observeFinishedBatches()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BatchSummary>())

    // Selected videos map
    private val _selected = MutableStateFlow(mutableMapOf<String, Boolean>())
    val selected: StateFlow<Map<String, Boolean>> = _selected

    // Context for UI
    val context: Context = getApplication<Context>()
    val invalidLinkMessage = getApplication<Context>().getString(R.string.media_invalid_link)

    companion object {
        private const val CLIPBOARD_DEBOUNCE_MS = 500L
        private const val MAX_PARALLEL_DOWNLOADS = 8
    }

    init {
        updateFolderDescription()
    }

    private fun updateFolderDescription() {
        val tree = _downloadFolder.value
        _folderDescription.value = if (tree == null) {
            "Descargas/TikitokiDownloader"
        } else {
            "Comprobando carpeta…"
        }
        // Async check
        viewModelScope.launch {
            val description = withContext(Dispatchers.IO) {
                runCatching {
                    val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(tree))
                    if (folder != null && folder.exists() && folder.canWrite()) {
                        folder.name ?: "Carpeta personalizada"
                    } else {
                        "Carpeta no disponible. Elige otra ubicación."
                    }
                }.getOrElse { "Carpeta no disponible. Elige otra ubicación." }
            }
            _folderDescription.value = description
        }
    }

    // Settings actions
    fun setPreferredQuality(value: String) {
        _preferredQuality.value = value
        settings.edit().putString("quality", value).apply()
    }

    fun setThemeChoice(value: String) {
        _themeChoice.value = value
        settings.edit().putString("theme", value).apply()
    }

    fun setWifiParallel(value: Int) {
        val clamped = value.coerceIn(1, MAX_PARALLEL_DOWNLOADS)
        _wifiParallel.value = clamped
        settings.edit().putInt("wifi_parallel", clamped).apply()
    }

    fun setMeteredParallel(value: Int) {
        val clamped = value.coerceIn(1, MAX_PARALLEL_DOWNLOADS)
        _meteredParallel.value = clamped
        settings.edit().putInt("metered_parallel", clamped).apply()
    }

    fun setAllowMetered(allowed: Boolean) {
        _networkSettingsBusy.value = true
        viewModelScope.launch {
            try {
                val pending = DownloadQueue.changeMeteredPolicy(context, allowed)
                _allowMetered.value = allowed
                _message.value = if (pending) {
                    "Cola pausada para cambiar la red. Pulsa Continuar en Descargas."
                } else {
                    "Preferencia de red actualizada."
                }
            } catch (e: Exception) {
                DiagnosticHistory.record(context, e.message)
                _message.value = "No se pudo cambiar la preferencia de red."
            } finally {
                _networkSettingsBusy.value = false
            }
        }
    }

    fun setRateKiB(rate: Int) {
        _rateKiB.value = rate
        settings.edit().putInt("rate_kib", rate).apply()
    }

    fun setMonitorHours(hours: Int) {
        _monitorHours.value = hours
        ProfileMonitor.setInterval(context, hours)
    }

    fun setMonitorPages(pages: Int) {
        _monitorPages.value = pages
        ProfileMonitor.setPageLimit(context, pages)
    }

    fun setShowProgressNotice(value: Boolean) {
        _showProgressNotice.value = value
        settings.edit().putBoolean("notify_progress_detail", value).apply()
    }

    fun setCompletionNotice(value: Boolean) {
        _completionNotice.value = value
        settings.edit().putBoolean("notify_completion", value).apply()
    }

    // Dialog actions
    fun setShowClearTemp(value: Boolean) { _showClearTemp.value = value }
    fun setShowPlayerChoice(value: Boolean) { _showPlayerChoice.value = value }
    fun setShowAbout(value: Boolean) { _showAbout.value = value }

    // Navigation actions
    fun setTab(value: Int) { _tab.value = value }
    fun setMusicRequestUri(value: String?) { _musicRequestUri.value = value }
    fun setMusicRequestTitle(value: String) { _musicRequestTitle.value = value }
    fun setGridMode(value: Boolean) { _gridMode.value = value }
    fun setSelectedPlatform(value: String?) { _selectedPlatform.value = value }
    fun setDeleteCandidate(value: DownloadRecord?) { _deleteCandidate.value = value }
    fun incrementMonitorRevision() { _monitorRevision.value = _monitorRevision.value + 1 }
    fun setUrl(value: String) { _url.value = value }
    fun setSearch(value: String) { _search.value = value }
    fun setOnlyUndownloaded(value: Boolean) { _onlyUndownloaded.value = value }
    fun setLibraryKind(value: String) { _libraryKind.value = value }
    fun setMessage(value: String) { _message.value = value }
    fun setInspecting(value: Boolean) { _inspecting.value = value }
    fun setForceRefresh(value: Boolean) { _forceRefresh.value = value }
    fun setQueueBusy(value: Boolean) { _queueBusy.value = value }
    fun setQualityVideos(value: List<VideoEntry>) { _qualityVideos.value = value }
    fun setPendingDownloads(value: List<DownloadSelection>) { _pendingDownloads.value = value }
    fun incrementAnalysisRevision() { _analysisRevision.value = _analysisRevision.value + 1 }

    // Selected videos map
    fun setSelected(map: Map<String, Boolean>) { _selected.value = map.toMutableMap() }
    fun clearSelected() { _selected.value.clear() }
    fun toggleSelected(videoId: String, checked: Boolean) {
        _selected.value = _selected.value.toMutableMap().also { it[videoId] = checked }
    }

    // Download folder
    fun setDownloadFolder(value: String?) {
        _downloadFolder.value = value
        if (value != null) {
            settings.edit().putString("download_tree", value).apply()
        } else {
            settings.edit().remove("download_tree").apply()
        }
        updateFolderDescription()
    }

    // Main actions
    fun handleIncomingLink(incomingLink: String, shareRevision: Int) {
        if (incomingLink.isNotBlank() && (shareRevision > 0 || !profileViewModel.isCurrentLink(incomingLink))) {
            _url.value = incomingLink
            profileViewModel.reset()
            clearSelected()
            _tab.value = 0
            _message.value = ""
            incrementAnalysisRevision()
        }
    }

    fun handleUrlChange(newUrl: String) {
        val link = IncomingLinkValidator.validate(newUrl)?.url ?: return
        if (!profileViewModel.isCurrentLink(link)) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(CLIPBOARD_DEBOUNCE_MS)
                if (!profileViewModel.isCurrentLink(link)) {
                    profileViewModel.inspect(link, false)
                }
            }
        }
    }

    private val addToQueue: (List<DownloadSelection>) -> Unit = { items ->
        if (!_queueBusy.value && items.isNotEmpty()) {
            _queueBusy.value = true
            viewModelScope.launch {
                try {
                    val result = DownloadQueue.enqueue(context, items)
                    _message.value = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                    if (result.added > 0 || result.duplicates > 0) {
                        items.forEach { clearSelected() }
                    }
                    if (_selected.value.isEmpty()) _tab.value = 1
                } catch (e: Exception) {
                    _message.value = "No se pudo crear la cola. Inténtalo otra vez."
                } finally {
                    _queueBusy.value = false
                }
            }
        }
    }

    fun requestDownload(items: List<DownloadSelection>) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _pendingDownloads.value = items
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Notifications permission request would be launched from UI
            }
            addToQueue(items)
        }
    }

    fun onStoragePermissionGranted(granted: Boolean) {
        val items = _pendingDownloads.value
        _pendingDownloads.value = emptyList()
        if (granted) {
            addToQueue(items)
        } else {
            _message.value = "Android necesita permiso para guardar en Descargas."
        }
    }

    fun chooseQuality() {
        if (!_queueBusy.value) {
            val videos = profileState.value.videos
            _qualityVideos.value = videos.filter { it.id in _selected.value }
        }
    }

    fun openPlayback(record: DownloadRecord) {
        val saved = record.savedUri
        if (saved.isNullOrBlank()) {
            _message.value = "No se encontró la ubicación del archivo."
        } else {
            viewModelScope.launch {
                try {
                    val uri = Uri.parse(saved)
                    val (mime, name) = withContext(Dispatchers.IO) {
                        if (uri.scheme == "content") {
                            val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                            val displayName = runCatching {
                                context.contentResolver
                                    .query(
                                        uri,
                                        arrayOf(DocumentsContract.DISPLAY_NAME),
                                        null,
                                        null,
                                        null,
                                    )?.use { if (it.moveToFirst()) it.getString(0) else null }
                            }.getOrNull()
                            type to (displayName ?: uri.lastPathSegment)
                        } else {
                            null to uri.lastPathSegment
                        }
                    }
                    if (PlaybackKind.isAudio(record.formatId, mime, name)) {
                        _musicRequestUri.value = saved
                        _musicRequestTitle.value = record.title
                        _tab.value = 6 // MUSIC_TAB
                    } else {
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            data = uri
                            putExtra("title", record.title)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    runCatching { DiagnosticHistory.record(context, e.message) }
                    _message.value = "No se pudo abrir el reproductor."
                }
            }
        }
    }

    fun requestDiagnosticExport(filename: String) {
        // Handled in UI via launcher
    }

    fun dismissFormatSheet() {
        _qualityVideos.value = emptyList()
    }

    fun saveDefaultFormat(id: String, label: String, height: Int?) {
        val preference = DownloadFormatPreference.fromChoice(id, label, height)
        settings.edit().putString("default_download_format", preference).apply()
    }

    fun acknowledgeBatch(batchId: String) {
        viewModelScope.launch { dao.acknowledgeBatch(batchId) }
    }

    fun clearTemporaryFiles() {
        _showClearTemp.value = false
        viewModelScope.launch {
            try {
                val deleted = DownloadQueue.clearTemporary(context)
                _message.value = "Archivos temporales eliminados: $deleted."
            } catch (e: Exception) {
                DiagnosticHistory.record(context, e.message)
                _message.value = "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas."
            }
        }
    }

    fun confirmDelete(item: DownloadRecord) {
        _deleteCandidate.value = null
        viewModelScope.launch {
            try {
                _message.value = when (DownloadQueue.deleteCompleted(context, item.videoId)) {
                    DeleteCompletedResult.DELETED -> "Video eliminado del dispositivo y del historial."
                    DeleteCompletedResult.FILE_MISSING ->
                        "El archivo ya no existía. Se eliminó del historial."
                    DeleteCompletedResult.FAILED ->
                        "No se pudo eliminar el archivo. El registro se conserva."
                }
            } catch (e: Exception) {
                _message.value = "No se pudo eliminar el archivo. Comprueba que siga disponible."
            }
        }
    }

    fun dismissDelete() { _deleteCandidate.value = null }

    fun onAboutDismiss() { _showAbout.value = false }
    fun onOpenSources() {
        runCatching {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/cponce123com-create/Tikitoki-Dowloader"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun onPlayerChoiceDismiss() { _showPlayerChoice.value = false }
    fun onChooseVideos() {
        _showPlayerChoice.value = false
        _libraryKind.value = "Videos"
        _tab.value = 2
    }
    fun onChooseMusic() {
        _showPlayerChoice.value = false
        _tab.value = 6
    }

    fun onFollowProfile(url: String) {
        if (ProfileMonitor.add(context, url.trim())) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Notification permission handled in UI
            }
            incrementMonitorRevision()
            _message.value = "Perfil agregado al monitoreo."
        } else {
            _message.value = "Pega un enlace válido de perfil en Buscar."
        }
    }

    fun onUnfollowProfile(profile: String) {
        ProfileMonitor.remove(context, profile)
        incrementMonitorRevision()
    }

    fun onRetryFailed() {
        viewModelScope.launch {
            DownloadQueue.retryFailed(context)
            _tab.value = 1
            _message.value = "Reintentando descargas."
        }
    }

    fun onClearErrors() {
        viewModelScope.launch { dao.clearFailed() }
    }

    fun onOpenPlayer() { _showPlayerChoice.value = true }

    fun onResetFolder() {
        settings.edit().remove("download_tree").apply()
        _downloadFolder.value = null
        updateFolderDescription()
    }

    fun openDownloadFolder() {
        viewModelScope.launch {
            try {
                val folder = _downloadFolder.value?.let { tree ->
                    val treeUri = Uri.parse(tree)
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri),
                    )
                } ?: DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download/TikitokiDownloader",
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK,
                    )
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                runCatching { DiagnosticHistory.record(context, e.message) }
                _message.value = "No se pudo abrir la carpeta. Busca ${_folderDescription.value} en tu administrador de archivos."
            }
        }
    }
}