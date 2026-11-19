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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.sqrt

private const val NODES_PER_HEMISPHERE = 28
private const val GOLDEN_ANGLE = 2.3999631f
private const val ROTATION_DURATION_MS = 7800

private data class BrainNode(
    val x: Float,
    val y: Float,
    val z: Float,
    val phase: Float,
    val hemisphere: Int,
    val isCore: Boolean = false,
)

private data class BrainConnection(
    val start: Int,
    val end: Int,
)

private data class BrainPose(
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
)

private data class ProjectedPoint(
    val position: Offset,
    val depth: Float,
)

@Suppress("MagicNumber")
private val brainNodes =
    buildList {
        repeat(2) { hemisphere ->
            val side = if (hemisphere == 0) -1f else 1f
            repeat(NODES_PER_HEMISPHERE) { index ->
                val vertical = 1f - 2f * ((index + 0.5f) / NODES_PER_HEMISPHERE)
                val ring = sqrt(1f - vertical * vertical)
                val angle = index * GOLDEN_ANGLE + hemisphere * 0.37f
                val folds =
                    1f +
                        0.07f * sin(angle * 4f + vertical * 5f) +
                        0.03f * cos(angle * 7f - vertical * 4f)
                add(
                    BrainNode(
                        x = side * 0.19f + side * ring * cos(angle) * 0.34f * folds,
                        y = vertical * 0.58f * folds,
                        z = ring * sin(angle) * 0.43f * folds,
                        phase = index * 0.37f + hemisphere * 0.42f,
                        hemisphere = hemisphere,
                    ),
                )
            }
        }
        add(BrainNode(0f, 0f, 0f, 0f, hemisphere = 2, isCore = true))
    }

@Suppress("MagicNumber")
private fun buildBrainConnections(nodes: List<BrainNode>): List<BrainConnection> {
    val edgeIndices = linkedSetOf<Pair<Int, Int>>()
    nodes.indices
        .filter { nodes[it].hemisphere < 2 }
        .forEach { sourceIndex ->
            val source = nodes[sourceIndex]
            nodes.indices
                .filter { it != sourceIndex && nodes[it].hemisphere == source.hemisphere }
                .sortedBy { candidateIndex ->
                    val candidate = nodes[candidateIndex]
                    val dx = source.x - candidate.x
                    val dy = source.y - candidate.y
                    val dz = source.z - candidate.z
                    dx * dx + dy * dy + dz * dz
                }.take(3)
                .forEach { candidateIndex ->
                    edgeIndices.add(minOf(sourceIndex, candidateIndex) to maxOf(sourceIndex, candidateIndex))
                }
        }

    for (index in 0 until NODES_PER_HEMISPHERE step 4) {
        val rightIndex = NODES_PER_HEMISPHERE + index
        edgeIndices.add(index to rightIndex)
    }

    val coreIndex = nodes.lastIndex
    nodes.indices
        .filter { it != coreIndex }
        .sortedBy { candidateIndex ->
            val node = nodes[candidateIndex]
            node.x * node.x + node.y * node.y + node.z * node.z
        }.take(10)
        .forEach { edgeIndices.add(minOf(it, coreIndex) to maxOf(it, coreIndex)) }

    return edgeIndices.map { (start, end) -> BrainConnection(start, end) }
}

private val brainConnections = buildBrainConnections(brainNodes)

/** A volumetric artificial-brain scan visualization, rendered only while analysis is active. */
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
                animation = tween(ROTATION_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "neural-brain-rotation",
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
                    Modifier.fillMaxWidth().height(150.dp).background(
                        Brush.radialGradient(
                            listOf(Color(0xFF15563C), Color(0xFF071F17)),
                            radius = 340f,
                        ),
                        RoundedCornerShape(14.dp),
                    ),
            ) {
                drawArtificialBrain(phase)
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
private fun DrawScope.drawArtificialBrain(phase: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val rotation = phase * 2f * PI.toFloat()
    val pose =
        BrainPose(
            yaw = rotation,
            pitch = 0.24f + 0.08f * sin(rotation * 0.55f),
            roll = 0.035f * sin(rotation * 0.5f),
        )
    val projected = brainNodes.map { node -> projectPoint(node.x, node.y, node.z, center, pose) }

    drawBrainAura(center)
    drawBrainHemispheres(center, pose)
    drawBrainOrbits(center, pose, rotation)
    drawBrainConnections(projected, phase)
    drawBrainNodes(projected)
    drawOrbitingSignals(center, pose, rotation)
}

@Suppress("MagicNumber")
private fun DrawScope.projectPoint(
    x: Float,
    y: Float,
    z: Float,
    center: Offset,
    pose: BrainPose,
): ProjectedPoint {
    val yawX = x * cos(pose.yaw) - z * sin(pose.yaw)
    val yawZ = x * sin(pose.yaw) + z * cos(pose.yaw)
    val pitchY = y * cos(pose.pitch) - yawZ * sin(pose.pitch)
    val pitchZ = y * sin(pose.pitch) + yawZ * cos(pose.pitch)
    val screenX = yawX * cos(pose.roll) - pitchY * sin(pose.roll)
    val screenY = yawX * sin(pose.roll) + pitchY * cos(pose.roll)
    val perspective = 3.2f / (3.2f - pitchZ)

    return ProjectedPoint(
        position =
            Offset(
                x = center.x + screenX * size.width * 0.68f * perspective,
                y = center.y + screenY * size.height * 0.72f * perspective,
            ),
        depth = pitchZ,
    )
}

@Suppress("MagicNumber")
private fun DrawScope.drawBrainAura(center: Offset) {
    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color(0xFF51F0B4).copy(alpha = 0.19f),
                        Color(0xFF42CFF5).copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                center = center,
                radius = size.height * 0.68f,
            ),
        radius = size.height * 0.68f,
        center = center,
    )
}

@Suppress("MagicNumber")
private fun DrawScope.drawBrainHemispheres(
    center: Offset,
    pose: BrainPose,
) {
    val hemisphereGlow = Color(0xFF4AE9B1).copy(alpha = 0.16f)
    listOf(-0.22f, 0.22f).forEach { x ->
        val projectedCenter = projectPoint(x, 0f, 0f, center, pose).position
        drawOval(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            hemisphereGlow,
                            Color(0xFF138B70).copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                    center = projectedCenter,
                    radius = size.height * 0.44f,
                ),
            topLeft = Offset(projectedCenter.x - size.height * 0.33f, projectedCenter.y - size.height * 0.43f),
            size = Size(size.height * 0.66f, size.height * 0.86f),
        )
    }
    drawLine(
        color = Color(0xFF9DFFE1).copy(alpha = 0.24f),
        start = projectPoint(0f, -0.54f, 0f, center, pose).position,
        end = projectPoint(0f, 0.54f, 0f, center, pose).position,
        strokeWidth = 0.8.dp.toPx(),
    )
}

@Suppress("MagicNumber")
private fun DrawScope.drawBrainOrbits(
    center: Offset,
    pose: BrainPose,
    rotation: Float,
) {
    repeat(2) { orbitIndex ->
        val path = Path()
        val steps = 64
        for (step in 0..steps) {
            val angle = step.toFloat() / steps * 2f * PI.toFloat()
            val orbitPoint =
                if (orbitIndex == 0) {
                    Triple(0.82f * cos(angle), 0.28f * sin(angle), 0.72f * sin(angle))
                } else {
                    Triple(0.58f * cos(angle), 0.70f * sin(angle), 0.16f * cos(angle + rotation))
                }
            val point = projectPoint(orbitPoint.first, orbitPoint.second, orbitPoint.third, center, pose).position
            if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        val orbitColor =
            if (orbitIndex == 0) {
                Color(0xFF76EFD0).copy(alpha = 0.22f)
            } else {
                Color(0xFF57CFF4).copy(alpha = 0.14f)
            }
        drawPath(
            path = path,
            color = orbitColor,
            style = Stroke(if (orbitIndex == 0) 0.9.dp.toPx() else 0.65.dp.toPx()),
        )
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawBrainConnections(
    projected: List<ProjectedPoint>,
    phase: Float,
) {
    val mint = Color(0xFF5CF0B7)
    val cyan = Color(0xFF83DFFF)
    brainConnections
        .sortedBy { (projected[it.start].depth + projected[it.end].depth) / 2f }
        .forEachIndexed { index, connection ->
            val start = projected[connection.start]
            val end = projected[connection.end]
            val depth = ((start.depth + end.depth) / 2f + 0.55f).coerceIn(0f, 1f)
            val pulse = (phase * 1.7f + index * 0.137f) % 1f
            val brightness = (0.10f + depth * 0.22f) + 0.16f * (0.5f + 0.5f * sin(pulse * 2f * PI.toFloat()))
            val signal =
                Offset(
                    x = start.position.x + (end.position.x - start.position.x) * pulse,
                    y = start.position.y + (end.position.y - start.position.y) * pulse,
                )
            val edgeColor = if (index % 7 == 0) cyan else mint

            drawLine(
                color = edgeColor.copy(alpha = brightness * 0.22f),
                start = start.position,
                end = end.position,
                strokeWidth = 2.2.dp.toPx(),
            )
            drawLine(
                color = edgeColor.copy(alpha = brightness),
                start = start.position,
                end = end.position,
                strokeWidth = (0.45f + depth * 0.65f).dp.toPx(),
            )
            drawCircle(
                color = cyan.copy(alpha = 0.22f),
                radius = (2.8f + depth * 1.8f).dp.toPx(),
                center = signal,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = 1.1.dp.toPx(),
                center = signal,
            )
        }
}

@Suppress("MagicNumber")
private fun DrawScope.drawBrainNodes(projected: List<ProjectedPoint>) {
    projected.indices
        .sortedBy { projected[it].depth }
        .forEach { index ->
            val node = projected[index]
            val depthScale = (0.72f + (node.depth + 0.55f).coerceIn(0f, 1f) * 0.58f)
            val isCore = brainNodes[index].isCore
            val radius = (if (isCore) 4.2f else 1.7f + depthScale * 1.25f).dp.toPx()
            val glow = if (isCore) Color(0xFFB8FFF0) else Color(0xFF62F3C1)

            drawCircle(
                color = glow.copy(alpha = if (isCore) 0.23f else 0.13f + depthScale * 0.08f),
                radius = radius * if (isCore) 3.8f else 2.6f,
                center = node.position,
            )
            drawCircle(
                color = if (isCore) Color.White else glow.copy(alpha = 0.76f + depthScale * 0.2f),
                radius = radius,
                center = node.position,
            )
            if (isCore) {
                drawCircle(
                    color = Color(0xFF76EFD0).copy(alpha = 0.55f),
                    radius = radius * 1.9f,
                    center = node.position,
                    style = Stroke(0.75.dp.toPx()),
                )
            }
        }
}

@Suppress("MagicNumber")
private fun DrawScope.drawOrbitingSignals(
    center: Offset,
    pose: BrainPose,
    rotation: Float,
) {
    repeat(9) { index ->
        val angle = rotation * 1.3f + index * (2f * PI.toFloat() / 9f)
        val point =
            projectPoint(
                x = 0.91f * cos(angle),
                y = 0.39f * sin(angle * 1.2f),
                z = 0.78f * sin(angle),
                center = center,
                pose = pose,
            )
        val radius = if (index % 3 == 0) 2.4.dp.toPx() else 1.5.dp.toPx()
        drawCircle(
            color = Color(0xFF83DFFF).copy(alpha = 0.2f),
            radius = radius * 3.2f,
            center = point.position,
        )
        drawCircle(
            color = if (index % 3 == 0) Color(0xFFBDF6FF) else Color(0xFF73F1C3),
            radius = radius,
            center = point.position,
        )
    }
}
