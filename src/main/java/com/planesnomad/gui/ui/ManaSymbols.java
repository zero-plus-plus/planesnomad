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
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mana and other card symbols ({R}, {2/W}, {U/P}, {T}, ...) drawn from a local symbol folder,
 * such as Forge's picture cache ({@code pics/symbols}: R.png, 2W.png, UP.png, T.png, ...).
 *
 * The symbols are copyrighted: they are read at runtime from the player's own folder and are
 * never part of the repository. With no folder (or a missing file) callers draw the text form,
 * e.g. "{T}", instead.
 */
public final class ManaSymbols {

    /** Matches one symbol in Forge's text form, e.g. {R}, {R/G}, {2/W}, {B/G/P}, {10}, {T}. */
    public static final Pattern TOKEN = Pattern.compile("\\{([^{}]{1,8})}");

    private static Path folder;
    private static final Map<String, Optional<BufferedImage>> ORIGINALS = new HashMap<>();
    private static final Map<String, BufferedImage> SCALED = new HashMap<>();

    private ManaSymbols() { }

    /** Sets the symbol folder (null = none). Clears cached symbols. */
    public static synchronized void setFolder(Path dir) {
        folder = dir;
        ORIGINALS.clear();
        SCALED.clear();
    }

    public static synchronized Path folder() {
        return folder;
    }

    /** True if symbols can be drawn at all. */
    public static synchronized boolean available() {
        return folder != null && Files.isDirectory(folder);
    }

    /**
     * The picture for one symbol (the text between the braces, e.g. "R/G"), scaled to a square of
     * the given size, or null if there is no picture for it.
     */
    public static synchronized BufferedImage get(String symbol, int size) {
        if (!available() || size <= 0) {
            return null;
        }
        String file = fileName(symbol);
        Optional<BufferedImage> original = ORIGINALS.computeIfAbsent(file, ManaSymbols::load);
        if (original.isEmpty() && symbol.contains("/")) {
            // Two-color hybrids are stored in one order only (e.g. UB.png, not BU.png).
            String[] parts = symbol.split("/");
            if (parts.length == 2) {
                file = fileName(parts[1] + "/" + parts[0]);
                original = ORIGINALS.computeIfAbsent(file, ManaSymbols::load);
            }
        }
        if (original.isEmpty()) {
            return null;
        }
        String key = file + "@" + size;
        BufferedImage hit = SCALED.get(key);
        if (hit == null) {
            hit = scale(original.get(), size);
            SCALED.put(key, hit);
        }
        return hit;
    }

    /** "R/G" -> "RG.png", "2/W" -> "2W.png", "U/P" -> "UP.png", "T" -> "T.png". */
    static String fileName(String symbol) {
        return symbol.replace("/", "").toUpperCase() + ".png";
    }

    private static Optional<BufferedImage> load(String file) {
        Path path = folder.resolve(file);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(ImageIO.read(path.toFile()));
        } catch (IOException e) {
            System.err.println("Could not read symbol " + path + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage current = src;
        int w = src.getWidth();
        while (w / 2 >= size) {
            w /= 2;
            current = resize(current, w);
        }
        return resize(current, size);
    }

    private static BufferedImage resize(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /** True if the text contains at least one symbol token. */
    public static boolean hasTokens(String text) {
        return text != null && TOKEN.matcher(text).find();
    }
}
