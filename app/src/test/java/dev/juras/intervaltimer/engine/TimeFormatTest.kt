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

class FormatBigTest {
    @Test
    fun belowOneHundredSecondsShowsOnlyTheSeconds() {
        assertEquals("0", formatBig(0))
        assertEquals("7", formatBig(7))
        assertEquals("99", formatBig(99))
    }

    @Test
    fun oneHundredSecondsAndMoreShowMinutesAndSeconds() {
        assertEquals("1:40", formatBig(100))
        assertEquals("2:05", formatBig(125))
    }

    @Test
    fun negativeSecondsShowAsZero() {
        assertEquals("0", formatBig(-4))
    }
}
