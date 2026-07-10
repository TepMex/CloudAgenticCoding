package com.tepmex.paircompelo.core

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Abstraction over time for deterministic tests. */
fun interface AppClock {
    fun now(): Instant
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override fun now(): Instant = Instant.now()
}

class FakeAppClock(
    private var instant: Instant,
) : AppClock {
    override fun now(): Instant = instant
    fun set(instant: Instant) {
        this.instant = instant
    }
    fun advanceDays(days: Double) {
        val millis = (days * 86_400_000.0).toLong()
        instant = instant.plusMillis(millis)
    }
}

fun Clock.toAppClock(): AppClock = AppClock { Instant.now(this) }
