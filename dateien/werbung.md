# 📣 Link-Zentrale bekannt machen – fertige Texte

**Links für alle Beiträge**
- Projekt (Code): https://github.com/tinoknopp-rgb/Link-Zentrale *(erst öffentlich schalten – Link-Zentrale → 🛠 Werkzeuge → Maske 9)*
- Download + Anleitung: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates
- APK: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates/releases/latest/download/Link-Zentrale.apk

**Reihenfolge-Tipp:** 1. Repo öffentlich + Themen + Screenshots → 2. Obtainium/IzzyOnDroid → 3. Reddit (EN) → 4. Show HN → 5. deutsche Foren → 6. Product Hunt (Dienstag/Mittwoch, 9:01 Uhr US-Zeit) → 7. Video.

---

## 1) Repo-Einstellungen (Maske 9)
- **Beschreibung:** `Your GitHub releases, testers and reviews in one app – Android + Windows, self-updating, no account built in.`
- **Themen:** `android, windows, github, github-releases, release-management, beta-testing, developer-tools, kotlin, jetpack-compose, dotnet, qr-code, open-source`
- **Webseite:** `https://github.com/tinoknopp-rgb/Link-Zentrale-Updates`

**Screenshots** (am Handy machen, mir schicken → ich baue sie in die README ein): 1. Startseite mit Projekt-Kästen · 2. Tester-Nachricht/QR-Code · 3. Bewertungen · 4. Werkzeuge.

---

## 2) Verzeichnisse

### Obtainium (sofort, nichts einreichen nötig)
Link ist schon in beiden READMEs. Beitrag in r/Obtainium möglich (Text wie Reddit unten, kürzer).

### IzzyOnDroid (F-Droid-kompatibles Verzeichnis) – Anfrage als Issue
Ort: https://codeberg.org/IzzyOnDroid/repodata/issues/new (Codeberg-Konto nötig, Vorlage „App Request“, Titel `[AppRequest] Link-Zentrale`)
> **Title:** Request: Link-Zentrale (de.tino.links)
> **Body:**
> Source: https://github.com/tinoknopp-rgb/Link-Zentrale
> Releases with APK: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates/releases (asset `Link-Zentrale.apk`)
> License: MIT
> Description: Shows all GitHub releases of an account with share-ready download links, QR codes, reviews (Issues), download counts and build status; optional token-based tools for repos, Actions secrets, builds and releases.
> Anti-features: none known; the app checks GitHub for its own updates (can be ignored when installed via your repo).
> I'm the developer and happy to adjust anything.

### F-Droid (später, strengere Prüfung)
„Request For Packaging“: https://gitlab.com/fdroid/rfp/-/issues – gleicher Text wie oben. Hinweis: F-Droid baut selbst aus dem Code; die Selbst-Update-Funktion wird dort meist als „Anti-Feature“ markiert.

### Awesome-Listen (Pull Request am Handy über GitHub)
Eintrag zum Einfügen:
```
- [Link-Zentrale](https://github.com/tinoknopp-rgb/Link-Zentrale) - Manage GitHub releases, testers, reviews (Issues), Actions secrets and builds from Android/Windows. ![Kotlin]
```
Passende Listen: `awesome-android-apps` (Kategorie Development/Tools), `awesome-github` (Apps/Tools), `awesome-selfhosted`-ähnliche Dev-Listen, `open-source-android-apps` (Development).

---

## 3) Beiträge

### Reddit (Englisch) – r/androidapps, r/SideProject, r/github, r/opensource
> **Title:** I built Link-Zentrale – manage GitHub releases, beta testers and reviews from your phone (open source, Android + Windows)
>
> I publish my own apps via GitHub Releases and got tired of digging for download links, copying them into messages and checking Issues on the go. So I built **Link-Zentrale**:
>
> • Every public repo with a release becomes a card: one tap sends testers a ready message (link + install guide) or shows a QR code
> • Reviews = GitHub Issues: read, reply, close – with unread counter and notifications
> • Downloads, ★ average, open bugs and last build status at a glance
> • With your own token: create repos, set **Actions secrets** (encrypted), upload files, start/re-run builds, manage releases, toggle public/private
> • Self-updating, widget, German/English, Windows version included
> • No account or key built in – you pick the account, your token stays encrypted on your device
>
> Download & guide: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates
> Code (MIT): https://github.com/tinoknopp-rgb/Link-Zentrale
>
> Feedback very welcome – there's a ⭐ button in the app that opens an issue.

### Reddit (Deutsch) – r/de_EDV, r/Android_DE
> **Titel:** Ich hab mir eine App gebaut, um GitHub-Releases, Tester und Bewertungen am Handy zu verwalten – kostenlos & Open Source
>
> Ich veröffentliche meine eigenen Apps über GitHub und war es leid, Download-Links zu suchen und Tester-Nachrichten zusammenzukopieren. Deshalb gibt es jetzt die **Link-Zentrale** (Android + Windows):
> – Jedes Projekt mit Release als Kasten: Test-Link mit Installationsanleitung per Tipp senden oder als QR-Code zeigen
> – Bewertungen (GitHub-Issues) lesen und beantworten, mit Zähler und Benachrichtigung
> – Downloads, Sterne, offene Fehler und Build-Status auf einen Blick
> – Mit eigenem Token: Repos anlegen, Secrets setzen, Dateien hochladen, Builds starten, Versionen verwalten
> – Aktualisiert sich selbst, Widget, Deutsch/Englisch, kein Konto fest eingebaut
>
> Download & Anleitung: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates
> Über Rückmeldungen freue ich mich!

### Hacker News – „Show HN“ (https://news.ycombinator.com/submit)
> **Title:** Show HN: Link-Zentrale – GitHub releases, testers and issues as a mobile dashboard
> **URL:** https://github.com/tinoknopp-rgb/Link-Zentrale
> **Erster Kommentar (direkt danach selbst schreiben):**
> I distribute my side-project apps through GitHub Releases. Link-Zentrale turns every public repo with a release into a card with share-ready tester messages and QR codes, shows Issues as reviews (with replies), download counts and CI status. With a personal token it can also create repos, set Actions secrets (libsodium sealed box), upload files and re-run workflows. Kotlin/Compose on Android, .NET WinForms on Windows, both built and released by one Actions workflow. Nothing is hard-coded: the account is chosen at first start, the token is stored with EncryptedSharedPreferences/DPAPI. Happy to hear what's missing.

### Product Hunt (https://www.producthunt.com/posts/new)
- **Name:** Link-Zentrale
- **Tagline (60 Zeichen):** `GitHub releases, testers & reviews in one app`
- **Beschreibung:** Turn every GitHub release into a share-ready tester link with QR code, read and answer reviews (Issues), watch downloads and builds – and manage repos, secrets and releases from your phone. Free, open source, Android + Windows.
- **Themen:** Developer Tools, Android, GitHub, Open Source
- **Galerie:** die 4 Screenshots + Video

### dev.to-Artikel (Gerüst)
> **Title:** How I manage my app releases and beta testers from my phone with GitHub
> 1. Problem: Releases, Links, Tester-Nachrichten, Issues verstreut
> 2. Idee: GitHub Releases + Issues als „App-Store-Backend“
> 3. Was die App zeigt (Screenshots)
> 4. Technik: ein Workflow baut Android + Windows, Selbst-Update über Releases, Secrets per libsodium
> 5. Download + Code-Link, Bitte um Feedback
> Tags: `android, github, opensource, productivity`

### Deutsche Foren – android-hilfe.de (Bereich „Eigene Apps“), ComputerBase („Software – Eigene Projekte“)
→ Text wie „Reddit (Deutsch)“, plus 2–3 Screenshots anhängen.

### Mastodon/X
> Ich hab die Link-Zentrale veröffentlicht 🔗 GitHub-Releases, Tester-Links mit QR-Code, Bewertungen & Builds – alles in einer App für Android + Windows. Kostenlos & Open Source: https://github.com/tinoknopp-rgb/Link-Zentrale-Updates #Android #GitHub #OpenSource #IndieDev

---

## 3b) Weitere Stellen zum Hochladen
- **Uptodown:** https://developers.uptodown.com · **APKPure:** https://developer.apkpure.com · **Samsung Galaxy Store:** https://seller.samsungapps.com
- **winget:** GitHub → Link-Zentrale → Actions → „winget anmelden“ → Run workflow
- **Datenschutzerklärung:** https://github.com/tinoknopp-rgb/Link-Zentrale-Updates/blob/main/DATENSCHUTZ.md

---

## 4) Video (30 Sekunden, YouTube Shorts / TikTok / Reels) – Bildschirmaufnahme am Handy
| Sek. | Bild | Text-Einblendung |
|---|---|---|
| 0–3 | Startseite mit Projekt-Kästen | „Alle meine Apps auf einen Blick“ |
| 3–9 | „📤 An Tester senden“ → WhatsApp öffnet sich mit fertiger Nachricht | „Tester einladen: 1 Tipp“ |
| 9–13 | 🔳 QR-Code | „…oder einfach scannen lassen“ |
| 13–19 | 💬 Bewertungen → antworten | „Bewertungen lesen & beantworten“ |
| 19–25 | 🛠 Werkzeuge → Secret speichern / Build starten | „GitHub-Werkzeuge in der Tasche“ |
| 25–30 | Logo + Link | „Kostenlos & Open Source – Link in der Beschreibung“ |
Musik: ruhig/elektronisch aus der Plattform-Bibliothek. Beschreibung: Download-Link + `#android #github #opensource`.
