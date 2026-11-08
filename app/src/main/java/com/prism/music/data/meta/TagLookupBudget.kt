package com.prism.music.data.meta

import java.util.concurrent.atomic.AtomicInteger

/** One visit/explicit refresh, including continuation pages and cancelled/restarted effects. */
class TagLookupBudget(limit: Int = 40) {
    private val remaining = AtomicInteger(limit.coerceIn(0, 40))
    fun tryTake(): Boolean {
        while (true) {
            val count = remaining.get()
            if (count <= 0) return false
            if (remaining.compareAndSet(count, count - 1)) return true
        }
    }
}
