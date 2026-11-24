package com.planesnomad.launcher;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Checks the launcher's safety rules against a local test server: good builds install, damaged or
 * changed ones don't, zips can't write outside their folder, only GitHub (https) is allowed,
 * signatures are enforced when a key is set, and old versions are cleaned up.
 */
public final class LauncherTest {

    private static int failures;
    private static final Map<String, byte[]> served = new HashMap<>();
    private static final Map<String, String> redirects = new HashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            if (redirects.containsKey(p)) {
                ex.getResponseHeaders().add("Location", redirects.get(p));
                ex.sendResponseHeaders(302, -1);
            } else if (served.containsKey(p)) {
                byte[] b = served.get(p);
                ex.sendResponseHeaders(200, b.length);
                try (OutputStream o = ex.getResponseBody()) {
                    o.write(b);
                }
            } else {
                ex.sendResponseHeaders(404, -1);
            }
            ex.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Release.allowTestHost("127.0.0.1");
        Release.latestUrl = base + "/latest/test-build.properties";
        Release.downloadBase = base + "/download/";

        File root = Files.createTempDirectory("pn-launcher").toFile();
        Launcher l = new Launcher(root);

        // 1. Nothing published yet: no crash, nothing installed.
        check("No release yet: nothing installed, no crash", l.checkForUpdate(null) == null);

        // 2. A good build installs.
        publish("test-build-1", game("test-build-1", null), null);
        String v = l.checkForUpdate(null);
        check("First build installs", "test-build-1".equals(v) && "test-build-1".equals(l.installed())
            && new File(root, "versions/test-build-1/planesnomad.jar").isFile());
        check("The launcher in the zip is kept as launcher-new.jar (swapped in by Planesnomad.bat)",
            new File(root, "launcher-new.jar").isFile());

        // 3. A damaged / changed download is refused; the installed version stays.
        byte[] good = game("test-build-2", null);
        publish("test-build-2", good, null);
        byte[] bad = good.clone();
        bad[bad.length / 2] ^= 0x55;
        served.put("/download/test-build-2/Planesnomad-test-build-2.zip", bad);
        String after = l.checkForUpdate(null);   // null current = no question asked
        check("A changed download (checksum mismatch) is refused, nothing installed",
            !"test-build-2".equals(after) && !new File(root, "versions/test-build-2").exists()
                && "test-build-1".equals(l.installed()));

        // 4. Zip tricks: files outside the version folder are refused.
        for (String evil : new String[] {"versions/test-build-3/../../evil.txt", "../evil.txt", "/etc/evil",
                "versions/test-build-3/a\\..\\..\\evil.txt", "C:/evil.txt"}) {
            publish("test-build-3", game("test-build-3", evil), null);
            boolean refused = !"test-build-3".equals(l.checkForUpdate("test-build-1-x"));
            check("Zip entry refused: " + evil, refused && !new File(root.getParentFile(), "evil.txt").exists()
                && !new File(root, "evil.txt").exists() && !new File(root, "versions/test-build-3").exists());
        }
        // Unrelated files in a zip are ignored, not written.
        publish("test-build-4", game("test-build-4", "other/notes.txt"), null);
        l.checkForUpdate(null);
        check("Files outside versions/<v>/ are ignored", "test-build-4".equals(l.installed())
            && !new File(root, "other").exists());

        // 5. Only GitHub over https.
        check("http to GitHub refused", refused("http://github.com/x"));
        check("Other hosts refused", refused("https://example.com/x"));
        check("GitHub https allowed", !refused("https://github.com/zero-plus-plus/planesnomad/releases/latest/download/test-build.properties")
            && !refused("https://objects.githubusercontent.com/x"));
        redirects.put("/latest/test-build.properties", "https://evil.example.com/x.properties");
        boolean redirectRefused;
        try {
            Release.latest();
            redirectRefused = false;
        } catch (IOException e) {
            redirectRefused = e.getMessage().contains("Refused");
        }
        check("A redirect to another host is refused", redirectRefused);
        redirects.clear();

        // 6. Bad descriptions are refused.
        for (String desc : new String[] {
                "version=../x\nzip=a.zip\nsize=1\nsha256=" + zeros(),
                "version=v1\nzip=../a.zip\nsize=1\nsha256=" + zeros(),
                "version=v1\nzip=a/b.zip\nsize=1\nsha256=" + zeros(),
                "version=v1\nzip=a.zip\nsize=999999999999\nsha256=" + zeros(),
                "version=v1\nzip=a.zip\nsize=1\nsha256=xyz"}) {
            boolean rejected;
            try {
                Release.parse(desc);
                rejected = false;
            } catch (IOException e) {
                rejected = true;
            }
            check("Bad build description refused: " + desc.replace('\n', ' '), rejected);
        }

        // 7. Signatures: required once a public key is set.
        String[] keys = Signing.generate();
        Release unsigned = Release.parse(new String(served.get("/latest/test-build.properties"), StandardCharsets.UTF_8));
        check("Unsigned build refused when a key is set", fails(unsigned, keys[0]));
        String sig = Signing.sign(keys[1], unsigned.signedText().getBytes(StandardCharsets.UTF_8));
        Release signed = Release.parse(new String(served.get("/latest/test-build.properties"), StandardCharsets.UTF_8)
            + "signature=" + sig + "\n");
        check("Correctly signed build accepted", !fails(signed, keys[0]));
        String text = new String(served.get("/latest/test-build.properties"), StandardCharsets.UTF_8)
            .replaceFirst("sha256=[0-9a-f]{64}", "sha256=" + zeros()) + "signature=" + sig + "\n";
        check("Signed description with a changed checksum refused", fails(Release.parse(text), keys[0]));
        check("Signed by another key refused", fails(signed, Signing.generate()[0]));
        check("No key set (test phase): checksum only", !fails(unsigned, ""));

        // 8. Current + previous kept, older versions removed.
        for (int i = 5; i <= 7; i++) {
            publish("test-build-" + i, game("test-build-" + i, null), null);
            l.checkForUpdate(l.installed());   // an update over the installed version (asked, answered yes)
        }
        File[] left = new File(root, "versions").listFiles();
        check("Only the current and previous versions are kept (" + left.length + ")", left.length == 2
            && new File(root, "versions/test-build-7").isDirectory() && new File(root, "versions/test-build-6").isDirectory()
            && "test-build-6".equals(Files2.read(new File(root, "previous.txt"))));
        check("No download leftovers", new File(root, "downloads").listFiles().length == 0
            && !new File(root, "versions/test-build-7.partial").exists());

        server.stop(0);
        System.out.println(failures == 0 ? "ALL LAUNCHER CHECKS PASSED" : failures + " LAUNCHER CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static boolean fails(Release r, String key) {
        try {
            r.checkSignature(key);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean refused(String url) {
        try {
            Release.checkUrl(new URL(url));
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static String zeros() {
        return "0000000000000000000000000000000000000000000000000000000000000000";
    }

    /** A build zip like the real one; "extra" adds one more entry with that name. */
    private static byte[] game(String version, String extra) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(buf)) {
            put(z, "Planesnomad.bat", "@echo off");
            put(z, "launcher.jar", "launcher");
            put(z, "versions/" + version + "/planesnomad.jar", "game " + version);
            put(z, "versions/" + version + "/data/encounters/x.json", "{}");
            if (extra != null) {
                put(z, extra, "x");
            }
        }
        return buf.toByteArray();
    }

    private static void put(ZipOutputStream z, String name, String text) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(text.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static void publish(String version, byte[] zip, String signature) throws Exception {
        String name = "Planesnomad-" + version + ".zip";
        served.put("/download/" + version + "/" + name, zip);
        String sum = Release.hex(MessageDigest.getInstance("SHA-256").digest(zip));
        served.put("/latest/test-build.properties", ("version=" + version + "\nzip=" + name + "\nsize=" + zip.length
            + "\nsha256=" + sum + "\n" + (signature == null ? "" : "signature=" + signature + "\n")).getBytes(StandardCharsets.UTF_8));
    }

    private static void check(String d, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + d);
        if (!ok) {
            failures++;
        }
    }
}
