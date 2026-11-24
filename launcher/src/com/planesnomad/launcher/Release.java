package com.planesnomad.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A published build, as described by the small {@code test-build.properties} file attached to the
 * latest GitHub release, and the safe download of its zip.
 *
 * Safety rules (kept in one place on purpose):
 * - only https, only GitHub's own hosts; every redirect is checked against that list
 * - names taken from the file must look like plain names (no slashes, no "..")
 * - sizes are capped; the download is checked against the published size and SHA-256
 * - when the launcher has the team's public key built in, the description must carry a valid
 *   Ed25519 signature, or the build is refused
 *
 * Compiled for Java 8 so that a too-old Java can still show a helpful message.
 */
public final class Release {

    /** The only repository builds are taken from. */
    public static final String REPO = "zero-plus-plus/planesnomad";

    /** The description of the newest build (GitHub redirects "latest" to the newest release). */
    public static final String LATEST_URL = "https://github.com/" + REPO + "/releases/latest/download/test-build.properties";

    /** Hosts a download may come from (GitHub and its file servers). */
    private static final Set<String> HOSTS = new HashSet<String>(Arrays.asList(
        "github.com", "api.github.com", "objects.githubusercontent.com",
        "release-assets.githubusercontent.com", "github-releases.githubusercontent.com"));

    /** A plain name: letters, digits, dot, dash, underscore; no path parts. */
    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

    /** Largest build zip accepted. */
    public static final long MAX_ZIP_BYTES = 600L * 1024 * 1024;

    public final String version;
    public final String zip;
    public final long size;
    public final String sha256;
    public final String built;
    public final String commit;
    public final String signature;

    private Release(Properties p) throws IOException {
        version = p.getProperty("version", "").trim();
        zip = p.getProperty("zip", "").trim();
        sha256 = p.getProperty("sha256", "").trim().toLowerCase(Locale.ROOT);
        built = p.getProperty("built", "").trim();
        commit = p.getProperty("commit", "").trim();
        signature = p.getProperty("signature", "").trim();
        long s;
        try {
            s = Long.parseLong(p.getProperty("size", "").trim());
        } catch (NumberFormatException e) {
            s = -1;
        }
        size = s;
        if (!PLAIN.matcher(version).matches()) {
            throw new IOException("Bad version name in the build description: " + version);
        }
        if (!PLAIN.matcher(zip).matches() || !zip.endsWith(".zip")) {
            throw new IOException("Bad file name in the build description: " + zip);
        }
        if (!HEX64.matcher(sha256).matches()) {
            throw new IOException("Bad checksum in the build description");
        }
        if (size <= 0 || size > MAX_ZIP_BYTES) {
            throw new IOException("Bad size in the build description: " + size);
        }
    }

    /** Reads a build description (e.g. a test-build.properties file). */
    public static Release parse(String text) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));
        return new Release(p);
    }

    /** The text that is signed: every field that decides what gets installed. */
    public String signedText() {
        return "planesnomad-release\nversion=" + version + "\nzip=" + zip + "\nsize=" + size + "\nsha256=" + sha256 + "\n";
    }

    /** Where release files are downloaded from (changed only by the launcher's own tests). */
    static String latestUrl = LATEST_URL;
    static String downloadBase = "https://github.com/" + REPO + "/releases/download/";

    /** Where the zip is: in the release named after the version. */
    public String zipUrl() {
        return downloadBase + version + "/" + zip;
    }

    /** Fetches the newest build's description (small text file). */
    public static Release latest() throws IOException {
        byte[] body = fetch(latestUrl, 64 * 1024, null);
        return parse(new String(body, StandardCharsets.UTF_8));
    }

    /**
     * Checks the signature when the launcher has a public key: missing or wrong = refused.
     * Without a built-in key (test phase) the checksum alone protects the download.
     */
    public void checkSignature(String publicKeyBase64) throws IOException {
        if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) {
            return;
        }
        if (signature.isEmpty()) {
            throw new IOException("This build is not signed, and this launcher only accepts signed builds.");
        }
        if (!Signing.verify(publicKeyBase64, signedText().getBytes(StandardCharsets.UTF_8), signature)) {
            throw new IOException("This build's signature is not valid. It was not installed.");
        }
    }

    /** Downloads the zip into {@code target}, then checks size and SHA-256 (deletes it if wrong). */
    public void download(File target, Progress progress) throws IOException {
        File part = new File(target.getPath() + ".part");
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }
        long got;
        OutputStream out = new FileOutputStream(part);
        try {
            got = stream(zipUrl(), out, sha, size, progress);
        } finally {
            out.close();
        }
        String hex = hex(sha.digest());
        if (got != size || !hex.equals(sha256)) {
            part.delete();
            throw new IOException("The download is damaged or was changed (size or checksum doesn't match). Nothing was installed.");
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Can't replace " + target);
        }
        if (!part.renameTo(target)) {
            throw new IOException("Can't finish the download into " + target);
        }
    }

    /** Download progress (bytes so far, total). */
    public interface Progress {
        void update(long done, long total);
    }

    // ------------------------------------------------------------------ HTTP

    static byte[] fetch(String url, int maxBytes, Progress progress) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        stream(url, buf, null, maxBytes, progress);
        return buf.toByteArray();
    }

    /** GET with redirects followed by hand, each one checked; at most maxBytes are read. */
    private static long stream(String url, OutputStream out, MessageDigest sha, long maxBytes, Progress progress) throws IOException {
        String current = url;
        for (int hop = 0; hop < 6; hop++) {
            URL u = new URL(current);
            checkUrl(u);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Planesnomad-Launcher");
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String next = c.getHeaderField("Location");
                c.disconnect();
                if (next == null) {
                    throw new IOException("Redirect without a target");
                }
                current = new URL(u, next).toString();
                continue;
            }
            if (code == 404) {
                throw new NotFound();
            }
            if (code != 200) {
                throw new IOException("GitHub answered " + code + " for " + u.getPath());
            }
            long total = c.getContentLengthLong();
            if (total > maxBytes) {
                throw new IOException("The file is larger than expected (" + total + " bytes)");
            }
            InputStream in = c.getInputStream();
            try {
                byte[] b = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(b)) > 0) {
                    done += n;
                    if (done > maxBytes) {
                        throw new IOException("The file is larger than expected");
                    }
                    out.write(b, 0, n);
                    if (sha != null) {
                        sha.update(b, 0, n);
                    }
                    if (progress != null) {
                        progress.update(done, total > 0 ? total : maxBytes);
                    }
                }
                return done;
            } finally {
                in.close();
            }
        }
        throw new IOException("Too many redirects");
    }

    /** https only, GitHub hosts only (tests may add a local test server, see allowTestHost). */
    static void checkUrl(URL u) throws IOException {
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (testHost != null && host.equals(testHost) && "http".equals(u.getProtocol())) {
            return;
        }
        if (!"https".equals(u.getProtocol()) || !HOSTS.contains(host)) {
            throw new IOException("Refused to download from " + u.getProtocol() + "://" + host + " (only GitHub is allowed)");
        }
    }

    private static String testHost;

    /** For the launcher's own tests only: also allow plain http from this host (e.g. 127.0.0.1). */
    static void allowTestHost(String host) {
        testHost = host;
    }

    /** "Not found": no published build yet (or no release with that file). */
    public static final class NotFound extends IOException {
        NotFound() {
            super("Not found");
        }
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xff));
        }
        return sb.toString();
    }
}
