package com.planesnomad.gui.ui.theme;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Generates the pictures of the example theme {@code themes/fantasy-example}: leather and wood
 * textures, gold 9-slice frames, bronze buttons, a gold ring for relics, felt tables. All drawn
 * by code (no outside art), to show what a designer can swap - a real theme would use painted art.
 *
 * Usage: java ... com.planesnomad.gui.ui.theme.ExampleThemeAssets [themes/fantasy-example]
 */
public final class ExampleThemeAssets {

    private static final Color GOLD = new Color(0xC9A23F);
    private static final Color GOLD_LIGHT = new Color(0xF3D98A);
    private static final Color GOLD_DARK = new Color(0x6E5418);

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "themes/fantasy-example");
        File frames = new File(dir, "frames");
        File textures = new File(dir, "textures");
        frames.mkdirs();
        textures.mkdirs();
        write(texture(256, new Color(0x2A1D14), new Color(0x3B2A1C), 11, 0.55), textures, "leather.png");
        write(texture(256, new Color(0x3A2616), new Color(0x5A3B22), 23, 0.35, true), textures, "wood.png");
        write(texture(256, new Color(0x1C2B20), new Color(0x24382A), 7, 0.4), textures, "felt-green.png");
        write(texture(256, new Color(0x2B1A1E), new Color(0x3A232A), 5, 0.4), textures, "felt-crimson.png");
        write(goldFrame(96, 22, new Color(0xF0241913, true), true), frames, "gold-frame.png");
        write(goldFrame(64, 14, new Color(0x1E1510), false), frames, "gold-frame-thin.png");
        write(button(48, 24, new Color(0x5B3A1E), new Color(0x8A5A2B), GOLD_DARK), frames, "button.png");
        write(button(48, 24, new Color(0x7A4E26), new Color(0xB07838), GOLD), frames, "button-hover.png");
        write(button(48, 24, new Color(0x3A2412), new Color(0x5B3A1E), GOLD_LIGHT), frames, "button-pressed.png");
        write(button(48, 24, new Color(0x2E2620), new Color(0x3C322A), new Color(0x4A4036)), frames, "button-disabled.png");
        write(pill(64, 32, new Color(0x2A0E0E), new Color(0x6B1A1A)), frames, "badge-life.png");
        write(pill(48, 24, new Color(0x1E1510), new Color(0x3B2A1C)), frames, "badge-count.png");
        write(ring(128), frames, "relic-ring.png");
        System.out.println("Example theme pictures written to " + dir.getAbsolutePath());
    }

    private static void write(BufferedImage img, File dir, String name) throws IOException {
        ImageIO.write(img, "png", new File(dir, name));
    }

    private static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static BufferedImage texture(int size, Color dark, Color light, long seed, double amount) {
        return texture(size, dark, light, seed, amount, false);
    }

    /** Seamless noise texture (value noise on a wrapping grid); "grain" stretches it like wood. */
    private static BufferedImage texture(int size, Color dark, Color light, long seed, double amount, boolean grain) {
        Random rnd = new Random(seed);
        int cells = 16;
        double[][] grid = new double[cells][cells];
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                grid[i][j] = rnd.nextDouble();
            }
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double v = 0;
                double amp = 1;
                double total = 0;
                for (int octave = 0; octave < 4; octave++) {
                    int f = 1 << octave;
                    double fx = grain ? x * f / (double) size * cells / 4 : x * f / (double) size * cells;
                    double fy = y * f / (double) size * cells;
                    v += amp * noise(grid, fx, fy, cells);
                    total += amp;
                    amp *= 0.5;
                }
                v /= total;
                if (grain) {
                    v = 0.5 + 0.5 * Math.sin(v * 18 + y * 0.02);
                }
                double t = Math.max(0, Math.min(1, 0.5 + (v - 0.5) * 2 * amount + rnd.nextGaussian() * 0.03));
                img.setRGB(x, y, mix(dark, light, t).getRGB());
            }
        }
        return img;
    }

    private static double noise(double[][] grid, double x, double y, int cells) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double tx = x - x0, ty = y - y0;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        double a = grid[Math.floorMod(x0, cells)][Math.floorMod(y0, cells)];
        double b = grid[Math.floorMod(x0 + 1, cells)][Math.floorMod(y0, cells)];
        double c = grid[Math.floorMod(x0, cells)][Math.floorMod(y0 + 1, cells)];
        double d = grid[Math.floorMod(x0 + 1, cells)][Math.floorMod(y0 + 1, cells)];
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /**
     * A square 9-slice frame: dark inside, a gold double border with a bevel, and (ornate) small
     * diamonds in the corners. Slice it with the border width as the corner size.
     */
    private static BufferedImage goldFrame(int size, int border, Color inside, boolean ornate) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        int r = border;
        g.setColor(inside);
        g.fill(new RoundRectangle2D.Double(2, 2, size - 4, size - 4, r, r));
        // Outer gold band with a light-to-dark bevel.
        g.setPaint(new GradientPaint(0, 0, GOLD_LIGHT, size, size, GOLD_DARK));
        g.setStroke(new BasicStroke(border * 0.28f));
        g.draw(new RoundRectangle2D.Double(border * 0.2, border * 0.2, size - border * 0.4, size - border * 0.4, r, r));
        // Inner fine line.
        g.setPaint(GOLD);
        g.setStroke(new BasicStroke(Math.max(1.5f, border * 0.08f)));
        double in = border * 0.62;
        g.draw(new RoundRectangle2D.Double(in, in, size - 2 * in, size - 2 * in, r * 0.6, r * 0.6));
        if (ornate) {
            for (int[] c : new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                double cx = c[0] == 0 ? border * 0.45 : size - border * 0.45;
                double cy = c[1] == 0 ? border * 0.45 : size - border * 0.45;
                double d = border * 0.42;
                java.awt.geom.Path2D gem = new java.awt.geom.Path2D.Double();
                gem.moveTo(cx, cy - d);
                gem.lineTo(cx + d, cy);
                gem.lineTo(cx, cy + d);
                gem.lineTo(cx - d, cy);
                gem.closePath();
                g.setPaint(new GradientPaint((float) (cx - d), (float) (cy - d), GOLD_LIGHT, (float) (cx + d), (float) (cy + d), GOLD_DARK));
                g.fill(gem);
                g.setColor(new Color(0x8E1B1B));
                g.fill(new Ellipse2D.Double(cx - d * 0.32, cy - d * 0.32, d * 0.64, d * 0.64));
            }
        }
        g.dispose();
        return img;
    }

    private static BufferedImage button(int w, int h, Color dark, Color light, Color rim) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 8, 8);
        g.setPaint(new GradientPaint(0, 0, light, 0, h, dark));
        g.fill(shape);
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new RoundRectangle2D.Double(3, 2, w - 6, h * 0.4, 6, 6));
        g.setColor(rim);
        g.setStroke(new BasicStroke(2f));
        g.draw(shape);
        g.dispose();
        return img;
    }

    private static BufferedImage pill(int w, int h, Color inside, Color glow) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        RoundRectangle2D shape = new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, h - 3, h - 3);
        g.setPaint(new GradientPaint(0, 0, glow, 0, h, inside));
        g.fill(shape);
        g.setPaint(new GradientPaint(0, 0, GOLD_LIGHT, 0, h, GOLD_DARK));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(shape);
        g.dispose();
        return img;
    }

    /** A gold ring with a transparent middle, drawn over relic icons. */
    private static BufferedImage ring(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        double band = size * 0.09;
        g.setPaint(new RadialGradientPaint(size / 2f, size / 2f, size / 2f,
            new float[] {0.80f, 0.9f, 1f}, new Color[] {GOLD_DARK, GOLD_LIGHT, GOLD_DARK}));
        g.setStroke(new BasicStroke((float) band));
        g.draw(new Ellipse2D.Double(band / 2 + 1, band / 2 + 1, size - band - 2, size - band - 2));
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i - Math.PI / 2;
            double cx = size / 2.0 + Math.cos(a) * (size / 2.0 - band / 2 - 1);
            double cy = size / 2.0 + Math.sin(a) * (size / 2.0 - band / 2 - 1);
            g.setColor(new Color(0x8E1B1B));
            g.fill(new Ellipse2D.Double(cx - band * 0.45, cy - band * 0.45, band * 0.9, band * 0.9));
            g.setColor(GOLD_LIGHT);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Ellipse2D.Double(cx - band * 0.45, cy - band * 0.45, band * 0.9, band * 0.9));
            g.setStroke(new BasicStroke((float) band));
        }
        g.dispose();
        return img;
    }

    private ExampleThemeAssets() { }
}
