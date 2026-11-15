#!/bin/sh
set -eu

archive="/home/aleks/Downloads/Better Nothing Music Visualizer (translations).zip"
res_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/app/src/main/res"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT HUP INT TERM

if [ ! -f "$archive" ]; then
    printf 'Translation archive not found: %s\n' "$archive" >&2
    exit 1
fi

unzip -q "$archive" \
    'ar/strings.xml' \
    'fr/strings.xml' \
    'hi/strings.xml' \
    'hu/strings.xml' \
    'it/strings.xml' \
    'ja/strings.xml' \
    'ru/strings.xml' \
    'sk/strings.xml' \
    'es-ES/strings.xml' \
    'tr/strings.xml' \
    -d "$tmp_dir"

for locale in ar fr hi hu it ja ru sk es-ES tr; do
    if [ ! -f "$tmp_dir/$locale/strings.xml" ]; then
        printf 'Missing %s/strings.xml in translation archive\n' "$locale" >&2
        exit 1
    fi
done

for locale in ar fr hi hu it ja ru sk tr; do
    mkdir -p "$res_dir/values-$locale"
    mv -f -- "$tmp_dir/$locale/strings.xml" "$res_dir/values-$locale/strings.xml"
done

mkdir -p "$res_dir/values-es-rES"
mv -f -- "$tmp_dir/es-ES/strings.xml" "$res_dir/values-es-rES/strings.xml"
