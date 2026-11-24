package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Playmat backgrounds for each half of the table. A player's playmat is the art crop of a named
 * card (set per enemy later); without one (or before its picture has loaded) a fallback picture
 * from {@code data/playmats/} is used: public-domain art, see CREDITS.txt there. If neither exists,
 * the plain table color is drawn. Playmats are darkened so cards stay readable.
 */
public final class Playmats {

    /** Fallback pictures: one for your half, one for the enemy's. */
    public static final String FALLBACK_YOU = "fallback-you.jpg";
    public static final String FALLBACK_ENEMY = "fallback-enemy.jpg";

    /** How strongly the playmat is darkened (0 = not at all, 1 = black). */
    private static final float DARKEN = 0.62f;

    private static final Path FOLDER = Path.of("data", "playmats");
    private static final Map<String, BufferedImage> FALLBACKS = new HashMap<>();
    private static final Map<String, BufferedImage> SCALED = new HashMap<>();

    private Playmats() { }

    /**
     * Paints one half of the table.
     * @return true if a picture was drawn (false = caller draws the plain color)
     */
    public static boolean paint(Graphics2D g, CardSnapshot playmatCard, boolean you, int x, int y, int w, int h) {
        return paint(g, playmatCard, null, you, x, y, w, h);
    }

    /**
     * As above, with a picture file (path from the game folder, e.g. an encounter's background)
     * used instead of the card's art when given.
     */
    public static boolean paint(Graphics2D g, CardSnapshot playmatCard, String file, boolean you, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return false;
        }
        Image picture = file == null ? null : picture(file, w, h);
        if (picture == null && playmatCard != null) {
            picture = CardPainter.artImage(playmatCard, w, h);
        }
        if (picture == null) {
            picture = fallback(you ? FALLBACK_YOU : FALLBACK_ENEMY, w, h);
        }
        if (picture == null) {
            return false;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.drawImage(picture, x, y, null);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DARKEN));
        g2.setColor(Color.BLACK);
        g2.fillRect(x, y, w, h);
        g2.dispose();
        return true;
    }

    private static synchronized Image fallback(String file, int w, int h) {
        return picture(FOLDER.resolve(file).toString(), w, h);
    }

    /** Any picture file, scaled and cropped to fill w x h (cached); null if it can't be read. */
    public static synchronized BufferedImage picture(String file, int w, int h) {
        BufferedImage src = FALLBACKS.computeIfAbsent(file, Playmats::load);
        if (src == null) {
            return null;
        }
        String key = file + "@" + w + "x" + h;
        BufferedImage hit = SCALED.get(key);
        if (hit == null) {
            hit = cover(src, w, h);
            if (SCALED.size() > 16) {
                SCALED.clear();
            }
            SCALED.put(key, hit);
        }
        return hit;
    }

    private static BufferedImage load(String file) {
        Path path = Path.of(file);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException e) {
            System.err.println("Could not read playmat " + path + ": " + e.getMessage());
            return null;
        }
    }

    /** Scales and crops the picture to fill w x h without distortion (centred). */
    private static BufferedImage cover(BufferedImage src, int w, int h) {
        double scale = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int sw = (int) Math.ceil(src.getWidth() * scale);
        int sh = (int) Math.ceil(src.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        g.dispose();
        return out;
    }
}
