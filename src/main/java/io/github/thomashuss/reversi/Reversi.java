package io.github.thomashuss.reversi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Reversi
{
    public static final double DEFAULT_ALPHA = 0.4;
    private static final List<Long> EMPTY_L = List.of();
    private static final List<Move> EMPTY_M = List.of();
    private static final byte MAX_DEPTH = 4;
    static final int ROWS = 8;
    static final int COLS = 8;
    static final byte DARK = 1;
    static final byte LIGHT = 2;

    private final byte myColor;
    private boolean game;
    private byte[][] board;
    private byte lastColor;
    private double humanAvg;
    private double alpha = DEFAULT_ALPHA;
    private Map<Long, Move> currentMoves;
    private List<Move> moveList;
    private Move lastMove;

    public Reversi(byte computerColor)
    {
        myColor = computerColor;
        reset();
    }

    public boolean inGame()
    {
        return game;
    }

    public void reset()
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
    }

    public void setAlpha(double alpha)
    {
        this.alpha = alpha;
    }

    public byte at(int row, int col)
    {
        return board[row][col];
    }

    public boolean play(int row, int col, Consumer<String> logger)
    {
        if (lastMove != null) throw new RuntimeException();
        if (lastColor != otherColor(myColor)) {
            Move m = currentMoves.get(encode(row, col));
            if (m != null) {
                board = m.getBoard();
                lastColor = otherColor(myColor);
                int max = moveList.get(0).getScore();
                double thisScore = max == 0 ? 0.0 : (double) m.getScore() / max;
                double oldHumanAvg = humanAvg;
                updateHumanAvg(thisScore);
                logger.accept("Your average is " + oldHumanAvg + ".  Your move scored " + thisScore
                        + ".  Your new average is " + humanAvg + ".");
                lastMove = m;
                return true;
            }
        }
        return false;
    }

    public long computerPlay(Consumer<String> logger)
    {
        if (lastMove != null) throw new RuntimeException();
        if (lastColor != myColor && !moveList.isEmpty()) {
            lastColor = myColor;
            int idx = Collections.binarySearch(moveList, (int) Math.ceil(humanAvg * moveList.get(0).getScore()));
            if (idx < 0) idx = Math.min(-idx - 1, moveList.size() - 1);
            Move m = moveList.get(idx);
            int max = moveList.get(0).getScore();
            synchronized (this) {
                board = m.getBoard();
            }
            logger.accept("Human average is " + humanAvg + ".  Choosing move of score "
                    + (max == 0 ? 0.0 : (double) m.getScore() / max) + " from " + moveList.size()
                    + " possibilities.");
            lastMove = m;
            return m.getRoot();
        }
        return -1;
    }

    /**
     * Invoke on a separate thread after each play.
     */
    public void think()
    throws Endgame, SkipNextMove
    {
        if (lastMove != null) thinkAbout(lastMove);
        lastMove = null;
    }

    /**
     * Invoke on a separate thread before starting the game.
     */
    public void init()
    {
        byte[][] board = new byte[ROWS][COLS];
        synchronized (this) {
            for (int i = 0; i < ROWS; i++) {
                System.arraycopy(this.board[i], 0, board[i], 0, COLS);
            }
        }
        List<Move> thoughts = think(DARK, board, (byte) 0);
        thoughts.sort(Move::sortComparator);
        if (myColor == DARK) {
            synchronized (this) {
                moveList = thoughts;
            }
        } else {
            Map<Long, Move> currentMoves = new HashMap<>(thoughts.size());
            for (Move m : thoughts) {
                currentMoves.put(m.getRoot(), m);
            }
            synchronized (this) {
                moveList = thoughts;
                this.currentMoves = currentMoves;
            }
        }
        game = true;
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

    private static void reconsider(Move m)
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
                if (!next.isEmpty()) m.setSkipNext();
                else next = EMPTY_M;
                m.setNext(next);
            }
        } else if (!next.isEmpty()) {
            int oldScore = m.getScore() + next.get(0).getScore();
            for (Move n : next) {
                reconsider(n);
            }
            next.sort(Move::sortComparator);
            m.setScore(oldScore - next.get(0).getScore());
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
    private static List<Move> think(byte color, byte[][] board, byte depth)
    {
        byte[][] testBoard;
        List<Long> testPieces;
        int score;
        List<Move> childMoves;
        List<Move> moves = new ArrayList<>();

        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                testPieces = getPieces(board, color, i, j);

                if (!testPieces.isEmpty()) {
                    testBoard = new byte[ROWS][COLS];
                    for (int r = 0; r < ROWS; r++) {
                        System.arraycopy(board[r], 0, testBoard[r], 0, COLS);
                    }

                    for (long l : testPieces) {
                        testBoard[getRow(l)][getCol(l)] = color;
                    }

                    childMoves = null;
                    score = testPieces.size();
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

    private static List<Long> lazilyCreatePieceList(List<Long> list, long l)
    {
        if (list.isEmpty()) list = new ArrayList<>(ROWS);
        list.add(l);
        return list;
    }

    private static List<Long> getPieces(byte[][] board, byte color, int row, int col)
    {
        final byte other = otherColor(color);
        if (!couldBeLegal(board, other, row, col)) return EMPTY_L;
        List<Long> pieces = EMPTY_L;
        int i, j;

        // check down
        i = row + 1;
        while (i < ROWS && board[i][col] == other) {
            i++;
        }
        if (i < ROWS && board[i][col] == color) {
            for (int k = row + 1; k < i; k++) pieces = lazilyCreatePieceList(pieces, encode(k, col));
        }

        // check up
        i = row - 1;
        while (i >= 0 && board[i][col] == other) {
            i--;
        }
        if (i >= 0 && board[i][col] == color) {
            for (int k = row - 1; k > i; k--) pieces = lazilyCreatePieceList(pieces, encode(k, col));
        }

        // check left
        i = col - 1;
        while (i >= 0 && board[row][i] == other) {
            i--;
        }
        if (i >= 0 && board[row][i] == color) {
            for (int k = col - 1; k > i; k--) pieces = lazilyCreatePieceList(pieces, encode(row, k));
        }

        // check right
        i = col + 1;
        while (i < COLS && board[row][i] == other) {
            i++;
        }
        if (i < COLS && board[row][i] == color) {
            for (int k = col + 1; k < i; k++) pieces = lazilyCreatePieceList(pieces, encode(row, k));
        }

        // check up-left
        i = row - 1;
        j = col - 1;
        while (i >= 0 && j >= 0 && board[i][j] == other) {
            i--;
            j--;
        }
        if (i >= 0 && j >= 0 && board[i][j] == color) {
            for (int k = row - 1, l = col - 1; k > i && l > j; k--, l--)
                pieces = lazilyCreatePieceList(pieces, encode(k, l));
        }

        // check up-right
        i = row - 1;
        j = col + 1;
        while (i >= 0 && j < COLS && board[i][j] == other) {
            i--;
            j++;
        }
        if (i >= 0 && j < COLS && board[i][j] == color) {
            for (int k = row - 1, l = col + 1; k > i && l < j; k--, l++)
                pieces = lazilyCreatePieceList(pieces, encode(k, l));
        }

        // check down-right
        i = row + 1;
        j = col + 1;
        while (i < ROWS && j < COLS && board[i][j] == other) {
            i++;
            j++;
        }
        if (i < ROWS && j < COLS && board[i][j] == color) {
            for (int k = row + 1, l = col + 1; k < i && l < j; k++, l++)
                pieces = lazilyCreatePieceList(pieces, encode(k, l));
        }

        // check down-left
        i = row + 1;
        j = col - 1;
        while (i < ROWS && j >= 0 && board[i][j] == other) {
            i++;
            j--;
        }
        if (i < ROWS && j >= 0 && board[i][j] == color) {
            for (int k = row + 1, l = col - 1; k < i && l > j; k++, l--)
                pieces = lazilyCreatePieceList(pieces, encode(k, l));
        }

        if (!pieces.isEmpty()) pieces.add(encode(row, col));
        return pieces;
    }

    private static byte otherColor(byte color)
    {
        return color == DARK ? LIGHT : color == LIGHT ? DARK : 0;
    }

    private static long encode(int row, int col)
    {
        return (((long) row) << 32) | (col & 0xffffffffL);
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
