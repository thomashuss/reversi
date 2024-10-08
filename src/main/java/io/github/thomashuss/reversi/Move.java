package io.github.thomashuss.reversi;

import java.util.List;

class Move
        implements Comparable<Integer>
{
    private final byte whoseTurn;
    private final byte[][] board;
    private final long root;
    private boolean skip;
    private List<Move> next;
    private int score;

    Move(long root, int score, List<Move> next, byte whoseTurn, byte[][] board)
    {
        this.root = root;
        this.score = Math.max(0, score);
        this.next = next;
        this.whoseTurn = whoseTurn;
        this.board = board;
    }

    public long getRoot()
    {
        return root;
    }

    public int getScore()
    {
        return score;
    }

    public void setScore(int score)
    {
        this.score = Math.max(0, score);
    }

    public List<Move> getNext()
    {
        return next;
    }

    public void setNext(List<Move> next)
    {
        this.next = next;
    }

    public byte getWhoseTurn()
    {
        return whoseTurn;
    }

    public boolean shouldSkipNext()
    {
        return skip;
    }

    public void setSkipNext()
    {
        skip = true;
    }

    public byte[][] getBoard()
    {
        return board;
    }

    @Override
    public int compareTo(Integer score)
    {
        return score - this.score;
    }

    public static int sortComparator(Move l, Move r)
    {
        return r.getScore() - l.getScore();
    }
}
