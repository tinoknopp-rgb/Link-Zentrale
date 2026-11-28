using LinkZentrale;
using Xunit;

/// <summary>Läuft bei jedem Build – schlägt ein Test fehl, wird nichts veröffentlicht</summary>
public class LogicTests
{
    [Theory]
    [InlineData("HandyPutz.apk", "HandyPutz")]
    [InlineData("HandyPutz-1.4.apk", "HandyPutz")]
    [InlineData("WunschFinder_v2.apk", "WunschFinder")]
    [InlineData("Link-Zentrale.exe", "Link-Zentrale")]
    public void ProjektnameAusDatei(string file, string expected) => Assert.Equal(expected, Logic.ProjectOf(file));

    [Fact] public void Sterne() { Assert.Equal(4, Logic.Stars("[⭐ Bewertung ★★★★☆] super")); Assert.Equal(0, Logic.Stars("[🐞 Fehler]")); }

    [Fact]
    public void Art()
    {
        Assert.Equal("⭐", Logic.Kind("[⭐ Bewertung ★★★★★] toll"));
        Assert.Equal("💡", Logic.Kind("[💡 Vorschlag] mehr"));
        Assert.Equal("🐞", Logic.Kind("[🐞 Absturz] x"));
        Assert.Equal("💬", Logic.Kind("Frage"));
    }

    [Fact]
    public void Trennen()
    {
        Assert.True(Logic.Belongs("Link-Zentrale", "Link-Zentrale: [⭐ Bewertung]"));
        Assert.False(Logic.Belongs("HandyPutz", "Link-Zentrale: [⭐ Bewertung]"));
        Assert.True(Logic.Belongs(null, "x"));
    }

    [Fact] public void QuellRepo() => Assert.Equal("max/WunschFinder", Logic.SrcRepo("max/WunschFinder-Updates"));
    [Fact] public void Versionsnummer() { Assert.Equal(21, Logic.TagCode("v1.21")); Assert.Equal(20, Logic.TagCode("link-v1.20")); Assert.Null(Logic.TagCode("link-zentrale")); }
}

public class MoreTests
{
    [Fact] public void Hinweis() => Assert.Equal("Gesendet mit Link-Zentrale: https://github.com/max/Link-Zentrale-Updates", Logic.PromoLine("max/Link-Zentrale-Updates"));
    [Fact] public void Themen() => Assert.Equal(new[] { "android", "github-tools" }, Logic.Topics("Android, GitHub Tools, android,"));
}

public class AuftragTests
{
    const string Beispiel = """
        Hier ist dein Auftrag:
        ```
        LZ-AUFTRAG
        {"version":1,"titel":"MeinTV vorbereiten","schritte":[
          {"aktion":"repo","repo":"MeinTV-Updates","oeffentlich":true},
          {"aktion":"dateien","repo":"MeinTV","nachricht":"[MeinTV] Build","dateien":[
            {"pfad":"/.github/workflows/build.yml","inhalt":"name: App bauen"},
            {"pfad":"gradlew","inhalt":"#!/bin/sh"},
            {"pfad":"alt.txt","loeschen":true}]},
          {"aktion":"secret","repo":"MeinTV","name":"UPDATE_TOKEN","wert":"@token"},
          {"aktion":"secret","repo":"MeinTV","name":"KEYSTORE_B64","wert":"@KEYSTORE_B64"},
          {"aktion":"einstellungen","repo":"max/MeinTV-Updates","oeffentlich":true,"themen":["android","iptv"]},
          {"aktion":"build","repo":"MeinTV"},
          {"aktion":"text","titel":"F-Droid-Antrag","text":"Hallo","url":"https://example.org"}
        ]}
        ```
        Viel Erfolg!
        """;

    [Fact]
    public void Erkennen()
    {
        var p = Auftrag.Parse(Beispiel, "max");
        Assert.Equal("MeinTV vorbereiten", p.Titel);
        Assert.Equal(7, p.Schritte.Count);
        Assert.Equal("max/MeinTV-Updates", p.Schritte[0].Repo);
        Assert.Equal("max/MeinTV-Updates", p.Schritte[4].Repo);
        var d = p.Schritte[1].Dateien;
        Assert.Equal(".github/workflows/build.yml", d[0].Pfad);
        Assert.Equal("100755", d[1].Mode);
        Assert.True(d[2].Loeschen);
        Assert.Equal("", p.Schritte[6].Repo);
    }

    [Fact]
    public void Beschreiben()
    {
        var p = Auftrag.Parse(Beispiel, "max");
        Assert.Equal(new[] { "KEYSTORE_B64" }, Auftrag.Needs(p));
        Assert.Equal("➕ Repo MeinTV-Updates anlegen (🌍 öffentlich)", Auftrag.Describe(p.Schritte[0]));
        Assert.Equal("🔐 Secret UPDATE_TOKEN → MeinTV (gespeicherter Token)", Auftrag.Describe(p.Schritte[2]));
        Assert.Equal("⚙ MeinTV-Updates: 🌍 öffentlich, 2 Themen", Auftrag.Describe(p.Schritte[4]));
        Assert.Equal(2, Auftrag.Warnings(p).Count);
    }

    [Theory]
    [InlineData("nur Text", "Kein Auftrag")]
    [InlineData("{\"schritte\":[{\"aktion\":\"zaubern\",\"repo\":\"x\"}]}", "unbekannte Aktion")]
    [InlineData("{\"schritte\":[{\"aktion\":\"build\"}]}", "repo")]
    [InlineData("{\"version\":9,\"schritte\":[]}", "aktualisieren")]
    public void FehlerKlar(string t, string teil) => Assert.Contains(teil, Assert.ThrowsAny<Exception>(() => Auftrag.Parse(t, "max")).Message);
}
