package au.plane.spotter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live aircraft near the phone, from the community ADS-B network at adsb.lol (ODbL licence),
 * with flight routes and aircraft details from adsbdb.com. Neither needs an account.
 *
 * Privacy: only a position rounded to two decimal places (roughly 1 km) is sent to adsb.lol.
 * The exact position stays on the phone and is used locally to work out where to look.
 */
data class LiveAircraft(
    val hex: String,
    val callsign: String?,
    val registration: String?,
    val icaoType: String?,
    val category: String?,
    val lat: Double,
    val lon: Double,
    /** Metres above sea level, null when on the ground. */
    val altitudeM: Double?,
    val onGround: Boolean,
    val groundSpeedKmh: Double?,
    val trackDeg: Double?,
    val verticalRateMpm: Double?,
    /** Seconds since the position was received, at fetch time. */
    val ageS: Double,
    val fetchedAtMs: Long,
) {
    /** Position moved forward along its track to "now", so markers glide between updates. */
    fun extrapolated(nowMs: Long): Pair<Double, Double> {
        val gs = groundSpeedKmh ?: return lat to lon
        val tr = trackDeg ?: return lat to lon
        if (onGround) return lat to lon
        val dt = ((nowMs - fetchedAtMs) / 1000.0 + ageS).coerceAtMost(30.0)
        val distM = gs / 3.6 * dt
        return Geo.destination(lat, lon, tr, distM)
    }
}

data class Airport(val name: String, val city: String?, val iata: String?, val country: String?, val lat: Double, val lon: Double) {
    val short get() = city ?: name
}

data class FlightRoute(val airline: String?, val flightNumber: String?, val origin: Airport?, val destination: Airport?)

data class AircraftDetails(val type: String?, val manufacturer: String?, val owner: String?, val registration: String?)

object LiveApi {
    private const val UA = "PlaneSpotter (github.com/nugggy/plane-spotter)"
    private val routes = ConcurrentHashMap<String, Result<FlightRoute?>>()
    private val details = ConcurrentHashMap<String, Result<AircraftDetails?>>()

    private fun get(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", UA)
        return try {
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Aircraft within [radiusNm] nautical miles of the rounded position. Throws on network failure. */
    suspend fun nearby(lat: Double, lon: Double, radiusNm: Int = 40): List<LiveAircraft> = withContext(Dispatchers.IO) {
        val rl = String.format(Locale.US, "%.2f", lat)
        val rn = String.format(Locale.US, "%.2f", lon)
        val json = get("https://api.adsb.lol/v2/point/$rl/$rn/$radiusNm") ?: error("No data")
        val now = System.currentTimeMillis()
        val arr = json.optJSONArray("ac") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            if (!o.has("lat") || !o.has("lon")) return@mapNotNull null
            val baro = o.opt("alt_baro")
            val onGround = baro == "ground"
            val altFt = when {
                onGround -> null
                o.has("alt_geom") -> o.optDouble("alt_geom")
                baro is Number -> baro.toDouble()
                else -> null
            }
            LiveAircraft(
                hex = o.getString("hex").lowercase(),
                callsign = o.optString("flight").trim().takeIf { cs -> cs.isNotEmpty() && cs.all { it.isLetterOrDigit() } },
                registration = o.optString("r").ifBlank { null },
                icaoType = o.optString("t").ifBlank { null },
                category = o.optString("category").ifBlank { null },
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                altitudeM = altFt?.takeIf { !it.isNaN() }?.times(0.3048),
                onGround = onGround,
                groundSpeedKmh = o.optDouble("gs").takeIf { !it.isNaN() }?.times(1.852),
                trackDeg = o.optDouble("track").takeIf { !it.isNaN() } ?: o.optDouble("true_heading").takeIf { !it.isNaN() },
                verticalRateMpm = (o.optDouble("geom_rate").takeIf { !it.isNaN() } ?: o.optDouble("baro_rate").takeIf { !it.isNaN() })?.times(0.3048),
                ageS = o.optDouble("seen_pos", 0.0),
                fetchedAtMs = now,
            )
        }
    }

    private fun airport(o: JSONObject?): Airport? = o?.let {
        Airport(
            name = it.optString("name"),
            city = it.optString("municipality").ifBlank { null },
            iata = it.optString("iata_code").ifBlank { null },
            country = it.optString("country_name").ifBlank { null },
            lat = it.optDouble("latitude"),
            lon = it.optDouble("longitude"),
        )
    }

    suspend fun route(callsign: String): FlightRoute? = withContext(Dispatchers.IO) {
        routes[callsign]?.let { return@withContext it.getOrNull() }
        val r = runCatching {
            val fr = get("https://api.adsbdb.com/v0/callsign/$callsign")?.optJSONObject("response")?.optJSONObject("flightroute")
            fr?.let {
                FlightRoute(
                    airline = it.optJSONObject("airline")?.optString("name")?.ifBlank { null },
                    flightNumber = it.optString("callsign_iata").ifBlank { null } ?: it.optString("callsign").ifBlank { null },
                    origin = airport(it.optJSONObject("origin")),
                    destination = airport(it.optJSONObject("destination")),
                )
            }
        }
        if (r.isSuccess) routes[callsign] = r
        r.getOrNull()
    }

    suspend fun aircraft(hex: String): AircraftDetails? = withContext(Dispatchers.IO) {
        details[hex]?.let { return@withContext it.getOrNull() }
        val r = runCatching {
            get("https://api.adsbdb.com/v0/aircraft/$hex")?.optJSONObject("response")?.optJSONObject("aircraft")?.let {
                AircraftDetails(
                    type = it.optString("type").ifBlank { null },
                    manufacturer = it.optString("manufacturer").ifBlank { null },
                    owner = it.optString("registered_owner").ifBlank { null },
                    registration = it.optString("registration").ifBlank { null },
                )
            }
        }
        if (r.isSuccess) details[hex] = r
        r.getOrNull()
    }
}

object Geo {
    private const val R = 6_371_000.0

    private fun rad(d: Double) = Math.toRadians(d)

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = rad(lat2 - lat1)
        val dLon = rad(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(rad(lon2 - lon1)) * cos(rad(lat2))
        val x = cos(rad(lat1)) * sin(rad(lat2)) - sin(rad(lat1)) * cos(rad(lat2)) * cos(rad(lon2 - lon1))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun destination(lat: Double, lon: Double, bearing: Double, distM: Double): Pair<Double, Double> {
        val d = distM / R
        val b = rad(bearing)
        val la1 = rad(lat)
        val la2 = Math.asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
        val lo2 = rad(lon) + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
        return Math.toDegrees(la2) to Math.toDegrees(lo2)
    }

    /** Angle above the horizon, allowing for the curve of the Earth. */
    fun elevationDeg(groundDistM: Double, heightDiffM: Double): Double {
        val drop = groundDistM * groundDistM / (2 * R)
        return Math.toDegrees(atan2(heightDiffM - drop, groundDistM))
    }

    fun compass(deg: Double): String =
        listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")[((deg + 22.5) / 45).toInt() % 8]
}

/** Maps ICAO aircraft type designators from ADS-B to aircraft in the app's library. */
object TypeMap {
    private val map: Map<String, String> = buildMap {
        fun add(id: String, vararg codes: String) = codes.forEach { put(it, id) }
        add("b737-800", "B736", "B737", "B738", "B739")
        add("b737-max", "B37M", "B38M", "B39M", "B3XM")
        add("a320", "A318", "A319", "A320", "A321", "A19N", "A20N")
        add("a321neo", "A21N")
        add("a330", "A332", "A333")
        add("a330neo", "A338", "A339")
        add("b787", "B788", "B789", "B78X")
        add("a350", "A359", "A35K")
        add("a380", "A388")
        add("b777", "B772", "B773", "B77W", "B77L")
        add("b777x", "B778", "B779")
        add("b747", "B741", "B742", "B743", "B744", "B748", "B74S", "B74R")
        add("c919", "C919")
        add("a220", "BCS1", "BCS3")
        add("e190", "E170", "E75L", "E75S", "E190", "E195")
        add("e195e2", "E290", "E295")
        add("f100", "F100", "F70")
        add("dash8", "DH8A", "DH8B", "DH8C", "DH8D")
        add("saab340", "SF34")
        add("atr72", "AT43", "AT45", "AT46", "AT72", "AT73", "AT75", "AT76")
        add("twinotter", "DHC6")
        add("belugaxl", "A337")
        add("dreamlifter", "BLCF")
        add("an124", "A124")
        add("an225", "A225")
        add("f35", "F35")
        add("superhornet", "F18S")
        add("hornet", "F18H")
        add("growler", "E18G")
        add("c17", "C17")
        add("c130j", "C30J", "C130")
        add("p8", "P8")
        add("e7", "E737")
        add("pc21", "PC21")
        add("hawk", "HAWK")
        add("f22", "F22"); add("b2", "B2"); add("b52", "B52"); add("a10", "A10"); add("f16", "F16")
        add("typhoon", "EUFI"); add("rafale", "RFAL"); add("gripen", "JAS39"); add("su57", "SU57")
        add("c5", "C5", "C5M"); add("a400m", "A400"); add("v22", "V22"); add("f14", "F14"); add("u2", "U2")
        add("tigermoth", "DH82"); add("dc3", "DC3"); add("spitfire", "SPIT"); add("mustang", "P51")
        add("catalina", "CAT"); add("b17", "B17"); add("lancaster", "LANC"); add("constellation", "CONI")
        add("concorde", "CONC"); add("b707", "B701", "B703"); add("comet", "COMT"); add("cub", "J3")
        add("c172", "C172"); add("pa28", "P28A", "P28B", "P28R", "P28T"); add("sr22", "SR20", "SR22", "S22T")
        add("da40", "DA40"); add("pc12", "PC12"); add("kingair", "BE20", "BE30", "BE9L", "BE9T", "B350")
        add("caravan", "C208"); add("beaver", "DHC2"); add("g650", "GLF6"); add("global7500", "GL7T")
        add("citation", "C25A", "C25B", "C25C", "C525"); add("extra300", "E300", "E330"); add("at802", "AT8T")
        add("cl415", "CL2T")
        add("a340", "A342", "A343", "A345", "A346"); add("b767", "B762", "B763", "B764"); add("b757", "B752", "B753")
        add("b717", "B712"); add("crj900", "CRJ2", "CRJ7", "CRJ9", "CRJX"); add("bae146", "B461", "B462", "B463", "RJ70", "RJ85", "RJ1H")
        add("metro", "SW3", "SW4"); add("beech1900", "B190"); add("md11", "MD11"); add("a300", "A306", "A30B", "A310")
        add("il76", "IL76"); add("belugast", "A3ST"); add("mq4c", "Q4"); add("c27j", "C27J"); add("f15", "F15")
        add("f4", "F4"); add("mig29", "MG29"); add("f117", "F117"); add("b1", "B1"); add("kc46", "KC46"); add("e3", "E3TF", "E3CF")
        add("tornado", "TOR"); add("f111", "F111"); add("sabre", "F86"); add("caribou", "DHC4"); add("pc9", "PC9")
        add("tu144", "T144"); add("dc10", "DC10"); add("l1011", "L101"); add("b727", "B721", "B722")
        add("nomad", "N22", "N24"); add("airvan", "GA8"); add("pc24", "PC24"); add("c152", "C152"); add("c182", "C182", "T182")
        add("p2008", "P208"); add("da42", "DA42"); add("bonanza", "BE33", "BE35", "BE36"); add("phenom300", "E55P")
    }

    fun libraryId(ac: LiveAircraft, details: AircraftDetails?): String? {
        val code = ac.icaoType?.uppercase() ?: return null
        val typeText = (details?.type ?: "").uppercase()
        val owner = (details?.owner ?: "").uppercase()
        if (code == "B77L" && (typeText.contains("F") || owner.contains("CARGO"))) return "b777f"
        if (code == "A332" && owner.contains("AIR FORCE")) return "kc30"
        return map[code]
    }
}

fun feet(m: Double) = (m / 0.3048).roundToInt()

/** Short type label, calling out helicopters (ADS-B emitter category A7). */
val LiveAircraft.typeLabel: String? get() = when {
    category == "A7" -> "Helicopter" + (icaoType?.let { " ($it)" } ?: "")
    else -> icaoType
}
