# 🔗 Anleitung: Projekt in der Link-Zentrale

Die **Link-Zentrale** (Handy-App + Windows-exe) zeigt für jedes Projekt automatisch:
Test-Link zum Weitergeben, QR-Code, Bewertungen (lesen/antworten), Downloads, ★-Schnitt, offene Fehler und Build-Status.

**Link-Zentrale laden (immer neueste Version):**
- 📱 Android: https://github.com/tinoknopp-rgb/HandyPutz-Updates/releases/download/link-zentrale/Link-Zentrale.apk
- 💻 Windows: https://github.com/tinoknopp-rgb/HandyPutz-Updates/releases/download/link-zentrale/Link-Zentrale.exe

---

## A) Damit ein Projekt dort erscheint (Regeln für jedes Projekt)

| Was | So muss es sein | Beispiel |
|---|---|---|
| Quell-Repo (Code, privat) | Name = Projektname | `WunschFinder` |
| Update-Repo (öffentlich) | Projektname + `-Updates` | `WunschFinder-Updates` |
| Release im Update-Repo | als **neueste Version** markiert, mit Datei | Tag `v1.5` |
| Dateiname | `<Projektname>.apk` / `.exe` / `.zip` / `.pdf` | `WunschFinder.apk` |
| Bewertungen / Fehler | als **Issues** im Update-Repo, Titel `[⭐ Bewertung ★★★★☆] …`, `[💡 Vorschlag] …`, `[🐞 Fehler] …` | – |
| Build-Status | GitHub-Actions-Workflow im Quell-Repo | `.github/workflows/build.yml` |

**Secrets im Quell-Repo** (Eintragen geht in der Link-Zentrale → 🛠 Werkzeuge → Maske 4):
- `UPDATE_TOKEN` – darf ins Update-Repo veröffentlichen
- `FEEDBACK_TOKEN` – darf nur Issues anlegen (für Bewertung/Fehlerbericht aus der App)
- `KEYSTORE_B64` + `KEYSTORE_PASS` – fester Signatur-Schlüssel (Android), damit Updates drüber installieren

Danach in der Link-Zentrale oben **↻** → das Projekt erscheint mit eigenem Kasten.

---

## B) Bedienung der Link-Zentrale

1. **Projekt-Kasten** → **📤 An Tester senden** (fertige Nachricht mit Link + Installationsanleitung) · **📋 Nachricht kopieren** · **🔳 QR-Code**
2. **💬 Bewertungen** (Zahl = neue Einträge) → Eintrag antippen → **📨 Antworten** / **✔ Erledigt**. Antworten sieht der Tester in seiner App.
3. Statuszeile im Kasten: ✅/❌ Build · ⬇ Downloads · ★ Schnitt · 🐞 offene Fehler (antippen = Build öffnen)
4. **🛠 Werkzeuge**: Token · Repo & alle Links · neues Repo · Secret · Ordner · Builds neu starten · Versionen verwalten · Repo löschen
5. **⭐** oben = eigenes Feedback · 🇩🇪/🇬🇧 = Sprache · Widget auf dem Startbildschirm · Benachrichtigung bei neuer Bewertung oder fehlgeschlagenem Build
6. Updates kommen automatisch beim Öffnen (Handy: „Jetzt aktualisieren“, PC: Knopf **⬆ Update**)
