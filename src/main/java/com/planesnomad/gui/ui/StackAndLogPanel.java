package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.MatchSnapshot.LogLine;
import com.planesnomad.gui.model.MatchSnapshot.StackItem;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Right column: the stack (always visible, plain language, top item first) above the game log.
 */
public final class StackAndLogPanel extends JPanel {

    /** Column width at the reference resolution. */
    private static final int WIDTH = 260;

    private final CardPreview preview;
    private final JLabel stackTitle = title("Stack");
    private final JPanel stackItems = new JPanel();
    private final JLabel logTitle = title("Game log");
    private final JTextPane logText = new JTextPane();
    private int logLinesShown = 0;
    private final JScrollPane logScroll;
    private List<StackItem> lastStack = List.of();
    private JPanel stackSection;
    private boolean stackExpanded;

    public StackAndLogPanel(CardPreview preview) {
        super(new BorderLayout());
        this.preview = preview;
        setBackground(Theme.PANEL);

        stackItems.setLayout(new BoxLayout(stackItems, BoxLayout.Y_AXIS));
        stackItems.setOpaque(false);
        JPanel stack = new JPanel(new BorderLayout());
        stackSection = stack;
        stack.setOpaque(false);
        stack.setVisible(false);   // shown only when the stack is expanded (right-click / Tab)
        stack.add(stackTitle, BorderLayout.NORTH);
        stack.add(stackItems, BorderLayout.CENTER);

        logText.setEditable(false);
        logText.setBackground(Theme.PANEL);
        logText.setFocusable(false);
        logScroll = new JScrollPane(logText, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.getViewport().setBackground(Theme.PANEL);
        JPanel log = new JPanel(new BorderLayout());
        log.setOpaque(false);
        log.add(logTitle, BorderLayout.NORTH);
        log.add(logScroll, BorderLayout.CENTER);

        add(stack, BorderLayout.NORTH);
        add(log, BorderLayout.CENTER);
        // Re-wrap the stack list when the column is resized.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (getWidth() != lastWidth) {
                    lastWidth = getWidth();
                    showStack(lastStack);
                }
            }
        });
        applyFonts();
        showStack(List.of());
    }

    public void applyFonts() {
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.PANEL_EDGE));
        stackTitle.setFont(Theme.font(Font.BOLD, 12));
        logTitle.setFont(Theme.font(Font.BOLD, 12));
        logText.setFont(Theme.font(Font.PLAIN, 11));
        int pad = Theme.px(6);
        logText.setBorder(BorderFactory.createEmptyBorder(0, pad, pad, pad));
        showStack(lastStack);
    }

    /** Show or hide the full text list of the stack above the log. */
    public void setStackExpanded(boolean expanded) {
        stackExpanded = expanded;
        stackSection.setVisible(expanded);
        revalidate();
        repaint();
    }

    public boolean isStackExpanded() {
        return stackExpanded;
    }

    public void update(MatchSnapshot s) {
        if (!s.stack().equals(lastStack)) {
            showStack(s.stack());
        }
        updateLog(s.log());
    }

    private void showStack(List<StackItem> items) {
        lastStack = items;
        stackItems.removeAll();
        int pad = Theme.px(6);
        if (items.isEmpty()) {
            JLabel empty = new JLabel("Empty");
            empty.setFont(Theme.font(Font.ITALIC, 11));
            empty.setForeground(Theme.TEXT_DIM);
            empty.setBorder(BorderFactory.createEmptyBorder(0, pad, pad, pad));
            stackItems.add(empty);
        }
        boolean top = true;
        for (StackItem item : items) {
            stackItems.add(stackEntry(item, top));
            top = false;
        }
        stackItems.revalidate();
        stackItems.repaint();
    }

    private JComponent stackEntry(StackItem item, boolean top) {
        int pad = Theme.px(6);
        JPanel entry = new JPanel(new BorderLayout());
        entry.setBackground(top ? Theme.blend(Theme.PANEL, Theme.ACCENT, 0.12) : Theme.PANEL);
        entry.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, pad, pad / 2, pad),
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, Theme.px(4), 0, 0, item.byYou() ? Theme.YOU : Theme.ENEMY),
                BorderFactory.createEmptyBorder(pad / 2, pad, pad / 2, pad))));
        int thumbW = Theme.px(40);
        int thumbH = Theme.px(30);
        // Wrap to the column's actual width (it can be resized); its default width before layout.
        int columnW = getWidth() > 0 ? getWidth() : Theme.px(WIDTH);
        int textWidth = Math.max(Theme.px(60), columnW - 2 * pad - Theme.px(4) - 2 * pad - Theme.px(14) - thumbW - pad);
        String note = item.resolving() ? "  (resolving)" : top ? "  (resolves next)" : "";
        JComponent headline = wrapped(item.text() + note, Theme.font(Font.BOLD, 11.5), Theme.TEXT, textWidth);
        // The effect text, a little larger than the log so it reads easily (user request).
        JComponent detail = wrapped(item.detail(), Theme.font(Font.PLAIN, 12.5), Theme.TEXT, textWidth);
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(headline, BorderLayout.NORTH);
        text.add(detail, BorderLayout.CENTER);
        // Relics show their relic badge; cards show their art.
        javax.swing.Icon thumbIcon = item.relic() == null ? new ArtIcon(item.source(), thumbW, thumbH)
            : new javax.swing.Icon() {
                @Override
                public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
                    RelicIcon.paintBadge((java.awt.Graphics2D) g, item.relic(), x + (thumbW - thumbH) / 2, y, thumbH);
                }

                @Override
                public int getIconWidth() {
                    return thumbW;
                }

                @Override
                public int getIconHeight() {
                    return thumbH;
                }
            };
        JLabel thumb = new JLabel(thumbIcon);
        thumb.setVerticalAlignment(SwingConstants.TOP);
        thumb.setBorder(BorderFactory.createEmptyBorder(Theme.px(2), 0, 0, pad));
        entry.add(thumb, BorderLayout.WEST);
        entry.add(text, BorderLayout.CENTER);
        entry.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (item.source() != null) {
            MouseAdapter hover = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    preview.hover(item.source(), (Component) e.getSource(), e.getPoint());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    preview.exit();
                }
            };
            for (JComponent c : new JComponent[] {entry, headline, detail, thumb, text}) {
                c.addMouseListener(hover);
                c.addMouseMotionListener(hover);
            }
        }
        return entry;
    }

    private void updateLog(List<LogLine> lines) {
        StyledDocument doc = logText.getStyledDocument();
        try {
            if (lines.size() < logLinesShown) {
                doc.remove(0, doc.getLength());   // new match
                logLinesShown = 0;
            }
            JScrollBar bar = logScroll.getVerticalScrollBar();
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - Theme.px(20);
            for (int i = logLinesShown; i < lines.size(); i++) {
                LogLine line = lines.get(i);
                SimpleAttributeSet style = line.turnHeader() ? headerStyle() : lineStyle();
                if (doc.getLength() > 0) {
                    doc.insertString(doc.getLength(), "\n", style);
                }
                int start = doc.getLength();
                insertWithSymbols(doc, line.text(), style, logText.getFont());
                doc.setParagraphAttributes(start, doc.getLength() - start, style, false);
            }
            logLinesShown = lines.size();
            if (atBottom) {
                logText.setCaretPosition(doc.getLength());
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private SimpleAttributeSet headerStyle() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, Theme.ACCENT);
        StyleConstants.setBold(a, true);
        StyleConstants.setSpaceAbove(a, Theme.px(4));
        StyleConstants.setLeftIndent(a, 0);
        return a;
    }

    private SimpleAttributeSet lineStyle() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, Theme.TEXT);
        StyleConstants.setBold(a, false);
        StyleConstants.setSpaceAbove(a, 0);
        StyleConstants.setLeftIndent(a, Theme.px(8));
        return a;
    }

    /** Read-only text that wraps at exactly the given width (Swing's HTML widths are not exact pixels). */
    private static JComponent wrapped(String text, Font font, java.awt.Color color, int width) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFocusable(false);
        pane.setOpaque(false);
        pane.setFont(font);
        pane.setForeground(color);
        pane.setBorder(null);
        pane.setMargin(new java.awt.Insets(0, 0, 0, 0));
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        try {
            insertWithSymbols(pane.getStyledDocument(), text, style, font);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        pane.setSize(width, Short.MAX_VALUE);          // lets the pane compute its wrapped height
        pane.setPreferredSize(new Dimension(width, pane.getPreferredSize().height));
        return pane;
    }

    /** Appends text, drawing {R}-style symbols as pictures when available (text form otherwise). */
    static void insertWithSymbols(StyledDocument doc, String text, javax.swing.text.AttributeSet style, Font font)
            throws BadLocationException {
        java.util.regex.Matcher m = ManaSymbols.TOKEN.matcher(text);
        int size = Math.max(8, (int) Math.round(font.getSize() * 1.05));
        int last = 0;
        while (m.find()) {
            java.awt.image.BufferedImage img = ManaSymbols.get(m.group(1), size);
            if (img == null) {
                continue;                     // no picture: leave the text form in place
            }
            doc.insertString(doc.getLength(), text.substring(last, m.start()), style);
            SimpleAttributeSet icon = new SimpleAttributeSet(style);
            StyleConstants.setIcon(icon, new javax.swing.ImageIcon(img));
            doc.insertString(doc.getLength(), m.group(), icon);
            last = m.end();
        }
        doc.insertString(doc.getLength(), text.substring(last), style);
    }

    private static JLabel title(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setForeground(Theme.TEXT_DIM);
        int pad = Theme.px(6);
        l.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad / 2, pad));
        return l;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(WIDTH), super.getPreferredSize().height);
    }
}
