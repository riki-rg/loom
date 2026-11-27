#!/usr/bin/env bash
# Fetch the Robolectric android-all jar used for off-device Compose screenshots.
# Maven Central rate-limits this 151MB artifact hard (HTTP 429); the Google mirror serves it
# at full speed. Run once after cloning; the jar is gitignored.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/robolectric-deps"
JAR=android-all-instrumented-14-robolectric-10818077-i7.jar
mkdir -p "$DEST"
[ -s "$DEST/$JAR" ] && { echo "already present: $DEST/$JAR"; exit 0; }
curl -fL --retry 3 --retry-all-errors \
  "https://maven-central.storage-download.googleapis.com/maven2/org/robolectric/android-all-instrumented/14-robolectric-10818077-i7/$JAR" \
  -o "$DEST/$JAR"
ls -lh "$DEST/$JAR"
