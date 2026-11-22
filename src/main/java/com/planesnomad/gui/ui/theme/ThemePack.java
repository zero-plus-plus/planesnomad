package com.planesnomad.gui.ui.theme;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * A theme: a folder with a {@code theme.properties} file and optional images and fonts, made by
 * a designer without touching code. Every value a theme leaves out comes from the theme it
 * extends ({@code extends=...}, default: {@code default}), and finally from the built-in look.
 *
 * Themes live in {@code themes/<name>/} next to the game (the folder can be changed with
 * {@code -Dplanesnomad.themes=<folder>}); the theme in use is chosen in the viewer's Settings
 * menu or with {@code -Dplanesnomad.theme=<name>}. See {@code themes/README.md}.
 */
public final class ThemePack {

    public static final String FILE = "theme.properties";
    public static final String DEFAULT = "default";

    private final String name;
    /** This theme's folder first, then the folders of the themes it extends. */
    private final List<Path> folders;
    /** Merged values: this theme's own override those it extends. */
    private final Map<String, String> values;
    private final Map<String, Optional<BufferedImage>> images = new HashMap<>();
    private final Map<String, Optional<Font>> fonts = new HashMap<>();
    private final List<String> problems = new ArrayList<>();

    private ThemePack(String name, List<Path> folders, Map<String, String> values) {
        this.name = name;
        this.folders = folders;
        this.values = values;
    }

    /** The built-in look only (no files). */
    public static ThemePack builtIn() {
        return new ThemePack("built-in", List.of(), Map.of());
    }

    /** The theme named by -Dplanesnomad.theme (default: "default"). */
    public static ThemePack fromSystemProperty() {
        return load(System.getProperty("planesnomad.theme", DEFAULT));
    }

    /** Folder holding all themes. */
    public static Path themesRoot() {
        String prop = System.getProperty("planesnomad.themes");
        return Paths.get(prop != null && !prop.isBlank() ? prop : "themes");
    }

    /** Names of the themes found (folders with a theme.properties), default first. */
    public static List<String> available() {
        List<String> out = new ArrayList<>();
        Path root = themesRoot();
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.list(root)) {
                s.filter(p -> Files.isRegularFile(p.resolve(FILE)))
                    .map(p -> p.getFileName().toString()).sorted().forEach(out::add);
            } catch (IOException e) {
                // no themes: the built-in look is used
            }
        }
        out.remove(DEFAULT);
        out.add(0, DEFAULT);
        return out;
    }

    /** Loads a theme by folder name (and the themes it extends). Never fails: problems are listed. */
    public static ThemePack load(String name) {
        List<Path> chain = new ArrayList<>();
        List<Map<String, String>> layers = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String current = name == null || name.isBlank() ? DEFAULT : name;
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (current != null && seen.add(current)) {
            Path folder = themesRoot().resolve(current);
            Path file = folder.resolve(FILE);
            if (!Files.isRegularFile(file)) {
                if (!current.equals(DEFAULT)) {
                    problems.add("Theme '" + current + "' not found at " + file.toAbsolutePath());
                }
                current = current.equals(DEFAULT) ? null : DEFAULT;
                continue;
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                problems.add("Could not read " + file + ": " + e.getMessage());
            }
            Map<String, String> layer = new HashMap<>();
            // A comment may follow a value after spaces: "color.text = #E8E4D8   # main text".
            p.forEach((k, v) -> layer.put(k.toString().trim(), v.toString().replaceFirst("\\s+#.*$", "").trim()));
            chain.add(folder);
            layers.add(layer);
            // Every theme ends in "default"; "default" itself ends in the built-in look.
            String parent = layer.get("extends");
            current = current.equals(DEFAULT) ? null : parent == null || parent.isBlank() ? DEFAULT : parent;
        }
        Map<String, String> merged = new HashMap<>();
        for (int i = layers.size() - 1; i >= 0; i--) {
            merged.putAll(layers.get(i));
        }
        ThemePack pack = new ThemePack(name == null ? DEFAULT : name, List.copyOf(chain), merged);
        pack.problems.addAll(problems);
        for (Map<String, String> layer : layers) {
            for (String key : layer.keySet()) {
                if (!isKnownKey(key)) {
                    pack.problems.add("Unknown setting '" + key + "' (a typo?) - see themes/default/theme.properties");
                }
            }
        }
        pack.checkFiles();
        return pack;
    }

    // ------------------------------------------------------------------ checking a theme

    private static final java.util.Set<String> COLOR_KEYS = java.util.Set.of("table", "tableEnemy", "midline",
        "panel", "panelEdge", "topbar", "text", "textDim", "accent", "you", "enemy", "life", "attacking",
        "blocking", "damage", "relicGlow", "relicUsed");
    private static final java.util.Set<String> HISTORY_KINDS = java.util.Set.of("cast", "ability", "trigger",
        "land", "attack", "block", "death", "exile", "bounce", "tokens", "damage", "gameEnd");
    private static final java.util.Set<String> ELEMENT_ATTRS = java.util.Set.of("image", "mode", "slice",
        "sliceScale", "tileScale", "fill", "border", "borderWidth", "radius", "text");

    /** True if the key is a setting the game uses (so typos can be reported). */
    public static boolean isKnownKey(String key) {
        if (key.equals("name") || key.equals("extends")) {
            return true;
        }
        if (key.startsWith("color.history.")) {
            return HISTORY_KINDS.contains(key.substring("color.history.".length()));
        }
        if (key.startsWith("color.")) {
            return COLOR_KEYS.contains(key.substring("color.".length()));
        }
        if (key.startsWith("card.frame.")) {
            return java.util.Set.of("W", "U", "B", "R", "G", "multi", "colorless", "land").contains(key.substring(11));
        }
        if (key.startsWith("font.")) {
            return java.util.Set.of("body", "heading", "numbers", "scale").contains(key.substring(5));
        }
        if (key.startsWith("icon.history.")) {
            return HISTORY_KINDS.contains(key.substring("icon.history.".length()));
        }
        if (key.equals("icon.collapse") || key.equals("icon.expand")) {
            return true;
        }
        if (key.equals("popup.padding") || key.equals("popup.sideBorder")
            || key.equals("relic.frame.extend") || key.equals("consumable.frame.extend")) {
            return true;
        }
        int dot = key.lastIndexOf('.');
        return dot > 0 && Skin.elements().contains(key.substring(0, dot)) && ELEMENT_ATTRS.contains(key.substring(dot + 1));
    }

    /** Loads every picture and font the theme names, so missing files are reported at once. */
    private void checkFiles() {
        for (String key : values.keySet()) {
            if (key.endsWith(".image") || key.startsWith("icon.")) {
                image(key);
            } else if (key.startsWith("font.") && !key.equals("font.scale")) {
                font(key);
            } else if (key.startsWith("color.") || key.startsWith("card.frame.") || key.endsWith(".fill")
                || key.endsWith(".border") || key.endsWith(".text")) {
                color(key, null);
            }
        }
    }

    public String name() {
        return name;
    }

    /** Human-readable name from the file ({@code name=...}), else the folder name. */
    public String displayName() {
        return values.getOrDefault("name", name);
    }

    /** Problems found while loading (missing files, bad values); empty if all is well. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    public boolean has(String key) {
        String v = values.get(key);
        return v != null && !v.isBlank();
    }

    public String string(String key, String fallback) {
        String v = values.get(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** "#RRGGBB" or "#AARRGGBB" (or "none" = fully transparent). */
    public Color color(String key, Color fallback) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            if (v.equalsIgnoreCase("none") || v.equalsIgnoreCase("transparent")) {
                return new Color(0, 0, 0, 0);
            }
            String hex = v.startsWith("#") ? v.substring(1) : v;
            if (hex.length() == 8) {
                long argb = Long.parseLong(hex, 16);
                return new Color((int) argb, true);
            }
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            problems.add(key + ": not a color: " + v);
            return fallback;
        }
    }

    public double number(String key, double fallback) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            problems.add(key + ": not a number: " + v);
            return fallback;
        }
    }

    public boolean flag(String key, boolean fallback) {
        String v = values.get(key);
        return v == null || v.isBlank() ? fallback : v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    /** "top,left,bottom,right" (one number = all four). */
    public Insets insets(String key, Insets fallback) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            String[] p = v.split(",");
            if (p.length == 1) {
                int n = Integer.parseInt(p[0].trim());
                return new Insets(n, n, n, n);
            }
            return new Insets(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
        } catch (RuntimeException e) {
            problems.add(key + ": expected top,left,bottom,right: " + v);
            return fallback;
        }
    }

    /** An image file named by the key (path relative to the theme folder), or null. */
    public BufferedImage image(String key) {
        String v = values.get(key);
        if (v == null || v.isBlank() || v.equalsIgnoreCase("none")) {
            return null;
        }
        return images.computeIfAbsent(key + "=" + v, k -> Optional.ofNullable(readImage(key, v))).orElse(null);
    }

    private BufferedImage readImage(String key, String relative) {
        Path file = find(relative);
        if (file == null) {
            problems.add(key + ": image not found: " + relative);
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                problems.add(key + ": not a readable image: " + file);
            }
            return img;
        } catch (IOException e) {
            problems.add(key + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * A font: a font file in the theme (.ttf/.otf, path relative to the theme folder) or the name
     * of a font installed on the computer ("Serif", "Georgia", ...). Null if not set.
     */
    public Font font(String key) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        return fonts.computeIfAbsent(key + "=" + v, k -> Optional.ofNullable(readFont(key, v))).orElse(null);
    }

    private Font readFont(String key, String v) {
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            Path file = find(v);
            if (file == null) {
                problems.add(key + ": font file not found: " + v);
                return null;
            }
            try {
                return Font.createFont(Font.TRUETYPE_FONT, file.toFile());
            } catch (IOException | FontFormatException e) {
                problems.add(key + ": not a usable font: " + file + " (" + e.getMessage() + ")");
                return null;
            }
        }
        // A list of installed fonts, first one found wins: "Book Antiqua, Georgia, Serif".
        java.util.Set<String> installed = java.util.Set.of(
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
        java.util.Set<String> lowerInstalled = new java.util.HashSet<>();
        installed.forEach(f -> lowerInstalled.add(f.toLowerCase(Locale.ROOT)));
        for (String family : v.split(",")) {
            String f = family.trim();
            String fl = f.toLowerCase(Locale.ROOT);
            if (fl.equals("serif") || fl.equals("sansserif") || fl.equals("sans-serif") || fl.equals("monospaced")
                || fl.equals("dialog") || lowerInstalled.contains(fl)) {
                return new Font(fl.equals("sans-serif") ? Font.SANS_SERIF : f, Font.PLAIN, 12);
            }
        }
        problems.add(key + ": none of these fonts is installed: " + v + " (using the default font)");
        return null;
    }

    /** The file in this theme's folder, else in a theme it extends. */
    private Path find(String relative) {
        for (Path folder : folders) {
            try {
                Path p = folder.resolve(relative).normalize();
                if (p.startsWith(folder.normalize()) && Files.isRegularFile(p)) {
                    return p;
                }
            } catch (java.nio.file.InvalidPathException e) {
                // not a usable path
            }
        }
        return null;
    }

    /** All keys set (merged), for tests and the designer's checks. */
    public java.util.Set<String> keys() {
        return java.util.Set.copyOf(values.keySet());
    }
}
