package de.tino.links

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ======================= Projekt-Zuordnung =======================

/** Update-Repo eines Projekts (dort liegen Dateien + Bewertungen) */
fun repoOf(ls: List<Link>): String? =
    ls.firstNotNullOfOrNull { Regex("github\\.com/([^/]+/[^/]+)/releases").find(it.url)?.groupValues?.get(1) }

/** Quell-Repo (dort laufen die Builds) */
fun srcRepo(project: String, repo: String) = if (project == "Link-Zentrale") "$USER/Link-Zentrale" else repo.removeSuffix("-Updates")

/** Gehört ein Eintrag (Bewertung) zu diesem Projekt? Die Link-Zentrale teilt sich das Repo mit HandyPutz. */
fun belongs(project: String?, title: String) = when {
    project == null -> true
    project == "Link-Zentrale" -> title.startsWith("Link-Zentrale")
    else -> !title.startsWith("Link-Zentrale")
}

fun stars(title: String) = title.count { it == '★' }

// ======================= Statistik je Projekt =======================

data class Info(
    val project: String, val repo: String, val unread: Int = 0, val ratings: Int = 0, val avg: Double = 0.0,
    val bugs: Int = 0, val downloads: Int = 0, val build: String = "", val buildId: Long = 0, val buildUrl: String = "",
) {
    fun json() = JSONObject().put("p", project).put("r", repo).put("u", unread).put("n", ratings).put("a", avg).put("b", bugs)
        .put("d", downloads).put("s", build).put("i", buildId).put("l", buildUrl)

    companion object {
        fun of(o: JSONObject) = Info(o.getString("p"), o.getString("r"), o.optInt("u"), o.optInt("n"), o.optDouble("a", 0.0),
            o.optInt("b"), o.optInt("d"), o.optString("s"), o.optLong("i"), o.optString("l"))
    }
}

object Stats {
    fun info(c: Context, project: String, repo: String): Info {
        val issues = try { Rv.list(repo, project) } catch (_: Exception) { emptyList() }
        val rated = issues.filter { stars(it.title) > 0 }
        val dl = try {
            val r = Api.call("GET", "/repos/$repo/releases?per_page=100")
            if (r.code !in 200..299) 0 else {
                val a = JSONArray(r.body); var sum = 0
                for (i in 0 until a.length()) {
                    val rel = a.getJSONObject(i)
                    val linkTag = rel.optString("tag_name").startsWith("link-")
                    if (linkTag != (project == "Link-Zentrale")) continue
                    val as_ = rel.optJSONArray("assets") ?: continue
                    for (k in 0 until as_.length()) {
                        val x = as_.getJSONObject(k)
                        if (project == "Link-Zentrale" || projectOf(x.getString("name")) == project) sum += x.optInt("download_count")
                    }
                }
                sum
            }
        } catch (_: Exception) { 0 }
        var build = ""; var bid = 0L; var burl = ""
        try {
            val r = Api.call("GET", "/repos/${srcRepo(project, repo)}/actions/runs?per_page=1")
            if (r.code in 200..299) {
                JSONObject(r.body).optJSONArray("workflow_runs")?.optJSONObject(0)?.let { o ->
                    build = when (o.optString("conclusion")) { "success" -> "✅"; "failure" -> "❌"; "cancelled" -> "⏹"; else -> if (o.optString("status") == "completed") "•" else "⏳" }
                    bid = o.optLong("id"); burl = o.optString("html_url")
                }
            }
        } catch (_: Exception) { }
        return Info(project, repo, issues.count { Rv.isNew(c, repo, it) }, rated.size,
            if (rated.isEmpty()) 0.0 else rated.map { stars(it.title) }.average(),
            issues.count { it.open && kind(it.title) == "🐞" }, dl, build, bid, burl)
    }

    /** Alle Projekte aus den zuletzt geladenen Links */
    fun all(c: Context): List<Info> {
        if (Api.token.isBlank()) Api.token = toolSp(c).getString("token", "") ?: ""
        return Store.cache(c).filter { it.project.isNotBlank() }.groupBy { it.project }
            .mapNotNull { (p, ls) -> repoOf(ls)?.let { info(c, p, it) } }
            .also { save(c, it) }
    }

    fun save(c: Context, l: List<Info>) =
        c.getSharedPreferences("links", Context.MODE_PRIVATE).edit().putString("info", JSONArray().apply { l.forEach { put(it.json()) } }.toString()).apply()

    fun load(c: Context): List<Info> = try {
        val a = JSONArray(c.getSharedPreferences("links", Context.MODE_PRIVATE).getString("info", "[]"))
        (0 until a.length()).map { Info.of(a.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }
}

// ======================= Benachrichtigungen + Hintergrund-Prüfung =======================

object Notify {
    private const val CH = "reviews"
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(c: Context, id: Int, title: String, text: String, project: String?) {
        if (!allowed(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Bewertungen & Builds", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (project != null) putExtra("reviews", project) }
        val pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            NotificationManagerCompat.from(c).notify(id, NotificationCompat.Builder(c, CH)
                .setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(tr(title)).setContentText(tr(text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(tr(text))).setContentIntent(pi).setAutoCancel(true).build())
        } catch (_: SecurityException) { }
    }
}

class WatchWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        L.init(c)
        val sp = c.getSharedPreferences("links", Context.MODE_PRIVATE)
        val infos = try { Stats.all(c) } catch (_: Exception) { return Result.retry() }
        // neue Bewertungen: nur melden, wenn es mehr geworden sind als beim letzten Mal
        infos.forEach { i ->
            val key = "notified_${i.repo}_${i.project}"
            if (i.unread > sp.getInt(key, 0))
                Notify.show(c, ("r" + i.project).hashCode(), "💬 ${i.project}: ${i.unread} " + "neue Bewertungen", "Tippe zum Lesen", i.project)
            sp.edit().putInt(key, i.unread).apply()
            if (i.build == "❌" && sp.getLong("buildfail_${i.project}", 0) != i.buildId) {
                Notify.show(c, ("b" + i.project).hashCode(), "❌ ${i.project}: Build fehlgeschlagen", i.buildUrl, null)
                sp.edit().putLong("buildfail_${i.project}", i.buildId).apply()
            }
        }
        LinkWidget.updateAll(c)
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) = WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "watch", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WatchWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
    }
}

// ======================= Startseiten-Widget =======================

class LinkWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = render(c, m, ids)

    companion object {
        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            render(c, m, m.getAppWidgetIds(ComponentName(c, LinkWidget::class.java)))
        }

        fun render(c: Context, m: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            L.init(c)
            val infos = Stats.load(c)
            val unread = infos.sumOf { it.unread }
            val bad = infos.count { it.build == "❌" }
            val line = tr(if (unread > 0) "💬 $unread neu" else "💬 0 neu") + "   " + (if (bad > 0) "❌ $bad Build" else "✅ Builds")
            val msg = Store.cache(c).filter { it.project.isNotBlank() }.groupBy { it.project }
                .map { (p, ls) -> testMsg(p, ls) }.joinToString("\n\n————\n\n")
            val share = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), tr("Link teilen"))
            val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val openRev = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("pick", true)
            val f = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ids.forEach { id ->
                val v = RemoteViews(c.packageName, R.layout.widget)
                v.setTextViewText(R.id.w_count, line)
                v.setTextViewText(R.id.w_share, tr("📤 Test-Links teilen"))
                v.setOnClickPendingIntent(R.id.w_title, PendingIntent.getActivity(c, 1, open, f))
                v.setOnClickPendingIntent(R.id.w_count, PendingIntent.getActivity(c, 2, openRev, f))
                v.setOnClickPendingIntent(R.id.w_share, PendingIntent.getActivity(c, 3, share, f))
                m.updateAppWidget(id, v)
            }
        }
    }
}

// ======================= QR-Code =======================

fun qr(text: String, size: Int = 640): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { if (m[it % size, it / size]) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrDialog(title: String, url: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(url) { qr(url) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("🔳 $title") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bmp.asImageBitmap(), null, Modifier.size(260.dp))
                Text("Zum Scannen mit der Handy-Kamera", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Text(url, fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = { share(ctx, "$title: $url") }) { Text("Link teilen") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Schließen") } }
    )
}

// ======================= Feedback aus der Link-Zentrale =======================

object Feedback {
    /** Erst eingebauter Feedback-Schlüssel, sonst eigener Token, sonst null (→ per Nachricht teilen) */
    private fun key(c: Context): String =
        BuildConfig.FEEDBACK_TOKEN.ifBlank { toolSp(c).getString("token", "") ?: "" }

    fun device(c: Context) = "App: Link-Zentrale 1.${versionCode(c)} • Android ${Build.VERSION.RELEASE} • ${Build.MANUFACTURER} ${Build.MODEL}"

    /** true = gesendet, false = kein Zugang */
    fun send(c: Context, title: String, body: String): Boolean {
        val k = key(c); if (k.isBlank()) return false
        val old = Api.token
        return try {
            Api.token = k
            Api.need(Api.call("POST", "/repos/$UPDATE_REPO/issues", JSONObject().put("title", "Link-Zentrale: $title".take(200)).put("body", body + "\n\n---\n" + device(c))), "Senden")
            true
        } catch (_: Exception) { false } finally { Api.token = old }
    }
}

@Composable
fun FeedbackDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("⭐") }
    var starsN by remember { mutableStateOf(5) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("⭐ Feedback geben") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("⭐" to "Bewertung", "💡" to "Vorschlag", "🐞" to "Fehler").forEach { (k, n) ->
                        FilterChip(type == k, { type = k }, label = { Text("$k $n") })
                    }
                }
                if (type == "⭐") Row {
                    (1..5).forEach { i -> TextButton(onClick = { starsN = i }, contentPadding = PaddingValues(2.dp)) { Text(if (i <= starsN) "★" else "☆", fontSize = 28.sp, color = Color(0xFFFFC94D)) } }
                }
                OutlinedTextField(text, { text = it }, label = { Text("Was möchtest du uns sagen?") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                if (status.isNotBlank()) Text(status, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val label = when (type) { "⭐" -> "⭐ Bewertung " + "★".repeat(starsN) + "☆".repeat(5 - starsN); "💡" -> "💡 Vorschlag"; else -> "🐞 Fehler" }
                val title = "[$label] " + text.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(80)
                scope.launch {
                    status = "⏳ Sende …"
                    val ok = withContext(Dispatchers.IO) { Feedback.send(ctx, title.trim(), text) }
                    if (ok) { toast(ctx, tr("✅ Danke! Feedback gesendet")); onClose() }
                    else { status = "Kein Feedback-Zugang – bitte per Nachricht senden"; share(ctx, "Link-Zentrale $title\n\n$text\n\n${Feedback.device(ctx)}") }
                }
            }) { Text("Senden") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } }
    )
}

// ======================= Absturz-Schutz =======================

class LinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        L.init(this)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                getSharedPreferences("guard", MODE_PRIVATE).edit()
                    .putString("crash", (e.stackTraceToString()).take(6000)).commit()
            } catch (_: Exception) { }
            prev?.uncaughtException(t, e)
        }
        try { WatchWorker.schedule(this) } catch (_: Exception) { }
    }
}

object Guard {
    private fun sp(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    /** Beim Start aufrufen. true = zu oft abgestürzt → Rettung anzeigen */
    fun starting(c: Context): Boolean {
        val v = versionCode(c)
        val sp = sp(c)
        if (sp.getLong("ok", 0) == v) return false          // diese Version lief schon sauber
        val n = sp.getInt("fails_$v", 0) + 1
        sp.edit().putInt("fails_$v", n).commit()
        return n >= 3
    }

    fun ok(c: Context) { val v = versionCode(c); sp(c).edit().putLong("ok", v).remove("fails_$v").apply() }

    /** Gespeicherten Absturz als Fehlerbericht senden */
    fun sendCrash(c: Context) {
        val crash = sp(c).getString("crash", null) ?: return
        val ok = Feedback.send(c, "[🐞 Absturz] " + crash.lineSequence().first().take(90), "Automatischer Fehlerbericht:\n```\n$crash\n```")
        if (ok) sp(c).edit().remove("crash").apply()
    }

    /** Neueste ältere Version der Link-Zentrale */
    fun previous(c: Context): Gh.Update? {
        val r = Api.call("GET", "/repos/$UPDATE_REPO/releases?per_page=40")
        if (r.code !in 200..299) return null
        val a = JSONArray(r.body); val cur = versionCode(c)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); val tag = o.optString("tag_name")
            if (!tag.startsWith("link-v")) continue
            val vc = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (vc >= cur) continue
            val as_ = o.optJSONArray("assets") ?: continue
            for (k in 0 until as_.length()) if (as_.getJSONObject(k).getString("name").endsWith(".apk"))
                return Gh.Update(vc, o.optString("name"), as_.getJSONObject(k).getString("browser_download_url"))
        }
        return null
    }
}

/** Rettungsbildschirm ohne Compose (läuft auch, wenn die Oberfläche kaputt ist) */
class RescueActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        L.init(this)
        val info = TextView(this).apply { setTextColor(AColor.WHITE); textSize = 17f; text = tr("Die App ist zweimal beim Start abgestürzt.") }
        val btnFix = Button(this).apply { text = tr("Reparierte Version suchen") }
        val btnPrev = Button(this).apply { text = tr("Vorgänger-Version laden") }
        val btnStart = Button(this).apply { text = tr("Trotzdem starten") }
        val lay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(60, 60, 60, 60); setBackgroundColor(0xFF0A2540.toInt())
            addView(info); addView(btnFix); addView(btnPrev); addView(btnStart)
        }
        setContentView(lay)
        Thread { Guard.sendCrash(this) }.start()
        btnStart.setOnClickListener {
            Guard.ok(this)
            startActivity(Intent(this, MainActivity::class.java)); finish()
        }
        // 1) Neuere (reparierte) Version installieren – geht direkt über die kaputte
        btnFix.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))); return@setOnClickListener
            }
            info.text = tr("Suche …")
            Thread {
                val u = try { Gh.selfUpdate(versionCode(this)) } catch (_: Exception) { null }
                if (u == null) { runOnUiThread { info.text = tr("Noch keine reparierte Version – bitte Vorgänger laden") }; return@Thread }
                try {
                    val f = Gh.download(this, u.url)
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
                    runOnUiThread {
                        info.text = u.name
                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                } catch (e: Exception) { runOnUiThread { info.text = "❌ ${e.message}" } }
            }.start()
        }
        // 2) Ältere Version: Android erlaubt kein Zurückstufen → Datei im Browser laden, dann deinstallieren und Datei öffnen
        btnPrev.setOnClickListener {
            info.text = tr("Suche Vorgänger …")
            Thread {
                val u = try { Guard.previous(this) } catch (_: Exception) { null }
                runOnUiThread {
                    if (u == null) { info.text = tr("Keine ältere Version gefunden"); return@runOnUiThread }
                    info.text = tr("1. Die Datei wird im Browser geladen.\n2. Link-Zentrale deinstallieren.\n3. Geladene Datei öffnen und installieren.")
                    open(this, u.url)
                    btnPrev.text = tr("Jetzt deinstallieren")
                    btnPrev.setOnClickListener { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))) }
                }
            }.start()
        }
    }
}
