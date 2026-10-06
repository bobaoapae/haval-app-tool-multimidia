# Exact-profile Service integration hooks

This directory contains source-level integration tooling, **not a signed or
installable APK**. The original read-only preflight remains unchanged. This tool
never signs, installs, mounts, restarts a service, changes permissions, or reads
credentials. Hook generation always reports `deployment_ready=false`.

## Inputs and atomic output

Supported input is the supplied Service APK SHA-256
`a9cfb4c0e559f9638466a80d77a861ffd4b5737d5ac9f71e0c20370f048ae1d8`, decoded using
verified apktool 3.0.2 with resources left raw and debug metadata preserved:

    java -jar tools/apktool_3.0.2.jar d -r -j 2 \
      -p scripts/.build/cluster-framework \
      -o scripts/.build/cluster-stock <supplied-apk>
    python3 scripts/aa-patches/integration/patch_service_hooks.py \
      scripts/.build/cluster-stock --source-apk <supplied-apk> --check
    python3 scripts/aa-patches/integration/patch_service_hooks.py \
      scripts/.build/cluster-stock --source-apk <supplied-apk> \
      --output scripts/.build/cluster-hooked

Output must be a new directory, outside the input tree. The tool pins all 4,900
smali files as one inventory digest and separately pins six relevant class-file
hashes. It also checks actual class, field, method and instruction declarations.
Missing/duplicate classes or methods, changed registration IDs, changed Binder
control flow, transaction 55 collision, and already-patched inputs are refused.
The checked tree is copied to sibling staging, checked again, patched there, and
published by a single directory rename. Validation and intermediate-write
failures do not publish a partial output. Original inputs remain untouched.

Apply these hooks **before adding helper smali**. Exactly three classes change:
GalIntegration, GalReceiver, and AndroidAutoService$LinkCommandBinder. The base
LinkCommand$Stub and all existing transaction bodies are byte-for-byte unchanged.
This is a smali-source check, not full APK signature/DEX/Android/runtime validation.
The pinned APK hash does not turn arbitrary external resources into trusted data;
the main build pipeline must preserve and verify untouched ZIP resources and
manifest, add only reviewed helper classes, assemble, and verify the final DEX.

The Python `_profile` argument is solely an in-process synthetic-test seam. There
is no command-line option to replace the production profile.

## Required Java helper descriptors

The helper classes are supplied by the separate integration implementation.
They are not generated, copied, compiled or verified by this hook tool.

- `Lcom/ts/androidauto/impulse/cluster/ClusterIntegration;->register(Lcom/google/android/projection/protocol/GalReceiver;I)V`
- `Lcom/ts/androidauto/impulse/cluster/ClusterIntegration;->retire(Lcom/google/android/projection/protocol/GalReceiver;)V`
- `Lcom/ts/androidauto/impulse/cluster/ClusterIntegration;->registerPair(Landroid/util/SparseArray;JZLcom/google/android/projection/protocol/CarServiceProvider;Lcom/google/android/projection/protocol/CarServiceProvider;)Z`
- `Lcom/ts/androidauto/impulse/cluster/ClusterBinder;->dispatch(Landroid/content/Context;Landroid/os/Parcel;Landroid/os/Parcel;I)Z`

`register(receiver, viewingDistance)` is inserted at the sole normal return of the verified
GalIntegration.registerCarService() method, guarded against a null receiver.
It reads the existing private GalIntegration.mViewingDistance field from that
same hooked class and passes it unchanged. This preserves configured vehicle
viewing-distance metadata rather than inventing the VideoSink constructor int.
This happens after the OEM registration list and before GalIntegration.start.
It must isolate recoverable CLUSTER failures and preserve MAIN startup.

`retire(receiver)` runs immediately before GalIntegration.destroy delegates to
GalReceiver.destroy. It must synchronously prevent further native ACK/focus
access from that generation, without deadlocking the GAL callback or decoder
worker, before returning. Decoder/Surface cleanup may finish asynchronously only
when it cannot touch the retired/freed endpoint. It must not throw a recoverable
exception that skips OEM destruction. A mere queued cleanup task is insufficient:
OEM destroy first destroys every provider, then the receiver. OEM stop is not a
thread-join/quiescence barrier. Ordinary CLUSTER hide never stops the shared GAL.

## Atomic pair registration contract

The added method is:

`GalReceiver.registerImpulseClusterPair(CarServiceProvider video, CarServiceProvider input):boolean`

It holds the receiver's existing monitor across the entire helper call, with a
catch-all monitor release. It passes the original mRegisteredServices map, native
receiver pointer, mStopping flag and both providers to registerPair. This shares
the lock used by original register/start/destroy. No reflection is used.

The helper must enforce, before any creation or map mutation:

1. Receiver is not stopping and its pointer is nonzero
2. IDs 21 and 22 are both absent, including an existing key with a null value
3. Providers are nonnull, distinct, fresh objects with native pointer zero
4. No existing service object is removed, destroyed or replaced

Create and fully configure video 21, then input 22, checking both results. Put
both into the map only after both succeed. Roll back only these own fresh
providers on a failed create/configuration, exception, or map insertion. Remove
a map entry only when its object is identical to the helper's own provider.
Do not call nativeRegister: the unchanged startCarServices registers them later.
The helper must not publish a partially configured pair to service discovery.

Configuration belongs inside the helper providers' create overrides (or the same
locked transaction) before insertion: native setters require created instances.
A post-registration configuration failure has no public OEM unregister method.

Both VideoSink and InputSource, and their create/destroy/getNativeInstance
methods, are public non-final in this profile. Native shutdown checks a zero
pointer, deletes only a nonzero own instance and then clears the pointer. This
supports rollback after attempted creation of a **fresh** provider; it does not
authorize destroying a previously initialized/existing service. Arbitrary fatal
native/JVM allocation failures are not proven recoverable by a Java try/catch.

VideoSink's constructor is `(ProjectionListener, boolean autoStartProjection,
int viewingDistance)`. Its third parameter is not DPI or display ID. A CLUSTER
implementation must keep its sink/listener/decoder separate from MAIN and use
the separately verified display/configuration APIs.

## Binder extension contract

Transaction 55 is newly assigned by this implementation; it is not an existing
OEM API. The exact original Stub handles 1–54 and INTERFACE_TRANSACTION. The new
concrete Binder override handles only 55; every other code invokes the original
super implementation with the original arguments and return value.

Context comes from the concrete Binder's existing this$0 outer Service field,
not reflection, an invented singleton, or an implicit application lookup.

`dispatch(context,data,reply,flags)` must authenticate the original Binder caller
UID against the current installed UID of the exact Impulse package
`br.com.redesurftank.havalshisuku` **before** Surface unmarshalling, state mutation
or asynchronous dispatch. Do not clear Binder identity first. Fail closed on
missing package, UID mismatch, unsupported flags, invalid version/token/payload
or invalid Surface. The hook itself does not implement this authentication.
The helper must use a bounded, explicit parcel contract and dispose rejected
Surface handles. No new exported component, manifest permission, security grant
or change to existing OEM transaction authorization is introduced by these hooks.

## Codec-config and packaging limits

Codec config is separate from data frames. JNI copies a new byte[] and calls
codecConfigCallback([B)I; the supplied Java returns zero. The native route returns
success without automatic media ACK. Existing OEM VideoPlayer synthesizes
ackFrames(0,1) for config; native ACK code sends that session zero unchanged.
Whether the phone requires or ignores that config credit is not established by
receiver-only static inspection. Do not label the Java return value a wire ACK,
or substitute a current data session ID without evidence.

The original Service uses android.uid.system and a BeanTechs Platform1 public
signing certificate. Rebuilding changes signed contents. An unsigned or
arbitrarily re-signed candidate is not a signature-compatible PackageManager
update. Preserve package/shared UID, components, AAP authentication material and
native ABI dependencies. Signing and an authorized loading/vehicle test path
are separate gates. No signing key, security bypass or deployment is supplied.
