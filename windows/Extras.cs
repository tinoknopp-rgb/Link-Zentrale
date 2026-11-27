using System.Reflection;
using System.Text.Json;
using QRCoder;
using static LinkZentrale.MainForm;

namespace LinkZentrale;

/// <summary>Deutsch/Englisch – gleiche Übersetzungsliste wie die Handy-App (en.json)</summary>
public static class Tr
{
    static readonly string F = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Link-Zentrale", "lang.txt");
    public static bool En = File.Exists(F) && File.ReadAllText(F).Trim() == "en";
    static Dictionary<string, string>? map;
    static List<KeyValuePair<string, string>> phrases = new();

    static Dictionary<string, string> M
    {
        get
        {
            if (map != null) return map;
            try
            {
                using var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("en.json")!;
                map = JsonSerializer.Deserialize<Dictionary<string, string>>(s) ?? new();
                phrases = map.Where(k => k.Key.Length >= 7).OrderByDescending(k => k.Key.Length).ToList();
            }
            catch { map = new(); }
            return map;
        }
    }

    public static string T(string s)
    {
        if (!En || string.IsNullOrEmpty(s)) return s;
        var m = M;
        var key = s.Replace("\r\n", "\n");
        if (m.TryGetValue(key, out var e)) return e.Replace("\n", "\r\n");
        foreach (var (de, en) in phrases) if (key.Contains(de)) key = key.Replace(de, en);
        return key.Replace("\n", "\r\n");
    }

    public static void Set(bool en)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(F)!);
        File.WriteAllText(F, en ? "en" : "de");
        Application.Restart();
        Environment.Exit(0);
    }
}

/// <summary>Absturz merken, beim nächsten Start als Fehlerbericht senden; Vorgänger anbieten</summary>
public static class Crash
{
    static readonly string F = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Link-Zentrale", "crash.txt");

    public static void Install()
    {
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => Handle(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Handle(e.ExceptionObject as Exception);
    }

    static void Handle(Exception? e)
    {
        try { Directory.CreateDirectory(Path.GetDirectoryName(F)!); File.WriteAllText(F, e?.ToString() ?? "?"); } catch { }
        var hasOld = File.Exists(Gh.Backup);
        var r = MessageBox.Show(Tr.T("Die Link-Zentrale hat einen Fehler. Der Bericht wird beim nächsten Start automatisch gesendet.") +
                                (hasOld ? "\n\n" + Tr.T("Vorgänger-Version wiederherstellen?") : ""),
            "Link-Zentrale", hasOld ? MessageBoxButtons.YesNo : MessageBoxButtons.OK, MessageBoxIcon.Error);
        if (hasOld && r == DialogResult.Yes) Gh.Restore();
    }

    public static async Task SendPending()
    {
        if (!File.Exists(F)) return;
        var t = File.ReadAllText(F);
        var first = t.Split('\n')[0].Trim();
        if (first.Length > 90) first = first[..90];
        if (await Gh.SendFeedback("[🐞 Absturz] " + first, "Automatischer Fehlerbericht (Windows):\n```\n" + (t.Length > 6000 ? t[..6000] : t) + "\n```"))
            File.Delete(F);
    }
}

/// <summary>Feedback geben: Bewertung mit Sternen, Vorschlag oder Fehler</summary>
public class FeedbackForm : Form
{
    public FeedbackForm()
    {
        Text = Tr.T("⭐ Feedback geben");
        BackColor = BG; ForeColor = Color.White; Font = new Font("Segoe UI", 11);
        ClientSize = new Size(520, 400); StartPosition = FormStartPosition.CenterParent; FormBorderStyle = FormBorderStyle.FixedDialog; MaximizeBox = false;
        var type = "⭐"; var stars = 5;
        var p = Page();
        var lblStars = Lbl("★★★★★", 24, false, Color.FromArgb(255, 201, 77));
        var starRow = Row(Enumerable.Range(1, 5).Select(i => (Control)Btn(i + "★", () => { stars = i; lblStars.Text = new string('★', i) + new string('☆', 5 - i); }, SOFT, 56)).ToArray());
        p.Controls.Add(Row(
            Btn("⭐ Bewertung", () => { type = "⭐"; starRow.Visible = lblStars.Visible = true; }, BLUE),
            Btn("💡 Vorschlag", () => { type = "💡"; starRow.Visible = lblStars.Visible = false; }, BLUE),
            Btn("🐞 Fehler", () => { type = "🐞"; starRow.Visible = lblStars.Visible = false; }, BLUE)));
        p.Controls.Add(lblStars); p.Controls.Add(starRow);
        var tb = new TextBox { Multiline = true, Width = 480, Height = 120, BackColor = Color.FromArgb(8, 28, 50), ForeColor = Color.White, PlaceholderText = Tr.T("Was möchtest du uns sagen?") };
        p.Controls.Add(tb);
        var status = Lbl("", 10, false, SOFT);
        p.Controls.Add(Row(Btn("Senden", async () =>
        {
            var label = type switch { "⭐" => "⭐ Bewertung " + new string('★', stars) + new string('☆', 5 - stars), "💡" => "💡 Vorschlag", _ => "🐞 Fehler" };
            var first = tb.Lines.FirstOrDefault(l => l.Trim() != "") ?? "";
            if (first.Length > 80) first = first[..80];
            var title = $"[{label}] {first}".Trim();
            status.Text = Tr.T("⏳ Sende …");
            if (await Gh.SendFeedback(title, tb.Text)) { MessageBox.Show(Tr.T("✅ Danke! Feedback gesendet")); Close(); }
            else
            {
                status.Text = Tr.T("Kein Feedback-Zugang – bitte per Nachricht senden");
                Mail("Link-Zentrale " + title, tb.Text + "\r\n\r\nLink-Zentrale 1." + Gh.CurrentCode + " (Windows)");
            }
        }, GREEN), Btn("Abbrechen", Close, SOFT)));
        p.Controls.Add(status);
        Controls.Add(p);
    }
}

/// <summary>QR-Code eines Test-Links zum Abscannen</summary>
public class QrForm : Form
{
    public QrForm(string title, string url)
    {
        Text = "🔳 " + title;
        BackColor = Color.White; ClientSize = new Size(420, 500); StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog; MaximizeBox = false;
        using var gen = new QRCodeGenerator();
        var png = new PngByteQRCode(gen.CreateQrCode(url, QRCodeGenerator.ECCLevel.M)).GetGraphic(12);
        var pic = new PictureBox { Image = Image.FromStream(new MemoryStream(png)), SizeMode = PictureBoxSizeMode.Zoom, Bounds = new Rectangle(20, 10, 380, 380) };
        var l = new Label { Text = Tr.T("Zum Scannen mit der Handy-Kamera") + "\r\n" + url, ForeColor = Color.Black, Bounds = new Rectangle(20, 395, 380, 60), Font = new Font("Segoe UI", 9) };
        var copy = new Button { Text = Tr.T("📋 Kopieren"), Bounds = new Rectangle(20, 455, 180, 34) };
        copy.Click += (_, _) => { try { Clipboard.SetText(url); } catch { } };
        var close = new Button { Text = Tr.T("Schließen"), Bounds = new Rectangle(220, 455, 180, 34) };
        close.Click += (_, _) => Close();
        Controls.AddRange(new Control[] { pic, l, copy, close });
    }
}
