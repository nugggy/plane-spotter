package au.plane.spotter

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

// ---------- Home ----------

private data class Tile(val emoji: String, val title: String, val subtitle: String, val screen: Screen)

@Composable
fun HomeScreen(planes: List<Plane>, store: SpotterStore, updates: UpdateState, go: (Screen) -> Unit) {
    val local = planes.count { it.localSpotting }
    val today = remember { planes[(LocalDate.now().toEpochDay() % planes.size).toInt()] }
    val tiles = buildList {
        add(Tile("📍", "Spot in Melbourne", "$local you can see around Victoria", Screen.Browse(localOnly = true)))
        add(Tile("🌏", "Every aircraft", "All ${planes.size} in the hangar", Screen.Browse()))
        Category.entries.forEach { c ->
            add(Tile(c.emoji, c.label, "${planes.count { it.category == c }} aircraft", Screen.Browse(category = c)))
        }
        add(Tile("❓", "Plane quiz", "Best score: ${store.quizBest.intValue} / 10", Screen.Quiz))
        add(Tile("⚖️", "Compare", "Put two aircraft head to head", Screen.Compare()))
        add(Tile("📓", "Spotter's log", "${store.spotted.size} spotted so far", Screen.Log))
        add(Tile("🔭", "Spotting guide", "Best places around Melbourne", Screen.Guide))
        add(Tile("ℹ️", "About and photo credits", "Version ${BuildConfig.VERSION_NAME}", Screen.About))
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(DeepSky, Sky)))
                    .statusBarsPadding()
                    .padding(20.dp)
            ) {
                Text("Plane Spotter", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${planes.size} aircraft · ${store.spotted.size} spotted", color = Color.White.copy(alpha = 0.85f))
                if (updates.available != null && !updates.showDialog) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { updates.showDialog = true }) { Text("Update to ${updates.available?.version}") }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { go(Screen.Detail(today.id)) },
                elevation = CardDefaults.cardElevation(3.dp),
            ) {
                Box {
                    PlanePhoto(today, Modifier.fillMaxWidth().aspectRatio(16f / 9f), targetWidth = 900)
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                            .padding(14.dp)
                    ) {
                        Text("AIRCRAFT OF THE DAY", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(today.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        today.role?.let { Text(it, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        items(tiles) { t ->
            Card(
                Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(116.dp).clickable { go(t.screen) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(t.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

// ---------- Browse ----------

enum class SortBy(val label: String) { NAME("A to Z"), BIGGEST("Biggest"), FASTEST("Fastest"), FURTHEST("Longest range"), OLDEST("Oldest"), NEWEST("Newest") }

@Composable
fun BrowseScreen(planes: List<Plane>, store: SpotterStore, start: Screen.Browse, go: (Screen) -> Unit, back: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf(start.category) }
    var localOnly by rememberSaveable { mutableStateOf(start.localOnly) }
    var sort by rememberSaveable { mutableStateOf(SortBy.NAME) }

    val shown = remember(query, category, localOnly, sort) {
        val q = query.trim().lowercase()
        planes.filter { p ->
            (category == null || p.category == category) && (!localOnly || p.localSpotting) &&
                (q.isEmpty() || listOfNotNull(p.name, p.nickname, p.manufacturer, p.role, p.local?.operators)
                    .any { it.lowercase().contains(q) })
        }.let { list ->
            when (sort) {
                SortBy.NAME -> list.sortedBy { it.name }
                SortBy.BIGGEST -> list.sortedByDescending { it.mtowKg ?: 0.0 }
                SortBy.FASTEST -> list.sortedByDescending { it.bestSpeedKmh ?: 0.0 }
                SortBy.FURTHEST -> list.sortedByDescending { it.rangeKm ?: 0.0 }
                SortBy.OLDEST -> list.sortedBy { it.firstFlightYear ?: 9999 }
                SortBy.NEWEST -> list.sortedByDescending { it.firstFlightYear ?: 0 }
            }
        }
    }
    val title = when {
        localOnly -> "Spot in Melbourne"
        category != null -> category!!.label
        else -> "Every aircraft"
    }

    Scaffold(topBar = { BackBar(title, back) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
                    placeholder = { Text("Search, e.g. Qantas, Boeing, jumbo") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear search") } },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = localOnly, onClick = { localOnly = !localOnly }, label = { Text("📍 Melbourne") }) }
                    item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All types") }) }
                    items(Category.entries) { c ->
                        FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text("${c.emoji} ${c.label}") })
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Sort:", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelLarge) }
                    items(SortBy.entries) { s -> FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) }) }
                }
            }
            item {
                Text("${shown.size} aircraft", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown, key = { it.id }) { p -> PlaneRow(p, store.spotted.containsKey(p.id), sort) { go(Screen.Detail(p.id)) } }
            if (shown.isEmpty()) item {
                Text("No aircraft match that search.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PlaneRow(p: Plane, spotted: Boolean, sort: SortBy = SortBy.NAME, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanePhoto(p, Modifier.width(120.dp).height(84.dp), targetWidth = 320)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                p.role?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val extra = when (sort) {
                    SortBy.BIGGEST -> weight(p.mtowKg)?.let { "Max take-off weight $it" }
                    SortBy.FASTEST -> kmh(p.bestSpeedKmh)
                    SortBy.FURTHEST -> km(p.rangeKm)?.let { "Range $it" }
                    SortBy.OLDEST, SortBy.NEWEST -> p.firstFlightYear?.let { "First flew $it" }
                    SortBy.NAME -> null
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (extra != null) Text(extra, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    else {
                        Text(p.category.emoji, style = MaterialTheme.typography.labelMedium)
                        if (p.localSpotting) Text("📍 Melbourne", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (spotted) Icon(Icons.Default.CheckCircle, "Spotted", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

// ---------- Detail ----------

@Composable
fun DetailScreen(p: Plane, planes: List<Plane>, store: SpotterStore, go: (Screen) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val spotted = store.spotted[p.id]
    val similar = remember(p.id) { planes.filter { it.category == p.category && it.id != p.id }.shuffled().take(8) }

    Scaffold(topBar = { BackBar(p.name, back) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                PlanePhoto(p, Modifier.fillMaxWidth().aspectRatio(3f / 2f), targetWidth = 1024)
                p.photo?.let { ph ->
                    Text(
                        "Photo: ${ph.artist.ifBlank { "Unknown" }} · ${ph.license} · Wikimedia Commons",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ph.page))) }
                        },
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    p.nickname?.let { Text("Nickname: $it", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary) }
                    p.role?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("${p.category.emoji} ${p.category.label}")
                        p.status?.let { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (spotted == null) {
                            Button(onClick = { store.toggle(p.id) }) { Text("👀 I spotted it") }
                        } else {
                            OutlinedButton(onClick = { store.toggle(p.id) }) { Text("✅ Spotted $spotted") }
                        }
                        OutlinedButton(onClick = { go(Screen.Compare(p.id)) }) { Text("⚖️ Compare") }
                    }
                }
            }
            p.local?.let { local ->
                item {
                    SectionCard(
                        if (p.localSpotting) "Where to spot it around Melbourne" else "Australian connection",
                        if (p.localSpotting) "📍" else "🇦🇺",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(local.where)
                        local.operators?.let { Spacer(Modifier.height(6.dp)); Text("Look for: $it", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            item { SectionCard("About it", "📖") { Text(p.summary) } }
            val compares = listOfNotNull(lengthComparison(p), speedComparison(p), rangeComparison(p))
            if (compares.isNotEmpty()) item {
                SectionCard("How big? How fast?", "📏", container = MaterialTheme.colorScheme.primaryContainer) {
                    compares.forEach { Text("• $it", Modifier.padding(vertical = 2.dp)) }
                }
            }
            item {
                SectionCard("Specifications", "🔧") {
                    p.specsVariant?.let { Text("Figures for the $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    SpecRow("Maker", p.manufacturer)
                    SpecRow("Country", p.country)
                    SpecRow("First flight", prettyDate(p.firstFlight))
                    SpecRow("Number built", p.numberBuilt)
                    SpecRow("Length", metres(p.lengthM))
                    SpecRow("Wingspan", metres(p.wingspanM))
                    SpecRow("Height", metres(p.heightM))
                    SpecRow("Max take-off weight", weight(p.mtowKg))
                    SpecRow("Cruising speed", kmh(p.cruiseKmh))
                    SpecRow("Top speed", kmh(p.maxKmh))
                    SpecRow("Range", km(p.rangeKm))
                    SpecRow("Highest altitude", metres(p.ceilingM))
                    SpecRow("Engines", p.engines)
                    SpecRow("Crew", p.crew)
                    SpecRow("Passengers", p.passengers)
                }
            }
            if (p.funFacts.isNotEmpty()) item {
                SectionCard("Fun facts", "💡") {
                    p.funFacts.forEach { Text("• $it", Modifier.padding(vertical = 3.dp)) }
                }
            }
            p.spotterTips?.let { item { SectionCard("How to recognise it", "🔍") { Text(it) } } }
            if (similar.isNotEmpty()) {
                item {
                    Text("More ${p.category.label.lowercase()}", Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(similar, key = { it.id }) { s ->
                            Column(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).clickable { go(Screen.Detail(s.id)) }) {
                                PlanePhoto(s, Modifier.width(150.dp).height(100.dp).clip(RoundedCornerShape(12.dp)), targetWidth = 320)
                                Text(s.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().size(8.dp)) }
        }
    }
}
