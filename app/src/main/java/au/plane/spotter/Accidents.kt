package au.plane.spotter

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** A notable aviation accident, shown on the live map and linked from aircraft pages. */
data class Accident(
    val id: String,
    /** accident, deliberate (an attack) or wartime */
    val kind: String,
    val title: String,
    val date: String,
    val lat: Double,
    val lon: Double,
    val approximate: Boolean,
    val place: String,
    val region: String,
    val aircraft: String?,
    val libraryId: String?,
    val operator: String?,
    val flight: String?,
    val onBoard: Int?,
    val fatalities: Int?,
    val survivors: Int?,
    val groundFatalities: Int?,
    val outcome: String,
    val summary: String,
    val cause: String?,
    val lesson: String?,
    val wikipedia: String?,
) {
    val year: String get() = date.take(4)
    val everyoneSurvived: Boolean get() = outcome == "everyone survived"
    val disappeared: Boolean get() = outcome == "disappeared"
}

object AccidentRepo {
    @Volatile private var cache: List<Accident>? = null

    fun all(context: Context): List<Accident> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun JSONObject.str(k: String) = if (isNull(k) || !has(k)) null else optString(k).ifBlank { null }
    private fun JSONObject.int(k: String) = if (isNull(k) || !has(k)) null else optInt(k)

    private fun load(context: Context): List<Accident> = runCatching {
        val arr = JSONArray(context.assets.open("accidents.json").bufferedReader().use { it.readText() })
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Accident(
                id = o.getString("id"), kind = o.optString("kind").ifBlank { "accident" }, title = o.getString("title"), date = o.optString("date"),
                lat = o.getDouble("lat"), lon = o.getDouble("lon"), approximate = o.optBoolean("approximate"),
                place = o.optString("place"), region = o.optString("region"), aircraft = o.str("aircraft"),
                libraryId = o.str("libraryId"), operator = o.str("operator"), flight = o.str("flight"),
                onBoard = o.int("onBoard"), fatalities = o.int("fatalities"), survivors = o.int("survivors"),
                groundFatalities = o.int("groundFatalities"), outcome = o.optString("outcome"),
                summary = o.optString("summary"), cause = o.str("cause"), lesson = o.str("lesson"), wikipedia = o.str("wikipedia"),
            )
        }.sortedBy { it.date }
    }.getOrDefault(emptyList())
}

@Composable
fun OutcomePill(a: Accident) {
    when {
        a.outcome == "nobody on board" -> Pill("Nobody on board", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        a.everyoneSurvived -> Pill("Everyone survived", Color(0xFFC8E6C9), Color(0xFF1B5E20))
        a.outcome == "some survived" -> Pill("Some survived", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        a.disappeared -> Pill("Disappeared", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Pill("Fatal", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun KindPill(a: Accident) {
    when (a.kind) {
        "deliberate" -> Pill("Deliberate attack", Color(0xFFFFE0B2), Color(0xFF4E2600))
        "wartime" -> Pill("Wartime", Color(0xFFD7CCC8), Color(0xFF3E2723))
        else -> Pill("Accident", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun AccidentScreen(a: Accident, planes: List<Plane>, go: (Screen) -> Unit, back: () -> Unit) {
    val plane = a.libraryId?.let { id -> planes.firstOrNull { it.id == id } }
    Scaffold(topBar = { BackBar(a.title, back) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(a.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(prettyDate(a.date) ?: a.date, style = MaterialTheme.typography.titleMedium)
                    Text(a.place + if (a.approximate) " (approximate location)" else "", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { KindPill(a); OutcomePill(a) }
                }
            }
            item {
                SectionCard("The flight", "✈️") {
                    SpecRow("Aircraft", a.aircraft)
                    SpecRow("Airline or operator", a.operator)
                    SpecRow("Flight", a.flight)
                    SpecRow("People on board", a.onBoard?.toString())
                    SpecRow("Survivors", a.survivors?.toString())
                    a.groundFatalities?.takeIf { it > 0 }?.let { SpecRow("People lost on the ground", it.toString()) }
                }
            }
            item { SectionCard("What happened", "📖") { Text(a.summary) } }
            a.cause?.let { item { SectionCard("Why it happened", "🔍") { Text(it) } } }
            a.lesson?.let {
                item {
                    SectionCard("What changed afterwards", "🛡️", container = Color(0xFFE8F5E9)) {
                        Text(it, color = Color(0xFF1B1B1B))
                    }
                }
            }
            plane?.let { p ->
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { go(Screen.Detail(p.id)) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlanePhoto(p, Modifier.width(110.dp).height(80.dp), targetWidth = 320)
                            Column(Modifier.padding(12.dp)) {
                                Text("About the ${p.name}", fontWeight = FontWeight.Bold)
                                Text("Open it in your library", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    when (a.kind) {
                        "deliberate" -> "Events like this are very rare. Afterwards, airports, airlines and governments around the world worked together on security so that flying is safe for everyone. The people on board are remembered by memorials and by their families and communities."
                        "wartime" -> "This happened during a war. It is remembered as part of history, and the people involved are honoured by memorials."
                        else -> "Flying is one of the safest ways to travel. Investigators study every serious accident, and what they learn is used to make aeroplanes, airports and training safer for everyone."
                    } +
                        (a.wikipedia?.let { "\n\nBased on the Wikipedia article \"$it\" and the official investigation." } ?: ""),
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Accidents involving this aircraft type, for the aircraft detail page. */
@Composable
fun AccidentLinks(accidents: List<Accident>, go: (Screen) -> Unit) {
    if (accidents.isEmpty()) return
    SectionCard("Famous crashes and incidents", "🛡️") {
        accidents.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { go(Screen.Accident(a.id)) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.title, fontWeight = FontWeight.SemiBold)
                    Text("${a.year} · ${a.place}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (a.everyoneSurvived) Text("Everyone survived", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
            }
        }
    }
}
