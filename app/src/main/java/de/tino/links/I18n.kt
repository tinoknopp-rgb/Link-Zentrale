package de.tino.links

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import org.json.JSONObject

/** Deutsch/Englisch: Texte werden über assets/en.json übersetzt (exakter Treffer, sonst Teil-Phrasen) */
object L {
    var en by mutableStateOf(false)
    private var map: Map<String, String> = emptyMap()
    private var phrases: List<Pair<String, String>> = emptyList()

    fun init(c: Context) {
        en = c.getSharedPreferences("links", Context.MODE_PRIVATE).getBoolean("en", false)
        try {
            val o = JSONObject(c.assets.open("en.json").bufferedReader().use { it.readText() })
            map = o.keys().asSequence().associateWith { o.getString(it) }
            phrases = map.filterKeys { it.length >= 7 }.toList().sortedByDescending { it.first.length }
        } catch (_: Exception) { }
    }

    fun set(c: Context, english: Boolean) {
        en = english
        c.getSharedPreferences("links", Context.MODE_PRIVATE).edit().putBoolean("en", english).apply()
    }

    fun tr(s: String): String {
        if (!en || s.isEmpty()) return s
        map[s]?.let { return it }
        var r = s
        for ((de, enT) in phrases) if (r.contains(de)) r = r.replace(de, enT)
        return r
    }
}

fun tr(s: String) = L.tr(s)

/** Ersetzt material3.Text im ganzen Paket – übersetzt automatisch */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
) = androidx.compose.material3.Text(tr(text), modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines)
