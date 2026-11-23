package com.tikitoki.downloader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.domain.FacebookPhotoLinks
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechPlatformStrip
import com.tikitoki.downloader.ui.TechPrimaryButton

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun HomeLinkInput(
    selectedPlatform: String?,
    url: String,
    loading: Boolean,
    loadingMore: Boolean,
    onPlatformChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onKeyboardDone: () -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
) {
    val showAnalyze = !FacebookPhotoLinks.isPhotoCollection(url)
    TechPlatformStrip(selectedPlatform, onPlatformChange)
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        placeholder = { Text(stringResource(R.string.home_link_hint)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 1,
        maxLines = 3,
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onKeyboardDone() }),
        shape = RoundedCornerShape(12.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f).height(44.dp),
            onClick = onPaste,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TechBorder),
        ) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = TechPink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.home_paste))
        }
        if (showAnalyze) {
            TechPrimaryButton(
                modifier = Modifier.weight(1f).height(44.dp),
                enabled = !loading && !loadingMore && url.isNotBlank(),
                onClick = onAnalyze,
                icon = Icons.Outlined.Search,
                text = stringResource(if (loading) R.string.home_analyzing else R.string.home_analyze),
            )
        }
    }
}
