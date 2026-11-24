package com.planesnomad.launcher;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Starts Planesnomad and keeps it up to date (Planesnomad.bat runs this).
 *
 * Folder layout (wherever the tester unzipped it):
 * <pre>
 *   Planesnomad.bat        double-click to play
 *   launcher.jar           this program
 *   current.txt            the installed version, e.g. test-build-12
 *   previous.txt           the one before (kept, to go back to if a new build won't start)
 *   versions/&lt;version&gt;/  the game: planesnomad.jar, lib/, forge-engine/, data/, themes/, decks/
 *   logs/game.log          what the game printed last time (send it with bug reports)
 * </pre>
 *
 * Each start: Java version check; look for a newer build on GitHub (skipped quietly when offline);
 * ask before updating; download, verify and unpack it safely ({@link Release}, {@link SafeUnzip});
 * switch over only when complete; start the game; if a new version crashes right away, offer the
 * previous one.
 *
 * Settings: a file "no-update-check.txt" next to launcher.jar turns the update check off.
 */
public final class Launcher {

    /** Format of this launcher (shown in its window titles). */
    public static final int VERSION = 1;

    /**
     * The team's public signing key (Base64). Empty: builds are checked by size and SHA-256 only
     * (fine for testing). Set it (see {@link Signing}) and this launcher installs only builds signed
     * by the team - recommended before public releases.
     */
    public static final String PUBLIC_KEY = "";

    /** The game needs Java 17 or newer. */
    public static final int JAVA_NEEDED = 17;

    private static final String TITLE = "Planesnomad";
    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern CLASS = Pattern.compile("[A-Za-z_][A-Za-z0-9_.$]{0,200}");

    private final File root;

    Launcher(File root) {
        this.root = root;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look is fine
        }
        int java = javaFeature();
        if (java < JAVA_NEEDED) {
            message("Planesnomad needs Java " + JAVA_NEEDED + " or newer; this computer has Java " + java + ".\n\n"
                + "Get it from https://adoptium.net (choose JDK " + JAVA_NEEDED + " or later), then start Planesnomad again.",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        File root;
        try {
            root = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            root = new File(".").getAbsoluteFile();
        }
        int code = new Launcher(root).run();
        System.exit(code);
    }

    int run() {
        String current = installed();
        if (!new File(root, "no-update-check.txt").exists()) {
            current = checkForUpdate(current);
        }
        if (current == null) {
            message("No game version is installed yet, and none could be downloaded.\n"
                + "Check the internet connection and start Planesnomad again.", JOptionPane.ERROR_MESSAGE);
            return 1;
        }
        return start(current, true);
    }

    /** The installed version (current.txt), if its folder is there. */
    String installed() {
        String v = Files2.read(new File(root, "current.txt"));
        return v != null && PLAIN.matcher(v).matches() && new File(new File(root, "versions"), v).isDirectory() ? v : null;
    }

    /** @return the version to start (new, or the current one if there's no update / it was declined / it failed) */
    String checkForUpdate(String current) {
        Release latest;
        try {
            latest = Release.latest();
        } catch (Release.NotFound e) {
            return current;   // nothing published yet
        } catch (IOException e) {
            if (current == null) {
                message("Couldn't reach GitHub to download the game:\n" + e.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
            return current;   // offline: play what's installed
        }
        if (latest.version.equals(current)) {
            return current;
        }
        if (current != null) {
            if (!ask("A new test build is ready: " + latest.version + (latest.built.isEmpty() ? "" : " (built " + latest.built + ")")
                    + ".\nYou have " + current + ".\n\nUpdate now? (" + (latest.size / (1024 * 1024) + 1) + " MB download)")) {
                return current;
            }
        }
        try {
            install(latest, current);
            return latest.version;
        } catch (IOException e) {
            message("The update to " + latest.version + " was not installed:\n" + e.getMessage()
                + (current == null ? "" : "\n\nStarting " + current + " instead."), JOptionPane.WARNING_MESSAGE);
            return current;
        }
    }

    /** Download, check, unpack, switch over; keeps the previous version and removes older ones. */
    void install(Release r, String current) throws IOException {
        r.checkSignature(PUBLIC_KEY);
        File downloads = new File(root, "downloads");
        downloads.mkdirs();
        File zip = new File(downloads, r.zip);
        Progress p = Progress.show("Downloading " + r.version + "...");
        try {
            r.download(zip, p);
            p.text("Unpacking " + r.version + "...");
            SafeUnzip.install(zip, r.version, root);
        } finally {
            p.close();
            zip.delete();
        }
        if (current != null && !current.equals(r.version)) {
            Files2.writeAtomic(new File(root, "previous.txt"), current);
        }
        Files2.writeAtomic(new File(root, "current.txt"), r.version);
        cleanUp();
    }

    /** Keeps the current and previous versions; removes older ones and leftovers. */
    void cleanUp() {
        String cur = Files2.read(new File(root, "current.txt"));
        String prev = Files2.read(new File(root, "previous.txt"));
        File[] all = new File(root, "versions").listFiles();
        if (all == null) {
            return;
        }
        for (File f : all) {
            String n = f.getName();
            if (!n.equals(cur) && !n.equals(prev)) {
                Files2.deleteTree(f);
            }
        }
    }

    /** Starts the game; if a just-installed or current version fails right away, offers the previous one. */
    int start(String version, boolean offerPrevious) {
        File dir = new File(new File(root, "versions"), version);
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaCommand());
        Properties launch = launchProperties(dir);
        for (String opt : split(launch.getProperty("jvm", ""))) {
            if (opt.startsWith("-D") || opt.startsWith("-Xmx") || opt.startsWith("-Xss")) {
                cmd.add(opt);   // only harmless JVM settings are taken from the build
            }
        }
        cmd.add("-cp");
        cmd.add("planesnomad.jar" + File.pathSeparator + "lib" + File.separator + "*");
        String main = launch.getProperty("main", "com.planesnomad.gui.MatchViewerApp").trim();
        if (!CLASS.matcher(main).matches()) {
            main = "com.planesnomad.gui.MatchViewerApp";
        }
        cmd.add(main);
        for (String a : split(launch.getProperty("args", "encounter"))) {
            if (PLAIN.matcher(a.replace('/', '-')).matches()) {
                cmd.add(a);
            }
        }
        File logs = new File(root, "logs");
        logs.mkdirs();
        File log = new File(logs, "game.log");
        File old = new File(logs, "game-before.log");
        old.delete();
        log.renameTo(old);
        try {
            Process proc = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true)
                .redirectOutput(log).start();
            // A build that can't start usually fails within seconds.
            if (proc.waitFor(20, TimeUnit.SECONDS) && proc.exitValue() != 0) {
                String prev = Files2.read(new File(root, "previous.txt"));
                boolean canGoBack = offerPrevious && prev != null && !prev.equals(version)
                    && new File(new File(root, "versions"), prev).isDirectory();
                String text = "Planesnomad " + version + " stopped with an error.\nDetails: " + log.getPath();
                if (canGoBack) {
                    if (ask(text + "\n\nStart the previous version (" + prev + ") instead?")) {
                        Files2.writeAtomic(new File(root, "previous.txt"), version);
                        Files2.writeAtomic(new File(root, "current.txt"), prev);
                        return start(prev, false);
                    }
                } else {
                    message(text, JOptionPane.ERROR_MESSAGE);
                }
                return 1;
            }
            return 0;   // running (or closed normally); the launcher's job is done
        } catch (Exception e) {
            message("Couldn't start the game: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
            return 1;
        }
    }

    /** versions/&lt;v&gt;/launch.properties: main class, its arguments, JVM settings (all checked). */
    static Properties launchProperties(File dir) {
        Properties p = new Properties();
        File f = new File(dir, "launch.properties");
        if (f.isFile()) {
            try {
                InputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
            } catch (IOException ignored) {
                // defaults
            }
        }
        return p;
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<String>();
        for (String part : s.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    /** The Java running this launcher (javaw on Windows: no extra console window). */
    static String javaCommand() {
        File bin = new File(System.getProperty("java.home"), "bin");
        File javaw = new File(bin, "javaw.exe");
        if (javaw.isFile()) {
            return javaw.getPath();
        }
        File exe = new File(bin, "java.exe");
        return exe.isFile() ? exe.getPath() : new File(bin, "java").getPath();
    }

    static int javaFeature() {
        String v = System.getProperty("java.specification.version", "0");
        if (v.startsWith("1.")) {
            v = v.substring(2);
        }
        try {
            return Integer.parseInt(v.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Yes/no question; without a screen (tests, servers) the answer is yes. */
    static boolean ask(String text) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println(text + " -> yes (no screen)");
            return true;
        }
        return JOptionPane.showConfirmDialog(null, text, TITLE, JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static void message(String text, int type) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println(text);
            return;
        }
        JOptionPane.showMessageDialog(null, text, TITLE, type);
    }

    /** A small window with a progress bar while downloading. */
    static final class Progress implements Release.Progress {
        private JDialog dialog;
        private JProgressBar bar;
        private JLabel label;

        static Progress show(final String text) {
            final Progress p = new Progress();
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return p;
            }
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        p.dialog = new JDialog((java.awt.Frame) null, TITLE + " - update", false);
                        JPanel panel = new JPanel(new BorderLayout(8, 8));
                        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
                        p.label = new JLabel(text);
                        p.bar = new JProgressBar(0, 1000);
                        p.bar.setStringPainted(true);
                        panel.add(p.label, BorderLayout.NORTH);
                        panel.add(p.bar, BorderLayout.CENTER);
                        p.dialog.setContentPane(panel);
                        p.dialog.setSize(380, 110);
                        p.dialog.setLocationRelativeTo(null);
                        p.dialog.setVisible(true);
                    }
                });
            } catch (Exception ignored) {
                // no window: download goes on without it
            }
            return p;
        }

        @Override
        public void update(final long done, final long total) {
            if (bar == null) {
                return;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    bar.setValue((int) (1000 * done / Math.max(1, total)));
                    bar.setString((done / (1024 * 1024)) + " / " + (total / (1024 * 1024)) + " MB");
                }
            });
        }

        void text(final String t) {
            if (label != null) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        label.setText(t);
                        bar.setIndeterminate(true);
                    }
                });
            }
        }

        void close() {
            if (dialog != null) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dispose();
                    }
                });
            }
        }
    }
}
