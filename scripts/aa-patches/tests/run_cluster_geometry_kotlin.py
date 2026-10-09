#!/usr/bin/env python3
"""Extra host-only behavioral evidence using an already installed Kotlin compiler.

The portable suite always runs the Java production policy. This optional harness
executes verbatim production Kotlin methods with synthetic Android/thread/render
adapters. It is not device rendering, full Android compilation or OEM execution.
Requires --compiler-dir containing official kotlin-compiler-embeddable and stdlib
jars; downloads nothing. All generated sources/classes go under scripts/.build.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

from test_cluster_geometry import ROOT, JAVA, HOST, PROJECTOR, BRIDGE, method


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=120)
    print(result.stdout, end="")
    print(result.stderr, end="")
    if result.returncode:
        raise SystemExit(result.returncode)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiler-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "scripts/.build/geometry-kotlin")
    parser.add_argument("--regression", choices=["legacy-custom-bypass", "legacy-theme-refresh"])
    args = parser.parse_args()
    deps = args.compiler_dir.resolve()
    output = args.output.resolve()
    output.relative_to((ROOT / "scripts/.build").resolve())
    output.mkdir(parents=True, exist_ok=True)
    java = shutil.which("java")
    if java is None:
        raise SystemExit("JDK required")
    jars = sorted(deps.glob("*.jar"))
    stdlibs = list(deps.glob("kotlin-stdlib-*.jar"))
    if len(stdlibs) != 1 or not list(deps.glob("kotlin-compiler-embeddable-*.jar")):
        raise SystemExit("Expected an official Kotlin embeddable compiler and one stdlib jar")
    (output / "input-manifest.json").write_text(json.dumps({
        "regression_mutant": args.regression,
        "production": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                       for path in (HOST, PROJECTOR, BRIDGE, JAVA / "projectors/AaClusterGeometry.java")},
        "compiler_jars": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in jars},
    }, indent=2) + "\n", encoding="utf-8")
    host = HOST.read_text(encoding="utf-8")
    projector = PROJECTOR.read_text(encoding="utf-8")
    bridge = BRIDGE.read_text(encoding="utf-8")
    host_methods = "\n".join(method(host, signature) for signature in (
        "fun refreshWindow(", "fun setNativeCardShown(", "fun mapBounds()",
        "internal fun parseBounds(", "private fun applyStreamTransform("))
    projector_methods = "\n".join(method(projector, signature) for signature in (
        "private fun syncAaClusterMapWindow(", "private fun updateAaClusterAppHole(",
        "override fun refreshDisplayBounds()", "fun prepareDisplay3AppHole("))
    # Execute the exact production hole selection/update block without unrelated
    # Shizuku queries and WebView overlay bookkeeping that surround it.
    visibility = method(projector, "private fun updateVirtualClusterVisibility(")
    hole_selection = visibility[visibility.index("        // AA/CarPlay are intentionally excluded"):
                                visibility.index("        val appInDashValue =")]
    if args.regression == "legacy-custom-bypass":
        host_methods = host_methods.replace("return AaClusterGeometry.resolve(custom, theme, nativeCardShown)",
            "parseBounds(custom)?.let { return it }\nreturn AaClusterGeometry.resolve(custom, theme, nativeCardShown)")
    if args.regression == "legacy-theme-refresh":
        projector_methods = projector_methods.replace(
            "AaClusterVideoHost.refreshWindow { updateAaClusterAppHole(it) }",
            "AaClusterVideoHost.refreshWindow()")

    sources = {
        "Android.kt": '''package android.graphics
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    constructor(other: Rect): this(other.left, other.top, other.right, other.bottom)
}
class Matrix {
    var values = floatArrayOf()
    fun setScale(x: Float, y: Float, px: Float, py: Float) { values = floatArrayOf(x, y, px, py) }
}
''',
        "Looper.kt": '''package android.os
object Looper {
    var isMain = true
    private val main = Any()
    private val worker = Any()
    fun getMainLooper() = main
    fun myLooper() = if (isMain) main else worker
}
class Handler {
    val queue = java.util.ArrayDeque<Runnable>()
    fun post(work: Runnable) { queue.addLast(work) }
    fun drain() {
        check(Looper.isMain)
        var count = 0
        while (!queue.isEmpty()) {
            check(++count < 100) { "feedback loop" }
            queue.removeFirst().run()
        }
    }
}
''',
        "Context.kt": '''package android.content
class Context {
    companion object { const val MODE_PRIVATE = 0 }
    fun getSharedPreferences(name: String, mode: Int) = Preferences()
}
class Preferences {
    fun getString(key: String, fallback: String?): String? = br.com.redesurftank.App.custom
}
''',
        "App.kt": '''package br.com.redesurftank
object App {
    var custom: String? = null
    fun getDeviceProtectedContext() = android.content.Context()
}
''',
        "Launcher.kt": '''package br.com.redesurftank.havalshisuku.managers
object DisplayAppLauncher {
    var dynamicThemeBounds: IntArray? = null
    fun themeClusterAppBounds() = dynamicThemeBounds
    fun getDisplayResolution(display: Int) = 1920 to 720
}
''',
        "Runtime.kt": '''package br.com.redesurftank.havalshisuku.projectors
import android.os.Looper
import android.graphics.Rect
import android.graphics.Matrix
object Log { fun w(tag: String, message: String) {} }
open class View {
    var width = 1920
    var height = 720
    var parent: Any? = null
}
class TextureView: View() {
    var clipBounds: Rect? = null
        set(value) { check(Looper.isMain); field = value }
    var transforms = 0
    var matrix: Matrix? = null
    fun setTransform(value: Matrix) { check(Looper.isMain); transforms++; matrix = value }
}
object AaClusterProtocol { const val STREAM_WIDTH = 1920; const val STREAM_HEIGHT = 1080 }
object SharedPreferencesKeys { object AA_CLUSTER_MAP_CUSTOM_BOUNDS { const val key = "custom" } }
object ClusterCardIds { const val NATIVE_CARD = 1 }
interface IBridgeContext { fun refreshDisplayBounds() }
''',
        "Host.kt": '''package br.com.redesurftank.havalshisuku.projectors
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.Matrix
import android.graphics.Rect
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
object AaClusterVideoHost {
    private const val TAG = "test"
    private const val PANEL_WIDTH = 1920
    private const val PANEL_HEIGHT = 720
    val DEFAULT_MAP_BOUNDS = intArrayOf(0, 62, 1920, 658)
    private var nativeCardShown = false
    val mainHandler = Handler()
    var textureView: TextureView? = TextureView().also { it.parent = View() }
''' + host_methods + "\n}\n",
        "Projector.kt": '''package br.com.redesurftank.havalshisuku.projectors
import android.os.Looper
class Projector: IBridgeContext {
    private val TAG = "test"
    var display3AppRect: android.graphics.Rect? = null
    var display3AppRectIsAaCluster = true
    var attached = true
    var isWarningDismissed = false
    var isWarningActive = false
    var currentCard = 0
    var maskUpdates = 0
    var managed = setOf<Int>()
    var externalHole: android.graphics.Rect? = null
    var projectionHole: android.graphics.Rect? = null
    val syncs = mutableListOf<Int>()
    var maskObserver: (() -> Unit)? = null
    fun isAaClusterInDash() = attached
    fun ensureUi(work: () -> Unit) {
        if (Looper.isMain) work() else AaClusterVideoHost.mainHandler.post(Runnable { work() })
    }
    fun updateNativeMaskViews() {
        check(Looper.isMain)
        if (display3AppRectIsAaCluster) {
            check(display3AppRect == AaClusterVideoHost.textureView!!.clipBounds) { "clip/hole mismatch" }
        }
        maskUpdates++
        maskObserver?.invoke()
    }
    fun hasManagedSecondaryDisplayWork(displayId: Int) = displayId in managed
    fun syncSecondaryDisplayApps(displayId: Int) { check(Looper.isMain); syncs.add(displayId) }
    fun changeCard(card: Int) { currentCard = card; syncAaClusterMapWindow() }
    fun changeCardWithMandatoryMask(card: Int) {
        currentCard = card
        syncAaClusterMapWindow(redrawMasks = false)
        updateNativeMaskViews()
    }
    fun resolveProjectionDisplay3AppRect() = projectionHole
    fun updateVirtualClusterVisibility(reason: String = "test", forceNativeMaskRefresh: Boolean = false) {
        var appRectOnDisplay3 = externalHole
        var appRectIsAaCluster = false
        var isLeftCovered = false
        var isRightCovered = false
''' + hole_selection + "\n    }\n" + projector_methods + "\n}\n",
        "Bridge.kt": '''package br.com.redesurftank.havalshisuku.projectors
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
class Bridge(private val context: IBridgeContext) {
    private val TAG = "test"
''' + method(bridge, "fun setAppDefaultDimensions(") + "\n}\n",
        "Harness.kt": '''package br.com.redesurftank.havalshisuku.projectors
import android.os.Looper
import android.graphics.Rect
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
private var checks = 0
private fun expect(condition: Boolean, label: String) { checks++; check(condition) { label } }
private fun window(projector: Projector, rect: Rect, label: String) {
    expect(AaClusterVideoHost.textureView!!.clipBounds == rect, "$label clip")
    expect(projector.display3AppRect == rect, "$label hole")
}
fun main() {
    val p = Projector()
    val bridge = Bridge(p)
    AaClusterVideoHost.refreshWindow()
    p.updateVirtualClusterVisibility()
    p.refreshDisplayBounds()
    window(p, Rect(0, 62, 1920, 658), "default full width")
    App.custom = "   "
    p.refreshDisplayBounds()
    window(p, Rect(0, 62, 1920, 658), "blank preference uses default")
    p.changeCard(ClusterCardIds.NATIVE_CARD)
    window(p, Rect(0, 62, 1344, 658), "blank default obeys card clamp")
    p.changeCard(0)
    App.custom = "0,62,1920,658"
    p.changeCard(ClusterCardIds.NATIVE_CARD)
    window(p, Rect(0, 62, 1344, 658), "custom native card")
    p.changeCard(0)
    window(p, Rect(0, 62, 1920, 658), "custom card dismissed")
    App.custom = "1500,100,1900,500"
    p.changeCard(ClusterCardIds.NATIVE_CARD)
    window(p, Rect(0, 0, 0, 0), "fully excluded remains empty")
    p.changeCard(0)
    App.custom = null
    bridge.setAppDefaultDimensions(300, 130, 1100, 400)
    window(p, Rect(0, 130, 1920, 530), "AA-only theme resize")
    App.custom = ""
    p.refreshDisplayBounds()
    window(p, Rect(0, 130, 1920, 530), "empty preference uses current theme")
    App.custom = null
    expect(p.syncs.isEmpty(), "AA-only never resizes an external app")
    val unchangedMasks = p.maskUpdates
    val unchangedTransforms = AaClusterVideoHost.textureView!!.transforms
    repeat(20) { bridge.setAppDefaultDimensions(300, 130, 1100, 400) }
    expect(p.maskUpdates == unchangedMasks, "identical theme does not repaint mask")
    expect(AaClusterVideoHost.textureView!!.transforms == unchangedTransforms, "identical theme is a no-op")
    // A render callback echoing the same theme bounds terminates at the bridge guard.
    p.maskObserver = { bridge.setAppDefaultDimensions(300, 140, 1100, 400) }
    bridge.setAppDefaultDimensions(300, 140, 1100, 400)
    expect(p.maskUpdates == unchangedMasks + 1, "no render/resize feedback loop")
    p.maskObserver = null
    p.changeCard(ClusterCardIds.NATIVE_CARD)
    window(p, Rect(0, 140, 1344, 540), "theme card clamp")
    p.isWarningDismissed = true
    p.changeCard(ClusterCardIds.NATIVE_CARD)
    window(p, Rect(0, 140, 1920, 540), "dismissed warning restores full width")
    p.isWarningDismissed = false
    p.isWarningActive = true
    p.changeCard(0)
    window(p, Rect(0, 140, 1344, 540), "warning clamps and synchronizes")
    p.isWarningActive = false
    p.changeCard(0)
    val beforeCardRepaint = p.maskUpdates
    p.changeCardWithMandatoryMask(ClusterCardIds.NATIVE_CARD)
    expect(p.maskUpdates == beforeCardRepaint + 1, "card path repaints only once")
    window(p, Rect(0, 140, 1344, 540), "mandatory repaint includes current clip")
    p.changeCardWithMandatoryMask(ClusterCardIds.NATIVE_CARD)
    expect(p.maskUpdates == beforeCardRepaint + 2, "unchanged geometry retains mandatory card repaint")
    p.changeCard(0)
    Looper.isMain = false
    val beforeQueue = p.maskUpdates
    bridge.setAppDefaultDimensions(0, 180, 1000, 300)
    expect(p.maskUpdates == beforeQueue, "off-main refresh queues before mutation")
    Looper.isMain = true
    AaClusterVideoHost.mainHandler.drain()
    window(p, Rect(0, 180, 1920, 480), "queued UI refresh")
    // External apps retain their existing mask hole and normal managed-app sync.
    val external = Rect(200, 150, 1200, 650)
    p.externalHole = external
    p.updateVirtualClusterVisibility()
    expect(!p.display3AppRectIsAaCluster, "production selection assigns ordinary-app ownership")
    p.managed = setOf(1, 3)
    val beforeExternal = p.maskUpdates
    bridge.setAppDefaultDimensions(0, 210, 1000, 280)
    expect(p.display3AppRect == external, "external hole preserved")
    expect(p.maskUpdates == beforeExternal, "AA callback leaves external mask untouched")
    expect(p.syncs == listOf(1, 3), "normal external apps still synchronize")
    p.changeCard(ClusterCardIds.NATIVE_CARD)
    expect(p.display3AppRect == external, "native card does not steal external hole")
    p.externalHole = null
    p.projectionHole = external
    p.updateVirtualClusterVisibility()
    expect(!p.display3AppRectIsAaCluster, "projection hole retains priority")
    expect(p.display3AppRect == external, "projection hole preserved")
    p.projectionHole = null
    p.updateVirtualClusterVisibility()
    expect(p.display3AppRectIsAaCluster, "production selection restores AA ownership")
    window(p, Rect(0, 210, 1344, 490), "AA fallback selected")
    p.prepareDisplay3AppHole(intArrayOf(200, 150, 1200, 650))
    expect(!p.display3AppRectIsAaCluster, "external prepare relinquishes AA ownership")
    expect(p.display3AppRect == external, "external prepare geometry unchanged")
    // A stale AA ownership flag cannot punch after the AA surface detached.
    p.display3AppRectIsAaCluster = true
    p.attached = false
    bridge.setAppDefaultDimensions(0, 220, 1000, 270)
    expect(p.display3AppRect == external, "detached AA cannot refresh hole")
    p.attached = true
    p.managed = emptySet()
    App.custom = "invalid"
    p.refreshDisplayBounds()
    window(p, Rect(0, 0, 0, 0), "invalid explicit bounds close both layers")
    p.updateVirtualClusterVisibility()
    window(p, Rect(0, 0, 0, 0), "visibility resolution does not reopen empty AA hole")
    p.attached = false
    p.updateVirtualClusterVisibility()
    expect(p.display3AppRect == null && !p.display3AppRectIsAaCluster, "detach clears AA hole and ownership")
    expect(AaClusterVideoHost.mainHandler.queue.isEmpty(), "no queued feedback work")
    expect(AaClusterVideoHost.textureView!!.width == 1920 &&
        AaClusterVideoHost.textureView!!.height == 720, "clip updates never resize texture")
    println("PASS total=$checks actual Kotlin geometry/refresh checks")
}
''',
    }
    for filename, content in sources.items():
        (output / filename).write_text(content, encoding="utf-8")
    classes = output / "classes"
    classes.mkdir(exist_ok=True)
    javac = shutil.which("javac")
    compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
    run(compiler + ["-source", "11", "-target", "11", "-Xlint:-options", "-d", str(classes),
        str(JAVA / "projectors/AaClusterGeometry.java")])
    runtime_cp = os.pathsep.join([str(classes), str(stdlibs[0])])
    run([java, "-cp", str(deps / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
         "-no-stdlib", "-no-reflect", "-nowarn", "-jvm-target", "11", "-classpath", runtime_cp,
         "-d", str(classes)] + [str(output / name) for name in sources])
    run([java, "-cp", runtime_cp, "br.com.redesurftank.havalshisuku.projectors.HarnessKt"])


if __name__ == "__main__":
    main()
