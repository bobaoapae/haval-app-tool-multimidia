# HAV-24 draft integration, 2026-10-09

## Scope and provenance

- Destination: existing PR152 head branch `mumu/hav-24-cluster-contract-preflight`, starting at `ed06246b8f30308ca123f209f66bb37baf06d7ba`
- Source: `netseek/fix/pr152-cluster-streams` at `a4e963ac427bc2053c8e3c54e2eb616620934c4f`, five commits ahead of that baseline
- The integration commit has the old PR head and the exact source head as parents, preserving author history. Its resulting tree deliberately excludes the items below
- Only the draft PR head is advanced. No merge into preview/master, release, signing, installation or vehicle action

## Exclusions and unchanged boundaries

1. The only accepted Service certificate remains `7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0`. The original `String` declaration and equality predicate are retained. The source's second-signer expansion is not adopted; callback/UID/package/interface checks and disabled-by-default caller handoff are unchanged.
2. `AaClusterVideoHost.kt` is byte-identical to source commit `ef2e11cdef8d40070b37ca53981afbd2389e3edd`. The final source commit's 175px shift and logo-hiding gradient Views are not adopted. Google attribution applicability/permissions and remaining clipping must still be checked; excluding these Views is not a blanket compliance finding.
3. Exact-profile fingerprints, OEM source bytes, pinned tools, Java21 full-assembly gate and loading/recovery security are unchanged. No proprietary APK is executed.

## Incorporated behavior

The integration keeps the Binder synchronous-flag correction and WARN diagnostics; new CLUSTER stream constants/configuration (1920x1080 with 360px total height margin); theme/custom map clipping and native-card handling; and navigation-demand gating. Surface ownership/quarantine and first-rendered-frame LIVE gating remain.

No bridge method signature, telemetry key or payload schema is added or renamed. The imported bridge call only refreshes the existing map window after an existing bounds update. No theme package, CarPlay path or release workflow is changed. Runtime geometry/readiness concerns below remain and prevent treating this draft as vehicle-ready.

## Known review findings

1. **P1, unresolved in resulting tree:** custom map bounds return before the native-card/warning clamp in `AaClusterVideoHost.mapBounds`. A normal full-width override can leave pixels in the protected right-hand region. No physical warning occlusion claim.
2. **P2, unresolved in resulting tree:** runtime theme dimensions update the video clip, while an AA-only session can retain the previous native-mask hole until a later visibility event.
3. **P2, unresolved in resulting tree:** every repeated inactive navigation callback restarts the five-second hide deadline, allowing indefinite continued demand.
4. **P2, source-branch finding, not introduced here:** a4e963a's separate full-height gradient Views paint outside the map rectangle and can obscure lower content where masks/theme are transparent. This source issue has not been fixed upstream; its introducing visual changes are excluded from this tree.

The first three findings are intentionally not repaired in this integration-only change. They require targeted follow-up tests and fixes before release or vehicle readiness.

## Evidence and outstanding gates

Netseek commit messages report LIVE/frame rendering and local test passes in its own vehicle setup. Those are author reports, not independent results for this integrated tree. In particular, retaining the original single signer makes this candidate different from the author's expanded-trust lab build.

Verify actual decoder output width/height/crop or SPS rather than inferring a 720px output from the advertised 1080px configuration. The host matrix assumes a full 1080px image with margins. Physical MAIN/audio/FPS coexistence, navigation pause/resume, disconnect/reconnection, native cards/warnings, theme-bound changes, MMI persistence and safe rollback remain unverified.

## Validation performed

- Passed locally: 172 unittest cases (169 source-baseline cases plus three new trust/stream regression cases), zero failures or skips. The unmodified a4 source baseline separately passed 169 cases.
- Passed: 97 standalone JVM assertions across the frame pump (25), integration core (33), lease barrier (18) and release ledger (21).
- Passed: actual source compilation against the fingerprint-pinned official Android28 API, including the Java host client. Java21 was used; caller handoff was disabled. No unsigned assembly or APK execution occurred.
- The new tests reject the unmodified two-signer source in two trust cases while retaining the stream-constant case; separate CRLF fixtures passed all three new tests.
- Not run locally: full host Kotlin/Gradle compilation, native Windows suite, any device/vehicle/OEM compositor testing. Exact-head CI must establish the first two where its jobs are available.
- Commands: `python3 -m unittest discover -s scripts/aa-patches/tests -v`; reviewed standalone JVM harness commands; `python3 scripts/aa-patches/integration/build_unsigned.py --compile-only --android-jar <verified-android28.jar> --output <fresh-output>`.

The cloud checkout is an isolated, blob-verified relevant-source snapshot. Existing older workspaces were not modified. The committed tree is based directly on the complete source Git tree, not constructed from that partial test snapshot.

No GitHub Actions result for the new integration commit was available before publication. Exact-head CI is tracked in PR152. Historical M0 validation of ed06246b does not establish the new runtime tree's vehicle behavior.

## Next step

Keep PR152 in draft and HAV-24 In Progress. Verify exact-head CI, then address the three retained review findings and obtain the necessary device/compositor evidence. Any future trust expansion or persistent loading/security change requires separate approval.
