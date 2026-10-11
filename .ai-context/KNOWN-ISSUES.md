# Known issues relevant to the v9 port

These are existing target limitations, not claims of newly reproduced vehicle
failures:

- A single delayed log scan for certificate errors does not prove that the
  expected Service restarted or that AA/MAIN recovered
- Rollback does not verify unmount success, stock-code loading or session/AOA
  recovery, and the arm latch does not reset after a terminal attempt
- The author's sibling gradient Views are not bounded by the TextureView's
  clip. Their existing shift/attribution behavior remains unchanged in this port
- File checksums and offline signature/provenance results do not prove the
  installed PackageManager identity or real runtime readiness

Full Android/Compose CI, rendered UI and physical validation are separate from
the local Java/Kotlin synthetic-adapter results recorded in [HANDOFF](HANDOFF.md).
