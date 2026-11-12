package com.tikitoki.downloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.domain.TikTokLinkType

class MainActivity : ComponentActivity() {
    private var sharedLink by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLink = receivedText(intent)
        setContent { TikitokiScreen(sharedLink) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedLink = receivedText(intent)
    }

    private fun receivedText(intent: Intent?): String {
        return if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else {
            ""
        }
    }
}

private val TikitokiColors = darkColorScheme(
    primary = Color(0xFFFF426F),
    onPrimary = Color.White,
    secondary = Color(0xFF59E4DE),
    background = Color(0xFF101014),
    surface = Color(0xFF1B1B22),
    onSurface = Color(0xFFF4F4F7),
)

@Composable
private fun TikitokiScreen(incomingLink: String) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf(incomingLink) }
    var parsed by remember { mutableStateOf<LinkParseResult?>(null) }

    LaunchedEffect(incomingLink) {
        if (incomingLink.isNotBlank() && incomingLink != input) {
            input = incomingLink
            parsed = null
            tab = 0
        }
    }

    MaterialTheme(colorScheme = TikitokiColors) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Inicio", "Biblioteca", "Acerca de").forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌂", "▤", "ⓘ")[index]) },
                            label = { Text(name) },
                        )
                    }
                }
            },
        ) { insets ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Tikitoki Downloader", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Android · Kotlin · Versión 0.1.0",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium)

                when (tab) {
                    0 -> {
                        Text("Analiza un enlace", style = MaterialTheme.typography.titleLarge)
                        Text("Pega un perfil o video público de TikTok. También puedes compartir un enlace desde otra aplicación.")
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; parsed = null },
                            label = { Text("Enlace de TikTok") },
                            placeholder = { Text("https://www.tiktok.com/@usuario") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = input.isNotBlank(),
                            onClick = { parsed = TikTokLinkParser.parse(input) },
                        ) { Text("Validar enlace") }

                        when (val result = parsed) {
                            is LinkParseResult.Success -> {
                                val link = result.link
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text("Enlace reconocido", fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary)
                                        Text(
                                            when (link.type) {
                                                TikTokLinkType.PROFILE -> "Perfil: @${link.username}"
                                                TikTokLinkType.VIDEO -> "Video: ${link.videoId}"
                                                TikTokLinkType.SHORT_LINK -> "Enlace corto de TikTok"
                                            }
                                        )
                                        Text(
                                            when (link.type) {
                                                TikTokLinkType.PROFILE ->
                                                    "El perfil se reconoció por su URL. La consulta de publicaciones todavía no está integrada."
                                                TikTokLinkType.VIDEO ->
                                                    "El enlace es válido. La consulta de formatos y descarga todavía no están integradas."
                                                TikTokLinkType.SHORT_LINK ->
                                                    "El enlace necesita resolver su redirección. Esa función aún está pendiente."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                            is LinkParseResult.Error -> {
                                Text(result.message, color = MaterialTheme.colorScheme.error)
                            }
                            null -> {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Primera etapa", fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(8.dp))
                                        Text("La app ya reconoce enlaces. El catálogo de videos, las calidades disponibles y las descargas se incorporarán tras conectar y probar un proveedor real.")
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        Text("Biblioteca", style = MaterialTheme.typography.titleLarge)
                        Text("Aún no hay videos descargados. Esta pantalla se conectará al gestor de descargas y al historial local.")
                    }
                    else -> {
                        Text("Acerca del proyecto", style = MaterialTheme.typography.titleLarge)
                        Text("Tikitoki Downloader es una aplicación Android independiente.")
                        Text("Estado: prototipo de validación de enlaces. No tiene acceso automático a publicaciones, ni elimina marcas de agua, ni descarga perfiles completos en esta versión.")
                        Text("Utiliza únicamente material propio o para el que tengas permiso de descarga.")
                    }
                }
            }
        }
    }
}
