package com.tikitoki.downloader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tikitoki.downloader.data.DownloadSettings
import com.tikitoki.downloader.data.PhotoDownloadWorker
import com.tikitoki.downloader.data.FacebookCookieState
import com.tikitoki.downloader.data.PhotoSession
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("ktlint:standard:function-naming", "LongMethod", "CyclomaticComplexMethod")
internal fun PhotoDownloadPanel(
    url: String,
    onManageFacebook: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { WorkManager.getInstance(context) }
    val work by remember(manager) { manager.getWorkInfosForUniqueWorkFlow(PhotoDownloadWorker.UNIQUE) }
        .collectAsState(initial = emptyList())
    val lastWorkPrefs = remember(context) { context.getSharedPreferences("bajape_photo_job", Context.MODE_PRIVATE) }
    var lastWorkId by remember { mutableStateOf(lastWorkPrefs.getString("last_work_id", null)) }
    val current =
        work.firstOrNull { it.id.toString() == lastWorkId }
            ?: work.firstOrNull { it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING) }
            ?: work.lastOrNull()
    val active = current?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    var message by remember { mutableStateOf("") }
    LaunchedEffect(current?.id, current?.state) {
        if (current?.state in setOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)) {
            message = ""
        }
    }
    var sessionPresent by remember { mutableStateOf(false) }
    var facebookReady by remember { mutableStateOf(false) }
    suspend fun hasFacebookSession(): Boolean =
        withContext(Dispatchers.IO) {
            PhotoSession.facebookCookieState(context) == FacebookCookieState.PRESENT
        }

    LaunchedEffect(context) {
        facebookReady = hasFacebookSession()
        sessionPresent = PhotoSession.isPresent(context)
    }
    var showImport by remember { mutableStateOf(false) }
    var showDeleteSession by remember { mutableStateOf(false) }
    var showSessionHelp by remember { mutableStateOf(false) }
    val isFacebook = IncomingLinkValidator.validate(url)?.platform == MediaPlatform.FACEBOOK
    val cookiePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    message =
                        try {
                            val count = withContext(Dispatchers.IO) { PhotoSession.import(context, uri) }
                            sessionPresent = PhotoSession.isPresent(context)
                            facebookReady = hasFacebookSession()
                            if (isFacebook && !facebookReady) {
                                "Archivo importado ($count cookies), pero faltan c_user o xs de Facebook."
                            } else {
                                "Sesión importada ($count cookies). Se guarda solo en este dispositivo."
                            }
                        } catch (_: Exception) {
                            "No se pudo importar la sesión. Selecciona un archivo cookies.txt válido."
                        }
                }
            }
        }

    fun enqueue() {
        val link = IncomingLinkValidator.validate(url)
        val supported = setOf(MediaPlatform.FACEBOOK, MediaPlatform.INSTAGRAM, MediaPlatform.TIKTOK)
        if (link == null || link.platform !in supported) {
            message = "Pega un enlace de Facebook, Instagram o TikTok."
            return
        }
        val request =
            OneTimeWorkRequestBuilder<PhotoDownloadWorker>()
                .setInputData(workDataOf(PhotoDownloadWorker.URL_KEY to link.url))
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(
                            if (DownloadSettings.allowMetered(context)) {
                                NetworkType.CONNECTED
                            } else {
                                NetworkType.UNMETERED
                            },
                        ).build(),
                ).build()
        lastWorkId = request.id.toString()
        lastWorkPrefs.edit().putString("last_work_id", lastWorkId).apply()
        manager.enqueueUniqueWork(PhotoDownloadWorker.UNIQUE, ExistingWorkPolicy.KEEP, request)
        message = "Descarga de fotos añadida."
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enqueue() else message = "Se necesita permiso de almacenamiento para guardar fotos."
        }
    if (showSessionHelp) {
        AlertDialog(
            onDismissRequest = { showSessionHelp = false },
            title = { Text("Cómo importar tu sesión de Facebook") },
            text = {
                Text(
                    "1. Inicia sesión en Facebook desde un navegador de confianza.\n" +
                        "2. Exporta solamente las cookies de facebook.com en formato Netscape (cookies.txt) " +
                        "usando una herramienta que conozcas y en la que confíes.\n" +
                        "3. En Baja.pe, pulsa Importar sesión y selecciona ese archivo.\n\n" +
                        "El archivo equivale a una sesión iniciada. No lo compartas ni lo subas a internet. " +
                        "El inicio de sesión en Chrome no se transmite automáticamente a Baja.pe.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showSessionHelp = false }) {
                    Text("Entendido")
                }
            },
        )
    }
    if (showImport) {
        PhotoSessionImportDialog(
            onDismiss = { showImport = false },
            onContinue = {
                showImport = false
                cookiePicker.launch(arrayOf("text/plain", "*/*"))
            },
        )
    }
    if (showDeleteSession) {
        AlertDialog(
            onDismissRequest = { showDeleteSession = false },
            title = { Text("Borrar sesiones importadas") },
            text = { Text("Se borrarán las sesiones de Facebook, Instagram y TikTok guardadas en Baja.pe.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSession = false
                    scope.launch {
                        message =
                            try {
                                withContext(Dispatchers.IO) { PhotoSession.delete(context) }
                                "Sesiones eliminadas de Baja.pe."
                            } catch (_: Exception) {
                                "No se pudo eliminar la sesión. Vuelve a intentarlo."
                            }
                        sessionPresent = PhotoSession.isPresent(context)
                        facebookReady = hasFacebookSession()
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteSession = false }) { Text("Cancelar") } },
        )
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
            if (active) {
                OutlinedButton(onClick = { manager.cancelUniqueWork(PhotoDownloadWorker.UNIQUE) }) { Text("Cancelar") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !active,
                modifier = Modifier.weight(1f),
                onClick = { showImport = true },
            ) {
                Text("Importar sesión")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { showSessionHelp = true },
            ) {
                Text("Cómo importar")
            }
        }
        if (sessionPresent) {
            OutlinedButton(enabled = !active, onClick = { showDeleteSession = true }) { Text("Borrar sesión") }
        }
        if (isFacebook) {
            OutlinedButton(onClick = onManageFacebook) { Text("Gestionar cuenta de Facebook") }
        }
        if (isFacebook && sessionPresent) {
            Text(
                if (facebookReady) {
                    "Cookies de Facebook detectadas; su validez se comprueba al descargar."
                } else {
                    "Archivo incompleto: faltan las cookies de Facebook c_user o xs."
                },
            )
        }
        if (active) Text("Fotos guardadas: ${current?.progress?.getInt(PhotoDownloadWorker.COUNT_KEY, 0) ?: 0}")
        val result = current?.outputData?.getString(PhotoDownloadWorker.RESULT_KEY)
        if (message.isNotBlank()) {
            Text(message)
        } else if (!result.isNullOrBlank()) {
            Text(result)
        }
        Text("Las fotos se guardan en Imágenes/Baja.pe. Algunas galerías requieren un archivo cookies.txt.")
        Text("Con sesión pueden aparecer fotos no públicas; la app no puede distinguir su privacidad.")
        Text("El archivo cookies.txt contiene tu sesión. No lo compartas ni envíes tus contraseñas.")
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun PhotoSessionImportDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar sesión para fotos") },
        text = { Text(PhotoSession.IMPORT_NOTICE) },
        confirmButton = { TextButton(onClick = onContinue) { Text("Elegir archivo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
