# 🔗 Anleitung: Projekt in der Link-Zentrale

Die **Link-Zentrale** (Handy-App + Windows-exe) zeigt für jedes Projekt automatisch:
Test-Link zum Weitergeben, QR-Code, Bewertungen (lesen/antworten), Downloads, ★-Schnitt, offene Fehler und Build-Status.

**Link-Zentrale laden (immer neueste Version, Updates aus dem Repo `Link-Zentrale-Updates`):**
Beim ersten Start fragt die App nach dem GitHub-Konto – im Code ist kein Konto und kein Schlüssel eingebaut. Der Token wird verschlüsselt gespeichert.

- 📱 Android: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates/releases/latest/download/Link-Zentrale.apk
- 💻 Windows: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates/releases/latest/download/Link-Zentrale.exe

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
- `FEEDBACK_TOKEN` – darf nur Issues anlegen (für Bewertung/Fehlerbericht aus der App des Projekts; die Link-Zentrale selbst braucht ihn nicht)
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

---

## C) Link-Zentrale aus jedem Projekt-Chat verbessern und erweitern

Jeder Chat darf die Link-Zentrale für **sein** Projekt verbessern. Zwei Wege:

### 1. Ohne Code: Datei `link-zentrale.json` ins **Update-Repo** des Projekts (öffentlich, Hauptordner)
```json
{
  "beschreibung": "Findet Geschenkideen in deinen Chats",
  "tester_text": "Bitte besonders die Suche nach Geburtstagen testen.",
  "links": [
    { "name": "📘 Handbuch", "url": "https://github.com/tinoknopp-rgb/WunschFinder-Updates#readme", "info": "Kurzanleitung" },
    { "name": "🎥 Video", "url": "https://…", "info": "So funktioniert's" }
  ]
}
```
→ Beschreibung steht im Projekt-Kasten, `tester_text` kommt in die Tester-Nachricht, `links` erscheinen als eigene Karten (Kopieren/Senden/QR). Wirkt sofort nach **↻** – ohne neue App-Version.

### 2. Mit Code: Repo `tinoknopp-rgb/Link-Zentrale` (privat)
| Bereich | Android (`app/src/main/java/de/tino/links/`) | Windows (`windows/`) |
|---|---|---|
| Konto, verschlüsselter Token | `Account.kt` | `Gh.cs` (Store) |
| Reine Logik + Tests | `app/src/test/…/LogicTest.kt` | `windows/Logic.cs` + `windows.Tests/` |
| Links, Projekt-Kästen, Download-Liste | `MainActivity.kt` | `MainForm.cs`, `Gh.cs` |
| Werkzeuge (Token, Repos, Secrets, Builds, Versionen) | `Tools.kt` | `MainForm.cs` (Reiter Werkzeuge), `Gh.cs` |
| Bewertungen lesen/antworten | `Reviews.kt` | `ReviewsForm.cs` |
| Statistik, Benachrichtigung, Widget, QR, Feedback, Absturzschutz | `Extras.kt` | `Extras.cs` |
| Deutsch/Englisch | `I18n.kt` + `app/src/main/assets/en.json` (gilt für beide) | `Extras.cs` (Tr) |

**Regeln:**
1. Immer **Android und Windows** gleich anpassen.
2. Kein Konto, keine Namen, keine Schlüssel fest in den Code schreiben – Konto kommt aus `USER`/`Gh.USER`.
3. Projekt-eigene Funktionen nur mit Bedingung auf den Projektnamen (`project == "WunschFinder"`) – nichts für andere Projekte kaputt machen.
4. Neue Texte auch in `en.json` eintragen.
5. Commit-Nachricht beginnt mit `[Projektname]`, Push auf `main`.
6. Der Build führt die Tests aus, baut Handy (Release) + PC und veröffentlicht automatisch `v1.N` im Repo `Link-Zentrale-Updates` – alle Link-Zentralen aktualisieren sich selbst. Build in Actions muss ✅ sein, sonst sofort reparieren.
