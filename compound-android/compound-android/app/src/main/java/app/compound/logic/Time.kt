package app.compound.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val LONG = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

fun longDate(d: LocalDate): String = d.format(LONG)
fun shortDate(d: LocalDate): String = d.format(SHORT)
fun day(s: String): LocalDate = LocalDate.parse(s)
fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun dayInitial(d: Int): String = "MTWTFSS"[d - 1].toString()
fun dayShort(d: Int): String = DayOfWeek.of(d).name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** "07:30" -> 450 minutes after midnight. */
fun toMin(t: String): Int? {
    val m = Regex("^(\\d{1,2}):(\\d{2})").find(t.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val mi = m.groupValues[2].toInt()
    if (h > 24 || mi > 59) return null
    return h * 60 + mi
}

fun fromMin(m: Int): String = if (m >= 1440) "24:00" else String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)

fun fmtDur(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val r = m % 60
    return when {
        h == 0 -> "$r min"
        r == 0 -> "$h h"
        else -> "$h h $r min"
    }
}

fun fmtW(w: Double): String {
    val r = (w * 100).roundToInt() / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

fun daysText(days: List<Int>): String {
    val s = days.toSet()
    return when {
        s.size == 7 -> "every day"
        s == setOf(1, 2, 3, 4, 5) -> "weekdays"
        s == setOf(6, 7) -> "weekends"
        else -> (1..7).filter { it in s }.joinToString(", ") { dayShort(it) }
    }
}

fun greeting(hour: Int): String = when {
    hour < 4 -> "Up late"
    hour < 12 -> "Good morning"
    hour < 18 -> "Good afternoon"
    else -> "Good evening"
}
