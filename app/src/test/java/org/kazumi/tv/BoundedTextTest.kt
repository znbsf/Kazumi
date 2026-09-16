package org.kazumi.tv

import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.BoundedText

class BoundedTextTest {
    @Test fun exactLimitAndUnicodeArePreserved() {
        assertEquals("测试abc",BoundedText.read("测试abc".reader(),5))
        assertEquals("",BoundedText.read("".reader(),1))
    }
    @Test fun oversizedResponseReadsOnlyOneCharacterPastLimit() {
        var count=0
        val reader=object:java.io.Reader() {
            override fun read(target: CharArray,offset: Int,length: Int): Int {
                count+=length; java.util.Arrays.fill(target,offset,offset+length,'x'); return length
            }
            override fun close() {}
        }
        assertThrows(IllegalStateException::class.java) { BoundedText.read(reader,10000) }
        assertEquals(10001,count)
    }
}
