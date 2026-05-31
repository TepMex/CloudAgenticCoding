package com.tepmex.ctxcalendar.ui.maps

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun rememberOsmMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = View.generateViewId()
            clipToOutline = true
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
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
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }

    return mapView
}

@Composable
fun OsmMapView(
    pathPoints: List<GeoPoint>,
    visitMarkers: List<OsmMapMarker>,
    fitBounds: Boolean,
    onFitBoundsApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberOsmMapView()

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()

            if (pathPoints.size >= 2) {
                view.overlays.add(
                    Polyline().apply {
                        outlinePaint.strokeWidth = 8f
                        setPoints(pathPoints)
                    },
                )
            }

            visitMarkers.forEach { markerData ->
                view.overlays.add(
                    Marker(view).apply {
                        position = markerData.position
                        title = markerData.title
                        snippet = markerData.snippet
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
            }

            if (fitBounds) {
                val allPoints = buildList {
                    addAll(pathPoints)
                    addAll(visitMarkers.map { it.position })
                }
                if (allPoints.isNotEmpty()) {
                    view.post {
                        if (allPoints.size == 1) {
                            view.controller.setZoom(14.0)
                            view.controller.setCenter(allPoints.first())
                        } else {
                            val bounds = BoundingBox.fromGeoPoints(allPoints)
                            view.zoomToBoundingBox(bounds, true, 64)
                        }
                        onFitBoundsApplied()
                    }
                } else {
                    onFitBoundsApplied()
                }
            }

            view.invalidate()
        },
    )
}

data class OsmMapMarker(
    val position: GeoPoint,
    val title: String,
    val snippet: String?,
)
