# Independent CLUSTER frame-pump research prototype

**Not an installable feature or Service patch.** Nothing in this directory is
loaded by the Android app, bundled into an APK, or registered with GAL. The
existing patch script still refuses mutation and reports CLUSTER/deploy false.

This is the tested Java ownership/queue boundary for a future independent
CLUSTER decoder. It does not provide a MediaCodec adapter, codec-config/IDR
recovery, Binder/Surface handoff, input registration, focus controller or proof
that Maps/Waze sends a second stream. It never calls MAIN's renderer.

## Why this boundary is now supported by evidence

The supplied current Service APK is byte-identical to vendor `48ff` already
inspected; current-installed identity is Rafael's report, not a device pull by
this tool. Marcel subsequently supplied both native ABIs. See
[`../DUMPS_V2_6_CLUSTER.md`](../DUMPS_V2_6_CLUSTER.md) for hashes and static proof.

The actual JNI callback copies compressed bytes into an array-backed Java
`BufferPool` buffer before calling Java. It can therefore be retained for
worker-thread consumption without another GAL-thread payload copy. Returning
that buffer and acknowledging a frame are separate operations. ACK targets the
specific VideoSink and session, so MAIN's listener must not be reused.

## Implemented and executable on a JVM

`ClusterFramePump.java` owns one immutable connection/Surface-generation token,
one independent decoder and one daemon worker with no polling loop.

- Admission limits count **queued plus in-flight frames** and full retained
  backing-array bytes, including slices. Position/limit changes before submission
  cannot evade the byte budget. Shared arrays are conservatively charged per frame
- The first `offer` transfers ownership, even on rejection. Never modify/reuse
  its buffer afterward. Duplicate submission of the same Frame is rejected
  without a second ACK or return. Buffers must expose an accessible backing
  array, matching the JNI callback's array access. Buffers without one need a
  separately verified adapter. This does not infer the Android buffer's
  `isDirect()` value from its behavior on a desktop JVM
- Decoder consumption and decoder close occur on the worker. `consume` must
  synchronously finish using/copying input before returning; it must not retain
  the buffer. This is not a claim that the frame rendered on a display
- An accepted frame is consumed or deliberately discarded, its pool-return is
  attempted, and admission capacity is released **before ACK credit is sent**.
  Unlike the OEM player's early ACK, normal ACK-triggered replenishment can fit
  into the released slot. This is an offline invariant, not measured phone-side
  flow control or a validated new wire protocol
- Count/byte overflow terminates the generation and drains ownership. It does
  not silently discard arbitrary H.264 frames and continue with dependent frames
- `close` invalidates queued work. At most the already selected in-flight call
  finishes; termination waits for it and decoder close. There is no restart of
  this pump. Re-enable/reconnect/Surface replacement needs a new generation,
  fresh decoder and a verified codec-config/keyframe restart strategy
- ACK and pool-return are each attempted exactly once, including error paths;
  a thrown callback is never retried because it might already have acted
- Each frame captures its original `FrameOwner` lease. Stale frames are not ACKed
  through a new connection's endpoint. The adapter must retain that endpoint
  until the frame's recycle/ACK disposal is complete, including late/rejected
  offers; a terminated pump cannot stop future upstream callbacks by itself
- Rejected-frame recycle/ACK run inline and must be short. Data copying and
  decoder work never run there. External callbacks execute outside the queue lock

If a callback throws, accepted queued frames become disposal-only, the decoder
closes once, and termination reports the failure. A stale foreign-generation
frame's disposal error is on that frame's future and does not stop the current
generation. A rejected offer after pump termination also reports disposal errors
on its own future; it cannot retroactively change a completed termination future.

### Important lifecycle limits

A buffer lease, not the pump's mere existence or mount hash, protects a real
native sink's lifetime. The real adapter is still missing. Stop/unregister the
producer safely and settle all accepted/rejected frame leases before destroying
the sink. Do not release/reuse the decoder's Surface until its termination has
settled. Callback operations must not wait on that same termination from the
worker, or they would deadlock. JVM/process-fatal failures are outside this model.

The prototype handles **data frames only**. OEM codec-config handling synthesizes
session-zero ACKs; that rule has not been carried into this prototype by guess.
The future adapter must map setup/config/data/teardown separately and validate
the chosen data-ACK timing and resume/keyframe behavior on the phone.

## Demand-driven integration plan, not implemented here

1. Register the separate CLUSTER VideoSink and paired InputSource before service
   discovery/session start, checking IDs against the actual registration set
2. Use the verified `autoStartProjection=false` constructor flag. Native setup
   then suppresses automatic unsolicited PROJECTED
3. Keep requested visibility as pending demand until real setup/channel activity.
   Native focus requests made before channel-open are discarded, not queued
4. Request PROJECTED/NATIVE on the CLUSTER sink only. The Java local focus callback
   is emitted before the native send and is not a phone ACK or video readiness
5. Wire a separate MediaCodec adapter and authenticated Surface transport with
   connection/Surface generations and explicit lease ownership. Do not reuse
   `AapVideoManager`, whose existing ACK route targets MAIN
6. Verify actual output before exposing the D3 mask; neither a mounted APK nor
   a Surface alone establishes a working second stream
7. With authorized parked-car testing, measure phone pause/resume, fresh codec
   config/keyframes, independent decoder capacity, repeated on/off/reconnect and
   Surface recreation, MAIN/audio/TBT preservation, Maps/Waze and two settled
   MAIN/CLUSTER FPS runs

No native address is used by this code. Addresses in research evidence identify
where a fact was observed, not patch offsets. No vehicle install, signing,
release, mounting, force-stop, Frida or socket frame transport is included.

## Tests

From the repository root:

    python3 -m unittest discover -s scripts/aa-patches/tests -v

A JDK with a compiler is required. The Python harness compiles the **actual**
Java prototype and executes 24 deterministic/latch-driven checks. It fails if
Java/compiler is absent, rather than reporting a skipped pass. Compilation uses
Java 8 source/target syntax on the available JDK; it is not an Android API/DEX
compatibility check. Intermediates use a temporary directory.

Coverage includes both bounds, oversized input, buffer-window mutation/slices,
ACK-credit replenishment, zero-copy identity, worker affinity, in-flight close,
queued cancellation, stale connection/Surface generations, reused session IDs,
duplicate frames, late arrivals, ACK/decode/recycle/close exceptions, reentrant
completion/cleanup callbacks and concurrent producers. The original 18 preflight
regressions remain unchanged. No OEM bytecode or native library is executed.
