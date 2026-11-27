@file:OptIn(ExperimentalMaterial3Api::class)

package de.tino.links

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val USER = "tinoknopp-rgb"
// Updates der Link-Zentrale liegen im öffentlichen Update-Repo mit Kennzeichen „link-v…“
const val UPDATE_REPO = "$USER/HandyPutz-Updates"

data class Link(val name: String, val url: String, val info: String = "", val own: Boolean = false)

// ======================= GitHub (ohne Anmeldung, nur öffentliche Repos) =======================

object Gh {
    private fun get(url: String, follow: Boolean = true): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.instanceFollowRedirects = follow
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Link-Zentrale")
        return try {
            val code = c.responseCode
            code to ((if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
        } finally { c.disconnect() }
    }

    /** Alle öffentlichen Repos mit fertigen Dateien (APK, ZIP, EXE …) – Links zeigen immer auf die neueste Version */
    fun downloads(): List<Link> {
        val (code, txt) = get("https://api.github.com/users/$USER/repos?per_page=100&sort=pushed")
        if (code !in 200..299) throw Exception("GitHub nicht erreichbar ($code)")
        val repos = JSONArray(txt)
        val out = mutableListOf<Link>()
        for (i in 0 until repos.length()) {
            val r = repos.getJSONObject(i)
            if (r.optBoolean("private")) continue
            val full = r.getString("full_name")
            val (rc, rt) = get("https://api.github.com/repos/$full/releases/latest")
            if (rc !in 200..299) continue
            val rel = JSONObject(rt)
            val assets = rel.optJSONArray("assets") ?: continue
            for (k in 0 until assets.length()) {
                val a = assets.getJSONObject(k)
                val name = a.getString("name")
                if (!name.matches(Regex(".*\\.(apk|zip|exe|msi|pdf)$", RegexOption.IGNORE_CASE))) continue
                if (name.startsWith("Link-Zentrale")) continue
                val label = name.substringBeforeLast('.')
                out += Link(
                    "${icon(name)} $label", "https://github.com/$full/releases/latest/download/$name",
                    "${rel.optString("name", rel.optString("tag_name"))} • ${size(a.optLong("size"))}"
                )
            }
            if (assets.length() > 0) out += Link("📄 ${r.getString("name")} – Infoseite", r.getString("html_url"), "Anleitung & alle Versionen")
        }
        return out
    }

    private fun icon(n: String) = when (n.substringAfterLast('.').lowercase()) { "apk" -> "📱"; "exe", "msi" -> "💻"; "pdf" -> "📄"; else -> "📦" }
    private fun size(b: Long) = if (b > 1048576) "%.1f MB".format(b / 1048576.0) else "${b / 1024} KB"

    data class Update(val code: Int, val name: String, val url: String)

    /** Neuere Version dieser App? (Releases mit Tag „link-v1.N“) */
    fun selfUpdate(current: Long): Update? {
        val (code, txt) = get("https://api.github.com/repos/$UPDATE_REPO/releases?per_page=30")
        if (code !in 200..299) return null
        val all = JSONArray(txt)
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            val tag = o.optString("tag_name")
            if (!tag.startsWith("link-v")) continue
            val vc = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (vc <= current) return null
            val assets = o.optJSONArray("assets") ?: continue
            for (k in 0 until assets.length()) {
                val a = assets.getJSONObject(k)
                if (a.getString("name").endsWith(".apk")) return Update(vc, o.optString("name"), a.getString("browser_download_url"))
            }
        }
        return null
    }

    fun download(ctx: Context, url: String): File {
        val f = File(ctx.cacheDir, "update.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Link-Zentrale")
        try { c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } } } finally { c.disconnect() }
        return f
    }
}

// ======================= Speicher =======================

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("links", Context.MODE_PRIVATE)
    private fun toJson(l: List<Link>) = JSONArray().apply { l.forEach { put(JSONObject().put("n", it.name).put("u", it.url).put("i", it.info)) } }.toString()
    private fun fromJson(s: String, own: Boolean) = try {
        val a = JSONArray(s); (0 until a.length()).map { val o = a.getJSONObject(it); Link(o.getString("n"), o.getString("u"), o.optString("i"), own) }
    } catch (_: Exception) { emptyList() }

    fun cache(c: Context) = fromJson(sp(c).getString("cache", "[]") ?: "[]", false)
    fun setCache(c: Context, l: List<Link>) = sp(c).edit().putString("cache", toJson(l)).apply()
    fun own(c: Context) = fromJson(sp(c).getString("own", "[]") ?: "[]", true)
    fun setOwn(c: Context, l: List<Link>) = sp(c).edit().putString("own", toJson(l)).apply()
}

// ======================= Aktionen =======================

fun toast(c: Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_SHORT).show()

fun copy(c: Context, text: String) {
    c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", text))
    toast(c, "✅ Kopiert – jetzt einfügen und senden")
}

fun share(c: Context, text: String) =
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Link senden"))

fun open(c: Context, url: String) = try {
    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
} catch (_: Exception) { toast(c, "Kein Browser gefunden") }

fun versionCode(c: Context): Long = try {
    val p = c.packageManager.getPackageInfo(c.packageName, 0)
    if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
} catch (_: Exception) { 0 }

// ======================= Oberfläche =======================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8FD3FF), secondary = Color(0xFFFF5C8A), background = Color(0xFF0A2540), surface = Color(0xFF11325A))) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0A2540)) { Screen() }
            }
        }
    }
}

private val GITHUB = listOf(
    Link("👤 Mein Konto", "https://github.com/$USER"),
    Link("📚 Alle Repos", "https://github.com/$USER?tab=repositories"),
    Link("⚙️ Builds", "https://github.com/$USER/HandyPutz/actions"),
    Link("📦 Versionen", "https://github.com/$USER/HandyPutz-Updates/releases"),
)

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var links by remember { mutableStateOf(Store.cache(ctx)) }
    var own by remember { mutableStateOf(Store.own(ctx)) }
    var status by remember { mutableStateOf("Aktualisiere …") }
    var update by remember { mutableStateOf<Gh.Update?>(null) }
    var add by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    if (tools) { ToolsScreen { tools = false }; return }

    fun refresh() {
        scope.launch {
            status = "Aktualisiere …"
            try {
                val l = withContext(Dispatchers.IO) { Gh.downloads() }
                links = l; Store.setCache(ctx, l); status = "✅ Aktuell"
            } catch (e: Exception) { status = "Offline – letzter Stand" }
            update = try { withContext(Dispatchers.IO) { Gh.selfUpdate(versionCode(ctx)) } } catch (_: Exception) { null }
        }
    }
    LaunchedEffect(Unit) { refresh() }   // bei jedem Öffnen: Links + App-Update prüfen

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Link-Zentrale", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = Color(0xFFA9C3E0), fontSize = 13.sp)
                }
                TextButton(onClick = { refresh() }) { Text("↻", fontSize = 26.sp, color = Color(0xFF8FD3FF)) }
            }
            update?.let { u ->
                Card(Modifier.fillMaxWidth().padding(vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5C8A))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⬆ Neue Version ${u.name}", color = Color.White, fontWeight = FontWeight.Bold)
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                                toast(ctx, "Bitte „Unbekannte Apps installieren“ erlauben")
                                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
                            } else scope.launch {
                                status = "Lade Update …"
                                try {
                                    val f = withContext(Dispatchers.IO) { Gh.download(ctx, u.url) }
                                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                } catch (e: Exception) { status = "Update fehlgeschlagen: ${e.message}" }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0A2540))) { Text("Jetzt aktualisieren") }
                    }
                }
            }
            Text("GitHub", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            GITHUB.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { g ->
                        Box(
                            Modifier.weight(1f).heightIn(min = 60.dp).background(Color(0xFF11325A), RoundedCornerShape(16.dp)).clickable { open(ctx, g.url) },
                            contentAlignment = Alignment.Center
                        ) { Text(g.name, color = Color.White, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            Button(
                onClick = { tools = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FD3FF), contentColor = Color(0xFF0A2540))
            ) { Text("🛠 Werkzeuge: Token • Repos • Ordner • Secrets", fontWeight = FontWeight.Bold) }
            Text("Download-Links", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Button(
                onClick = { copy(ctx, (links + own).joinToString("\n\n") { it.name.substringAfter(' ') + ":\n" + it.url }) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C8A), contentColor = Color.White)
            ) { Text("📋 Alle Links auf einmal kopieren", fontWeight = FontWeight.Bold) }
        }
        items(links + own, key = { it.url + it.own }) { l -> LinkCard(l) { own = own - l; Store.setOwn(ctx, own) } }
        item {
            OutlinedButton(onClick = { add = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("➕ Eigenen Link hinzufügen", color = Color(0xFF8FD3FF)) }
            Text("Neue Apps in öffentlichen Repos mit Release erscheinen hier automatisch.", color = Color(0xFFA9C3E0), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (add) {
        var n by remember { mutableStateOf("") }
        var u by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("Eigener Link") },
            text = {
                Column {
                    OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(u, { u = it }, label = { Text("https://…") }, singleLine = true, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = n.isNotBlank() && u.startsWith("http"), onClick = {
                    own = own + Link("🔗 " + n.trim(), u.trim(), own = true); Store.setOwn(ctx, own); add = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
fun LinkCard(l: Link, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val msg = l.name.substringAfter(' ') + ": " + l.url
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11325A))) {
        Column(Modifier.padding(14.dp)) {
            Text(l.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (l.info.isNotBlank()) Text(l.info, color = Color(0xFFA9C3E0), fontSize = 13.sp)
            Text(l.url, color = Color(0xFFA9C3E0), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copy(ctx, l.url) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FD3FF), contentColor = Color(0xFF0A2540))) { Text("📋 Kopieren") }
                Button(onClick = { share(ctx, msg) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color(0xFF06301A))) { Text("📤 Senden") }
            }
            Row {
                TextButton(onClick = { open(ctx, l.url) }) { Text("Öffnen", color = Color(0xFF8FD3FF)) }
                if (l.own) TextButton(onClick = onDelete) { Text("🗑 Löschen", color = Color(0xFFFF5C8A)) }
            }
        }
    }
}
