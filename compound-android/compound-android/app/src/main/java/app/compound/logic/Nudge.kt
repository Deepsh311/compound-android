package app.compound.logic

import app.compound.data.AppData
import app.compound.data.KIND_BREAK
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A short, specific note for today, built on-device from your own data.
 * It follows a few rules from the book: never miss twice, shrink the habit when time is short,
 * and notice when a lift is ready to move up.
 */
fun dailyNudge(data: AppData, plan: DayPlan, today: LocalDate, nowMinute: Int): String {
    val notes = mutableListOf<String>()
    val open = (plan.segments.flatMap { it.items } + plan.anytime).filter { it.habit != null && !isDone(data, it.habit.id, today) }

    open.firstOrNull { missedLast(data, it.habit!!, today) }?.habit?.let { h ->
        notes += if (h.kind == KIND_BREAK) "Yesterday “${h.name}” slipped. Don’t let it slip twice; one good day resets the run."
        else "You missed “${h.name}” last time. Don’t miss twice${if (h.two.isNotBlank()) ". Even the two-minute version counts: ${h.two.lowercase().trimEnd('.')}." else "."}"
    }

    val unit = data.unit
    val lift = data.exercises.map { it to progressOf(data, it) }
    lift.firstOrNull { it.second.ready && it.first.inc > 0 }?.let { (e, _) ->
        notes += "${e.name} is ready to move up to ${nextLabel(e, unit)}."
    } ?: lift.filter { it.second.best != null && it.second.toGo in 1..3 }.minByOrNull { it.second.toGo }?.let { (e, p) ->
        notes += "${e.name} is ${plural(p.toGo, "rep")} away from moving up."
    }

    val remainingFree = plan.segments.filter { it.end > nowMinute }.sumOf { it.free }
    if (notes.size < 2 && remainingFree < 60 && open.isNotEmpty()) {
        open.firstOrNull { it.habit!!.two.isNotBlank() }?.habit?.let { h ->
            notes += "The day is full. If time runs out, do the two-minute version of “${h.name}”: ${h.two.lowercase()}."
        }
    }

    if (notes.isEmpty()) {
        notes += when {
            plan.total > 0 && plan.done == plan.total -> "Everything on today’s list is done. That’s ${plural(votes(data, today).week, "vote")} this week."
            open.isNotEmpty() && nowMinute < 12 * 60 -> "Start with “${open.first().title}”. An early win makes the next one easier."
            open.isNotEmpty() -> "${plural(open.size, "habit")} still open. Pick the smallest and get it done."
            else -> "Add a habit or two and Compound will fit them around your day."
        }
    }
    return notes.take(2).joinToString(" ")
}

/** Workout minutes per week from Health Connect, oldest first. */
fun weeklyWorkoutMinutes(data: AppData, weeks: Int, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Int> {
    val thisWeek = weekStart(today)
    return (weeks - 1 downTo 0).map { back ->
        val from = thisWeek.minusWeeks(back.toLong())
        val to = from.plusDays(6)
        data.workouts.filter {
            val d = Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()
            !d.isBefore(from) && !d.isAfter(to)
        }.sumOf { ((it.end - it.start) / 60000).toInt().coerceAtLeast(0) }
    }
}
