package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.ui.MatchView;
import com.planesnomad.gui.ui.Theme;
import com.planesnomad.gui.ui.TopBar;
import com.planesnomad.gui.ui.theme.Skin;
import com.planesnomad.gui.ui.theme.ThemePack;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks for swappable themes: the shipped themes load without problems, mistakes in a theme are
 * reported (not crashes), 9-slice frames keep their corners, and switching theme changes the
 * screen. With an output folder, saves the same board in every theme for comparison.
 */
public final class ThemeTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        // 1. Shipped themes.
        List<String> names = ThemePack.available();
        System.out.println("Themes: " + names);
        check("Themes found: default and the fantasy example", names.contains("default") && names.contains("fantasy-example"));
        for (String n : names) {
            ThemePack t = ThemePack.load(n);
            check("Theme '" + n + "' loads without problems " + t.problems(), t.problems().isEmpty());
        }
        ThemePack fantasy = ThemePack.load("fantasy-example");
        check("A theme inherits what it leaves out (fantasy has default's history colors)",
            fantasy.has("color.history.attack") && fantasy.has("color.panel"));
        check("A theme's own values win (fantasy panel color differs from default)",
            !fantasy.color("color.panel", null).equals(ThemePack.load("default").color("color.panel", null)));
        check("The default theme file matches the built-in colors (panel #15181D)",
            ThemePack.load("default").color("color.panel", null).equals(new Color(0x15181D)));

        // 2. Mistakes in a theme are reported, never fatal.
        Path tmp = Files.createTempDirectory("themes");
        Path broken = tmp.resolve("broken");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve(ThemePack.FILE), String.join("\n",
            "name = Broken on purpose",
            "popup.imgae = frames/x.png",
            "panel.image = frames/missing.png",
            "color.text = not-a-color",
            "font.heading = No Such Font Anywhere",
            "color.accent = #FF00FF   # comment after a value"));
        String oldRoot = System.getProperty("planesnomad.themes");
        System.setProperty("planesnomad.themes", tmp.toString());
        ThemePack b = ThemePack.load("broken");
        System.setProperty("planesnomad.themes", oldRoot == null ? "themes" : oldRoot);
        if (oldRoot == null) {
            System.clearProperty("planesnomad.themes");
        }
        String all = String.join(" | ", b.problems());
        System.out.println("  reported: " + all);
        check("A misspelled setting is reported as unknown", all.contains("popup.imgae"));
        check("A missing picture is reported", all.contains("missing.png"));
        check("A bad color is reported", all.contains("not-a-color"));
        check("A font that isn't installed is reported", all.contains("No Such Font"));
        check("A comment after a value is ignored (#FF00FF read)", new Color(0xFF00FF).equals(b.color("color.accent", null)));
        check("Missing files don't stop the theme: it still has a name and values", b.displayName().equals("Broken on purpose"));

        // 3. 9-slice: corners keep their size, the middle stretches.
        BufferedImage frame = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) {
                boolean corner = (x < 10 || x >= 20) && (y < 10 || y >= 20);
                frame.setRGB(x, y, corner ? 0xFFFF0000 : 0xFF0000FF);
            }
        }
        BufferedImage out = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        Skin.nineSlice(g, frame, new Insets(10, 10, 10, 10), 1.0, 0, 0, 200, 100);
        g.dispose();
        check("9-slice: corners stay 10 px (red at 5,5 and 194,94), middle stretched (blue at 100,50)",
            out.getRGB(5, 5) == 0xFFFF0000 && out.getRGB(194, 94) == 0xFFFF0000 && out.getRGB(100, 50) == 0xFF0000FF
                && out.getRGB(15, 5) == 0xFF0000FF);

        // 4. The screen changes with the theme, and switching back restores it.
        ForgeEngine.initialize();
        var images = ImageSetup.create();
        MatchSnapshot snap = com.planesnomad.gui.StressTest.board(com.planesnomad.ForgeEngine.initialize(), images, "a-20-creatures");
        images.preloadNow(snap.you().battlefield());
        File dir = args.length > 0 ? new File(args[0]) : null;
        if (dir != null) {
            dir.mkdirs();
        }
        render(snap, ThemePack.load("default"));   // warm-up: card pictures finish loading
        Thread.sleep(1500);
        render(snap, ThemePack.load("default"));
        List<BufferedImage> shots = new ArrayList<>();
        for (String n : List.of("default", "fantasy-example", "default")) {
            BufferedImage img = render(snap, ThemePack.load(n));
            shots.add(img);
            if (dir != null) {
                ImageIO.write(img, "png", new File(dir, "theme-" + shots.size() + "-" + n + ".png"));
            }
        }
        double changed = difference(shots.get(0), shots.get(1));
        double restored = difference(shots.get(0), shots.get(2));
        check(String.format("Switching to the fantasy theme changes the screen (%.0f%% of pixels)", changed * 100), changed > 0.15);
        check(String.format("Switching back to default restores it (%.2f%% differ)", restored * 100), restored < 0.01);
        Theme.apply(ThemePack.load("default"));

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL THEME CHECKS PASSED");
        } else {
            System.out.println(failures + " THEME CHECK(S) FAILED");
            System.exit(1);
        }
        System.exit(0);
    }

    private static BufferedImage render(MatchSnapshot snap, ThemePack theme) throws Exception {
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            MatchView view = new MatchView(new TopBar.Controls() {
                public void setPaused(boolean p) { }
                public void step() { }
                public void setSpeed(double s) { }
                public void setRevealEnemyHand(boolean r) { }
                public void newMatch() { }
            });
            view.setSize(1280, 720);
            view.applyTheme(theme);
            view.update(snap);
            for (int i = 0; i < 3; i++) {
                layout(view);
            }
            Graphics2D g = img.createGraphics();
            view.printAll(g);
            g.dispose();
        });
        return img;
    }

    private static void layout(Component c) {
        if (c instanceof Container k) {
            k.doLayout();
            for (Component x : k.getComponents()) {
                layout(x);
            }
        }
    }

    /** Share of pixels that differ noticeably. */
    private static double difference(BufferedImage a, BufferedImage b) {
        long diff = 0;
        for (int y = 0; y < a.getHeight(); y += 2) {
            for (int x = 0; x < a.getWidth(); x += 2) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                int d = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255))
                    + Math.abs((p & 255) - (q & 255));
                if (d > 30) {
                    diff++;
                }
            }
        }
        return diff / (double) ((a.getHeight() / 2) * (a.getWidth() / 2));
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }

    private ThemeTest() { }
}
