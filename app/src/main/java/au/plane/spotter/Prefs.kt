package au.plane.spotter

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The spotter's log and quiz best score, kept on the device. */
class SpotterStore(context: Context) {
    private val prefs = context.getSharedPreferences("spotter", Context.MODE_PRIVATE)

    /** Plane id to the date it was first spotted (dd/MM/yyyy). */
    val spotted = mutableStateMapOf<String, String>()
    val quizBest = mutableIntStateOf(prefs.getInt("quizBest", 0))

    init {
        val json = JSONObject(prefs.getString("spotted", "{}") ?: "{}")
        json.keys().forEach { spotted[it] = json.getString(it) }
    }

    fun toggle(id: String) {
        if (spotted.containsKey(id)) spotted.remove(id)
        else spotted[id] = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        prefs.edit().putString("spotted", JSONObject(spotted.toMap()).toString()).apply()
    }

    fun recordQuiz(score: Int) {
        if (score > quizBest.intValue) {
            quizBest.intValue = score
            prefs.edit().putInt("quizBest", score).apply()
        }
    }
}
