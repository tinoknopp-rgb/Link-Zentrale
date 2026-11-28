# 📥 LZ-AUFTRAG – ein Code-Block für alles (Regeln für Claude-Chats)

Der Nutzer kopiert **einen** Code-Block komplett und fügt ihn in der Link-Zentrale ein
(🛠 Werkzeuge → **📥 Auftrag von Claude** → „Aus Zwischenablage einfügen“ → **▶ Ausführen**).
Die App erkennt selbst, was in welche Maske gehört, zeigt vorher alle Schritte und führt sie dann mit dem gespeicherten Token aus.

**Für Claude:** Alles, was auf GitHub passieren soll (Dateien, Repos, Secrets, Builds, Versionen, Einstellungen, Veröffentlichen),
**nicht** Zeile für Zeile erklären, sondern als EINEN solchen Block ausgeben. Darüber höchstens 1–2 Sätze, was er bewirkt.

## Aufbau

````
```
LZ-AUFTRAG
{
  "version": 1,
  "titel": "MeinTV: Update 1.5 vorbereiten",
  "schritte": [
    { "aktion": "repo", "repo": "MeinTV-Updates", "oeffentlich": true, "beschreibung": "Updates für MeinTV" },
    { "aktion": "dateien", "repo": "MeinTV", "nachricht": "[MeinTV] Suche verbessert", "dateien": [
        { "pfad": "app/src/main/java/de/tino/meintv/Suche.kt", "inhalt": "…ganzer Dateiinhalt…" },
        { "pfad": "dateien/logo.png", "base64": "iVBORw0…" },
        { "pfad": "alt/Unnoetig.kt", "loeschen": true }
    ]},
    { "aktion": "secret", "repo": "MeinTV", "name": "UPDATE_TOKEN", "wert": "@token" },
    { "aktion": "secret", "repo": "MeinTV", "name": "KEYSTORE_B64", "wert": "@KEYSTORE_B64" },
    { "aktion": "build", "repo": "MeinTV" },
    { "aktion": "text", "titel": "Text für F-Droid", "text": "…zum Kopieren…", "url": "https://gitlab.com/fdroid/rfp/-/issues/new" }
  ]
}
```
````

## Aktionen

| aktion | Felder | Wirkung (Maske) |
|---|---|---|
| `repo` | `repo`, `oeffentlich` (Standard: privat), `beschreibung` | Repo anlegen (3) – gibt es es schon, wird übersprungen |
| `dateien` | `repo`, `nachricht`, `dateien[]` mit `pfad` + `inhalt` (Text) **oder** `base64` (Bilder/Binär) **oder** `loeschen: true`; optional `ausfuehrbar: true` | Alle Dateien in **einem** Commit (6) – ganze Dateien, keine Ausschnitte. `gradlew`/`*.sh` werden automatisch ausführbar |
| `ordner` | `repo`, `pfad` | Ordner anlegen (5) |
| `secret` | `repo`, `name`, `wert` | Secret (4). **Nie echte Schlüssel in den Auftrag!** `"@token"` = gespeicherter Token, `"@NAME"` = Wert aus dem Tresor der App (wird beim ersten Mal abgefragt und verschlüsselt gemerkt) |
| `einstellungen` | `repo`, optional `oeffentlich`, `beschreibung`, `themen` [ ], `webseite` | Repo-Einstellungen (9) – nur angegebene Felder ändern sich |
| `build` | `repo` | Build starten (7) |
| `neueste` | `repo`, `tag` | Version als neueste markieren (8) |
| `version_loeschen` | `repo`, `tag` | Version löschen (8) |
| `text` | `titel`, `text`, `url` | Für Seiten außerhalb von GitHub (F-Droid, IzzyOnDroid, winget, Foren …): zeigt Knöpfe **📋 Kopieren** und **↗ Öffnen** |

## Regeln
- `repo` ohne Konto (`MeinTV`) → das in der App eingestellte Konto. Kein Konto/Name fest einbauen.
- Dateien immer **vollständig**, UTF-8, JSON richtig escapen (`\n`, `\"`, `\\`).
- Schritte laufen der Reihe nach; beim ersten Fehler stoppt die App und zeigt ihn an.
- Vor „öffentlich machen“, „Version löschen“ und „Datei löschen“ fragt die App extra nach.
- Repos löschen geht absichtlich **nicht** per Auftrag (nur Maske 10).
- Unbekannte Aktion / `version` größer als die App kennt → App meldet „erst Link-Zentrale aktualisieren“.
- Sehr große Aufträge (> ca. 5 MB) besser auf mehrere Blöcke aufteilen.
