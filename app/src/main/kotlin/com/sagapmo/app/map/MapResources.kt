package com.sagapmo.app.map

import java.net.HttpURLConnection
import java.net.URI
import kotlin.time.Instant
import kotlinx.coroutines.*
import org.maplibre.compose.resource.*
import org.maplibre.compose.style.BaseStyle

const val MAP_STYLE_2D_URL = "https://tiles.openfreemap.org/styles/bright"
const val MAP_STYLE_3D_URL = "https://tiles.openfreemap.org/styles/liberty"

val DefaultMapStyle2D: BaseStyle = BaseStyle.Uri(MAP_STYLE_2D_URL)
val MapStyle3D: BaseStyle = BaseStyle.Uri(MAP_STYLE_3D_URL)

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

// Native can render a full frame after a sprite fails; observe these requests directly.
internal object AndroidSpriteResources : MapResourceProvider {
    override fun accepts(request: MapResourceRequest) = request.kind == MapResourceKind.SpriteJson || request.kind == MapResourceKind.SpriteImage
    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad = runInterruptible(Dispatchers.IO) {
        val url = URI(request.url).toURL()
        require(url.protocol == "https" || url.protocol == "http")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            request.priorEtag?.let { connection.setRequestProperty("If-None-Match", it) }
            request.priorModified?.let { connection.ifModifiedSince = it.toEpochMilliseconds() }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> MapResourceLoad.NotModified()
                in 200..299 -> MapResourceLoad.Bytes(connection.inputStream.use { it.readBytes() },
                    etag = connection.getHeaderField("ETag"),
                    modified = connection.lastModified.takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds),
                    expires = connection.expiration.takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds))
                else -> MapResourceLoad.Failed(MapResourceError.Server, "Sprite request returned HTTP ${connection.responseCode}")
            }
        } finally { connection.disconnect() }
    }
}
