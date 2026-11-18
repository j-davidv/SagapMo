package com.sagapmo.app.map

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable internal fun CampusLabel(landmark: CampusLandmark, modifier: Modifier) {
    Text(landmark.name, modifier.widthIn(max = 100.dp).testTag("landmark-${landmark.name.replace('\n', ' ')}"),
        style = TextStyle(color = Color(0xFF303A34), fontSize = if ('\n' in landmark.name) 9.sp else 11.sp, lineHeight = 13.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            shadow = Shadow(color = Color.White, blurRadius = 6f)))
}
