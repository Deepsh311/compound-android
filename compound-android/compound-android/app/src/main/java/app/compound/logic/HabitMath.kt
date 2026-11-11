package app.compound.logic

import app.compound.data.AppData
import app.compound.data.Habit
import java.time.LocalDate

fun isDone(data: AppData, habitId: String, d: LocalDate): Boolean = data.days[d.toString()]?.h?.containsKey(habitId) == true

fun createdOn(h: Habit): LocalDate = runCatching { day(h.created) }.getOrDefault(LocalDate.MIN)

fun scheduledOn(h: Habit, d: LocalDate): Boolean = !d.isBefore(createdOn(h)) && d.dayOfWeek.value in h.days

/** Completions in a row, counting back from today. A scheduled day that was missed ends the run (today is still open). */
fun streak(data: AppData, h: Habit, today: LocalDate): Int {
    val created = createdOn(h)
    var n = 0
    var d = today
    var i = 0
    while (i < 500 && !d.isBefore(created)) {
        if (isDone(data, h.id, d)) n++ else if (i > 0 && d.dayOfWeek.value in h.days) break
        d = d.minusDays(1)
        i++
    }
    return n
}

/** True when the last scheduled day before today was missed: time for "never miss twice". */
fun missedLast(data: AppData, h: Habit, today: LocalDate): Boolean {
    val created = createdOn(h)
    for (i in 1..7) {
        val d = today.minusDays(i.toLong())
        if (d.isBefore(created)) return false
        if (d.dayOfWeek.value in h.days) return !isDone(data, h.id, d)
    }
    return false
}

/** Share of scheduled days that were done over the last [days] days. Today only counts once it is done. */
fun rate(data: AppData, habits: List<Habit>, days: Int, today: LocalDate): Double? = rateBetween(data, habits, today.minusDays(days - 1L), today, today)

fun rateBetween(data: AppData, habits: List<Habit>, from: LocalDate, to: LocalDate, today: LocalDate): Double? {
    var due = 0
    var done = 0
    for (h in habits) {
        var d = from
        while (!d.isAfter(to)) {
            if (scheduledOn(h, d) && !d.isAfter(today)) {
                val ok = isDone(data, h.id, d)
                if (!(d == today && !ok)) {
                    due++
                    if (ok) done++
                }
            }
            d = d.plusDays(1)
        }
    }
    return if (due == 0) null else done.toDouble() / due
}

data class Votes(val week: Int, val today: Int)

/** Every ticked habit, finished priority and logged exercise this week (Monday to Sunday). */
fun votes(data: AppData, today: LocalDate): Votes {
    var total = 0
    var todayN = 0
    var d = weekStart(today)
    while (!d.isAfter(today)) {
        val n = votesOn(data, d)
        total += n
        if (d == today) todayN = n
        d = d.plusDays(1)
    }
    return Votes(total, todayN)
}

fun votesOn(data: AppData, d: LocalDate): Int {
    val key = d.toString()
    val r = data.days[key]
    return (r?.h?.size ?: 0) + (r?.p?.count { it.x } ?: 0) + data.sessions.count { it.d == key }
}

fun activeDays(data: AppData): Int {
    val set = HashSet<String>()
    data.days.forEach { (k, r) -> if (r.h.isNotEmpty() || r.p.any { it.x }) set.add(k) }
    data.sessions.forEach { set.add(it.d) }
    return set.size
}

fun bestStreak(data: AppData, today: LocalDate): Pair<String, Int>? =
    data.habits.map { it.name to streak(data, it, today) }.maxByOrNull { it.second }?.takeIf { it.second > 0 }

/** Weekly consistency for the last [weeks] weeks, oldest first. Null where nothing was scheduled. */
fun weeklyRates(data: AppData, habits: List<Habit>, weeks: Int, today: LocalDate): List<Double?> {
    val thisWeek = weekStart(today)
    return (weeks - 1 downTo 0).map { back ->
        val from = thisWeek.minusWeeks(back.toLong())
        rateBetween(data, habits, from, from.plusDays(6), today)
    }
}

/** Fraction of scheduled habits done on each of the last [days] days (null when none were scheduled). */
fun dailyFractions(data: AppData, days: Int, today: LocalDate): List<Pair<LocalDate, Double?>> =
    (days - 1 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        val due = data.habits.filter { scheduledOn(it, d) }
        val f = if (due.isEmpty()) null else due.count { isDone(data, it.id, d) }.toDouble() / due.size
        d to f
    }

fun weeklyVotes(data: AppData, weeks: Int, today: LocalDate): List<Int> {
    val thisWeek = weekStart(today)
    return (weeks - 1 downTo 0).map { back ->
        val from = thisWeek.minusWeeks(back.toLong())
        (0..6).sumOf { i -> val d = from.plusDays(i.toLong()); if (d.isAfter(today)) 0 else votesOn(data, d) }
    }
}
