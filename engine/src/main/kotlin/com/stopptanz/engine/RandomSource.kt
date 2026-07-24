package com.stopptanz.engine

fun interface RandomSource {
    fun nextLong(min: Long, max: Long): Long
}

class DefaultRandomSource(private val random: kotlin.random.Random = kotlin.random.Random.Default) : RandomSource {
    override fun nextLong(min: Long, max: Long): Long {
        if (min == max) return min
        return random.nextLong(min, max + 1)
    }
}
