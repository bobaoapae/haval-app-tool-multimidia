package harness

import android.os.TestQueue
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterController as Controller
import br.com.redesurftank.havalshisuku.managers.AndroidAutoClusterClient
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions
import br.com.redesurftank.havalshisuku.managers.AndroidAutoTelemetryKeys
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.projectors.AaClusterVideoHost

private var checks = 0
private fun expect(condition: Boolean, message: String) {
    checks++
    check(condition) { message }
}
private fun nav(active: Boolean) = Controller.onNavigationUpdate(Directions(active))
private fun shown(expected: Boolean, message: String) {
    TestQueue.drain()
    expect(AaClusterVideoHost.isShown() == expected, message)
}
private fun beginSession() {
    Controller.onLinkStatus(0)
    shown(false, "session teardown hides output")
    Controller.onLinkStatus(1)
    Controller.setClusterMapEnabled(true, "harness")
    nav(true)
    shown(true, "session, theme demand and guidance show output")
}

fun main() {
    Controller.start()
    shown(false, "startup baseline hidden")
    Controller.setClusterMapEnabled(true, "harness")
    nav(true)
    shown(false, "pre-session guidance alone cannot show output")
    Controller.onLinkStatus(1)
    shown(true, "existing pre-session guidance behavior preserved")
    expect(AndroidAutoClusterClient.outputs.last() != null, "controller delivers consumer with valid demand")
    expect(br.com.redesurftank.havalshisuku.models.BottomBarState.linked == listOf(false, true), "v9 dock receives stopped and active session states")

    val firstInactive = TestQueue.now
    nav(false)
    val expired = TestQueue.pendingHide()
    for (offset in listOf(1L, 999L, 1_999L, 2_999L, 3_999L, 4_999L)) {
        TestQueue.advanceTo(firstInactive + offset)
        nav(false)
        shown(true, "map retained during grace period $offset")
    }
    TestQueue.advanceTo(firstInactive + 5_000)
    shown(false, "repeated inactive callbacks must hide at first five-second deadline")
    expect(AndroidAutoClusterClient.outputs.last() == null, "expiry detaches controller output")
    expired.callback.run()
    shown(false, "duplicate expiry stays hidden")
    nav(true)
    shown(true, "navigation resumes without new theme request")
    expired.callback.run()
    shown(true, "completed old callback cannot hide resumed navigation")

    nav(false)
    val cancelled = TestQueue.pendingHide()
    TestQueue.advanceTo(TestQueue.now + 4_999)
    nav(true)
    shown(true, "reactivation before deadline retains output")
    cancelled.callback.run()
    shown(true, "dequeued cancelled callback cannot hide active output")
    nav(false)
    val fresh = TestQueue.pendingHide()
    cancelled.callback.run()
    TestQueue.advanceTo(fresh.due - 1)
    shown(true, "new gap keeps its own deadline")
    TestQueue.advanceTo(fresh.due)
    shown(false, "new gap expires normally")

    beginSession()
    nav(false)
    val disconnected = TestQueue.pendingHide()
    Controller.onLinkStatus(0)
    shown(false, "disconnect cancels output immediately")
    expect(!Controller.isClusterRequested(), "disconnect clears theme request")
    expect(br.com.redesurftank.havalshisuku.models.BottomBarState.linked.last() == false, "v9 dock disconnect publication retained")
    expect(Controller.currentSessionValue() == AndroidAutoTelemetryKeys.SESSION_STOPPED, "stopped baseline retained")
    expect(ServiceManager.getInstance().telemetry.last() == AndroidAutoTelemetryKeys.DIRECTIONS to Directions(false).toJson(), "disconnect publishes inactive directions")
    disconnected.callback.run()
    shown(false, "old callback after teardown does not restore output")
    Controller.onLinkStatus(1)
    Controller.setClusterMapEnabled(true, "harness")
    shown(false, "new session requires fresh navigation evidence")
    nav(true)
    shown(true, "reconnected session accepts fresh guidance")
    nav(false)
    val reconnected = TestQueue.pendingHide()
    disconnected.callback.run()
    shown(true, "old-session hide cannot affect reconnected navigation")
    TestQueue.advanceTo(reconnected.due)
    shown(false, "reconnected session expires its own gap")

    beginSession()
    nav(false)
    val disabled = TestQueue.pendingHide()
    Controller.setClusterMapEnabled(false, "harness")
    shown(false, "explicit theme disable hides during grace period")
    disabled.callback.run()
    shown(false, "late timer does not re-enable disabled demand")
    Controller.onLinkStatus(0)
    TestQueue.advanceTo(TestQueue.now + 20_000)
    shown(false, "teardown without restart leaves output hidden")
    println("PASS total=$checks actual controller wiring checks")
}
