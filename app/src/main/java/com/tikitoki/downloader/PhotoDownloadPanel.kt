package com.tikitoki.downloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tikitoki.downloader.data.PhotoDownloadWorker
import com.tikitoki.downloader.data.PhotoSession
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun PhotoDownloadPanel(url: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { WorkManager.getInstance(context) }
    val work by remember(manager) { manager.getWorkInfosForUniqueWorkFlow(PhotoDownloadWorker.UNIQUE) }
        .collectAsState(initial = emptyList())
    val current = work.firstOrNull()
    val active = current?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    var message by remember { mutableStateOf("") }
    var sessionPresent by remember { mutableStateOf(PhotoSession.isPresent(context)) }
    val cookiePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    message =
                        try {
                            val count = withContext(Dispatchers.IO) { PhotoSession.import(context, uri) }
                            sessionPresent = true
                            "Sesión importada ($count cookies). Se guarda solo en este dispositivo."
                        } catch (_: Exception) {
                            "No se pudo importar la sesión. Selecciona un archivo cookies.txt válido."
                        }
                }
            }
        }

    fun enqueue() {
        val link = IncomingLinkValidator.validate(url)
        if (link == null || link.platform !in setOf(MediaPlatform.FACEBOOK, MediaPlatform.INSTAGRAM, MediaPlatform.TIKTOK)) {
            message = "Pega un enlace de Facebook, Instagram o TikTok."
            return
        }
        val request =
            OneTimeWorkRequestBuilder<PhotoDownloadWorker>()
                .setInputData(workDataOf(PhotoDownloadWorker.URL_KEY to link.url))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        manager.enqueueUniqueWork(PhotoDownloadWorker.UNIQUE, ExistingWorkPolicy.KEEP, request)
        message = "Descarga de fotos añadida."
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enqueue() else message = "Se necesita permiso de almacenamiento para guardar fotos."
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT < 29 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    enqueue()
                }
            }, enabled = !active, modifier = Modifier.weight(1f)) { Text("Descargar fotos") }
            if (active) OutlinedButton(onClick = { manager.cancelUniqueWork(PhotoDownloadWorker.UNIQUE) }) { Text("Cancelar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { cookiePicker.launch(arrayOf("text/plain", "*/*")) }) { Text("Importar sesión") }
            if (sessionPresent) {
                OutlinedButton(onClick = {
                    PhotoSession.delete(context)
                    sessionPresent = false
                }) { Text("Borrar sesión") }
            }
        }
        if (active) Text("Fotos guardadas: ${current?.progress?.getInt(PhotoDownloadWorker.COUNT_KEY, 0) ?: 0}")
        val result = current?.outputData?.getString(PhotoDownloadWorker.RESULT_KEY)
        if (!result.isNullOrBlank()) {
            Text(result)
        } else if (message.isNotBlank()) {
            Text(message)
        }
        Text("Las fotos se guardan en Imágenes/Baja.pe. Facebook puede requerir una sesión.")
    }
}
