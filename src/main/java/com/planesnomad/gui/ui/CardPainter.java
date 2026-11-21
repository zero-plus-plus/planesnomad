package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a card at any size. Until card images are downloaded this is a text-only frame
 * (our own design, no official frames): colored border, name, cost, a placeholder art box,
 * type line, rules text and power/toughness. Small sizes drop the rules text.
 */
public final class CardPainter {

    /** Supplies card pictures already scaled to the requested size, or null if there is none (yet). */
    public interface ImageSource {
        Image full(CardSnapshot card, int w, int h);

        Image art(CardSnapshot card, int w, int h);
    }

    private static final ImageSource NONE = new ImageSource() {
        @Override
        public Image full(CardSnapshot card, int w, int h) {
            return null;
        }

        @Override
        public Image art(CardSnapshot card, int w, int h) {
            return null;
        }
    };

    private static ImageSource images = NONE;

    private CardPainter() { }

    public static void setImageSource(ImageSource source) {
        images = source == null ? NONE : source;
    }

    /** The card's art crop scaled to fill w x h, or null if none (yet). */
    public static Image artImage(CardSnapshot card, int w, int h) {
        return card == null ? null : images.art(card, w, h);
    }

    public static void paint(Graphics2D g0, CardSnapshot card, int x, int y, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (card.faceDown()) {
                paintBack(g, x, y, w, h);
            } else {
                // Ability cards always use the frame (only the ability's text), with the source's art.
                Image picture = card.ability() ? null : images.full(card, w, h);
                if (picture != null) {
                    Shape clip = round(x, y, w, h);
                    Shape oldClip = g.getClip();
                    g.clip(clip);                       // hide the image's square corners
                    g.drawImage(picture, x, y, null);
                    g.setClip(oldClip);
                    g.setColor(new Color(0, 0, 0, 120));
                    g.draw(clip);
                    // The picture shows the printed card: show what has changed on top of it.
                    if (!card.granted().isEmpty()) {
                        grantedStrip(g, card, x, y, w, h);
                    }
                    if (card.statsChanged()) {
                        statsBadge(g, card, x, y, w, h);
                    }
                    if (card.damage() > 0) {
                        damageBadge(g, card, x, y, w, h);
                    }
                } else {
                    paintFrame(g, card, x, y, w, h);
                }
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * A small art thumbnail (art crop for the card's set). Without a picture or a crop box for
     * the set, a colored placeholder with the card's initial.
     */
    public static void paintArt(Graphics2D g0, CardSnapshot card, int x, int y, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Shape clip = new RoundRectangle2D.Double(x, y, w, h, Math.max(4, h / 4.0), Math.max(4, h / 4.0));
            g.clip(clip);
            Image art = card == null ? null : images.art(card, w, h);
            if (art != null) {
                g.drawImage(art, x, y, null);
            } else {
                Color base = card == null ? Theme.PANEL_EDGE : Theme.cardArt(card.colors(), card.land());
                g.setColor(base);
                g.fillRect(x, y, w, h);
                if (card != null && !card.name().isEmpty()) {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, (int) (h * 0.5))));
                    FontMetrics fm = g.getFontMetrics();
                    String initial = card.name().substring(0, 1);
                    g.setColor(Theme.textOn(base));
                    g.drawString(initial, x + (w - fm.stringWidth(initial)) / 2, y + (h + fm.getAscent() - fm.getDescent()) / 2);
                }
            }
            g.setClip(null);
            g.setColor(new Color(0, 0, 0, 140));
            g.draw(clip);
        } finally {
            g.dispose();
        }
    }

    /** Green if power+toughness went up, red if down, amber if mixed. */
    private static Color statsColor(CardSnapshot card) {
        int dp = card.power() - card.printedPower();
        int dt = card.toughness() - card.printedToughness();
        if (dp >= 0 && dt >= 0) {
            return new Color(0x2E9E4F);
        }
        if (dp <= 0 && dt <= 0) {
            return new Color(0xC8382E);
        }
        return new Color(0xC98A1B);
    }

    /** Current power/toughness over the printed P/T box of a picture. */
    private static void statsBadge(Graphics2D g, CardSnapshot card, int x, int y, int w, int h) {
        String text = card.power() + "/" + card.toughness();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, (int) (w * 0.13))));
        FontMetrics fm = g.getFontMetrics();
        int pad = Math.max(2, w / 30);
        int bw = fm.stringWidth(text) + 2 * pad;
        int bh = fm.getHeight();
        int bx = x + w - bw - pad;
        int by = y + h - bh - pad;
        g.setColor(statsColor(card));
        g.fillRoundRect(bx, by, bw, bh, bh / 2, bh / 2);
        g.setColor(Color.WHITE);
        g.drawString(text, bx + pad, by + fm.getAscent());
    }

    /**
     * Abilities granted by other cards (not printed on the card), listed on a dark strip over the
     * lower part of a picture, e.g. "+ Islandwalk".
     */
    private static void grantedStrip(Graphics2D g, CardSnapshot card, int x, int y, int w, int h) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(7, (int) (w * 0.075))));
        FontMetrics fm = g.getFontMetrics();
        int pad = Math.max(2, w / 24);
        List<String> lines = new ArrayList<>();
        for (String granted : card.granted().split("\n")) {
            lines.addAll(SymbolText.wrap("+ " + granted, fm, w - 3 * pad));
        }
        int maxLines = Math.max(1, (int) (h * 0.35 / fm.getHeight()));
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, lines.get(maxLines - 1) + " …");
        }
        int boxH = lines.size() * fm.getHeight() + pad;
        int boxY = y + (int) (h * 0.58);
        g.setColor(new Color(12, 14, 18, 215));
        g.fillRoundRect(x + pad / 2, boxY, w - pad, boxH, pad, pad);
        g.setColor(Theme.ACCENT);
        int lineY = boxY + pad / 2 + fm.getAscent();
        for (String line : lines) {
            SymbolText.draw(g, line, x + pad, lineY);
            lineY += fm.getHeight();
        }
    }

    /** Damage marked on a creature, drawn over a picture (the text frame shows it in its P/T box). */
    private static void damageBadge(Graphics2D g, CardSnapshot card, int x, int y, int w, int h) {
        String text = "-" + card.damage();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, (int) (w * 0.14))));
        FontMetrics fm = g.getFontMetrics();
        int pad = Math.max(2, w / 30);
        int bw = fm.stringWidth(text) + 2 * pad;
        int bh = fm.getHeight();
        int bx = x + pad;
        int by = y + h - bh - pad;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(bx, by, bw, bh, bh / 2, bh / 2);
        g.setColor(Theme.DAMAGE);
        g.drawString(text, bx + pad, by + fm.getAscent());
    }

    private static RoundRectangle2D round(int x, int y, int w, int h) {
        double r = Math.max(4, w * 0.06);
        return new RoundRectangle2D.Double(x, y, w - 1, h - 1, r, r);
    }

    private static void paintBack(Graphics2D g, int x, int y, int w, int h) {
        RoundRectangle2D shape = round(x, y, w, h);
        g.setPaint(new GradientPaint(x, y, new Color(0x3B2F5C), x + w, y + h, new Color(0x1C1630)));
        g.fill(shape);
        g.setColor(new Color(0x8E7CC3));
        g.setStroke(new BasicStroke(Math.max(1f, w / 40f)));
        g.draw(shape);
        int inset = Math.max(3, w / 6);
        g.setColor(new Color(0x6A5A9E));
        g.drawOval(x + inset, y + h / 2 - (w - 2 * inset) / 2, w - 2 * inset, w - 2 * inset);
    }

    private static void paintFrame(Graphics2D g, CardSnapshot card, int x, int y, int w, int h) {
        Color frame = Theme.cardFrame(card.colors(), card.land());
        Color text = Theme.textOn(frame);
        RoundRectangle2D shape = round(x, y, w, h);
        g.setColor(frame);
        g.fill(shape);
        g.setColor(Theme.blend(frame, Color.BLACK, 0.5));
        g.setStroke(new BasicStroke(Math.max(1f, w / 60f)));
        g.draw(shape);

        int pad = Math.max(2, w / 18);
        boolean large = w >= 150;
        boolean tiny = w < 60;

        // Title bar: name and cost
        Font nameFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, (int) (w * (large ? 0.075 : 0.105))));
        g.setFont(nameFont);
        FontMetrics fm = g.getFontMetrics();
        int titleH = fm.getHeight() + pad / 2;
        // With symbol pictures the cost is drawn as symbols; otherwise as short text ("1R").
        String cost = card.manaCost() == null ? "" : card.manaCost();
        boolean symbols = ManaSymbols.available();
        String shown = symbols ? cost : shortCost(cost);
        int costW = tiny || shown.isEmpty() ? 0 : SymbolText.width(shown, fm) + pad;
        g.setColor(text);
        drawClipped(g, card.name(), x + pad, y + pad + fm.getAscent(), w - 2 * pad - costW);
        if (costW > 0) {
            SymbolText.draw(g, shown, x + w - pad - SymbolText.width(shown, fm), y + pad + fm.getAscent());
        }

        // Art box: the source's art for ability cards, otherwise a placeholder until images exist
        int artTop = y + pad + titleH;
        int artH = (int) (h * (large ? 0.40 : 0.36));
        Image art = card.ability() ? images.art(card, w - 2 * pad, artH) : null;
        if (art != null) {
            g.drawImage(art, x + pad, artTop, null);
        } else {
            g.setColor(Theme.cardArt(card.colors(), card.land()));
            g.fillRect(x + pad, artTop, w - 2 * pad, artH);
        }

        // Type line
        Font typeFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(7, (int) (w * (large ? 0.06 : 0.085))));
        g.setFont(typeFont);
        fm = g.getFontMetrics();
        int typeY = artTop + artH + fm.getAscent() + pad / 2;
        g.setColor(text);
        if (!tiny) {
            drawClipped(g, card.typeLine(), x + pad, typeY, w - 2 * pad);
        }

        // Rules text (only when there's room)
        int ptH = 0;
        if (card.power() != null) {
            Font ptFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, (int) (w * (large ? 0.085 : 0.13))));
            g.setFont(ptFont);
            FontMetrics pfm = g.getFontMetrics();
            String pt = card.power() + "/" + card.toughness();
            int boxW = pfm.stringWidth(pt) + pad * 2;
            ptH = pfm.getHeight();
            int bx = x + w - pad - boxW;
            int by = y + h - pad - ptH;
            g.setColor(Theme.blend(frame, Color.WHITE, 0.55));
            g.fillRoundRect(bx, by, boxW, ptH, pad, pad);
            g.setColor(card.damage() > 0 ? Theme.DAMAGE : card.statsChanged() ? statsColor(card) : new Color(0x1A1A1A));
            g.drawString(pt, bx + pad, by + pfm.getAscent());
        }
        if (!tiny && h - (typeY - y) > 3 * fm.getHeight()) {
            // Ability cards hold one short ability, so their text can be larger.
            double share = card.ability() ? (large ? 0.075 : 0.095) : (large ? 0.055 : 0.075);
            Font rulesFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(7, (int) (w * share)));
            g.setFont(rulesFont);
            FontMetrics rfm = g.getFontMetrics();
            int top = typeY + pad;
            int bottom = y + h - pad - ptH;
            int lineY = top + rfm.getAscent();
            for (String line : SymbolText.wrap(card.rulesText(), rfm, w - 2 * pad)) {
                if (lineY + rfm.getDescent() > bottom) {
                    break;
                }
                SymbolText.draw(g, line, x + pad, lineY);
                lineY += rfm.getHeight();
            }
        }

        if (card.damage() > 0 && !tiny) {
            Font dmgFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, (int) (w * 0.11)));
            g.setFont(dmgFont);
            g.setColor(Theme.DAMAGE);
            g.drawString("-" + card.damage(), x + pad, y + h - pad);
        }
    }

    /** "{1}{R/G}" -> "1R/G" for when there are no symbol pictures. */
    static String shortCost(String cost) {
        return cost == null ? "" : cost.replace("}{", "").replace("{", "").replace("}", "");
    }

    private static void drawClipped(Graphics2D g, String s, int x, int baseline, int maxWidth) {
        if (s == null || maxWidth <= 0) {
            return;
        }
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= maxWidth) {
            g.drawString(s, x, baseline);
            return;
        }
        String ell = "…";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + ell) > maxWidth) {
            end--;
        }
        g.drawString(s.substring(0, end) + ell, x, baseline);
    }

    /** Word-wraps text (respecting line breaks) to the given width. */
    public static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }
}
