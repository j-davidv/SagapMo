package com.sagapmo.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.*
import org.maplibre.compose.resource.*
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.style.BaseStyle

const val MAP_STYLE_2D_URL = "sagapmo://campus/styles/bright.json"
const val MAP_STYLE_3D_URL = "sagapmo://campus/styles/liberty.json"

val DefaultMapStyle2D: BaseStyle = BaseStyle.Uri(MAP_STYLE_2D_URL)
val MapStyle3D: BaseStyle = BaseStyle.Uri(MAP_STYLE_3D_URL)
val OnlineMapStyle2D: BaseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/bright")
val OnlineMapStyle3D: BaseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty")

internal fun campusMapStyle(use3dMap: Boolean, internetAvailable: Boolean): BaseStyle =
    if (internetAvailable) {
        if (use3dMap) OnlineMapStyle3D else OnlineMapStyle2D
    } else {
        if (use3dMap) MapStyle3D else DefaultMapStyle2D
    }

internal fun campusMapNotice(style: BaseStyle, camera: CameraPosition): String? {
    if (style != DefaultMapStyle2D && style != MapStyle3D) return null
    if (camera.center.longitude !in 121.055..121.095 || camera.center.latitude !in 13.765..13.805)
        return "Outside the offline campus map. Use Return to campus overview."
    if (camera.zoom < 12.0) return "Zoom in to see the offline campus map."
    return null
}

internal class ObservedMapResources(private val delegate: MapResourceProvider,
    private val onResult: (String, Boolean) -> Unit) : MapResourceProvider {
    override fun accepts(request: MapResourceRequest) = delegate.accepts(request)
    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
        try {
            return delegate.load(request).also { onResult(request.url, it is MapResourceLoad.Failed) }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { onResult(request.url, true); throw error }
    }
}

@Composable internal fun rememberCampusMapResources(): MapResourceProvider {
    val assets = LocalContext.current.applicationContext.assets
    return remember(assets) { CampusMapResources { path -> assets.open(path).use { it.readBytes() } } }
}

internal class CampusMapResources(private val readAsset: (String) -> ByteArray) : MapResourceProvider {
    override fun accepts(request: MapResourceRequest) = request.url.startsWith("sagapmo://campus/")

    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad = runInterruptible(Dispatchers.IO) {
        try {
            val uri = URI.create(request.url)
            require(uri.scheme == "sagapmo" && uri.host == "campus")
            val path = uri.path.removePrefix("/")
            require(path.isNotEmpty() && '\\' !in path && path.split('/').none { it == ".." || it == "." || it.isEmpty() })
            MapResourceLoad.Bytes(readAsset("campus-map/$path"))
        } catch (error: IOException) {
            MapResourceLoad.Failed(MapResourceError.NotFound, "Bundled campus map resource is missing")
        } catch (error: IllegalArgumentException) {
            MapResourceLoad.Failed(MapResourceError.Other, "Invalid campus map resource URL")
        }
    }
}
