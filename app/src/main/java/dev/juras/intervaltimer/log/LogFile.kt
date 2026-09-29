package dev.juras.intervaltimer.log

import java.io.File

/**
 * An append-only text log that never grows past about twice [maxBytes]: when the file is full it
 * becomes `<name>.1` (replacing the previous one) and a new file starts.
 */
class LogFile(private val file: File, private val maxBytes: Long = 512 * 1024) {
    @Synchronized
    fun append(line: String) {
        file.parentFile?.mkdirs()
        if (file.length() > maxBytes) rotate()
        file.appendText(line + "\n")
    }

    /** The older file first, then the current one. */
    @Synchronized
    fun readAll(): String = listOf(older(), file).filter { it.exists() }.joinToString("") { it.readText() }

    @Synchronized
    fun clear() {
        older().delete()
        file.delete()
    }

    private fun rotate() {
        older().delete()
        file.renameTo(older())
    }

    private fun older() = File(file.path + ".1")
}
