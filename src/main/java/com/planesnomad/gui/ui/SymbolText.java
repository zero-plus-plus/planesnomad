package com.planesnomad.gui.ui;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Measures, wraps and draws text that contains symbols such as {T} or {2/W}, drawing each symbol
 * as its picture (see {@link ManaSymbols}) and falling back to the text form when there is none.
 */
public final class SymbolText {

    private SymbolText() { }

    /** A piece of a line: plain text, or one symbol. */
    private record Piece(String text, String symbol) {
        boolean isSymbol() {
            return symbol != null;
        }
    }

    /** Symbol size for the current font: about the height of a capital letter plus a bit. */
    private static int symbolSize(FontMetrics fm) {
        return Math.max(6, (int) Math.round(fm.getAscent() * 0.95));
    }

    private static List<Piece> pieces(String text) {
        List<Piece> out = new ArrayList<>();
        Matcher m = ManaSymbols.TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.add(new Piece(text.substring(last, m.start()), null));
            }
            out.add(new Piece(m.group(), m.group(1)));
            last = m.end();
        }
        if (last < text.length()) {
            out.add(new Piece(text.substring(last), null));
        }
        return out;
    }

    private static int width(Piece p, FontMetrics fm) {
        if (p.isSymbol()) {
            int size = symbolSize(fm);
            return ManaSymbols.get(p.symbol(), size) != null ? size + 1 : fm.stringWidth(p.text());
        }
        return fm.stringWidth(p.text());
    }

    /** Width of a line of text with symbols. */
    public static int width(String text, FontMetrics fm) {
        int w = 0;
        for (Piece p : pieces(text)) {
            w += width(p, fm);
        }
        return w;
    }

    /** Draws one line; {@code baseline} is the text baseline. */
    public static void draw(Graphics2D g, String text, int x, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        int size = symbolSize(fm);
        int cx = x;
        for (Piece p : pieces(text)) {
            BufferedImage img = p.isSymbol() ? ManaSymbols.get(p.symbol(), size) : null;
            if (img != null) {
                // Sit the symbol on the baseline, centred on the letters' height.
                int top = baseline - fm.getAscent() + (fm.getAscent() - size) / 2 + 1;
                g.drawImage(img, cx, top, null);
                cx += size + 1;
            } else {
                g.drawString(p.text(), cx, baseline);
                cx += fm.stringWidth(p.text());
            }
        }
    }

    /** Word-wraps text with symbols to the width; symbols never split. Respects line breaks. */
    public static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(candidate, fm) > maxWidth && line.length() > 0) {
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
