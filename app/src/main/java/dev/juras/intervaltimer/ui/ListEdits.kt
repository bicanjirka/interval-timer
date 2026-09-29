package dev.juras.intervaltimer.ui

/** New lists for the editor's move / duplicate / delete buttons; out-of-range moves change nothing. */
fun <T> List<T>.moved(from: Int, by: Int): List<T> {
    val to = from + by
    if (from !in indices || to !in indices) return this
    return toMutableList().also { it.add(to, it.removeAt(from)) }
}

fun <T> List<T>.duplicated(index: Int): List<T> =
    if (index !in indices) this else toMutableList().also { it.add(index + 1, this[index]) }

fun <T> List<T>.without(index: Int): List<T> = filterIndexed { i, _ -> i != index }

fun <T> List<T>.replaced(index: Int, item: T): List<T> = mapIndexed { i, old -> if (i == index) item else old }

/** The next multiple of [step] above (direction 1) or below (-1) [value], so 42 steps to 45 and 40 rather than 47 and 37. */
fun stepped(value: Int, direction: Int, step: Int): Int =
    if (direction > 0) (Math.floorDiv(value, step) + 1) * step else -Math.floorDiv(-value, step) * step - step
