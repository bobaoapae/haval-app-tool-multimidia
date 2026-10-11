package android.os

/** Synthetic monotonic main queue, deliberately able to replay removed work. */
object TestQueue {
    data class Entry(val callback: Runnable, val due: Long, val delay: Long, var removed: Boolean = false, var delivered: Boolean = false)
    var now = 0L
    val entries = mutableListOf<Entry>()

    fun post(callback: Runnable, delay: Long): Boolean {
        entries += Entry(callback, now + delay, delay)
        return true
    }

    fun remove(callback: Runnable) {
        entries.filter { it.callback === callback }.forEach { it.removed = true }
    }

    fun advanceTo(target: Long) {
        check(target >= now)
        while (true) {
            val next = entries.filter { !it.removed && !it.delivered && it.due <= target }.minByOrNull { it.due } ?: break
            now = next.due
            next.delivered = true
            next.callback.run()
        }
        now = target
    }

    fun drain() = advanceTo(now)
    fun pendingHide(): Entry = entries.single { !it.removed && !it.delivered && it.delay == 5_000L }
}

class Looper private constructor() {
    companion object {
        private val main = Looper()
        fun getMainLooper() = main
    }
}

class Handler(looper: Looper) {
    fun post(callback: Runnable) = TestQueue.post(callback, 0)
    fun postDelayed(callback: Runnable, delayMs: Long) = TestQueue.post(callback, delayMs)
    fun removeCallbacks(callback: Runnable) = TestQueue.remove(callback)
}

object SystemClock {
    fun elapsedRealtime() = TestQueue.now
}
