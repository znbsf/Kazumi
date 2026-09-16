package org.kazumi.tv.rules

import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.ExpiringLruCache

class ExpiringLruCacheTest {
    @Test fun browsingCategoryKeepsItWhenCapacityIsExceeded() {
        val cache = ExpiringLruCache<String, Int>(2, 100) { 0L }
        cache.put("a", 1); cache.put("b", 2)
        assertEquals(1, cache.get("a"))
        cache.put("c", 3)
        assertNull(cache.get("b")); assertEquals(1, cache.get("a")); assertEquals(3, cache.get("c"))
    }
    @Test fun readsDoNotExtendTtlAndClearRemovesResults() {
        var now = 0L
        val cache = ExpiringLruCache<String, Int>(2, 100) { now }
        cache.put("a", 1)
        now = 99; assertEquals(1, cache.get("a"))
        now = 100; assertNull(cache.get("a"))
        cache.put("a", 2); cache.clear(); assertNull(cache.get("a"))
    }
}
