package com.tikitoki.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLink
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.domain.TikTokLinkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val videos: List<VideoEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPage: Int = 1,
    val error: String? = null,
)

/** Survives configuration changes; keeps extractor work out of Compose. */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val source = TikTokVideoSource(application)
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState
    private var activeLink: TikTokLink? = null
    private var generation = 0

    fun inspect(url: String, forceRefresh: Boolean = false) {
        val parsed = TikTokLinkParser.parse(url)
        if (parsed !is LinkParseResult.Success) {
            mutableState.value = ProfileUiState(error = (parsed as LinkParseResult.Error).message)
            return
        }
        val link = parsed.link
        val token = ++generation
        activeLink = link
        mutableState.value = ProfileUiState(loading = true)
        viewModelScope.launch {
            try {
                val isProfile = link.type == TikTokLinkType.PROFILE
                val first = if (isProfile) source.inspectPage(link, 1) else source.inspect(link, forceRefresh)
                if (token == generation) {
                    mutableState.value = ProfileUiState(
                        videos = first.distinctBy { it.id },
                        hasMore = isProfile && first.size >= 60,
                        nextPage = 2
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) mutableState.value = ProfileUiState(error = e.message ?: "Error de extracción")
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
                val page = source.inspectPage(link, current.nextPage)
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
        activeLink = null
        mutableState.value = ProfileUiState()
    }
}
