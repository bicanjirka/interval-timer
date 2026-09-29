package dev.juras.intervaltimer.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun secondsUnderAMinuteShowAsZeroMinutes() {
        assertEquals("0:07", formatSeconds(7))
    }

    @Test
    fun minutesAndSecondsAreZeroPadded() {
        assertEquals("12:05", formatSeconds(725))
    }

    @Test
    fun anHourOrMoreShowsHours() {
        assertEquals("1:01:01", formatSeconds(3661))
    }

    @Test
    fun negativeSecondsShowAsZero() {
        assertEquals("0:00", formatSeconds(-3))
    }
}
