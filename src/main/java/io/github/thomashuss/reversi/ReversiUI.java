package io.github.thomashuss.reversi;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutionException;

public class ReversiUI
        extends JFrame
{
    private static final String DARK = "●";
    private static final String LIGHT = "○";
    private static final String[] PIECES = {"", DARK, LIGHT};
    private static final String[] COLORS = {"Dark", "Light"};
    private static final Dimension BOARD_DIM = new Dimension(600, 600);
    private static final Font BTN_FONT = new Font(null, Font.PLAIN, 24);
    private static final int COMPUTER_DELAY = 1000;
    private static final int INDICATOR_DELAY = 300;
    private static final Color DEFAULT_COLOR = Color.LIGHT_GRAY;
    private static final Color INDICATOR_COLOR = Color.YELLOW;

    private final JButton[][] board;
    private final ArrayDeque<JButton> indicated;
    private final Timer computerPlayTimer;
    private final Timer indicateTimer;
    private final DefaultListModel<String> log;
    private final JList<String> logList;
    private final JLabel alphaLabel;
    private final JSlider alphaSlider;
    private final Reversi reversi;
    private boolean thinking;

    private ReversiUI()
    {
        super("Reversi! Can you believe it?");
        reversi = new Reversi(this::log);

        JPanel logPanel = new JPanel();
        logPanel.setLayout(new BorderLayout());
        log = new DefaultListModel<>();
        log.addElement("Welcome to Reversi.  Use the slider at the top to adjust the sensitivity to your playing style.");
        logList = new JList<>(log);
        logList.setFont(Font.decode(Font.MONOSPACED));
        JScrollPane logScrollPane = new JScrollPane(logList);
        logPanel.add(logScrollPane, BorderLayout.CENTER);

        computerPlayTimer = new Timer(COMPUTER_DELAY, actionEvent -> computerPlay());
        computerPlayTimer.setRepeats(false);
        indicateTimer = new Timer(INDICATOR_DELAY, actionEvent -> unIndicate());
        indicateTimer.setRepeats(false);
        final JPanel boardPanel = new JPanel();
        boardPanel.setLayout(new GridLayout(Reversi.ROWS, Reversi.COLS));
        boardPanel.setPreferredSize(BOARD_DIM);

        board = new JButton[Reversi.ROWS][Reversi.COLS];
        JButton btn;
        for (int i = 0; i < Reversi.ROWS; i++) {
            for (int j = 0; j < Reversi.COLS; j++) {
                boardPanel.add(btn = board[i][j] = new JButton(PIECES[reversi.at(i, j)]));
                btn.setFont(BTN_FONT);
                btn.setBackground(DEFAULT_COLOR);
                btn.addActionListener(new PlayListener(i, j));
            }
        }
        indicated = new ArrayDeque<>(1);

        JPanel topPanel = new JPanel();
        JButton resetBtn = new JButton("New game");
        resetBtn.addActionListener(actionEvent -> reset());
        alphaSlider = new JSlider(SwingConstants.HORIZONTAL,
                0, 100, (int) (Reversi.DEFAULT_ALPHA * 100));
        alphaSlider.setEnabled(false);
        alphaSlider.addChangeListener(this::setAlpha);
        alphaLabel = new JLabel(String.valueOf(Reversi.DEFAULT_ALPHA));
        topPanel.add(resetBtn);
        topPanel.add(alphaSlider);
        topPanel.add(alphaLabel);

        add(topPanel, BorderLayout.PAGE_START);
        add(boardPanel, BorderLayout.CENTER);
        add(logPanel, BorderLayout.PAGE_END);
        pack();
    }

    private void init()
    {
        reversi.setHumanColor((byte) (JOptionPane.showOptionDialog(this,
                        "Select your color:", "New game",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, COLORS, COLORS[0]) + 1));
        new InitWorker().execute();
    }

    private void reset()
    {
        if (!thinking) {
            reversi.reset();
            fillBoard();
            init();
        }
    }

    private void log(String s)
    {
        log.addElement(s);
        final int max = log.size() - 1;
        logList.scrollRectToVisible(logList.getCellBounds(max, max));
    }

    private void setAlpha(ChangeEvent changeEvent)
    {
        double a = (double) ((JSlider) changeEvent.getSource()).getValue() / 100;
        reversi.setAlpha(a);
        alphaLabel.setText(String.valueOf(a));
    }

    private void fillBoard()
    {
        synchronized (reversi) {
            for (int i = 0; i < Reversi.ROWS; i++) {
                for (int j = 0; j < Reversi.COLS; j++) {
                    board[i][j].setText(PIECES[reversi.at(i, j)]);
                }
            }
        }
    }

    private void computerPlay()
    {
        long played = reversi.computerPlay();
        fillBoard();
        indicate(Reversi.getRow(played), Reversi.getCol(played));
        new ThinkWorker(false).execute();
    }

    private void indicate(int row, int col)
    {
        JButton btn = board[row][col];
        btn.setBackground(INDICATOR_COLOR);
        indicated.add(btn);
        indicateTimer.start();
    }

    private void unIndicate()
    {
        JButton b;
        if ((b = indicated.poll()) != null) {
            b.setBackground(DEFAULT_COLOR);
        }
    }

    private void endgame(int dark, int light)
    {
        log("The game is over.      Dark's score: " + dark + "      Light's score: " + light);
    }

    private static void createAndShowGUI()
    {
        ReversiUI ui = new ReversiUI();
        ui.setVisible(true);
        ui.setDefaultCloseOperation(EXIT_ON_CLOSE);
        ui.init();
    }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(ReversiUI::createAndShowGUI);
    }

    private class InitWorker
            extends SwingWorker<Void, Void>
    {
        private InitWorker()
        {
            thinking = true;
        }

        @Override
        protected Void doInBackground()
        {
            reversi.init();
            return null;
        }

        @Override
        protected void done()
        {
            try {
                get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            thinking = false;
            alphaSlider.setEnabled(true);
            byte humanColor = reversi.getHumanColor();
            log("New game with human as " + COLORS[humanColor - 1] + ".");
            if (humanColor == Reversi.LIGHT) computerPlayTimer.start();
        }
    }

    private class ThinkWorker
            extends SwingWorker<Void, Void>
    {
        private final boolean invokedByHuman;

        private ThinkWorker(boolean invokedByHuman)
        {
            this.invokedByHuman = invokedByHuman;
            thinking = true;
        }

        @Override
        protected Void doInBackground()
        throws Endgame, SkipNextMove
        {
            reversi.think();
            return null;
        }

        @Override
        protected void done()
        {
            thinking = false;
            try {
                get();
                if (invokedByHuman) computerPlayTimer.start();
            } catch (InterruptedException | ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Endgame eg) {
                    endgame(eg.getDarkScore(), eg.getLightScore());
                } else if (cause instanceof SkipNextMove) {
                    if (invokedByHuman) {
                        log("I have no legal moves.  Go again.");
                    } else {
                        log("You have no legal moves.  I will go again.");
                        computerPlayTimer.start();
                    }
                } else throw new RuntimeException(e);
            }
        }
    }

    private class PlayListener
            implements ActionListener
    {
        private final int row;
        private final int col;

        private PlayListener(int row, int col)
        {
            this.row = row;
            this.col = col;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent)
        {
            if (!thinking && reversi.inGame()) {
                if (reversi.play(row, col)) {
                    fillBoard();
                    new ThinkWorker(true).execute();
                } else {
                    JOptionPane.showMessageDialog(ReversiUI.this, "Illegal move.");
                }
            }
        }
    }
}
