package io.github.thomashuss.reversi;

public class CopyOnWriteBoard
{
    private byte[][] board;
    private int pieceCount;

    public void setBoard(byte[][] board)
    {
        this.board = board;
        pieceCount = 0;
    }

    public int getPieceCount()
    {
        return pieceCount;
    }

    public byte[][] getBoard()
    {
        return board;
    }

    public byte at(int row, int col)
    {
        return board[row][col];
    }

    public void mark(int row, int col, byte color)
    {
        if (pieceCount++ == 0) copy();
        board[row][col] = color;
    }

    private void copy()
    {
        final int cols = board[0].length;
        byte[][] copied = new byte[board.length][cols];
        for (int i = 0; i < board.length; i++) {
            System.arraycopy(board[i], 0, copied[i], 0, cols);
        }
        board = copied;
    }
}
