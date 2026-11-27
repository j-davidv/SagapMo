package com.sagapmo.app.map

import org.junit.Assert.*
import org.junit.Test
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

class MapStyleSelectionTest {
    @Test fun reconnectSwitchesBothStylesToOnlineCoverage() {
        val outsideCampus = CameraPosition(center = Position(121.2, 13.9), zoom = 17.0)
        for (use3d in listOf(false, true)) {
            assertNotNull(campusMapNotice(campusMapStyle(use3d, false), outsideCampus))
            val online = campusMapStyle(use3d, true)
            assertEquals(BaseStyle.Uri("https://tiles.openfreemap.org/styles/${if (use3d) "liberty" else "bright"}"), online)
            assertNull(campusMapNotice(online, outsideCampus))
            assertEquals(if (use3d) MapStyle3D else DefaultMapStyle2D, campusMapStyle(use3d, false))
        }
    }
}
