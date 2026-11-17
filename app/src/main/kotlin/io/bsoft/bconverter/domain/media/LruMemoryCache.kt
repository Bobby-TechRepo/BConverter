package io.bsoft.bconverter.domain.media

/** A thread-safe least-recently-used cache bounded by total weight (e.g. bytes), so decoded pictures cannot grow without limit (spec §44). */
class LruMemoryCache<K : Any, V : Any>(private val maxWeight: Long, private val weigher: (V) -> Long) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    var weight: Long = 0L
        private set

    @Synchronized fun get(key: K): V? = map[key]

    @Synchronized fun put(key: K, value: V) {
        map.remove(key)?.let { weight -= weigher(it) }
        val w = weigher(value)
        if (w > maxWeight) return
        map[key] = value
        weight += w
        val it = map.entries.iterator()
        while (weight > maxWeight && it.hasNext()) {
            val oldest = it.next()
            if (oldest.key == key) continue
            weight -= weigher(oldest.value)
            it.remove()
        }
    }

    @Synchronized fun clear() { map.clear(); weight = 0 }
}
