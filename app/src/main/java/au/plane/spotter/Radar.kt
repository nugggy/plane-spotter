package au.plane.spotter

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

val RADAR_RANGES_KM = listOf(10, 25, 50, 100)

private val RadarGreen = Color(0xFF69F0AE)
private val LowColour = Color(0xFFFFEB3B)
private val MidColour = Color(0xFFFFA726)
private val HighColour = Color(0xFF4FC3F7)

private val radarChip @Composable get() = FilterChipDefaults.filterChipColors(labelColor = Color.White)

private fun altColour(altM: Double?) = when {
    altM == null -> Color.Gray
    altM < 3000 -> LowColour
    altM < 9000 -> MidColour
    else -> HighColour
}

/** Which way the top of the phone (or the camera, if held upright) is facing, in degrees from true north. */
fun phoneHeadingDeg(r: FloatArray, declination: Float): Float {
    val topH = hypot(r[1], r[4])
    val camH = hypot(r[2], r[5])
    val h = if (topH > camH * 0.5f) Math.atan2(r[1].toDouble(), r[4].toDouble()) else Math.atan2(-r[2].toDouble(), -r[5].toDouble())
    return ((Math.toDegrees(h) + declination + 360) % 360).toFloat()
}

private data class Blip(val p: SkyPosition, val x: Float, val y: Float)

@Composable
fun RadarView(
    positions: List<SkyPosition>,
    headingDeg: Float?,
    headingUp: Boolean,
    rangeKm: Int,
    onRange: (Int) -> Unit,
    onHeadingUp: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "sweepAngle",
    )
    val rot = if (headingUp && headingDeg != null) headingDeg else 0f
    val density = LocalDensity.current
    val inRange = positions.filter { (it.groundM ?: Double.MAX_VALUE) <= rangeKm * 1000.0 }

    Column(Modifier.fillMaxSize().background(Color(0xFF07170F)).verticalScroll(rememberScrollState())) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp)) {
            val sizePx = with(density) { maxWidth.toPx() }
            val c = sizePx / 2
            val radius = c * 0.92f
            val blips = inRange.mapNotNull { p ->
                val g = p.groundM ?: return@mapNotNull null
                val b = p.bearingDeg ?: return@mapNotNull null
                val a = Math.toRadians((b - rot).toDouble())
                val r = (g / (rangeKm * 1000.0)).toFloat() * radius
                Blip(p, c + r * sin(a).toFloat(), c - r * cos(a).toFloat())
            }
            val labelPaint = remember(density) {
                Paint().apply {
                    isAntiAlias = true; color = android.graphics.Color.WHITE
                    textSize = with(density) { 11.dp.toPx() }; typeface = Typeface.DEFAULT_BOLD
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }
            }
            val compassPaint = remember(density) {
                Paint().apply {
                    isAntiAlias = true; color = android.graphics.Color.rgb(105, 240, 174)
                    textSize = with(density) { 15.dp.toPx() }; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
                }
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(blips) {
                    detectTapGestures { tap ->
                        blips.minByOrNull { hypot((it.x - tap.x).toDouble(), (it.y - tap.y).toDouble()) }
                            ?.takeIf { hypot((it.x - tap.x).toDouble(), (it.y - tap.y).toDouble()) < 48.dp.toPx() }
                            ?.let { onSelect(it.p.ac.hex) }
                    }
                },
            ) {
                val centre = Offset(c, c)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF0E3B24), Color(0xFF041009)), centre, radius), radius, centre)
                for (i in 1..4) drawCircle(RadarGreen.copy(alpha = 0.35f), radius * i / 4, centre, style = Stroke(2f))
                drawLine(RadarGreen.copy(alpha = 0.25f), Offset(c - radius, c), Offset(c + radius, c), 2f)
                drawLine(RadarGreen.copy(alpha = 0.25f), Offset(c, c - radius), Offset(c, c + radius), 2f)
                // Sweep with a fading tail
                for (i in 0 until 24) {
                    drawArc(
                        RadarGreen.copy(alpha = 0.22f * (1 - i / 24f)), startAngle = sweep - 90 - i * 2.5f, sweepAngle = 2.6f,
                        useCenter = true, topLeft = Offset(c - radius, c - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    )
                }
                // Compass letters
                listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (t, deg) ->
                    val a = Math.toRadians((deg - rot).toDouble())
                    val rr = radius - 18.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(t, c + rr * sin(a).toFloat(), c - rr * cos(a).toFloat() + 6.dp.toPx(), compassPaint)
                }
                // Range ring labels
                for (i in 1..4) {
                    val km = rangeKm * i / 4.0
                    drawContext.canvas.nativeCanvas.drawText(
                        if (km < 10) "%.1f km".format(km) else "${km.roundToInt()} km",
                        c + 4.dp.toPx(), c - radius * i / 4 + 13.dp.toPx(), labelPaint.apply { alpha = 150 },
                    )
                }
                labelPaint.alpha = 255
                // You
                drawCircle(Color.White, 6.dp.toPx(), centre)
                // Aircraft
                blips.forEach { b ->
                    val col = altColour(b.p.ac.altitudeM)
                    if (b.p.ac.onGround) {
                        drawCircle(Color.Gray, 3.dp.toPx(), Offset(b.x, b.y))
                    } else {
                        val track = (b.p.ac.trackDeg ?: 0.0).toFloat() - rot
                        rotate(track, Offset(b.x, b.y)) {
                            val s = 9.dp.toPx()
                            val path = Path().apply {
                                moveTo(b.x, b.y - s); lineTo(b.x + s * 0.7f, b.y + s * 0.8f)
                                lineTo(b.x, b.y + s * 0.4f); lineTo(b.x - s * 0.7f, b.y + s * 0.8f); close()
                            }
                            drawPath(path, col)
                        }
                        val label = b.p.ac.callsign ?: b.p.ac.registration
                        if (label != null) drawContext.canvas.nativeCanvas.drawText(label, b.x + 11.dp.toPx(), b.y + 4.dp.toPx(), labelPaint)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Range", color = Color.White, style = MaterialTheme.typography.labelLarge)
                RADAR_RANGES_KM.forEach { km ->
                    FilterChip(selected = rangeKm == km, onClick = { onRange(km) }, label = { Text("$km km") }, colors = radarChip)
                }
            }
            if (headingDeg != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = headingUp, onClick = { onHeadingUp(true) }, label = { Text("Turns with me") }, colors = radarChip)
                    FilterChip(selected = !headingUp, onClick = { onHeadingUp(false) }, label = { Text("North up") }, colors = radarChip)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(LowColour, "Under 3 km up")
                Legend(MidColour, "3 to 9 km")
                Legend(HighColour, "Over 9 km")
            }
            Spacer(Modifier.height(8.dp))
            val flying = inRange.count { !it.ac.onGround }
            Text(
                if (flying == 0) "No planes flying within $rangeKm km right now. Try a bigger range."
                else "$flying flying within $rangeKm km. Tap a plane to see where it's going.",
                color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            inRange.firstOrNull { !it.ac.onGround }?.let { n ->
                Text(
                    "Closest: ${n.ac.callsign ?: n.ac.registration ?: "unknown"}, ${((n.distanceM ?: 0.0) / 1000).oneDp()} km to the ${Geo.compass(n.bearingDeg ?: 0.0)}",
                    color = RadarGreen, style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun remember(key: Any, calc: () -> Paint): Paint = androidx.compose.runtime.remember(key) { calc() }

@Composable
private fun Legend(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c, CircleShape))
        Text(" $t", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
