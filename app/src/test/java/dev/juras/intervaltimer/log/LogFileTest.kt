package dev.juras.intervaltimer.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun logFile(maxBytes: Long = 1000) = LogFile(File(folder.root, "logs/timer.log"), maxBytes)

    @Test
    fun linesAreKeptInTheOrderTheyWereWritten() {
        val log = logFile()

        log.append("one")
        log.append("two")

        assertEquals("one\ntwo\n", log.readAll())
    }

    @Test
    fun aFullFileMovesToTheOlderFileAndKeepsBeingReadInOrder() {
        val log = logFile(maxBytes = 10)

        log.append("first line that is long")
        log.append("second")

        assertEquals("first line that is long\nsecond\n", log.readAll())
        assertEquals(true, File(folder.root, "logs/timer.log.1").exists())
    }

    @Test
    fun onlyTheNewestTwoFilesAreKept() {
        val log = logFile(maxBytes = 5)

        listOf("aaaaaaaa", "bbbbbbbb", "cccccccc", "dddddddd").forEach(log::append)

        assertEquals("cccccccc\ndddddddd\n", log.readAll())
    }

    @Test
    fun clearingRemovesBothFiles() {
        val log = logFile(maxBytes = 5)
        listOf("aaaaaaaa", "bbbbbbbb").forEach(log::append)

        log.clear()

        assertEquals("", log.readAll())
        assertFalse(File(folder.root, "logs/timer.log.1").exists())
    }
}
