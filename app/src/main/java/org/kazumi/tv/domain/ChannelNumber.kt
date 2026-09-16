package org.kazumi.tv.domain

/** LCN is a one-based index within the displayed category. */
object ChannelNumber {
    fun append(previous: String, digit: Int): String {
        require(digit in 0..9)
        return if (previous.length >= 3) digit.toString() else previous + digit
    }
    fun index(input: String, count: Int): Int? = input.toIntOrNull()
        ?.takeIf { it in 1..count }?.minus(1)
}
