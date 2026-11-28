#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <path-to-QuranAlarm.app> <output-ipa-path>" >&2
  exit 1
fi

APP_PATH="$1"
OUT_IPA="$2"

if [ ! -d "$APP_PATH" ]; then
  echo "ERROR: .app bundle not found at $APP_PATH" >&2
  exit 1
fi

OUT_DIR="$(dirname "$OUT_IPA")"
mkdir -p "$OUT_DIR"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/Payload"
cp -R "$APP_PATH" "$TMP_DIR/Payload/"

ABS_OUT_IPA="$(cd "$OUT_DIR" && pwd)/$(basename "$OUT_IPA")"
rm -f "$ABS_OUT_IPA"
(cd "$TMP_DIR" && zip -qry "$ABS_OUT_IPA" Payload)

echo "Packaged unsigned IPA at: $ABS_OUT_IPA"
