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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.CatalogResult
import com.tikitoki.downloader.data.CollectionsCatalog
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechMuted
import kotlinx.coroutines.CancellationException

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun CollectionsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val catalog by produceState<CatalogResult?>(initialValue = null, key1 = context) {
        try {
            value = CollectionsCatalog.load(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }
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
        val result = catalog
        if (result == null) {
            item {
                if (failed) {
                    Text(stringResource(R.string.collections_unavailable), color = TechMuted)
                } else {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.collections_loading))
                }
            }
        } else if (section == null) {
            items(result.sections) { entry ->
                Card(onClick = { section = entry.nameRes }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(entry.nameRes), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.collections_count, entry.playlists.size), color = TechMuted)
                    }
                }
            }
            item { Text(stringResource(R.string.collections_classification), color = TechMuted) }
        } else {
            val all =
                result.sections
                    .firstOrNull { it.nameRes == section }
                    ?.playlists
                    .orEmpty()
            val entries = all.filter { it.title.contains(query.trim(), ignoreCase = true) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.collections_search)) },
                    singleLine = true,
                )
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(if (all.isEmpty()) R.string.collections_empty else R.string.collections_no_results),
                        color = TechMuted,
                    )
                }
            }
            items(entries) { entry ->
                Card(onClick = { onOpenPlaylist(entry.url) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.collections_open), color = TechMuted)
                    }
                }
            }
        }
    }
}
