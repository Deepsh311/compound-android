package app.compound.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import app.compound.AppViewModel
import app.compound.BuildConfigInfo
import app.compound.UiState
import app.compound.data.Commitment
import app.compound.data.DaySegments
import app.compound.data.ReminderPrefs
import app.compound.data.newId
import app.compound.logic.daysText
import app.compound.logic.toMin
import app.compound.platform.DeviceCalendar
import app.compound.platform.HealthSource
import app.compound.platform.Reminders
import app.compound.ui.SheetReq
import app.compound.ui.components.Btn
import app.compound.ui.components.CheckRow
import app.compound.ui.components.Confirm
import app.compound.ui.components.DaysPicker
import app.compound.ui.components.Field
import app.compound.ui.components.HairLine
import app.compound.ui.components.IconBtn
import app.compound.ui.components.Muted
import app.compound.ui.components.PillButton
import app.compound.ui.components.ScreenPadding
import app.compound.ui.components.SectionHeader
import app.compound.ui.components.Segmented
import app.compound.ui.components.Sheet
import app.compound.ui.components.TimeField
import app.compound.ui.sketch.CalendarArt
import app.compound.ui.sketch.Glyph
import app.compound.ui.sketch.WatchArt
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(vm: AppViewModel, s: UiState, onBack: () -> Unit, open: (SheetReq) -> Unit) {
    val p = LocalPalette.current
    val d = s.data
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.setCalendar(d.calendar.copy(enabled = true)) else vm.say("Compound needs calendar access to plan around your events.")
        vm.refreshCalendar()
    }
    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthSource.PERMISSIONS)) vm.setGarmin(d.garmin.copy(enabled = true))
        else vm.say("Workouts stay off until Compound can read exercise data.")
        vm.refreshHealth(sync = false)
    }
    var pendingReminders by remember { mutableStateOf<ReminderPrefs?>(null) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val wanted = pendingReminders
        pendingReminders = null
        if (ok && wanted != null) vm.setReminders(wanted) else if (!ok) vm.say("Turn on notifications for Compound to get reminders.")
    }
    fun setReminders(r: ReminderPrefs) {
        val turningOn = (r.morning && !d.reminders.morning) || (r.evening && !d.reminders.evening) || (r.why && !d.reminders.why)
        if (turningOn && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) {
            pendingReminders = r
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.setReminders(r)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportText()
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } }
                .onSuccess { vm.say("Backup saved.") }.onFailure { vm.say("Couldn\u2019t save the backup.") }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text == null) vm.say("Couldn\u2019t open that file.") else vm.importText(text)
        }
    }

    LazyColumn(contentPadding = ScreenPadding) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconBtn(Glyph.Back, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("Settings", style = Type.title, color = p.ink)
            }
        }
        item { YouSection(vm, s) }
        item { DaySection(vm, s) }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Phone calendar")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalendarArt(Modifier.width(96.dp))
                    Spacer(Modifier.width(12.dp))
                    Muted(
                        if (d.calendar.enabled && s.calendarGranted) "Compound reads today\u2019s events from the calendars on this phone (Google, Outlook, your uni timetable) and fits habits around them. Nothing is changed."
                        else "Let Compound read your phone\u2019s calendars so it can plan habits around lectures, shifts and plans. It never changes your events.",
                        Modifier.weight(1f),
                    )
                }
                if (d.calendar.enabled && s.calendarGranted) {
                    var cals by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
                    LaunchedEffect(Unit) { cals = vm.deviceCalendars() }
                    if (cals.isNotEmpty()) Text("Plan around", style = Type.label, color = p.ink2, modifier = Modifier.padding(top = 12.dp))
                    cals.forEach { c ->
                        CheckRow("${c.name}${if (c.account.isNotBlank() && c.account != c.name) " (${c.account})" else ""}", c.id !in d.calendar.hidden) { on ->
                            vm.setCalendar(d.calendar.copy(hidden = if (on) d.calendar.hidden - c.id else d.calendar.hidden + c.id))
                        }
                    }
                    PillButton("Disconnect", { vm.setCalendar(d.calendar.copy(enabled = false)) }, Modifier.padding(top = 8.dp), kind = Btn.Ghost, small = true)
                } else {
                    PillButton("Connect calendar", {
                        if (s.calendarGranted) vm.setCalendar(d.calendar.copy(enabled = true)) else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    }, Modifier.padding(top = 12.dp), small = true)
                }
            }
        }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Fixed commitments") { PillButton("Add", { open(SheetReq.CommitEdit(null)) }, small = true) }
                Muted("Regular blocks that aren\u2019t in a calendar, such as a commute or a weekly shift.")
                d.commits.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open(SheetReq.CommitEdit(c.id)) }.padding(vertical = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = Type.item, color = p.ink)
                            Muted(daysText(c.days))
                        }
                        Text("${c.start}\u2013${c.end}", style = Type.small, color = p.ink2)
                    }
                    HairLine()
                }
            }
        }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Garmin and Health Connect")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WatchArt(Modifier.width(96.dp))
                    Spacer(Modifier.width(12.dp))
                    Muted(
                        "Garmin Connect can share your workouts with Health Connect on Android 14 and later. Compound reads them from there: no Garmin password, nothing leaves your phone.",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                when (s.health.status) {
                    HealthConnectClient.SDK_UNAVAILABLE -> Muted("Health Connect isn\u2019t available on this phone, so Garmin workouts can\u2019t be read.")
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Muted("Health Connect needs to be installed or updated first.")
                        PillButton("Get Health Connect", { runCatching { ctx.startActivity(HealthSource.installIntent()) } }, Modifier.padding(top = 10.dp), small = true)
                    }
                    else -> if (!s.health.granted) {
                        Steps()
                        PillButton("Allow Compound to read workouts", { healthPermission.launch(HealthSource.PERMISSIONS) }, Modifier.padding(top = 12.dp), small = true)
                    } else {
                        SwitchRow("Use workouts from Health Connect", d.garmin.enabled) { vm.setGarmin(d.garmin.copy(enabled = it)) }
                        if (d.garmin.enabled) {
                            SwitchRow("Only Garmin Connect workouts", d.garmin.garminOnly) { vm.setGarmin(d.garmin.copy(garminOnly = it)) }
                            SwitchRow("A workout ticks my training habits", d.garmin.tickTraining) { vm.setGarmin(d.garmin.copy(tickTraining = it)) }
                            val last = if (d.garmin.lastSync > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.garmin.lastSync)) else "never"
                            Muted("${d.workouts.size} workouts stored. Last synced: $last.", Modifier.padding(top = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                                PillButton("Sync now", { vm.refreshHealth(sync = true, manual = true) }, small = true)
                                PillButton("Open Health Connect", { runCatching { ctx.startActivity(HealthSource.settingsIntent()) } }, kind = Btn.Ghost, small = true)
                            }
                        }
                        Steps()
                    }
                }
            }
        }
        item {
            val r = d.reminders
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Reminders")
                ReminderRow("Morning quote", "Today\u2019s quote and how many habits are planned.", r.morning, r.morningAt,
                    { setReminders(r.copy(morning = it)) }, { setReminders(r.copy(morningAt = it)) })
                ReminderRow("Evening check-in", "Only if something is still open, with a nudge not to miss twice.", r.evening, r.eveningAt,
                    { setReminders(r.copy(evening = it)) }, { setReminders(r.copy(eveningAt = it)) })
                ReminderRow("Remember why", "One of your reasons, from the Why tab.", r.why, r.whyAt,
                    { setReminders(r.copy(why = it)) }, { setReminders(r.copy(whyAt = it)) })
            }
        }
        item {
            Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Appearance")
                Text("Weight units", style = Type.label, color = p.ink2)
                Segmented(listOf("kg" to "kg", "lb" to "lb"), d.unit, { vm.setUnit(it); vm.say("Weights now show in $it. Numbers you\u2019ve logged aren\u2019t converted.") })
                Text("Theme", style = Type.label, color = p.ink2)
                Segmented(listOf("auto" to "Match phone", "light" to "Light", "dark" to "Dark"), d.theme, { vm.setTheme(it) })
            }
        }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Your data")
                Muted("Everything is saved on this phone only. Export a backup before you switch phones.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    PillButton("Export backup", { exportFile.launch("compound-backup-${s.today}.json") }, kind = Btn.Ghost, small = true)
                    PillButton("Import backup", { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) }, kind = Btn.Ghost, small = true)
                }
                PillButton("Reset everything", { confirmReset = true }, Modifier.padding(top = 10.dp), kind = Btn.Danger, small = true)
            }
        }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("About")
                Muted("Compound ${BuildConfigInfo.VERSION}. Built around the ideas in Atomic Habits by James Clear; not affiliated with the author or publisher. Typeset in Archivo (SIL Open Font License).")
            }
        }
    }
    if (confirmReset) {
        Confirm("Reset everything?", "This deletes your habits, goals, lifts, logs and settings from this phone. Export a backup first if you might want them.", "Reset", {
            vm.resetAll()
            onBack()
        }, { confirmReset = false })
    }
}

@Composable
private fun Steps() {
    Column(Modifier.padding(top = 8.dp)) {
        Text("To share from Garmin", style = Type.label, color = LocalPalette.current.ink2)
        Muted("1. In Garmin Connect, open More, then Settings, then Connected apps, then Health Connect.")
        Muted("2. Allow Garmin Connect to write workouts (and anything else you like).")
        Muted("3. Sync your watch with Garmin Connect, then come back and tap Sync now.")
    }
}

@Composable
private fun SwitchRow(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!on) }) {
        Text(text, style = Type.body, color = p.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.ink, checkedThumbColor = p.hi, uncheckedTrackColor = p.line, uncheckedThumbColor = p.ink3, uncheckedBorderColor = p.line2),
        )
    }
}

@Composable
private fun ReminderRow(title: String, text: String, on: Boolean, at: String, onToggle: (Boolean) -> Unit, onTime: (String) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.item, color = p.ink)
                Muted(text)
            }
            Switch(
                checked = on, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = p.ink, checkedThumbColor = p.hi, uncheckedTrackColor = p.line, uncheckedThumbColor = p.ink3, uncheckedBorderColor = p.line2),
            )
        }
        if (on) TimeField("Time", at, { if (toMin(it) != null) onTime(it) }, Modifier.padding(top = 8.dp))
    }
    HairLine()
}

@Composable
private fun YouSection(vm: AppViewModel, s: UiState) {
    var name by remember(s.data.name) { mutableStateOf(s.data.name) }
    var identity by remember(s.data.identity) { mutableStateOf(s.data.identity) }
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("You")
        Field("Your first name (optional)", name, { name = it }, max = 40)
        Field("Finish the sentence: I\u2019m someone who\u2026", identity, { identity = it }, singleLine = false, minLines = 2,
            placeholder = "trains four times a week and finishes what I start")
        PillButton("Save", {
            vm.setName(name)
            vm.setIdentity(identity)
            vm.say("Saved. That\u2019s who you\u2019re voting for.")
        }, small = true)
    }
}

@Composable
private fun DaySection(vm: AppViewModel, s: UiState) {
    val g = s.data.segs
    var start by remember(g) { mutableStateOf(g.start) }
    var aft by remember(g) { mutableStateOf(g.aft) }
    var eve by remember(g) { mutableStateOf(g.eve) }
    var end by remember(g) { mutableStateOf(g.end) }
    Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Your day")
        Muted("Compound splits the day into morning, afternoon and evening, then fits habits into the free time around your commitments.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Day starts", start, { start = it }, Modifier.weight(1f))
            TimeField("Afternoon from", aft, { aft = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Evening from", eve, { eve = it }, Modifier.weight(1f))
            TimeField("Day ends", end, { end = it }, Modifier.weight(1f))
        }
        PillButton("Save times", {
            val a = toMin(start); val b = toMin(aft); val c = toMin(eve)
            var e = toMin(end)
            if (e == 0) e = 1440
            if (a == null || b == null || c == null || e == null || !(a < b && b < c && c < e)) vm.say("Times need to run in order through the day.")
            else { vm.setSegments(DaySegments(start, aft, eve, end)); vm.say("Day times saved.") }
        }, small = true)
    }
}

@Composable
fun CommitmentEditor(vm: AppViewModel, s: UiState, id: String?, close: () -> Unit) {
    val existing = s.data.commits.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var days by remember { mutableStateOf(existing?.days ?: listOf(1, 2, 3, 4, 5)) }
    var start by remember { mutableStateOf(existing?.start ?: "09:00") }
    var end by remember { mutableStateOf(existing?.end ?: "11:00") }
    Sheet(if (existing == null) "New commitment" else "Edit commitment", close) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Name", name, { name = it }, placeholder = "Commute", max = 60)
            DaysPicker(days) { days = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("From", start, { start = it }, Modifier.weight(1f))
                TimeField("To", end, { end = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (existing != null) PillButton("Delete", { vm.deleteCommit(existing.id); close() }, kind = Btn.Danger)
                Spacer(Modifier.weight(1f))
                PillButton(if (existing == null) "Add" else "Save", {
                    when {
                        name.isBlank() -> vm.say("Give it a name.")
                        days.isEmpty() -> vm.say("Pick at least one day.")
                        (toMin(end) ?: 0) <= (toMin(start) ?: 0) -> vm.say("The end time needs to be after the start.")
                        else -> { vm.saveCommit(Commitment(existing?.id ?: newId(), name.trim(), days, start, end)); close() }
                    }
                })
            }
        }
    }
}
