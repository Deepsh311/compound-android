package app.compound.logic

import app.compound.data.AppData
import app.compound.data.Habit
import app.compound.data.KIND_BREAK
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil

/** A calendar event read from the phone's calendar. */
data class CalEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String = "",
)

enum class ItemKind { CALENDAR, FIXED, WORKOUT, HABIT }

data class PlanItem(
    val kind: ItemKind,
    val title: String,
    val start: Int?,
    val end: Int?,
    val seg: String,
    val habit: Habit? = null,
    val quick: Boolean = false,
    val noSlot: Boolean = false,
    val clash: String = "",
    val detail: String = "",
)

/** A coloured stretch of the segment bar. type: 0 busy, 1 planned habit, 2 finished habit. */
data class BarBlock(val from: Float, val width: Float, val type: Int)

data class SegmentPlan(
    val id: String,
    val name: String,
    val start: Int,
    val end: Int,
    val items: List<PlanItem>,
    val bar: List<BarBlock>,
    val free: Int,
    val isNow: Boolean,
    val nowFraction: Float,
)

data class DayPlan(
    val allDay: List<String>,
    val segments: List<SegmentPlan>,
    val anytime: List<PlanItem>,
    val total: Int,
    val done: Int,
)

private data class Busy(val start: Int, val end: Int, val title: String, val kind: ItemKind, val detail: String)

data class Seg(val id: String, val name: String, val start: Int, val end: Int)

fun segments(data: AppData): List<Seg> {
    val g = data.segs
    var a = toMin(g.start)
    var b = toMin(g.aft)
    var c = toMin(g.eve)
    var e = toMin(g.end)
    if (e == 0) e = 1440
    if (a == null || b == null || c == null || e == null || !(a < b && b < c && c < e)) {
        a = 390; b = 720; c = 1050; e = 1380
    }
    return listOf(Seg("morning", "Morning", a, b), Seg("afternoon", "Afternoon", b, c), Seg("evening", "Evening", c, e))
}

fun findGap(start: Int, end: Int, sortedBlocks: List<IntRange>, dur: Int): Int? {
    var t = start
    for (b in sortedBlocks) {
        val bs = b.first
        val be = b.last
        if (be <= t) continue
        if (bs >= end) break
        if (bs - t >= dur) return t
        t = (ceil(maxOf(t, be) / 5.0) * 5).toInt()
    }
    return if (end - t >= dur) t else null
}

fun unionLength(blocks: List<IntRange>, s: Int, e: Int): Int {
    val iv = blocks.map { maxOf(it.first, s) to minOf(it.last, e) }.filter { it.second > it.first }.sortedBy { it.first }
    var total = 0
    var cs = -1
    var ce = -1
    for ((a, b) in iv) {
        if (cs < 0) { cs = a; ce = b; continue }
        if (a > ce) { total += ce - cs; cs = a; ce = b } else ce = maxOf(ce, b)
    }
    if (cs >= 0) total += ce - cs
    return total
}

fun planDay(
    data: AppData,
    date: LocalDate,
    events: List<CalEvent>,
    nowMinute: Int,
    isToday: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): DayPlan {
    val segs = segments(data)
    val allDay = mutableListOf<String>()
    val busy = mutableListOf<Busy>()

    for (e in events) {
        if (e.allDay) {
            val first = e.start.toLocalDate()
            val endExclusive = if (e.end.toLocalDate().isAfter(first)) e.end.toLocalDate() else first.plusDays(1)
            if (!date.isBefore(first) && date.isBefore(endExclusive)) allDay += e.title
            continue
        }
        val sd = e.start.toLocalDate()
        val ed = e.end.toLocalDate()
        if (date.isBefore(sd) || date.isAfter(ed)) continue
        val s = if (sd == date) e.start.hour * 60 + e.start.minute else 0
        val en = if (ed == date) e.end.hour * 60 + e.end.minute else 1440
        if (en <= s) continue
        busy += Busy(s, en, e.title.ifBlank { "Busy" }, ItemKind.CALENDAR, e.location)
    }
    val dow = date.dayOfWeek.value
    for (c in data.commits) {
        val s = toMin(c.start) ?: continue
        val en = toMin(c.end) ?: continue
        if (dow in c.days && en > s) busy += Busy(s, en, c.name, ItemKind.FIXED, "fixed commitment")
    }
    for (w in data.workouts) {
        val st = Instant.ofEpochMilli(w.start).atZone(zone).toLocalDateTime()
        val en = Instant.ofEpochMilli(w.end).atZone(zone).toLocalDateTime()
        if (st.toLocalDate() != date) continue
        val s = st.hour * 60 + st.minute
        val e = if (en.toLocalDate() == date) en.hour * 60 + en.minute else 1440
        busy += Busy(s, maxOf(e, s + 1), w.title, ItemKind.WORKOUT, "from ${w.source}")
    }
    busy.sortBy { it.start }

    val habits = data.habits.filter { scheduledOn(it, date) }
    fun segOf(m: Int) = when {
        m < segs[1].start -> "morning"
        m < segs[2].start -> "afternoon"
        else -> "evening"
    }
    val items = busy.map { PlanItem(it.kind, it.title, it.start, it.end, segOf(it.start), detail = it.detail) }.toMutableList()
    val placed = mutableListOf<IntRange>()
    val anytime = mutableListOf<PlanItem>()

    for (h in habits) {
        if (h.kind == KIND_BREAK) continue
        val st = toMin(h.time) ?: continue
        val en = st + h.dur
        val hit = busy.firstOrNull { if (h.dur > 0) st < it.end && en > it.start else st >= it.start && st < it.end }
        items += PlanItem(ItemKind.HABIT, h.name, st, en, segOf(st), habit = h, clash = hit?.title ?: "")
        if (h.dur > 0) placed += st..en
    }
    for (h in habits) {
        if (h.kind != KIND_BREAK && toMin(h.time) != null) continue
        if (h.seg == "any") { anytime += PlanItem(ItemKind.HABIT, h.name, null, null, "any", habit = h, quick = true); continue }
        val seg = segs.firstOrNull { it.id == h.seg } ?: segs[0]
        if (h.kind == KIND_BREAK || h.dur <= 0) {
            items += PlanItem(ItemKind.HABIT, h.name, null, null, seg.id, habit = h, quick = true)
            continue
        }
        val blocks = (busy.map { it.start..it.end } + placed).sortedBy { it.first }
        val st = findGap(seg.start, seg.end, blocks, h.dur)
        if (st == null) {
            items += PlanItem(ItemKind.HABIT, h.name, null, null, seg.id, habit = h, noSlot = true)
        } else {
            items += PlanItem(ItemKind.HABIT, h.name, st, st + h.dur, seg.id, habit = h)
            placed += st..(st + h.dur)
        }
    }

    val busyRanges = busy.map { it.start..it.end }
    val segPlans = segs.map { s ->
        val its = items.filter { it.seg == s.id }.sortedWith(compareBy<PlanItem>({ if (it.quick) 0 else if (it.noSlot) 2 else 1 }, { it.start ?: 0 }))
        val len = (s.end - s.start).toFloat()
        val blocks = busy.map { Triple(it.start, it.end, 0) } +
            items.filter { it.kind == ItemKind.HABIT && it.start != null && it.end != null && it.end > it.start }
                .map { Triple(it.start!!, it.end!!, if (isDone(data, it.habit!!.id, date)) 2 else 1) }
        val bar = blocks.mapNotNull { (a0, z0, type) ->
            val a = maxOf(a0, s.start)
            val z = minOf(z0, s.end)
            if (z > a) BarBlock((a - s.start) / len, (z - a) / len, type) else null
        }
        val free = (s.end - s.start) - unionLength(busyRanges + placed, s.start, s.end)
        SegmentPlan(s.id, s.name, s.start, s.end, its, bar, free, isToday && nowMinute >= s.start && nowMinute < s.end, (nowMinute - s.start) / len)
    }
    return DayPlan(allDay, segPlans, anytime, habits.size, habits.count { isDone(data, it.id, date) })
}
