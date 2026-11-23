package com.tikitoki.downloader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tikitoki.downloader.data.FacebookCookieState
import com.tikitoki.downloader.data.FacebookSessionHealth
import com.tikitoki.downloader.data.FacebookSessionOutcome
import com.tikitoki.downloader.data.FacebookSessionSummary
import com.tikitoki.downloader.data.PhotoDownloadWorker
import com.tikitoki.downloader.data.PhotoSession
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Manage the already imported local session. This is deliberately not a fake
 * Facebook Login button: Meta's OAuth token does not authenticate gallery-dl.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "LongMethod")
internal fun FacebookAccountPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { WorkManager.getInstance(context) }
    val jobs by remember(manager) { manager.getWorkInfosForUniqueWorkFlow(PhotoDownloadWorker.UNIQUE) }
        .collectAsState(initial = emptyList())
    val busy = jobs.any { it.state in setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED) }
    var revision by remember { mutableIntStateOf(0) }
    var summary by remember {
        mutableStateOf(
            FacebookSessionSummary(FacebookCookieState.MISSING, FacebookSessionOutcome.NOT_CHECKED, 0L),
        )
    }
    var message by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(context, revision, jobs) {
        summary = withContext(Dispatchers.IO) { FacebookSessionHealth.snapshot(context) }
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    message =
                        try {
                            val count = withContext(Dispatchers.IO) { PhotoSession.import(context, uri) }
                            "Sesión guardada en este teléfono ($count cookies)."
                        } catch (_: Exception) {
                            "No se pudo importar el archivo. Selecciona cookies.txt en formato Netscape."
                        }
                    revision++
                }
            }
        }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Cuenta de Facebook en Baja.pe") },
            text = {
                Text(
                    "La sesión que ya importaste se utiliza automáticamente al descargar fotos. " +
                        "No es necesario volver a importarla mientras Facebook la acepte.\n\n" +
                        "Si caduca o Facebook la rechaza, usa Renovar sesión para seleccionar un " +
                        "nuevo cookies.txt de tu propia cuenta.\n\n" +
                        "Chrome y Baja.pe no comparten cookies. Facebook Login oficial entrega " +
                        "un token para Graph API, no las cookies que utiliza gallery-dl.\n\n" +
                        "No compartas tu archivo ni tu contraseña. Solo se guardan las cookies " +
                        "en almacenamiento privado del dispositivo.",
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Entendido") } },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Borrar sesiones importadas") },
            text = { Text("Se eliminará el archivo de cookies utilizado para Facebook y otras plataformas.") },
            confirmButton = {
                TextButton(onClick = {
                    PhotoSession.delete(context)
                    message = "Sesiones eliminadas de este teléfono."
                    revision++
                    showDelete = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cuenta de Facebook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val detail =
                when (summary.cookies) {
                    FacebookCookieState.MISSING -> "No hay una sesión importada."
                    FacebookCookieState.INCOMPLETE -> "Faltan cookies necesarias de Facebook (c_user o xs)."
                    FacebookCookieState.EXPIRED -> "Las cookies del archivo ya caducaron."
                    FacebookCookieState.PRESENT ->
                        when (summary.outcome) {
                            FacebookSessionOutcome.NOT_CHECKED ->
                                "Sesión guardada. Se comprobará cuando descargues fotos."
                            FacebookSessionOutcome.NO_AUTH_ERROR ->
                                "La última consulta terminó sin pedir iniciar sesión."
                            FacebookSessionOutcome.LOGIN_REQUIRED ->
                                "Facebook rechazó la última consulta. Debes renovar la sesión."
                        }
                }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (summary.lastAttemptAt > 0L) {
                val last = DateFormat.getDateTimeInstance().format(Date(summary.lastAttemptAt))
                Text("Última comprobación por descarga: $last", color = TechMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Baja.pe reutiliza la sesión local. El estado no garantiza acceso a todos los perfiles.",
                color = TechMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("text/plain", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Importar / renovar") }
                OutlinedButton(
                    onClick = { revision++ },
                    modifier = Modifier.weight(1f),
                ) { Text("Actualizar estado") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showHelp = true }, modifier = Modifier.weight(1f)) {
                    Text("Cómo funciona")
                }
                OutlinedButton(
                    onClick = { showDelete = true },
                    enabled = !busy && summary.cookies != FacebookCookieState.MISSING,
                    modifier = Modifier.weight(1f),
                ) { Text("Eliminar sesión") }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
