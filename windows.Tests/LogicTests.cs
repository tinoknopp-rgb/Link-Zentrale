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
