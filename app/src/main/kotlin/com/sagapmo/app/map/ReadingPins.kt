package com.sagapmo.app.map

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sagapmo.app.reading.ConnectionType
import com.sagapmo.app.reading.Rating
import com.sagapmo.app.ui.label
import com.sagapmo.app.ui.ratingPair
import com.sagapmo.app.ui.symbol
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

private val PinShape = GenericShape { size, _ ->
    val width = size.width
    val height = size.height
    moveTo(width / 2, height)
    cubicTo(width * .36f, height * .80f, 0f, height * .53f, 0f, height * .39f)
    cubicTo(0f, -height * .13f, width, -height * .13f, width, height * .39f)
    cubicTo(width, height * .53f, width * .64f, height * .80f, width / 2, height)
    close()
}

internal data class PinArtwork(val painter: Painter, val width: Dp, val height: Dp, val labelHeight: Dp)

@Composable internal fun rememberPinArtwork(type: ConnectionType, rating: Rating): PinArtwork {
    val density = LocalDensity.current
    val (pinColor, iconColor) = ratingPair(rating)
    val colors = MaterialTheme.colorScheme
    val label = rememberTextMeasurer().measure(
        AnnotatedString(rating.label()),
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface),
    )
    return remember(density, type, pinColor, iconColor, colors.surface, label) {
        with(density) {
            val bodySize = Size(36.dp.toPx(), 44.dp.toPx())
            val labelWidth = label.size.width + 10.dp.toPx()
            val labelHeight = label.size.height + 4.dp.toPx()
            val imageSize = Size(maxOf(48.dp.toPx(), labelWidth), bodySize.height + labelHeight)
            val icon = PathParser().parsePathString(type.symbol().data).toPath()
            val painter = object : Painter() {
                override val intrinsicSize = imageSize

                override fun DrawScope.onDraw() {
                    val body = (PinShape.createOutline(bodySize, layoutDirection, this) as Outline.Generic).path
                    translate((size.width - bodySize.width) / 2) {
                        drawPath(body, pinColor)
                        clipPath(body) { drawPath(body, colors.surface, style = Stroke(4.dp.toPx())) }
                    }
                    translate((size.width - 22.dp.toPx()) / 2, 6.dp.toPx()) {
                        scale(22.dp.toPx() / 24f, pivot = Offset.Zero) {
                            drawPath(icon, iconColor, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    val labelLeft = (size.width - labelWidth) / 2
                    drawRoundRect(colors.surface, Offset(labelLeft, bodySize.height),
                        Size(labelWidth, labelHeight), CornerRadius(4.dp.toPx()))
                    drawText(label, topLeft = Offset(labelLeft + 5.dp.toPx(), bodySize.height + 2.dp.toPx()))
                }
            }
            PinArtwork(painter, imageSize.width.toDp(), imageSize.height.toDp(), labelHeight.toDp())
        }
    }
}

@Composable internal fun ReadingPinLayer(id: String, position: Position, artwork: PinArtwork) {
    val source = rememberGeoJsonSource(GeoJsonData.Features(Point(position)))
    // The rating label sits below the pin tip, which marks the saved position.
    SymbolLayer(id = "pin-$id", source = source, iconImage = image(artwork.painter),
        iconAnchor = const(SymbolAnchor.Bottom), iconOffset = const(DpOffset(0.dp, artwork.labelHeight)),
        iconAllowOverlap = const(true), iconIgnorePlacement = const(true),
        iconRotationAlignment = const(IconRotationAlignment.Viewport),
        iconPitchAlignment = const(IconPitchAlignment.Viewport))
}
