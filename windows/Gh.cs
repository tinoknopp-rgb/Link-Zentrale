using System.Diagnostics;
using System.Net.Http.Headers;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace LinkZentrale;

public record Link(string Name, string Url, string Info = "", bool Own = false, string Project = "");
public record Repo(string Full, string Name, bool Private, string Branch, string Html)
{
    public override string ToString() => (Private ? "🔒 " : "🌍 ") + Name;
}
public record RunInfo(long Id, string Title, string Status, string Url);
public record Rel(long Id, string Tag, string Name, bool Latest, List<string> Files);
public record AppUpdate(int Code, string Name, string Url);

/// <summary>Einstellungen in %APPDATA%\Link-Zentrale (Token mit Windows-Schutz verschlüsselt)</summary>
public static class Store
{
    static readonly string Dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Link-Zentrale");
    static string F(string n) => Path.Combine(Dir, n);

    public static string Token
    {
        get
        {
            try { return Encoding.UTF8.GetString(ProtectedData.Unprotect(File.ReadAllBytes(F("token.bin")), null, DataProtectionScope.CurrentUser)); }
            catch { return ""; }
        }
        set
        {
            Directory.CreateDirectory(Dir);
            if (string.IsNullOrWhiteSpace(value)) { try { File.Delete(F("token.bin")); } catch { } return; }
            File.WriteAllBytes(F("token.bin"), ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null, DataProtectionScope.CurrentUser));
        }
    }

    public static List<Link> Load(string name)
    {
        try { return JsonSerializer.Deserialize<List<Link>>(File.ReadAllText(F(name))) ?? new(); } catch { return new(); }
    }
    public static void Save(string name, List<Link> l)
    {
        Directory.CreateDirectory(Dir);
        File.WriteAllText(F(name), JsonSerializer.Serialize(l));
    }
}

public static class Gh
{
    public const string USER = "tinoknopp-rgb";
    public const string UPDATE_REPO = USER + "/HandyPutz-Updates";
    public const string TOKEN_URL = "https://github.com/settings/tokens/new?description=Link-Zentrale-PC&scopes=repo,workflow,delete_repo";

    static readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(60) };
    public static string Token = "";

    static Gh() { http.DefaultRequestHeaders.UserAgent.ParseAdd("Link-Zentrale-Windows"); }

    public record R(int Code, string Body, string Scopes);

    static async Task<R> Call(string method, string path, JsonNode? body = null, bool auth = true)
    {
        var url = path.StartsWith("http") ? path : "https://api.github.com" + path;
        using var req = new HttpRequestMessage(new HttpMethod(method), url);
        req.Headers.Accept.ParseAdd("application/vnd.github+json");
        req.Headers.Add("X-GitHub-Api-Version", "2022-11-28");
        if (auth && Token != "") req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", Token);
        if (body != null) req.Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json");
        using var res = await http.SendAsync(req);
        var txt = await res.Content.ReadAsStringAsync();
        var scopes = res.Headers.TryGetValues("X-OAuth-Scopes", out var v) ? string.Join(",", v) : "";
        return new R((int)res.StatusCode, txt, scopes);
    }

    static R Need(R r, string what)
    {
        if (r.Code is >= 200 and < 300) return r;
        string m;
        try { m = JsonNode.Parse(r.Body)?["message"]?.ToString() ?? ""; } catch { m = r.Body.Length > 120 ? r.Body[..120] : r.Body; }
        var hint = r.Code switch
        {
            401 => " – Token ungültig",
            403 or 404 => " – fehlt dem Token ein Haken (repo / workflow / delete_repo)?",
            _ => ""
        };
        throw new Exception($"{what} fehlgeschlagen ({r.Code}): {m}{hint}");
    }

    static JsonNode J(R r) => JsonNode.Parse(r.Body)!;

    // ---------------- Öffentliche Download-Links ----------------
    public static async Task<List<Link>> Downloads()
    {
        var r = await Call("GET", $"/users/{USER}/repos?per_page=100&sort=pushed", auth: false);
        if (r.Code is < 200 or >= 300) throw new Exception($"GitHub nicht erreichbar ({r.Code})");
        var outL = new List<Link>();
        foreach (var repo in J(r).AsArray())
        {
            if (repo!["private"]!.GetValue<bool>()) continue;
            var full = repo["full_name"]!.ToString();
            var rr = await Call("GET", $"/repos/{full}/releases/latest", auth: false);
            if (rr.Code is < 200 or >= 300) continue;
            var rel = J(rr);
            var assets = rel["assets"]!.AsArray();
            foreach (var a in assets)
            {
                var name = a!["name"]!.ToString();
                if (!Regex.IsMatch(name, @"\.(apk|zip|exe|msi|pdf)$", RegexOptions.IgnoreCase)) continue;
                if (name.StartsWith("Link-Zentrale")) continue;
                var size = a["size"]!.GetValue<long>();
                var relName = rel["name"]?.ToString();
                outL.Add(new Link(Icon(name) + " " + Path.GetFileNameWithoutExtension(name),
                    $"https://github.com/{full}/releases/latest/download/{name}",
                    $"{(string.IsNullOrEmpty(relName) ? rel["tag_name"] : relName)} • {Size(size)}", false, ProjectOf(name)));
            }
            if (assets.Count > 0)
            {
                var rn = repo["name"]!.ToString();
                var proj = outL.Select(l => l.Project).Distinct().FirstOrDefault(p => p != "" && rn.StartsWith(p)) ?? rn;
                outL.Add(new Link($"📄 {rn} – Infoseite", repo["html_url"]!.ToString(), "Anleitung & alle Versionen", false, proj));
            }
        }
        // Link-Zentrale selbst (fester Link, zeigt immer auf die neueste Version)
        var lz = await Call("GET", $"/repos/{UPDATE_REPO}/releases/tags/link-zentrale", auth: false);
        if (lz.Code is >= 200 and < 300)
            foreach (var a in J(lz)["assets"]!.AsArray())
            {
                var n = a!["name"]!.ToString();
                outL.Add(new Link(Icon(n) + " " + n, $"https://github.com/{UPDATE_REPO}/releases/download/link-zentrale/{n}",
                    (n.EndsWith(".exe") ? "Windows" : "Android") + " • " + Size(a["size"]!.GetValue<long>()), false, "Link-Zentrale"));
            }
        return outL;
    }

    static string Size(long b) => b > 1048576 ? $"{b / 1048576.0:0.0} MB" : $"{b / 1024} KB";

    /// <summary>„HandyPutz-1.4.apk“ → „HandyPutz“</summary>
    public static string ProjectOf(string file) => Regex.Replace(Path.GetFileNameWithoutExtension(file), @"[-_ ]?v?\d+(\.\d+)*$", "");

    /// <summary>Fertige Nachricht für Testpersonen</summary>
    public static string TestMsg(string project, List<Link> links)
    {
        var files = links.Where(l => !l.Name.Contains("Infoseite")).ToList();
        var sb = new StringBuilder($"🧪 Hallo! Bitte teste meine App „{project}“.\r\n\r\n");
        foreach (var l in files) sb.Append(l.Name[(l.Name.IndexOf(' ') + 1)..]).Append(":\r\n").Append(l.Url).Append("\r\n\r\n");
        if (files.Any(l => l.Url.EndsWith(".apk"))) sb.Append("📱 Android: Link öffnen → Datei laden → öffnen → installieren (falls gefragt: „Unbekannte Apps installieren“ erlauben).\r\n");
        if (files.Any(l => l.Url.EndsWith(".exe"))) sb.Append("💻 Windows: Datei laden → Doppelklick → bei der blauen Warnung „Weitere Informationen“ → „Trotzdem ausführen“.\r\n");
        var info = links.FirstOrDefault(l => l.Name.Contains("Infoseite"));
        if (info != null) sb.Append("ℹ️ Infos & Anleitung: ").Append(info.Url).Append("\r\n");
        sb.Append("\r\nUpdates kommen automatisch. Danke für deine Rückmeldung! 🙏");
        return sb.ToString();
    }

    static string Icon(string n) => Path.GetExtension(n).ToLower() switch { ".apk" => "📱", ".exe" or ".msi" => "💻", ".pdf" => "📄", _ => "📦" };

    // ---------------- Selbst-Update ----------------
    public static int CurrentCode => Assembly.GetExecutingAssembly().GetName().Version?.Minor ?? 0;

    public static async Task<AppUpdate?> SelfUpdate()
    {
        var r = await Call("GET", $"/repos/{UPDATE_REPO}/releases?per_page=30", auth: false);
        if (r.Code is < 200 or >= 300) return null;
        foreach (var o in J(r).AsArray())
        {
            var tag = o!["tag_name"]!.ToString();
            if (!tag.StartsWith("link-v")) continue;
            var m = Regex.Match(tag, @"(\d+)$");
            if (!m.Success) continue;
            var vc = int.Parse(m.Groups[1].Value);
            if (vc <= CurrentCode) return null;
            foreach (var a in o["assets"]!.AsArray())
                if (a!["name"]!.ToString().EndsWith(".exe"))
                    return new AppUpdate(vc, o["name"]?.ToString() ?? tag, a["browser_download_url"]!.ToString());
        }
        return null;
    }

    /// <summary>Lädt die neue exe, ersetzt die laufende nach dem Beenden und startet neu</summary>
    public static async Task InstallUpdate(AppUpdate u)
    {
        var me = Environment.ProcessPath!;
        var neu = Path.Combine(Path.GetTempPath(), "Link-Zentrale-neu.exe");
        var bytes = await http.GetByteArrayAsync(u.Url);
        await File.WriteAllBytesAsync(neu, bytes);
        var cmd = Path.Combine(Path.GetTempPath(), "link-zentrale-update.cmd");
        await File.WriteAllTextAsync(cmd,
            "@echo off\r\n" +
            "timeout /t 2 /nobreak >nul\r\n" +
            $"move /y \"{neu}\" \"{me}\" >nul\r\n" +
            $"start \"\" \"{me}\"\r\n" +
            "del \"%~f0\"\r\n", Encoding.Default);
        Process.Start(new ProcessStartInfo("cmd.exe", $"/c \"{cmd}\"") { CreateNoWindow = true, UseShellExecute = false });
        Application.Exit();
    }

    // ---------------- Werkzeuge (mit Token) ----------------
    public static async Task<string> Me()
    {
        var r = Need(await Call("GET", "/user"), "Anmeldung");
        return $"✅ Angemeldet als {J(r)["login"]} • Rechte: {(r.Scopes == "" ? "–" : r.Scopes)}";
    }

    public static async Task<List<Repo>> Repos()
    {
        var r = Need(await Call("GET", "/user/repos?per_page=100&sort=pushed&affiliation=owner"), "Repos laden");
        return J(r).AsArray().Select(o => new Repo(o!["full_name"]!.ToString(), o["name"]!.ToString(),
            o["private"]!.GetValue<bool>(), o["default_branch"]?.ToString() ?? "main", o["html_url"]!.ToString())).ToList();
    }

    public static async Task<string> CreateRepo(string name, string desc, bool priv)
    {
        var r = Need(await Call("POST", "/user/repos", new JsonObject { ["name"] = name, ["description"] = desc, ["private"] = priv, ["auto_init"] = true }), "Repo anlegen");
        return "✅ Repo angelegt: " + J(r)["html_url"];
    }

    public static async Task<string> DeleteRepo(Repo r) { Need(await Call("DELETE", $"/repos/{r.Full}"), "Repo löschen"); return $"🗑 {r.Full} gelöscht"; }

    public static async Task<List<string>> Folders(Repo r)
    {
        var t = await Call("GET", $"/repos/{r.Full}/git/trees/{r.Branch}?recursive=1");
        if (t.Code is < 200 or >= 300) return new();
        return J(t)["tree"]!.AsArray().Where(e => e!["type"]!.ToString() == "tree").Select(e => e!["path"]!.ToString()).ToList();
    }

    public static async Task<string> CreateFolder(Repo r, string path)
    {
        var p = path.Trim().Trim('/');
        Need(await Call("PUT", $"/repos/{r.Full}/contents/{p}/.gitkeep", new JsonObject { ["message"] = $"Ordner {p} angelegt", ["content"] = "", ["branch"] = r.Branch }), "Ordner anlegen");
        return $"✅ Ordner „{p}“ in {r.Name} angelegt";
    }

    public static async Task<string> DeleteFolder(Repo r, string path)
    {
        var p = path.Trim().Trim('/');
        var head = J(Need(await Call("GET", $"/repos/{r.Full}/git/ref/heads/{r.Branch}"), "Stand lesen"))["object"]!["sha"]!.ToString();
        var tree = J(Need(await Call("GET", $"/repos/{r.Full}/git/commits/{head}"), "Commit lesen"))["tree"]!["sha"]!.ToString();
        var all = J(Need(await Call("GET", $"/repos/{r.Full}/git/trees/{tree}?recursive=1"), "Dateien lesen"))["tree"]!.AsArray();
        var del = new JsonArray();
        foreach (var e in all)
            if (e!["type"]!.ToString() == "blob" && e["path"]!.ToString().StartsWith(p + "/"))
                del.Add(new JsonObject { ["path"] = e["path"]!.ToString(), ["mode"] = e["mode"]!.ToString(), ["type"] = "blob", ["sha"] = null });
        if (del.Count == 0) throw new Exception($"Ordner „{p}“ nicht gefunden oder leer");
        var n = del.Count;
        var newTree = J(Need(await Call("POST", $"/repos/{r.Full}/git/trees", new JsonObject { ["base_tree"] = tree, ["tree"] = del }), "Löschen vorbereiten"))["sha"]!.ToString();
        var commit = J(Need(await Call("POST", $"/repos/{r.Full}/git/commits", new JsonObject { ["message"] = $"Ordner {p} gelöscht", ["tree"] = newTree, ["parents"] = new JsonArray(head) }), "Commit"))["sha"]!.ToString();
        Need(await Call("PATCH", $"/repos/{r.Full}/git/refs/heads/{r.Branch}", new JsonObject { ["sha"] = commit }), "Speichern");
        return $"🗑 Ordner „{p}“ gelöscht ({n} Dateien)";
    }

    public static async Task<string> SetSecret(Repo r, string name, string value)
    {
        var k = J(Need(await Call("GET", $"/repos/{r.Full}/actions/secrets/public-key"), "Schlüssel holen"));
        var sealedBox = Sodium.SealedPublicKeyBox.Create(Encoding.UTF8.GetBytes(value), Convert.FromBase64String(k["key"]!.ToString()));
        Need(await Call("PUT", $"/repos/{r.Full}/actions/secrets/{name}", new JsonObject
        {
            ["encrypted_value"] = Convert.ToBase64String(sealedBox),
            ["key_id"] = k["key_id"]!.ToString()
        }), "Secret speichern");
        return $"✅ Secret {name} in {r.Name} gespeichert";
    }

    public static async Task<List<string>> Assets(Repo r)
    {
        var rel = await Call("GET", $"/repos/{r.Full}/releases/latest");
        if (rel.Code is < 200 or >= 300) return new();
        return J(rel)["assets"]!.AsArray().Select(a => a!["name"]!.ToString()).ToList();
    }

    public static async Task<List<RunInfo>> Runs(Repo r)
    {
        var t = await Call("GET", $"/repos/{r.Full}/actions/runs?per_page=8");
        if (t.Code is < 200 or >= 300) return new();
        return J(t)["workflow_runs"]!.AsArray().Select(o =>
        {
            var st = o!["conclusion"]?.ToString() switch
            {
                "success" => "✅", "failure" => "❌", "cancelled" => "⏹", "skipped" => "⏭",
                _ => o["status"]?.ToString() == "completed" ? "•" : "⏳"
            };
            var title = o["display_title"]?.ToString() ?? "";
            if (title.Length > 50) title = title[..50];
            return new RunInfo(o["id"]!.GetValue<long>(), $"#{o["run_number"]} {title}", st, o["html_url"]!.ToString());
        }).ToList();
    }

    public static async Task<string> Build(Repo r)
    {
        var a = J(Need(await Call("GET", $"/repos/{r.Full}/actions/workflows"), "Workflows lesen"))["workflows"]!.AsArray();
        if (a.Count == 0) throw new Exception($"{r.Name} hat keinen Build (Workflow)");
        var wf = a[0]!;
        Need(await Call("POST", $"/repos/{r.Full}/actions/workflows/{wf["id"]}/dispatches", new JsonObject { ["ref"] = r.Branch }), "Build starten");
        return $"🚀 Build „{wf["name"]}“ in {r.Name} gestartet";
    }

    public static async Task<string> Rerun(Repo r, RunInfo x) { Need(await Call("POST", $"/repos/{r.Full}/actions/runs/{x.Id}/rerun"), "Wiederholen"); return $"🔁 {x.Title} wird wiederholt"; }
    public static async Task<string> Cancel(Repo r, RunInfo x) { Need(await Call("POST", $"/repos/{r.Full}/actions/runs/{x.Id}/cancel"), "Abbrechen"); return $"⏹ {x.Title} abgebrochen"; }

    public static async Task<List<Rel>> Releases(Repo r)
    {
        var t = await Call("GET", $"/repos/{r.Full}/releases?per_page=30");
        if (t.Code is < 200 or >= 300) return new();
        var l = await Call("GET", $"/repos/{r.Full}/releases/latest");
        long latest = l.Code is >= 200 and < 300 ? J(l)["id"]!.GetValue<long>() : -1;
        return J(t).AsArray().Select(o =>
        {
            var tag = o!["tag_name"]!.ToString();
            var name = o["name"]?.ToString();
            return new Rel(o["id"]!.GetValue<long>(), tag, string.IsNullOrEmpty(name) ? tag : name, o["id"]!.GetValue<long>() == latest,
                o["assets"]!.AsArray().Select(a => a!["name"]!.ToString()).ToList());
        }).ToList();
    }

    public static async Task<string> MakeLatest(Repo r, Rel v)
    {
        Need(await Call("PATCH", $"/repos/{r.Full}/releases/{v.Id}", new JsonObject { ["make_latest"] = "true" }), "Als neueste markieren");
        return $"⭐ {v.Name} ist jetzt die neueste Version";
    }

    public static async Task<string> DeleteRelease(Repo r, Rel v)
    {
        Need(await Call("DELETE", $"/repos/{r.Full}/releases/{v.Id}"), "Version löschen");
        await Call("DELETE", $"/repos/{r.Full}/git/refs/tags/{v.Tag}");
        return $"🗑 {v.Name} gelöscht";
    }
}
