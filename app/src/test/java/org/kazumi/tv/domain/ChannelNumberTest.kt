package org.kazumi.tv.domain

import org.junit.Assert.*
import org.junit.Test

class ChannelNumberTest {
    @Test fun consecutiveDigitsResolveOneBasedChannel() {
        val input = ChannelNumber.append(ChannelNumber.append("", 1), 2)
        assertEquals(11, ChannelNumber.index(input, 48))
    }
    @Test fun invalidNumbersCannotSelectAnotherProgram() {
        listOf("0", "49", "999", "", "oops").forEach { assertNull(ChannelNumber.index(it, 48)) }
    }
    @Test fun fourthDigitStartsNewInput() { assertEquals("2", ChannelNumber.append("123", 2)) }
}
