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
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.ScrollPaneConstants;
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

    public StackAndLogPanel(CardPreview preview) {
        super(new BorderLayout());
        this.preview = preview;
        setBackground(Theme.PANEL);

        stackItems.setLayout(new BoxLayout(stackItems, BoxLayout.Y_AXIS));
        stackItems.setOpaque(false);
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
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
        int textWidth = Theme.px(WIDTH - 50);
        JLabel headline = new JLabel("<html><body style='width: " + textWidth + "px'>" + escape(item.text())
            + (top ? " <span style='color:#9AA0A8;font-weight:normal'>(resolves next)</span>" : "") + "</body></html>");
        headline.setFont(Theme.font(Font.BOLD, 11));
        headline.setForeground(Theme.TEXT);
        JTextArea detail = new JTextArea(item.detail());
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setEditable(false);
        detail.setOpaque(false);
        detail.setFont(Theme.font(Font.PLAIN, 11));
        detail.setForeground(Theme.TEXT_DIM);
        detail.setFocusable(false);
        entry.add(headline, BorderLayout.NORTH);
        entry.add(detail, BorderLayout.CENTER);
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
            for (JComponent c : new JComponent[] {entry, headline, detail}) {
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
                int start = doc.getLength();
                doc.insertString(start, (start == 0 ? "" : "\n") + line.text(), line.turnHeader() ? headerStyle() : lineStyle());
                doc.setParagraphAttributes(doc.getLength() - line.text().length(), line.text().length(),
                    line.turnHeader() ? headerStyle() : lineStyle(), false);
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

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
