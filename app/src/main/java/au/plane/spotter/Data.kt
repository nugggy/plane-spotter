package au.plane.spotter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Category(val key: String, val label: String, val emoji: String) {
    AIRLINER("airliner", "Airliners", "✈️"),
    REGIONAL("regional", "Regional", "🛫"),
    CARGO("cargo", "Cargo giants", "📦"),
    MILITARY("military", "Military", "🎖️"),
    HISTORIC("historic", "History makers", "🏛️"),
    GENERAL("general", "Small planes", "🛩️"),
    EXPERIMENTAL("experimental", "Record breakers", "🚀");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

data class Photo(val asset: String, val artist: String, val license: String, val page: String)

data class LocalInfo(val where: String, val operators: String?)

data class Plane(
    val id: String,
    val name: String,
    val category: Category,
    val nickname: String?,
    val manufacturer: String?,
    val country: String?,
    val role: String?,
    val firstFlight: String?,
    val status: String?,
    val numberBuilt: String?,
    val specsVariant: String?,
    val lengthM: Double?,
    val wingspanM: Double?,
    val heightM: Double?,
    val mtowKg: Double?,
    val cruiseKmh: Double?,
    val maxKmh: Double?,
    val rangeKm: Double?,
    val ceilingM: Double?,
    val engines: String?,
    val crew: String?,
    val passengers: String?,
    val summary: String,
    val funFacts: List<String>,
    val spotterTips: String?,
    val local: LocalInfo?,
    /** True when he is genuinely likely to see this one around Melbourne. */
    val localSpotting: Boolean,
    val photo: Photo?,
) {
    /** Best single speed figure for comparisons: top speed if known, otherwise cruise. */
    val bestSpeedKmh: Double? get() = maxKmh ?: cruiseKmh
    val firstFlightYear: Int? get() = firstFlight?.take(4)?.toIntOrNull()
}

object PlaneRepo {
    @Volatile private var cache: List<Plane>? = null

    fun all(context: Context): List<Plane> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<Plane> {
        val text = context.assets.open("planes.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.num(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() && it > 0 }

    private fun parse(o: JSONObject): Plane {
        val facts = o.optJSONArray("funFacts")
        val melb = o.optJSONObject("melbourne")
        val photo = o.optJSONObject("photo")
        return Plane(
            id = o.getString("id"),
            name = o.getString("name"),
            category = Category.of(o.getString("category")),
            nickname = o.str("nickname"),
            manufacturer = o.str("manufacturer"),
            country = o.str("country"),
            role = o.str("role"),
            firstFlight = o.str("firstFlight"),
            status = o.str("status"),
            numberBuilt = o.str("numberBuilt"),
            specsVariant = o.str("specsVariant"),
            lengthM = o.num("lengthM"),
            wingspanM = o.num("wingspanM"),
            heightM = o.num("heightM"),
            mtowKg = o.num("mtowKg"),
            cruiseKmh = o.num("cruiseKmh"),
            maxKmh = o.num("maxKmh"),
            rangeKm = o.num("rangeKm"),
            ceilingM = o.num("ceilingM"),
            engines = o.str("engines"),
            crew = o.str("crew"),
            passengers = o.str("passengers"),
            summary = o.optString("summary"),
            funFacts = if (facts == null) emptyList() else (0 until facts.length()).map { facts.getString(it) },
            spotterTips = o.str("spotterTips"),
            local = melb?.let { LocalInfo(it.optString("where"), it.str("operators")) },
            localSpotting = o.optBoolean("localSpotting"),
            photo = photo?.let {
                Photo(it.getString("asset"), it.optString("artist"), it.optString("license"), it.optString("page"))
            },
        )
    }
}
