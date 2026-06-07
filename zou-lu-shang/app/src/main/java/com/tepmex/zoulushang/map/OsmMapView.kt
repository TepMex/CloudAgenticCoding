package com.tepmex.zoulushang.map

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun rememberOsmMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = View.generateViewId()
            clipToOutline = true
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(39.9, 116.4))
        }
    }

    val lifecycleObserver = remember(mapView) {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose { lifecycle.removeObserver(lifecycleObserver) }
    }

    return mapView
}

@Composable
fun VisitedTilesMap(
    visitedLookup: HashMap<Long, Int>,
    fitBounds: BoundingBox?,
    modifier: Modifier = Modifier,
    onViewportChanged: () -> Unit = {},
) {
    val mapView = rememberOsmMapView()
    var overlay by remember { mutableStateOf<VisitedTilesOverlay?>(null) }
    var lastLookupKey by remember { mutableStateOf<Long?>(null) }
    var lastBoundsKey by remember { mutableStateOf<String?>(null) }

    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                onViewportChanged()
                mapView.invalidate()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                onViewportChanged()
                mapView.invalidate()
                return false
            }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val currentOverlay = overlay ?: VisitedTilesOverlay(visitedLookup).also {
                overlay = it
                view.overlays.add(it)
            }
            val lookupKey = visitedLookup.entries.fold(0L) { acc, (key, count) ->
                acc xor key xor count.toLong()
            }
            if (lookupKey != lastLookupKey) {
                currentOverlay.updateLookup(visitedLookup)
                lastLookupKey = lookupKey
            }

            val boundsKey = fitBounds?.let {
                "${it.latNorth},${it.lonEast},${it.latSouth},${it.lonWest}"
            }
            if (fitBounds != null && boundsKey != lastBoundsKey) {
                view.post {
                    view.zoomToBoundingBox(fitBounds, true, 64)
                    lastBoundsKey = boundsKey
                }
            }

            view.invalidate()
        },
    )
}
