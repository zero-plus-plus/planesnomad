package com.planesnomad.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Small file helpers (Java 8). */
final class Files2 {

    /** Deletes a folder and everything in it (does not follow links out of it). */
    static void deleteTree(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteTree(k);
                }
            }
        }
        f.delete();
    }

    /** First line of a text file, trimmed; null if missing or empty. */
    static String read(File f) {
        try {
            if (!f.isFile()) {
                return null;
            }
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            int nl = s.indexOf('\n');
            s = (nl >= 0 ? s.substring(0, nl) : s).trim();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes the file through a temporary file and a rename, so it is never half-written. */
    static void writeAtomic(File f, String text) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), (text + "\n").getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Files2() { }
}
