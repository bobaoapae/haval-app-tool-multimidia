#!/bin/sh
set -eu

THEMES_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

sha256_file() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{ print $1 }'
    elif command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{ print $1 }'
    else
        fail "shasum or sha256sum is required"
    fi
}

check_hash() {
    path=$1
    expected=$2
    [ -f "$THEMES_DIR/$path" ] || fail "missing $path"
    actual=$(sha256_file "$THEMES_DIR/$path")
    [ "$actual" = "$expected" ] || fail "$path hash mismatch: $actual"
    check_contains 'SPORT_THEMES_SHA256SUMS' "$expected  $path"
}

check_contains() {
    path=$1
    token=$2
    LC_ALL=C grep -Fq "$token" "$THEMES_DIR/$path" || fail "$path is missing contract: $token"
}

validate_metadata() {
    theme=$1
    metadata="$theme/theme.xml"
    check_contains "$metadata" '<version>0.16.44</version>'
    check_contains "$metadata" '<thumbnail>thumbnail.png</thumbnail>'
    check_contains "$metadata" '<mainFile>index.html</mainFile>'
    check_contains "$metadata" '<x>0</x>'
    check_contains "$metadata" '<y>62</y>'
    check_contains "$metadata" '<width>1920</width>'
    check_contains "$metadata" '<height>596</height>'
}

validate_html_contract() {
    theme=$1
    html="$theme/index.html"

    # Android/WebView globals.
    check_contains "$html" 'window.control'
    check_contains "$html" 'window.focus'
    check_contains "$html" 'window.showScreen'
    check_contains "$html" 'window.cleanup'

    # T1/T2 display modes and M3 color palettes.
    check_contains "$html" 'Analógico V2'
    check_contains "$html" 'Digital'
    check_contains "$html" 'Mapa Graduado'
    check_contains "$html" 'Mapa Limpo'
    check_contains "$html" 'colorTheme'
    check_contains "$html" 'Red Sport'
    check_contains "$html" 'Red GT'
    check_contains "$html" 'Ocean Blue'
    check_contains "$html" 'Green'
    check_contains "$html" 'Dark Blue'
    check_contains "$html" 'Amber Gold'
    check_contains "$html" 'Purple GT'

    # M4 Now Playing.
    check_contains "$html" 'now_playing'
    check_contains "$html" 'nowPlayingTitle'
    check_contains "$html" 'nowPlayingArt'
    check_contains "$html" 'nowPlayingDurationMs'
    check_contains "$html" 'nowPlayingElapsedMs'
    check_contains "$html" 'nowPlayingPlaying'

    # M5 trip and TPMS panel.
    check_contains "$html" 'v2TripInfo'
    check_contains "$html" 'tripAvgConsumption'
    check_contains "$html" 'tripDriveTime'
    check_contains "$html" 'tripOdometer'
    check_contains "$html" 'tripAvgSpeed'
    check_contains "$html" 'tirePressures'
    check_contains "$html" 'tireTemperatures'

    # M6 speed-limit sign and M7 map speedometer visibility.
    check_contains "$html" 'speedLimit'
    check_contains "$html" 'speedLimitActive'
    check_contains "$html" 'hideSpeedometerOnMaps'
}

check_hash 'SportRed/index.html' '9bf68762d82b70338e4ccbd3150c7664da93c8dcf25202c1b13f2a988000fbba'
check_hash 'SportRed/theme.xml' '7f5e78a911346154c8b775694d73bc7d6a80342195b381645b0135ad6d04b3f1'
check_hash 'SportRed/thumbnail.png' '33b048b571cf8d6d11dc14fe328aeb3115adf0ff7b0e12f82017f29e424dd0c6'
check_hash 'SportRedLite/index.html' '9a5ced2244f589c78fba3b3cca2b1f2c4f67739d41a251b66da93418d9c8c630'
check_hash 'SportRedLite/theme.xml' 'f8a7330ad7dcf32bf37151a5c06483d8fc44208ad8981539d4e17e79526f2a7b'
check_hash 'SportRedLite/thumbnail.png' '33b048b571cf8d6d11dc14fe328aeb3115adf0ff7b0e12f82017f29e424dd0c6'

for theme in SportRed SportRedLite; do
    validate_metadata "$theme"
    validate_html_contract "$theme"
done

printf 'Sport theme payload contracts OK (SportRed and SportRedLite 0.16.44).\n'
