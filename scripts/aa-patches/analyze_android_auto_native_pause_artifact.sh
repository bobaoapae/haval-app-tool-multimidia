#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/aa-patches/analyze_android_auto_native_pause_artifact.sh <artifact-dir>

Reads an artifact captured by capture_android_auto_native_pause_state.sh and
prints a compact verdict. This is a classifier for review, not proof by itself.
EOF
}

if [[ $# -ne 1 ]]; then
  usage >&2
  exit 1
fi

ARTIFACT_DIR="${1%/}"
SUMMARY="$ARTIFACT_DIR/summary.txt"

if [[ ! -d "$ARTIFACT_DIR" ]]; then
  echo "Artifact directory not found: $ARTIFACT_DIR" >&2
  exit 1
fi

if [[ ! -f "$SUMMARY" ]]; then
  echo "summary.txt not found in artifact directory: $ARTIFACT_DIR" >&2
  exit 1
fi

summary_value() {
  local key="$1"
  grep -E "^${key}=" "$SUMMARY" | tail -n 1 | sed "s/^${key}=//"
}

has_pattern() {
  local file="$1"
  local pattern="$2"
  [[ -f "$file" ]] && grep -Eiq "$pattern" "$file"
}

extract_spotify_state() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    printf 'missing\n'
    return
  fi

  awk '
    BEGIN {
      window = 0
      found = 0
    }
    /package=com\.spotify\.music/ {
      window = 80
    }
    window > 0 && /state=PlaybackState/ {
      if ($0 ~ /PAUSED\(2\)/) {
        print "PAUSED(2)"
        found = 1
        exit
      }
      if ($0 ~ /PLAYING\(3\)/) {
        print "PLAYING(3)"
        found = 1
        exit
      }
      if ($0 ~ /STOPPED\(1\)/) {
        print "STOPPED(1)"
        found = 1
        exit
      }
      if ($0 ~ /BUFFERING\(6\)/) {
        print "BUFFERING(6)"
        found = 1
        exit
      }
      print "other"
      found = 1
      exit
    }
    {
      if (window > 0) {
        window--
      }
    }
    END {
      if (!found) {
        print "unknown"
      }
    }
  ' "$file"
}

audio_signal() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    printf 'missing\n'
  elif has_pattern "$file" 'USAGE_AAUTO_MEDIA|usage=28'; then
    if has_pattern "$file" 'state:started|state=started'; then
      printf 'aa_media_started\n'
    else
      printf 'aa_media_present\n'
    fi
  else
    printf 'no_aa_audio_signal\n'
  fi
}

debug_signal() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    printf 'missing\n'
  elif has_pattern "$file" 'sent=true|dispatch.*true|AA_DASHBOARD_PLAYBACK|AAHardKeyPolicy|command=aa_'; then
    printf 'ack_or_debug_signal\n'
  else
    printf 'no_ack_signal\n'
  fi
}

log_signal() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    printf 'missing\n'
  elif has_pattern "$file" 'AndroidRuntime|FATAL|ANR'; then
    printf 'runtime_error\n'
  elif has_pattern "$file" 'sent=true|AAHardKeyPolicy|HardKeyModel|AapController|command=aa_'; then
    printf 'native_command_signal\n'
  elif has_pattern "$file" 'USAGE_AAUTO_MEDIA|AudioFocus|mIsMediaFocus'; then
    printf 'focus_or_audio_signal\n'
  else
    printf 'no_relevant_signal\n'
  fi
}

artifact_display="$ARTIFACT_DIR"
send_command="$(summary_value send_command || true)"
post_wait_seconds="$(summary_value post_wait_seconds || true)"
before_state="$(extract_spotify_state "$ARTIFACT_DIR/before-media-session.txt")"
after_state="$(extract_spotify_state "$ARTIFACT_DIR/after-media-session.txt")"
after_audio="$(audio_signal "$ARTIFACT_DIR/after-audio.txt")"
after_debug="$(debug_signal "$ARTIFACT_DIR/after-debug-state-command.txt")"
after_log="$(log_signal "$ARTIFACT_DIR/after-logcat-filtered.txt")"

central_unreachable="$(summary_value central_unreachable || true)"
adb_ready="$(summary_value adb_ready || true)"
adb_tcp_ready="$(summary_value adb_tcp_ready || true)"

verdict="needs_review"
reason="artifact needs manual review"

if [[ "$central_unreachable" == "1" || "$adb_ready" == "0" || "$adb_tcp_ready" == "0" ]]; then
  verdict="blocked_offline"
  reason="central adb/tcp preflight failed; no media command should have been dispatched"
elif [[ -z "$send_command" || "$send_command" == "capture_only" ]]; then
  verdict="capture_only"
  reason="artifact captured state without sending a media command"
elif [[ "$after_log" == "runtime_error" ]]; then
  verdict="failure_runtime"
  reason="runtime crash/anr signal found after command"
elif [[ "$after_state" == "PAUSED(2)" && "$after_audio" != "aa_media_started" ]]; then
  verdict="candidate_pause_sustained"
  reason="spotify is paused after the wait window and aa audio is not reported started"
elif [[ "$after_state" == "PLAYING(3)" ]]; then
  verdict="failure_still_playing"
  reason="spotify is still playing after the wait window"
elif [[ "$after_audio" == "aa_media_started" ]]; then
  verdict="failure_audio_still_started"
  reason="android auto audio remains started after the wait window"
elif [[ "$after_debug" == "ack_or_debug_signal" || "$after_log" == "native_command_signal" ]]; then
  verdict="failure_ack_without_pause"
  reason="command/debug signal exists, but spotify paused state was not observed"
fi

printf 'artifact=%s\n' "$artifact_display"
printf 'verdict=%s\n' "$verdict"
printf 'reason=%s\n' "$reason"
printf 'send_command=%s\n' "${send_command:-unknown}"
printf 'post_wait_seconds=%s\n' "${post_wait_seconds:-unknown}"
printf 'before_spotify_state=%s\n' "$before_state"
printf 'after_spotify_state=%s\n' "$after_state"
printf 'after_audio_signal=%s\n' "$after_audio"
printf 'after_debug_signal=%s\n' "$after_debug"
printf 'after_log_signal=%s\n' "$after_log"
