package com.sagapmo.app.speedtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedTestGauge(
    progress: SpeedTestProgress?,
    modifier: Modifier = Modifier
) {
    val phase = progress?.phase ?: SpeedTestPhase.IDLE
    val targetProgress = progress?.overallProgress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "speed_progress"
    )

    val currentSpeedMbps = progress?.currentRateMbps
        ?: when (phase) {
            SpeedTestPhase.DOWNLOAD -> progress?.downloadMbps
            SpeedTestPhase.UPLOAD -> progress?.uploadMbps
            SpeedTestPhase.COMPLETED -> progress?.downloadMbps
            else -> null
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speed-test-gauge"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                val startAngle = 140f
                val totalSweep = 260f

                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                val currentSweep = totalSweep * animatedProgress
                if (currentSweep > 0f) {
                    drawArc(
                        color = primaryColor,
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                val needleAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                val radius = diameter / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val needleEnd = Offset(
                    (center.x + (radius) * cos(needleAngleRad)).toFloat(),
                    (center.y + (radius) * sin(needleAngleRad)).toFloat()
                )
                drawCircle(
                    color = primaryColor,
                    radius = strokeWidth / 2f + 2f,
                    center = needleEnd
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (phase == SpeedTestPhase.LATENCY && progress?.latencyMs != null) {
                    Text(
                        text = String.format(Locale.US, "%.0f", progress.latencyMs),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Text(
                        text = "ms latency",
                        fontSize = 12.sp,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else if (currentSpeedMbps != null) {
                    Text(
                        text = String.format(Locale.US, "%.1f", currentSpeedMbps),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Text(
                        text = "Mbps",
                        fontSize = 12.sp,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "--",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceVariant
                    )
                    Text(
                        text = "Mbps",
                        fontSize = 12.sp,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        val phaseLabel = when (phase) {
            SpeedTestPhase.IDLE -> "Preparing speed test…"
            SpeedTestPhase.LATENCY -> "Measuring latency & jitter…"
            SpeedTestPhase.DOWNLOAD -> "Testing download speed…"
            SpeedTestPhase.UPLOAD -> "Testing upload speed…"
            SpeedTestPhase.COMPLETED -> "Speed test completed"
            SpeedTestPhase.FAILED -> "Speed test failed"
        }
        Text(
            text = phaseLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = onSurface,
            textAlign = TextAlign.Center
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Download: ${progress?.downloadMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface
                    )
                    Text(
                        text = "Upload: ${progress?.uploadMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Latency: ${progress?.latencyMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                    Text(
                        text = "Jitter: ${progress?.jitterMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
            }
        }
    }
}
