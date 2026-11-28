#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$IOS_DIR/.." && pwd)"

ANDROID_QURAN_DIR="$REPO_ROOT/app/src/main/assets/quran"
ANDROID_FONTS_DIR="$REPO_ROOT/app/src/main/res/font"
ANDROID_RAW_DIR="$REPO_ROOT/app/src/main/res/raw"

OUT_DIR="$IOS_DIR/GeneratedResources"
OUT_QURAN_DIR="$OUT_DIR/quran"
OUT_PAGES_DIR="$OUT_QURAN_DIR/pages"
OUT_FONTS_DIR="$OUT_DIR/Fonts"
OUT_SOUNDS_DIR="$OUT_DIR/Sounds"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Preparing iOS shared resources in: $OUT_DIR"

mkdir -p "$OUT_PAGES_DIR" "$OUT_FONTS_DIR" "$OUT_SOUNDS_DIR"

# 1. Copy ayahinfo.db & 604 Madinah Mushaf WebP pages
if [ ! -f "$ANDROID_QURAN_DIR/ayahinfo.db" ]; then
  echo "ERROR: Missing $ANDROID_QURAN_DIR/ayahinfo.db" >&2
  exit 1
fi
cp -f "$ANDROID_QURAN_DIR/ayahinfo.db" "$OUT_QURAN_DIR/ayahinfo.db"

if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete "$ANDROID_QURAN_DIR/pages/" "$OUT_PAGES_DIR/"
else
  rm -rf "$OUT_PAGES_DIR"/*.webp
  cp -f "$ANDROID_QURAN_DIR/pages/"*.webp "$OUT_PAGES_DIR/"
fi

# 2. Copy bundled offline Cairo TTF fonts
for font_file in cairo_regular.ttf cairo_medium.ttf cairo_semibold.ttf cairo_bold.ttf; do
  if [ ! -f "$ANDROID_FONTS_DIR/$font_file" ]; then
    echo "ERROR: Missing font $ANDROID_FONTS_DIR/$font_file" >&2
    exit 1
  fi
  cp -f "$ANDROID_FONTS_DIR/$font_file" "$OUT_FONTS_DIR/$font_file"
done

# 3. Copy & convert alarm sounds (.m4a for AVAudioPlayer + <=28s .caf IMA4 for UNNotificationSound)
for m4a_name in adhan_fajr eid_takbeer quran_alert; do
  if [ ! -f "$ANDROID_RAW_DIR/${m4a_name}.m4a" ]; then
    echo "ERROR: Missing raw audio $ANDROID_RAW_DIR/${m4a_name}.m4a" >&2
    exit 1
  fi
  cp -f "$ANDROID_RAW_DIR/${m4a_name}.m4a" "$OUT_SOUNDS_DIR/${m4a_name}.m4a"
done

if [ ! -f "$ANDROID_RAW_DIR/calm_tone.webm" ]; then
  echo "ERROR: Missing raw audio $ANDROID_RAW_DIR/calm_tone.webm" >&2
  exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ERROR: ffmpeg is required to convert calm_tone.webm and build iOS notification tones" >&2
  exit 1
fi

ffmpeg -y -loglevel error -i "$ANDROID_RAW_DIR/calm_tone.webm" -vn -c:a aac -b:a 128k "$OUT_SOUNDS_DIR/calm_tone.m4a"

for tone_name in adhan_fajr eid_takbeer quran_alert calm_tone; do
  src_m4a="$OUT_SOUNDS_DIR/${tone_name}.m4a"
  tmp_wav="$TMP_DIR/${tone_name}_28s.wav"
  out_caf="$OUT_SOUNDS_DIR/${tone_name}_30s.caf"

  ffmpeg -y -loglevel error -i "$src_m4a" -t 28 -vn -ac 1 -ar 22050 -c:a pcm_s16le "$tmp_wav"
  if [ -x /usr/bin/afconvert ]; then
    /usr/bin/afconvert -f caff -d ima4 "$tmp_wav" "$out_caf"
  else
    ffmpeg -y -loglevel error -i "$tmp_wav" -c:a adpcm_ima_qt "$out_caf"
  fi
done

page_count=$(find "$OUT_PAGES_DIR" -maxdepth 1 -name 'page_*.webp' | wc -l | tr -d ' ')
if [ "$page_count" -ne 604 ]; then
  echo "ERROR: Expected 604 Mushaf WebP pages, found $page_count" >&2
  exit 1
fi

echo "PASS: Prepared ayahinfo.db, $page_count WebP pages, 4 Cairo fonts, 4 .m4a and 4 .caf tones."
