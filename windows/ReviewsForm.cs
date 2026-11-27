using static LinkZentrale.MainForm;

namespace LinkZentrale;

/// <summary>Bewertungen, Vorschläge und Fehlerberichte eines Projekts lesen und beantworten</summary>
public class ReviewsForm : Form
{
    readonly string repo;
    readonly Label status = Lbl("Lade …", 10, false, SOFT);
    readonly FlowLayoutPanel list = Stack();
    readonly FlowLayoutPanel detail = Stack();
    List<Issue> issues = new();
    string filter = "alle";

    public ReviewsForm(string project, string repo)
    {
        this.repo = repo;
        Text = $"💬 Bewertungen – {project}";
        BackColor = BG; ForeColor = Color.White; Font = new Font("Segoe UI", 10);
        ClientSize = new Size(W + 60, 820); StartPosition = FormStartPosition.CenterParent;
        try { Icon = Icon.ExtractAssociatedIcon(Environment.ProcessPath!); } catch { }

        var top = Stack();
        top.Controls.Add(Lbl($"💬 {project}", 18, true));
        top.Controls.Add(status);
        var chips = Row();
        foreach (var f in new[] { "alle", "neu", "offen", "⭐", "💡", "🐞" })
            chips.Controls.Add(Btn(f, () => { filter = f; Render(); }, SOFT));
        chips.Controls.Add(Btn("↻", async () => await LoadList(), BLUE));
        chips.Controls.Add(Btn("✔ Alle gelesen", () => { foreach (var i in issues) Seen.Mark(repo, i, i.Comments); Render(); }, BLUE));
        top.Controls.Add(chips);

        Controls.Add(Page(top, detail, list));
        Load += async (_, _) => await LoadList();
    }

    void SetStatus(string s) { status.Text = s; status.ForeColor = s.StartsWith("❌") ? PINK : SOFT; }

    async Task LoadList()
    {
        SetStatus("Lade …");
        try
        {
            issues = await Gh.Issues(repo);
            Render();
        }
        catch (Exception e) { SetStatus("❌ " + e.Message); }
    }

    void Render()
    {
        var n = issues.Count(i => Seen.IsNew(repo, i));
        SetStatus(issues.Count == 0 ? "Noch keine Bewertungen" : $"{issues.Count} Einträge • {n} neu");
        list.SuspendLayout();
        list.Controls.Clear();
        foreach (var i in issues.Where(i => filter switch
                 {
                     "neu" => Seen.IsNew(repo, i), "offen" => i.Open, "⭐" or "💡" or "🐞" => Gh.Kind(i.Title) == filter, _ => true
                 }))
        {
            var c = Card("");
            var isNew = Seen.IsNew(repo, i);
            c.Controls.Add(Lbl((isNew ? "🔴 NEU  " : "") + $"{Gh.Kind(i.Title)} {i.Title}", 11, true, isNew ? PINK : Color.White));
            c.Controls.Add(Lbl($"{i.Date} • #{i.Number} • {(i.Open ? "offen" : "erledigt ✔")} • 💬 {i.Comments}", 9, false, SOFT));
            var preview = i.Body.Split("\n---")[0].Trim();
            if (preview.Length > 200) preview = preview[..200] + " …";
            c.Controls.Add(Lbl(preview, 9, false, SOFT));
            c.Controls.Add(Btn("Öffnen & antworten", async () => await ShowIssue(i), BLUE));
            list.Controls.Add(c);
        }
        list.ResumeLayout();
    }

    async Task ShowIssue(Issue i)
    {
        detail.SuspendLayout();
        detail.Controls.Clear();
        var c = Card($"{Gh.Kind(i.Title)} #{i.Number}  {i.Title}");
        c.BackColor = Color.FromArgb(27, 74, 128);
        c.Controls.Add(Lbl($"{i.Date} • {(i.Open ? "offen" : "erledigt ✔")}", 9, false, SOFT));
        c.Controls.Add(Lbl(string.IsNullOrWhiteSpace(i.Body) ? "(kein Text)" : i.Body, 10));
        List<Comment> comments = new();
        try { comments = await Gh.Comments(repo, i.Number); } catch (Exception e) { SetStatus("❌ " + e.Message); }
        foreach (var k in comments)
        {
            var me = k.User == Gh.USER;
            c.Controls.Add(Lbl($"{(me ? "Du" : k.User)} • {k.Date}", 9, true, me ? BLUE : SOFT));
            c.Controls.Add(Lbl(k.Body, 10));
        }
        var tb = new TextBox
        {
            Multiline = true, Height = 90, Width = W - 30, BackColor = Color.FromArgb(8, 28, 50), ForeColor = Color.White,
            BorderStyle = BorderStyle.FixedSingle, PlaceholderText = "Antwort schreiben …", ScrollBars = ScrollBars.Vertical
        };
        c.Controls.Add(tb);
        var open = i.Open;
        c.Controls.Add(Row(
            Btn("📨 Antworten", async () =>
            {
                if (tb.Text.Trim() == "") return;
                try { await Gh.Reply(repo, i.Number, tb.Text.Trim()); SetStatus("✅ Antwort gesendet"); await LoadList(); await ShowIssue(issues.FirstOrDefault(x => x.Number == i.Number) ?? i); }
                catch (Exception e) { SetStatus("❌ " + e.Message); }
            }),
            Btn(open ? "✔ Als erledigt markieren" : "↺ Wieder öffnen", async () =>
            {
                try { await Gh.SetOpen(repo, i.Number, !open); SetStatus(open ? "✔ Erledigt" : "Wieder geöffnet"); await LoadList(); detail.Controls.Clear(); }
                catch (Exception e) { SetStatus("❌ " + e.Message); }
            }, SOFT),
            Btn("Auf GitHub ↗", () => MainForm.Open(i.Url), SOFT),
            Btn("✕ Schließen", () => detail.Controls.Clear(), PINK)));
        detail.Controls.Add(c);
        detail.ResumeLayout();
        Seen.Mark(repo, i, comments.Count);
        Render();
        if (Controls[0] is ScrollableControl sc) sc.ScrollControlIntoView(detail);
    }
}
