package au.plane.spotter

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val nf: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "AU"))

fun Double.grouped(): String = nf.format(this.roundToInt())

fun Double.oneDp(): String =
    if (this >= 100) grouped() else String.format(Locale("en", "AU"), "%.1f", this).removeSuffix(".0")

fun metres(v: Double?) = v?.let { "${it.oneDp()} m" }
fun kmh(v: Double?) = v?.let { "${it.grouped()} km/h" }
fun km(v: Double?) = v?.let { "${it.grouped()} km" }

fun weight(kg: Double?) = kg?.let {
    if (it >= 10_000) "${(it / 1000).oneDp()} tonnes" else "${it.grouped()} kg"
}

/** Turns "1997-07-31" into "31 July 1997", leaving plain years alone. */
fun prettyDate(s: String?): String? {
    if (s == null) return null
    return try {
        LocalDate.parse(s).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("en", "AU")))
    } catch (e: Exception) {
        s
    }
}

// Everyday Melbourne things to make big numbers feel real.
const val TRAM_LENGTH_M = 33.0      // E-class tram
const val FREEWAY_KMH = 100.0       // typical Victorian freeway limit
const val MEL_SYD_KM = 713.0        // Melbourne to Sydney, great-circle distance

fun lengthComparison(p: Plane): String? = p.lengthM?.let {
    val trams = it / TRAM_LENGTH_M
    when {
        trams >= 1.15 -> "About ${String.format(Locale("en", "AU"), "%.1f", trams)} Melbourne E-class trams long."
        trams >= 0.85 -> "About as long as a Melbourne E-class tram."
        else -> "About ${((it / TRAM_LENGTH_M) * 100).roundToInt()}% of the length of a Melbourne E-class tram."
    }
}

fun speedComparison(p: Plane): String? = p.bestSpeedKmh?.let {
    val times = it / FREEWAY_KMH
    if (times < 1.5) null
    else "About ${String.format(Locale("en", "AU"), "%.0f", times)} times faster than a car on the freeway."
}

fun rangeComparison(p: Plane): String? = p.rangeKm?.let {
    val trips = it / MEL_SYD_KM
    when {
        trips >= 2 -> "Far enough for ${trips.toInt()} Melbourne to Sydney flights in a row without refuelling."
        trips >= 1 -> "Far enough to fly from Melbourne to Sydney without refuelling."
        else -> null
    }
}
