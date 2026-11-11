package app.compound.logic

import app.compound.data.AppData
import app.compound.data.Exercise
import app.compound.data.Session
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Double progression: keep the weight until every set reaches the top of the rep range, then add weight.
 * Each set's bar fills from one rep below the range (empty) to the top of the range (full).
 */

fun e1rm(w: Double, reps: Int): Double = when {
    reps <= 0 -> 0.0
    reps == 1 -> w
    else -> w * (1 + reps / 30.0)
}

fun sessionsOf(data: AppData, exerciseId: String): List<Session> =
    data.sessions.filter { it.x == exerciseId }.sortedWith(compareByDescending<Session> { it.d }.thenByDescending { it.at })

fun fillsFor(e: Exercise, reps: List<Int>): List<Float> = (0 until e.sets).map { i ->
    val x = reps.getOrElse(i) { 0 }
    val span = e.hi - e.lo + 1
    ((x - (e.lo - 1)).toFloat() / span).coerceIn(0f, 1f)
}

data class Progress(
    val fills: List<Float>,
    val ready: Boolean,
    val toGo: Int,
    val best: Session?,
    val last: Session?,
)

fun sameWeight(a: Double, b: Double) = abs(a - b) < 1e-6

fun progressOf(data: AppData, e: Exercise): Progress {
    val all = sessionsOf(data, e.id)
    val atWeight = all.filter { sameWeight(it.w, e.w) && it.d >= e.since }
    var best: Session? = null
    var bestScore = -1f
    for (s in atWeight) {
        val sc = fillsFor(e, s.r).sum()
        if (sc > bestScore + 1e-6f) { best = s; bestScore = sc }
    }
    val reps = best?.r ?: emptyList()
    val toGo = (0 until e.sets).sumOf { i -> max(e.hi - reps.getOrElse(i) { 0 }, 0) }
    val ready = best != null && reps.size >= e.sets && reps.take(e.sets).all { it >= e.hi }
    return Progress(fillsFor(e, reps), ready, toGo, best, all.firstOrNull())
}

fun round2(x: Double) = (x * 100).roundToInt() / 100.0

fun weightLabel(e: Exercise, w: Double, unit: String): String =
    if (e.bw) (if (w > 0) "bodyweight + ${fmtW(w)} $unit" else "bodyweight") else "${fmtW(w)} $unit"

fun bigWeight(e: Exercise, unit: String): String =
    if (e.bw) (if (e.w > 0) "+${fmtW(e.w)} $unit" else "Bodyweight") else "${fmtW(e.w)} $unit"

fun nextLabel(e: Exercise, unit: String) = weightLabel(e, round2(e.w + e.inc), unit)

fun progressMessage(e: Exercise, p: Progress, unit: String): String = when {
    p.best == null -> "Log a session at ${weightLabel(e, e.w, unit)} to start the bar."
    p.ready && e.inc > 0 -> "Every set hit ${e.hi}. Time for ${nextLabel(e, unit)}."
    p.ready -> "Every set hit ${e.hi}. Try a harder variation."
    else -> "${plural(p.toGo, "rep")} to go, then ${if (e.inc > 0) nextLabel(e, unit) else "a harder variation"}."
}

fun bestE1(data: AppData, exerciseId: String, skipSession: String? = null): Double =
    data.sessions.filter { it.x == exerciseId && it.id != skipSession }
        .maxOfOrNull { s -> s.r.maxOfOrNull { e1rm(s.w, it) } ?: 0.0 } ?: 0.0

/** Best estimated one-rep max per session, oldest first (bodyweight lifts use total reps instead). */
fun strengthSeries(data: AppData, e: Exercise, limit: Int = 24): List<Pair<LocalDate, Double>> =
    sessionsOf(data, e.id).take(limit).reversed().map { s ->
        val v = if (s.w > 0) s.r.maxOfOrNull { e1rm(s.w, it) } ?: 0.0 else s.r.sum().toDouble()
        day(s.d) to v
    }

/** Total weight lifted (weight x reps) per week for the last [weeks] weeks, oldest first. */
fun weeklyVolume(data: AppData, weeks: Int, today: LocalDate): List<Double> {
    val thisWeek = weekStart(today)
    return (weeks - 1 downTo 0).map { back ->
        val from = thisWeek.minusWeeks(back.toLong())
        val to = from.plusDays(6)
        data.sessions.filter { val d = day(it.d); !d.isBefore(from) && !d.isAfter(to) }.sumOf { s -> s.w * s.r.sum() }
    }
}
