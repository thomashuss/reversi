package io.github.thomashuss.reversi;

public class Endgame
        extends Exception
{
    private final int darkScore;
    private final int lightScore;

    public Endgame(int darkScore, int lightScore)
    {
        this.darkScore = darkScore;
        this.lightScore = lightScore;
    }

    public int getDarkScore()
    {
        return darkScore;
    }

    public int getLightScore()
    {
        return lightScore;
    }
}
