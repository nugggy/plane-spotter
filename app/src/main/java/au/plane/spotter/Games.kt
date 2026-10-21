package au.plane.spotter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// ---------- Quiz ----------

private const val ROUNDS = 10

sealed interface Question {
    val prompt: String
    val answerId: String
    val explain: String

    data class NameIt(val plane: Plane, val options: List<Plane>) : Question {
        override val prompt = "What aircraft is this?"
        override val answerId = plane.id
        override val explain = plane.funFacts.firstOrNull() ?: plane.summary
    }

    data class Pair(val a: Plane, val b: Plane, override val prompt: String, override val answerId: String,
                    override val explain: String) : Question
}

private fun makeQuestion(planes: List<Plane>, used: Set<String>): Question {
    val pool = planes.filter { it.photo != null && it.id !in used }.ifEmpty { planes }
    val kind = (0..9).random()
    if (kind < 6) {
        val p = pool.random()
        // Prefer distractors from the same category so it's a real challenge.
        val same = planes.filter { it.id != p.id && it.category == p.category }.shuffled()
        val other = planes.filter { it.id != p.id && it.category != p.category }.shuffled()
        val options = (same.take(2) + other).distinctBy { it.id }.take(3) + p
        return Question.NameIt(p, options.shuffled())
    }
    // Head-to-head question. Pick a pair with a clear difference.
    repeat(40) {
        val a = pool.random()
        val b = planes.random()
        if (a.id == b.id) return@repeat
        when (kind) {
            6, 7 -> {
                val sa = a.bestSpeedKmh; val sb = b.bestSpeedKmh
                if (sa != null && sb != null && abs(sa - sb) / maxOf(sa, sb) > 0.15) {
                    val win = if (sa > sb) a else b
                    return Question.Pair(a, b, "Which is faster?", win.id,
                        "${a.name}: ${kmh(sa)}. ${b.name}: ${kmh(sb)}.")
                }
            }
            8 -> {
                val wa = a.wingspanM; val wb = b.wingspanM
                if (wa != null && wb != null && abs(wa - wb) / maxOf(wa, wb) > 0.15) {
                    val win = if (wa > wb) a else b
                    return Question.Pair(a, b, "Which has the wider wingspan?", win.id,
                        "${a.name}: ${metres(wa)}. ${b.name}: ${metres(wb)}.")
                }
            }
            else -> {
                val ya = a.firstFlightYear; val yb = b.firstFlightYear
                if (ya != null && yb != null && abs(ya - yb) >= 5) {
                    val win = if (ya < yb) a else b
                    return Question.Pair(a, b, "Which flew first?", win.id,
                        "${a.name} first flew in $ya. ${b.name} first flew in $yb.")
                }
            }
        }
    }
    val p = pool.random()
    return Question.NameIt(p, (planes.filter { it.id != p.id }.shuffled().take(3) + p).shuffled())
}

@Composable
fun QuizScreen(planes: List<Plane>, store: SpotterStore, back: () -> Unit) {
    var round by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    val used = remember { mutableSetOf<String>() }
    var question by remember { mutableStateOf(makeQuestion(planes, used)) }
    var picked by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }

    fun next() {
        if (round >= ROUNDS) {
            finished = true
            store.recordQuiz(score)
            return
        }
        round++
        picked = null
        used += question.answerId
        question = makeQuestion(planes, used)
    }

    fun restart() {
        round = 1; score = 0; used.clear(); picked = null; finished = false
        question = makeQuestion(planes, used)
    }

    Scaffold(topBar = { BackBar("Plane quiz", back) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            if (finished) {
                Spacer(Modifier.height(40.dp))
                Text(if (score >= 8) "🏆" else if (score >= 5) "✈️" else "🛫", style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("You scored $score out of $ROUNDS", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Text("Best score: ${store.quizBest.intValue}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = ::restart, modifier = Modifier.fillMaxWidth()) { Text("Play again") }
                return@Column
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Question $round of $ROUNDS", style = MaterialTheme.typography.labelLarge)
                Text("Score: $score", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { round / ROUNDS.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            fun choose(id: String) {
                if (picked != null) return
                picked = id
                if (id == question.answerId) score++
            }

            fun colourFor(id: String): Color? = when {
                picked == null -> null
                id == question.answerId -> Color(0xFF2E7D32)
                id == picked -> Color(0xFFC62828)
                else -> null
            }

            when (val q = question) {
                is Question.NameIt -> {
                    PlanePhoto(q.plane, Modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(RoundedCornerShape(16.dp)), targetWidth = 900)
                    Spacer(Modifier.height(12.dp))
                    q.options.forEach { o ->
                        val c = colourFor(o.id)
                        OutlinedButton(
                            onClick = { choose(o.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            border = BorderStroke(if (c != null) 3.dp else 1.dp, c ?: MaterialTheme.colorScheme.outline),
                        ) { Text(o.name, Modifier.padding(vertical = 6.dp)) }
                    }
                }
                is Question.Pair -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(q.a, q.b).forEach { o ->
                            val c = colourFor(o.id)
                            Card(
                                Modifier.weight(1f).clickable { choose(o.id) },
                                border = BorderStroke(if (c != null) 3.dp else 1.dp, c ?: MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                PlanePhoto(o, Modifier.fillMaxWidth().aspectRatio(4f / 3f), targetWidth = 480)
                                Text(o.name, Modifier.padding(10.dp).heightIn(min = 44.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (picked != null) {
                Spacer(Modifier.height(12.dp))
                val right = picked == question.answerId
                Card(colors = CardDefaults.cardColors(containerColor = if (right) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (right) "Correct" else "Not quite", fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
                        Text(question.explain, color = Color(0xFF1B1B1B))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = ::next, modifier = Modifier.fillMaxWidth()) { Text(if (round >= ROUNDS) "See my score" else "Next question") }
            }
        }
    }
}

// ---------- Compare ----------

private data class Metric(val label: String, val value: (Plane) -> Double?, val format: (Double?) -> String?, val lowerWins: Boolean = false)

private val metrics = listOf(
    Metric("Length", { it.lengthM }, ::metres),
    Metric("Wingspan", { it.wingspanM }, ::metres),
    Metric("Height", { it.heightM }, ::metres),
    Metric("Max take-off weight", { it.mtowKg }, ::weight),
    Metric("Top or cruising speed", { it.bestSpeedKmh }, ::kmh),
    Metric("Range", { it.rangeKm }, ::km),
    Metric("Highest altitude", { it.ceilingM }, ::metres),
    Metric("First flight (earlier wins)", { it.firstFlightYear?.toDouble() }, { v -> v?.toInt()?.toString() }, lowerWins = true),
)

@Composable
fun CompareScreen(planes: List<Plane>, firstId: String?, go: (Screen) -> Unit, back: () -> Unit) {
    var a by remember { mutableStateOf(planes.firstOrNull { it.id == firstId } ?: planes.first { it.id == "a380" }) }
    var b by remember { mutableStateOf(planes.first { it.id == if (a.id == "b737-800") "a380" else "b737-800" }) }
    var picking by remember { mutableStateOf<Int?>(null) }

    Scaffold(topBar = { BackBar("Compare", back) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(a to 0, b to 1).forEach { (p, idx) ->
                    Card(Modifier.weight(1f)) {
                        PlanePhoto(p, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clickable { go(Screen.Detail(p.id)) }, targetWidth = 480)
                        Text(p.name, Modifier.padding(horizontal = 10.dp, vertical = 6.dp).heightIn(min = 40.dp),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { picking = idx }, modifier = Modifier.padding(start = 2.dp)) { Text("Change") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            metrics.forEach { m ->
                val va = m.value(a); val vb = m.value(b)
                if (va == null && vb == null) return@forEach
                val max = maxOf(va ?: 0.0, vb ?: 0.0).takeIf { it > 0 } ?: 1.0
                val aWins = va != null && vb != null && (if (m.lowerWins) va < vb else va > vb)
                val bWins = va != null && vb != null && (if (m.lowerWins) vb < va else vb > va)
                Text(m.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                CompareBar(a.name, m.format(va) ?: "Unknown", if (m.lowerWins) null else (va ?: 0.0) / max, aWins, MaterialTheme.colorScheme.primary)
                CompareBar(b.name, m.format(vb) ?: "Unknown", if (m.lowerWins) null else (vb ?: 0.0) / max, bWins, MaterialTheme.colorScheme.secondary)
            }
        }
    }

    picking?.let { idx ->
        PlanePicker(planes, onDismiss = { picking = null }) { chosen ->
            if (idx == 0) a = chosen else b = chosen
            picking = null
        }
    }
}

@Composable
private fun CompareBar(name: String, value: String, fraction: Double?, winner: Boolean, colour: Color) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
            Text((if (winner) "🏆 " else "") + value, style = MaterialTheme.typography.bodySmall,
                fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal)
        }
        if (fraction != null) {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(colour))
            }
        }
    }
}

@Composable
fun PlanePicker(planes: List<Plane>, onDismiss: () -> Unit, onPick: (Plane) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = planes.filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }.sortedBy { it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an aircraft") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 400.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(shown, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlanePhoto(p, Modifier.height(40.dp).aspectRatio(1.4f).clip(RoundedCornerShape(6.dp)), targetWidth = 160)
                            Text(p.name, Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
