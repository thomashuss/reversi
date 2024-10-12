package io.github.thomashuss.reversi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Reversi
{
    private static final List<Move> EMPTY_M = List.of();
    private static final byte MAX_DEPTH = 4;
    public static final byte DARK = 1;
    public static final byte LIGHT = 2;
    public static final double DEFAULT_ALPHA = 0.4;
    public static final int COLS = 8;
    public static final int ROWS = 8;

    private final CopyOnWriteBoard[] boards;
    private final Consumer<String> logger;
    private byte myColor;
    private boolean game;
    private byte[][] board;
    private byte lastColor;
    private double humanAvg;
    private double alpha = DEFAULT_ALPHA;
    private Map<Long, Move> currentMoves;
    private List<Move> moveList;
    private Move lastMove;

    public Reversi(Consumer<String> logger)
    {
        this.logger = logger;
        boards = new CopyOnWriteBoard[MAX_DEPTH + 1];
        for (int i = 0; i <= MAX_DEPTH; i++) {
            boards[i] = new CopyOnWriteBoard();
        }
        reset();
    }

    public boolean inGame()
    {
        return game;
    }

    public void setHumanColor(byte humanColor)
    {
        synchronized (this) {
            if (game) throw new RuntimeException();
        }
        myColor = otherColor(humanColor);
    }

    public byte getHumanColor()
    {
        return otherColor(myColor);
    }

    public synchronized void reset()
    {
        board = new byte[ROWS][COLS];
        final int midRow = ROWS / 2;
        final int midCol = COLS / 2;
        board[midRow - 1][midCol] = board[midRow][midCol - 1] = DARK;
        board[midRow - 1][midCol - 1] = board[midRow][midCol] = LIGHT;
        lastColor = 0;
        humanAvg = 0.5;
        currentMoves = null;
        moveList = null;
        lastMove = null;
        game = false;
    }

    public void setAlpha(double alpha)
    {
        this.alpha = alpha;
    }

    public byte at(int row, int col)
    {
        return board[row][col];
    }

    public synchronized boolean play(int row, int col)
    {
        if (lastMove != null) throw new RuntimeException();
        if (currentMoves != null && lastColor != otherColor(myColor)) {
            Move m = currentMoves.get(encode(row, col));
            if (m != null) {
                board = m.getBoard();
                lastColor = otherColor(myColor);
                double thisScore = computeScore(m.getScore(),
                        moveList.get(0).getScore(), moveList.get(moveList.size() - 1).getScore());
                double oldHumanAvg = humanAvg;
                updateHumanAvg(thisScore);
                logger.accept("H: " + encodeNotation(row, col)
                        + "   Your average is " + oldHumanAvg + ".  Your move scored " + thisScore
                        + ".  Your new average is " + humanAvg + ".");
                lastMove = m;
                currentMoves = null;
                return true;
            }
        }
        return false;
    }

    public synchronized long computerPlay()
    {
        if (lastMove != null) throw new RuntimeException();
        if (lastColor != myColor && !moveList.isEmpty()) {
            lastColor = myColor;
            double target = humanAvg >= 0 ? Math.ceil(humanAvg * moveList.get(0).getScore())
                    : Math.floor(humanAvg * moveList.get(moveList.size() - 1).getScore());
            int idx = Collections.binarySearch(moveList, (int) target);
            if (idx < 0) idx = Math.min(-idx - 1, moveList.size() - 1);
            Move m = moveList.get(idx);
            board = m.getBoard();
            long root = m.getRoot();
            logger.accept("C: " + encodeNotation(getRow(root), getCol(root))
                    + "   Human average is " + humanAvg + ".  Choosing move of score "
                    + computeScore(m.getScore(), moveList.get(0).getScore(), moveList.get(moveList.size() - 1).getScore())
                    + " from " + moveList.size() + " possibilities.");
            lastMove = m;
            return root;
        }
        return -1;
    }

    /**
     * Invoke on a separate thread after each play.
     */
    public synchronized void think()
    throws Endgame, SkipNextMove
    {
        try {
            if (lastMove != null) thinkAbout(lastMove);
        } finally {
            lastMove = null;
        }
    }

    /**
     * Invoke on a separate thread before starting the game.
     */
    public void init()
    {
        if (myColor == 0) throw new RuntimeException();
        List<Move> thoughts = think(DARK, board, (byte) 0);
        thoughts.sort(Move::sortComparator);
        if (myColor == DARK) {
            synchronized (this) {
                moveList = thoughts;
                game = true;
            }
        } else {
            Map<Long, Move> currentMoves = new HashMap<>(thoughts.size());
            for (Move m : thoughts) {
                currentMoves.put(m.getRoot(), m);
            }
            synchronized (this) {
                moveList = thoughts;
                this.currentMoves = currentMoves;
                game = true;
            }
        }
    }

    private void updateHumanAvg(double d)
    {
        humanAvg += alpha * (d - humanAvg);
    }

    /**
     * The move <code>m</code> was just made; proceed to the next layer and update the move cache.
     *
     * @param m most recent move
     */
    private void thinkAbout(Move m)
    throws SkipNextMove, Endgame
    {
        Map<Long, Move> nextMoves;
        List<Move> nextMoveList = m.getNext();

        if (nextMoveList.isEmpty()) {
            game = false;
            endgame(m.getBoard());
        } else {
            if (m.shouldSkipNext()) {
                lastColor = otherColor(lastColor);
            }
            if (otherColor(lastColor) == myColor) {
                for (Move n : nextMoveList) {
                    reconsider(n);
                }
                nextMoveList.sort(Move::sortComparator);
                synchronized (this) {
                    moveList = nextMoveList;
                }
            } else {
                nextMoves = new HashMap<>(nextMoveList.size());
                for (Move n : nextMoveList) {
                    nextMoves.put(n.getRoot(), n);
                    reconsider(n);
                }
                nextMoveList.sort(Move::sortComparator);
                synchronized (this) {
                    currentMoves = nextMoves;
                    moveList = nextMoveList;
                }
            }
        }
        if (m.shouldSkipNext()) {
            throw new SkipNextMove();
        }
    }

    private static void endgame(byte[][] board)
    throws Endgame
    {
        int dark = 0, light = 0;
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                switch (board[i][j]) {
                    case DARK -> dark++;
                    case LIGHT -> light++;
                }
            }
        }
        throw new Endgame(dark, light);
    }

    private void reconsider(Move m)
    {
        List<Move> next = m.getNext();
        if (next == null) {
            next = think(otherColor(m.getWhoseTurn()), m.getBoard(), MAX_DEPTH);
            if (!next.isEmpty()) {
                next.sort(Move::sortComparator);
                m.setScore(m.getScore() - next.get(0).getScore());
                m.setNext(next);
            } else if (!m.shouldSkipNext()) {
                next = think(m.getWhoseTurn(), m.getBoard(), MAX_DEPTH);
                if (next.isEmpty()) {
                    next = EMPTY_M;
                } else {
                    next.sort(Move::sortComparator);
                    m.setSkipNext();
                    m.setScore(m.getScore() + next.get(0).getScore());
                }
                m.setNext(next);
            }
        } else if (!next.isEmpty()) {
            int oldScore = m.shouldSkipNext() ? m.getScore() - next.get(0).getScore()
                    : m.getScore() + next.get(0).getScore();
            for (Move n : next) {
                reconsider(n);
            }
            next.sort(Move::sortComparator);
            m.setScore(m.shouldSkipNext() ? oldScore + next.get(0).getScore() : oldScore - next.get(0).getScore());
        }
    }

    /**
     * Recursively generate all possible outcomes for the given color and board.  Does not allow depth
     * to exceed <code>MAX_DEPTH</code>; e.g., for <code>MAX_DEPTH = 2</code>, 3 layers will be generated.
     *
     * @param color color for which moves are to be generated
     * @param board current state of the board
     * @param depth recursion depth
     * @return all possible moves for the given color and board
     */
    private List<Move> think(byte color, byte[][] board, byte depth)
    {
        byte[][] testBoard;
        CopyOnWriteBoard cowBoard = boards[depth];
        int score;
        List<Move> childMoves;
        List<Move> moves = new ArrayList<>();

        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                cowBoard.setBoard(board);
                getBoardAfterMove(cowBoard, color, i, j);

                if ((score = cowBoard.getPieceCount()) != 0) {
                    testBoard = cowBoard.getBoard();
                    childMoves = null;
                    if (depth != MAX_DEPTH) {
                        childMoves = think(otherColor(color), testBoard, (byte) (depth + 1));
                        if (childMoves.isEmpty()) childMoves = EMPTY_M;
                        else {
                            childMoves.sort(Move::sortComparator);
                            score -= childMoves.get(0).getScore();
                        }
                    }
                    moves.add(new Move(encode(i, j), score, childMoves, color, testBoard));
                }
            }
        }
        cowBoard.forgetBoard();
        return moves;
    }

    private static boolean check(byte[][] board, byte color, int row, int col)
    {
        return row >= 0 && col >= 0 && row < ROWS && col < COLS && board[row][col] == color;
    }

    private static boolean couldBeLegal(byte[][] board, byte color, int row, int col)
    {
        return check(board, (byte) 0, row, col) && (check(board, color, row - 1, col) || check(board, color, row + 1, col)
                || check(board, color, row, col - 1) || check(board, color, row, col + 1)
                || check(board, color, row - 1, col - 1) || check(board, color, row + 1, col + 1)
                || check(board, color, row - 1, col + 1) || check(board, color, row + 1, col - 1));
    }

    private static void getBoardAfterMove(CopyOnWriteBoard board, byte color, int row, int col)
    {
        final byte other = otherColor(color);
        if (!couldBeLegal(board.getBoard(), other, row, col)) return;
        int i, j;

        // check down
        i = row + 1;
        while (i < ROWS && board.at(i, col) == other) {
            i++;
        }
        if (i < ROWS && board.at(i, col) == color) {
            for (int k = row + 1; k < i; k++)
                board.mark(k, col, color);
        }

        // check up
        i = row - 1;
        while (i >= 0 && board.at(i, col) == other) {
            i--;
        }
        if (i >= 0 && board.at(i, col) == color) {
            for (int k = row - 1; k > i; k--)
                board.mark(k, col, color);
        }

        // check left
        i = col - 1;
        while (i >= 0 && board.at(row, i) == other) {
            i--;
        }
        if (i >= 0 && board.at(row, i) == color) {
            for (int k = col - 1; k > i; k--)
                board.mark(row, k, color);
        }

        // check right
        i = col + 1;
        while (i < COLS && board.at(row, i) == other) {
            i++;
        }
        if (i < COLS && board.at(row, i) == color) {
            for (int k = col + 1; k < i; k++)
                board.mark(row, k, color);
        }

        // check up-left
        i = row - 1;
        j = col - 1;
        while (i >= 0 && j >= 0 && board.at(i, j) == other) {
            i--;
            j--;
        }
        if (i >= 0 && j >= 0 && board.at(i, j) == color) {
            for (int k = row - 1, l = col - 1; k > i && l > j; k--, l--)
                board.mark(k, l, color);
        }

        // check up-right
        i = row - 1;
        j = col + 1;
        while (i >= 0 && j < COLS && board.at(i, j) == other) {
            i--;
            j++;
        }
        if (i >= 0 && j < COLS && board.at(i, j) == color) {
            for (int k = row - 1, l = col + 1; k > i && l < j; k--, l++)
                board.mark(k, l, color);
        }

        // check down-right
        i = row + 1;
        j = col + 1;
        while (i < ROWS && j < COLS && board.at(i, j) == other) {
            i++;
            j++;
        }
        if (i < ROWS && j < COLS && board.at(i, j) == color) {
            for (int k = row + 1, l = col + 1; k < i && l < j; k++, l++)
                board.mark(k, l, color);
        }

        // check down-left
        i = row + 1;
        j = col - 1;
        while (i < ROWS && j >= 0 && board.at(i, j) == other) {
            i++;
            j--;
        }
        if (i < ROWS && j >= 0 && board.at(i, j) == color) {
            for (int k = row + 1, l = col - 1; k < i && l > j; k++, l--)
                board.mark(k, l, color);
        }

        if (board.getPieceCount() != 0) board.mark(row, col, color);
    }

    private static double computeScore(int moveScore, int maxScore, int minScore)
    {
        if (maxScore == 0) {
            if (moveScore < 0) return -1.0;
            else return 1.0;
        }
        if (moveScore >= 0) {
            return (double) moveScore / maxScore;
        }
        return (double) -moveScore / minScore;
    }

    private static byte otherColor(byte color)
    {
        return color == DARK ? LIGHT : color == LIGHT ? DARK : 0;
    }

    private static long encode(int row, int col)
    {
        return (((long) row) << 32) | (col & 0xffffffffL);
    }

    private static String encodeNotation(int row, int col)
    {
        return String.valueOf((char) (col + 'a')) + (row + 1);
    }

    public static int getRow(long l)
    {
        return (int) (l >> 32);
    }

    public static int getCol(long l)
    {
        return (int) l;
    }
}
