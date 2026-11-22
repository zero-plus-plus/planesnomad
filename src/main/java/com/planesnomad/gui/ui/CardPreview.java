package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Card previews:
 * - Hover: an enlarged card next to the cursor (the main preview).
 * - Large view: a much bigger card in the middle of the window, toggled with a hotkey (Q by default).
 * - Listeners: e.g. the optional pinned preview panel, which shows the last hovered card.
 *
 * Does nothing visible in headless mode (screenshot tests).
 */
public final class CardPreview {

    /** Hover preview width at the reference resolution. */
    private static final int HOVER_WIDTH = 240;
    /** Large view height as a share of the window height. */
    private static final double LARGE_HEIGHT_SHARE = 0.85;
    /** Gap between the cursor and the preview. */
    private static final int CURSOR_GAP = 18;

    private final List<Consumer<CardSnapshot>> listeners = new ArrayList<>();
    private final boolean headless = GraphicsEnvironment.isHeadless();

    private JWindow hoverWindow;
    private CardCanvas hoverCanvas;
    private JWindow largeWindow;
    private CardCanvas largeCanvas;

    private CardSnapshot hovered;
    private boolean hoverEnabled = true;

    public void addListener(Consumer<CardSnapshot> listener) {
        listeners.add(listener);
    }

    public void setHoverEnabled(boolean enabled) {
        hoverEnabled = enabled;
        if (!enabled) {
            hideHover();
        }
    }

    public CardSnapshot hovered() {
        return hovered;
    }

    /** Mouse entered or moved over a card. */
    public void hover(CardSnapshot card, Component source, Point pointInSource) {
        if (card == null || card.isHidden()) {
            exit();
            return;
        }
        hovered = card;
        listeners.forEach(l -> l.accept(card));
        if (headless || !hoverEnabled || isLargeShowing()) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(source);
        if (hoverWindow == null || hoverWindow.getOwner() != owner) {
            hoverWindow = new JWindow(owner);
            hoverCanvas = new CardCanvas();
            hoverWindow.setContentPane(hoverCanvas);
            hoverWindow.setFocusableWindowState(false);
        }
        int cw = Theme.px(HOVER_WIDTH);
        int ch = (int) (cw / Theme.CARD_ASPECT);
        java.awt.Dimension size = InspectPainter.size(card, cw, ch);
        int w = size.width;
        int h = size.height;
        hoverCanvas.card = card;
        hoverWindow.setSize(w, h);

        Point screen = new Point(pointInSource);
        SwingUtilities.convertPointToScreen(screen, source);
        Rectangle bounds = owner != null ? owner.getGraphicsConfiguration().getBounds()
            : new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        int gap = Theme.px(CURSOR_GAP);
        int x = screen.x + gap;
        if (x + w > bounds.x + bounds.width) {
            x = screen.x - gap - w;            // flip to the left of the cursor
        }
        int y = screen.y - h / 2;
        y = Math.max(bounds.y, Math.min(y, bounds.y + bounds.height - h));
        hoverWindow.setLocation(x, y);
        hoverCanvas.repaint();
        if (!hoverWindow.isVisible()) {
            hoverWindow.setVisible(true);
        }
    }

    /** Mouse left the card. */
    public void exit() {
        hovered = null;
        hideHover();
    }

    private void hideHover() {
        if (hoverWindow != null) {
            hoverWindow.setVisible(false);
        }
    }

    public boolean isLargeShowing() {
        return largeWindow != null && largeWindow.isVisible();
    }

    /** Hotkey (toggle setting): show the hovered card large, or close the large view. */
    public void toggleLarge(Window owner) {
        if (isLargeShowing()) {
            largeWindow.setVisible(false);
            return;
        }
        openLarge(owner);
    }

    /** Hotkey (hold setting, the default): show the hovered card large while the key is held. */
    public void openLarge(Window owner) {
        if (isLargeShowing()) {
            return;
        }
        if (headless || hovered == null || owner == null) {
            return;
        }
        hideHover();
        if (largeWindow == null || largeWindow.getOwner() != owner) {
            largeWindow = new JWindow(owner);
            largeCanvas = new CardCanvas();
            largeWindow.setContentPane(largeCanvas);
            largeWindow.setFocusableWindowState(false);
        }
        int ch = (int) (owner.getHeight() * LARGE_HEIGHT_SHARE);
        int cw = (int) (ch * Theme.CARD_ASPECT);
        java.awt.Dimension size = InspectPainter.size(hovered, cw, ch);
        // Keep the whole inspection inside the window: shrink if the text panel makes it too big.
        double fit = Math.min(1.0, Math.min(owner.getWidth() * 0.95 / size.width, owner.getHeight() * 0.95 / size.height));
        int w = (int) (size.width * fit);
        int h = (int) (size.height * fit);
        largeCanvas.card = hovered;
        largeWindow.setSize(w, h);
        largeWindow.setLocation(owner.getX() + (owner.getWidth() - w) / 2, owner.getY() + (owner.getHeight() - h) / 2);
        largeWindow.setVisible(true);
    }

    public void closeLarge() {
        if (largeWindow != null) {
            largeWindow.setVisible(false);
        }
    }

    /** Paints one card filling the component. */
    public static final class CardCanvas extends JComponent {
        CardSnapshot card;

        public CardCanvas() {
            setOpaque(true);
            setBackground(Color.BLACK);
        }

        public void setCard(CardSnapshot card) {
            this.card = card;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            int w = Theme.px(200);
            return new Dimension(w, (int) (w / Theme.CARD_ASPECT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(getParent() != null ? getParent().getBackground() : Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (card == null) {
                return;
            }
            // Draw the card for inspection (current text, granted abilities in color) at a
            // reference size, scaled to fit the component.
            int refW = 300;
            int refH = (int) (refW / Theme.CARD_ASPECT);
            java.awt.Dimension need = InspectPainter.size(card, refW, refH);
            double scale = Math.min(getWidth() / (double) need.width, getHeight() / (double) need.height);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate((getWidth() - need.width * scale) / 2, (getHeight() - need.height * scale) / 2);
            g2.scale(scale, scale);
            InspectPainter.paint(g2, card, 0, 0, refW, refH);
            g2.dispose();
        }
    }
}
