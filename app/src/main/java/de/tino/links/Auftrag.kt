package de.tino.links

import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val A_SOFT = Color(0xFFA9C3E0)
private val A_BLUE = Color(0xFF8FD3FF)
private val A_PINK = Color(0xFFFF5C8A)

/** Führt einen geprüften Auftrag aus – Schritt für Schritt, stoppt beim ersten Fehler */
object AuftragRun {
    class Hinweis(val titel: String, val text: String, val url: String)

    fun run(p: Auftrag.Plan, token: String, vault: (String) -> String, log: (String) -> Unit, hint: (Hinweis) -> Unit): Boolean {
        p.schritte.forEachIndexed { i, s ->
            val nr = "${i + 1}/${p.schritte.size}"
            if (i > 0 && s.aktion == "repo") Thread.sleep(3000)   // GitHub sperrt bei vielen neuen Repos in kurzer Zeit
            try { log("$nr ${step(s, token, vault, hint)}") }
            catch (e: Exception) { log("❌ $nr ${Auftrag.describe(s)}: ${e.message}"); return false }
        }
        return true
    }

    private fun step(s: Auftrag.Schritt, token: String, vault: (String) -> String, hint: (Hinweis) -> Unit): String = when (s.aktion) {
        "repo" -> if (Api.exists(s.repo)) "ℹ️ Repo ${Auftrag.short(s.repo)} gibt es schon"
                  else Api.createRepo(Auftrag.short(s.repo), s.s("beschreibung"), s.b("oeffentlich") != true)
        "dateien" -> Api.commitFiles(Api.repoOf(s.repo), s.s("nachricht").ifBlank { "Auftrag aus der Link-Zentrale" }, s.dateien)
        "ordner" -> Api.createFolder(Api.repoOf(s.repo), s.s("pfad"))
        "secret" -> {
            val w = s.s("wert")
            val v = Auftrag.ref(w)?.let { if (it.equals("token", true)) token else vault(it) } ?: w
            if (v.isBlank()) throw Exception("Wert fehlt")
            Api.setSecret(Api.repoOf(s.repo), s.s("name").uppercase(), v)
        }
        "einstellungen" -> Api.patchMeta(Api.repoOf(s.repo), s)
        "build" -> {
            val r = Api.repoOf(s.repo)
            try { Api.build(r) } catch (_: Exception) { Thread.sleep(6000); Api.build(r) }   // neuer Workflow braucht kurz
        }
        "neueste" -> Api.repoOf(s.repo).let { Api.makeLatest(it, Api.relByTag(it, s.s("tag"))) }
        "version_loeschen" -> Api.repoOf(s.repo).let { Api.deleteRelease(it, Api.relByTag(it, s.s("tag"))) }
        "text" -> { hint(Hinweis(s.s("titel").ifBlank { "Text" }, s.s("text"), s.s("url"))); "📝 ${s.s("titel")} – unten kopieren/öffnen" }
        else -> throw Exception("unbekannt")
    }
}

/** Text, den man in jeden Claude-Chat kopieren kann */
fun auftragVorlage() = "Bitte gib mir Änderungen an GitHub immer als EINEN Code-Block „LZ-AUFTRAG“ für meine Link-Zentrale " +
    "(Format: https://github.com/$USER/Link-Zentrale/blob/main/AUFTRAG.md). Ich kopiere ihn komplett und füge ihn in der Link-Zentrale unter 📥 Auftrag ein."

@Composable
fun AuftragSection(status: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<Auftrag.Plan?>(null) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    val missing = remember { mutableStateMapOf<String, String>() }
    val log = remember { mutableStateListOf<String>() }
    val hints = remember { mutableStateListOf<AuftragRun.Hinweis>() }
    var vaultCount by remember { mutableStateOf(Sec.vaultNames(ctx).size) }

    fun check(t: String) {
        raw = t; log.clear(); hints.clear(); missing.clear()
        if (t.isBlank()) { plan = null; err = ""; return }
        try {
            val p = Auftrag.parse(t, USER)
            plan = p; err = ""
            Auftrag.needs(p).filter { Sec.vault(ctx, it).isBlank() }.forEach { missing[it] = "" }
        } catch (e: Exception) { plan = null; err = "❌ ${e.message}" }
    }

    fun go() {
        val p = plan ?: return
        val token = Sec.token(ctx)
        if (token.isBlank()) { err = "❌ Zuerst Token eintragen (Maske 1)"; return }
        if (missing.any { it.value.isBlank() }) { err = "❌ Bitte alle Werte unten eintragen"; return }
        missing.forEach { (k, v) -> Sec.setVault(ctx, k, v) }
        vaultCount = Sec.vaultNames(ctx).size
        busy = true; err = ""; log.clear(); hints.clear()
        status("⏳ Auftrag „${p.titel}“ läuft …")
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                Api.token = token
                AuftragRun.run(p, token, { Sec.vault(ctx, it) }, { l -> scope.launch { log.add(l) } }, { h -> scope.launch { hints.add(h) } })
            }
            busy = false
            status(if (ok) "✅ Auftrag „${p.titel}“ erledigt" else "❌ Auftrag abgebrochen – siehe Liste")
        }
    }

    Section("📥 Auftrag von Claude") {
        Text("Claude schreibt dir einen Code-Block (beginnt mit LZ-AUFTRAG). Komplett kopieren, hier einfügen – die App trägt alles selbst in die richtigen Masken ein.", color = A_SOFT, fontSize = 13.sp)
        Btn("📋 Aus Zwischenablage einfügen", A_PINK) {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            check(cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: "")
        }
        val big = raw.length > 4000
        OutlinedTextField(if (big) "" else raw, { check(it) }, modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp), maxLines = 6,
            label = { Text(if (big) "Auftrag eingefügt (${raw.length / 1024} KB)" else "… oder hier einfügen") })
        if (err.isNotEmpty()) Text(err, color = A_PINK, fontSize = 14.sp)
        plan?.let { p ->
            Text("„${p.titel}“ – ${p.schritte.size} Schritte:", color = Color.White, fontWeight = FontWeight.Bold)
            p.schritte.forEachIndexed { i, s -> Text("${i + 1}. ${Auftrag.describe(s)}", color = Color.White, fontSize = 13.sp) }
            missing.keys.sorted().forEach { k ->
                Field(missing[k] ?: "", "Wert für $k (wird verschlüsselt gemerkt)", secret = true) { missing[k] = it.trim() }
            }
            Btn(if (busy) "⏳ Läuft …" else "▶ Ausführen") { if (!busy) { if (Auftrag.warnings(p).isNotEmpty()) ask = true else go() } }
        }
        log.forEach { Text(it, color = if (it.startsWith("❌")) A_PINK else Color.White, fontSize = 13.sp) }
        hints.forEach { h ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(h.titel, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (h.text.isNotBlank()) Text(h.text.take(400), color = A_SOFT, fontSize = 12.sp)
                Row {
                    if (h.text.isNotBlank()) TextButton(onClick = { copy(ctx, h.text) }) { Text("📋 Kopieren", color = A_BLUE) }
                    if (h.url.isNotBlank()) TextButton(onClick = { open(ctx, h.url) }) { Text("↗ Öffnen", color = A_BLUE) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(ctx, auftragVorlage()) }, modifier = Modifier.weight(1f)) { Text("📋 Satz für Claude", color = A_BLUE, fontSize = 12.sp) }
            OutlinedButton(onClick = { Sec.clearVault(ctx); vaultCount = 0; status("🔐 Tresor geleert") }, modifier = Modifier.weight(1f)) {
                Text("🔐 Tresor leeren ($vaultCount)", color = A_PINK, fontSize = 12.sp)
            }
        }
    }

    if (ask) plan?.let { p ->
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Wirklich ausführen?") },
            text = { Text("Diese Schritte lassen sich nicht einfach rückgängig machen:\n" + Auftrag.warnings(p).joinToString("\n") { "• $it" }) },
            confirmButton = { TextButton(onClick = { ask = false; go() }) { Text("Ja, ausführen", color = A_PINK) } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Abbrechen") } }
        )
    }
}
