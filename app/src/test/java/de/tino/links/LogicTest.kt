package de.tino.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Schnelle Prüfung der Kern-Logik – läuft bei jedem Build. Schlägt sie fehl, wird nichts veröffentlicht. */
class LogicTest {
    @Test fun projektnameAusDatei() {
        assertEquals("HandyPutz", projectOf("HandyPutz.apk"))
        assertEquals("HandyPutz", projectOf("HandyPutz-1.4.apk"))
        assertEquals("WunschFinder", projectOf("WunschFinder_v2.apk"))
        assertEquals("Link-Zentrale", projectOf("Link-Zentrale.exe"))
    }

    @Test fun sterneZaehlen() {
        assertEquals(4, stars("[⭐ Bewertung ★★★★☆] super"))
        assertEquals(0, stars("[🐞 Fehler] stürzt ab"))
    }

    @Test fun artErkennen() {
        assertEquals("⭐", kind("[⭐ Bewertung ★★★★★] toll"))
        assertEquals("💡", kind("[💡 Vorschlag] mehr Farben"))
        assertEquals("🐞", kind("[🐞 Absturz] NullPointer"))
        assertEquals("💬", kind("Frage"))
    }

    @Test fun eintraegeTrennen() {
        assertTrue(belongs("Link-Zentrale", "Link-Zentrale: [⭐ Bewertung ★★★★★]"))
        assertFalse(belongs("HandyPutz", "Link-Zentrale: [⭐ Bewertung ★★★★★]"))
        assertTrue(belongs("HandyPutz", "[⭐ Bewertung ★★★★★] gut"))
        assertTrue(belongs(null, "egal"))
    }

    @Test fun quellRepo() {
        assertEquals("max/WunschFinder", srcRepo("WunschFinder", "max/WunschFinder-Updates"))
        assertEquals("max/Link-Zentrale", srcRepo("Link-Zentrale", "max/Link-Zentrale-Updates"))
    }
}

class PromoTest {
    @Test fun hinweisZeile() =
        assertEquals("Gesendet mit Link-Zentrale: https://github.com/max/Link-Zentrale-Updates", promoLine("max/Link-Zentrale-Updates"))
}
