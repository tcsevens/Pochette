package app.pochette.tv;

final class PartyColors {
    private PartyColors() { }
    static int team(int i) { return Game.TEAM_COLORS[i % Game.TEAM_COLORS.length]; }
}
