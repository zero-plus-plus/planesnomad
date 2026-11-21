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
import java.util.function.Function;

/**
 * Draws a card at any size. Until card images are downloaded this is a text-only frame
 * (our own design, no official frames): colored border, name, cost, a placeholder art box,
 * type line, rules text and power/toughness. Small sizes drop the rules text.
 */
public final class CardPainter {

    /** Supplies a card's picture, or null if none is cached yet. Set once images exist. */
    private static Function<CardSnapshot, Image> images = card -> null;

    private CardPainter() { }

    public static void setImageSource(Function<CardSnapshot, Image> source) {
        images = source == null ? card -> null : source;
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
                Image picture = images.apply(card);
                if (picture != null) {
                    Shape clip = round(x, y, w, h);
                    g.setClip(clip);
                    g.drawImage(picture, x, y, w, h, null);
                    g.setClip(null);
                    g.setColor(new Color(0, 0, 0, 120));
                    g.draw(clip);
                } else {
                    paintFrame(g, card, x, y, w, h);
                }
            }
        } finally {
            g.dispose();
        }
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
        String cost = card.manaCost() == null ? "" : card.manaCost();
        int costW = tiny || cost.isEmpty() ? 0 : fm.stringWidth(cost) + pad;
        g.setColor(text);
        drawClipped(g, card.name(), x + pad, y + pad + fm.getAscent(), w - 2 * pad - costW);
        if (costW > 0) {
            g.drawString(cost, x + w - pad - fm.stringWidth(cost), y + pad + fm.getAscent());
        }

        // Art box (placeholder until images exist)
        int artTop = y + pad + titleH;
        int artH = (int) (h * (large ? 0.40 : 0.36));
        g.setColor(Theme.cardArt(card.colors(), card.land()));
        g.fillRect(x + pad, artTop, w - 2 * pad, artH);

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
            g.setColor(card.damage() > 0 ? Theme.DAMAGE : new Color(0x1A1A1A));
            g.drawString(pt, bx + pad, by + pfm.getAscent());
        }
        if (!tiny && h - (typeY - y) > 3 * fm.getHeight()) {
            Font rulesFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(7, (int) (w * (large ? 0.055 : 0.075))));
            g.setFont(rulesFont);
            FontMetrics rfm = g.getFontMetrics();
            int top = typeY + pad;
            int bottom = y + h - pad - ptH;
            int lineY = top + rfm.getAscent();
            for (String line : wrap(card.rulesText(), rfm, w - 2 * pad)) {
                if (lineY + rfm.getDescent() > bottom) {
                    break;
                }
                g.drawString(line, x + pad, lineY);
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
