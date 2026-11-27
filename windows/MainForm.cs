using System.Diagnostics;

namespace LinkZentrale;

static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}

public class MainForm : Form
{
    internal static readonly Color BG = Color.FromArgb(10, 37, 64), CARD = Color.FromArgb(17, 50, 90), INPUT = Color.FromArgb(8, 28, 50),
        SOFT = Color.FromArgb(169, 195, 224), BLUE = Color.FromArgb(143, 211, 255), PINK = Color.FromArgb(255, 92, 138), GREEN = Color.FromArgb(37, 211, 102);
    internal const int W = 700;

    readonly Label status = new();
    readonly Button btnUpdate;
    readonly FlowLayoutPanel linksPanel = Stack(), repoLinks = Stack(), runsPanel = Stack(), relsPanel = Stack();
    readonly ComboBox cbRepo = Combo(), cbFolder = Combo();
    readonly List<Label> targets = new();

    List<Link> links = Store.Load("cache.json"), own = Store.Load("own.json");
    List<Repo> repos = new();
    Repo? repo;
    List<string> assets = new();
    AppUpdate? update;
    Dictionary<string, int> badges = new();

    static string? RepoOf(string proj, List<Link> ls) =>
        proj == "Link-Zentrale" ? Gh.USER + "/Link-Zentrale"
        : ls.Select(l => System.Text.RegularExpressions.Regex.Match(l.Url, @"github\.com/([^/]+/[^/]+)/releases")).FirstOrDefault(m => m.Success)?.Groups[1].Value;

    async Task CountBadges()
    {
        var m = new Dictionary<string, int>();
        foreach (var g in links.GroupBy(l => l.Project))
        {
            var r = RepoOf(g.Key, g.ToList()); if (r == null || m.ContainsKey(r)) continue;
            try { m[r] = (await Gh.Issues(r)).Count(i => Seen.IsNew(r, i)); } catch { m[r] = 0; }
        }
        badges = m;
        var total = m.Values.Sum();
        Text = $"Link-Zentrale 1.{Gh.CurrentCode}" + (total > 0 ? $"  •  💬 {total} neu" : "");
        RenderLinks();
    }

    public MainForm()
    {
        Text = $"Link-Zentrale 1.{Gh.CurrentCode}";
        try { Icon = Icon.ExtractAssociatedIcon(Environment.ProcessPath!); } catch { }
        BackColor = BG; ForeColor = Color.White; Font = new Font("Segoe UI", 10);
        ClientSize = new Size(W + 60, 860); MinimumSize = new Size(W + 60, 500);
        StartPosition = FormStartPosition.CenterScreen;

        // ---------- Kopfzeile ----------
        var head = new Panel { Dock = DockStyle.Top, Height = 92, BackColor = BG, Padding = new Padding(16, 10, 16, 6) };
        var title = Lbl("Link-Zentrale", 22, true);
        title.Location = new Point(14, 6);
        status.AutoSize = true; status.ForeColor = SOFT; status.Location = new Point(18, 56); status.Text = "Aktualisiere …";
        status.MaximumSize = new Size(W - 120, 0);
        var refresh = Btn("↻", async () => await RefreshAll(), BLUE);
        refresh.Location = new Point(W - 20, 14);
        btnUpdate = Btn("⬆ Update", async () => await DoUpdate(), PINK);
        btnUpdate.Location = new Point(W - 150, 14); btnUpdate.Visible = false;
        head.Controls.AddRange(new Control[] { title, status, refresh, btnUpdate });

        // ---------- Reiter ----------
        var tabs = new TabControl { Dock = DockStyle.Fill, Font = new Font("Segoe UI", 11, FontStyle.Bold), Padding = new Point(16, 6) };
        var tLinks = new TabPage("🔗 Links") { BackColor = BG };
        var tTools = new TabPage("🛠 Werkzeuge") { BackColor = BG };
        tLinks.Controls.Add(Page(BuildLinks()));
        tTools.Controls.Add(Page(BuildTools()));
        tabs.TabPages.AddRange(new[] { tLinks, tTools });

        Controls.Add(tabs);
        Controls.Add(head);

        RenderLinks();
        Load += async (_, _) =>
        {
            Gh.Token = Store.Token;
            await RefreshAll();
            if (Gh.Token != "") { await Run("Anmelden", Gh.Me); await LoadRepos(); }
        };
    }

    // ======================= Bausteine =======================

    internal static FlowLayoutPanel Stack() => new() { FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoSize = true, Margin = new Padding(0), BackColor = Color.Transparent };

    internal static FlowLayoutPanel Page(params Control[] cs)
    {
        var p = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoScroll = true, Padding = new Padding(14), BackColor = BG };
        p.Controls.AddRange(cs);
        return p;
    }

    internal static Label Lbl(string t, float size = 10, bool bold = false, Color? c = null) => new()
    {
        Text = t, AutoSize = true, MaximumSize = new Size(W - 30, 0), ForeColor = c ?? Color.White, BackColor = Color.Transparent,
        Font = new Font("Segoe UI", size, bold ? FontStyle.Bold : FontStyle.Regular), Margin = new Padding(3, 4, 3, 2)
    };

    internal static Button Btn(string t, Action a, Color? c = null, int w = 0)
    {
        var b = new Button
        {
            Text = t, Height = 38, FlatStyle = FlatStyle.Flat, BackColor = c ?? BLUE, ForeColor = BG, Cursor = Cursors.Hand,
            Font = new Font("Segoe UI", 10, FontStyle.Bold), Margin = new Padding(3), Padding = new Padding(8, 2, 8, 2)
        };
        b.FlatAppearance.BorderSize = 0;
        if (w > 0) b.Width = w; else { b.AutoSize = true; b.AutoSizeMode = AutoSizeMode.GrowAndShrink; b.MinimumSize = new Size(0, 38); }
        b.Click += (_, _) => a();
        return b;
    }

    internal static Button Wide(string t, Action a, Color? c = null) => Btn(t, a, c, W - 30);

    internal static FlowLayoutPanel Row(params Control[] cs)
    {
        var p = new FlowLayoutPanel { FlowDirection = FlowDirection.LeftToRight, WrapContents = true, AutoSize = true, MaximumSize = new Size(W - 20, 0), Margin = new Padding(0), BackColor = Color.Transparent };
        p.Controls.AddRange(cs);
        return p;
    }

    internal static TextBox Txt(string ph, bool pw = false) => new()
    {
        PlaceholderText = ph, UseSystemPasswordChar = pw, Width = W - 30, BackColor = INPUT, ForeColor = Color.White,
        BorderStyle = BorderStyle.FixedSingle, Font = new Font("Segoe UI", 11), Margin = new Padding(3)
    };

    static ComboBox Combo() => new()
    {
        DropDownStyle = ComboBoxStyle.DropDownList, Width = W - 80, BackColor = INPUT, ForeColor = Color.White,
        FlatStyle = FlatStyle.Flat, Font = new Font("Segoe UI", 11), Margin = new Padding(3)
    };

    internal static FlowLayoutPanel Card(string title)
    {
        var c = new FlowLayoutPanel
        {
            FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoSize = true, BackColor = CARD,
            Padding = new Padding(10), Margin = new Padding(0, 6, 0, 6), MinimumSize = new Size(W, 0)
        };
        if (title != "") c.Controls.Add(Lbl(title, 13, true));
        return c;
    }

    internal static void Open(string url)
    {
        try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
    }

    void Copy(string text)
    {
        try { Clipboard.SetText(text); SetStatus("✅ Kopiert – jetzt einfügen und senden"); } catch { SetStatus("❌ Zwischenablage belegt – nochmal versuchen", true); }
    }

    internal static void Mail(string subject, string body) =>
        Open($"mailto:?subject={Uri.EscapeDataString(subject)}&body={Uri.EscapeDataString(body)}");

    void SetStatus(string s, bool err = false) { status.Text = s; status.ForeColor = err || s.StartsWith("❌") ? PINK : SOFT; }

    async Task Run(string label, Func<Task<string>> f)
    {
        SetStatus($"⏳ {label} …");
        UseWaitCursor = true;
        try { SetStatus(await f()); }
        catch (Exception e) { SetStatus("❌ " + e.Message, true); }
        finally { UseWaitCursor = false; }
    }

    static string? Prompt(string title, string label, bool pw = false)
    {
        using var f = new Form
        {
            Text = title, FormBorderStyle = FormBorderStyle.FixedDialog, StartPosition = FormStartPosition.CenterParent,
            ClientSize = new Size(460, 150), MaximizeBox = false, MinimizeBox = false, BackColor = BG, ForeColor = Color.White, Font = new Font("Segoe UI", 10)
        };
        var l = new Label { Text = label, AutoSize = true, Location = new Point(14, 14), MaximumSize = new Size(430, 0) };
        var t = new TextBox { Location = new Point(14, 50), Width = 430, UseSystemPasswordChar = pw, BackColor = INPUT, ForeColor = Color.White, BorderStyle = BorderStyle.FixedSingle };
        var ok = new Button { Text = "OK", DialogResult = DialogResult.OK, Location = new Point(270, 100), Width = 80, BackColor = BLUE, ForeColor = BG, FlatStyle = FlatStyle.Flat };
        var no = new Button { Text = "Abbrechen", DialogResult = DialogResult.Cancel, Location = new Point(360, 100), Width = 84, FlatStyle = FlatStyle.Flat };
        f.Controls.AddRange(new Control[] { l, t, ok, no });
        f.AcceptButton = ok; f.CancelButton = no;
        return f.ShowDialog() == DialogResult.OK ? t.Text.Trim() : null;
    }

    internal static bool Ask(string q) => MessageBox.Show(q, "Link-Zentrale", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) == DialogResult.Yes;

    // ======================= Reiter 1: Links =======================

    Control BuildLinks()
    {
        var s = Stack();
        s.Controls.Add(Lbl("GitHub", 14, true));
        s.Controls.Add(Row(
            Btn("👤 Mein Konto", () => Open($"https://github.com/{Gh.USER}")),
            Btn("📚 Alle Repos", () => Open($"https://github.com/{Gh.USER}?tab=repositories")),
            Btn("⚙️ Builds", () => Open($"https://github.com/{Gh.USER}/HandyPutz/actions")),
            Btn("📦 Versionen", () => Open($"https://github.com/{Gh.UPDATE_REPO}/releases"))));
        s.Controls.Add(Lbl("Download-Links", 14, true));
        s.Controls.Add(Wide("📋 Alle Links auf einmal kopieren", () =>
            Copy(string.Join("\r\n\r\n", links.Concat(own).Select(l => Strip(l.Name) + ":\r\n" + l.Url))), PINK));
        s.Controls.Add(linksPanel);
        s.Controls.Add(Wide("➕ Eigenen Link hinzufügen", () =>
        {
            var n = Prompt("Eigener Link", "Name:"); if (string.IsNullOrWhiteSpace(n)) return;
            var u = Prompt("Eigener Link", "Adresse (https://…):"); if (u == null || !u.StartsWith("http")) return;
            own.Add(new Link("🔗 " + n, u, "", true)); Store.Save("own.json", own); RenderLinks();
        }, CARD));
        var hint = Lbl("Neue Apps in öffentlichen Repos mit Release erscheinen hier automatisch.", 9, false, SOFT);
        s.Controls.Add(hint);
        return s;
    }

    static string Strip(string name) => name.Contains(' ') ? name[(name.IndexOf(' ') + 1)..] : name;

    void RenderLinks()
    {
        linksPanel.SuspendLayout();
        linksPanel.Controls.Clear();
        foreach (var g in links.Concat(own).GroupBy(l => l.Own ? "Eigene Links" : (l.Project == "" ? "Sonstiges" : l.Project)))
        {
            var proj = g.Key; var ls = g.ToList();
            var h = Card("📦 " + proj);
            h.BackColor = Color.FromArgb(27, 74, 128); h.Margin = new Padding(0, 16, 0, 4);
            if (proj != "Eigene Links")
            {
                var repoP = RepoOf(proj, ls);
                if (repoP != null)
                {
                    var n = badges.GetValueOrDefault(repoP);
                    h.Controls.Add(Btn(n > 0 ? $"💬 Bewertungen  ({n} neu)" : "💬 Bewertungen", () =>
                    {
                        using (var f = new ReviewsForm(proj, repoP)) f.ShowDialog(this);
                        _ = CountBadges();
                    }, n > 0 ? PINK : SOFT));
                }
                h.Controls.Add(Lbl("Test-Link zum Weitergeben – fertige Nachricht mit Anleitung", 9, false, SOFT));
                h.Controls.Add(Row(
                    Btn("📋 Test-Nachricht kopieren", () => Copy(Gh.TestMsg(proj, ls))),
                    Btn("📧 Per E-Mail an Tester", () => Mail($"Bitte teste: {proj}", Gh.TestMsg(proj, ls)), GREEN)));
            }
            linksPanel.Controls.Add(h);
            foreach (var l in ls)
            {
                var c = Card("");
                c.Controls.Add(Lbl(l.Name, 12, true));
                if (l.Info != "") c.Controls.Add(Lbl(l.Info, 9, false, SOFT));
                c.Controls.Add(Lbl(l.Url, 9, false, SOFT));
                var row = Row(
                    Btn("📋 Kopieren", () => Copy(l.Url)),
                    Btn("📧 Senden", () => Mail(Strip(l.Name), Strip(l.Name) + ": " + l.Url), GREEN),
                    Btn("↗ Öffnen", () => Open(l.Url), SOFT));
                if (l.Own) row.Controls.Add(Btn("🗑", () => { own.Remove(l); Store.Save("own.json", own); RenderLinks(); }, PINK));
                c.Controls.Add(row);
                linksPanel.Controls.Add(c);
            }
        }
        linksPanel.ResumeLayout();
    }

    async Task RefreshAll()
    {
        SetStatus("Aktualisiere …");
        try
        {
            links = await Gh.Downloads(); Store.Save("cache.json", links); RenderLinks();
            SetStatus("✅ Aktuell");
            await CountBadges();
        }
        catch { SetStatus("Offline – letzter Stand"); }
        try { update = await Gh.SelfUpdate(); } catch { update = null; }
        if (update != null) { btnUpdate.Text = $"⬆ {update.Name}"; btnUpdate.Left = W - 30 - btnUpdate.Width; btnUpdate.Visible = true; }
    }

    async Task DoUpdate()
    {
        if (update == null || !Ask($"Neue Version „{update.Name}“ jetzt installieren?\nDie Link-Zentrale startet danach neu.")) return;
        await Run("Lade Update", async () => { await Gh.InstallUpdate(update); return "Neustart …"; });
    }

    // ======================= Reiter 2: Werkzeuge =======================

    Label Target() { var l = Lbl("Repo: – erst in Maske 2 wählen –", 9, false, SOFT); targets.Add(l); return l; }

    Control BuildTools()
    {
        var s = Stack();

        // 1 — Token
        var c1 = Card("1 🔑 Token");
        c1.Controls.Add(Lbl("„Neuen Token erstellen“ öffnet GitHub mit fertig ausgefüllter Maske. Dort nur Ablauf wählen → Generate token → kopieren → hier einfügen.", 9, false, SOFT));
        var tbToken = Txt("Token hier einfügen (ghp_…)", true); tbToken.Text = Store.Token;
        c1.Controls.Add(Wide("🆕 Neuen Token erstellen", () => Open(Gh.TOKEN_URL)));
        c1.Controls.Add(tbToken);
        c1.Controls.Add(Row(
            Btn("💾 Speichern & prüfen", async () =>
            {
                Store.Token = tbToken.Text.Trim(); Gh.Token = tbToken.Text.Trim();
                await Run("Prüfen", Gh.Me); await LoadRepos();
            }),
            Btn("📋 Kopieren", () => { if (tbToken.Text != "") Copy(tbToken.Text); }),
            Btn("Token-Liste / löschen", () => Open("https://github.com/settings/tokens"), SOFT),
            Btn("Aus App entfernen", () =>
            {
                tbToken.Text = ""; Store.Token = ""; Gh.Token = ""; repos.Clear(); repo = null; cbRepo.Items.Clear(); SetStatus("Token aus der App entfernt");
            }, PINK)));
        s.Controls.Add(c1);

        // 2 — Repo & Links
        var c2 = Card("2 📁 Repo & Links");
        cbRepo.SelectedIndexChanged += async (_, _) => { if (cbRepo.SelectedItem is Repo r) { repo = r; await LoadRepo(); } };
        c2.Controls.Add(Row(cbRepo, Btn("↻", async () => await LoadRepos())));
        c2.Controls.Add(repoLinks);
        s.Controls.Add(c2);

        // 3 — Neues Repo
        var c3 = Card("3 ➕ Neues Repo");
        var tbName = Txt("Name (z. B. MeinTV)"); var tbDesc = Txt("Beschreibung (optional)");
        var chkPriv = new CheckBox { Text = "🔒 Privat (aus = öffentlich, für Downloads ohne Anmeldung)", Checked = true, AutoSize = true, ForeColor = Color.White, Margin = new Padding(3, 6, 3, 6) };
        c3.Controls.AddRange(new Control[] { tbName, tbDesc, chkPriv });
        c3.Controls.Add(Wide("Repo anlegen", async () =>
        {
            var n = tbName.Text.Trim().Replace(' ', '-'); if (n == "") return;
            await Run("Anlegen", () => Gh.CreateRepo(n, tbDesc.Text.Trim(), chkPriv.Checked));
            tbName.Text = ""; tbDesc.Text = ""; await LoadRepos();
        }));
        s.Controls.Add(c3);

        // 4 — Secret
        var c4 = Card("4 🔐 Secret eintragen");
        c4.Controls.Add(Target());
        var tbSName = Txt("Name"); tbSName.Text = "UPDATE_TOKEN"; tbSName.CharacterCasing = CharacterCasing.Upper;
        var tbSVal = Txt("Wert", true);
        c4.Controls.Add(Row(new[] { "UPDATE_TOKEN", "FEEDBACK_TOKEN", "KEYSTORE_PASS", "KEYSTORE_B64" }
            .Select(n => (Control)Btn(n, () => tbSName.Text = n, SOFT)).ToArray()));
        c4.Controls.AddRange(new Control[] { tbSName, tbSVal });
        c4.Controls.Add(Row(
            Btn("🔑 Gespeicherten Token als Wert einsetzen", () => tbSVal.Text = Store.Token, SOFT),
            Btn("📂 Wert aus Datei…", () =>
            {
                using var d = new OpenFileDialog { Title = "Datei wählen (z. B. .jks wird automatisch Base64)" };
                if (d.ShowDialog() != DialogResult.OK) return;
                var bytes = File.ReadAllBytes(d.FileName);
                tbSVal.Text = d.FileName.EndsWith(".txt", StringComparison.OrdinalIgnoreCase) ? File.ReadAllText(d.FileName).Trim() : Convert.ToBase64String(bytes);
            }, SOFT)));
        c4.Controls.Add(Wide("Secret speichern", async () =>
        {
            if (repo == null || tbSName.Text.Trim() == "" || tbSVal.Text == "") { SetStatus("❌ Repo, Name und Wert ausfüllen", true); return; }
            var r = repo; var n = tbSName.Text.Trim().Replace(' ', '_'); var v = tbSVal.Text.Trim();
            await Run("Secret speichern", () => Gh.SetSecret(r, n, v)); tbSVal.Text = "";
        }));
        s.Controls.Add(c4);

        // 5 — Ordner
        var c5 = Card("5 📂 Ordner im Repo");
        c5.Controls.Add(Target());
        var tbFolder = Txt("Neuer Ordner (z. B. dateien/bilder)");
        c5.Controls.Add(tbFolder);
        c5.Controls.Add(Wide("Ordner anlegen", async () =>
        {
            if (repo == null || tbFolder.Text.Trim() == "") return;
            var r = repo; var p = tbFolder.Text;
            await Run("Ordner anlegen", () => Gh.CreateFolder(r, p)); tbFolder.Text = ""; await LoadRepo();
        }));
        c5.Controls.Add(Lbl("Ordner zum Löschen:", 9, false, SOFT));
        c5.Controls.Add(cbFolder);
        c5.Controls.Add(Wide("🗑 Ordner löschen", async () =>
        {
            if (repo == null || cbFolder.SelectedItem is not string f) return;
            if (!Ask($"Ordner „{f}“ mit allem Inhalt löschen?\nDas kann nicht rückgängig gemacht werden.")) return;
            var r = repo;
            await Run("Ordner löschen", () => Gh.DeleteFolder(r, f)); await LoadRepo();
        }, PINK));
        s.Controls.Add(c5);

        // 6 — Builds
        var c6 = Card("6 🚀 Builds");
        c6.Controls.Add(Target());
        c6.Controls.Add(Row(
            Btn("🚀 Neu bauen", async () => { if (repo != null) { var r = repo; await Run("Build starten", () => Gh.Build(r)); } }),
            Btn("↻ Liste laden", async () => await LoadRuns())));
        c6.Controls.Add(runsPanel);
        s.Controls.Add(c6);

        // 7 — Releases
        var c7 = Card("7 📦 Versionen (Releases)");
        c7.Controls.Add(Lbl("Tipp: Die Versionen von HandyPutz und Link-Zentrale liegen im Repo HandyPutz-Updates.", 9, false, SOFT));
        c7.Controls.Add(Target());
        c7.Controls.Add(Wide("↻ Versionen laden", async () => await LoadRels()));
        c7.Controls.Add(relsPanel);
        s.Controls.Add(c7);

        // 8 — Repo löschen
        var c8 = Card("8 🗑 Repo löschen");
        c8.Controls.Add(Lbl("Löscht das in Maske 2 gewählte Repo endgültig. Zur Sicherheit musst du den Namen eintippen.", 9, false, SOFT));
        c8.Controls.Add(Target());
        c8.Controls.Add(Wide("Repo löschen", async () =>
        {
            if (repo == null) return;
            var r = repo;
            var typed = Prompt("Repo löschen", $"Repo {r.Full} endgültig löschen?\nZum Bestätigen „{r.Name}“ eintippen:");
            if (typed != r.Name) { if (typed != null) SetStatus("❌ Name stimmt nicht – nichts gelöscht", true); return; }
            await Run("Repo löschen", () => Gh.DeleteRepo(r)); repo = null; await LoadRepos();
        }, PINK));
        s.Controls.Add(c8);
        return s;
    }

    async Task LoadRepos()
    {
        if (Gh.Token == "") return;
        try
        {
            repos = await Gh.Repos();
            var keep = repo?.Full;
            cbRepo.Items.Clear();
            foreach (var r in repos) cbRepo.Items.Add(r);
            var idx = repos.FindIndex(r => r.Full == keep);
            if (idx >= 0) cbRepo.SelectedIndex = idx;
            else { repo = null; repoLinks.Controls.Clear(); UpdateTargets(); }
            SetStatus($"✅ {repos.Count} Repos geladen");
        }
        catch (Exception e) { SetStatus("❌ " + e.Message, true); }
    }

    void UpdateTargets()
    {
        foreach (var t in targets) t.Text = repo == null ? "Repo: – erst in Maske 2 wählen –" : $"Repo: {repo.Name}";
    }

    async Task LoadRepo()
    {
        if (repo == null) return;
        var r = repo;
        UpdateTargets();
        runsPanel.Controls.Clear(); relsPanel.Controls.Clear();
        await Run($"{r.Name} laden", async () =>
        {
            assets = await Gh.Assets(r);
            var folders = await Gh.Folders(r);
            cbFolder.Items.Clear(); foreach (var f in folders) cbFolder.Items.Add(f);
            RenderRepoLinks(r);
            return $"✅ {r.Name}: {folders.Count} Ordner, {assets.Count} Dateien im neuesten Release";
        });
    }

    void RenderRepoLinks(Repo r)
    {
        var l = new List<(string, string)>
        {
            ("Repo", r.Html), ("Secrets-Seite", r.Html + "/settings/secrets/actions"), ("Builds (Actions)", r.Html + "/actions"),
            ("Versionen", r.Html + "/releases"), ("Klonen", r.Html + ".git")
        };
        l.AddRange(assets.Select(a => ("⬇ " + a, $"{r.Html}/releases/latest/download/{a}")));
        repoLinks.SuspendLayout();
        repoLinks.Controls.Clear();
        repoLinks.Controls.Add(Wide($"📋 Alle Links von {r.Name} kopieren", () => Copy(string.Join("\r\n", l.Select(x => $"{x.Item1}: {x.Item2}"))), PINK));
        foreach (var (n, u) in l)
        {
            var row = Row(Btn("📋", () => Copy(u), BLUE, 44), Btn("↗", () => Open(u), SOFT, 44));
            var txt = Stack();
            txt.Controls.Add(Lbl(n, 10, true));
            var ul = Lbl(u, 8, false, SOFT); ul.MaximumSize = new Size(W - 140, 0);
            txt.Controls.Add(ul);
            row.Controls.Add(txt);
            repoLinks.Controls.Add(row);
        }
        repoLinks.ResumeLayout();
    }

    async Task LoadRuns()
    {
        if (repo == null) return;
        var r = repo;
        await Run("Builds laden", async () =>
        {
            var runs = await Gh.Runs(r);
            runsPanel.Controls.Clear();
            foreach (var x in runs)
            {
                var row = Row(Lbl($"{x.Status} {x.Title}", 10));
                row.Controls[0].MaximumSize = new Size(W - 170, 0);
                if (x.Status == "⏳") row.Controls.Add(Btn("⏹", async () => { await Run("Abbrechen", () => Gh.Cancel(r, x)); await LoadRuns(); }, PINK, 44));
                else row.Controls.Add(Btn("🔁", async () => { await Run("Wiederholen", () => Gh.Rerun(r, x)); await LoadRuns(); }, BLUE, 44));
                row.Controls.Add(Btn("↗", () => Open(x.Url), SOFT, 44));
                runsPanel.Controls.Add(row);
            }
            return $"✅ {runs.Count} letzte Builds";
        });
    }

    async Task LoadRels()
    {
        if (repo == null) return;
        var r = repo;
        await Run("Versionen laden", async () =>
        {
            var rels = await Gh.Releases(r);
            relsPanel.SuspendLayout();
            relsPanel.Controls.Clear();
            foreach (var v in rels)
            {
                relsPanel.Controls.Add(Lbl((v.Latest ? "⭐ " : "") + $"{v.Name}  ({v.Tag})", 11, true));
                foreach (var f in v.Files)
                {
                    var u = $"{r.Html}/releases/download/{v.Tag}/{f}";
                    relsPanel.Controls.Add(Row(Lbl("⬇ " + f, 9, false, SOFT), Btn("📋", () => Copy(u), BLUE, 44), Btn("📧", () => Mail(f, $"{f}: {u}"), GREEN, 44)));
                }
                var act = Row();
                if (!v.Latest) act.Controls.Add(Btn("⭐ Als neueste", async () => { await Run("Markieren", () => Gh.MakeLatest(r, v)); await LoadRels(); }, BLUE));
                act.Controls.Add(Btn("🗑 Löschen", async () =>
                {
                    if (!Ask($"Version {v.Name} löschen?")) return;
                    await Run("Version löschen", () => Gh.DeleteRelease(r, v)); await LoadRels();
                }, PINK));
                relsPanel.Controls.Add(act);
            }
            relsPanel.ResumeLayout();
            return $"✅ {rels.Count} Versionen";
        });
    }
}
