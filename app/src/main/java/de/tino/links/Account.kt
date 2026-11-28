package de.tino.links

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Gewähltes GitHub-Konto – in der App ist kein Konto fest eingebaut */
object Acc {
    var owner by mutableStateOf("")
    private fun sp(c: Context) = c.getSharedPreferences("links", Context.MODE_PRIVATE)

    fun init(c: Context) {
        Promo.on = sp(c).getBoolean("promo", true)
        if (owner.isNotBlank()) return
        owner = sp(c).getString("owner", "") ?: ""
        if (owner.isBlank()) {
            // Übergang: Konto aus den zuletzt geladenen Links ableiten (ältere Versionen hatten es fest eingebaut)
            val old = Regex("github\\.com/([^/]+)/").find(sp(c).getString("cache", "") ?: "")?.groupValues?.get(1)
            if (!old.isNullOrBlank()) set(c, old)
        }
    }

    fun setPromo(c: Context, on: Boolean) { Promo.on = on; sp(c).edit().putBoolean("promo", on).apply() }

    fun set(c: Context, name: String) {
        owner = name.trim().removePrefix("https://github.com/").trim('/')
        sp(c).edit().putString("owner", owner).apply()
    }
}

/** Token verschlüsselt speichern (Android-Schlüsselspeicher) */
object Sec {
    @Volatile private var prefs: SharedPreferences? = null

    private fun p(c: Context): SharedPreferences = prefs ?: synchronized(this) {
        prefs ?: try {
            val key = MasterKey.Builder(c.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(c.applicationContext, "secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (_: Exception) {
            c.getSharedPreferences("secure_fallback", Context.MODE_PRIVATE)
        }.also { s ->
            // Übergang: alten, unverschlüsselten Token übernehmen und löschen
            val old = c.getSharedPreferences("tools", Context.MODE_PRIVATE)
            old.getString("token", null)?.let { t -> s.edit().putString("token", t).apply(); old.edit().remove("token").apply() }
            prefs = s
        }
    }

    fun token(c: Context): String = p(c).getString("token", "") ?: ""
    fun setToken(c: Context, t: String) { p(c).edit().apply { if (t.isBlank()) remove("token") else putString("token", t) }.apply() }

    // Tresor für Aufträge: Secret-Werte (z. B. KEYSTORE_B64) einmal eingeben, danach automatisch
    fun vault(c: Context, name: String): String = p(c).getString("v_" + name.uppercase(), "") ?: ""
    fun setVault(c: Context, name: String, v: String) { p(c).edit().putString("v_" + name.uppercase(), v).apply() }
    fun vaultNames(c: Context): List<String> = p(c).all.keys.filter { it.startsWith("v_") }.map { it.removePrefix("v_") }.sorted()
    fun clearVault(c: Context) { p(c).edit().apply { p(c).all.keys.filter { it.startsWith("v_") }.forEach { remove(it) } }.apply() }
}

/** Erster Start: Konto wählen */
@Composable
fun AccountScreen() {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("🔗 Link-Zentrale", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Welches GitHub-Konto soll angezeigt werden?", color = Color(0xFFA9C3E0), fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(name, { name = it.trim() }, label = { Text("GitHub-Konto (Name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (name.isNotBlank()) Acc.set(ctx, name) }, enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8FD3FF), contentColor = Color(0xFF0A2540))) { Text("Weiter", fontWeight = FontWeight.Bold) }
        Text("Später änderbar unter 🛠 Werkzeuge → Konto.", color = Color(0xFFA9C3E0), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    }
}
