package com.planesnomad.gui.ui;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Relic icon pictures from {@code data/relic-icons/} (placeholder Twemoji icons for now; see
 * CREDITS.txt there). Returns null if a file is missing, so callers fall back to a letter.
 */
public final class RelicImages {

    private static final Path FOLDER = Path.of("data", "relic-icons");
    private static final Map<String, BufferedImage> COLOR = new HashMap<>();
    private static final Map<String, BufferedImage> GREY = new HashMap<>();

    private RelicImages() { }

    /** The icon in color, or greyed out (for a used relic). */
    public static synchronized BufferedImage get(String file, boolean greyed) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        BufferedImage color = COLOR.computeIfAbsent(file, RelicImages::load);
        if (color == null || !greyed) {
            return color;
        }
        return GREY.computeIfAbsent(file, f -> greyscale(color));
    }

    private static BufferedImage load(String file) {
        Path path = FOLDER.resolve(file);
        if (!Files.isRegularFile(path)) {
            System.err.println("Relic icon not found: " + path.toAbsolutePath());
            return null;
        }
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException e) {
            System.err.println("Could not read relic icon " + path + ": " + e.getMessage());
            return null;
        }
    }

    /** Grey and slightly faded, keeping transparency. */
    private static BufferedImage greyscale(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = argb >>> 24;
                int r = (argb >> 16) & 255;
                int g = (argb >> 8) & 255;
                int b = argb & 255;
                int lum = (r * 299 + g * 587 + b * 114) / 1000;
                lum = 70 + lum * 50 / 100;          // compress toward a mid grey
                out.setRGB(x, y, ((a * 70 / 100) << 24) | (lum << 16) | (lum << 8) | lum);
            }
        }
        return out;
    }

    /** Draws an icon scaled smoothly into the given square. */
    public static void draw(Graphics2D g, BufferedImage icon, int x, int y, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(icon, x, y, size, size, null);
        g2.dispose();
    }
}
