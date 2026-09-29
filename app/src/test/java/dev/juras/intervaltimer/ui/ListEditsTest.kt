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

class SteppedTest {
    @Test
    fun aMultipleStepsByExactlyOneStep() {
        assertEquals(45, stepped(40, 1, 5))
        assertEquals(35, stepped(40, -1, 5))
    }

    @Test
    fun otherValuesSnapToTheNextMultiple() {
        assertEquals(45, stepped(42, 1, 5))
        assertEquals(40, stepped(42, -1, 5))
    }

    @Test
    fun aStepOfOneCountsByOne() {
        assertEquals(9, stepped(8, 1, 1))
        assertEquals(7, stepped(8, -1, 1))
    }

    @Test
    fun negativeValuesStepTheSameWay() {
        assertEquals(-5, stepped(-10, 1, 5))
        assertEquals(-15, stepped(-10, -1, 5))
        assertEquals(-5, stepped(-7, 1, 5))
        assertEquals(-10, stepped(-7, -1, 5))
    }
}
