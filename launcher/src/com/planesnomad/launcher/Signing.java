package com.planesnomad.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 signatures for builds (needs Java 15+ at run time, which the game needs anyway).
 *
 * The team keeps one private key secret (a GitHub Actions secret, RELEASE_SIGNING_KEY); the
 * matching public key is built into the launcher ({@link Launcher#PUBLIC_KEY}). A launcher with a
 * public key installs only builds signed with the private key, so even someone who could upload
 * files to the releases page couldn't get a changed build installed.
 *
 * Command line (from the build or by a maintainer):
 *   java -cp launcher.jar com.planesnomad.launcher.Signing generate
 *       prints a new key pair: the public key goes into Launcher.PUBLIC_KEY, the private key into
 *       the RELEASE_SIGNING_KEY secret (never into the repository)
 *   java -cp launcher.jar com.planesnomad.launcher.Signing sign <test-build.properties>
 *       adds "signature=..." to a build description, with the key from RELEASE_SIGNING_KEY
 */
public final class Signing {

    public static boolean verify(String publicKeyBase64, byte[] data, String signatureBase64) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(Base64.getDecoder().decode(signatureBase64.trim()));
        } catch (Exception e) {
            return false;   // bad key, bad signature format, or no Ed25519 in this Java: not valid
        }
    }

    public static String sign(String privateKeyBase64, byte[] data) throws Exception {
        PrivateKey key = KeyFactory.getInstance("Ed25519")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64.trim())));
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key);
        s.update(data);
        return Base64.getEncoder().encodeToString(s.sign());
    }

    /** {public key, private key}, Base64. */
    public static String[] generate() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        return new String[] {
            Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()),
            Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())};
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("generate")) {
            String[] k = generate();
            System.out.println("Public key (put into Launcher.PUBLIC_KEY):");
            System.out.println(k[0]);
            System.out.println();
            System.out.println("Private key (GitHub repository secret RELEASE_SIGNING_KEY; never commit it):");
            System.out.println(k[1]);
            return;
        }
        if (args.length == 2 && args[0].equals("sign")) {
            String key = System.getenv("RELEASE_SIGNING_KEY");
            if (key == null || key.trim().isEmpty()) {
                System.err.println("RELEASE_SIGNING_KEY is not set: build left unsigned.");
                return;
            }
            String text = new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8);
            Release r = Release.parse(text);
            String sig = sign(key, r.signedText().getBytes(StandardCharsets.UTF_8));
            Files.write(Paths.get(args[1]), (text + (text.endsWith("\n") ? "" : "\n") + "signature=" + sig + "\n")
                .getBytes(StandardCharsets.UTF_8));
            System.out.println("Signed " + r.version);
            return;
        }
        System.err.println("Usage: Signing generate | Signing sign <test-build.properties>");
        System.exit(2);
    }

    private Signing() { }
}
