package com.tikitoki.downloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val TechPurple = Color(0xFF9736FF)
val TechPink = Color(0xFFE34BEA)
val TechBackground = Color(0xFF090914)
val TechPanel = Color(0xFF191629)
val TechBorder = Color(0xFF49315E)
val TechMuted = Color(0xFFB4AFC9)
val TechGradient = Brush.horizontalGradient(listOf(Color(0xFF6B21ED), TechPurple, TechPink))

@Composable
fun TechHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).background(TechGradient, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text("↓", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text("Tikitoki Downloader", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text("Videos y música · Universal", style = MaterialTheme.typography.bodySmall, color = TechMuted)
        }
        TextButton(onClick = onSettings) { Text("⚙", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
fun TechPlatformStrip() {
    val platforms = listOf("♪" to "TikTok", "▶" to "YouTube", "f" to "Facebook", "◎" to "Instagram")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        platforms.forEach { (symbol, label) ->
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                color = TechPanel, border = BorderStroke(1.dp, TechBorder)) {
                Column(Modifier.padding(vertical = 14.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(symbol, style = MaterialTheme.typography.headlineMedium, color = when(label) {
                        "YouTube" -> Color(0xFFFF4159)
                        "Facebook" -> Color(0xFF4C9BFF)
                        "Instagram" -> TechPink
                        else -> Color(0xFF42E7F5)
                    }, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun TechShortcut(title: String, value: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(17.dp),
        color = TechPanel, border = BorderStroke(1.dp, TechBorder)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, color = TechPink, style = MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.labelSmall, color = TechMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TechPrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TechPurple, contentColor = Color.White),
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 18.dp)) {
        Box(Modifier.fillMaxWidth().background(if (enabled) TechGradient else Brush.horizontalGradient(listOf(TechBorder, TechBorder)),
            RoundedCornerShape(12.dp)).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}
