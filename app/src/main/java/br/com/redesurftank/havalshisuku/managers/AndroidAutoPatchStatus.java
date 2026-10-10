package br.com.redesurftank.havalshisuku.managers;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only file evidence, deliberately separate from mount and CLUSTER runtime state.
 * Matching MD5 checksums are not proof of a bind mount, a loaded process, a particular
 * Service candidate, the binder handshake, or a LIVE video stream.
 */
public final class AndroidAutoPatchStatus {
    public enum Presence { ABSENT, PRESENT, UNKNOWN }
    public enum State { NOT_CHECKED, ABSENT, STAGED, CHECKSUM_DIFFER, CHECKSUM_MATCH, UNKNOWN }

    public static final String RUNTIME_NOTICE =
            "Montagem e processo não verificados. Service: versão CLUSTER e vídeo LIVE não verificados.";

    /** One exact path read. A failed checksum can still establish file presence. */
    public static final class FileEvidence {
        private static final Pattern CHECKSUM = Pattern.compile("([0-9a-fA-F]{32}) [ *](.+)");
        private final Presence presence;
        private final String md5;

        private FileEvidence(Presence presence, String md5) {
            this.presence = presence;
            this.md5 = md5;
        }

        public static FileEvidence unknown() { return new FileEvidence(Presence.UNKNOWN, null); }
        public Presence getPresence() { return presence; }

        /** Accept only our complete presence record and a checksum for the exact requested path. */
        public static FileEvidence fromShellOutput(String path, String output) {
            if (path == null || output == null) return unknown();
            String[] lines = output.replace("\r\n", "\n").trim().split("\n", -1);
            if (lines.length == 1 && "AA_PATCH_ABSENT".equals(lines[0])) {
                return new FileEvidence(Presence.ABSENT, null);
            }
            if (lines.length == 0 || !"AA_PATCH_PRESENT".equals(lines[0])) return unknown();
            if (lines.length == 2) {
                Matcher checksum = CHECKSUM.matcher(lines[1]);
                if (checksum.matches() && path.equals(checksum.group(2))) {
                    return new FileEvidence(Presence.PRESENT, checksum.group(1).toLowerCase(Locale.ROOT));
                }
            }
            return new FileEvidence(Presence.PRESENT, null);
        }
    }

    public static final class Component {
        private final Presence stagedPresence;
        private final State state;

        private Component(Presence stagedPresence, State state) {
            this.stagedPresence = stagedPresence;
            this.state = state;
        }

        public Presence getStagedPresence() { return stagedPresence; }
        public State getState() { return state; }
        public boolean hasStagedFile() { return stagedPresence == Presence.PRESENT; }
        public boolean hasMatchingChecksums() { return state == State.CHECKSUM_MATCH; }

        public static Component compare(FileEvidence staged, FileEvidence vendor) {
            Objects.requireNonNull(staged, "staged");
            Objects.requireNonNull(vendor, "vendor");
            State state;
            if (staged.presence == Presence.ABSENT) state = State.ABSENT;
            else if (staged.presence != Presence.PRESENT || staged.md5 == null) state = State.UNKNOWN;
            else if (vendor.presence == Presence.ABSENT) state = State.STAGED;
            else if (vendor.md5 == null) state = State.UNKNOWN;
            else state = staged.md5.equals(vendor.md5) ? State.CHECKSUM_MATCH : State.CHECKSUM_DIFFER;
            return new Component(staged.presence, state);
        }

        /** UI wording never upgrades file evidence to activation or readiness. */
        public String getSummary() {
            switch (state) {
                case NOT_CHECKED: return "não verificado";
                case ABSENT: return "ausente";
                case STAGED: return "preparado";
                case CHECKSUM_DIFFER: return "MD5 diverge";
                case CHECKSUM_MATCH: return "MD5 confere";
                default: return hasStagedFile()
                        ? "presente; erro" : "leitura falhou";
            }
        }
    }

    private final Component app;
    private final Component service;

    public AndroidAutoPatchStatus(Component app, Component service) {
        this.app = Objects.requireNonNull(app, "app");
        this.service = Objects.requireNonNull(service, "service");
    }

    public Component getApp() { return app; }
    public Component getService() { return service; }

    public static AndroidAutoPatchStatus notChecked() {
        return new AndroidAutoPatchStatus(
                new Component(Presence.UNKNOWN, State.NOT_CHECKED),
                new Component(Presence.UNKNOWN, State.NOT_CHECKED));
    }

    /** Immutable UI poll generation. An older in-flight read cannot undo an action/reset. */
    public static final class PollState {
        private final long generation;
        private final AndroidAutoPatchStatus snapshot;

        private PollState(long generation, AndroidAutoPatchStatus snapshot) {
            this.generation = generation;
            this.snapshot = snapshot;
        }

        public static PollState initial() { return new PollState(0, notChecked()); }
        public long getGeneration() { return generation; }
        public AndroidAutoPatchStatus getSnapshot() { return snapshot; }
        public PollState reset() { return new PollState(generation + 1, notChecked()); }
        public PollState accept(long expectedGeneration, AndroidAutoPatchStatus result) {
            return expectedGeneration == generation
                    ? new PollState(generation, Objects.requireNonNull(result, "result")) : this;
        }
    }
}
