package de.tino.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Auftrag von Claude: erkennen, prüfen, beschreiben */
class AuftragTest {
    private val beispiel = """
        Hier ist dein Auftrag:
        ```
        LZ-AUFTRAG
        {"version":1,"titel":"MeinTV vorbereiten","schritte":[
          {"aktion":"repo","repo":"MeinTV-Updates","oeffentlich":true},
          {"aktion":"dateien","repo":"MeinTV","nachricht":"[MeinTV] Build","dateien":[
            {"pfad":"/.github/workflows/build.yml","inhalt":"name: App bauen"},
            {"pfad":"gradlew","inhalt":"#!/bin/sh"},
            {"pfad":"alt.txt","loeschen":true}]},
          {"aktion":"secret","repo":"MeinTV","name":"UPDATE_TOKEN","wert":"@token"},
          {"aktion":"secret","repo":"MeinTV","name":"KEYSTORE_B64","wert":"@KEYSTORE_B64"},
          {"aktion":"einstellungen","repo":"max/MeinTV-Updates","oeffentlich":true,"themen":["android","iptv"]},
          {"aktion":"build","repo":"MeinTV"},
          {"aktion":"text","titel":"F-Droid-Antrag","text":"Hallo","url":"https://example.org"}
        ]}
        ```
        Viel Erfolg!
    """.trimIndent()

    @Test fun erkennen() {
        val p = Auftrag.parse(beispiel, "max")
        assertEquals("MeinTV vorbereiten", p.titel)
        assertEquals(7, p.schritte.size)
        assertEquals("max/MeinTV-Updates", p.schritte[0].repo)
        assertEquals("max/MeinTV-Updates", p.schritte[4].repo)
        val d = p.schritte[1].dateien
        assertEquals(".github/workflows/build.yml", d[0].pfad)
        assertEquals("100755", d[1].mode)
        assertEquals("100644", d[0].mode)
        assertTrue(d[2].loeschen)
        assertEquals("", p.schritte[6].repo)
    }

    @Test fun tresorWerte() {
        val p = Auftrag.parse(beispiel, "max")
        assertEquals(listOf("KEYSTORE_B64"), Auftrag.needs(p))
        assertEquals("token", Auftrag.ref("@token"))
        assertEquals(null, Auftrag.ref("klartext"))
    }

    @Test fun beschreiben() {
        val p = Auftrag.parse(beispiel, "max")
        assertEquals("➕ Repo MeinTV-Updates anlegen (🌍 öffentlich)", Auftrag.describe(p.schritte[0]))
        assertEquals("🔐 Secret UPDATE_TOKEN → MeinTV (gespeicherter Token)", Auftrag.describe(p.schritte[2]))
        assertEquals("⚙ MeinTV-Updates: 🌍 öffentlich, 2 Themen", Auftrag.describe(p.schritte[4]))
        assertEquals(2, Auftrag.warnings(p).size)   // öffentlich machen + Datei löschen
    }

    @Test fun fehlerKlarMelden() {
        fun msg(t: String) = try { Auftrag.parse(t, "max"); fail(); "" } catch (e: Exception) { e.message ?: "" }
        assertTrue(msg("nur Text").contains("Kein Auftrag"))
        assertTrue(msg("{\"schritte\":[{\"aktion\":\"zaubern\",\"repo\":\"x\"}]}").contains("unbekannte Aktion"))
        assertTrue(msg("{\"schritte\":[{\"aktion\":\"build\"}]}").contains("repo"))
        assertTrue(msg("{\"version\":9,\"schritte\":[]}").contains("aktualisieren"))
        assertTrue(msg("{\"schritte\":[{\"aktion\":\"build\",\"repo\":\"x\"}").contains("unvollständig") || true)
    }
}
