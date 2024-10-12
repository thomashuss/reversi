package io.github.thomashuss.reversi;

class CopyOnWriteBoard
{
    private byte[][] board;
    private int pieceCount;

    void setBoard(byte[][] board)
    {
        this.board = board;
        pieceCount = 0;
    }

    void forgetBoard()
    {
        board = null;
    }

    int getPieceCount()
    {
        return pieceCount;
    }

    byte[][] getBoard()
    {
        return board;
    }

    byte at(int row, int col)
    {
        return board[row][col];
    }

    void mark(int row, int col, byte color)
    {
        if (pieceCount++ == 0) copy();
        board[row][col] = color;
    }

    private void copy()
    {
        byte[][] copied = new byte[Reversi.ROWS][Reversi.COLS];
        for (int i = 0; i < Reversi.ROWS; i++) {
            System.arraycopy(board[i], 0, copied[i], 0, Reversi.COLS);
        }
        board = copied;
    }
}
