@file:OptIn(ExperimentalMaterial3Api::class)

package de.tino.links

import android.content.Context
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ======================= GitHub mit Token (Repos, Ordner, Secrets) =======================

object Api {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    var token = ""

    class R(val code: Int, val body: String, val scopes: String)
    data class Repo(val full: String, val name: String, val private: Boolean, val branch: String, val html: String)

    fun call(method: String, path: String, body: Any? = null): R {
        val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
            ?: if (method in listOf("POST", "PUT", "PATCH")) "".toRequestBody() else null
        val req = Request.Builder().url("https://api.github.com$path").method(method, rb)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Link-Zentrale")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(req).execute().use { return R(it.code, it.body?.string() ?: "", it.header("X-OAuth-Scopes") ?: "") }
    }

    fun need(r: R, what: String): R {
        if (r.code in 200..299) return r
        val m = try { JSONObject(r.body).optString("message") } catch (_: Exception) { r.body.take(120) }
        throw Exception("$what fehlgeschlagen (${r.code}): $m" + when (r.code) {
            401 -> " – Token ungültig"
            403, 404 -> " – fehlt dem Token ein Haken (repo / workflow / delete_repo)?"
            else -> ""
        })
    }

    fun me(): String {
        val r = need(call("GET", "/user"), "Anmeldung")
        return "✅ Angemeldet als ${JSONObject(r.body).getString("login")} • Rechte: ${r.scopes.ifBlank { "–" }}"
    }

    fun repos(): List<Repo> {
        val a = JSONArray(need(call("GET", "/user/repos?per_page=100&sort=pushed&affiliation=owner"), "Repos laden").body)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Repo(o.getString("full_name"), o.getString("name"), o.optBoolean("private"), o.optString("default_branch", "main"), o.getString("html_url"))
        }
    }

    fun createRepo(name: String, desc: String, private: Boolean): String {
        val o = JSONObject(need(call("POST", "/user/repos", JSONObject()
            .put("name", name).put("description", desc).put("private", private).put("auto_init", true)), "Repo anlegen").body)
        return "✅ Repo angelegt: ${o.getString("html_url")}"
    }

    fun deleteRepo(full: String): String { need(call("DELETE", "/repos/$full"), "Repo löschen"); return "🗑 $full gelöscht" }

    fun folders(r: Repo): List<String> {
        val t = call("GET", "/repos/${r.full}/git/trees/${r.branch}?recursive=1")
        if (t.code !in 200..299) return emptyList()
        val a = JSONObject(t.body).getJSONArray("tree")
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.getString("type") == "tree" }.map { it.getString("path") }
    }

    fun createFolder(r: Repo, path: String): String {
        val p = path.trim().trim('/')
        need(call("PUT", "/repos/${r.full}/contents/$p/.gitkeep", JSONObject().put("message", "Ordner $p angelegt").put("content", "").put("branch", r.branch)), "Ordner anlegen")
        return "✅ Ordner „$p“ in ${r.name} angelegt"
    }

    /** Löscht alle Dateien unter dem Ordner in einem einzigen Commit */
    fun deleteFolder(r: Repo, path: String): String {
        val p = path.trim().trim('/')
        val head = JSONObject(need(call("GET", "/repos/${r.full}/git/ref/heads/${r.branch}"), "Stand lesen").body).getJSONObject("object").getString("sha")
        val tree = JSONObject(need(call("GET", "/repos/${r.full}/git/commits/$head"), "Commit lesen").body).getJSONObject("tree").getString("sha")
        val all = JSONObject(need(call("GET", "/repos/${r.full}/git/trees/$tree?recursive=1"), "Dateien lesen").body).getJSONArray("tree")
        val del = JSONArray()
        for (i in 0 until all.length()) {
            val e = all.getJSONObject(i)
            if (e.getString("type") == "blob" && e.getString("path").startsWith("$p/"))
                del.put(JSONObject().put("path", e.getString("path")).put("mode", e.getString("mode")).put("type", "blob").put("sha", JSONObject.NULL))
        }
        if (del.length() == 0) throw Exception("Ordner „$p“ nicht gefunden oder leer")
        val newTree = JSONObject(need(call("POST", "/repos/${r.full}/git/trees", JSONObject().put("base_tree", tree).put("tree", del)), "Löschen vorbereiten").body).getString("sha")
        val commit = JSONObject(need(call("POST", "/repos/${r.full}/git/commits", JSONObject()
            .put("message", "Ordner $p gelöscht").put("tree", newTree).put("parents", JSONArray().put(head))), "Commit").body).getString("sha")
        need(call("PATCH", "/repos/${r.full}/git/refs/heads/${r.branch}", JSONObject().put("sha", commit)), "Speichern")
        return "🗑 Ordner „$p“ gelöscht (${del.length()} Dateien)"
    }

    fun setSecret(r: Repo, name: String, value: String): String {
        val k = JSONObject(need(call("GET", "/repos/${r.full}/actions/secrets/public-key"), "Schlüssel holen").body)
        val pk = Base64.decode(k.getString("key"), Base64.NO_WRAP)
        val msg = value.toByteArray()
        val out = ByteArray(msg.size + 48)   // crypto_box_SEALBYTES
        if (!LazySodiumAndroid(SodiumAndroid()).cryptoBoxSeal(out, msg, msg.size.toLong(), pk)) throw Exception("Verschlüsseln fehlgeschlagen")
        need(call("PUT", "/repos/${r.full}/actions/secrets/$name", JSONObject()
            .put("encrypted_value", Base64.encodeToString(out, Base64.NO_WRAP)).put("key_id", k.getString("key_id"))), "Secret speichern")
        return "✅ Secret $name in ${r.name} gespeichert"
    }

    fun assets(r: Repo): List<String> {
        val rel = call("GET", "/repos/${r.full}/releases/latest")
        if (rel.code !in 200..299) return emptyList()
        val a = JSONObject(rel.body).optJSONArray("assets") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).getString("name") }
    }
    // ---------- Builds ----------
    data class Run(val id: Long, val title: String, val status: String, val url: String)

    fun runs(r: Repo): List<Run> {
        val t = call("GET", "/repos/${r.full}/actions/runs?per_page=8")
        if (t.code !in 200..299) return emptyList()
        val a = JSONObject(t.body).getJSONArray("workflow_runs")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val st = when (o.optString("conclusion")) {
                "success" -> "✅"; "failure" -> "❌"; "cancelled" -> "⏹"; "skipped" -> "⏭"
                else -> if (o.optString("status") == "completed") "•" else "⏳"
            }
            Run(o.getLong("id"), "#${o.optInt("run_number")} ${o.optString("display_title").take(40)}", st, o.getString("html_url"))
        }
    }

    fun build(r: Repo): String {
        val a = JSONObject(need(call("GET", "/repos/${r.full}/actions/workflows"), "Workflows lesen").body).getJSONArray("workflows")
        if (a.length() == 0) throw Exception("${r.name} hat keinen Build (Workflow)")
        val wf = a.getJSONObject(0)
        need(call("POST", "/repos/${r.full}/actions/workflows/${wf.getLong("id")}/dispatches", JSONObject().put("ref", r.branch)), "Build starten")
        return "🚀 Build „${wf.optString("name")}“ in ${r.name} gestartet"
    }

    fun rerun(r: Repo, run: Run): String { need(call("POST", "/repos/${r.full}/actions/runs/${run.id}/rerun"), "Wiederholen"); return "🔁 ${run.title} wird wiederholt" }
    fun cancel(r: Repo, run: Run): String { need(call("POST", "/repos/${r.full}/actions/runs/${run.id}/cancel"), "Abbrechen"); return "⏹ ${run.title} abgebrochen" }

    // ---------- Releases ----------
    data class Rel(val id: Long, val tag: String, val name: String, val latest: Boolean, val files: List<String>)

    fun releases(r: Repo): List<Rel> {
        val t = call("GET", "/repos/${r.full}/releases?per_page=30")
        if (t.code !in 200..299) return emptyList()
        val latestId = call("GET", "/repos/${r.full}/releases/latest").let { if (it.code in 200..299) JSONObject(it.body).optLong("id") else -1L }
        val a = JSONArray(t.body)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val as_ = o.optJSONArray("assets") ?: JSONArray()
            Rel(o.getLong("id"), o.getString("tag_name"), o.optString("name").ifBlank { o.getString("tag_name") }, o.getLong("id") == latestId,
                (0 until as_.length()).map { k -> as_.getJSONObject(k).getString("name") })
        }
    }

    fun makeLatest(r: Repo, rel: Rel): String {
        need(call("PATCH", "/repos/${r.full}/releases/${rel.id}", JSONObject().put("make_latest", "true")), "Als neueste markieren")
        return "⭐ ${rel.name} ist jetzt die neueste Version"
    }

    fun deleteRelease(r: Repo, rel: Rel): String {
        need(call("DELETE", "/repos/${r.full}/releases/${rel.id}"), "Version löschen")
        call("DELETE", "/repos/${r.full}/git/refs/tags/${rel.tag}")
        return "🗑 ${rel.name} gelöscht"
    }
}

fun toolSp(c: Context) = c.getSharedPreferences("tools", Context.MODE_PRIVATE)
private fun sp(c: Context) = c.getSharedPreferences("tools", Context.MODE_PRIVATE)
const val TOKEN_URL = "https://github.com/settings/tokens/new?description=Link-Zentrale&scopes=repo,workflow,delete_repo"

// ======================= Oberfläche: Werkzeuge =======================

private val CARD = Color(0xFF11325A)
private val SOFT = Color(0xFFA9C3E0)
private val BLUE = Color(0xFF8FD3FF)
private val PINK = Color(0xFFFF5C8A)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = CARD)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            content()
        }
    }
}

@Composable
private fun Btn(text: String, color: Color = BLUE, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) =
    Button(onClick, modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF0A2540))) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }

@Composable
private fun Field(value: String, label: String, secret: Boolean = false, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)

@Composable
private fun <T> Picker(label: String, items: List<T>, selected: T?, name: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(selected?.let(name) ?: label, color = Color.White, modifier = Modifier.weight(1f)); Text("▾", color = BLUE)
        }
        DropdownMenu(open, { open = false }) {
            if (items.isEmpty()) DropdownMenuItem(text = { Text("(nichts gefunden)") }, onClick = { open = false })
            items.forEach { DropdownMenuItem(text = { Text(name(it)) }, onClick = { onPick(it); open = false }) }
        }
    }
}

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var token by remember { mutableStateOf(sp(ctx).getString("token", "") ?: "") }
    var status by remember { mutableStateOf(if (token.isBlank()) "Zuerst Token eintragen (Maske 1)" else "…") }
    var repos by remember { mutableStateOf<List<Api.Repo>>(emptyList()) }
    var repo by remember { mutableStateOf<Api.Repo?>(null) }
    var assets by remember { mutableStateOf<List<String>>(emptyList()) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }  // Titel, Muss-Eingabe, Aktion

    fun run(label: String, after: () -> Unit = {}, block: () -> String) {
        scope.launch {
            status = "⏳ $label …"
            status = try { withContext(Dispatchers.IO) { block() }.also { after() } } catch (e: Exception) { "❌ ${e.message}" }
        }
    }
    fun loadRepos() = run("Repos laden") {
        val l = Api.repos(); scope.launch { repos = l; repo = l.firstOrNull { it.full == repo?.full } ?: repo }; "✅ ${l.size} Repos geladen"
    }
    fun loadRepo(r: Api.Repo) = run("${r.name} laden") {
        val a = Api.assets(r); val f = Api.folders(r)
        scope.launch { assets = a; folders = f }; "✅ ${r.name}: ${f.size} Ordner, ${a.size} Dateien im neuesten Release"
    }
    LaunchedEffect(Unit) {
        Api.token = token
        if (token.isNotBlank()) run("Anmelden", { loadRepos() }) { Api.me() }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←", fontSize = 26.sp, color = BLUE) }
            Text("Werkzeuge", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Text(status, color = if (status.startsWith("❌")) PINK else SOFT, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))

        // 1 — Token
        Section("1 🔑 Token") {
            Text("„Neuen Token erstellen“ öffnet GitHub mit fertig ausgefüllter Maske. Dort nur Ablauf wählen → Generate token → kopieren → hier einfügen.", color = SOFT, fontSize = 13.sp)
            Btn("🆕 Neuen Token erstellen") { open(ctx, TOKEN_URL) }
            Field(token, "Token hier einfügen (ghp_…)", secret = true) { token = it.trim() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("💾 Speichern", modifier = Modifier.weight(1f)) {
                    sp(ctx).edit().putString("token", token).apply(); Api.token = token
                    run("Prüfen", { loadRepos() }) { Api.me() }
                }
                Btn("📋 Kopieren", modifier = Modifier.weight(1f)) { if (token.isNotBlank()) copy(ctx, token) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open(ctx, "https://github.com/settings/tokens") }, modifier = Modifier.weight(1f)) { Text("Token-Liste / löschen", color = BLUE) }
                OutlinedButton(onClick = { token = ""; Api.token = ""; sp(ctx).edit().remove("token").apply(); repos = emptyList(); repo = null; status = "Token aus der App entfernt" },
                    modifier = Modifier.weight(1f)) { Text("Aus App entfernen", color = PINK) }
            }
        }

        // 2 — Repo wählen + alle Links
        Section("2 📁 Repo & Links") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Picker("Repo wählen", repos, repo, { (if (it.private) "🔒 " else "🌍 ") + it.name }) { repo = it; loadRepo(it) } }
                TextButton(onClick = { loadRepos() }) { Text("↻", fontSize = 22.sp, color = BLUE) }
            }
            repo?.let { r ->
                val links = buildList {
                    add("Repo" to r.html)
                    add("Secrets-Seite" to "${r.html}/settings/secrets/actions")
                    add("Builds (Actions)" to "${r.html}/actions")
                    add("Versionen" to "${r.html}/releases")
                    add("Klonen" to "${r.html}.git")
                    assets.forEach { add("⬇ $it" to "${r.html}/releases/latest/download/$it") }
                }
                Btn("📋 Alle Links von ${r.name} kopieren", PINK) { copy(ctx, links.joinToString("\n") { "${it.first}: ${it.second}" }) }
                links.forEach { (n, u) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(u, color = SOFT, fontSize = 11.sp)
                        }
                        TextButton(onClick = { copy(ctx, u) }) { Text("📋") }
                        TextButton(onClick = { open(ctx, u) }) { Text("↗") }
                    }
                }
            }
        }

        // 3 — Neues Repo
        Section("3 ➕ Neues Repo") {
            var n by remember { mutableStateOf("") }
            var d by remember { mutableStateOf("") }
            var priv by remember { mutableStateOf(true) }
            Field(n, "Name (z. B. MeinTV)") { n = it.replace(' ', '-') }
            Field(d, "Beschreibung (optional)") { d = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(priv, { priv = it }); Text(if (priv) "  🔒 Privat" else "  🌍 Öffentlich (für Downloads ohne Anmeldung)", color = Color.White)
            }
            Btn("Repo anlegen") { if (n.isNotBlank()) run("Anlegen", { n = ""; d = ""; loadRepos() }) { Api.createRepo(n.trim(), d.trim(), priv) } }
        }

        // 4 — Secret
        Section("4 🔐 Secret eintragen") {
            var n by remember { mutableStateOf("UPDATE_TOKEN") }
            var v by remember { mutableStateOf("") }
            Text("Ziel: ${repo?.name ?: "– erst in Maske 2 ein Repo wählen –"}", color = SOFT, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("UPDATE_TOKEN", "FEEDBACK_TOKEN", "KEYSTORE_PASS", "KEYSTORE_B64").forEach {
                    FilterChip(n == it, { n = it }, label = { Text(it.removeSuffix("_TOKEN").removePrefix("KEYSTORE_"), fontSize = 11.sp) })
                }
            }
            Field(n, "Name") { n = it.uppercase().replace(' ', '_') }
            Field(v, "Wert", secret = true) { v = it.trim() }
            OutlinedButton(onClick = { v = token }, modifier = Modifier.fillMaxWidth()) { Text("🔑 Gespeicherten Token als Wert einsetzen", color = BLUE) }
            Btn("Secret speichern") { val r = repo; if (r != null && n.isNotBlank() && v.isNotBlank()) run("Secret speichern", { v = "" }) { Api.setSecret(r, n, v) } }
        }

        // 5 — Ordner
        Section("5 📂 Ordner im Repo") {
            var p by remember { mutableStateOf("") }
            var del by remember { mutableStateOf<String?>(null) }
            Text("Repo: ${repo?.name ?: "– erst in Maske 2 wählen –"}", color = SOFT, fontSize = 13.sp)
            Field(p, "Neuer Ordner (z. B. dateien/bilder)") { p = it }
            Btn("Ordner anlegen") { val r = repo; if (r != null && p.isNotBlank()) run("Ordner anlegen", { p = ""; loadRepo(r) }) { Api.createFolder(r, p) } }
            Picker("Ordner zum Löschen wählen", folders, del, { it }) { del = it }
            Btn("🗑 Ordner löschen", PINK) {
                val r = repo; val f = del
                if (r != null && f != null) confirm = Triple("Ordner „$f“ mit allem Inhalt löschen?", "", { run("Ordner löschen", { del = null; loadRepo(r) }) { Api.deleteFolder(r, f) } })
            }
        }

        // 7 — Builds
        Section("7 🚀 Builds") {
            var runs by remember(repo) { mutableStateOf<List<Api.Run>>(emptyList()) }
            Text("Repo: ${repo?.name ?: "– erst in Maske 2 wählen –"}", color = SOFT, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("🚀 Neu bauen", modifier = Modifier.weight(1f)) { val r = repo; if (r != null) run("Build starten") { Api.build(r) } }
                Btn("↻ Liste", modifier = Modifier.weight(1f)) { val r = repo; if (r != null) run("Builds laden") { val l = Api.runs(r); scope.launch { runs = l }; "✅ ${l.size} letzte Builds" } }
            }
            runs.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${b.status} ${b.title}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (b.status == "⏳") TextButton(onClick = { val r = repo; if (r != null) run("Abbrechen") { Api.cancel(r, b) } }) { Text("⏹") }
                    else TextButton(onClick = { val r = repo; if (r != null) run("Wiederholen") { Api.rerun(r, b) } }) { Text("🔁") }
                    TextButton(onClick = { open(ctx, b.url) }) { Text("↗") }
                }
            }
        }

        // 8 — Releases
        Section("8 📦 Versionen (Releases)") {
            var rels by remember(repo) { mutableStateOf<List<Api.Rel>>(emptyList()) }
            Text("Tipp: Die Versionen von HandyPutz und Link-Zentrale liegen im Repo HandyPutz-Updates.", color = SOFT, fontSize = 13.sp)
            Btn("↻ Versionen von ${repo?.name ?: "…"} laden") { val r = repo; if (r != null) run("Versionen laden") { val l = Api.releases(r); scope.launch { rels = l }; "✅ ${l.size} Versionen" } }
            rels.forEach { v ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text((if (v.latest) "⭐ " else "") + v.name + "  (${v.tag})", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    v.files.forEach { f ->
                        val u = "${repo?.html}/releases/download/${v.tag}/$f"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⬇ $f", color = SOFT, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { copy(ctx, u) }) { Text("📋") }
                            TextButton(onClick = { share(ctx, "$f: $u") }) { Text("📤") }
                        }
                    }
                    Row {
                        if (!v.latest) TextButton(onClick = { val r = repo; if (r != null) run("Markieren") { Api.makeLatest(r, v).also { val l = Api.releases(r); scope.launch { rels = l } } } }) { Text("⭐ Als neueste", color = BLUE) }
                        TextButton(onClick = {
                            val r = repo
                            if (r != null) confirm = Triple("Version ${v.name} löschen?", "", { run("Version löschen") { Api.deleteRelease(r, v).also { val l = Api.releases(r); scope.launch { rels = l } } } })
                        }) { Text("🗑 Löschen", color = PINK) }
                    }
                    HorizontalDivider(color = Color(0x33FFFFFF))
                }
            }
        }

        // 6 — Repo löschen
        Section("6 🗑 Repo löschen") {
            Text("Löscht das in Maske 2 gewählte Repo endgültig. Zur Sicherheit musst du den Namen eintippen.", color = SOFT, fontSize = 13.sp)
            Btn("Repo ${repo?.name ?: ""} löschen", PINK) {
                val r = repo
                if (r != null) confirm = Triple("Repo ${r.full} endgültig löschen?", r.name, { run("Repo löschen", { repo = null; loadRepos() }) { Api.deleteRepo(r.full) } })
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    confirm?.let { (title, must, action) ->
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { if (must.isNotEmpty()) Field(typed, "Zum Bestätigen „$must“ eintippen") { typed = it } else Text("Das kann nicht rückgängig gemacht werden.") },
            confirmButton = { TextButton(enabled = must.isEmpty() || typed.trim() == must, onClick = { confirm = null; action() }) { Text("Löschen", color = PINK) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } }
        )
    }
}
