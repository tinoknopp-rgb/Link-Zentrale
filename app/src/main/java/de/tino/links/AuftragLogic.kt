package de.tino.links

import org.json.JSONObject

/**
 * Auftrag von Claude: EIN Code-Block, den die App selbst auf die richtigen Masken verteilt.
 * Format siehe AUFTRAG.md im Repo. Reine Logik ohne Android – getestet in AuftragTest.
 */
object Auftrag {
    const val MARKE = "LZ-AUFTRAG"
    const val VERSION = 1
    val AKTIONEN = listOf("repo", "dateien", "ordner", "secret", "einstellungen", "build", "neueste", "version_loeschen", "text")

    data class Datei(val pfad: String, val inhalt: String?, val base64: String?, val loeschen: Boolean, val mode: String)

    class Schritt(val aktion: String, val repo: String, val o: JSONObject) {
        fun s(k: String): String = if (o.has(k) && !o.isNull(k)) o.optString(k) else ""
        fun b(k: String): Boolean? = if (o.has(k) && !o.isNull(k)) o.optBoolean(k) else null
        val dateien: List<Datei>
            get() {
                val a = o.optJSONArray("dateien") ?: return emptyList()
                return (0 until a.length()).map {
                    val d = a.getJSONObject(it)
                    val p = d.optString("pfad").trim().trim('/')
                    Datei(p, if (d.has("inhalt")) d.optString("inhalt") else null, if (d.has("base64")) d.optString("base64") else null,
                        d.optBoolean("loeschen"), mode(p, d.optBoolean("ausfuehrbar")))
                }
            }
    }

    class Plan(val titel: String, val schritte: List<Schritt>)

    /** Nimmt den ganzen kopierten Text (auch mit ``` oder Chat-Text drumherum) */
    fun parse(text: String, owner: String): Plan {
        val s = text.indexOf('{'); val e = text.lastIndexOf('}')
        if (s < 0 || e <= s) throw Exception("Kein Auftrag gefunden – bitte den ganzen Code-Block von Claude kopieren")
        val o = try { JSONObject(text.substring(s, e + 1)) } catch (x: Exception) {
            throw Exception("Auftrag unvollständig kopiert – bitte den Code-Block nochmal ganz kopieren")
        }
        if (o.optInt("version", 1) > VERSION) throw Exception("Auftrag ist neuer als diese App – erst Link-Zentrale aktualisieren")
        val arr = o.optJSONArray("schritte") ?: throw Exception("Auftrag ohne „schritte“")
        val list = (0 until arr.length()).map { i ->
            val st = arr.optJSONObject(i) ?: throw Exception("Schritt ${i + 1} ist kein Objekt")
            val a = st.optString("aktion").trim().lowercase()
            if (a !in AKTIONEN) throw Exception("Schritt ${i + 1}: unbekannte Aktion „$a“ – erst Link-Zentrale aktualisieren")
            val r = st.optString("repo").trim()
            if (a != "text" && r.isBlank()) throw Exception("Schritt ${i + 1} ($a): „repo“ fehlt")
            if (r.isNotBlank() && owner.isBlank() && '/' !in r) throw Exception("Erst Konto eintragen (👤 Konto)")
            val step = Schritt(a, if (r.isBlank()) "" else fullRepo(owner, r), st)
            when (a) {
                "dateien" -> if (step.dateien.isEmpty() || step.dateien.any { it.pfad.isBlank() }) throw Exception("Schritt ${i + 1}: Dateien ohne Pfad")
                "secret" -> if (step.s("name").isBlank() || step.s("wert").isBlank()) throw Exception("Schritt ${i + 1}: Secret ohne Name/Wert")
                "ordner" -> if (step.s("pfad").isBlank()) throw Exception("Schritt ${i + 1}: Ordner ohne Pfad")
                "neueste", "version_loeschen" -> if (step.s("tag").isBlank()) throw Exception("Schritt ${i + 1}: „tag“ fehlt")
            }
            step
        }
        if (list.isEmpty()) throw Exception("Auftrag hat keine Schritte")
        return Plan(o.optString("titel").ifBlank { "Auftrag" }, list)
    }

    /** „MeinTV“ → „konto/MeinTV“, „x/y“ bleibt */
    fun fullRepo(owner: String, r: String): String {
        val t = r.removePrefix("https://github.com/").trim().trim('/')
        return if ('/' in t) t else "$owner/$t"
    }

    fun short(full: String) = full.substringAfter('/')

    /** gradlew / *.sh brauchen das Ausführ-Recht */
    fun mode(path: String, exec: Boolean) = if (exec || path.substringAfterLast('/') == "gradlew" || path.endsWith(".sh")) "100755" else "100644"

    /** „@token“ = gespeicherter Token, „@NAME“ = Wert aus dem Tresor der App, sonst der Wert selbst */
    fun ref(wert: String): String? = if (wert.startsWith("@")) wert.drop(1) else null

    /** Tresor-Werte, die der Auftrag braucht (ohne @token) */
    fun needs(p: Plan): List<String> = p.schritte.filter { it.aktion == "secret" }.mapNotNull { ref(it.s("wert")) }
        .filter { !it.equals("token", true) }.distinct()

    fun describe(s: Schritt): String {
        val r = short(s.repo)
        return when (s.aktion) {
            "repo" -> "➕ Repo $r anlegen (" + (if (s.b("oeffentlich") == true) "🌍 öffentlich" else "🔒 privat") + ")"
            "dateien" -> {
                val d = s.dateien
                val names = d.take(3).joinToString(", ") { (if (it.loeschen) "🗑 " else "") + it.pfad } + if (d.size > 3) " … (+${d.size - 3})" else ""
                "📄 ${d.size} Datei(en) → $r: $names"
            }
            "ordner" -> "📂 Ordner ${s.s("pfad")} in $r"
            "secret" -> "🔐 Secret ${s.s("name")} → $r" + (ref(s.s("wert"))?.let { if (it.equals("token", true)) " (gespeicherter Token)" else " (aus Tresor: $it)" } ?: "")
            "einstellungen" -> "⚙ $r: " + listOfNotNull(
                s.b("oeffentlich")?.let { if (it) "🌍 öffentlich" else "🔒 privat" },
                if (s.o.has("beschreibung")) "Beschreibung" else null,
                s.o.optJSONArray("themen")?.let { "${it.length()} Themen" },
                if (s.o.has("webseite")) "Webseite" else null
            ).joinToString(", ")
            "build" -> "🚀 Build in $r starten"
            "neueste" -> "⭐ ${s.s("tag")} in $r als neueste Version"
            "version_loeschen" -> "🗑 Version ${s.s("tag")} in $r löschen"
            "text" -> "📝 " + s.s("titel").ifBlank { "Text zum Kopieren" }
            else -> s.aktion
        }
    }

    /** Schritte, die man nicht einfach rückgängig machen kann → extra nachfragen */
    fun warnings(p: Plan): List<String> = p.schritte.filter {
        it.aktion == "version_loeschen" || (it.aktion == "einstellungen" && it.b("oeffentlich") == true) ||
            (it.aktion == "dateien" && it.dateien.any { d -> d.loeschen })
    }.map { describe(it) }
}
