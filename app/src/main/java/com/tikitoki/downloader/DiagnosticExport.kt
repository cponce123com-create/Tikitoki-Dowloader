package com.tikitoki.downloader

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.ErrorCategory
import com.tikitoki.downloader.data.ExtractorVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the diagnostic document picker and shares the generated report. */
@Composable
internal fun rememberDiagnosticPicker(onError: () -> Unit): ManagedActivityResultLauncher<String, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { destination ->
            if (destination != null) {
                scope.launch {
                    try {
                        val text =
                            withContext(Dispatchers.IO) {
                                val status = context.getSharedPreferences("extractor_status", android.content.Context.MODE_PRIVATE)
                                val unknown = context.getString(R.string.diagnostic_unknown)
                                val version =
                                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                                        ?: unknown
                                val engineVersion = ExtractorVersion.read(context) ?: unknown
                                val last = status.getLong("last_success", 0L)
                                val updated =
                                    if (last > 0) {
                                        java.text.DateFormat
                                            .getDateTimeInstance()
                                            .format(java.util.Date(last))
                                    } else {
                                        unknown
                                    }
                                val errors = DiagnosticHistory.read(context)
                                buildString {
                                    appendLine(context.getString(R.string.diagnostic_heading))
                                    appendLine(context.getString(R.string.diagnostic_app, version))
                                    appendLine(context.getString(R.string.diagnostic_engine, engineVersion))
                                    appendLine(context.getString(R.string.diagnostic_last_update, updated))
                                    appendLine(context.getString(R.string.diagnostic_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                                    appendLine(context.getString(R.string.diagnostic_abi, Build.SUPPORTED_ABIS.joinToString(", ")))
                                    appendLine(context.getString(R.string.diagnostic_errors))
                                    if (errors.isEmpty()) appendLine(context.getString(R.string.diagnostic_none))
                                    errors.forEach { error ->
                                        val date =
                                            java.text.DateFormat
                                                .getDateTimeInstance()
                                                .format(java.util.Date(error.time))
                                        val category =
                                            when (error.category) {
                                                ErrorCategory.RATE_LIMIT -> R.string.diagnostic_rate_limit
                                                ErrorCategory.TIMEOUT -> R.string.diagnostic_timeout
                                                ErrorCategory.NETWORK -> R.string.diagnostic_network
                                                ErrorCategory.PERMISSION -> R.string.diagnostic_permission
                                                ErrorCategory.OTHER -> R.string.diagnostic_other
                                            }
                                        appendLine(context.getString(R.string.diagnostic_error_entry, date, context.getString(category)))
                                    }
                                }
                            }
                        withContext(Dispatchers.IO) {
                            val stream = context.contentResolver.openOutputStream(destination) ?: error("output unavailable")
                            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        }
                        val send =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, destination)
                                clipData = android.content.ClipData.newRawUri("diagnóstico", destination)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.diagnostic_share)))
                    } catch (
                        e: kotlinx.coroutines.CancellationException,
                    ) {
                        throw e
                    } catch (
                        e: Exception,
                    ) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        DiagnosticHistory.record(context, e.message)
                        onError()
                    }
                }
            }
        }

}
