package com.tepmex.zoulushang2.map

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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun rememberOsmMapView(
    enableMyLocation: Boolean,
): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = View.generateViewId()
            clipToOutline = true
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(39.9, 116.4))
        }
    }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    DisposableEffect(mapView, enableMyLocation) {
        val overlay = locationOverlay ?: MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).also {
            locationOverlay = it
            mapView.overlays.add(it)
        }
        if (enableMyLocation) {
            overlay.enableMyLocation()
        } else {
            overlay.disableMyLocation()
        }
        onDispose {
            overlay.disableMyLocation()
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

fun MapView.centerOnMyLocation(): Boolean {
    val overlay = overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull() ?: return false
    val location = overlay.myLocation ?: return false
    controller.animateTo(location)
    return true
}

@Composable
fun PaintedMap(
    paintLookup: HashMap<Long, Int>,
    enableMyLocation: Boolean,
    recenterMyLocationToken: Int,
    modifier: Modifier = Modifier,
    onViewportChanged: () -> Unit = {},
) {
    val mapView = rememberOsmMapView(enableMyLocation = enableMyLocation)
    var overlay by remember { mutableStateOf<PaintOverlay?>(null) }
    var lastLookupKey by remember { mutableStateOf<Long?>(null) }
    var lastRecenterToken by remember { mutableStateOf(recenterMyLocationToken) }

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
            val currentOverlay = overlay ?: PaintOverlay(paintLookup).also {
                overlay = it
                view.overlays.add(0, it)
            }

            val lookupKey = paintLookup.entries.fold(0L) { acc, (key, intensity) ->
                acc xor key xor intensity.toLong()
            }
            if (lookupKey != lastLookupKey) {
                currentOverlay.updateLookup(paintLookup)
                lastLookupKey = lookupKey
            }

            if (recenterMyLocationToken != lastRecenterToken) {
                lastRecenterToken = recenterMyLocationToken
                view.post { view.centerOnMyLocation() }
            }

            view.invalidate()
        },
    )
}
