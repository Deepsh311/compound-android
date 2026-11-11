package app.compound.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.compound.CompoundApp
import app.compound.MainActivity
import app.compound.R
import app.compound.data.AppData
import app.compound.data.KIND_BREAK
import app.compound.data.ReminderPrefs
import app.compound.data.Workout
import app.compound.data.quoteFor
import app.compound.logic.CalEvent
import app.compound.logic.isDone
import app.compound.logic.missedLast
import app.compound.logic.scheduledOn
import app.compound.logic.toMin
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/* ---------------- Phone calendar ---------------- */

data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int)

object CalendarSource {
    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(ctx: Context): List<DeviceCalendar> {
        if (!hasPermission(ctx)) return emptyList()
        val out = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        runCatching {
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += DeviceCalendar(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "", c.getInt(3))
                }
            }
        }
        return out
    }

    /** Events (with repeats already expanded by Android) between [from] and [to], inclusive. */
    fun events(ctx: Context, from: LocalDate, to: LocalDate, hidden: Set<Long>): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val zone = ZoneId.systemDefault()
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        val out = mutableListOf<CalEvent>()
        runCatching {
            ctx.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getLong(5) in hidden) continue
                    if (c.getInt(6) == CalendarContract.Events.STATUS_CANCELED) continue
                    if (c.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val allDay = c.getInt(3) == 1
                    val s = Instant.ofEpochMilli(c.getLong(1))
                    val e = Instant.ofEpochMilli(c.getLong(2))
                    val start: LocalDateTime
                    val endTime: LocalDateTime
                    if (allDay) {
                        start = s.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay()
                        endTime = e.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay()
                    } else {
                        start = s.atZone(zone).toLocalDateTime()
                        endTime = e.atZone(zone).toLocalDateTime()
                    }
                    if (endTime.toLocalDate().isBefore(from) || start.toLocalDate().isAfter(to)) continue
                    out += CalEvent(c.getString(0) ?: "Busy", start, endTime, allDay, c.getString(4) ?: "")
                }
            }
        }
        return out
    }
}

/* ---------------- Garmin via Health Connect ---------------- */

object HealthSource {
    const val GARMIN = "com.garmin.android.apps.connectmobile"
    private const val PROVIDER = "com.google.android.apps.healthdata"
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

    fun sdkStatus(ctx: Context): Int = HealthConnectClient.getSdkStatus(ctx, PROVIDER)

    suspend fun granted(ctx: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    suspend fun readWorkouts(ctx: Context, since: Instant, garminOnly: Boolean): List<Workout> {
        val client = HealthConnectClient.getOrCreate(ctx)
        val out = mutableListOf<Workout>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(since),
                    dataOriginFilter = if (garminOnly) setOf(DataOrigin(GARMIN)) else emptySet(),
                    pageSize = 500,
                    pageToken = token,
                ),
            )
            for (r in response.records) {
                out += Workout(
                    id = r.metadata.id,
                    start = r.startTime.toEpochMilli(),
                    end = r.endTime.toEpochMilli(),
                    type = r.exerciseType,
                    title = r.title?.takeIf { it.isNotBlank() } ?: typeName(r.exerciseType),
                    source = sourceName(r.metadata.dataOrigin.packageName),
                )
            }
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    fun typeName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ride"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Indoor ride"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool swim"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open-water swim"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stair climber"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
        else -> "Workout"
    }

    private fun sourceName(pkg: String): String = when (pkg) {
        GARMIN -> "Garmin Connect"
        "com.strava" -> "Strava"
        "com.sec.android.app.shealth" -> "Samsung Health"
        "com.google.android.apps.fitness" -> "Google Fit"
        else -> "another app"
    }

    /** Opens the Play Store page for installing or updating Health Connect (Android 13 and older). */
    fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"),
    ).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens Health Connect so people can check which apps share data (Garmin Connect writes, Compound reads). */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/* ---------------- Reminders ---------------- */

object Reminders {
    private const val CHANNEL = "reminders"
    const val MORNING = "morning"
    const val EVENING = "evening"
    const val WHY = "why"

    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Morning quote, evening check-in and the reasons behind your goals"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun scheduleAll(ctx: Context, prefs: ReminderPrefs) {
        schedule(ctx, MORNING, prefs.morning, prefs.morningAt)
        schedule(ctx, EVENING, prefs.evening, prefs.eveningAt)
        schedule(ctx, WHY, prefs.why, prefs.whyAt)
    }

    fun schedule(ctx: Context, kind: String, enabled: Boolean, at: String) {
        val wm = WorkManager.getInstance(ctx)
        val name = "reminder-$kind"
        if (!enabled) {
            wm.cancelUniqueWork(name)
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntil(at), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("kind" to kind))
            .build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private fun delayUntil(at: String): Long {
        val minutes = toMin(at) ?: 450
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong())
        if (Duration.between(now, target).toMillis() < 60_000) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }

    data class Content(val title: String, val text: String)

    fun content(kind: String, data: AppData, today: LocalDate): Content? {
        if (!data.onboarded) return null
        return when (kind) {
            MORNING -> {
                val q = quoteFor(today)
                val due = data.habits.count { scheduledOn(it, today) }
                val hello = if (data.name.isNotBlank()) "Good morning, ${data.name.trim().substringBefore(' ')}" else "Good morning"
                Content(hello, "\u201C${q.text}\u201D \u2014 ${q.author}\n\n${if (due > 0) "$due habits planned today." else "Plan your day in Compound."}")
            }
            EVENING -> {
                val open = data.habits.filter { scheduledOn(it, today) && !isDone(data, it.id, today) }
                if (open.isEmpty()) return null
                val missed = open.firstOrNull { missedLast(data, it, today) }
                val names = open.take(3).joinToString(", ") { if (it.kind == KIND_BREAK) "avoid ${it.name.lowercase()}" else it.name }
                val extra = if (missed != null) " You missed \u201C${missed.name}\u201D last time, so don\u2019t miss twice." else ""
                Content("Evening check-in", "Still open: $names.$extra")
            }
            WHY -> {
                val goals = data.goals.filter { it.why.isNotBlank() || it.becoming.isNotBlank() }
                if (goals.isEmpty()) return null
                val g = goals[Math.floorMod(today.toEpochDay(), goals.size.toLong()).toInt()]
                val parts = listOfNotNull(
                    g.why.takeIf { it.isNotBlank() },
                    g.becoming.takeIf { it.isNotBlank() }?.let { "This makes you ${it.trimEnd('.')}." },
                )
                Content("Remember why: ${g.title}", parts.joinToString(" "))
            }
            else -> null
        }
    }

    fun notify(ctx: Context, kind: String, c: Content) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, kind.hashCode(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", if (kind == WHY) "why" else "today"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.title)
            .setContentText(c.text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(kind.hashCode(), n)
        } catch (_: SecurityException) {
            // Notifications were turned off after the reminder was scheduled.
        }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind") ?: return Result.success()
        val repo = (applicationContext as CompoundApp).repository
        val data = repo.current()
        Reminders.content(kind, data, LocalDate.now())?.let { Reminders.notify(applicationContext, kind, it) }
        val p = data.reminders
        when (kind) {
            Reminders.MORNING -> Reminders.schedule(applicationContext, kind, p.morning, p.morningAt)
            Reminders.EVENING -> Reminders.schedule(applicationContext, kind, p.evening, p.eveningAt)
            Reminders.WHY -> Reminders.schedule(applicationContext, kind, p.why, p.whyAt)
        }
        return Result.success()
    }
}
