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

    /// <summary>Versionsnummer aus Tag „v1.21“ / „link-v1.21“ → 21</summary>
    public static int? TagCode(string tag) { var m = Regex.Match(tag, @"(\d+)$"); return m.Success ? int.Parse(m.Groups[1].Value) : null; }
}
