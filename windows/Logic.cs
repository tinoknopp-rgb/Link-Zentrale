using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace LinkZentrale;

/// <summary>Reine Logik ohne Oberfläche – wird mit Tests geprüft (windows.Tests)</summary>
public static class Logic
{
    /// <summary>„HandyPutz-1.4.apk“ → „HandyPutz“</summary>
    public static string ProjectOf(string file) => Regex.Replace(Path.GetFileNameWithoutExtension(file), @"[-_ ]?v?\d+(\.\d+)*$", "");

    public static int Stars(string t) => t.Count(ch => ch == '★');

    public static string Kind(string t) =>
        t.Contains("Bewertung", StringComparison.OrdinalIgnoreCase) || t.Contains('★') || t.Contains("⭐") ? "⭐" :
        t.Contains("Vorschlag", StringComparison.OrdinalIgnoreCase) || t.Contains("💡") ? "💡" :
        t.Contains("Fehler", StringComparison.OrdinalIgnoreCase) || t.Contains("Absturz", StringComparison.OrdinalIgnoreCase) || t.Contains("🐞") ? "🐞" : "💬";

    /// <summary>Alte Link-Zentrale-Einträge liegen im HandyPutz-Repo → nach Titel trennen</summary>
    public static bool Belongs(string? project, string title) =>
        project == null || (project == "Link-Zentrale" ? title.StartsWith("Link-Zentrale") : !title.StartsWith("Link-Zentrale"));

    /// <summary>„max/WunschFinder-Updates“ → „max/WunschFinder“ (dort laufen die Builds)</summary>
    public static string SrcRepo(string repo) => repo.EndsWith("-Updates") ? repo[..^8] : repo;

    /// <summary>Kleiner Hinweis unter Tester-Nachrichten</summary>
    public static string PromoLine(string appRepo) => $"Gesendet mit Link-Zentrale: https://github.com/{appRepo}";

    /// <summary>„android, GitHub Tools“ → ["android", "github-tools"]</summary>
    public static List<string> Topics(string s) =>
        s.Split(',').Select(t => t.Trim().ToLowerInvariant().Replace(' ', '-')).Where(t => t != "").Distinct().ToList();

    /// <summary>Versionsnummer aus Tag „v1.21“ / „link-v1.21“ → 21</summary>
    public static int? TagCode(string tag) { var m = Regex.Match(tag, @"(\d+)$"); return m.Success ? int.Parse(m.Groups[1].Value) : null; }
}

// ======================= Auftrag von Claude =======================
// EIN Code-Block (LZ-AUFTRAG), den die App selbst auf die richtigen Masken verteilt. Format: AUFTRAG.md

public record Datei(string Pfad, string? Inhalt, string? Base64, bool Loeschen, string Mode);

public record Schritt(string Aktion, string Repo, JsonObject O)
{
    public string S(string k) => O[k] is JsonNode n ? n.ToString() : "";
    public bool? B(string k) => O[k] is JsonValue v && v.TryGetValue<bool>(out var b) ? b : null;
    public bool Has(string k) => O.ContainsKey(k);
    public List<Datei> Dateien => O["dateien"] is JsonArray a
        ? a.Select(x => x as JsonObject).Where(x => x != null).Select(d =>
        {
            var p = (d!["pfad"]?.ToString() ?? "").Trim().Trim('/');
            var exec = d["ausfuehrbar"] is JsonValue ev && ev.TryGetValue<bool>(out var eb) && eb;
            var del = d["loeschen"] is JsonValue dv && dv.TryGetValue<bool>(out var db) && db;
            return new Datei(p, d.ContainsKey("inhalt") ? d["inhalt"]?.ToString() ?? "" : null, d.ContainsKey("base64") ? d["base64"]?.ToString() : null, del, Auftrag.Mode(p, exec));
        }).ToList()
        : new();
}

public record Plan(string Titel, List<Schritt> Schritte);

public static class Auftrag
{
    public const string MARKE = "LZ-AUFTRAG";
    public const int VERSION = 1;
    public static readonly string[] AKTIONEN = { "repo", "dateien", "ordner", "secret", "einstellungen", "build", "neueste", "version_loeschen", "text" };

    /// <summary>Nimmt den ganzen kopierten Text (auch mit ``` oder Chat-Text drumherum)</summary>
    public static Plan Parse(string text, string owner)
    {
        int s = text.IndexOf('{'), e = text.LastIndexOf('}');
        if (s < 0 || e <= s) throw new Exception("Kein Auftrag gefunden – bitte den ganzen Code-Block von Claude kopieren");
        JsonObject o;
        try { o = JsonNode.Parse(text[s..(e + 1)]) as JsonObject ?? throw new Exception(); }
        catch { throw new Exception("Auftrag unvollständig kopiert – bitte den Code-Block nochmal ganz kopieren"); }
        if (o["version"] is JsonValue vv && vv.TryGetValue<int>(out var ver) && ver > VERSION) throw new Exception("Auftrag ist neuer als diese App – erst Link-Zentrale aktualisieren");
        if (o["schritte"] is not JsonArray arr) throw new Exception("Auftrag ohne „schritte“");
        var list = new List<Schritt>();
        for (int i = 0; i < arr.Count; i++)
        {
            if (arr[i] is not JsonObject st) throw new Exception($"Schritt {i + 1} ist kein Objekt");
            var a = (st["aktion"]?.ToString() ?? "").Trim().ToLowerInvariant();
            if (!AKTIONEN.Contains(a)) throw new Exception($"Schritt {i + 1}: unbekannte Aktion „{a}“ – erst Link-Zentrale aktualisieren");
            var r = (st["repo"]?.ToString() ?? "").Trim();
            if (a != "text" && r == "") throw new Exception($"Schritt {i + 1} ({a}): „repo“ fehlt");
            if (r != "" && owner == "" && !r.Contains('/')) throw new Exception("Erst Konto eintragen (👤 Konto)");
            var step = new Schritt(a, r == "" ? "" : FullRepo(owner, r), st);
            switch (a)
            {
                case "dateien": if (step.Dateien.Count == 0 || step.Dateien.Any(d => d.Pfad == "")) throw new Exception($"Schritt {i + 1}: Dateien ohne Pfad"); break;
                case "secret": if (step.S("name") == "" || step.S("wert") == "") throw new Exception($"Schritt {i + 1}: Secret ohne Name/Wert"); break;
                case "ordner": if (step.S("pfad") == "") throw new Exception($"Schritt {i + 1}: Ordner ohne Pfad"); break;
                case "neueste": case "version_loeschen": if (step.S("tag") == "") throw new Exception($"Schritt {i + 1}: „tag“ fehlt"); break;
            }
            list.Add(step);
        }
        if (list.Count == 0) throw new Exception("Auftrag hat keine Schritte");
        var titel = o["titel"]?.ToString() ?? "";
        return new Plan(titel == "" ? "Auftrag" : titel, list);
    }

    /// <summary>„MeinTV“ → „konto/MeinTV“, „x/y“ bleibt</summary>
    public static string FullRepo(string owner, string r)
    {
        var t = r.Replace("https://github.com/", "").Trim().Trim('/');
        return t.Contains('/') ? t : $"{owner}/{t}";
    }

    public static string Short(string full) => full.Contains('/') ? full[(full.IndexOf('/') + 1)..] : full;

    /// <summary>gradlew / *.sh brauchen das Ausführ-Recht</summary>
    public static string Mode(string path, bool exec) => exec || path.Split('/').Last() == "gradlew" || path.EndsWith(".sh") ? "100755" : "100644";

    /// <summary>„@token“ = gespeicherter Token, „@NAME“ = Wert aus dem Tresor, sonst der Wert selbst</summary>
    public static string? Ref(string wert) => wert.StartsWith("@") ? wert[1..] : null;

    public static List<string> Needs(Plan p) => p.Schritte.Where(s => s.Aktion == "secret").Select(s => Ref(s.S("wert")))
        .Where(n => n != null && !n.Equals("token", StringComparison.OrdinalIgnoreCase)).Select(n => n!).Distinct().ToList();

    public static string Describe(Schritt s)
    {
        var r = Short(s.Repo);
        switch (s.Aktion)
        {
            case "repo": return $"➕ Repo {r} anlegen (" + (s.B("oeffentlich") == true ? "🌍 öffentlich" : "🔒 privat") + ")";
            case "dateien":
                var d = s.Dateien;
                return $"📄 {d.Count} Datei(en) → {r}: " + string.Join(", ", d.Take(3).Select(x => (x.Loeschen ? "🗑 " : "") + x.Pfad)) + (d.Count > 3 ? $" … (+{d.Count - 3})" : "");
            case "ordner": return $"📂 Ordner {s.S("pfad")} in {r}";
            case "secret":
                var rf = Ref(s.S("wert"));
                return $"🔐 Secret {s.S("name")} → {r}" + (rf == null ? "" : rf.Equals("token", StringComparison.OrdinalIgnoreCase) ? " (gespeicherter Token)" : $" (aus Tresor: {rf})");
            case "einstellungen":
                var parts = new List<string>();
                if (s.B("oeffentlich") is bool pub) parts.Add(pub ? "🌍 öffentlich" : "🔒 privat");
                if (s.Has("beschreibung")) parts.Add("Beschreibung");
                if (s.O["themen"] is JsonArray th) parts.Add($"{th.Count} Themen");
                if (s.Has("webseite")) parts.Add("Webseite");
                return $"⚙ {r}: " + string.Join(", ", parts);
            case "build": return $"🚀 Build in {r} starten";
            case "neueste": return $"⭐ {s.S("tag")} in {r} als neueste Version";
            case "version_loeschen": return $"🗑 Version {s.S("tag")} in {r} löschen";
            case "text": var t = s.S("titel"); return "📝 " + (t == "" ? "Text zum Kopieren" : t);
            default: return s.Aktion;
        }
    }

    /// <summary>Schritte, die man nicht einfach rückgängig machen kann → extra nachfragen</summary>
    public static List<string> Warnings(Plan p) => p.Schritte.Where(s =>
        s.Aktion == "version_loeschen" || (s.Aktion == "einstellungen" && s.B("oeffentlich") == true) ||
        (s.Aktion == "dateien" && s.Dateien.Any(d => d.Loeschen))).Select(Describe).ToList();
}
