package au.plane.spotter

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------- Spotter's log ----------

@Composable
fun LogScreen(planes: List<Plane>, store: SpotterStore, go: (Screen) -> Unit, back: () -> Unit) {
    val spotted = planes.filter { store.spotted.containsKey(it.id) }
    val local = planes.filter { it.localSpotting }
    val localSpotted = local.count { store.spotted.containsKey(it.id) }
    val badge = when {
        spotted.size >= 50 -> "🏆 Ace spotter"
        spotted.size >= 25 -> "🥇 Senior spotter"
        spotted.size >= 10 -> "🥈 Keen spotter"
        spotted.size >= 1 -> "🥉 Rookie spotter"
        else -> "Tap \"I spotted it\" on any aircraft page to start your log"
    }

    Scaffold(topBar = { BackBar("Spotter's log", back) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SectionCard("Your progress", "📓") {
                    Text(badge, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Melbourne regulars: $localSpotted of ${local.size}")
                    LinearProgressIndicator(progress = { localSpotted / local.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Text("Everything: ${spotted.size} of ${planes.size}")
                    LinearProgressIndicator(progress = { spotted.size / planes.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
            }
            item {
                SectionCard("By type", "📊") {
                    Category.entries.forEach { c ->
                        val all = planes.count { it.category == c }
                        val got = spotted.count { it.category == c }
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${c.emoji} ${c.label}", Modifier.weight(1f))
                            Text("$got / $all", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (spotted.isNotEmpty()) {
                item { Text("Spotted so far", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(spotted.sortedByDescending { store.spotted[it.id]?.split('/')?.reversed()?.joinToString("") }, key = { it.id }) { p ->
                    PlaneRow(p, true) { go(Screen.Detail(p.id)) }
                }
            }
            val todo = local.filter { !store.spotted.containsKey(it.id) }
            if (todo.isNotEmpty()) {
                item { Text("Still to spot around Melbourne", Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(todo, key = { "todo-" + it.id }) { p -> PlaneRow(p, false) { go(Screen.Detail(p.id)) } }
            }
        }
    }
}

// ---------- Melbourne spotting guide ----------

private val guide = listOf(
    Triple("🛬", "Melbourne Airport (Tullamarine)",
        "Victoria's biggest airport and the place to see the most variety. Domestic jets from Qantas, Virgin Australia and Jetstar come and go all day, and international airlines bring wide-bodies such as the Airbus A380, A350, Boeing 777 and 787. It has two runways that cross each other, and which one is in use depends on the wind. Plane spotters often watch from public roads around the airport boundary, such as Sharps Road. Always stay outside the fence, park legally and keep well away from traffic."),
    Triple("🎪", "Avalon Airport and the Avalon Airshow",
        "Avalon, near Geelong, has Jetstar flights and is home to the Australian International Airshow, held every second year (odd-numbered years). The show has RAAF fast jets, the Roulettes, visiting military aircraft from other countries, aerobatic displays and huge transport aircraft you can walk around. Check the airshow's website for the next dates."),
    Triple("💼", "Essendon Fields",
        "Melbourne's first major airport, opened in 1921. Today it is busy with business jets, air ambulances and charter flights, and it still has historic buildings from the early days of flying."),
    Triple("🎓", "Moorabbin Airport",
        "One of the busiest training airports in Australia. Watch student pilots practise take-offs and landings in Cessnas, Pipers and Diamonds. The Australian National Aviation Museum is on the airport and has historic aircraft to see up close."),
    Triple("🏛️", "RAAF Museum, Point Cook",
        "Point Cook is where the Royal Australian Air Force began, and it is often described as the oldest continuously operating military airfield in the world. The museum has a big collection of RAAF aircraft from the First World War to the jet age, and some historic aircraft still fly there."),
    Triple("🔴", "RAAF Base East Sale",
        "In Gippsland, home of the RAAF's pilot training and the Roulettes aerobatic team, who fly the Pilatus PC-21."),
)

private val tips = listOf(
    "Australian aircraft registrations start with VH-, for example VH-OQA was the first Qantas A380. Writing down registrations is how real spotters keep track.",
    "Airline codes help on flight boards: QF is Qantas, VA is Virgin Australia, JQ is Jetstar, EK is Emirates, SQ is Singapore Airlines.",
    "Count the engines and look at the wingtips and nose. The \"How to recognise it\" section on each aircraft page tells you what to look for.",
    "Aircraft take off and land into the wind, so a windy day can change which runway is used and where the best view is.",
    "Flight-tracking apps and websites show what is about to arrive. Ask a parent before installing one.",
    "Bring binoculars, a hat, sunscreen and water. Never fly drones near an airport and never shine lights at aircraft.",
)

@Composable
fun GuideScreen(back: () -> Unit) {
    Scaffold(topBar = { BackBar("Spotting guide", back) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(guide) { (emoji, title, body) -> SectionCard(title, emoji) { Text(body) } }
            item {
                SectionCard("Spotter tips", "🔭", container = MaterialTheme.colorScheme.primaryContainer) {
                    tips.forEach { Text("• $it", Modifier.padding(vertical = 3.dp)) }
                }
            }
            item {
                Text(
                    "Opening hours, events and viewing areas change, so check with an adult before you go.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- About and credits ----------

@Composable
fun AboutScreen(planes: List<Plane>, updates: UpdateState, checkNow: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { BackBar("About and photo credits", back) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                SectionCard("Plane Spotter ${BuildConfig.VERSION_NAME}", "✈️") {
                    Text("Made for a young aviation fan in Melbourne. The aircraft guide works offline. The app goes online to check for new versions and, when you open the sky scanner, to find live aircraft near you. Only a rounded position (about 1 km) is sent for that, and nothing is stored.")
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = checkNow) { Text("Check for updates") }
                    updates.lastCheckMessage?.let { Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                SectionCard("About the facts", "📚") {
                    Text("Specifications come mainly from English Wikipedia, checked in October 2026, and are rounded. Different versions of the same aircraft can have quite different numbers, so each page says which version the figures are for.")
                }
            }
            item {
                Text("Photo credits", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("All photos are from Wikimedia Commons. Tap a credit to see the original photo and its licence.",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
            items(planes.filter { it.photo != null }.sortedBy { it.name }, key = { it.id }) { p ->
                val ph = p.photo!!
                Column(
                    Modifier.fillMaxWidth().clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ph.page))) }
                    }.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(p.name, style = MaterialTheme.typography.labelLarge)
                    Text("${ph.artist.ifBlank { "Unknown" }} · ${ph.license}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
