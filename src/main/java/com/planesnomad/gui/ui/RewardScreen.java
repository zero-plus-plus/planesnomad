package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;
import com.planesnomad.gui.ui.theme.Skin;
import com.planesnomad.gui.ui.theme.SkinButton;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The screen after a won fight: a list of rewards to claim, Slay-the-Spire style. Click a row to
 * take it: gold and relics are taken at once; "Add a card to your deck" opens a choice of cards
 * (click one, or Skip). Continue leaves the screen (unclaimed rewards are left behind).
 *
 * Covers the board with a dimmed backdrop and takes all clicks while shown. Themable elements:
 * {@code reward.backdrop}, {@code reward} (the panel), {@code reward.item}, {@code reward.item.hover},
 * {@code reward.item.claimed}; icons {@code icon.reward.gold}, {@code icon.reward.cards},
 * {@code icon.reward.relic} (optional pictures replacing the drawn coin / cards; a relic shows its own icon).
 */
public final class RewardScreen extends JComponent {

    /** One reward. Exactly one of the parts is set. */
    public static final class Reward {
        public enum Kind { GOLD, CARDS, RELIC }

        public final Kind kind;
        public final int gold;
        public final List<CardSnapshot> cards;
        public final RelicSnapshot relic;
        private boolean claimed;
        private CardSnapshot chosen;

        private Reward(Kind kind, int gold, List<CardSnapshot> cards, RelicSnapshot relic) {
            this.kind = kind;
            this.gold = gold;
            this.cards = cards == null ? List.of() : List.copyOf(cards);
            this.relic = relic;
        }

        public static Reward gold(int amount) {
            return new Reward(Kind.GOLD, amount, null, null);
        }

        public static Reward cards(List<CardSnapshot> offer) {
            return new Reward(Kind.CARDS, 0, offer, null);
        }

        public static Reward relic(RelicSnapshot r) {
            return new Reward(Kind.RELIC, 0, null, r);
        }

        public boolean claimed() {
            return claimed;
        }

        /** The card picked from a card reward, or null (not yet, or skipped). */
        public CardSnapshot chosen() {
            return chosen;
        }

        String title() {
            return switch (kind) {
                case GOLD -> gold + " Gold";
                case CARDS -> chosen != null ? "Added " + chosen.name() + " to your deck"
                    : claimed ? "Card reward skipped" : "Add a card to your deck";
                case RELIC -> relic.name();
            };
        }

        String subtitle() {
            return switch (kind) {
                case GOLD -> claimed ? "Taken" : "Click to take";
                case CARDS -> claimed ? "" : "Choose 1 of " + cards.size();
                case RELIC -> (claimed ? "Taken - " : "Relic - ") + firstLine(relic.description());
            };
        }

        private static String firstLine(String s) {
            if (s == null) {
                return "";
            }
            String t = s.replace("\r", "").split("\n")[0].trim();
            return t.length() > 70 ? t.substring(0, 68) + "…" : t;
        }
    }

    /** What the player took, reported when they press Continue. */
    public record Outcome(int gold, List<CardSnapshot> cards, List<RelicSnapshot> relics) { }

    private static final int PANEL_W = 440;
    private static final int ROW_H = 58;
    private static final int CARD_W = 170;

    private final CardPreview preview;
    private String title = "Victory!";
    private String subtitle = "";
    private List<Reward> rewards = List.of();
    private Reward choosing;
    private int hoverRow = -1;
    private int hoverCard = -1;
    private final List<Rectangle> rowBoxes = new ArrayList<>();
    private final List<Rectangle> cardBoxes = new ArrayList<>();
    private Rectangle panel = new Rectangle();
    private final SkinButton continueButton = new SkinButton("Continue");
    private final SkinButton skipButton = new SkinButton("Skip");
    private final SkinButton backButton = new SkinButton("Back");
    private Consumer<Outcome> onContinue = o -> { };

    public RewardScreen(CardPreview preview) {
        this.preview = preview;
        setLayout(null);
        setOpaque(false);
        setVisible(false);
        for (SkinButton b : List.of(continueButton, skipButton, backButton)) {
            b.setFocusable(false);
            add(b);
        }
        continueButton.addActionListener(e -> {
            setVisible(false);
            preview.exit();
            onContinue.accept(outcome());
        });
        skipButton.addActionListener(e -> {
            if (choosing != null) {
                choosing.claimed = true;
                choosing = null;
                refresh();
            }
        });
        backButton.addActionListener(e -> {
            choosing = null;
            refresh();
        });
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (choosing != null) {
                    int i = indexAt(cardBoxes, e);
                    if (i >= 0) {
                        choosing.chosen = choosing.cards.get(i);
                        choosing.claimed = true;
                        choosing = null;
                        preview.exit();
                        refresh();
                    }
                    return;
                }
                int i = indexAt(rowBoxes, e);
                if (i >= 0) {
                    Reward r = rewards.get(i);
                    if (r.claimed) {
                        return;
                    }
                    if (r.kind == Reward.Kind.CARDS) {
                        choosing = r;
                    } else {
                        r.claimed = true;
                    }
                    refresh();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int row = choosing == null ? indexAt(rowBoxes, e) : -1;
                int card = choosing != null ? indexAt(cardBoxes, e) : -1;
                if (card >= 0) {
                    preview.hover(choosing.cards.get(card), RewardScreen.this, e.getPoint());
                } else {
                    preview.exit();
                }
                if (row != hoverRow || card != hoverCard) {
                    hoverRow = row;
                    hoverCard = card;
                    repaint();
                }
                setCursor(java.awt.Cursor.getPredefinedCursor(
                    card >= 0 || row >= 0 && !rewards.get(row).claimed ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverCard = -1;
                preview.exit();
                repaint();
            }
        };
        // The screen takes every click while it's shown, so nothing on the board underneath reacts.
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(e -> { });
    }

    /** Shows the screen with these rewards. */
    public void show(String title, String subtitle, List<Reward> rewards, Consumer<Outcome> onContinue) {
        this.title = title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.rewards = List.copyOf(rewards);
        this.onContinue = onContinue == null ? o -> { } : onContinue;
        choosing = null;
        hoverRow = -1;
        hoverCard = -1;
        setVisible(true);
        refresh();
    }

    public List<Reward> rewards() {
        return rewards;
    }

    /** True while the card choice is open. */
    public boolean isChoosingCard() {
        return choosing != null;
    }

    /** Opens the card choice of the first card reward (for tests and screenshots). */
    public void openCardChoice() {
        rewards.stream().filter(r -> r.kind == Reward.Kind.CARDS && !r.claimed).findFirst().ifPresent(r -> {
            choosing = r;
            refresh();
        });
    }

    /** Clicks a row (as the player would), for tests. */
    public void clickRow(int i) {
        Reward r = rewards.get(i);
        if (!r.claimed) {
            if (r.kind == Reward.Kind.CARDS) {
                choosing = r;
            } else {
                r.claimed = true;
            }
            refresh();
        }
    }

    /** Picks a card in the open card choice, for tests. */
    public void pickCard(int i) {
        if (choosing != null) {
            choosing.chosen = choosing.cards.get(i);
            choosing.claimed = true;
            choosing = null;
            refresh();
        }
    }

    public Outcome outcome() {
        int gold = 0;
        List<CardSnapshot> cards = new ArrayList<>();
        List<RelicSnapshot> relics = new ArrayList<>();
        for (Reward r : rewards) {
            if (!r.claimed) {
                continue;
            }
            switch (r.kind) {
                case GOLD -> gold += r.gold;
                case CARDS -> {
                    if (r.chosen != null) {
                        cards.add(r.chosen);
                    }
                }
                case RELIC -> relics.add(r.relic);
            }
        }
        return new Outcome(gold, cards, relics);
    }

    private void refresh() {
        continueButton.setVisible(choosing == null);
        skipButton.setVisible(choosing != null);
        backButton.setVisible(choosing != null);
        doLayout();
        repaint();
    }

    private static int indexAt(List<Rectangle> boxes, MouseEvent e) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(e.getPoint())) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ layout

    private int pad() {
        return Theme.px(18);
    }

    private int headerH() {
        return Theme.px(64);
    }

    private int buttonH() {
        return Theme.px(30);
    }

    @Override
    public void doLayout() {
        int pad = pad();
        int w;
        int h;
        rowBoxes.clear();
        cardBoxes.clear();
        if (choosing == null) {
            w = Theme.px(PANEL_W);
            int rowH = Theme.px(ROW_H);
            int gap = Theme.px(8);
            h = headerH() + rewards.size() * (rowH + gap) + pad + buttonH() + pad;
            int x = (getWidth() - w) / 2;
            int y = Math.max(Theme.px(8), (getHeight() - h) / 2);
            panel = new Rectangle(x, y, w, h);
            int ry = y + headerH();
            for (int i = 0; i < rewards.size(); i++) {
                rowBoxes.add(new Rectangle(x + pad, ry, w - 2 * pad, rowH));
                ry += rowH + gap;
            }
            int bw = Theme.px(140);
            continueButton.setFont(Theme.font(Font.BOLD, 13));
            continueButton.setBounds(x + (w - bw) / 2, y + h - pad - buttonH(), bw, buttonH());
        } else {
            int n = choosing.cards.size();
            int cw = Theme.px(CARD_W);
            // Smaller cards if the board is narrow.
            int maxCw = (getWidth() - 2 * pad - (n - 1) * Theme.px(18) - Theme.px(40)) / Math.max(1, n);
            cw = Math.max(Theme.px(90), Math.min(cw, maxCw));
            int ch = (int) (cw / Theme.CARD_ASPECT);
            int gap = Theme.px(18);
            w = Math.max(Theme.px(PANEL_W), 2 * pad + n * cw + (n - 1) * gap);
            h = headerH() + ch + pad + buttonH() + pad;
            int x = (getWidth() - w) / 2;
            int y = Math.max(Theme.px(8), (getHeight() - h) / 2);
            panel = new Rectangle(x, y, w, h);
            int cx = x + (w - (n * cw + (n - 1) * gap)) / 2;
            for (int i = 0; i < n; i++) {
                cardBoxes.add(new Rectangle(cx + i * (cw + gap), y + headerH(), cw, ch));
            }
            int bw = Theme.px(110);
            int by = y + h - pad - buttonH();
            for (SkinButton b : List.of(skipButton, backButton)) {
                b.setFont(Theme.font(Font.PLAIN, 12));
            }
            backButton.setBounds(x + w / 2 - bw - Theme.px(6), by, bw, buttonH());
            skipButton.setBounds(x + w / 2 + Theme.px(6), by, bw, buttonH());
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Skin.paint(g, "reward.backdrop", 0, 0, getWidth(), getHeight());
        Skin.paint(g, "reward", panel.x, panel.y, panel.width, panel.height);

        // Header
        g.setFont(Theme.font(Theme.FontRole.HEADING, Font.BOLD, choosing == null ? 26 : 20));
        FontMetrics fm = g.getFontMetrics();
        String head = choosing == null ? title : "Choose a card";
        g.setColor(Skin.text("reward", Theme.ACCENT));
        int ty = panel.y + pad() + fm.getAscent() - Theme.px(4);
        g.drawString(head, panel.x + (panel.width - fm.stringWidth(head)) / 2, ty);
        String sub = choosing == null ? subtitle : "Click a card to add it to your deck, or skip";
        g.setFont(Theme.font(Font.PLAIN, 12));
        FontMetrics sm = g.getFontMetrics();
        g.setColor(Theme.TEXT_DIM);
        g.drawString(sub, panel.x + (panel.width - sm.stringWidth(sub)) / 2, ty + sm.getHeight() + Theme.px(2));

        if (choosing == null) {
            for (int i = 0; i < rewards.size(); i++) {
                paintRow(g, rewards.get(i), rowBoxes.get(i), i == hoverRow);
            }
        } else {
            for (int i = 0; i < cardBoxes.size(); i++) {
                Rectangle r = cardBoxes.get(i);
                boolean hot = i == hoverCard;
                Rectangle d = hot ? grow(r, Theme.px(4)) : r;
                CardPainter.paint(g, choosing.cards.get(i), d.x, d.y, d.width, d.height);
                if (hot) {
                    g.setColor(Theme.ACCENT);
                    g.setStroke(new BasicStroke(Math.max(2f, Theme.px(3))));
                    int arc = Math.max(4, d.width / 10);
                    g.drawRoundRect(d.x, d.y, d.width - 1, d.height - 1, arc, arc);
                }
            }
        }
        g.dispose();
    }

    private static Rectangle grow(Rectangle r, int by) {
        // Keep the card's shape: grow height by the aspect ratio.
        int dh = (int) Math.round(by / Theme.CARD_ASPECT);
        return new Rectangle(r.x - by, r.y - dh, r.width + 2 * by, r.height + 2 * dh);
    }

    private void paintRow(Graphics2D g, Reward r, Rectangle box, boolean hot) {
        String element = r.claimed ? "reward.item.claimed" : hot ? "reward.item.hover" : "reward.item";
        Skin.paint(g, element, box.x, box.y, box.width, box.height);
        int iconD = box.height - Theme.px(14);
        int ix = box.x + Theme.px(8);
        int iy = box.y + (box.height - iconD) / 2;
        paintIcon(g, r, ix, iy, iconD);
        if (r.claimed) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(ix, iy, iconD, iconD);
            check(g, ix + iconD / 2, iy + iconD / 2, iconD * 0.32);
        }
        int tx = ix + iconD + Theme.px(12);
        g.setFont(Theme.font(Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        Color text = Skin.text(element, Theme.TEXT);
        g.setColor(r.claimed ? Theme.blend(text, Theme.PANEL, 0.4) : text);
        String sub = r.subtitle();
        int titleY = sub.isEmpty() ? box.y + (box.height + fm.getAscent() - fm.getDescent()) / 2
            : box.y + box.height / 2 - Theme.px(2);
        g.drawString(clip(r.title(), fm, box.x + box.width - tx - Theme.px(8)), tx, titleY);
        if (!sub.isEmpty()) {
            g.setFont(Theme.font(Font.PLAIN, 11));
            FontMetrics sm = g.getFontMetrics();
            g.setColor(Theme.TEXT_DIM);
            g.drawString(clip(sub, sm, box.x + box.width - tx - Theme.px(8)), tx, titleY + sm.getHeight());
        }
    }

    private static String clip(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > max) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private void paintIcon(Graphics2D g, Reward r, int x, int y, int d) {
        String key = switch (r.kind) {
            case GOLD -> "icon.reward.gold";
            case CARDS -> "icon.reward.cards";
            case RELIC -> "icon.reward.relic";
        };
        BufferedImage pic = Theme.pack().image(key);
        if (pic != null && r.kind != Reward.Kind.RELIC) {
            g.drawImage(pic, x, y, d, d, null);
            return;
        }
        switch (r.kind) {
            case GOLD -> coin(g, x, y, d);
            case CARDS -> fannedCards(g, r, x, y, d);
            case RELIC -> {
                if (pic != null) {
                    g.drawImage(pic, x, y, d, d, null);   // a frame or backing picture behind the relic
                }
                RelicIcon.paintBadge(g, r.relic, x, y, d);
            }
        }
    }

    /** A drawn gold coin. */
    private static void coin(Graphics2D g, int x, int y, int d) {
        Color gold = new Color(0xE8B83A);
        g.setColor(Theme.blend(gold, Color.BLACK, 0.35));
        g.fill(new Ellipse2D.Double(x, y + d * 0.06, d, d * 0.94));
        g.setColor(gold);
        g.fill(new Ellipse2D.Double(x, y, d, d * 0.94));
        g.setColor(Theme.blend(gold, Color.WHITE, 0.35));
        g.setStroke(new BasicStroke(Math.max(1.5f, d / 16f)));
        g.draw(new Ellipse2D.Double(x + d * 0.18, y + d * 0.16, d * 0.64, d * 0.6));
        g.setFont(Theme.font(Theme.FontRole.NUMBERS, Font.BOLD, 1).deriveFont((float) (d * 0.42)));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.blend(gold, Color.BLACK, 0.45));
        String s = "G";
        g.drawString(s, x + (d - fm.stringWidth(s)) / 2, (int) (y + d * 0.47 + fm.getAscent() / 2.6));
    }

    /** Up to three offered cards' art, fanned out. */
    private static void fannedCards(Graphics2D g0, Reward r, int x, int y, int d) {
        int n = Math.min(3, r.cards.size());
        int ch = d;
        int cw = (int) (ch * Theme.CARD_ASPECT);
        for (int i = 0; i < n; i++) {
            Graphics2D g = (Graphics2D) g0.create();
            double angle = (i - (n - 1) / 2.0) * 0.28;
            g.rotate(angle, x + d / 2.0, y + d);
            int cx = x + (d - cw) / 2;
            CardSnapshot c = r.chosen != null ? r.chosen : r.cards.get(i);
            CardPainter.paint(g, c, cx, y, cw, ch);
            g.dispose();
        }
    }

    /** A drawn check mark (no font glyph needed). */
    private static void check(Graphics2D g, int cx, int cy, double s) {
        g.setStroke(new BasicStroke((float) Math.max(2, s * 0.35), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.YOU);
        g.drawLine((int) (cx - s), cy, (int) (cx - s * 0.25), (int) (cy + s * 0.7));
        g.drawLine((int) (cx - s * 0.25), (int) (cy + s * 0.7), (int) (cx + s), (int) (cy - s * 0.75));
    }
}
