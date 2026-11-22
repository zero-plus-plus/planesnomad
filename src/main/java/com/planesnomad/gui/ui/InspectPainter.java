package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Draws a card for inspection (hover preview, large view, pinned panel), showing what the card is
 * NOW. A card picture only shows the printed card, so a panel beside it gives the card's full
 * current text: printed abilities first, then abilities granted by other cards in gold with a "+",
 * then the current power/toughness if it differs from the printed one.
 *
 * (Chosen by the team out of four tested styles: text box overlay, side panel with full text,
 * side panel with only changes, panel below.)
 */
public final class InspectPainter {

    /** Granted abilities are drawn in this color (gold), printed text in white. */
    public static final Color GRANTED = new Color(0xF2C14E);
    private static final Color PANEL = new Color(14, 16, 21, 236);
    private static final Color PANEL_EDGE = new Color(0x3A414D);

    /** Side panel width as a share of the card width. */
    private static final double PANEL_SHARE = 0.85;

    private InspectPainter() { }

    /**
     * One line of card text, and whether it was granted by another card.
     * @param continued true for the 2nd+ piece of a wrapped line
     */
    public record Line(String text, boolean granted, boolean continued) {
        public Line(String text, boolean granted) {
            this(text, granted, false);
        }
    }

    /** Total size needed to inspect this card with the card drawn at cardW x cardH. */
    public static Dimension size(CardSnapshot card, int cardW, int cardH) {
        if (card.isHidden() || card.faceDown()) {
            return new Dimension(cardW, cardH);
        }
        return new Dimension(cardW + gap(cardW) + panelWidth(cardW), cardH);
    }

    /** Draws the card at (x, y) (cardW x cardH) with its current text in a panel beside it. */
    public static void paint(Graphics2D g0, CardSnapshot card, int x, int y, int cardW, int cardH) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        CardPainter.paint(g, card, x, y, cardW, cardH, false);   // the panel replaces the "+" strip
        if (card.isHidden() || card.faceDown()) {
            g.dispose();
            return;
        }
        int pad = Math.max(4, cardW / 28);
        int px = x + cardW + gap(cardW);
        int pw = panelWidth(cardW);
        panel(g, px, y, pw, cardH, cardW);

        Font text = textFont(cardW);
        g.setFont(text.deriveFont(Font.BOLD));
        FontMetrics bold = g.getFontMetrics();
        int lineY = y + pad + bold.getAscent();
        g.setColor(Theme.TEXT);
        for (String piece : SymbolText.wrap(card.name(), bold, pw - 2 * pad)) {
            g.drawString(piece, px + pad, lineY);
            lineY += bold.getHeight();
        }
        g.setFont(text);
        FontMetrics fm = g.getFontMetrics();
        if (card.typeLine() != null && !card.typeLine().isEmpty()) {
            g.setColor(Theme.TEXT_DIM);
            for (String piece : SymbolText.wrap(card.typeLine(), fm, pw - 2 * pad)) {
                g.drawString(piece, px + pad, lineY);
                lineY += fm.getHeight();
            }
        }
        lineY += pad / 2;
        int bottom = y + cardH - pad;
        List<Line> lines = wrap(currentText(card), fm, pw - 2 * pad);
        for (int i = 0; i < lines.size(); i++) {
            if (lineY + fm.getDescent() > bottom) {
                break;
            }
            Line line = lines.get(i);
            boolean lastShown = lineY + fm.getHeight() + fm.getDescent() > bottom && i < lines.size() - 1;
            g.setColor(line.granted() ? GRANTED : Theme.TEXT);
            SymbolText.draw(g, lastShown ? line.text() + " …" : line.text(), px + pad, lineY);
            lineY += fm.getHeight();
        }
        g.dispose();
    }

    /** The card's full current text: printed abilities, then granted ones (marked), then changed P/T. */
    public static List<Line> currentText(CardSnapshot card) {
        Set<String> granted = card.granted().isEmpty() ? Set.of()
            : List.of(card.granted().split("\n")).stream().map(InspectPainter::key).collect(Collectors.toSet());
        List<Line> printed = new ArrayList<>();
        List<Line> added = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String raw : card.rulesText().split("\n")) {
            String text = raw.trim();
            if (text.isEmpty()) {
                continue;
            }
            String k = key(text);
            boolean isGranted = granted.stream().anyMatch(gk -> !gk.isEmpty() && (k.equals(gk) || k.startsWith(gk)));
            if (isGranted) {
                added.add(new Line("+ " + text, true));
            } else {
                printed.add(new Line(text, false));
            }
            seen.add(k);
        }
        // Granted abilities the engine didn't include in the current text: list them too.
        for (String g : card.granted().split("\n")) {
            if (!g.isBlank() && seen.stream().noneMatch(k -> k.startsWith(key(g)))) {
                added.add(new Line("+ " + g.trim(), true));
            }
        }
        List<Line> out = new ArrayList<>(printed);
        out.addAll(added);
        if (card.statsChanged()) {
            out.add(new Line("Now " + card.power() + "/" + card.toughness()
                + " (printed " + card.printedPower() + "/" + card.printedToughness() + ")", true));
        }
        return out;
    }

    /** Compare abilities without reminder text, case or punctuation. */
    private static String key(String s) {
        return s.replaceAll("\\([^)]*\\)", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static int gap(int cardW) {
        return Math.max(4, cardW / 40);
    }

    private static int panelWidth(int cardW) {
        return (int) (cardW * PANEL_SHARE);
    }

    private static Font textFont(int cardW) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, (int) Math.round(cardW * 0.05)));
    }

    /** Word-wraps each line to the width, keeping its granted mark on every wrapped piece. */
    private static List<Line> wrap(List<Line> lines, FontMetrics fm, int width) {
        List<Line> out = new ArrayList<>();
        for (Line line : lines) {
            boolean first = true;
            for (String piece : SymbolText.wrap(line.text(), fm, Math.max(20, width))) {
                out.add(new Line(piece, line.granted(), !first));
                first = false;
            }
        }
        return out;
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h, int cardW) {
        // Themed element "inspect" (the text panel beside an inspected card).
        com.planesnomad.gui.ui.theme.Skin.paint(g, "inspect", x, y, w, h);
    }
}
