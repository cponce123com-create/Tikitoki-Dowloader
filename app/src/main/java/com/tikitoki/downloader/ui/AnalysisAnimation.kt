package com.tikitoki.downloader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class Vertex(val x: Float, val y: Float, val z: Float)

/** A lightweight perspective projection, rendered only while analysis is active. */
@Composable
fun AnalysisAnimation(found: Int, scanning: Boolean) {
    val transition = rememberInfiniteTransition(label = "analysis")
    val angle by transition.animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "rotation")
    Card(modifier = Modifier.fillMaxWidth().semantics {
        contentDescription = if (found > 0) "Buscando más videos. $found encontrados" else "Analizando enlace"
    }, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102B23)),
        border = BorderStroke(1.dp, Color(0xFF356453))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(120.dp).background(
                Brush.radialGradient(listOf(Color(0xFF15563C), Color(0xFF071F17))), RoundedCornerShape(14.dp))) {
                val center = Offset(size.width / 2, size.height / 2)
                val scale = size.height * .30f
                drawCircle(Color(0xFF47D99A).copy(alpha = .09f), radius = scale * 1.55f, center = center)
                drawCircle(Color(0xFF47D99A).copy(alpha = .3f), radius = scale * 1.45f, center = center, style = Stroke(1.dp.toPx()))
                val points = listOf(
                    Vertex(-1f,-1f,-1f), Vertex(1f,-1f,-1f), Vertex(1f,1f,-1f), Vertex(-1f,1f,-1f),
                    Vertex(-1f,-1f,1f), Vertex(1f,-1f,1f), Vertex(1f,1f,1f), Vertex(-1f,1f,1f))
                val cy = cos(angle); val sy = sin(angle)
                val cx = cos(.48f); val sx = sin(.48f)
                val rotated = points.map { p ->
                    val x = p.x * cy + p.z * sy
                    val z = -p.x * sy + p.z * cy
                    Vertex(x, p.y * cx - z * sx, p.y * sx + z * cx)
                }
                fun project(p: Vertex): Offset {
                    val perspective = 4.6f / (4.6f - p.z)
                    return Offset(center.x + p.x * scale * .62f * perspective, center.y + p.y * scale * .62f * perspective)
                }
                val faces = listOf(intArrayOf(0,1,2,3), intArrayOf(4,5,6,7), intArrayOf(0,1,5,4),
                    intArrayOf(2,3,7,6), intArrayOf(0,3,7,4), intArrayOf(1,2,6,5))
                val colors = listOf(Color(0xFF0C6042), Color(0xFF19A46B), Color(0xFF38D896), Color(0xFF0F7953), Color(0xFF137A55), Color(0xFF1DB579))
                faces.indices.sortedBy { face -> faces[face].map { rotated[it].z }.average() }.forEach { face ->
                    val path = Path().apply {
                        faces[face].forEachIndexed { index, vertex -> val p = project(rotated[vertex]); if (index == 0) moveTo(p.x,p.y) else lineTo(p.x,p.y) }
                        close()
                    }
                    drawPath(path, colors[face].copy(alpha = .92f))
                    drawPath(path, Color(0xFFA7FFDC).copy(alpha = .8f), style = Stroke(1.dp.toPx()))
                }
                repeat(3) { i ->
                    val a = angle + i * 2f * PI.toFloat() / 3f
                    val dot = Offset(center.x + cos(a) * scale * 2.05f, center.y + sin(a) * scale * .85f)
                    drawCircle(Color(0xFF80F4C4).copy(alpha = .75f), radius = 2.5.dp.toPx(), center = dot)
                }
            }
            Text(if (scanning && found > 0) "Buscando más videos" else "Analizando enlace", color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (found > 0) "$found videos encontrados hasta ahora" else "Buscando el contenido disponible…",
                color = TechMuted, fontSize = 12.sp)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                color = Color(0xFF43E6A1), trackColor = Color(0xFF21483A))
        }
    }
}
