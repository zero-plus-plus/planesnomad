package com.planesnomad.gui.ui;

import com.planesnomad.gui.ui.theme.Skin;
import com.planesnomad.gui.ui.theme.SkinButton;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The question the game is asking you (while you play), with its buttons: "Your turn: play
 * something, or pass  [Pass]". Sits on the board just above your hand. Themed element "prompt".
 *
 * Laid out by hand from the current sizes of its text and buttons, so it always encloses them
 * (whatever the system's fonts and scaling); if it would be wider than the room it has, the
 * question wraps onto more lines instead of running out of the frame.
 */
public final class PromptBar extends JComponent {

    private final JLabel text = new JLabel();
    private final List<SkinButton> buttons = new ArrayList<>();
    private String prompt = "";
    /** Widest the bar may be (set by the screen from the board's width). */
    private int maxWidth = Integer.MAX_VALUE;

    public PromptBar() {
        setLayout(null);
        setOpaque(false);
        add(text);
        setVisible(false);
    }

    /** Shows the question and its buttons, or hides the bar (prompt null). */
    public void show(String prompt, List<PlayInput.Action> actions) {
        if (prompt == null) {
            setVisible(false);
            return;
        }
        this.prompt = prompt;
        text.setFont(Theme.font(Font.BOLD, 13));
        text.setForeground(Skin.text("prompt", Theme.TEXT));
        for (SkinButton b : buttons) {
            remove(b);
        }
        buttons.clear();
        for (PlayInput.Action a : actions) {
            SkinButton b = new SkinButton(a.label() + (a.keyHint() != null ? "  (" + a.keyHint() + ")" : ""));
            b.setFocusable(false);
            b.setFont(Theme.font(a.primary() ? Font.BOLD : Font.PLAIN, 12));
            b.addActionListener(e -> a.run().run());
            buttons.add(b);
            add(b);
        }
        setVisible(true);
        fit();
        repaint();
    }

    /** The room available (e.g. the board's width minus margins); the text wraps to fit it. */
    public void setMaxWidth(int w) {
        if (w != maxWidth) {
            maxWidth = Math.max(Theme.px(200), w);
            if (isVisible()) {
                fit();
            }
        }
    }

    private int pad() {
        return Theme.px(Theme.pack().number("prompt.padding", 10));
    }

    private int gap() {
        return Theme.px(6);
    }

    private int buttonsWidth() {
        int w = 0;
        for (SkinButton b : buttons) {
            w += b.getPreferredSize().width + gap();
        }
        return w;
    }

    /** Sets the text (wrapped if needed) and the bar's size from what it holds now. */
    private void fit() {
        int pad = pad();
        int buttonsW = buttonsWidth();
        text.setText(prompt);
        int textW = text.getPreferredSize().width;
        int full = 2 * pad + textW + gap() * 2 + buttonsW;
        if (full > maxWidth) {
            // Too wide: wrap the question within the room left beside the buttons.
            int room = Math.max(Theme.px(140), maxWidth - 2 * pad - gap() * 2 - buttonsW);
            text.setText("<html><div style='width:" + room + "px'>" + escape(prompt) + "</div></html>");
        }
        setSize(getPreferredSize());
        doLayout();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public Dimension getPreferredSize() {
        int pad = pad();
        Dimension t = text.getPreferredSize();
        int buttonsH = 0;
        for (SkinButton b : buttons) {
            buttonsH = Math.max(buttonsH, b.getPreferredSize().height);
        }
        int w = 2 * pad + t.width + gap() * 2 + buttonsWidth();
        int h = Math.max(t.height, buttonsH) + pad + Theme.px(4);
        return new Dimension(w, Math.max(h, Theme.px(40)));
    }

    @Override
    public void doLayout() {
        int pad = pad();
        int h = getHeight();
        Dimension t = text.getPreferredSize();
        text.setBounds(pad, (h - t.height) / 2, t.width, t.height);
        // Buttons from the right edge, each at its own size, vertically centered.
        int x = getWidth() - pad;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            SkinButton b = buttons.get(i);
            Dimension d = b.getPreferredSize();
            x -= d.width;
            b.setBounds(x, (h - d.height) / 2, d.width, d.height);
            x -= gap();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Skin.paint((Graphics2D) g, "prompt", 0, 0, getWidth(), getHeight());
    }
}
