package app.compound

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.compound.data.AppData
import app.compound.data.CalendarPrefs
import app.compound.data.Commitment
import app.compound.data.DayRecord
import app.compound.data.DaySegments
import app.compound.data.Exercise
import app.compound.data.GarminPrefs
import app.compound.data.Goal
import app.compound.data.Habit
import app.compound.data.KIND_BUILD
import app.compound.data.Priority
import app.compound.data.ReminderPrefs
import app.compound.data.Review
import app.compound.data.ScoreItem
import app.compound.data.Session
import app.compound.data.cleanIdentity
import app.compound.data.emptyData
import app.compound.data.newId
import app.compound.data.starterData
import app.compound.logic.CalEvent
import app.compound.logic.bestE1
import app.compound.logic.createdOn
import app.compound.logic.e1rm
import app.compound.logic.fmtW
import app.compound.logic.nextLabel
import app.compound.logic.plural
import app.compound.logic.progressOf
import app.compound.logic.round2
import app.compound.logic.sameWeight
import app.compound.logic.weekStart
import app.compound.logic.weightLabel
import app.compound.platform.CalendarSource
import app.compound.platform.DeviceCalendar
import app.compound.platform.HealthSource
import app.compound.platform.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToLong

data class HealthState(val status: Int = HealthConnectClient.SDK_UNAVAILABLE, val granted: Boolean = false)

data class UiState(
    val data: AppData,
    val today: LocalDate,
    val nowMinute: Int,
    val events: List<CalEvent>,
    val calendarGranted: Boolean,
    val health: HealthState,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    private val repo = (app as CompoundApp).repository

    private val clock = MutableStateFlow(LocalDateTime.now())
    private val events = MutableStateFlow<List<CalEvent>>(emptyList())
    private val calendarGranted = MutableStateFlow(false)
    private val health = MutableStateFlow(HealthState())

    /** Short confirmations shown in a snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val restEndsAt = MutableStateFlow<Long?>(null)
    val quoteShift = MutableStateFlow(0)
    /** Goes up whenever a vote is cast, so the tally can draw its newest stroke. */
    val voteTick = MutableStateFlow(0)

    val ui: StateFlow<UiState?> = combine(repo.data, clock, events, calendarGranted, health) { d, now, ev, cal, h ->
        UiState(d, now.toLocalDate(), now.hour * 60 + now.minute, ev, cal, h)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                val n = LocalDateTime.now()
                val old = clock.value
                if (n.toLocalDate() != old.toLocalDate() || n.hour != old.hour || n.minute != old.minute) {
                    clock.value = n
                    if (n.toLocalDate() != old.toLocalDate()) {
                        quoteShift.value = 0
                        refreshCalendar()
                    }
                }
            }
        }
        viewModelScope.launch { repo.data.map { it.calendar }.distinctUntilChanged().collect { refreshCalendar() } }
    }

    private fun today(): LocalDate = clock.value.toLocalDate()

    private fun edit(transform: (AppData) -> AppData) {
        viewModelScope.launch { repo.update(transform) }
    }

    private fun AppData.updateDay(date: LocalDate, f: (DayRecord) -> DayRecord): AppData {
        val key = date.toString()
        return copy(days = days + (key to f(days[key] ?: DayRecord())))
    }

    fun say(message: String) {
        messages.tryEmit(message)
    }

    fun onResume() {
        clock.value = LocalDateTime.now()
        refreshCalendar()
        refreshHealth(sync = true)
    }

    /* ---------- onboarding ---------- */

    fun onboard(starter: Boolean, identity: String, name: String) =
        edit { if (starter) starterData(identity, name, today()) else emptyData(identity, name, today()) }

    /* ---------- today ---------- */

    fun toggleHabit(id: String) {
        val date = today()
        viewModelScope.launch {
            var nowDone = false
            val d = repo.update { d ->
                d.updateDay(date) { rec ->
                    nowDone = !rec.h.containsKey(id)
                    rec.copy(h = if (nowDone) rec.h + (id to 1) else rec.h - id)
                }
            }
            if (nowDone) {
                voteTick.value++
                val h = d.habits.firstOrNull { it.id == id }
                if (h != null && h.kind == KIND_BUILD && h.reward.isNotBlank()) say("Done. Your reward: ${h.reward}")
            }
        }
    }

    fun addPriority(text: String) {
        val t = text.trim().take(90)
        if (t.isEmpty()) return
        edit { d -> d.updateDay(today()) { it.copy(p = (it.p + Priority(newId(), t)).take(3)) } }
    }

    fun togglePriority(id: String) {
        val date = today()
        viewModelScope.launch {
            var nowDone = false
            repo.update { d ->
                d.updateDay(date) { rec ->
                    rec.copy(p = rec.p.map { if (it.id == id) { nowDone = !it.x; it.copy(x = nowDone) } else it })
                }
            }
            if (nowDone) voteTick.value++
        }
    }

    fun deletePriority(id: String) = edit { d -> d.updateDay(today()) { rec -> rec.copy(p = rec.p.filter { it.id != id }) } }

    fun carryOver() = edit { d ->
        val y = d.days[today().minusDays(1).toString()]?.p?.filter { !it.x }.orEmpty()
        d.updateDay(today()) { rec -> rec.copy(p = (rec.p + y.map { Priority(newId(), it.t) }).take(3)) }
    }

    fun nextQuote() {
        quoteShift.value = quoteShift.value + 1
    }

    /* ---------- habits, goals, scorecard, reviews ---------- */

    fun saveHabit(h: Habit) = edit { d ->
        d.copy(habits = if (d.habits.any { it.id == h.id }) d.habits.map { if (it.id == h.id) h else it } else d.habits + h)
    }

    fun deleteHabit(id: String) = edit { d ->
        d.copy(habits = d.habits.filter { it.id != id }, goals = d.goals.map { g -> g.copy(habits = g.habits - id) })
    }

    fun saveGoal(g: Goal) = edit { d ->
        d.copy(goals = if (d.goals.any { it.id == g.id }) d.goals.map { if (it.id == g.id) g else it } else d.goals + g)
    }

    fun deleteGoal(id: String) = edit { d -> d.copy(goals = d.goals.filter { it.id != id }) }

    fun addScore(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) edit { d -> d.copy(scorecard = d.scorecard + ScoreItem(newId(), t.take(80))) }
    }

    fun rateScore(id: String, rating: Int) = edit { d -> d.copy(scorecard = d.scorecard.map { if (it.id == id) it.copy(rating = rating) else it }) }

    fun deleteScore(id: String) = edit { d -> d.copy(scorecard = d.scorecard.filter { it.id != id }) }

    fun saveReview(well: String, hard: String, change: String) {
        val week = weekStart(today()).toString()
        edit { d ->
            val r = Review(d.reviews.firstOrNull { it.week == week }?.id ?: newId(), week, well.trim(), hard.trim(), change.trim(), System.currentTimeMillis())
            d.copy(reviews = (d.reviews.filter { it.week != week } + r).sortedByDescending { it.week })
        }
        say("Review saved. Small adjustments, every week.")
    }

    /* ---------- gym ---------- */

    fun saveExercise(e: Exercise) = edit { d ->
        val old = d.exercises.firstOrNull { it.id == e.id }
        val fixed = if (old == null || !sameWeight(old.w, e.w)) e.copy(since = today().toString()) else e
        d.copy(exercises = if (old != null) d.exercises.map { if (it.id == e.id) fixed else it } else d.exercises + fixed)
    }

    fun deleteExercise(id: String) = edit { d -> d.copy(exercises = d.exercises.filter { it.id != id }) }

    fun levelUp(id: String) {
        viewModelScope.launch {
            val d = repo.update { d ->
                d.copy(exercises = d.exercises.map { if (it.id == id) it.copy(w = round2(it.w + it.inc), since = today().toString()) else it })
            }
            d.exercises.firstOrNull { it.id == id }?.let { say("Working weight is now ${weightLabel(it, it.w, d.unit)}. New bar, same system.") }
        }
    }

    fun saveSession(exerciseId: String, weight: Double, reps: List<Int>, sessionId: String?, makeWorking: Boolean) {
        val date = today()
        viewModelScope.launch {
            val before = bestE1(repo.current(), exerciseId, sessionId)
            val w = round2(weight.coerceAtLeast(0.0))
            val r = reps.map { it.coerceIn(0, 100) }
            if (r.none { it > 0 }) { say("Log at least one rep."); return@launch }
            val d = repo.update { d0 ->
                var d = d0
                val e = d.exercises.firstOrNull { it.id == exerciseId } ?: return@update d0
                if (makeWorking && !sameWeight(w, e.w)) {
                    d = d.copy(exercises = d.exercises.map { if (it.id == exerciseId) it.copy(w = w, since = date.toString()) else it })
                }
                val now = System.currentTimeMillis()
                val sessions = if (sessionId != null && d.sessions.any { it.id == sessionId }) {
                    d.sessions.map { if (it.id == sessionId) it.copy(w = w, r = r, at = now) else it }
                } else {
                    d.sessions + Session(newId(), exerciseId, date.toString(), w, r, now)
                }
                d = d.copy(sessions = sessions)
                val gym = d.habits.filter { it.gym && !date.isBefore(createdOn(it)) }
                if (gym.isNotEmpty()) d = d.updateDay(date) { rec -> rec.copy(h = rec.h + gym.associate { it.id to 1 }) }
                d
            }
            voteTick.value++
            val e = d.exercises.firstOrNull { it.id == exerciseId } ?: return@launch
            val p = progressOf(d, e)
            val after = if (w > 0) r.maxOf { e1rm(w, it) } else 0.0
            say(
                when {
                    p.ready && e.inc > 0 -> "Full bar. Next time: ${nextLabel(e, d.unit)}."
                    before > 0 && after > before + 0.01 && !e.bw -> "New best: estimated 1RM ${fmtW((after * 2).roundToLong() / 2.0)} ${d.unit}."
                    sessionId != null -> "Session updated."
                    else -> "Session saved."
                },
            )
        }
    }

    fun deleteSession(id: String) = edit { d -> d.copy(sessions = d.sessions.filter { it.id != id }) }

    fun startRest(seconds: Int) {
        restEndsAt.value = System.currentTimeMillis() + seconds * 1000L
    }

    fun stopRest() {
        restEndsAt.value = null
    }

    /* ---------- settings ---------- */

    fun setName(name: String) = edit { it.copy(name = name.trim().take(40)) }
    fun setIdentity(text: String) = edit { it.copy(identity = cleanIdentity(text)) }
    fun setSegments(s: DaySegments) = edit { it.copy(segs = s) }
    fun setUnit(unit: String) = edit { it.copy(unit = unit) }
    fun setTheme(theme: String) = edit { it.copy(theme = theme) }

    fun saveCommit(c: Commitment) = edit { d ->
        d.copy(commits = if (d.commits.any { it.id == c.id }) d.commits.map { if (it.id == c.id) c else it } else d.commits + c)
    }

    fun deleteCommit(id: String) = edit { d -> d.copy(commits = d.commits.filter { it.id != id }) }

    fun setCalendar(prefs: CalendarPrefs) = edit { it.copy(calendar = prefs) }

    fun refreshCalendar() {
        viewModelScope.launch {
            val granted = CalendarSource.hasPermission(ctx)
            calendarGranted.value = granted
            val d = repo.current()
            val day = today()
            events.value = if (granted && d.calendar.enabled) {
                withContext(Dispatchers.IO) { CalendarSource.events(ctx, day, day, d.calendar.hidden.toSet()) }
            } else emptyList()
        }
    }

    suspend fun deviceCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) { CalendarSource.calendars(ctx) }

    fun setGarmin(prefs: GarminPrefs) {
        viewModelScope.launch {
            repo.update { it.copy(garmin = prefs) }
            if (prefs.enabled) refreshHealth(sync = true, manual = true)
        }
    }

    fun refreshHealth(sync: Boolean, manual: Boolean = false) {
        viewModelScope.launch {
            val status = runCatching { HealthSource.sdkStatus(ctx) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)
            val granted = status == HealthConnectClient.SDK_AVAILABLE && HealthSource.granted(ctx)
            health.value = HealthState(status, granted)
            val d = repo.current()
            val stale = System.currentTimeMillis() - d.garmin.lastSync > 10 * 60_000L
            if (sync && granted && d.garmin.enabled && (manual || stale)) syncWorkouts(manual)
        }
    }

    private suspend fun syncWorkouts(manual: Boolean) {
        val prefs = repo.current().garmin
        val since = Instant.now().minus(Duration.ofDays(30))
        val read = runCatching { withContext(Dispatchers.IO) { HealthSource.readWorkouts(ctx, since, prefs.garminOnly) } }
        val list = read.getOrElse {
            if (manual) say("Couldn\u2019t read workouts from Health Connect. Check that Compound still has access.")
            return
        }
        val zone = ZoneId.systemDefault()
        var added = 0
        repo.update { d ->
            val cutoff = since.toEpochMilli()
            val known = d.workouts.map { it.id }.toSet()
            added = list.count { it.id !in known }
            val merged = (d.workouts.filter { it.start < cutoff } + list).distinctBy { it.id }.sortedBy { it.start }.takeLast(800)
            var out = d.copy(workouts = merged, garmin = d.garmin.copy(lastSync = System.currentTimeMillis()))
            if (d.garmin.tickTraining) {
                val gym = d.habits.filter { it.gym }
                for (w in list) {
                    val date = Instant.ofEpochMilli(w.start).atZone(zone).toLocalDate()
                    val due = gym.filter { !date.isBefore(createdOn(it)) }
                    if (due.isNotEmpty()) out = out.updateDay(date) { rec -> rec.copy(h = rec.h + due.associate { it.id to 1 }) }
                }
            }
            out
        }
        if (manual) say(if (added > 0) "Synced ${plural(added, "new workout")}." else "Up to date. No new workouts.")
    }

    fun setReminders(prefs: ReminderPrefs) {
        edit { it.copy(reminders = prefs) }
        Reminders.scheduleAll(ctx, prefs)
    }

    /* ---------- data ---------- */

    suspend fun exportText(): String = repo.export(repo.current())

    fun importText(text: String) {
        viewModelScope.launch {
            val parsed = runCatching { repo.import(text) }.getOrNull()
            if (parsed == null || !parsed.onboarded) {
                say("That file isn\u2019t a Compound backup.")
                return@launch
            }
            repo.update { parsed }
            Reminders.scheduleAll(ctx, parsed.reminders)
            say("Backup restored.")
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            repo.update { AppData() }
            Reminders.scheduleAll(ctx, ReminderPrefs())
        }
    }
}
