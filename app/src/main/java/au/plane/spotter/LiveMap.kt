package au.plane.spotter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.util.LruCache
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import kotlin.math.roundToInt

/** Draws a plane icon pointing along its track, with an optional label underneath. */
private class PlaneIcons(private val context: Context) {
    private val d = context.resources.displayMetrics.density
    private val cache = LruCache<String, BitmapDrawable>(300)
    private val base: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_map_plane)!!
    private val paint = Paint().apply {
        isAntiAlias = true; color = android.graphics.Color.WHITE; textSize = 11 * d
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        setShadowLayer(3 * d, 0f, 0f, android.graphics.Color.BLACK)
    }

    /** Returns the icon and the vertical anchor (fraction from the top) at the plane's centre. */
    fun get(trackDeg: Double?, label: String?, ground: Boolean, small: Boolean = false): Pair<BitmapDrawable, Float> {
        val t = ((trackDeg ?: 0.0) / 10).roundToInt() * 10
        val key = "$t|$label|$ground|$small"
        val size = (if (ground) 12 else if (small) 15 else 26) * d
        val w = if (label != null) (110 * d) else size + 4 * d
        val h = if (label != null) size + 18 * d else size + 4 * d
        val cy = (size + 4 * d) / 2
        val icon = cache.get(key) ?: run {
            val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.save()
            c.rotate(t.toFloat(), w / 2, cy)
            val half = size / 2
            base.setBounds((w / 2 - half).toInt(), (cy - half).toInt(), (w / 2 + half).toInt(), (cy + half).toInt())
            base.alpha = if (ground) 170 else 255
            base.draw(c)
            c.restore()
            if (label != null) c.drawText(label, w / 2, h - 4 * d, paint)
            BitmapDrawable(context.resources, bmp).also { cache.put(key, it) }
        }
        return icon to (cy / h)
    }
}

/** A slippy OpenStreetMap map showing live aircraft, you, and (optionally) accident sites. */
@Composable
fun LiveMap(
    aircraft: List<LiveAircraft>,
    now: Long,
    user: Location?,
    accidents: List<Accident>,
    showAccidents: Boolean,
    onToggleAccidents: () -> Unit,
    startLat: Double,
    startLon: Double,
    startZoom: Double,
    radiusKm: Double,
    onViewChanged: (lat: Double, lon: Double, zoom: Double, radiusKm: Double) -> Unit,
    onPlane: (String) -> Unit,
    onAccident: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val viewChanged by rememberUpdatedState(onViewChanged)
    val planeClick by rememberUpdatedState(onPlane)
    val accidentClick by rememberUpdatedState(onAccident)
    var recentre by remember { mutableIntStateOf(0) }

    val icons = remember { PlaneIcons(context) }
    val planeFolder = remember { FolderOverlay() }
    val accidentFolder = remember { FolderOverlay() }
    val planeMarkers = remember { mutableMapOf<String, Marker>() }

    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = "PlaneSpotter/${BuildConfig.VERSION_NAME} (github.com/nugggy/plane-spotter)"
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 2.5
            maxZoomLevel = 18.0
            isTilesScaledToDpi = true
            isVerticalMapRepetitionEnabled = false
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
            controller.setZoom(startZoom)
            controller.setCenter(GeoPoint(startLat, startLon))
            overlays.add(accidentFolder)
            overlays.add(planeFolder)
            overlays.add(CopyrightOverlay(context))
        }
    }
    val youMarker = remember {
        Marker(map).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_you)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
            title = "You"
        }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        val listener = DelayedMapListener(object : MapListener {
            fun report(): Boolean {
                val bb = map.boundingBox
                val c = map.mapCenter
                val r = Geo.distanceM(c.latitude, c.longitude, bb.latNorth, bb.lonEast) / 1000
                viewChanged(c.latitude, c.longitude, map.zoomLevelDouble, r)
                return false
            }
            override fun onScroll(event: ScrollEvent?) = report()
            override fun onZoom(event: ZoomEvent?) = report()
        }, 700)
        map.addMapListener(listener)
        onDispose {
            map.removeMapListener(listener)
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
        }
    }

    // Accident markers only change when the toggle does.
    LaunchedEffect(showAccidents, accidents) {
        accidentFolder.items.clear()
        if (showAccidents) accidents.forEach { a ->
            accidentFolder.add(Marker(map).apply {
                position = GeoPoint(a.lat, a.lon)
                icon = ContextCompat.getDrawable(context, if (a.everyoneSurvived) R.drawable.ic_accident_ok else R.drawable.ic_accident)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> accidentClick(a.id); true }
            })
        }
        map.invalidate()
    }

    LaunchedEffect(recentre) {
        if (recentre > 0) user?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude), 11.0, 800L) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { m ->
            val showLabels = m.zoomLevelDouble >= 8.5
            val seen = HashSet<String>()
            aircraft.forEach { ac ->
                seen += ac.hex
                val (lat, lon) = ac.extrapolated(now)
                val marker = planeMarkers.getOrPut(ac.hex) {
                    Marker(m).apply {
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ -> planeClick(ac.hex); true }
                        planeFolder.add(this)
                    }
                }
                val (ic, anchorY) = icons.get(ac.trackDeg, if (showLabels) ac.callsign ?: ac.registration else null, ac.onGround, small = m.zoomLevelDouble < 7)
                if (marker.icon !== ic) {
                    marker.icon = ic
                    marker.setAnchor(Marker.ANCHOR_CENTER, anchorY)
                }
                marker.position = GeoPoint(lat, lon)
            }
            planeMarkers.keys.filter { it !in seen }.forEach { hex ->
                planeMarkers.remove(hex)?.let { planeFolder.remove(it) }
            }
            m.overlays.remove(youMarker)
            if (user != null) {
                youMarker.position = GeoPoint(user.latitude, user.longitude)
                m.overlays.add(youMarker)
            }
            m.invalidate()
        })

        Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = showAccidents, onClick = onToggleAccidents,
                label = { Text(if (showAccidents) "🛡️ Crash sites on" else "🛡️ Show crash sites") },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp)) {
                Text(
                    "${aircraft.count { !it.onGround }} planes flying within about ${radiusKm.roundToInt()} km of the middle of the map",
                    color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp),
                )
            }
        }
        if (user != null) {
            SmallFloatingActionButton(
                onClick = { recentre++ },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Text("📍") }
        }
    }
}
