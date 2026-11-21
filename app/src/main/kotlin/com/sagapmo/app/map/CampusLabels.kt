package com.sagapmo.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

internal data class CampusLandmark(val name: String, val position: Position)

// shortcut: anchors estimated from the user's map; replace with surveyed coordinates when available.
internal val campusLandmarks = listOf(
    CampusLandmark("CIT Building", Position(121.073717, 13.784972)),
    CampusLandmark("Student\nServices\nCenter", Position(121.074155, 13.784929)),
    CampusLandmark("STEER HUB", Position(121.073772, 13.784529)),
    CampusLandmark("GYMNASIUM", Position(121.073940, 13.784147)),
    CampusLandmark("CEAFA Building", Position(121.074781, 13.784252)),
    CampusLandmark("Student\nCenter", Position(121.074262, 13.783801)),
    CampusLandmark("CICS Building", Position(121.074012, 13.783592)),
    CampusLandmark("RGR Building", Position(121.074423, 13.783498)),
)

@Composable internal fun CampusLabel(landmark: CampusLandmark) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val text = measurer.measure(AnnotatedString(landmark.name),
        style = TextStyle(color = Color(0xFF303A34), fontSize = if ('\n' in landmark.name) 9.sp else 11.sp, lineHeight = 13.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            shadow = Shadow(color = Color.White, blurRadius = 6f)),
        constraints = Constraints(maxWidth = with(density) { 100.dp.roundToPx() }))
    val padding = with(density) { 3.dp.toPx() }
    // Render the existing typography once; native symbols then move in the same frame as the map.
    val painter = remember(text, padding) {
        object : Painter() {
            override val intrinsicSize = Size(text.size.width + padding * 2, text.size.height + padding * 2)
            override fun DrawScope.onDraw() { drawText(text, topLeft = Offset(padding, padding)) }
        }
    }
    val source = rememberGeoJsonSource(GeoJsonData.Features(Point(landmark.position)))
    SymbolLayer(id = "campus-${landmark.name}", source = source, minZoom = 17f,
        iconImage = image(painter), iconAllowOverlap = const(true), iconIgnorePlacement = const(true),
        iconRotationAlignment = const(IconRotationAlignment.Viewport), iconPitchAlignment = const(IconPitchAlignment.Viewport))
}
