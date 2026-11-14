package com.tikitoki.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikitoki.downloader.data.*
import com.tikitoki.downloader.domain.MediaLink
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val videos: List<VideoEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPage: Int = 1,
    val totalVideos: Int? = null,
    val totalIsDeclared: Boolean = false,
    val countingTotal: Boolean = false,
    val error: String? = null,
    val incompletePage: Boolean = false,
    val omittedPages: Boolean = false,
    val profileImage: String? = null,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private companion object { const val PAGE_SIZE = 100 }
    private val source = TikTokVideoSource(application)
    private val universal = UniversalMediaSource(application)
    private val newPipe = NewPipeSource()
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState
    private var activeLink: MediaLink? = null
    private var generation = 0
    private var inspectionJob: Job? = null
    private var paginationJob: Job? = null
    private var nextScanPage = 1
    // A reattempt may contain only items already saved from the partial response.
    private var retryingPartial = false

    private suspend fun fetchPage(media: MediaLink, page: Int): ScanPage {
        var last: Exception? = null
        repeat(4) { attempt ->
            try {
                return if (media.platform == MediaPlatform.TIKTOK) {
                    val parsed = TikTokLinkParser.parse(media.url) as LinkParseResult.Success
                    source.inspectPageWithStatus(parsed.link, page, PAGE_SIZE)
                } else universal.inspectWithStatus(media, (page - 1) * PAGE_SIZE + 1, PAGE_SIZE)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (EngineFallback.isAccessDenied(e)) throw e
                last = e
                if (attempt < 3) delay(1500L * (1 shl attempt))
            }
        }
        throw last ?: IllegalStateException("No se pudo recuperar la página")
    }

    fun inspect(url: String, forceRefresh: Boolean = false) {
        val media = MediaLinkParser.parse(url)
        if (media == null) {
            mutableState.value = ProfileUiState(error = "Usa un enlace HTTPS de TikTok, YouTube, Facebook o Instagram.")
            return
        }
        val parsed = TikTokLinkParser.parse(url)
        if (media.platform == MediaPlatform.TIKTOK && parsed !is LinkParseResult.Success) {
            mutableState.value = ProfileUiState(error = (parsed as LinkParseResult.Error).message)
            return
        }
        val token = ++generation
        inspectionJob?.cancel()
        paginationJob?.cancel()
        activeLink = media
        nextScanPage = 1
        retryingPartial = false
        mutableState.value = ProfileUiState(loading = true)
        inspectionJob = viewModelScope.launch {
            try {
                if (media.collection) {
                    val page = fetchPage(media, 1)
                    if (token != generation) return@launch
                    mutableState.value = ProfileUiState(profileImage = page.profileImage)
                    processPage(page, token)
                } else {
                    val first = EngineFallback(
                        primary = {
                            if (media.platform == MediaPlatform.TIKTOK && parsed is LinkParseResult.Success)
                                source.inspect(parsed.link, forceRefresh)
                            else universal.inspect(media, count = PAGE_SIZE)
                        },
                        alternatives = if (media.platform == MediaPlatform.YOUTUBE)
                            listOf("newpipe" to suspend { listOf(newPipe.inspectVideo(media.url)) })
                        else emptyList()
                    ).execute()
                    if (token == generation) mutableState.value = ProfileUiState(videos = first.distinctBy { it.id }, totalVideos = first.size)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (token == generation) {
                    mutableState.value = ProfileUiState(hasMore = media.collection, nextPage = 1,
                        error = "No se pudo analizar el enlace. Comprueba que sea público y vuelve a intentarlo.")
                }
            }
        }
    }

    private fun processPage(page: ScanPage, token: Int) {
        val current = mutableState.value
        val seen = current.videos.mapTo(mutableSetOf()) { it.id }
        val fresh = page.items.filter { seen.add(it.id) }
        val state = scanPageState(page, PAGE_SIZE,
            if (retryingPartial && page.items.isNotEmpty()) page.items.size else fresh.size)
        val combined = current.videos + fresh
        when (state) {
            ScanPageState.PARTIAL -> {
                retryingPartial = true
                mutableState.value = current.copy(videos = combined, loading = false, countingTotal = false,
                    hasMore = true, incompletePage = true, nextPage = nextScanPage, totalVideos = null,
                    error = "Algunos resultados no estuvieron disponibles. Reintenta esta parte o continúa con la siguiente.")
            }
            ScanPageState.END -> {
                retryingPartial = false
                mutableState.value = current.copy(videos = combined, loading = false, countingTotal = false,
                    hasMore = false, incompletePage = false,
                    totalVideos = if (current.omittedPages) null else combined.size,
                    error = if (current.omittedPages) "Exploración terminada con resultados omitidos." else null)
            }
            ScanPageState.CONTINUE -> {
                retryingPartial = false
                nextScanPage++
                mutableState.value = current.copy(videos = combined, loading = false, countingTotal = true,
                    hasMore = true, incompletePage = false, nextPage = nextScanPage, error = null)
                scanRemaining(token)
            }
        }
    }

    private fun scanRemaining(token: Int) {
        val media = activeLink ?: return
        paginationJob = viewModelScope.launch {
            try {
                while (token == generation) {
                    val page = fetchPage(media, nextScanPage)
                    if (token != generation) return@launch
                    val current = mutableState.value.let {
                        if (it.profileImage == null && page.profileImage != null) it.copy(profileImage = page.profileImage) else it
                    }
                    val known = current.videos.mapTo(mutableSetOf()) { it.id }
                    val fresh = page.items.filter { known.add(it.id) }
                    val decision = scanPageState(page, PAGE_SIZE,
                        if (retryingPartial && page.items.isNotEmpty()) page.items.size else fresh.size)
                    val combined = current.videos + fresh
                    if (decision == ScanPageState.PARTIAL) {
                        retryingPartial = true
                        mutableState.value = current.copy(videos = combined, countingTotal = false, hasMore = true,
                            incompletePage = true, nextPage = nextScanPage, totalVideos = null,
                            error = "Algunos resultados no estuvieron disponibles. Reintenta esta parte o continúa con la siguiente.")
                        return@launch
                    }
                    retryingPartial = false
                    if (decision == ScanPageState.END) {
                        mutableState.value = current.copy(videos = combined, countingTotal = false, hasMore = false,
                            incompletePage = false, totalVideos = if (current.omittedPages) null else combined.size,
                            error = if (current.omittedPages) "Exploración terminada con resultados omitidos." else null)
                        return@launch
                    }
                    nextScanPage++
                    mutableState.value = current.copy(videos = combined, nextPage = nextScanPage, hasMore = true, incompletePage = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (token == generation) mutableState.value = mutableState.value.copy(countingTotal = false,
                    hasMore = true, error = "Conexión interrumpida. Pulsa Reanudar escaneo para continuar.")
            }
        }
    }

    fun resumeScan() {
        val media = activeLink ?: return
        if (!media.collection || mutableState.value.loading || mutableState.value.countingTotal) return
        paginationJob?.cancel()
        mutableState.value = mutableState.value.copy(error = null, countingTotal = true)
        scanRemaining(generation)
    }

    fun skipIncompletePage() {
        if (!mutableState.value.incompletePage || mutableState.value.countingTotal) return
        nextScanPage++
        retryingPartial = false
        mutableState.value = mutableState.value.copy(incompletePage = false, omittedPages = true,
            hasMore = true, countingTotal = true, error = null, nextPage = nextScanPage)
        scanRemaining(generation)
    }

    fun reset() {
        generation++
        inspectionJob?.cancel()
        paginationJob?.cancel()
        activeLink = null
        nextScanPage = 1
        retryingPartial = false
        mutableState.value = ProfileUiState()
    }
}
