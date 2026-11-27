@file:OptIn(ExperimentalMaterial3Api::class)

package de.tino.links

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ======================= Bewertungen = GitHub-Issues im Update-Repo des Projekts =======================

object Rv {
    data class Issue(val number: Int, val title: String, val body: String, val user: String, val date: String,
                     val open: Boolean, val comments: Int, val url: String)
    data class Comment(val user: String, val body: String, val date: String)

    fun list(repo: String, project: String? = null): List<Issue> {
        val r = Api.call("GET", "/repos/$repo/issues?state=all&per_page=60&sort=created&direction=desc")
        if (r.code == 404) return emptyList()
        val a = JSONArray(Api.need(r, "Bewertungen laden").body)
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { !it.has("pull_request") && belongs(project, it.optString("title")) }.map {
            Issue(it.getInt("number"), it.optString("title"), it.optString("body"), it.getJSONObject("user").optString("login"),
                it.optString("created_at").take(10), it.optString("state") == "open", it.optInt("comments"), it.optString("html_url"))
        }
    }

    fun comments(repo: String, n: Int): List<Comment> {
        val a = JSONArray(Api.need(Api.call("GET", "/repos/$repo/issues/$n/comments?per_page=100"), "Antworten laden").body)
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Comment(it.getJSONObject("user").optString("login"), it.optString("body"), it.optString("created_at").take(16).replace('T', ' '))
        }
    }

    fun reply(repo: String, n: Int, text: String) {
        if (Api.token.isBlank()) throw Exception("Zum Antworten zuerst unter 🛠 Werkzeuge → Token speichern")
        Api.need(Api.call("POST", "/repos/$repo/issues/$n/comments", JSONObject().put("body", text)), "Antwort senden")
    }

    fun setOpen(repo: String, n: Int, open: Boolean) {
        if (Api.token.isBlank()) throw Exception("Dafür zuerst unter 🛠 Werkzeuge → Token speichern")
        Api.need(Api.call("PATCH", "/repos/$repo/issues/$n", JSONObject().put("state", if (open) "open" else "closed")), "Status ändern")
    }

    // ---- gelesen / neu ----
    private fun sp(c: Context) = c.getSharedPreferences("seen", Context.MODE_PRIVATE)
    fun isNew(c: Context, repo: String, i: Issue) = sp(c).getInt("$repo#${i.number}", -1) < i.comments
    fun markRead(c: Context, repo: String, i: Issue, comments: Int) = sp(c).edit().putInt("$repo#${i.number}", comments).apply()
    fun unread(c: Context, repo: String, project: String? = null) = list(repo, project).count { isNew(c, repo, it) }
}

fun kind(title: String) = when {
    title.contains("Bewertung", true) || title.contains("★") || title.contains("⭐") -> "⭐"
    title.contains("Vorschlag", true) || title.contains("💡") -> "💡"
    title.contains("Fehler", true) || title.contains("Absturz", true) || title.contains("Crash", true) || title.contains("🐞") -> "🐞"
    else -> "💬"
}

private val CARD = Color(0xFF11325A)
private val SOFT = Color(0xFFA9C3E0)
private val BLUE = Color(0xFF8FD3FF)
private val PINK = Color(0xFFFF5C8A)

@Composable
fun ReviewsScreen(project: String, repo: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Rv.Issue>>(emptyList()) }
    var status by remember { mutableStateOf("Lade …") }
    var open by remember { mutableStateOf<Rv.Issue?>(null) }
    var filter by remember { mutableStateOf("alle") }
    var tick by remember { mutableStateOf(0) }

    fun load() = scope.launch {
        status = "Lade …"
        status = try {
            val l = withContext(Dispatchers.IO) { Rv.list(repo, project) }
            list = l
            val n = l.count { Rv.isNew(ctx, repo, it) }
            if (l.isEmpty()) "Noch keine Bewertungen" else "${l.size} Einträge • $n neu"
        } catch (e: Exception) { "❌ ${e.message}" }
    }
    LaunchedEffect(Unit) { load() }

    open?.let { i -> IssueScreen(repo, i) { open = null; tick++; load() }; return }
    BackHandler(onBack = onBack)

    val shown = list.filter {
        when (filter) { "neu" -> Rv.isNew(ctx, repo, it); "offen" -> it.open; "⭐", "💡", "🐞" -> kind(it.title) == filter; else -> true }
    }
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("←", fontSize = 26.sp, color = BLUE) }
                Column(Modifier.weight(1f)) {
                    Text("💬 $project", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = if (status.startsWith("❌")) PINK else SOFT, fontSize = 13.sp)
                }
                TextButton(onClick = { load() }) { Text("↻", fontSize = 24.sp, color = BLUE) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("alle", "neu", "offen", "⭐", "💡", "🐞").forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text(f) }) }
            }
            if (list.any { Rv.isNew(ctx, repo, it) })
                TextButton(onClick = { list.forEach { Rv.markRead(ctx, repo, it, it.comments) }; tick++; load() }) { Text("✔ Alle als gelesen markieren", color = BLUE) }
        }
        items(shown, key = { it.number }) { i ->
            val isNew = remember(tick, i) { Rv.isNew(ctx, repo, i) }
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { open = i }, colors = CardDefaults.cardColors(containerColor = CARD)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${kind(i.title)} ${i.title}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (isNew) Badge(containerColor = PINK) { Text("neu", color = Color.White) }
                    }
                    Text("${i.date} • #${i.number} • ${if (i.open) "offen" else "erledigt ✔"} • 💬 ${i.comments}", color = SOFT, fontSize = 12.sp)
                    Text(i.body.substringBefore("\n---").trim().take(160), color = SOFT, fontSize = 13.sp, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun IssueScreen(repo: String, issue: Rv.Issue, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    var comments by remember { mutableStateOf<List<Rv.Comment>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var isOpen by remember { mutableStateOf(issue.open) }

    fun load() = scope.launch {
        try {
            val c = withContext(Dispatchers.IO) { Rv.comments(repo, issue.number) }
            comments = c; Rv.markRead(ctx, repo, issue, c.size)
        } catch (e: Exception) { status = "❌ ${e.message}" }
    }
    LaunchedEffect(Unit) { load() }

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("←", fontSize = 26.sp, color = BLUE) }
                Text("${kind(issue.title)} #${issue.number}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(issue.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("${issue.date} • ${if (isOpen) "offen" else "erledigt ✔"}", color = SOFT, fontSize = 12.sp)
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = CARD)) {
                Text(issue.body.ifBlank { "(kein Text)" }, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
            }
            if (comments.isNotEmpty()) Text("Antworten", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        }
        items(comments) { c ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (c.user == USER) Color(0xFF1B4A80) else CARD)) {
                Column(Modifier.padding(12.dp)) {
                    Text((if (c.user == USER) "Du" else c.user) + " • " + c.date, color = SOFT, fontSize = 12.sp)
                    Text(c.body, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        item {
            OutlinedTextField(answer, { answer = it }, label = { Text("Antwort schreiben …") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 3)
            if (status.isNotBlank()) Text(status, color = if (status.startsWith("❌")) PINK else SOFT, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {
                    if (answer.isBlank()) return@Button
                    scope.launch {
                        status = "⏳ Sende …"
                        status = try {
                            withContext(Dispatchers.IO) { Rv.reply(repo, issue.number, answer.trim()) }
                            answer = ""; load(); "✅ Antwort gesendet"
                        } catch (e: Exception) { "❌ ${e.message}" }
                    }
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BLUE, contentColor = Color(0xFF0A2540))) { Text("📨 Antworten", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = {
                    scope.launch {
                        status = try {
                            withContext(Dispatchers.IO) { Rv.setOpen(repo, issue.number, !isOpen) }
                            isOpen = !isOpen; if (isOpen) "Wieder geöffnet" else "✔ Als erledigt markiert"
                        } catch (e: Exception) { "❌ ${e.message}" }
                    }
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (isOpen) "✔ Erledigt" else "↺ Öffnen", color = Color.White) }
            }
            TextButton(onClick = { open(ctx, issue.url) }) { Text("Auf GitHub ansehen ↗", color = BLUE) }
            Spacer(Modifier.height(40.dp))
        }
    }
}
