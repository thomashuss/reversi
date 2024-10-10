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
        this.score = score;
        this.next = next;
        this.whoseTurn = whoseTurn;
        this.board = board;
    }

    long getRoot()
    {
        return root;
    }

    int getScore()
    {
        return score;
    }

    void setScore(int score)
    {
        this.score = score;
    }

    List<Move> getNext()
    {
        return next;
    }

    void setNext(List<Move> next)
    {
        this.next = next;
    }

    byte getWhoseTurn()
    {
        return whoseTurn;
    }

    boolean shouldSkipNext()
    {
        return skip;
    }

    void setSkipNext()
    {
        skip = true;
    }

    byte[][] getBoard()
    {
        return board;
    }

    @Override
    public int compareTo(Integer score)
    {
        return score - this.score;
    }

    static int sortComparator(Move l, Move r)
    {
        return r.getScore() - l.getScore();
    }
}
