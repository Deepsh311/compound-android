package app.compound.logic

import app.compound.data.AppData
import app.compound.data.DayRecord
import app.compound.data.Exercise
import app.compound.data.Habit
import app.compound.data.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LogicTest {
    private val monday = LocalDate.of(2026, 11, 9)

    private fun habit(id: String, seg: String = "morning", dur: Int = 0, time: String = "", days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)) =
        Habit(id = id, name = id, seg = seg, dur = dur, time = time, days = days, created = monday.minusDays(30).toString())

    private fun done(vararg pairs: Pair<LocalDate, String>): Map<String, DayRecord> =
        pairs.groupBy({ it.first.toString() }, { it.second }).mapValues { (_, ids) -> DayRecord(h = ids.associateWith { 1 }) }

    @Test
    fun habitsFitAroundCalendarEvents() {
        val data = AppData(onboarded = true, habits = listOf(habit("read", dur = 60), habit("walk", dur = 30)))
        val lecture = CalEvent("Lecture", LocalDateTime.of(2026, 11, 9, 6, 30), LocalDateTime.of(2026, 11, 9, 8, 0), false)
        val plan = planDay(data, monday, listOf(lecture), nowMinute = 7 * 60, isToday = true)
        val morning = plan.segments.first { it.id == "morning" }
        val read = morning.items.first { it.habit?.id == "read" }
        val walk = morning.items.first { it.habit?.id == "walk" }
        assertEquals(8 * 60, read.start)
        assertEquals(9 * 60, walk.start)
        assertEquals(330 - 90 - 60 - 30, morning.free)
        assertTrue(morning.isNow)
    }

    @Test
    fun habitWithoutRoomIsFlagged() {
        val data = AppData(onboarded = true, habits = listOf(habit("deep", seg = "morning", dur = 600)))
        val plan = planDay(data, monday, emptyList(), nowMinute = 0, isToday = false)
        assertTrue(plan.segments.first().items.first { it.habit != null }.noSlot)
    }

    @Test
    fun streakSkipsUnscheduledDaysAndBreaksOnMisses() {
        val h = habit("gym", days = listOf(1, 3, 5))
        val fri = monday.minusDays(3)
        val wed = monday.minusDays(5)
        val data = AppData(habits = listOf(h), days = done(monday to "gym", fri to "gym", wed to "gym"))
        assertEquals(3, streak(data, h, monday))
        val missedFriday = AppData(habits = listOf(h), days = done(monday to "gym", wed to "gym"))
        assertEquals(1, streak(missedFriday, h, monday))
    }

    @Test
    fun neverMissTwiceLooksAtTheLastScheduledDay() {
        val h = habit("read")
        val data = AppData(habits = listOf(h), days = done(monday.minusDays(2) to "read"))
        assertTrue(missedLast(data, h, monday))
        val ok = AppData(habits = listOf(h), days = done(monday.minusDays(1) to "read"))
        assertFalse(missedLast(ok, h, monday))
    }

    @Test
    fun doubleProgressionFillsTheBarAndSaysWhenToMoveUp() {
        val e = Exercise("bench", "Bench press", sets = 3, lo = 6, hi = 10, w = 60.0, inc = 2.5, since = "2026-01-01")
        val partial = AppData(exercises = listOf(e), sessions = listOf(Session("s1", "bench", "2026-11-09", 60.0, listOf(10, 8, 7))))
        val p1 = progressOf(partial, e)
        assertFalse(p1.ready)
        assertEquals(5, p1.toGo)
        assertEquals(1f, p1.fills[0], 0.001f)
        assertEquals(0.6f, p1.fills[1], 0.001f)
        val full = partial.copy(sessions = partial.sessions + Session("s2", "bench", "2026-11-10", 60.0, listOf(10, 10, 10)))
        val p2 = progressOf(full, e)
        assertTrue(p2.ready)
        assertEquals("62.5 kg", nextLabel(e, "kg"))
        val heavier = full.copy(sessions = listOf(Session("s3", "bench", "2026-11-10", 62.5, listOf(10, 10, 10))))
        assertNull(progressOf(heavier, e).best)
    }

    @Test
    fun weeklyRatesIgnoreTheFutureAndTodayUntilDone() {
        val h = habit("read")
        val data = AppData(habits = listOf(h), days = done(monday.minusDays(1) to "read", monday.minusDays(2) to "read"))
        val r = rate(data, listOf(h), 3, monday)
        assertEquals(1.0, r!!, 0.0001)
    }
}
