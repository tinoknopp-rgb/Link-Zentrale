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

data class Link(val name: String, val url: String, val info: String = "", val own: Boolean = false, val project: String = "") {
    /** Beschreibung / Tester-Text aus link-zentrale.json – keine eigene Karte */
    val isMeta get() = url.startsWith("desc:") || url.startsWith("tester:")
}

/** Projektname aus dem Dateinamen: „HandyPutz-1.4.apk“ → „HandyPutz“ */
fun projectOf(file: String) = file.substringBeforeLast('.').replace(Regex("[-_ ]?v?\\d+(\\.\\d+)*$"), "")

/** Fertige Nachricht für Testpersonen */
fun testMsg(project: String, links: List<Link>): String {
    val files = links.filter { !it.name.contains("Infoseite") && !it.isMeta }
    val sb = StringBuilder("🧪 Hallo! Bitte teste meine App „$project“.\n\n")
    files.forEach { sb.append(it.name.substringAfter(' ')).append(":\n").append(it.url).append("\n\n") }
    if (files.any { it.url.endsWith(".apk") }) sb.append("📱 Android: Link öffnen → Datei laden → öffnen → installieren (falls gefragt: „Unbekannte Apps installieren“ erlauben).\n")
    if (files.any { it.url.endsWith(".exe") }) sb.append("💻 Windows: Datei laden → Doppelklick → bei der blauen Warnung „Weitere Informationen“ → „Trotzdem ausführen“.\n")
    links.firstOrNull { it.name.contains("Infoseite") }?.let { sb.append("ℹ️ Infos & Anleitung: ").append(it.url).append("\n") }
    links.firstOrNull { it.url.startsWith("tester:") }?.let { sb.append("\n").append(it.name).append("\n") }
    sb.append("\nUpdates kommen automatisch. Danke für deine Rückmeldung! 🙏")
    return tr(sb.toString())
}

// ======================= GitHub (ohne Anmeldung, nur öffentliche Repos) =======================

object Gh {
    private fun get(url: String, follow: Boolean = true): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.instanceFollowRedirects = follow
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Link-Zentrale")
        if (Api.token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer ${Api.token}")
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
                    "${rel.optString("name", rel.optString("tag_name"))} • ${size(a.optLong("size"))}", project = projectOf(name)
                )
            }
            if (assets.length() > 0) {
                val rn = r.getString("name")
                val proj = out.map { it.project }.distinct().firstOrNull { rn.startsWith(it) } ?: rn
                out += Link("📄 $rn – Infoseite", r.getString("html_url"), "Anleitung & alle Versionen", project = proj)
                out += extras(full, proj)
            }
        }
        // Link-Zentrale selbst (fester Link, zeigt immer auf die neueste Version)
        val (lc, lt) = get("https://api.github.com/repos/$UPDATE_REPO/releases/tags/link-zentrale")
        if (lc in 200..299) {
            val a = JSONObject(lt).optJSONArray("assets") ?: JSONArray()
            for (k in 0 until a.length()) {
                val n = a.getJSONObject(k).getString("name")
                out += Link("${icon(n)} $n", "https://github.com/$UPDATE_REPO/releases/download/link-zentrale/$n",
                    (if (n.endsWith(".exe")) "Windows" else "Android") + " • ${size(a.getJSONObject(k).optLong("size"))}", project = "Link-Zentrale")
            }
        }
        return out
    }

    /**
     * Projekt-eigene Erweiterung: Datei „link-zentrale.json“ im Update-Repo des Projekts.
     * { "beschreibung": "…", "tester_text": "…", "links": [ {"name":"📘 Handbuch","url":"https://…","info":"…"} ] }
     */
    private fun extras(full: String, proj: String): List<Link> = try {
        val (c, t) = get("https://raw.githubusercontent.com/$full/HEAD/link-zentrale.json")
        if (c !in 200..299) emptyList() else {
            val o = JSONObject(t)
            buildList {
                o.optString("beschreibung").takeIf { it.isNotBlank() }?.let { add(Link(it, "desc:$proj", project = proj)) }
                o.optString("tester_text").takeIf { it.isNotBlank() }?.let { add(Link(it, "tester:$proj", project = proj)) }
                val a = o.optJSONArray("links") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val l = a.getJSONObject(i)
                    if (l.optString("url").startsWith("http")) add(Link(l.optString("name", "🔗 Link"), l.getString("url"), l.optString("info"), project = proj))
                }
            }
        }
    } catch (_: Exception) { emptyList() }

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
    private fun toJson(l: List<Link>) = JSONArray().apply { l.forEach { put(JSONObject().put("n", it.name).put("u", it.url).put("i", it.info).put("p", it.project)) } }.toString()
    private fun fromJson(s: String, own: Boolean) = try {
        val a = JSONArray(s); (0 until a.length()).map { val o = a.getJSONObject(it); Link(o.getString("n"), o.getString("u"), o.optString("i"), own, o.optString("p")) }
    } catch (_: Exception) { emptyList() }

    fun cache(c: Context) = fromJson(sp(c).getString("cache", "[]") ?: "[]", false)
    fun setCache(c: Context, l: List<Link>) = sp(c).edit().putString("cache", toJson(l)).apply()
    fun own(c: Context) = fromJson(sp(c).getString("own", "[]") ?: "[]", true)
    fun setOwn(c: Context, l: List<Link>) = sp(c).edit().putString("own", toJson(l)).apply()
}

// ======================= Aktionen =======================

fun toast(c: Context, m: String) = Toast.makeText(c, tr(m), Toast.LENGTH_SHORT).show()

fun copy(c: Context, text: String) {
    c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", text))
    toast(c, "✅ Kopiert – jetzt einfügen und senden")
}

fun share(c: Context, text: String) =
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("Link senden")))

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
        if (Guard.starting(this)) { startActivity(Intent(this, RescueActivity::class.java)); finish(); return }
        handle(intent)
        if (Build.VERSION.SDK_INT >= 33 && !Notify.allowed(this))
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8FD3FF), secondary = Color(0xFFFF5C8A), background = Color(0xFF0A2540), surface = Color(0xFF11325A))) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0A2540)) { Screen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        i?.getStringExtra("reviews")?.let { Nav.reviews = it }
        if (i?.getBooleanExtra("pick", false) == true) Nav.pick = true
    }
}

/** Sprung aus Benachrichtigung/Widget */
object Nav {
    var reviews by mutableStateOf<String?>(null)
    var pick by mutableStateOf(false)
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
    var reviews by remember { mutableStateOf<Pair<String, String>?>(null) }   // Projekt, Repo
    var infos by remember { mutableStateOf(Stats.load(ctx).associateBy { it.project }) }
    var pick by remember { mutableStateOf(false) }
    var qrFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var feedback by remember { mutableStateOf(false) }
    if (Api.token.isBlank()) Api.token = toolSp(ctx).getString("token", "") ?: ""
    fun countBadges() {
        scope.launch {
            val l = withContext(Dispatchers.IO) { try { Stats.all(ctx) } catch (_: Exception) { null } }
            if (l != null) { infos = l.associateBy { it.project }; LinkWidget.updateAll(ctx) }
        }
    }
    // Sprung aus Benachrichtigung / Widget
    LaunchedEffect(Nav.reviews, Nav.pick, links) {
        Nav.reviews?.let { p -> links.filter { it.project == p }.let(::repoOf)?.let { reviews = p to it; Nav.reviews = null } }
        if (Nav.pick) { pick = true; Nav.pick = false }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000); Guard.ok(ctx)
        withContext(Dispatchers.IO) { try { Guard.sendCrash(ctx) } catch (_: Exception) { } }
    }
    if (tools) { ToolsScreen { tools = false }; return }
    reviews?.let { (p, r) -> ReviewsScreen(p, r) { reviews = null; countBadges() }; return }

    fun refresh() {
        scope.launch {
            status = "Aktualisiere …"
            try {
                val l = withContext(Dispatchers.IO) { Gh.downloads() }
                links = l; Store.setCache(ctx, l); status = "✅ Aktuell"; countBadges()
            } catch (e: Exception) { status = "Offline – letzter Stand" }
            update = try { withContext(Dispatchers.IO) { Gh.selfUpdate(versionCode(ctx)) } } catch (_: Exception) { null }
        }
    }
    LaunchedEffect(Unit) { refresh() }   // bei jedem Öffnen: Links + App-Update prüfen

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Link-Zentrale", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        val total = infos.values.sumOf { it.unread }
                        if (total > 0) Badge(containerColor = Color(0xFFFF5C8A), modifier = Modifier.padding(start = 8.dp)) { Text("$total neu", color = Color.White) }
                    }
                    Text("Version 1.${versionCode(ctx)} • $status", color = Color(0xFFA9C3E0), fontSize = 13.sp)
                }
                TextButton(onClick = { L.set(ctx, !L.en) }, contentPadding = PaddingValues(4.dp)) { androidx.compose.material3.Text(if (L.en) "🇬🇧" else "🇩🇪", fontSize = 22.sp) }
                TextButton(onClick = { feedback = true }, contentPadding = PaddingValues(4.dp)) { Text("⭐", fontSize = 22.sp) }
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
            val totalNew = infos.values.sumOf { it.unread }
            Button(
                onClick = { pick = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (totalNew > 0) Color(0xFFFF5C8A) else Color(0xFF1B4A80), contentColor = Color.White)
            ) { Text(if (totalNew > 0) "💬 Bewertungen – $totalNew neu" else "💬 Bewertungen lesen & beantworten", fontWeight = FontWeight.Bold) }
            Button(
                onClick = { tools = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FD3FF), contentColor = Color(0xFF0A2540))
            ) { Text("🛠 Werkzeuge: Token • Repos • Ordner • Secrets", fontWeight = FontWeight.Bold) }
            Text("Download-Links", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Button(
                onClick = { copy(ctx, (links + own).filter { !it.isMeta }.joinToString("\n\n") { it.name.substringAfter(' ') + ":\n" + it.url }) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C8A), contentColor = Color.White)
            ) { Text("📋 Alle Links auf einmal kopieren", fontWeight = FontWeight.Bold) }
        }
        val groups = (links + own).groupBy { if (it.own) "Eigene Links" else it.project.ifBlank { "Sonstiges" } }
        groups.forEach { (proj, ls) ->
            item(key = "h_$proj") {
                Card(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B4A80))) {
                    Column(Modifier.padding(14.dp)) {
                        val repoP = repoOf(ls)
                        val inf = infos[proj]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📦 $proj", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (repoP != null && proj != "Eigene Links") BadgedBox(badge = {
                                val n = inf?.unread ?: 0
                                if (n > 0) Badge(containerColor = Color(0xFFFF5C8A)) { Text("$n", color = Color.White) }
                            }) {
                                OutlinedButton(onClick = { reviews = proj to repoP }) { Text("💬 Bewertungen", color = Color.White) }
                            }
                        }
                        ls.firstOrNull { it.url.startsWith("desc:") }?.let { Text(it.name, color = Color(0xFFA9C3E0), fontSize = 14.sp) }
                        if (inf != null) {
                            val parts = buildList {
                                if (inf.build.isNotBlank()) add(inf.build + " Build")
                                add("⬇ ${inf.downloads} Downloads")
                                if (inf.ratings > 0) add("★ %.1f (${inf.ratings})".format(inf.avg))
                                if (inf.bugs > 0) add("🐞 ${inf.bugs} offene Fehler")
                            }
                            Text(parts.joinToString("  •  "), color = Color.White, fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp).clickable(enabled = inf.buildUrl.isNotBlank()) { open(ctx, inf.buildUrl) })
                        }
                        if (proj != "Eigene Links") {
                            Text("Test-Link zum Weitergeben – fertige Nachricht mit Anleitung", color = Color(0xFFA9C3E0), fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { share(ctx, testMsg(proj, ls)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color(0xFF06301A))) { Text("📤 An Tester senden", fontWeight = FontWeight.Bold) }
                                Button(onClick = { copy(ctx, testMsg(proj, ls)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FD3FF), contentColor = Color(0xFF0A2540))) { Text("📋 Nachricht kopieren") }
                            }
                            val main = ls.firstOrNull { it.url.endsWith(".apk") } ?: ls.firstOrNull { !it.name.contains("Infoseite") }
                            if (main != null) OutlinedButton(onClick = { qrFor = proj to main.url }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text("🔳 QR-Code", color = Color.White)
                            }
                        }
                    }
                }
            }
            items(ls.filter { !it.isMeta }, key = { it.url + it.own }) { l -> LinkCard(l) { own = own - l; Store.setOwn(ctx, own) } }
        }
        item {
            OutlinedButton(onClick = { add = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("➕ Eigenen Link hinzufügen", color = Color(0xFF8FD3FF)) }
            Text("Neue Apps in öffentlichen Repos mit Release erscheinen hier automatisch.", color = Color(0xFFA9C3E0), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (pick) {
        val projs = links.filter { it.project.isNotBlank() }.groupBy { it.project }.mapNotNull { (p, ls) -> repoOf(ls)?.let { p to it } }
        AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text("💬 Bewertungen") },
            text = {
                Column {
                    if (projs.isEmpty()) Text("Noch keine Projekte geladen – oben ↻ antippen.")
                    projs.forEach { (p, r) ->
                        val n = infos[p]?.unread ?: 0
                        OutlinedButton(onClick = { pick = false; reviews = p to r }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("📦 $p", modifier = Modifier.weight(1f))
                            if (n > 0) Badge(containerColor = Color(0xFFFF5C8A)) { Text("$n neu", color = Color.White) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("Schließen") } }
        )
    }

    qrFor?.let { (p, u) -> QrDialog(p, u) { qrFor = null } }
    if (feedback) FeedbackDialog { feedback = false }

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
