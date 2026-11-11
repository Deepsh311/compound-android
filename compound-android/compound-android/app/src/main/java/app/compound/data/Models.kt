package app.compound.data

import kotlinx.serialization.Serializable

/*
 * Everything Compound saves lives in one AppData object, stored as JSON with DataStore.
 * Dates are ISO strings ("2026-11-10") and times are "HH:MM" strings so the file stays readable.
 * Days of the week use java.time numbering: 1 = Monday ... 7 = Sunday.
 */

val ALL_DAYS = listOf(1, 2, 3, 4, 5, 6, 7)
const val KIND_BUILD = "build"
const val KIND_BREAK = "break"

@Serializable
data class AppData(
    val version: Int = 1,
    val onboarded: Boolean = false,
    val name: String = "",
    val identity: String = "",
    val segs: DaySegments = DaySegments(),
    val unit: String = "kg",
    val theme: String = "auto",
    val habits: List<Habit> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val commits: List<Commitment> = emptyList(),
    val days: Map<String, DayRecord> = emptyMap(),
    val scorecard: List<ScoreItem> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val calendar: CalendarPrefs = CalendarPrefs(),
    val garmin: GarminPrefs = GarminPrefs(),
    val reminders: ReminderPrefs = ReminderPrefs(),
    val created: String = "",
)

@Serializable
data class DaySegments(
    val start: String = "06:30",
    val aft: String = "12:00",
    val eve: String = "17:30",
    val end: String = "23:00",
)

/**
 * A habit you are building, or one you are breaking (kind = "break").
 * The four text fields follow the four laws of behaviour change. For a habit you are breaking,
 * the same fields hold the inverted laws (invisible, unattractive, difficult, unsatisfying).
 */
@Serializable
data class Habit(
    val id: String,
    val name: String,
    val kind: String = KIND_BUILD,
    val seg: String = "morning",
    val time: String = "",
    val dur: Int = 0,
    val days: List<Int> = ALL_DAYS,
    val cue: String = "",
    val bundle: String = "",
    val prep: String = "",
    val two: String = "",
    val reward: String = "",
    val gym: Boolean = false,
    val created: String,
)

@Serializable
data class Goal(
    val id: String,
    val title: String,
    val why: String = "",
    val habits: List<String> = emptyList(),
    val becoming: String = "",
    val stakes: String = "",
    val letter: String = "",
    val promise: String = "",
    val partner: String = "",
    val consequence: String = "",
    val created: String = "",
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val group: String = "",
    val sets: Int = 3,
    val lo: Int = 8,
    val hi: Int = 12,
    val w: Double = 20.0,
    val inc: Double = 2.5,
    val bw: Boolean = false,
    val since: String = "",
)

/** One logged exercise on one day: working weight and reps per set. */
@Serializable
data class Session(
    val id: String,
    val x: String,
    val d: String,
    val w: Double,
    val r: List<Int>,
    val at: Long = 0,
)

@Serializable
data class Commitment(
    val id: String,
    val name: String,
    val days: List<Int> = listOf(1, 2, 3, 4, 5),
    val start: String = "09:00",
    val end: String = "11:00",
)

@Serializable
data class DayRecord(
    val h: Map<String, Int> = emptyMap(),
    val p: List<Priority> = emptyList(),
)

@Serializable
data class Priority(val id: String, val t: String, val x: Boolean = false)

/** A line in the habits scorecard: +1 helps, 0 neutral, -1 works against you. */
@Serializable
data class ScoreItem(val id: String, val text: String, val rating: Int = 0)

@Serializable
data class Review(
    val id: String,
    val week: String,
    val well: String = "",
    val hard: String = "",
    val change: String = "",
    val at: Long = 0,
)

/** A workout read from Health Connect (Garmin Connect writes these). Times are epoch millis. */
@Serializable
data class Workout(
    val id: String,
    val start: Long,
    val end: Long,
    val type: Int,
    val title: String,
    val source: String,
)

@Serializable
data class CalendarPrefs(val enabled: Boolean = false, val hidden: List<Long> = emptyList())

@Serializable
data class GarminPrefs(
    val enabled: Boolean = false,
    val garminOnly: Boolean = true,
    val tickTraining: Boolean = true,
    val lastSync: Long = 0,
)

@Serializable
data class ReminderPrefs(
    val morning: Boolean = false,
    val morningAt: String = "07:30",
    val evening: Boolean = false,
    val eveningAt: String = "20:30",
    val why: Boolean = false,
    val whyAt: String = "19:00",
)
