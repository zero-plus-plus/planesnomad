# Test builds: one-click download and updates

## For testers

1. Get Java 17 or newer once: https://adoptium.net (JDK 17 or later).
2. Open the repository's **Releases** page and download the newest `Planesnomad-test-build-<n>.zip`.
3. Unzip it anywhere and double-click **`Planesnomad.bat`**.

From then on, just double-click `Planesnomad.bat`: it checks for a newer test build, asks, and
updates itself. Without internet it starts the version you have. Bug reports: attach
`logs\game.log`. The zip's `README.txt` says the same.

## How it works

- **Building:** every merge to `main` runs `.github/workflows/test-build.yml`. It builds the game
  with `scripts/build-test-release.sh`, runs the launcher, encounter and match-setup tests, and
  publishes a release `test-build-<n>`. Only the 10 newest test builds are kept. It can also be
  started by hand: Actions → Test build → Run workflow.
- **The launcher** (`launcher/`, about 25 KB, no libraries) is what `Planesnomad.bat` runs. It
  reads `test-build.properties` from the newest release, downloads the zip, checks it, unpacks it
  into `versions/<n>/`, and starts the game (`launch.properties` in the build says how).

## Safety

| Risk | What protects against it |
|---|---|
| A download changed on the way, or damaged | Size and SHA-256 must match the published description; otherwise it's deleted and nothing is installed. |
| Being sent somewhere else | Only `https`, only GitHub's own hosts; every redirect is checked. Names in the description must be plain names. |
| A zip that writes outside its folder ("zip slip") | Only `versions/<n>/…` (plus `launcher.jar` and `README.txt`) is unpacked; `..`, absolute paths and drive letters are refused; file count and unpacked size are capped. |
| A half-installed update | Unpacked into a temporary folder, switched over only when complete. |
| A new build that won't start | The previous version is kept; if the new one fails within 20 seconds, the launcher offers the previous one. |
| Tampered libraries in the build | Every library must match the SHA-256 in `build/dependencies.txt`. |
| Someone else's code running with the release token | The workflow uses no third-party actions and only gets permission to write releases. |
| Someone who can upload release files (e.g. a stolen GitHub login) | **Signing (optional, recommended before public releases):** see below. Without it, the checksum alone can't stop this, since the description and zip come from the same place. |
| Too-old Java | The launcher is built for Java 8, so it can still say "you need Java 17" instead of failing silently. |

Not covered: Windows SmartScreen may warn about the `.bat` the first time (only a paid
code-signing certificate avoids that), and a tester's PC that's already compromised.

### Turning on signing (owner, once)

1. On a trusted computer: `java -cp launcher.jar com.planesnomad.launcher.Signing generate`
   (`launcher.jar` is in any test build). It prints a public and a private key.
2. GitHub → repository Settings → Secrets and variables → Actions → New secret
   `RELEASE_SIGNING_KEY` = the private key. Keep a backup somewhere safe; never commit it.
3. Put the public key into `Launcher.PUBLIC_KEY` (`launcher/src/com/planesnomad/launcher/Launcher.java`)
   and merge. From the next build on, builds are signed, and launchers from that build on refuse
   anything unsigned or signed with another key.

## Building one by hand

`scripts/build-test-release.sh test-build-local` (Linux, Mac or WSL with Java 17+, curl and
python3) writes `dist/Planesnomad-test-build-local.zip`. Add `RUN_TESTS=1` to run the tests first.
