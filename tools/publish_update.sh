#!/bin/bash
# Publish the current release APK as an over-the-air update.
#
# The app checks <setup-url>/app/version.json on every launch (AppUpdater);
# this script is the other half: it puts the APK + manifest there. Run it
# after every release build that should reach the boxes:
#
#   JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
#   tools/publish_update.sh
#
# Safety properties:
#  * versionCode/Name are read FROM THE APK ITSELF (aapt), not from source —
#    a stale build can't get published under a fresh number.
#  * The APK uploads under a versioned name (sstreams-<code>.apk) and
#    version.json is swapped in LAST, atomically (upload to .tmp, mv) — a box
#    checking mid-publish sees either the old manifest or the new one, never
#    a manifest pointing at a half-uploaded APK.
#  * Signing: boxes only accept updates signed like the installed build —
#    which is this machine's debug keystore (~/.android/debug.keystore).
#    Guard that file with your life (back it up!); a rebuilt keystore means
#    every box needs a manual uninstall/reinstall.
set -euo pipefail
cd "$(dirname "$0")/.."

APK=app/build.nosync/outputs/apk/release/app-release.apk
HOST=dh_pem38x@iad1-shared-b7-44.dreamhost.com
REMOTE_DIR='savoy.click/setup/app'
BASE_URL='https://savoy.click/setup/app'

[ -f "$APK" ] || { echo "no release APK at $APK — run assembleRelease first" >&2; exit 1; }

AAPT=$(ls /opt/homebrew/share/android-commandlinetools/build-tools/*/aapt 2>/dev/null | sort -V | tail -1)
[ -n "$AAPT" ] || { echo "aapt not found under build-tools" >&2; exit 1; }

BADGING=$("$AAPT" dump badging "$APK" | head -1)
CODE=$(echo "$BADGING" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
NAME=$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
[ -n "$CODE" ] && [ -n "$NAME" ] || { echo "couldn't read version from APK" >&2; exit 1; }

echo "Publishing $NAME (versionCode $CODE, $(du -h "$APK" | cut -f1 | tr -d ' '))"

MANIFEST=$(mktemp)
trap 'rm -f "$MANIFEST"' EXIT
printf '{"versionCode":%s,"versionName":"%s","apkUrl":"%s/sstreams-%s.apk"}\n' \
    "$CODE" "$NAME" "$BASE_URL" "$CODE" > "$MANIFEST"

ssh "$HOST" "mkdir -p $REMOTE_DIR"
scp "$APK" "$HOST:$REMOTE_DIR/sstreams-$CODE.apk"
scp "$MANIFEST" "$HOST:$REMOTE_DIR/version.json.tmp"
# chmod: mktemp makes 0600 and scp preserves it — Apache needs world-read.
# sstreams-latest.apk: stable alias behind https://savoy.click/app (the
# family's Downloader/TVBro install URL) — refreshed on every publish so
# that link never goes stale. cp (not mv): the versioned APK must survive,
# version.json points at it.
ssh "$HOST" "chmod 644 $REMOTE_DIR/sstreams-$CODE.apk $REMOTE_DIR/version.json.tmp && cp $REMOTE_DIR/sstreams-$CODE.apk $REMOTE_DIR/sstreams-latest.apk && chmod 644 $REMOTE_DIR/sstreams-latest.apk && mv $REMOTE_DIR/version.json.tmp $REMOTE_DIR/version.json"

echo "Verifying…"
curl -sf "$BASE_URL/version.json" | grep -q "\"versionCode\":$CODE" \
    || { echo "version.json readback mismatch" >&2; exit 1; }
curl -sfI "$BASE_URL/sstreams-$CODE.apk" | head -1
echo "Published: boxes will offer $NAME on their next app launch."
