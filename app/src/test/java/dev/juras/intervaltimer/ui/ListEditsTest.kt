package dev.juras.intervaltimer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ListEditsTest {
    private val abc = listOf("a", "b", "c")

    @Test
    fun anItemMovesDownOnePlace() {
        assertEquals(listOf("b", "a", "c"), abc.moved(0, by = 1))
    }

    @Test
    fun anItemMovesUpOnePlace() {
        assertEquals(listOf("a", "c", "b"), abc.moved(2, by = -1))
    }

    @Test
    fun movingPastTheEndsChangesNothing() {
        assertEquals(abc, abc.moved(0, by = -1))
        assertEquals(abc, abc.moved(2, by = 1))
    }

    @Test
    fun duplicatingPutsTheCopyRightAfterTheOriginal() {
        assertEquals(listOf("a", "b", "b", "c"), abc.duplicated(1))
    }

    @Test
    fun removingDropsOnlyThatPosition() {
        assertEquals(listOf("a", "c"), abc.without(1))
    }

    @Test
    fun replacingSwapsOnlyThatPosition() {
        assertEquals(listOf("a", "x", "c"), abc.replaced(1, "x"))
    }
}
