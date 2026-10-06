#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PHONE_PROBE="$ROOT_DIR/scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh"
NATIVE_ANALYZER="$ROOT_DIR/scripts/aa-patches/analyze_android_auto_native_pause_artifact.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aa-pause-analyzer-test.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

write_phone_media_session() {
  local file="$1"
  local state="$2"
  cat >"$file" <<EOF
Sessions Stack - have 1 sessions:
  session2 br.com.example/spotify
    package=com.spotify.music
      state=PlaybackState {state=${state}, position=1000, buffered position=0, speed=1.0}
      metadata: description=Lucky Again
EOF
}

write_phone_artifact() {
  local dir="$1"
  local before="$2"
  local after="$3"
  local recheck="$4"
  mkdir -p "$dir"
  printf 'artifact=%s\n' "$dir" >"$dir/summary.txt"
  write_phone_media_session "$dir/before-media-session.txt" "$before"
  write_phone_media_session "$dir/after-media-session.txt" "$after"
  write_phone_media_session "$dir/recheck-media-session.txt" "$recheck"
}

write_native_media_session() {
  local file="$1"
  local state="$2"
  cat >"$file" <<EOF
Sessions Stack - have 1 sessions:
  session2 br.com.example/spotify
    package=com.spotify.music
      state=PlaybackState {state=${state}, position=1000, buffered position=0, speed=1.0}
      metadata: description=Lucky Again
EOF
}

expect_verdict() {
  local name="$1"
  local expected="$2"
  local output="$3"
  local actual
  actual="$(printf '%s\n' "$output" | awk -F= '$1 == "verdict" { print $2; exit }')"
  if [[ "$actual" != "$expected" ]]; then
    printf 'FAIL %s: expected verdict=%s got verdict=%s\n' "$name" "$expected" "${actual:-missing}" >&2
    printf '%s\n' "$output" >&2
    exit 1
  fi
  printf 'OK %s verdict=%s\n' "$name" "$actual"
}

phone_pass="$TMP_DIR/phone-pass"
write_phone_artifact "$phone_pass" "PLAYING(3)" "PAUSED(2)" "PAUSED(2)"
expect_verdict \
  "phone-pass" \
  "pass_pause_sustained" \
  "$("$PHONE_PROBE" --analyze-only --run-dhu --artifact-dir "$phone_pass")"

phone_setup="$TMP_DIR/phone-setup"
write_phone_artifact "$phone_setup" "PAUSED(2)" "PAUSED(2)" "PAUSED(2)"
expect_verdict \
  "phone-setup" \
  "setup_not_playing" \
  "$("$PHONE_PROBE" --analyze-only --run-dhu --artifact-dir "$phone_setup")"

phone_fail="$TMP_DIR/phone-fail"
write_phone_artifact "$phone_fail" "PLAYING(3)" "PLAYING(3)" "PLAYING(3)"
expect_verdict \
  "phone-failure" \
  "failure_still_or_resumed_playing" \
  "$("$PHONE_PROBE" --analyze-only --run-dhu --artifact-dir "$phone_fail")"

native_pass="$TMP_DIR/native-pass"
mkdir -p "$native_pass"
cat >"$native_pass/summary.txt" <<EOF
artifact=$native_pass
send_command=aa_hardkey_play_pause_d3
post_wait_seconds=25
EOF
write_native_media_session "$native_pass/after-media-session.txt" "PAUSED(2)"
printf 'no active android auto audio\n' >"$native_pass/after-audio.txt"
expect_verdict \
  "native-pass" \
  "candidate_pause_sustained" \
  "$("$NATIVE_ANALYZER" "$native_pass")"

native_ack="$TMP_DIR/native-ack"
mkdir -p "$native_ack"
cat >"$native_ack/summary.txt" <<EOF
artifact=$native_ack
send_command=aa_hardkey_play_pause_d3
post_wait_seconds=25
EOF
printf 'command=aa_hardkey_play_pause_d3 sent=true\n' >"$native_ack/after-debug-state-command.txt"
expect_verdict \
  "native-ack-without-pause" \
  "failure_ack_without_pause" \
  "$("$NATIVE_ANALYZER" "$native_ack")"

native_offline="$TMP_DIR/native-offline"
mkdir -p "$native_offline"
cat >"$native_offline/summary.txt" <<EOF
artifact=$native_offline
send_command=capture_only
post_wait_seconds=20
central_unreachable=1
No media command was dispatched.
EOF
expect_verdict \
  "native-offline" \
  "blocked_offline" \
  "$("$NATIVE_ANALYZER" "$native_offline")"
