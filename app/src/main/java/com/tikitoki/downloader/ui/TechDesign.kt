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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.tikitoki.downloader.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate

val TechPurple = Color(0xFF9736FF)
val TechPink = Color(0xFFE34BEA)
val TechBackground = Color(0xFF090914)
val TechPanel = Color(0xFF191629)
val TechBorder = Color(0xFF49315E)
val TechMuted = Color(0xFFB4AFC9)
val TechGradient = Brush.horizontalGradient(listOf(Color(0xFF6B21ED), TechPurple, TechPink))

@Composable
fun TechHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(40.dp).background(TechGradient, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(R.drawable.tikitoki_logo_oficial), contentDescription = "Tikitoki Downloader", modifier = Modifier.size(38.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Tikitoki Downloader", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text("TikTok · YouTube · Facebook · Instagram", color = TechMuted, fontSize = 10.sp, maxLines = 1)
        }
        TextButton(onClick = onSettings) { Text("⚙", color = Color.White, style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
fun TechPlatformStrip(selected: String?, onSelect: (String) -> Unit) {
    val platforms = listOf("TikTok", "YouTube", "Facebook", "Instagram", "Auto")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        platforms.forEach { platform ->
            Surface(modifier = Modifier.weight(1f).clickable { onSelect(platform) }, shape = RoundedCornerShape(12.dp),
                color = if (selected == platform) TechPurple.copy(alpha = 0.25f) else TechPanel,
                border = BorderStroke(if (selected == platform) 2.dp else 1.dp, if (selected == platform) TechPink else TechBorder)) {
                Column(Modifier.padding(vertical = 12.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (platform == "Auto") Icon(Icons.Outlined.Public, contentDescription = "Web", tint = Color(0xFF46C8FF), modifier = Modifier.size(31.dp)) else PlatformLogo(platform, Modifier.size(31.dp))
                }
            }
        }
    }
}

/** Locally drawn recognizable brand marks; no tracking, remote images or icon SDK. */
@Composable
private fun PlatformLogo(platform: String, modifier: Modifier = Modifier) {
    when (platform) {
        "Facebook" -> Canvas(modifier) {
            drawCircle(Color(0xFF1877F2))
            val w = size.width
            val f = Path().apply {
                moveTo(w * .57f, w * .94f); lineTo(w * .57f, w * .58f)
                lineTo(w * .70f, w * .58f); lineTo(w * .72f, w * .44f)
                lineTo(w * .57f, w * .44f); lineTo(w * .57f, w * .36f)
                cubicTo(w * .57f, w * .29f, w * .60f, w * .27f, w * .66f, w * .27f)
                lineTo(w * .72f, w * .27f); lineTo(w * .72f, w * .15f)
                cubicTo(w * .67f, w * .14f, w * .63f, w * .14f, w * .59f, w * .14f)
                cubicTo(w * .47f, w * .14f, w * .41f, w * .21f, w * .41f, w * .34f)
                lineTo(w * .41f, w * .44f); lineTo(w * .31f, w * .44f)
                lineTo(w * .31f, w * .58f); lineTo(w * .41f, w * .58f)
                lineTo(w * .41f, w * .94f); close()
            }
            drawPath(f, Color.White)
        }
        "TikTok" -> Canvas(modifier) {
            val w = size.width
            val note = Path().apply {
                moveTo(w * .54f, w * .10f); lineTo(w * .66f, w * .10f)
                cubicTo(w * .68f, w * .23f, w * .75f, w * .30f, w * .88f, w * .31f)
                lineTo(w * .88f, w * .44f)
                cubicTo(w * .79f, w * .44f, w * .72f, w * .41f, w * .66f, w * .36f)
                lineTo(w * .66f, w * .67f)
                cubicTo(w * .66f, w * .85f, w * .55f, w * .93f, w * .40f, w * .93f)
                cubicTo(w * .25f, w * .93f, w * .16f, w * .83f, w * .16f, w * .70f)
                cubicTo(w * .16f, w * .55f, w * .28f, w * .46f, w * .43f, w * .48f)
                lineTo(w * .43f, w * .61f)
                cubicTo(w * .35f, w * .59f, w * .29f, w * .63f, w * .29f, w * .70f)
                cubicTo(w * .29f, w * .77f, w * .34f, w * .80f, w * .40f, w * .80f)
                cubicTo(w * .48f, w * .80f, w * .54f, w * .75f, w * .54f, w * .66f)
                close()
            }
            translate(left = -w * .035f, top = w * .02f) { drawPath(note, Color(0xFF25F4EE)) }
            translate(left = w * .035f, top = -w * .02f) { drawPath(note, Color(0xFFFE2C55)) }
            drawPath(note, Color.White)
        }
        else -> Canvas(modifier) {
            if (platform == "YouTube") {
                drawRoundRect(Color(0xFFFF334F), topLeft = Offset(size.width * .04f, size.height * .18f),
                    size = Size(size.width * .92f, size.height * .64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .2f))
                val triangle = Path().apply {
                    moveTo(size.width * .42f, size.height * .34f)
                    lineTo(size.width * .42f, size.height * .68f)
                    lineTo(size.width * .70f, size.height * .51f)
                    close()
                }
                drawPath(triangle, Color.White)
            } else {
                val magenta = Color(0xFFEA4CCF)
                drawRoundRect(magenta, topLeft = Offset(size.width * .10f, size.height * .10f),
                    size = Size(size.width * .80f, size.height * .80f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .23f),
                    style = Stroke(width = size.width * .075f))
                drawCircle(magenta, radius = size.width * .20f,
                    center = Offset(size.width * .50f, size.height * .50f),
                    style = Stroke(width = size.width * .075f))
                drawCircle(magenta, radius = size.width * .055f,
                    center = Offset(size.width * .74f, size.height * .27f))
            }
        }
    }
}

@Composable
fun TechShortcut(title: String, value: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(12.dp),
        color = TechPanel, border = BorderStroke(1.dp, TechBorder)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, color = TechPink, style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.labelSmall, color = TechMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TechPrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 42.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TechPurple, contentColor = Color.White),
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

