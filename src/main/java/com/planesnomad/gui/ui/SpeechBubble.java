package com.planesnomad.gui.ui;

import com.planesnomad.gui.ui.theme.Skin;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * What an enemy says (its barks): a speech bubble at the left edge of the board, its tail
 * pointing at the enemy's portrait. It fades out by itself after a few seconds (longer for longer
 * lines); a click closes it at once. Themable element {@code bubble} (fill, border, text color).
 */
public final class SpeechBubble extends JComponent {

    private static final int MAX_WIDTH = 300;
    private String text;
    private List<String> lines = List.of();
    private final javax.swing.Timer hide = new javax.swing.Timer(4000, e -> setVisible(false));

    public SpeechBubble() {
        setOpaque(false);
        setVisible(false);
        hide.setRepeats(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setVisible(false);
            }
        });
    }

    /** Says this line (null hides the bubble). */
    public void say(String line) {
        hide.stop();
        text = line;
        if (line == null) {
            setVisible(false);
            return;
        }
        lines = wrap(line);
        setSize(getPreferredSize());
        setVisible(true);
        hide.setInitialDelay(Math.max(4000, 70 * line.length()));
        hide.start();
        repaint();
    }

    public String text() {
        return isVisible() ? text : null;
    }

    private int tail() {
        return Theme.px(12);
    }

    private int pad() {
        return Theme.px(10);
    }

    private Font font() {
        return Theme.font(Font.ITALIC, 13);
    }

    private List<String> wrap(String line) {
        FontMetrics fm = getFontMetrics(font());
        int max = Theme.px(MAX_WIDTH) - 2 * pad() - tail();
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : ("“" + line + "”").split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(next) > max && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(font());
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, fm.stringWidth(l));
        }
        return new Dimension(w + 2 * pad() + tail(), lines.size() * fm.getHeight() + 2 * pad());
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (text == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int t = tail();
        int w = getWidth() - t;
        int h = getHeight();
        // Tail on the left, pointing at the portrait; drawn first so the bubble's border covers its root.
        int cy = Math.min(h - Theme.px(10), Theme.px(18));
        Polygon tri = new Polygon(new int[] {0, t + Theme.px(2), t + Theme.px(2)},
            new int[] {cy, cy - Theme.px(7), cy + Theme.px(7)}, 3);
        g.setColor(Skin.fill("bubble"));
        g.fillPolygon(tri);
        Skin.paint(g, "bubble", t, 0, w, h);
        g.setFont(font());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Skin.text("bubble", new java.awt.Color(0x1C1C1C)));
        int y = pad() + fm.getAscent();
        for (String l : lines) {
            g.drawString(l, t + pad(), y);
            y += fm.getHeight();
        }
        g.dispose();
    }
}
