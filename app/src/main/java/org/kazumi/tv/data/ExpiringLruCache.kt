package org.kazumi.tv.data

class ExpiringLruCache<K, V>(private val capacity: Int, private val ttlMs: Long, private val clock: () -> Long) {
    private data class Entry<V>(val created: Long, val value: V)
    private val entries = LinkedHashMap<K, Entry<V>>(capacity, 0.75f, true)
    init { require(capacity > 0 && ttlMs > 0) }
    @Synchronized fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (clock() - entry.created >= ttlMs) { entries.remove(key); return null }
        return entry.value
    }
    @Synchronized fun put(key: K, value: V) {
        entries[key] = Entry(clock(), value)
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
    @Synchronized fun clear() = entries.clear()
}
