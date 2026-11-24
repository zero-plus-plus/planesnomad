#!/usr/bin/env bash
# Builds a Planesnomad test release from this repository (GitHub runs it for every merge to main,
# see .github/workflows/test-build.yml; it also works on any Linux/Mac/WSL machine with Java 17+).
#
#   scripts/build-test-release.sh <version>          e.g. test-build-12
#
# Produces in dist/ (or $OUT):
#   Planesnomad-<version>.zip   the whole thing for a first install: unzip, double-click Planesnomad.bat
#   test-build.properties       what the launcher reads to find, check (size, SHA-256) and install it
#
# Safety: every library comes from Maven Central over https and must match the SHA-256 in
# build/dependencies.txt; the build stops on any mismatch. With RELEASE_SIGNING_KEY set, the
# description is signed (Ed25519) for launchers that require signed builds.
# RUN_TESTS=1 also runs the launcher and encounter tests before packaging.
set -euo pipefail

VERSION="${1:?usage: build-test-release.sh <version>, e.g. test-build-12}"
if [[ ! "$VERSION" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$ ]]; then
  echo "Bad version name: $VERSION" >&2; exit 1
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT:-$ROOT/dist}"
WORK="$OUT/work"
CACHE="${DEP_CACHE:-$HOME/.cache/planesnomad-deps}"
rm -rf "$WORK"
mkdir -p "$WORK" "$CACHE" "$OUT"
cd "$ROOT"

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -c1-64; else shasum -a 256 "$1" | cut -c1-64; fi; }

echo "== Libraries (Maven Central, checked against build/dependencies.txt)"
DEPS=()
while read -r want path; do
  [[ -z "$want" || "$want" == \#* ]] && continue
  file="$CACHE/$path"
  if [[ ! -f "$file" || "$(sha256 "$file")" != "$want" ]]; then
    mkdir -p "$(dirname "$file")"
    curl -fsSL --proto '=https' --tlsv1.2 --retry 3 -o "$file.part" "https://repo1.maven.org/maven2/$path"
    got="$(sha256 "$file.part")"
    if [[ "$got" != "$want" ]]; then
      echo "SHA-256 mismatch for $path: expected $want, got $got. Stopping." >&2
      rm -f "$file.part"; exit 1
    fi
    mv "$file.part" "$file"
  fi
  DEPS+=("$file")
done < build/dependencies.txt
FORGE=(forge-engine/forge-core.jar forge-engine/forge-game.jar forge-engine/forge-ai.jar)
CP="$(IFS=:; echo "${FORGE[*]}:${DEPS[*]}")"

echo "== Compiling the game (Java 17)"
mkdir -p "$WORK/classes"
find src/main/java -name '*.java' > "$WORK/sources.txt"
javac --release 17 -encoding UTF-8 -nowarn -d "$WORK/classes" -cp "$CP" @"$WORK/sources.txt"
jar --create --file "$WORK/planesnomad.jar" -C "$WORK/classes" .

echo "== Compiling the launcher (Java 8, so an old Java still gets a clear message)"
mkdir -p "$WORK/launcher-classes"
javac --release 8 -encoding UTF-8 -nowarn -d "$WORK/launcher-classes" $(find launcher/src -name '*.java')
jar --create --file "$WORK/launcher.jar" --main-class com.planesnomad.launcher.Launcher -C "$WORK/launcher-classes" .

if [[ "${RUN_TESTS:-0}" == "1" ]]; then
  echo "== Tests"
  mkdir -p "$WORK/launcher-test"
  javac -encoding UTF-8 -nowarn -d "$WORK/launcher-test" -cp "$WORK/launcher-classes" $(find launcher/test -name '*.java')
  java -Djava.awt.headless=true -cp "$WORK/launcher-test:$WORK/launcher-classes" com.planesnomad.launcher.LauncherTest
  java -Djava.awt.headless=true -Dplanesnomad.downloads=false -cp "$WORK/classes:$CP" com.planesnomad.encounter.EncounterTest
  java -Djava.awt.headless=true -Dplanesnomad.downloads=false -cp "$WORK/classes:$CP" com.planesnomad.match.MatchSetupTest
fi

echo "== Assembling $VERSION"
STAGE="$WORK/stage"
GAME="$STAGE/versions/$VERSION"
mkdir -p "$GAME/lib" "$GAME/forge-engine"
cp "$WORK/planesnomad.jar" "$GAME/"
cp "${FORGE[@]}" "${DEPS[@]}" "$GAME/lib/"
# Forge's card data; translated card-name lists are left out (the game runs in English).
cp -r forge-engine/resources "$GAME/forge-engine/"
rm -f "$GAME"/forge-engine/resources/languages/cardnames-*.txt
cp -r data themes decks "$GAME/"
cp LICENSE "$GAME/"
COMMIT="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "$GAME/launch.properties" <<PROPS
# Read by the launcher: what to start (checked before use).
main=com.planesnomad.gui.MatchViewerApp
args=encounter
jvm=-Xmx2g
PROPS
printf 'version=%s\ncommit=%s\nbuilt=%s\n' "$VERSION" "$COMMIT" "$BUILT" > "$GAME/version.txt"
cp "$WORK/launcher.jar" launcher/Planesnomad.bat launcher/README.txt "$STAGE/"
printf '%s\r\n' "$VERSION" > "$STAGE/current.txt"

ZIP="Planesnomad-$VERSION.zip"
python3 - "$STAGE" "$OUT/$ZIP" <<'PY'
import os, sys, zipfile
stage, out = sys.argv[1], sys.argv[2]
files = []
for d, _, names in os.walk(stage):
    for n in names:
        files.append(os.path.relpath(os.path.join(d, n), stage).replace(os.sep, "/"))
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for rel in sorted(files):
        info = zipfile.ZipInfo(rel, date_time=(2020, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        with open(os.path.join(stage, rel), "rb") as f:
            z.writestr(info, f.read())
PY

SIZE="$(wc -c < "$OUT/$ZIP" | tr -d ' ')"
SUM="$(sha256 "$OUT/$ZIP")"
cat > "$OUT/test-build.properties" <<PROPS
version=$VERSION
zip=$ZIP
size=$SIZE
sha256=$SUM
built=$BUILT
commit=$COMMIT
launcher=1
PROPS
java -cp "$WORK/launcher.jar" com.planesnomad.launcher.Signing sign "$OUT/test-build.properties"
echo "== Done: $OUT/$ZIP ($((SIZE / 1024 / 1024)) MB), sha256 $SUM"
