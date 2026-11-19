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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class NeuralNode(
    val x: Float,
    val y: Float,
    val phase: Float,
)

@Suppress("MagicNumber")
private val neuralNodes =
    listOf(
        NeuralNode(0f, 0f, 0f),
        NeuralNode(-0.82f, -0.38f, 0.4f),
        NeuralNode(-0.68f, 0.42f, 1.1f),
        NeuralNode(-0.42f, -0.68f, 2.1f),
        NeuralNode(-0.18f, 0.66f, 2.8f),
        NeuralNode(0.18f, -0.72f, 1.6f),
        NeuralNode(0.44f, 0.62f, 0.8f),
        NeuralNode(0.76f, -0.42f, 2.4f),
        NeuralNode(0.84f, 0.30f, 1.9f),
        NeuralNode(-0.40f, -0.18f, 3.1f),
        NeuralNode(-0.46f, 0.20f, 1.4f),
        NeuralNode(0.36f, -0.20f, 2.7f),
        NeuralNode(0.40f, 0.20f, 0.6f),
    )

@Suppress("MagicNumber")
private val neuralConnections =
    listOf(
        0 to 1,
        0 to 2,
        0 to 3,
        0 to 4,
        0 to 5,
        0 to 6,
        0 to 7,
        0 to 8,
        1 to 3,
        1 to 9,
        1 to 10,
        2 to 4,
        2 to 10,
        2 to 9,
        3 to 5,
        3 to 9,
        4 to 6,
        4 to 10,
        5 to 7,
        5 to 11,
        6 to 8,
        6 to 12,
        7 to 8,
        7 to 11,
        8 to 12,
        9 to 10,
        9 to 11,
        10 to 12,
        11 to 12,
    )

/** A lightweight neural-network scan visualization, rendered only while analysis is active. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun AnalysisAnimation(
    found: Int,
    scanning: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "analysis")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "neural-signal",
    )

    Card(
        modifier =
            Modifier.fillMaxWidth().semantics {
                contentDescription = if (found > 0) "Buscando más videos. $found encontrados" else "Analizando enlace"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102B23)),
        border = BorderStroke(1.dp, Color(0xFF356453)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(
                modifier =
                    Modifier.fillMaxWidth().height(120.dp).background(
                        Brush.radialGradient(
                            listOf(Color(0xFF15563C), Color(0xFF071F17)),
                            radius = 300f,
                        ),
                        RoundedCornerShape(14.dp),
                    ),
            ) {
                drawNeuralNetwork(phase)
            }
            Text(
                text = if (scanning && found > 0) "Buscando más videos" else "Analizando enlace",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (found > 0) "$found videos encontrados hasta ahora" else "Buscando el contenido disponible…",
                color = TechMuted,
                fontSize = 12.sp,
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                color = Color(0xFF43E6A1),
                trackColor = Color(0xFF21483A),
            )
        }
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawNeuralNetwork(phase: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radians = phase * 2f * PI.toFloat()
    val nodePositions =
        neuralNodes.map { node ->
            Offset(
                x = center.x + (node.x + 0.035f * sin(radians + node.phase)) * size.width * 0.36f,
                y = center.y + (node.y + 0.075f * cos(radians + node.phase)) * size.height * 0.43f,
            )
        }

    drawScanRings(center)
    drawNeuralConnections(nodePositions, phase)
    drawNeuralNodes(nodePositions)
    drawNeuralSatellites(center, radians)
}

@Suppress("MagicNumber")
private fun DrawScope.drawScanRings(center: Offset) {
    drawCircle(
        color = Color(0xFF47D99A).copy(alpha = 0.08f),
        radius = size.height * 0.48f,
        center = center,
    )
    drawCircle(
        color = Color(0xFF47D99A).copy(alpha = 0.2f),
        radius = size.height * 0.40f,
        center = center,
        style = Stroke(1.dp.toPx()),
    )
    drawCircle(
        color = Color(0xFF47D99A).copy(alpha = 0.12f),
        radius = size.height * 0.31f,
        center = center,
        style = Stroke(1.dp.toPx()),
    )
}

@Suppress("MagicNumber")
private fun DrawScope.drawNeuralConnections(
    nodePositions: List<Offset>,
    phase: Float,
) {
    val signalColor = Color(0xFF67F5BA)
    neuralConnections.forEachIndexed { index, (startIndex, endIndex) ->
        val start = nodePositions[startIndex]
        val end = nodePositions[endIndex]
        val pulse = (phase + index * 0.137f) % 1f
        val lineAlpha = 0.13f + 0.22f * (0.5f + 0.5f * sin((pulse * 2f * PI).toFloat()))
        val signalPosition =
            Offset(
                x = start.x + (end.x - start.x) * pulse,
                y = start.y + (end.y - start.y) * pulse,
            )

        drawLine(
            color = signalColor.copy(alpha = lineAlpha * 0.28f),
            start = start,
            end = end,
            strokeWidth = 3.dp.toPx(),
        )
        drawLine(
            color = signalColor.copy(alpha = lineAlpha),
            start = start,
            end = end,
            strokeWidth = 0.8.dp.toPx(),
        )
        drawCircle(
            color = signalColor.copy(alpha = 0.16f),
            radius = 4.dp.toPx(),
            center = signalPosition,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = 1.35.dp.toPx(),
            center = signalPosition,
        )
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawNeuralNodes(nodePositions: List<Offset>) {
    val signalColor = Color(0xFF67F5BA)
    nodePositions.forEachIndexed { index, position ->
        val isHub = index == 0
        val nodeRadius = if (isHub) 3.8.dp.toPx() else 2.5.dp.toPx()
        val glowRadius = nodeRadius * if (isHub) 3.4f else 2.7f

        drawCircle(
            color = signalColor.copy(alpha = if (isHub) 0.18f else 0.12f),
            radius = glowRadius,
            center = position,
        )
        drawCircle(
            color = if (isHub) Color.White else signalColor,
            radius = nodeRadius,
            center = position,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = nodeRadius * 1.7f,
            center = position,
            style = Stroke(0.7.dp.toPx()),
        )
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawNeuralSatellites(
    center: Offset,
    radians: Float,
) {
    repeat(3) { index ->
        val satelliteAngle = radians + index * (2f * PI.toFloat() / 3f)
        val satellite =
            Offset(
                x = center.x + cos(satelliteAngle) * size.width * 0.43f,
                y = center.y + sin(satelliteAngle) * size.height * 0.34f,
            )
        drawCircle(
            color = Color(0xFF67F5BA).copy(alpha = 0.72f),
            radius = 1.7.dp.toPx(),
            center = satellite,
        )
    }
}
