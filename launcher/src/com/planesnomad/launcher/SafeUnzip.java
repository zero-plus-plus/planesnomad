package com.planesnomad.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unpacks a build zip without letting it write anywhere it shouldn't:
 * - only entries under {@code versions/<version>/} are unpacked (into a temporary folder that is
 *   renamed into place only when everything was written), plus {@code launcher.jar} (kept as
 *   launcher-new.jar, swapped in by Planesnomad.bat on the next start) and {@code README.txt}
 * - names with "..", absolute paths, drive letters or backslash tricks are refused
 * - the number of files and the unpacked size are capped (counted while writing, not taken
 *   from the zip's own claims)
 */
public final class SafeUnzip {

    public static final int MAX_FILES = 120_000;
    public static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;

    /** @return the installed version folder */
    public static File install(File zip, String version, File root) throws IOException {
        String prefix = "versions/" + version + "/";
        File versions = new File(root, "versions");
        File partial = new File(versions, version + ".partial");
        File done = new File(versions, version);
        Files2.deleteTree(partial);
        if (!partial.mkdirs()) {
            throw new IOException("Can't create " + partial);
        }
        int files = 0;
        long bytes = 0;
        boolean sawGame = false;
        ZipFile z = new ZipFile(zip);
        try {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                check(name);
                File target;
                if (name.startsWith(prefix)) {
                    String rest = name.substring(prefix.length());
                    if (rest.isEmpty()) {
                        continue;
                    }
                    target = new File(partial, rest.replace('/', File.separatorChar));
                    sawGame |= rest.equals("planesnomad.jar");
                } else if (name.equals("launcher.jar")) {
                    target = new File(root, "launcher-new.jar");
                } else if (name.equals("README.txt")) {
                    target = new File(root, "README.txt");
                } else {
                    continue;   // anything else in the zip is ignored
                }
                if (!inside(target, root)) {
                    throw new IOException("Refused a file outside the game folder: " + name);
                }
                if (++files > MAX_FILES) {
                    throw new IOException("The build has too many files");
                }
                if (e.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Can't create " + parent);
                }
                InputStream in = z.getInputStream(e);
                OutputStream out = new FileOutputStream(target);
                try {
                    byte[] b = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        bytes += n;
                        if (bytes > MAX_BYTES) {
                            throw new IOException("The build unpacks to more than expected");
                        }
                        out.write(b, 0, n);
                    }
                } finally {
                    out.close();
                    in.close();
                }
            }
        } catch (IOException ex) {
            Files2.deleteTree(partial);
            throw ex;
        } finally {
            z.close();
        }
        if (!sawGame) {
            Files2.deleteTree(partial);
            throw new IOException("The download doesn't contain the game for " + version);
        }
        Files2.deleteTree(done);
        if (!partial.renameTo(done)) {
            throw new IOException("Can't move the new version into place: " + done);
        }
        return done;
    }

    /** Refuses absolute paths, drive letters, backslashes and ".." parts. */
    static void check(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.contains("\0")) {
            throw new IOException("Refused a file name in the download: " + name);
        }
        for (String part : name.split("/")) {
            if (part.equals("..") || part.equals(".")) {
                throw new IOException("Refused a file name in the download: " + name);
            }
        }
    }

    static boolean inside(File f, File root) throws IOException {
        String r = root.getCanonicalPath() + File.separator;
        return f.getCanonicalPath().startsWith(r);
    }

    private SafeUnzip() { }
}
