package com.tikitoki.downloader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.CatalogPlaylist
import com.tikitoki.downloader.data.CatalogResult
import com.tikitoki.downloader.data.CatalogSection
import com.tikitoki.downloader.data.CollectionsCatalog
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechMuted
import kotlinx.coroutines.CancellationException

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun CollectionsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val context = LocalContext.current
    val catalogState by produceState<Result<CatalogResult>?>(initialValue = null, key1 = context) {
        value =
            try {
                Result.success(CollectionsCatalog.load(context))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                Result.failure(e)
            }
    }
    val catalog = catalogState?.getOrNull()
    var section by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = section != null) {
        section = null
        query = ""
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(TechBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedButton(onClick = {
                if (section != null) {
                    section = null
                    query = ""
                } else {
                    onBack()
                }
            }) { Text(stringResource(R.string.collections_back)) }
        }
        catalogHeader(section, catalog)
        when {
            catalog == null -> catalogStatus(failed = catalogState?.isFailure == true)
            section == null -> catalogSections(catalog.sections, onSelect = { section = it })
            else ->
                catalogPlaylists(
                    playlists =
                        catalog.sections
                            .firstOrNull { it.nameRes == section }
                            ?.playlists
                            .orEmpty(),
                    query = query,
                    onQueryChange = { query = it },
                    onOpenPlaylist = onOpenPlaylist,
                )
        }
    }
}

private fun LazyListScope.catalogHeader(
    section: Int?,
    catalog: CatalogResult?,
) {
    item {
        Text(
            section?.let {
                stringResource(it)
            } ?: stringResource(R.string.collections_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.collections_subtitle), color = TechMuted)
        if (catalog?.cached == true) Text(stringResource(R.string.collections_cached), color = TechMuted)
        if (catalog?.capped == true) Text(stringResource(R.string.collections_limit), color = TechMuted)
    }
}

private fun LazyListScope.catalogStatus(failed: Boolean) {
    item {
        if (failed) {
            Text(stringResource(R.string.collections_unavailable), color = TechMuted)
        } else {
            CircularProgressIndicator()
            Text(stringResource(R.string.collections_loading))
        }
    }
}

private fun LazyListScope.catalogSections(
    sections: List<CatalogSection>,
    onSelect: (Int) -> Unit,
) {
    items(sections, key = { it.nameRes }) { entry ->
        Card(onClick = { onSelect(entry.nameRes) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(entry.nameRes), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.collections_count, entry.playlists.size), color = TechMuted)
            }
        }
    }
    item { Text(stringResource(R.string.collections_classification), color = TechMuted) }
}

private fun LazyListScope.catalogPlaylists(
    playlists: List<CatalogPlaylist>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val entries = playlists.filter { it.title.contains(query.trim(), ignoreCase = true) }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.collections_search)) },
            singleLine = true,
        )
    }
    if (entries.isEmpty()) {
        val emptyMessage = if (playlists.isEmpty()) R.string.collections_empty else R.string.collections_no_results
        item { Text(stringResource(emptyMessage), color = TechMuted) }
    }
    items(entries, key = { it.url }) { entry ->
        Card(onClick = { onOpenPlaylist(entry.url) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.collections_open), color = TechMuted)
            }
        }
    }
}
