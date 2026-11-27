# Regeln für Claude-Chats in diesem Repo (Link-Zentrale)

- Jeder Projekt-Chat darf die Link-Zentrale für sein Projekt verbessern/erweitern.
- **Kein Konto, keine Namen, keine Schlüssel fest im Code** (App soll öffentlich gehen können). Konto = `USER` (Android) / `Gh.USER` (Windows), App-Update-Repo kommt aus dem Build.
- Neue Logik möglichst testbar machen und Tests ergänzen (`app/src/test/…/LogicTest.kt`, `windows.Tests/`).
- Immer **Android (`app/`) und Windows (`windows/`) gleich** anpassen; neue Texte zusätzlich in `app/src/main/assets/en.json`.
- Projekt-eigene Funktionen nur mit Bedingung auf den Projektnamen – nichts für andere Projekte kaputt machen.
- Commit-Nachricht beginnt mit `[Projektname]`, Push auf `main`. Build (Actions) muss ✅ sein, sonst sofort reparieren.
- Danach die geänderten Dateien im Claude-Projekt unter `link-zentrale-code/…` aktualisieren (Spiegel für alle Chats, siehe `link-zentrale-code/README.md`).
- Ohne Code-Änderung: `link-zentrale.json` ins öffentliche Update-Repo des Projekts (siehe `LINK-ZENTRALE.md`, Teil C).
