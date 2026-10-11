package br.com.redesurftank.havalshisuku.managers;

import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchStatus.Component;
import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchStatus.FileEvidence;
import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchStatus.PollState;
import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchStatus.Presence;
import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchStatus.State;
import java.util.Locale;

/** Executable checks against the production model; synthetic checksums, no APK/Android runtime. */
public final class AaPatchStatusHarness {
    private static final String PATCH = "/data/local/tmp/aa_patches/AndroidAutoService.apk";
    private static final String VENDOR = "/vendor/app/AndroidAutoService/AndroidAutoService.apk";
    private static final String HASH_A = "0123456789abcdef0123456789abcdef";
    private static final String HASH_B = "fedcba9876543210fedcba9876543210";
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static FileEvidence file(String path, String hash) {
        return FileEvidence.fromShellOutput(path, "AA_PATCH_PRESENT\n" + hash + "  " + path + "\n");
    }

    private static FileEvidence absent() {
        return FileEvidence.fromShellOutput(PATCH, "AA_PATCH_ABSENT\n");
    }

    private static Component match() {
        return Component.compare(file(PATCH, HASH_A), file(VENDOR, HASH_A));
    }

    private static void state(Component actual, State expected, boolean staged, String label) {
        check(actual.getState() == expected, label + ": state");
        check(actual.hasStagedFile() == staged, label + ": presence");
        check(actual.hasMatchingChecksums() == (expected == State.CHECKSUM_MATCH), label + ": checksum evidence");
        String summary = actual.getSummary().toLowerCase(Locale.ROOT);
        for (String claim : new String[] {"ativo", "montado", "live", "pronto", "cluster"}) {
            check(!summary.contains(claim), label + ": no runtime/candidate claim " + claim);
        }
    }

    public static void main(String[] args) {
        PollState initial = PollState.initial();
        state(initial.getSnapshot().getApp(), State.NOT_CHECKED, false, "initial App");
        state(initial.getSnapshot().getService(), State.NOT_CHECKED, false, "initial Service");
        check(initial.getGeneration() == 0, "initial generation");
        check(initial.getSnapshot().getApp().getStagedPresence() == Presence.UNKNOWN, "initial not absent");

        Component missing = Component.compare(absent(), FileEvidence.unknown());
        state(missing, State.ABSENT, false, "absent patch");
        state(Component.compare(absent(), file(VENDOR, HASH_A)), State.ABSENT, false, "absent beats stale vendor");
        Component staged = Component.compare(file(PATCH, HASH_A), absent());
        state(staged, State.STAGED, true, "installed only");
        Component different = Component.compare(file(PATCH, HASH_A), file(VENDOR, HASH_B));
        state(different, State.CHECKSUM_DIFFER, true, "different files");
        state(match(), State.CHECKSUM_MATCH, true, "matching files");

        for (Component service : new Component[] {missing, staged, different, match()}) {
            AndroidAutoPatchStatus snapshot = new AndroidAutoPatchStatus(match(), service);
            state(snapshot.getApp(), State.CHECKSUM_MATCH, true, "App unaffected by Service " + service.getState());
            check(snapshot.getService() == service, "independent Service evidence");
        }

        for (String error : new String[] {null, "", "Permission denied", "Shizuku unavailable",
                "AA_PATCH_UNKNOWN\n", "yes", "AA_PATCH_ABSENT\nerror", "error\nAA_PATCH_ABSENT"}) {
            FileEvidence unreadable = FileEvidence.fromShellOutput(PATCH, error);
            check(unreadable.getPresence() == Presence.UNKNOWN, "unknown read: " + error);
            state(Component.compare(unreadable, unreadable), State.UNKNOWN, false, "identical errors");
            state(Component.compare(file(PATCH, HASH_A), unreadable), State.UNKNOWN, true, "vendor read error");
        }

        for (String malformed : new String[] {"", "Permission denied", HASH_A, HASH_A + "  /wrong/path",
                HASH_A.substring(1) + "  " + PATCH, HASH_A + "0  " + PATCH,
                "z" + HASH_A.substring(1) + "  " + PATCH, HASH_A + "  " + PATCH + "\nwarning",
                HASH_A + "  " + PATCH + "\n" + HASH_A + "  " + PATCH,
                HASH_A + "  " + PATCH + ".old", HASH_A + " " + PATCH}) {
            FileEvidence unreadable = FileEvidence.fromShellOutput(PATCH, "AA_PATCH_PRESENT\n" + malformed);
            check(unreadable.getPresence() == Presence.PRESENT, "failed hash preserves presence");
            state(Component.compare(unreadable, unreadable), State.UNKNOWN, true, "malformed equal output");
            state(Component.compare(unreadable, file(VENDOR, HASH_A)), State.UNKNOWN, true, "malformed staged");
        }
        state(Component.compare(file(PATCH, HASH_A.toUpperCase(Locale.ROOT)), file(VENDOR, HASH_A)),
                State.CHECKSUM_MATCH, true, "checksum hex case");
        FileEvidence binaryCrLf = FileEvidence.fromShellOutput(PATCH,
                "AA_PATCH_PRESENT\r\n" + HASH_A + " *" + PATCH + "\r\n");
        state(Component.compare(binaryCrLf, file(VENDOR, HASH_A)), State.CHECKSUM_MATCH, true, "binary marker and CRLF");
        state(Component.compare(FileEvidence.fromShellOutput(null, HASH_A), file(VENDOR, HASH_A)),
                State.UNKNOWN, false, "missing requested path");

        AndroidAutoPatchStatus bothMatch = new AndroidAutoPatchStatus(match(), match());
        PollState loaded = initial.accept(0, bothMatch);
        check(loaded.getSnapshot() == bothMatch, "accept current read");
        PollState reset = loaded.reset();
        check(reset.getGeneration() == 1, "reset changes generation");
        state(reset.getSnapshot().getApp(), State.NOT_CHECKED, false, "reset clears App");
        state(reset.getSnapshot().getService(), State.NOT_CHECKED, false, "reset clears Service");
        check(reset.accept(0, bothMatch) == reset, "stale success cannot undo reset");
        AndroidAutoPatchStatus removed = new AndroidAutoPatchStatus(missing, missing);
        PollState afterRemoval = reset.accept(1, removed);
        check(afterRemoval.getSnapshot() == removed, "post-removal files replace old success");
        PollState repeat = afterRemoval.accept(1, removed);
        state(repeat.getSnapshot().getService(), State.ABSENT, false, "repeated read stable");
        PollState resetAgain = repeat.reset().reset();
        check(resetAgain.getGeneration() == 3, "repeated actions advance generation");
        check(resetAgain.accept(1, bothMatch) == resetAgain, "older action completion ignored");
        check(resetAgain.accept(2, bothMatch) == resetAgain, "previous reset completion ignored");
        Component failed = Component.compare(FileEvidence.unknown(), FileEvidence.unknown());
        AndroidAutoPatchStatus partial = new AndroidAutoPatchStatus(match(), failed);
        PollState afterPartial = resetAgain.accept(3, partial);
        state(afterPartial.getSnapshot().getService(), State.UNKNOWN, false, "partial/error not stale match");
        AndroidAutoPatchStatus recovered = afterPartial.accept(3, bothMatch).getSnapshot();
        state(recovered.getService(), State.CHECKSUM_MATCH, true, "recovered checksum only");
        state(afterPartial.accept(3, partial).getSnapshot().getService(), State.UNKNOWN, false, "error repeated");

        // Even an old bundled Service with matching file hashes is never identified as the
        // new candidate, mounted, process-loaded, binder-compatible, or producing LIVE frames.
        check(match().getSummary().equals("MD5 confere"), "old Service has only checksum wording");
        check(AndroidAutoPatchStatus.RUNTIME_NOTICE.contains("Montagem e processo não verificados"), "mount/process disclaimer");
        check(AndroidAutoPatchStatus.RUNTIME_NOTICE.contains("versão CLUSTER e vídeo LIVE não verificados"), "candidate/LIVE disclaimer");
        System.out.println("PASS total=" + checks + " patch status checks");
    }
}
