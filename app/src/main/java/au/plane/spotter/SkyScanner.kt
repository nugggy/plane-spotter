package au.plane.spotter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val EUREKA_TOWER_M = 297.0
private const val POLL_MS = 8000L
private const val MELBOURNE_LAT = -37.8136
private const val MELBOURNE_LON = 144.9631

enum class ScanMode(val label: String, val title: String) {
    CAMERA("📷 Camera", "Sky scanner"),
    RADAR("📡 Radar", "Radar"),
    MAP("🗺️ Map", "Live map"),
    LIST("📋 List", "Planes near you"),
}

/** Where a live aircraft sits relative to the person holding the phone (null when location is unknown). */
data class SkyPosition(
    val ac: LiveAircraft,
    val distanceM: Double?,
    val groundM: Double?,
    val bearingDeg: Double?,
    val elevationDeg: Double?,
)

private fun hasPermission(context: Context, p: String) =
    ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

/** adsb.lol takes a radius in nautical miles, up to 250. Bucketed so small map moves don't refetch. */
private fun radiusBucketNm(km: Double): Int {
    val nm = km / 1.852
    return listOf(10, 20, 40, 80, 160, 250).firstOrNull { it >= nm } ?: 250
}

@Composable
fun SkyScannerScreen(planes: List<Plane>, store: SpotterStore, start: Screen.Scanner, go: (Screen) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var locationOk by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    var cameraOk by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationOk = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        cameraOk = hasPermission(context, Manifest.permission.CAMERA)
        asked = true
    }
    val request = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA))
    }
    Scanner(planes, store, start, locationOk, cameraOk, asked, request, go, back)
}

@Composable
private fun PermissionIntro(asked: Boolean, request: () -> Unit, useMap: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("📡", style = MaterialTheme.typography.displayMedium)
        Text("See the planes flying around you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("The camera view and radar use live data from planes' radio transponders to show which aircraft you're looking at, where it's flying from and to, how high it is and how fast it's going.")
        Spacer(Modifier.height(12.dp))
        Text("They need to know roughly where you are. The camera lets you see the sky behind the labels.")
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp)) {
                Text("For parents", fontWeight = FontWeight.Bold)
                Text(
                    "Your exact location stays on this phone. To find nearby aircraft the app sends only a rounded position (about 1 km) to the free flight-tracking service adsb.lol. Map pictures come from OpenStreetMap, which sees the area being viewed. Nothing is saved or shared, and the camera picture never leaves the phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = request, modifier = Modifier.fillMaxWidth()) { Text("Turn on location and camera") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = useMap, modifier = Modifier.fillMaxWidth()) { Text("Just show me the map") }
        if (asked) {
            Spacer(Modifier.height(12.dp))
            Text("Location is still off. You can turn it on for Plane Spotter in your phone's Settings, under Apps.", color = MaterialTheme.colorScheme.error)
        }
    }
}

// ---------- Sensors and location ----------

@SuppressLint("MissingPermission")
@Composable
private fun rememberLocation(enabled: Boolean): Location? {
    val context = LocalContext.current
    var loc by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { l -> if (loc == null || l.accuracy <= (loc?.accuracy ?: Float.MAX_VALUE) + 50) loc = l }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            .forEach { p ->
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { if (loc == null) loc = it }
                runCatching { lm.requestLocationUpdates(p, 2000L, 5f, listener, Looper.getMainLooper()) }
            }
        onDispose { lm.removeUpdates(listener) }
    }
    return loc
}

/** Rotation matrix (device to world, world = east/north/up relative to magnetic north), smoothed. */
@Composable
private fun rememberRotation(enabled: Boolean): FloatArray? {
    val context = LocalContext.current
    var matrix by remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val smooth = FloatArray(9)
        var first = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val r = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(r, e.values)
                for (i in 0 until 9) smooth[i] = if (first) r[i] else smooth[i] + 0.2f * (r[i] - smooth[i])
                first = false
                matrix = smooth.copyOf()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    return matrix
}

/** Full field of view of the back camera in degrees (horizontal, vertical) with the phone upright. */
private fun cameraFov(context: Context): Pair<Double, Double> = runCatching {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
    val ch = cm.getCameraCharacteristics(id)
    val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
    val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!.first()
    // The sensor's long side lies along the phone's height when held upright.
    val h = Math.toDegrees(2 * kotlin.math.atan(size.height / (2.0 * f)))
    val v = Math.toDegrees(2 * kotlin.math.atan(size.width / (2.0 * f)))
    h to v
}.getOrDefault(50.0 to 65.0)

// ---------- Main scanner ----------

@Composable
private fun Scanner(
    planes: List<Plane>, store: SpotterStore, start: Screen.Scanner, locationOk: Boolean, cameraOk: Boolean,
    asked: Boolean, request: () -> Unit, go: (Screen) -> Unit, back: () -> Unit,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(start.mode) }
    var showAccidents by rememberSaveable { mutableStateOf(start.accidents && store.accidentIntroSeen) }
    var radarRangeKm by rememberSaveable { mutableIntStateOf(25) }
    var headingUp by rememberSaveable { mutableStateOf(true) }
    var mapLat by rememberSaveable { mutableDoubleStateOf(MELBOURNE_LAT) }
    var mapLon by rememberSaveable { mutableDoubleStateOf(MELBOURNE_LON) }
    var mapZoom by rememberSaveable { mutableDoubleStateOf(9.0) }
    var mapRadiusKm by rememberSaveable { mutableDoubleStateOf(80.0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var centredOnUser by rememberSaveable { mutableStateOf(false) }
    var showAccidentIntro by remember { mutableStateOf(start.accidents && !store.accidentIntroSeen) }

    val location = rememberLocation(locationOk)
    val rotation = rememberRotation(mode == ScanMode.CAMERA || mode == ScanMode.RADAR)
    val fov = remember { cameraFov(context) }
    val accidents = remember { AccidentRepo.all(context) }
    var aircraft by remember { mutableStateOf<List<LiveAircraft>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // The first time the map opens with location on, start it over the user rather than the city.
    LaunchedEffect(location != null) {
        if (location != null && !centredOnUser) {
            mapLat = location.latitude; mapLon = location.longitude; centredOnUser = true
        }
    }

    // What to ask the flight tracker for: around you, or around the middle of the map.
    val target: Triple<Int, Int, Int>? = when (mode) {
        ScanMode.MAP -> Triple((mapLat * 100).roundToInt(), (mapLon * 100).roundToInt(), radiusBucketNm(mapRadiusKm))
        else -> location?.let {
            val nm = if (mode == ScanMode.RADAR) radiusBucketNm(radarRangeKm * 1.1) else 40
            Triple((it.latitude * 100).roundToInt(), (it.longitude * 100).roundToInt(), nm)
        }
    }
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        while (true) {
            try {
                aircraft = LiveApi.nearby(t.first / 100.0, t.second / 100.0, t.third)
                status = if (aircraft.isEmpty()) "No aircraft are being tracked here right now." else null
            } catch (e: Exception) {
                status = "Can't reach the flight tracker. Check the internet connection."
            }
            delay(POLL_MS)
        }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    val positions = remember(aircraft, location, now) {
        aircraft.map { ac ->
            val loc = location ?: return@map SkyPosition(ac, null, null, null, null)
            val userAlt = if (loc.hasAltitude()) loc.altitude else 0.0
            val (lat, lon) = ac.extrapolated(now)
            val ground = Geo.distanceM(loc.latitude, loc.longitude, lat, lon)
            val height = (ac.altitudeM ?: userAlt) - userAlt
            SkyPosition(ac, hypot(ground, height), ground, Geo.bearingDeg(loc.latitude, loc.longitude, lat, lon),
                if (ac.onGround) 0.0 else Geo.elevationDeg(ground, height))
        }.sortedBy { it.distanceM ?: 0.0 }
    }
    val declination = remember(location?.latitude?.roundToInt(), location?.longitude?.roundToInt()) {
        location?.let { GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis()).declination } ?: 0f
    }

    val sel = selected?.let { h -> positions.firstOrNull { it.ac.hex == h } }
    if (sel != null) {
        BackHandler { selected = null }
        FlightDetails(sel, planes, store, go) { selected = null }
        return
    }
    BackHandler(enabled = mode != start.mode) { mode = start.mode }

    Scaffold(
        topBar = { BackBar(mode.title, back) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ScanMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val needsLocation = mode != ScanMode.MAP && !locationOk
            when {
                needsLocation -> PermissionIntro(asked, request) { mode = ScanMode.MAP }
                mode == ScanMode.CAMERA -> CameraMode(positions, rotation, declination, fov, cameraOk, location, status, planes) { selected = it }
                mode == ScanMode.RADAR -> {
                    if (location == null) Waiting("Finding where you are...")
                    else RadarView(
                        positions, rotation?.let { phoneHeadingDeg(it, declination) }, headingUp, radarRangeKm,
                        { radarRangeKm = it }, { headingUp = it }, { selected = it },
                    )
                }
                mode == ScanMode.MAP -> LiveMap(
                    aircraft = aircraft, now = now, user = location, accidents = accidents, showAccidents = showAccidents,
                    onToggleAccidents = {
                        if (!showAccidents && !store.accidentIntroSeen) showAccidentIntro = true
                        else showAccidents = !showAccidents
                    },
                    startLat = mapLat, startLon = mapLon, startZoom = mapZoom, radiusKm = radiusBucketNm(mapRadiusKm) * 1.852,
                    onViewChanged = { la, lo, z, r -> mapLat = la; mapLon = lo; mapZoom = z; mapRadiusKm = r },
                    onPlane = { selected = it },
                    onAccident = { go(Screen.Accident(it)) },
                )
                else -> NearbyList(positions, status, location != null) { selected = it }
            }
        }
    }

    if (showAccidentIntro) {
        AlertDialog(
            onDismissRequest = { showAccidentIntro = false },
            title = { Text("Crash sites") },
            text = {
                Text(
                    "These markers show places where aeroplanes have crashed or had serious emergencies. Green ones are where everyone survived.\n\n" +
                        "Some are sad, including a few that were caused on purpose or happened in wars. Flying is one of the safest ways to travel, and each page explains what changed afterwards to keep people safe.",
                )
            },
            confirmButton = {
                TextButton(onClick = { store.accidentIntroSeen = true; showAccidentIntro = false; showAccidents = true }) { Text("Show them") }
            },
            dismissButton = { TextButton(onClick = { showAccidentIntro = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun Waiting(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(msg, Modifier.padding(24.dp), textAlign = TextAlign.Center) }
}

@Composable
private fun CameraMode(
    positions: List<SkyPosition>, rotation: FloatArray?, declination: Float, fov: Pair<Double, Double>, cameraOk: Boolean,
    location: Location?, status: String?, planes: List<Plane>, select: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B3D91), Color(0xFF4FC3F7))))) {
        if (cameraOk) CameraPreview(Modifier.fillMaxSize())

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            // PreviewView fills the box and crops, so use the larger of the two focal lengths.
            val fPx = maxOf(wPx / 2 / tan(Math.toRadians(fov.first / 2)), hPx / 2 / tan(Math.toRadians(fov.second / 2))).toFloat()
            val cx = wPx / 2
            val cy = hPx / 2

            data class Screenpos(val p: SkyPosition, val x: Float, val y: Float, val inFront: Boolean, val dx: Float, val dy: Float)

            val projected = if (rotation == null) emptyList() else positions.filter { !it.ac.onGround && it.bearingDeg != null }.map { p ->
                val az = Math.toRadians(p.bearingDeg!! - declination)
                val el = Math.toRadians(p.elevationDeg ?: 0.0)
                val e = (cos(el) * sin(az)).toFloat()
                val n = (cos(el) * cos(az)).toFloat()
                val u = sin(el).toFloat()
                val r = rotation
                val dx = r[0] * e + r[3] * n + r[6] * u
                val dy = r[1] * e + r[4] * n + r[7] * u
                val dz = r[2] * e + r[5] * n + r[8] * u
                val inFront = dz < -0.05f
                Screenpos(p, if (inFront) cx + fPx * dx / -dz else Float.NaN, if (inFront) cy - fPx * dy / -dz else Float.NaN, inFront, dx, dy)
            }
            val onScreen = projected.filter { it.inFront && it.x in -40f..wPx + 40f && it.y in -40f..hPx + 40f }
            val lockRadius = wPx * 0.22f
            val locked = onScreen.minByOrNull { hypot((it.x - cx).toDouble(), (it.y - cy).toDouble()) }
                ?.takeIf { hypot((it.x - cx).toDouble(), (it.y - cy).toDouble()) < lockRadius }

            Canvas(Modifier.fillMaxSize()) {
                drawCircle(if (locked != null) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.8f), radius = lockRadius,
                    center = Offset(cx, cy), style = Stroke(width = 4f))
                drawLine(Color.White.copy(alpha = 0.6f), Offset(cx - 30, cy), Offset(cx + 30, cy), 3f)
                drawLine(Color.White.copy(alpha = 0.6f), Offset(cx, cy - 30), Offset(cx, cy + 30), 3f)
            }

            onScreen.forEach { s ->
                val isLocked = s.p.ac.hex == locked?.p?.ac?.hex
                with(density) {
                    Column(
                        Modifier.offset { IntOffset((s.x - 14.dp.toPx()).roundToInt(), (s.y - 14.dp.toPx()).roundToInt()) }
                            .clickable { select(s.p.ac.hex) },
                    ) {
                        Box(Modifier.size(28.dp).border(3.dp, if (isLocked) Color(0xFF66BB6A) else Sunset, CircleShape))
                        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
                            Text(markerLabel(s.p), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            if (onScreen.isEmpty() && projected.isNotEmpty()) {
                val target = projected.minBy { it.p.distanceM ?: 0.0 }
                var ax = target.dx
                var ay = -target.dy
                if (ax == 0f && ay == 0f) ay = -1f
                val len = hypot(ax.toDouble(), ay.toDouble()).toFloat().coerceAtLeast(0.0001f)
                ax /= len; ay /= len
                val angle = Math.toDegrees(atan2(ay.toDouble(), ax.toDouble())).toFloat()
                val ex = cx + ax * (wPx * 0.36f)
                val ey = cy + ay * (hPx * 0.30f)
                with(density) {
                    Column(
                        Modifier.offset { IntOffset((ex - 60.dp.toPx()).roundToInt(), (ey - 30.dp.toPx()).roundToInt()) }.width(120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("➜", color = Sunset, style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.rotate(angle))
                        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
                            Text("Nearest plane this way", color = Color.White, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            ) {
                Text("${positions.count { !it.ac.onGround }} flying · ${positions.count { it.ac.onGround }} on the ground",
                    color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                val msg = when {
                    location == null -> "Finding where you are..."
                    rotation == null -> "This phone has no compass sensor, so try the radar or the list instead."
                    else -> status
                }
                if (msg != null) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)) {
                        Text(msg, color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                } else if (locked != null) {
                    LockedCard(locked.p, planes) { select(locked.p.ac.hex) }
                } else {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)) {
                        Text("Aim the circle at a plane to lock on.", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                }
                Text("Live data: adsb.lol (ODbL) · routes: adsbdb.com", color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun markerLabel(p: SkyPosition): String {
    val name = p.ac.callsign ?: p.ac.registration ?: p.ac.hex.uppercase()
    val alt = p.ac.altitudeM?.let { " · ${(it / 1000).oneDp()} km up" } ?: ""
    return "$name${p.ac.typeLabel?.let { " · $it" } ?: ""}$alt"
}

@Composable
private fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

// ---------- Locked-on card, list and details ----------

@Composable
private fun rememberRoute(ac: LiveAircraft): FlightRoute? {
    val cs = ac.callsign ?: return null
    val r by produceState<FlightRoute?>(null, cs) { value = LiveApi.route(cs) }
    return r
}

@Composable
private fun rememberDetails(ac: LiveAircraft): AircraftDetails? {
    val d by produceState<AircraftDetails?>(null, ac.hex) { value = LiveApi.aircraft(ac.hex) }
    return d
}

private fun distanceText(p: SkyPosition) = p.distanceM?.let { "${(it / 1000).oneDp()} km away" }

@Composable
private fun LockedCard(p: SkyPosition, planes: List<Plane>, onOpen: () -> Unit) {
    val route = rememberRoute(p.ac)
    val details = rememberDetails(p.ac)
    val lib = TypeMap.libraryId(p.ac, details)?.let { id -> planes.firstOrNull { it.id == id } }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), elevation = CardDefaults.cardElevation(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (lib != null) PlanePhoto(lib, Modifier.width(110.dp).height(90.dp), targetWidth = 320)
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text("🎯 Locked on", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(route?.airline, route?.flightNumber ?: p.ac.callsign).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(lib?.name ?: details?.type ?: p.ac.typeLabel ?: "Unknown aircraft", style = MaterialTheme.typography.bodyMedium)
                if (route?.origin != null && route.destination != null) {
                    Text("${route.origin.short} ➜ ${route.destination.short}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(listOfNotNull(distanceText(p), p.ac.altitudeM?.let { "${metres(it)} up" }, "tap for details").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NearbyList(positions: List<SkyPosition>, status: String?, haveLocation: Boolean, select: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (!haveLocation) item { Text("Finding where you are...", Modifier.padding(16.dp)) }
        if (status != null) item { Text(status, Modifier.padding(16.dp)) }
        val flying = positions.filter { !it.ac.onGround }
        val ground = positions.filter { it.ac.onGround }
        if (flying.isNotEmpty()) item { Header("In the air") }
        items(flying, key = { "f" + it.ac.hex }) { p -> NearbyRow(p) { select(p.ac.hex) } }
        if (ground.isNotEmpty()) item { Header("On the ground") }
        items(ground, key = { "g" + it.ac.hex }) { p -> NearbyRow(p) { select(p.ac.hex) } }
        item {
            Text("Live data from adsb.lol, shared under the Open Database Licence. Flight routes from adsbdb.com and may sometimes be out of date.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(t: String) = Text(t, Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
private fun NearbyRow(p: SkyPosition, onClick: () -> Unit) {
    val route = rememberRoute(p.ac)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(route?.flightNumber ?: p.ac.callsign ?: p.ac.registration ?: p.ac.hex.uppercase(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (p.distanceM != null && p.bearingDeg != null) {
                    Text("${(p.distanceM / 1000).oneDp()} km ${Geo.compass(p.bearingDeg)}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(listOfNotNull(route?.airline, p.ac.typeLabel).joinToString(" · ").ifBlank { "Unknown" }, style = MaterialTheme.typography.bodySmall)
            if (route?.origin != null && route.destination != null) {
                Text("${route.origin.short} ➜ ${route.destination.short}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (!p.ac.onGround && p.bearingDeg != null) Text("Look ${Geo.compass(p.bearingDeg)}, ${(p.elevationDeg ?: 0.0).roundToInt()}° above the horizon",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FlightDetails(p: SkyPosition, planes: List<Plane>, store: SpotterStore, go: (Screen) -> Unit, close: () -> Unit) {
    val ac = p.ac
    val route = rememberRoute(ac)
    val details = rememberDetails(ac)
    val lib = TypeMap.libraryId(ac, details)?.let { id -> planes.firstOrNull { it.id == id } }
    val title = listOfNotNull(route?.airline, route?.flightNumber ?: ac.callsign).joinToString(" ").ifBlank { ac.registration ?: "Aircraft" }

    Scaffold(topBar = { BackBar(title, close) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (lib != null) item {
                Box {
                    PlanePhoto(lib, Modifier.fillMaxWidth().aspectRatio(16f / 9f), targetWidth = 900)
                    Surface(color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.BottomStart)) {
                        Text("Library photo of a ${lib.name}, not this exact aircraft", color = Color.White,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(lib?.name ?: details?.type ?: ac.typeLabel ?: "Unknown aircraft type", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lib != null) {
                            if (!store.spotted.containsKey(lib.id)) Button(onClick = { store.toggle(lib.id) }) { Text("👀 Add to my log") }
                            else OutlinedButton(onClick = {}) { Text("✅ In your log") }
                            OutlinedButton(onClick = { go(Screen.Detail(lib.id)) }) { Text("📖 All about it") }
                        }
                    }
                }
            }
            if (route?.origin != null && route.destination != null) item { TripCard(ac, route) }
            item {
                SectionCard("Right now", "📡") {
                    if (ac.onGround) SpecRow("Status", "On the ground")
                    ac.altitudeM?.let { SpecRow("Height", "${metres(it)} (${"%,d".format(feet(it))} ft)") }
                    ac.groundSpeedKmh?.let { SpecRow("Speed", kmh(it)) }
                    ac.trackDeg?.let { SpecRow("Heading", Geo.compass(it).replaceFirstChar { c -> c.uppercase() }) }
                    ac.verticalRateMpm?.takeIf { !ac.onGround }?.let {
                        SpecRow("Climbing or descending", when {
                            it > 60 -> "Climbing ${it.roundToInt()} m a minute"
                            it < -60 -> "Descending ${(-it).roundToInt()} m a minute"
                            else -> "Flying level"
                        })
                    }
                    if (p.distanceM != null && p.bearingDeg != null) SpecRow("Distance from you", "${(p.distanceM / 1000).oneDp()} km to the ${Geo.compass(p.bearingDeg)}")
                    if (!ac.onGround && p.elevationDeg != null) SpecRow("Where to look", "${p.elevationDeg.roundToInt()}° above the horizon")
                }
            }
            ac.altitudeM?.takeIf { it > EUREKA_TOWER_M * 1.5 }?.let { alt ->
                item {
                    SectionCard("How high is that?", "🏙️", container = MaterialTheme.colorScheme.primaryContainer) {
                        Text("About ${(alt / EUREKA_TOWER_M).roundToInt()} Eureka Towers stacked on top of each other.")
                    }
                }
            }
            item {
                SectionCard("The aircraft", "✈️") {
                    SpecRow("Type", details?.type ?: ac.typeLabel)
                    SpecRow("Maker", details?.manufacturer)
                    SpecRow("Registration", ac.registration ?: details?.registration)
                    if ((ac.registration ?: "").startsWith("VH-")) Text("VH- means it is registered in Australia.", style = MaterialTheme.typography.bodySmall)
                    SpecRow("Owner", details?.owner)
                    SpecRow("Callsign", ac.callsign)
                    SpecRow("Transponder code", ac.hex.uppercase())
                }
            }
            item {
                Text("Live data from adsb.lol (Open Database Licence). Routes and aircraft details from adsbdb.com, which are usually right but can be out of date.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TripCard(ac: LiveAircraft, route: FlightRoute) {
    val o = route.origin!!
    val d = route.destination!!
    val (lat, lon) = ac.extrapolated(System.currentTimeMillis())
    val flown = Geo.distanceM(o.lat, o.lon, lat, lon) / 1000
    val left = Geo.distanceM(lat, lon, d.lat, d.lon) / 1000
    val total = Geo.distanceM(o.lat, o.lon, d.lat, d.lon) / 1000
    val plausible = flown + left < total * 1.3 + 50
    SectionCard("The trip", "🗺️", container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(o.iata ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(o.short, style = MaterialTheme.typography.bodySmall)
            }
            Text("✈️", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(d.iata ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(d.short, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("From ${o.name}${o.country?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        Text("To ${d.name}${d.country?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (plausible && !ac.onGround) {
            LinearProgressIndicator(progress = { (flown / (flown + left)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("${flown.grouped()} km flown, about ${left.grouped()} km to go.")
            val gs = ac.groundSpeedKmh
            if (gs != null && gs > 150 && left > 5) {
                val mins = (left / gs * 60).roundToInt()
                Text(if (mins >= 90) "Roughly ${mins / 60} hours ${mins % 60} minutes until it gets there." else "Roughly $mins minutes until it gets there.",
                    fontWeight = FontWeight.SemiBold)
            }
        } else if (!plausible) {
            Text("This plane isn't where we'd expect for that route, so the route information may be out of date.", style = MaterialTheme.typography.bodySmall)
        }
        Text("The whole trip is about ${total.grouped()} km.", style = MaterialTheme.typography.bodySmall)
    }
}
