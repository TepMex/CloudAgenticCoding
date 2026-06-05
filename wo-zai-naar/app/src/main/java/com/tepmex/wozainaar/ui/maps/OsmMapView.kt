package com.tepmex.wozainaar.ui.maps

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
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(20.0, 0.0))
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
fun DailyMovementMap(
    pathPoints: List<GeoPoint>,
    fitBounds: Boolean,
    onFitBoundsApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberOsmMapView()
    var lastPointCount by remember { mutableStateOf(-1) }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            if (pathPoints.size == lastPointCount && !fitBounds) return@AndroidView
            lastPointCount = pathPoints.size

            view.overlays.clear()

            if (pathPoints.size >= 2) {
                view.overlays.add(
                    Polyline().apply {
                        outlinePaint.strokeWidth = 8f
                        setPoints(pathPoints)
                    },
                )
            }

            pathPoints.forEachIndexed { index, point ->
                view.overlays.add(
                    Marker(view).apply {
                        position = point
                        title = "Sample ${index + 1}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
            }

            if (fitBounds && pathPoints.isNotEmpty()) {
                view.post {
                    if (pathPoints.size == 1) {
                        view.controller.setZoom(15.0)
                        view.controller.setCenter(pathPoints.first())
                    } else {
                        val bounds = BoundingBox.fromGeoPoints(pathPoints)
                        view.zoomToBoundingBox(bounds, true, 72)
                    }
                    onFitBoundsApplied()
                }
            } else if (fitBounds && pathPoints.isEmpty()) {
                view.post {
                    view.controller.setZoom(4.0)
                    view.controller.setCenter(GeoPoint(20.0, 0.0))
                    onFitBoundsApplied()
                }
            } else if (fitBounds) {
                onFitBoundsApplied()
            }

            view.invalidate()
        },
    )
}
