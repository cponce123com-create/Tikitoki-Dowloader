package com.tikitoki.downloader.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadSettings
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.DownloadTelemetry
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.DownloadIdentity
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.domain.PlaybackKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Main ViewModel holding all UI state for the app.
 * Replaces the 50+ mutableStateOf in MainActivity.
 */
class MainViewModel(
    application: Application,
    private val settings: SharedPreferences,
    private val dao: com.tikitoki.downloader.data.DownloadDao,
    private val profileViewModel: ProfileViewModel,
    private val downloadsViewModel: DownloadsViewModel,
    private val thumbnailResolver: com.tikitoki.downloader.data.VideoThumbnailResolver,
    private val universalSource: com.tikitoki.downloader.data.UniversalMediaSource,
    private val source: com.tikitoki.downloader.data.TikTokVideoSource,
    private val avatarResolver: com.tikitoki.downloader.data.ProfileAvatarResolver,
) : AndroidViewModel(application) {

    // =========================================================================
    // SETTINGS STATE (persisted in SharedPreferences)
    // =========================================================================

    private val _preferredQuality = MutableStateFlow(settings.getString("quality", "Máxima") ?: "Máxima")
    val preferredQuality = _preferredQuality.asStateFlow().distinctUntilChanged()

    private val _themeChoice = MutableStateFlow(settings.getString("theme", "Oscuro") ?: "Oscuro")
    val themeChoice = _themeChoice.asStateFlow().distinctUntilChanged()

    private val _wifiParallel = MutableStateFlow(DownloadSettings.wifiLimit(getApplication()))
    val wifiParallel = _wifiParallel.asStateFlow().distinctUntilChanged()

    private val _meteredParallel = MutableStateFlow(DownloadSettings.meteredLimit(getApplication()))
    val meteredParallel = _meteredParallel.asStateFlow().distinctUntilChanged()

    private val _allowMetered = MutableStateFlow(DownloadSettings.allowMetered(getApplication()))
    val allowMetered = _allowMetered.asStateFlow().distinctUntilChanged()

    private val _showProgressNotice = MutableStateFlow(DownloadSettings.showProgress(getApplication()))
    val showProgressNotice = _showProgressNotice.asStateFlow().distinctUntilChanged()

    private val _completionNotice = MutableStateFlow(DownloadSettings.notifyCompletion(getApplication()))
    val completionNotice = _completionNotice.asStateFlow().distinctUntilChanged()

    private val _rateKiB = MutableStateFlow(DownloadSettings.rateKiB(getApplication()))
    val rateKiB = _rateKiB.asStateFlow().distinctUntilChanged()

    private val _monitorHours = MutableStateFlow(ProfileMonitor.interval(settings))
    val monitorHours = _monitorHours.asStateFlow().distinctUntilChanged()

    private val _monitorPages = MutableStateFlow(ProfileMonitor.pageLimit(settings))
    val monitorPages = _monitorPages.asStateFlow().distinctUntilChanged()

    private val _downloadFolder = MutableStateFlow(settings.getString("download_tree", null))
    val downloadFolder = _downloadFolder.asStateFlow().distinctUntilChanged()

    // Expose thumbnailResolver for screens that need it
    val thumbnailResolver = thumbnailResolver

    // =========================================================================
    // NAVIGATION STATE
    // =========================================================================

    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow().distinctUntilChanged()

    private val _gridMode = MutableStateFlow(true)
    val gridMode = _gridMode.asStateFlow().distinctUntilChanged()

    private val _selectedPlatform = MutableStateFlow<String?>("Auto")
    val selectedPlatform = _selectedPlatform.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // HOME / ANALYSIS STATE
    // =========================================================================

    private val _url = MutableStateFlow("")
    val url = _url.asStateFlow().distinctUntilChanged()

    private val _search = MutableStateFlow("")
    val search = _search.asStateFlow().distinctUntilChanged()

    private val _onlyUndownloaded = MutableStateFlow(false)
    val onlyUndownloaded = _onlyUndownloaded.asStateFlow().distinctUntilChanged()

    private val _forceRefresh = MutableStateFlow(false)
    val forceRefresh = _forceRefresh.asStateFlow().distinctUntilChanged()

    private val _formatsBusy = MutableStateFlow(false)
    val formatsBusy = _formatsBusy.asStateFlow().distinctUntilChanged()

    private val _formats = MutableStateFlow<List<com.tikitoki.downloader.data.FormatChoice>>(emptyList())
    val formats = _formats.asStateFlow().distinctUntilChanged()

    private val _chosenFormat = MutableStateFlow<com.tikitoki.downloader.data.FormatChoice?>(null)
    val chosenFormat = _chosenFormat.asStateFlow().distinctUntilChanged()

    private val _rememberFormat = MutableStateFlow(false)
    val rememberFormat = _rememberFormat.asStateFlow().distinctUntilChanged()

    private val _formatError = MutableStateFlow(false)
    val formatError = _formatError.asStateFlow().distinctUntilChanged()

    private val _showQualitySheet = MutableStateFlow(false)
    val showQualitySheet = _showQualitySheet.asStateFlow().distinctUntilChanged()

    private val _inspecting = MutableStateFlow(false)
    val inspecting = _inspecting.asStateFlow().distinctUntilChanged()

    // Batch format checking state
    private val _batchChecking = MutableStateFlow(false)
    val batchChecking = _batchChecking.asStateFlow().distinctUntilChanged()

    private val _batchChecked = MutableStateFlow(0)
    val batchChecked = _batchChecked.asStateFlow().distinctUntilChanged()

    private val _batchFailed = MutableStateFlow(0)
    val batchFailed = _batchFailed.asStateFlow().distinctUntilChanged()

    private val _batchTotal = MutableStateFlow(0)
    val batchTotal = _batchTotal.asStateFlow().distinctUntilChanged()

    private val _batchCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val batchCounts = _batchCounts.asStateFlow().distinctUntilChanged()

    private val _batchGeneration = MutableStateFlow(0)
    val batchGeneration = _batchGeneration.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // LIBRARY STATE
    // =========================================================================

    private val _libraryKind = MutableStateFlow("Videos")
    val libraryKind = _libraryKind.asStateFlow().distinctUntilChanged()

    private val _historyFilter = MutableStateFlow("TODOS")
    val historyFilter = _historyFilter.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // TOOLS / PROFILE MONITOR STATE
    // =========================================================================

    private val _monitorRevision = MutableStateFlow(0)
    val monitorRevision = _monitorRevision.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // DOWNLOAD QUEUE STATE (from DownloadsViewModel)
    // =========================================================================

    val records = downloadsViewModel.records
    val downloadSpeeds = DownloadTelemetry.speeds.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // DIALOGS / OVERLAYS STATE
    // =========================================================================

    private val _showClearTemp = MutableStateFlow(false)
    val showClearTemp = _showClearTemp.asStateFlow().distinctUntilChanged()

    private val _showPlayerChoice = MutableStateFlow(false)
    val showPlayerChoice = _showPlayerChoice.asStateFlow().distinctUntilChanged()

    private val _showAbout = MutableStateFlow(false)
    val showAbout = _showAbout.asStateFlow().distinctUntilChanged()

    private val _showBrand = MutableStateFlow(true)
    val showBrand = _showBrand.asStateFlow().distinctUntilChanged()

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow().distinctUntilChanged()

    private val _deleteCandidate = MutableStateFlow<DownloadRecord?>(null)
    val deleteCandidate = _deleteCandidate.asStateFlow().distinctUntilChanged()

    // Finished batch dialog
    val finishedBatches = MutableStateFlow<List<com.tikitoki.downloader.data.BatchSummary>>(emptyList())
        .also { dao.observeFinishedBatches().onEach { it.update(it) }.launchIn(viewModelScope) }

    // Music player request
    private val _musicRequestUri = MutableStateFlow<String?>(null)
    val musicRequestUri = _musicRequestUri.asStateFlow().distinctUntilChanged()

    private val _musicRequestTitle = MutableStateFlow("")
    val musicRequestTitle = _musicRequestTitle.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // PROFILE STATE (from ProfileViewModel)
    // =========================================================================

    val profileState = profileViewModel.state

    // =========================================================================
    // NETWORK SETTINGS BUSY FLAG
    // =========================================================================

    private val _networkSettingsBusy = MutableStateFlow(false)
    val networkSettingsBusy = _networkSettingsBusy.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // SELECTED VIDEOS (for batch operations)
    // =========================================================================

    private val _selectedVideos = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val selectedVideos = _selectedVideos.asStateFlow().distinctUntilChanged()

    // =========================================================================
    // DEFAULT FORMAT (derived from preferredQuality)
    // =========================================================================

    private val _defaultFormat = MutableStateFlow<com.tikitoki.downloader.data.FormatChoice?>(null)
    val defaultFormat = _defaultFormat.asStateFlow().distinctUntilChanged()

    init {
        // Initialize default format
        updateDefaultFormat(_preferredQuality.value)

        // Hide brand after 1 second
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _showBrand.value = false
        }

        // Check for pending downloads on startup
        checkPendingRecovery()
    }

    // =========================================================================
    // SETTINGS ACTIONS
    // =========================================================================

    fun onThemeChange(option: String) {
        _themeChoice.value = option
        settings.edit().putString("theme", option).apply()
    }

    fun onQualityChange(option: String) {
        _preferredQuality.value = option
        settings.edit().putString("quality", option).apply()
        updateDefaultFormat(option)
    }

    fun onWifiChange(value: Int) {
        val clamped = value.coerceIn(1, 8)
        _wifiParallel.value = clamped
        settings.edit().putInt("wifi_parallel", clamped).apply()
    }

    fun onMeteredChange(value: Int) {
        val clamped = value.coerceIn(1, 8)
        _meteredParallel.value = clamped
        settings.edit().putInt("metered_parallel", clamped).apply()
    }

    fun onAllowMeteredChange(allowed: Boolean) {
        _networkSettingsBusy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pending = DownloadQueue.changeMeteredPolicy(getApplication(), allowed)
                _allowMetered.value = allowed
                _message.value = if (pending) {
                    "Cola pausada para cambiar la red. Pulsa Continuar en Descargas."
                } else {
                    "Preferencia de red actualizada."
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                com.tikitoki.downloader.data.DiagnosticHistory.record(getApplication(), e.message)
                _message.value = "No se pudo cambiar la preferencia de red."
            } finally {
                _networkSettingsBusy.value = false
            }
        }
    }

    fun onRateChange(rate: Int) {
        _rateKiB.value = rate
        settings.edit().putInt("rate_kib", rate).apply()
    }

    fun onProgressNoticeChange(enabled: Boolean) {
        _showProgressNotice.value = enabled
        settings.edit().putBoolean("notify_progress_detail", enabled).apply()
        if (enabled && android.os.Build.VERSION.SDK_INT >= 33 &&
            getApplication().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // Notification permission handled by UI
        }
    }

    fun onCompletionNoticeChange(enabled: Boolean) {
        _completionNotice.value = enabled
        settings.edit().putBoolean("notify_completion", enabled).apply()
    }

    fun onMonitorHoursChange(hours: Int) {
        _monitorHours.value = hours
        ProfileMonitor.setInterval(settings, hours)
    }

    fun onMonitorPagesChange(pages: Int) {
        _monitorPages.value = pages
        ProfileMonitor.setPageLimit(settings, pages)
    }

    fun onChooseFolder(folderPicker: androidx.activity.result.ActivityResultLauncher<android.content.Intent>) {
        folderPicker.launch(null)
    }

    fun onResetFolder() {
        settings.edit().remove("download_tree").apply()
        _downloadFolder.value = null
    }

    fun onFolderSelected(uri: android.net.Uri) {
        try {
            getApplication().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settings.edit().putString("download_tree", uri.toString()).apply()
            _downloadFolder.value = uri.toString()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            com.tikitoki.downloader.data.DiagnosticHistory.record(getApplication(), e.message)
            settings.edit().remove("download_tree").apply()
        }
    }

    // =========================================================================
    // NAVIGATION ACTIONS
    // =========================================================================

    fun onTabSelected(tab: Int) {
        _currentTab.value = tab
    }

    fun onGridModeChange(enabled: Boolean) {
        _gridMode.value = enabled
    }

    fun onSelectedPlatformChange(platform: String?) {
        _selectedPlatform.value = platform
    }

    // =========================================================================
    // HOME / ANALYSIS ACTIONS
    // =========================================================================

    fun onUrlChange(newUrl: String) {
        _url.value = newUrl
    }

    fun onSearchChange(query: String) {
        _search.value = query
    }

    fun onOnlyUndownloadedChange(enabled: Boolean) {
        _onlyUndownloaded.value = enabled
    }

    fun onForceRefreshChange(enabled: Boolean) {
        _forceRefresh.value = enabled
    }

    fun onInspectLink() {
        val link = IncomingLinkValidator.validate(_url.value)?.url ?: return
        _inspecting.value = true
        profileViewModel.inspect(link, _forceRefresh.value)
    }

    fun onResumeScan() {
        profileViewModel.resumeScan()
    }

    fun onSkipIncompletePage() {
        profileViewModel.skipIncompletePage()
    }

    fun chooseQuality(selectedIds: List<String>) {
        val videos = profileState.value.videos
        val preview = if (selectedIds.size == 1) videos.firstOrNull { it.id == selectedIds.first() } else null

        _batchGeneration.value++
        _batchChecking.value = false
        _batchChecked.value = 0
        _batchFailed.value = 0
        _batchTotal.value = 0
        _batchCounts.value = emptyMap()
        _formatError.value = false
        _showQualitySheet.value = true
        _formatsBusy.value = selectedIds.size == 1

        val currentDefault = _defaultFormat.value
        _formats.value = if (selectedIds.size == 1) listOf(currentDefault!!) else com.tikitoki.downloader.data.FormatOptions.batch(getApplication())
        _chosenFormat.value = _formats.value.first()
        _rememberFormat.value = false

        viewModelScope.launch {
            try {
                if (selectedIds.size == 1 && preview != null) {
                    val found = if (preview.id.contains(":")) universalSource.formats(preview) else source.formats(preview)
                    if (found.isEmpty()) throw IllegalStateException("No formats")
                    _formats.value = found
                }
                val saved = settings.getString("default_download_format", "video")
                _chosenFormat.value =
                    _formats.value.firstOrNull { DownloadFormatPreference.matches(saved, it.id, it.label, com.tikitoki.downloader.data.TikTokVideoSource.DEFAULT_FORMAT) }
                        ?: _formats.value.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kotlinx.coroutines.runCatching { com.tikitoki.downloader.data.DiagnosticHistory.record(getApplication(), e.message) }
                _message.value = "No se pudieron cargar las opciones de calidad. Inténtalo otra vez."
                _formatError.value = true
            } finally {
                _formatsBusy.value = false
            }
        }
    }

    fun onFormatSelected(format: com.tikitoki.downloader.data.FormatChoice) {
        _chosenFormat.value = format
    }

    fun onRememberFormatChange(checked: Boolean) {
        _rememberFormat.value = checked
    }

    fun onDownloadSelected(selectedIds: List<String>) {
        val format = _chosenFormat.value ?: return
        val videos = profileState.value.videos
        val batch = videos.filter { it.id in selectedIds }
        val formatId = format.id

        _queueBusy.value = true
        viewModelScope.launch {
            try {
                val result = DownloadQueue.enqueue(getApplication(), batch, formatId)
                _message.value = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                if (result.added > 0 || result.duplicates > 0) {
                    _selectedVideos.value = emptyMap()
                    onNavigateToDownloads()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = "No se pudo crear la cola. Inténtalo otra vez."
            } finally {
                _queueBusy.value = false
            }
        }
    }

    // =========================================================================
    // DOWNLOAD QUEUE ACTIONS
    // =========================================================================

    private var _queueBusy = MutableStateFlow(false)
    val queueBusy = _queueBusy.asStateFlow().distinctUntilChanged()

    fun onPauseQueue() {
        viewModelScope.launch { downloadsViewModel.pauseQueue() }
        _message.value = "Cola pausada."
    }

    fun onResumeQueue() {
        viewModelScope.launch { downloadsViewModel.resumeQueue() }
        _message.value = "Cola reanudada."
    }

    fun onCancelDownload(id: String) {
        viewModelScope.launch {
            downloadsViewModel.cancelDownload(id)
            _message.value = "Descarga cancelada."
        }
    }

    fun onRetryFailed() {
        viewModelScope.launch {
            DownloadQueue.retryFailed(getApplication())
            onNavigateToDownloads()
            _message.value = "Reintentando descargas."
        }
    }

    fun onClearErrors() {
        viewModelScope.launch { dao.clearFailed() }
    }

    fun onOpenFile(record: DownloadRecord) {
        val saved = record.savedUri
        if (saved.isNullOrBlank()) {
            _message.value = "No se encontró la ubicación del archivo."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(saved)
                val (mime, name) = withContext(Dispatchers.IO) {
                    if (uri.scheme == "content") {
                        val type = kotlinx.coroutines.runCatching { getApplication().contentResolver.getType(uri) }.getOrNull()
                        val displayName = kotlinx.coroutines.runCatching {
                            getApplication().contentResolver
                                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        }.getOrNull()
                        type to (displayName ?: uri.lastPathSegment)
                    } else {
                        null to uri.lastPathSegment
                    }
                }
                if (PlaybackKind.isAudio(record.formatId, mime, name)) {
                    _musicRequestUri.value = saved
                    _musicRequestTitle.value = record.title
                    // Navigation handled by UI
                } else {
                    getApplication().startActivity(
                        android.content.Intent(getApplication(), com.tikitoki.downloader.PlayerActivity::class.java).apply {
                            data = uri
                            putExtra("title", record.title)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }.also { it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kotlinx.coroutines.runCatching { com.tikitoki.downloader.data.DiagnosticHistory.record(getApplication(), e.message) }
                _message.value = "No se pudo abrir el reproductor."
            }
        }
    }

    // =========================================================================
    // LIBRARY ACTIONS
    // =========================================================================

    fun onLibraryKindChange(kind: String) {
        _libraryKind.value = kind
    }

    fun onHistoryFilterChange(filter: String) {
        _historyFilter.value = filter
    }

    fun onDeleteVideo(record: DownloadRecord) {
        _deleteCandidate.value = record
    }

    fun confirmDelete() {
        val item = _deleteCandidate.value ?: return
        _deleteCandidate.value = null
        viewModelScope.launch {
            try {
                _message.value = when (com.tikitoki.downloader.data.DownloadQueue.deleteCompleted(getApplication(), item.videoId)) {
                    com.tikitoki.downloader.data.DeleteCompletedResult.DELETED -> "Video eliminado del dispositivo y del historial."
                    com.tikitoki.downloader.data.DeleteCompletedResult.FILE_MISSING -> "El archivo ya no existía. Se eliminó del historial."
                    com.tikitoki.downloader.data.DeleteCompletedResult.FAILED -> "No se pudo eliminar el archivo. El registro se conserva."
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = "No se pudo eliminar el archivo. Comprueba que siga disponible."
            }
        }
    }

    // =========================================================================
    // TOOLS / PROFILE MONITOR ACTIONS
    // =========================================================================

    fun onFollowProfile(currentUrl: String) {
        if (ProfileMonitor.add(settings, currentUrl.trim())) {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                getApplication().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Notification permission handled by UI
            }
            _monitorRevision.value++
            _message.value = "Perfil agregado al monitoreo."
        } else {
            _message.value = "Pega un enlace válido de perfil en Buscar."
        }
    }

    fun onUnfollowProfile(profile: String) {
        ProfileMonitor.remove(settings, profile)
        _monitorRevision.value++
    }

    fun onUpdateExtractor() {
        ExtractorUpdateWorker.updateNow(getApplication())
        _message.value = "Actualización solicitada. Se ejecutará sin descargas pendientes."
    }

    // =========================================================================
    // DIALOG ACTIONS
    // =========================================================================

    fun onShowClearTemp() { _showClearTemp.value = true }
    fun onDismissClearTemp() { _showClearTemp.value = false }

    fun onShowPlayerChoice() { _showPlayerChoice.value = true }
    fun onDismissPlayerChoice() { _showPlayerChoice.value = false }

    fun onShowAbout() { _showAbout.value = true }
    fun onDismissAbout() { _showAbout.value = false }

    fun onMusicRequestConsumed() { _musicRequestUri.value = null }

    fun onConfirmClearTemp() {
        _showClearTemp.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deleted = DownloadQueue.clearTemporary(getApplication())
                _message.value = "Archivos temporales eliminados: $deleted."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                com.tikitoki.downloader.data.DiagnosticHistory.record(getApplication(), e.message)
                _message.value = "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas."
            }
        }
    }

    fun onOpenMusicLibrary() {
        _libraryKind.value = "Videos"
        _showPlayerChoice.value = false
        // Navigation handled by UI
    }

    fun onOpenMusicPlayer() {
        _showPlayerChoice.value = false
        // Navigation handled by UI
    }

    // =========================================================================
    // SELECTION ACTIONS
    // =========================================================================

    fun toggleSelection(videoId: String) {
        _selectedVideos.value = _selectedVideos.value.toMutableMap().also { map ->
            if (map.containsKey(videoId)) map.remove(videoId) else map[videoId] = true
        }
    }

    fun clearSelection() {
        _selectedVideos.value = emptyMap()
    }

    fun selectAll(videos: List<com.tikitoki.downloader.data.VideoEntry>) {
        _selectedVideos.value = videos.associate { it.id to true }
    }

    // =========================================================================
    // INCOMING LINK HANDLING
    // =========================================================================

    fun handleIncomingLink(incomingLink: String, shareRevision: Int) {
        if (incomingLink.isNotBlank() && (shareRevision > 0 || !profileViewModel.isCurrentLink(incomingLink))) {
            _url.value = incomingLink
            profileViewModel.reset()
            clearSelection()
            _formats.value = listOf(_defaultFormat.value!!)
            _chosenFormat.value = _defaultFormat.value!!
            _message.value = ""
            // Navigation handled by UI
        }
    }

    fun handleUrlChange() {
        val link = IncomingLinkValidator.validate(_url.value)?.url ?: return
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            if (!profileViewModel.isCurrentLink(link)) profileViewModel.inspect(link, false)
        }
    }

    fun isCurrentLink(link: String): Boolean = profileViewModel.isCurrentLink(link)

    fun onShowClearTempDismiss() { _showClearTemp.value = false }
    fun onShowPlayerChoiceDismiss() { _showPlayerChoice.value = false }
    fun onShowAboutDismiss() { _showAbout.value = false }
    fun onClearTemporary() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deleted = DownloadQueue.clearTemporary(getApplication())
                _message.value = "Archivos temporales eliminados: $deleted."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                com.tikitoki.downloader.data.DiagnosticHistory.record(getApplication(), e.message)
                _message.value = "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas."
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun updateDefaultFormat(quality: String) {
        val selector = when (quality) {
            "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
            else -> com.tikitoki.downloader.data.TikTokVideoSource.DEFAULT_FORMAT
        }
        _defaultFormat.value = com.tikitoki.downloader.data.FormatChoice(selector, quality)
    }

    private fun checkPendingRecovery() {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = dao.pendingRecoveryCount()
            if (pending > 0) DownloadQueue.start(getApplication())
        }
    }

    // Expose navigation callbacks for UI
    val onNavigateToDownloads: () -> Unit = { /* implemented by UI */ }
    val onNavigateToSettings: () -> Unit = { /* implemented by UI */ }
    val onNavigateToHome: () -> Unit = { /* implemented by UI */ }
    val onNavigateToLibrary: () -> Unit = { /* implemented by UI */ }
    val onNavigateToMusic: () -> Unit = { /* implemented by UI */ }
    val onOpenPlayer: () -> Unit = { /* implemented by UI */ }
}