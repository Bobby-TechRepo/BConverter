package io.bsoft.bconverter.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruMemoryCacheTest {
    private fun cache(max: Long) = LruMemoryCache<String, String>(max) { it.length.toLong() }

    @Test fun stored_values_are_returned() {
        val c = cache(10); c.put("a", "xx")
        assertEquals("xx", c.get("a")); assertNull(c.get("b"))
    }

    @Test fun the_least_recently_used_entries_go_first_when_the_budget_is_exceeded() {
        val c = cache(6)
        c.put("a", "xx"); c.put("b", "xx"); c.put("c", "xx")
        c.get("a")            // b is now the oldest
        c.put("d", "xx")
        assertNull(c.get("b")); assertEquals("xx", c.get("a")); assertEquals("xx", c.get("c")); assertEquals("xx", c.get("d"))
        assertEquals(6, c.weight)
    }

    @Test fun replacing_a_key_updates_the_weight() {
        val c = cache(10); c.put("a", "xxxx"); c.put("a", "xx")
        assertEquals(2, c.weight); assertEquals("xx", c.get("a"))
    }

    @Test fun a_value_larger_than_the_whole_budget_is_not_kept() {
        val c = cache(3); c.put("a", "xx"); c.put("big", "xxxxxx")
        assertNull(c.get("big")); assertEquals("xx", c.get("a"))
    }

    @Test fun clear_empties_the_cache() {
        val c = cache(10); c.put("a", "x"); c.clear()
        assertNull(c.get("a")); assertEquals(0, c.weight)
    }
}
