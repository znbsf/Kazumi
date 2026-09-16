package org.kazumi.tv.data

import java.io.Reader

object BoundedText {
    fun read(reader: Reader, maxChars: Int): String {
        require(maxChars in 1..10_000_000)
        val result=StringBuilder()
        val buffer=CharArray(minOf(8192,maxChars+1))
        while(true) {
            val count=reader.read(buffer,0,minOf(buffer.size,maxChars+1-result.length))
            if(count<0)break
            check(result.length+count<=maxChars) { "响应超过大小限制" }
            result.append(buffer,0,count)
        }
        return result.toString()
    }
}
