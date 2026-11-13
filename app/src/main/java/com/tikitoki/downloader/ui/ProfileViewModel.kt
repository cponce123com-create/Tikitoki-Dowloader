package com.tikitoki.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.EngineRegistry
import com.tikitoki.downloader.data.EngineFallback
import com.tikitoki.downloader.data.NewPipeSource
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLink
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.domain.TikTokLinkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
)

/** Survives configuration changes; keeps extractor work out of Compose. */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val source = TikTokVideoSource(application)
    private val universal = UniversalMediaSource(application)
    private val newPipe = NewPipeSource()
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState
    private var activeLink: com.tikitoki.downloader.domain.MediaLink? = null
    private var generation = 0
    private var inspectionJob: Job? = null
    private var paginationJob: Job? = null
    private var countJob: Job? = null

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
        val link = (parsed as? LinkParseResult.Success)?.link
        val token = ++generation
        inspectionJob?.cancel()
        paginationJob?.cancel()
        countJob?.cancel()
        activeLink = media
        mutableState.value = ProfileUiState(loading = true)
        inspectionJob = viewModelScope.launch {
            try {
                val isProfile = media.collection
                val first = EngineFallback(
                    primary = {
                        if (media.platform == MediaPlatform.TIKTOK && link != null) {
                            if (isProfile) source.inspectPage(link, 1) else source.inspect(link, forceRefresh)
                        } else universal.inspect(media)
                    },
                    alternatives = if (media.platform == MediaPlatform.YOUTUBE && !media.collection) {
                        listOf("newpipe" to suspend { listOf(newPipe.inspectVideo(media.url)) })
                    } else emptyList()
                ).execute()
                if (token == generation) {
                    mutableState.value = ProfileUiState(
                        videos = first.distinctBy { it.id },
                        hasMore = isProfile && first.size >= 60,
                        nextPage = 2,
                        totalVideos = if (!isProfile || first.size < 60) first.distinctBy { it.id }.size else null,
                        countingTotal = isProfile && first.size >= 60
                    )
                    if (isProfile) {
                        if (media.platform != MediaPlatform.TIKTOK) fetchDeclaredTotal(media, token)
                        if (first.size >= 60) countProfileInBackground(media, link, first, token)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) mutableState.value = ProfileUiState(error = (e.message ?: "Error de extracción") + "\n" + EngineRegistry.statusMessage())
            }
        }
    }

    private fun fetchDeclaredTotal(
        media: com.tikitoki.downloader.domain.MediaLink,
        token: Int,
    ) {
        countJob = viewModelScope.launch {
            val declared = try { universal.declaredCollectionCount(media) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            if (token == generation && declared != null) {
                mutableState.value = mutableState.value.copy(
                    totalVideos = declared,
                    totalIsDeclared = true,
                    countingTotal = false,
                )
            }
        }
    }

    /** Fetch all accessible pages progressively; each page becomes visible immediately. */
    private fun countProfileInBackground(
        media: com.tikitoki.downloader.domain.MediaLink,
        tikTokLink: TikTokLink?,
        first: List<VideoEntry>,
        token: Int,
    ) {
        paginationJob = viewModelScope.launch {
            val seen = first.mapTo(mutableSetOf()) { it.id }
            var pageNumber = 2
            try {
                while (token == generation) {
                    val page = if (media.platform == MediaPlatform.TIKTOK && tikTokLink != null) {
                        source.inspectPage(tikTokLink, pageNumber)
                    } else {
                        universal.inspect(media, (pageNumber - 1) * 60 + 1)
                    }
                    if (token != generation) return@launch
                    val fresh = page.filter { seen.add(it.id) }
                    val latest = mutableState.value
                    mutableState.value = latest.copy(
                        videos = latest.videos + fresh,
                        nextPage = pageNumber + 1,
                        hasMore = page.size >= 60 && fresh.isNotEmpty(),
                    )
                    if (page.size < 60 || fresh.isEmpty()) {
                        mutableState.value = mutableState.value.copy(
                            totalVideos = if (mutableState.value.totalIsDeclared) mutableState.value.totalVideos else seen.size,
                            countingTotal = false
                        )
                        return@launch
                    }
                    pageNumber++
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (token == generation) mutableState.value = mutableState.value.copy(countingTotal = false, hasMore = false, error = "No se pudieron recuperar todas las publicaciones.")
            }
        }
    }

    fun loadMore() {
        val link = activeLink ?: return
        val current = mutableState.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        val token = generation
        mutableState.value = current.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            try {
                val page = if (link.platform == MediaPlatform.TIKTOK) {
                    val parsed = TikTokLinkParser.parse(link.url) as LinkParseResult.Success
                    source.inspectPage(parsed.link, current.nextPage)
                } else universal.inspect(link, (current.nextPage - 1) * 60 + 1)
                if (token != generation) return@launch
                val latest = mutableState.value
                mutableState.value = latest.copy(
                    videos = (latest.videos + page).distinctBy { it.id },
                    loadingMore = false,
                    hasMore = page.size >= 60 && page.any { candidate -> latest.videos.none { it.id == candidate.id } },
                    nextPage = current.nextPage + 1
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) mutableState.value = mutableState.value.copy(loadingMore = false, error = e.message)
            }
        }
    }

    fun reset() {
        generation++
        inspectionJob?.cancel()
        paginationJob?.cancel()
        countJob?.cancel()
        activeLink = null
        mutableState.value = ProfileUiState()
    }
}
