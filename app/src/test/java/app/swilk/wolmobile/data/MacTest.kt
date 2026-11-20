package app.swilk.wolmobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacTest {
    @Test
    fun acceptsCommonFormats() {
        for (input in listOf("aa:bb:cc:dd:ee:0f", "AA-BB-CC-DD-EE-0F", "aabb.ccdd.ee0f", " aabbccddee0f ")) {
            assertEquals("AA:BB:CC:DD:EE:0F", normalizeMac(input))
        }
    }

    @Test
    fun rejectsInvalid() {
        for (input in listOf("", "aa:bb:cc:dd:ee", "aa:bb:cc:dd:ee:0f:11", "gg:bb:cc:dd:ee:0f")) {
            assertNull(normalizeMac(input))
        }
    }
}
