package app.compound.ui.gym

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.compound.AppViewModel
import app.compound.UiState
import app.compound.data.AppData
import app.compound.data.Exercise
import app.compound.data.newId
import app.compound.logic.bigWeight
import app.compound.logic.fmtW
import app.compound.logic.nextLabel
import app.compound.logic.progressMessage
import app.compound.logic.progressOf
import app.compound.logic.round2
import app.compound.logic.sameWeight
import app.compound.logic.sessionsOf
import app.compound.logic.shortDate
import app.compound.logic.strengthSeries
import app.compound.logic.day
import app.compound.logic.fmtDur
import app.compound.logic.weightLabel
import app.compound.ui.SheetReq
import app.compound.ui.components.Btn
import app.compound.ui.components.CheckRow
import app.compound.ui.components.Chip
import app.compound.ui.components.Confirm
import app.compound.ui.components.Field
import app.compound.ui.components.HairLine
import app.compound.ui.components.Muted
import app.compound.ui.components.PillButton
import app.compound.ui.components.ScreenPadding
import app.compound.ui.components.SectionHeader
import app.compound.ui.components.SetBars
import app.compound.ui.components.Sheet
import app.compound.ui.components.Sparkline
import app.compound.ui.components.Stepper
import app.compound.ui.sketch.BarbellArt
import app.compound.ui.sketch.WatchArt
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import java.time.Instant
import java.time.ZoneId

@Composable
fun GymScreen(vm: AppViewModel, s: UiState, open: (SheetReq) -> Unit, openSettings: () -> Unit) {
    val p = LocalPalette.current
    val d = s.data
    var filter by rememberSaveable { mutableStateOf("All") }
    val groups = d.exercises.map { it.group }.filter { it.isNotBlank() }.distinct()
    if (filter != "All" && filter !in groups) filter = "All"
    val list = d.exercises.filter { filter == "All" || it.group == filter }
    LazyColumn(contentPadding = ScreenPadding) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Gym", style = Type.title, color = p.ink, modifier = Modifier.weight(1f))
                PillButton("Add exercise", { open(SheetReq.ExerciseEdit(null)) }, small = true)
            }
        }
        item { Muted("Each bar is one set. Fill every set to the top of its rep range and it\u2019s time to add weight.", Modifier.padding(top = 6.dp)) }
        if (groups.size > 1) {
            item {
                Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("All") + groups).forEach { g -> Chip(g, filter == g) { filter = g } }
                }
            }
        }
        if (list.isEmpty()) {
            item {
                Column(Modifier.padding(top = 20.dp)) {
                    BarbellArt(Modifier.padding(horizontal = 16.dp))
                    Text("Add your first exercise and Compound will tell you when it\u2019s time to put more weight on the bar.", style = Type.body, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        items(list, key = { it.id }) { e -> ExerciseRow(e, d, vm, open) }
        item { GarminPanel(vm, s, openSettings) }
    }
}

@Composable
private fun ExerciseRow(e: Exercise, d: AppData, vm: AppViewModel, open: (SheetReq) -> Unit) {
    val p = LocalPalette.current
    val pr = progressOf(d, e)
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Log ${e.name}") { open(SheetReq.Logger(e.id)) }.padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(e.name, style = Type.item.copy(fontSize = 18.sp, fontWeight = Type.label.fontWeight), color = p.ink, modifier = Modifier.weight(1f))
            Text(bigWeight(e, d.unit), style = Type.number, color = p.ink)
        }
        Muted("${e.sets} \u00D7 ${e.lo}${if (e.hi > e.lo) "\u2013${e.hi}" else ""} reps${if (e.group.isNotBlank()) ", ${e.group}" else ""}")
        SetBars(pr.fills, pr.ready, Modifier.padding(top = 12.dp))
        Text(progressMessage(e, pr, d.unit), style = Type.small.copy(fontSize = 14.5.sp), color = p.ink2, modifier = Modifier.padding(top = 8.dp))
        pr.last?.let { Muted("Last: ${it.r.joinToString("/")} at ${weightLabel(e, it.w, d.unit)}, ${shortDate(day(it.d))}") }
        if (pr.ready && e.inc > 0) PillButton("Move up to ${nextLabel(e, d.unit)}", { vm.levelUp(e.id) }, Modifier.padding(top = 12.dp), kind = Btn.Hi)
    }
    HairLine()
}

@Composable
private fun GarminPanel(vm: AppViewModel, s: UiState, openSettings: () -> Unit) {
    val p = LocalPalette.current
    val d = s.data
    Column(Modifier.padding(top = 30.dp)) {
        SectionHeader("From your watch") {
            if (d.garmin.enabled && s.health.granted) PillButton("Sync now", { vm.refreshHealth(sync = true, manual = true) }, small = true, kind = Btn.Ghost)
        }
        if (!d.garmin.enabled || !s.health.granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WatchArt(Modifier.width(110.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bring in your Garmin workouts", style = Type.label, color = p.ink)
                    Muted("Garmin Connect shares workouts with Health Connect. Compound reads them to tick training habits and chart your weekly minutes.")
                    PillButton("Set up in Settings", openSettings, Modifier.padding(top = 10.dp), small = true)
                }
            }
            return@Column
        }
        val zone = ZoneId.systemDefault()
        val recent = d.workouts.sortedByDescending { it.start }.take(6)
        if (recent.isEmpty()) Muted("No workouts in the last 30 days yet. After your watch syncs with Garmin Connect, tap Sync now.")
        recent.forEach { w ->
            val start = Instant.ofEpochMilli(w.start).atZone(zone)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(w.title, style = Type.item, color = p.ink)
                    Muted("${shortDate(start.toLocalDate())}, ${start.toLocalTime().toString().take(5)}, from ${w.source}")
                }
                Text(fmtDur(((w.end - w.start) / 60000).toInt()), style = Type.label, color = p.ink2)
            }
            HairLine()
        }
    }
}

/* ---------------- Logger ---------------- */

@Composable
fun LoggerSheet(vm: AppViewModel, s: UiState, id: String, close: () -> Unit, edit: (String) -> Unit) {
    val p = LocalPalette.current
    val d = s.data
    val e = d.exercises.firstOrNull { it.id == id }
    if (e == null) {
        LaunchedEffect(id) { close() }
        return
    }
    val today = s.today.toString()
    val existing = remember(id) { d.sessions.firstOrNull { it.x == id && it.d == today } }
    val previous = remember(id) { sessionsOf(d, id).firstOrNull { it.d < today && sameWeight(it.w, e.w) } }
    var weight by remember(id) { mutableStateOf(fmtW(existing?.w ?: e.w)) }
    var reps by remember(id) {
        mutableStateOf(existing?.r?.map { it.toString() } ?: List(e.sets) { i -> (previous?.r?.getOrNull(i) ?: e.lo).toString() })
    }
    var makeWorking by remember(id) { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val step = if (e.inc > 0) e.inc else 2.5
    val w = weight.toDoubleOrNull() ?: 0.0
    val history = sessionsOf(d, id).take(6)

    Sheet(e.name, close) {
        Muted("${e.sets} \u00D7 ${e.lo}${if (e.hi > e.lo) "\u2013${e.hi}" else ""} at ${weightLabel(e, e.w, d.unit)}. Hit ${e.hi} on every set to move up.")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(80.dp)) {
                    Text(if (e.bw) "Added" else "Weight", style = Type.label, color = p.ink2)
                    Muted(d.unit, style = Type.small.copy(fontSize = 13.sp))
                }
                Stepper(weight, { weight = fmtW((w - step).coerceAtLeast(0.0)) }, { weight = fmtW(round2(w + step)) }, { weight = it }, decimal = true, label = "weight", modifier = Modifier.weight(1f))
            }
            if (!sameWeight(w, e.w)) CheckRow("Make ${weightLabel(e, w, d.unit)} my working weight", makeWorking) { makeWorking = it }
            reps.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(80.dp)) {
                        Text("Set ${i + 1}", style = Type.label, color = p.ink2)
                        Muted("reps", style = Type.small.copy(fontSize = 13.sp))
                    }
                    val n = r.toIntOrNull() ?: 0
                    Stepper(
                        r,
                        { reps = reps.toMutableList().also { it[i] = (n - 1).coerceAtLeast(0).toString() } },
                        { reps = reps.toMutableList().also { it[i] = (n + 1).coerceAtMost(100).toString() } },
                        { v -> reps = reps.toMutableList().also { it[i] = v } },
                        decimal = false, label = "reps in set ${i + 1}", modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reps.size < 12) PillButton("Add set", { reps = reps + (reps.lastOrNull() ?: e.lo.toString()) }, kind = Btn.Ghost, small = true)
                if (reps.size > 1) PillButton("Remove last set", { reps = reps.dropLast(1) }, kind = Btn.Ghost, small = true)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState())) {
            Text("Rest", style = Type.label, color = p.ink2)
            listOf(60 to "60 s", 90 to "90 s", 120 to "2 min", 180 to "3 min").forEach { (sec, label) -> Chip(label, false) { vm.startRest(sec) } }
        }
        PillButton(if (existing != null) "Update today\u2019s session" else "Save session", {
            vm.saveSession(id, w, reps.map { it.toIntOrNull() ?: 0 }, existing?.id, makeWorking)
            close()
        }, Modifier.fillMaxWidth().padding(top = 18.dp))
        if (existing != null) {
            PillButton("Delete today\u2019s session", { confirmDelete = true }, Modifier.fillMaxWidth().padding(top = 10.dp), kind = Btn.Danger)
        }
        Text("History", style = Type.heading, color = p.ink, modifier = Modifier.padding(top = 26.dp, bottom = 4.dp))
        if (history.isEmpty()) Muted("No sessions yet. Your first one sets the baseline.")
        val series = strengthSeries(d, e, 12)
        if (series.size >= 2) {
            Sparkline(series.map { it.second }, "Strength over the last ${series.size} sessions")
            Muted(if (history.any { it.w > 0 }) "Estimated one-rep max, one point per session" else "Total reps, one point per session", style = Type.small.copy(fontSize = 13.sp))
        }
        history.forEach { h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Text(shortDate(day(h.d)), style = Type.small, color = p.ink3, modifier = Modifier.weight(1f))
                Text(weightLabel(e, h.w, d.unit).replaceFirstChar { it.uppercase() }, style = Type.small, color = p.ink, modifier = Modifier.padding(end = 14.dp))
                Text(h.r.joinToString("/"), style = Type.small, color = p.ink)
            }
            HairLine()
        }
        PillButton("Edit exercise", { edit(id) }, Modifier.padding(top = 14.dp), kind = Btn.Ghost, small = true)
    }
    if (confirmDelete && existing != null) {
        Confirm("Delete today\u2019s session?", "The sets you logged for ${e.name} today will be removed.", "Delete", {
            vm.deleteSession(existing.id)
            close()
        }, { confirmDelete = false })
    }
}

/* ---------------- Exercise editor ---------------- */

@Composable
fun ExerciseEditor(vm: AppViewModel, s: UiState, id: String?, close: () -> Unit) {
    val d = s.data
    val existing = d.exercises.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var group by remember { mutableStateOf(existing?.group ?: "") }
    var sets by remember { mutableStateOf((existing?.sets ?: 3).toString()) }
    var lo by remember { mutableStateOf((existing?.lo ?: 8).toString()) }
    var hi by remember { mutableStateOf((existing?.hi ?: 12).toString()) }
    var w by remember { mutableStateOf(fmtW(existing?.w ?: 20.0)) }
    var inc by remember { mutableStateOf(fmtW(existing?.inc ?: 2.5)) }
    var bw by remember { mutableStateOf(existing?.bw ?: false) }
    var confirm by remember { mutableStateOf(false) }
    Sheet(if (existing == null) "New exercise" else "Edit exercise", close) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Exercise", name, { name = it }, placeholder = "Incline dumbbell press", max = 60)
            Field("Workout (optional)", group, { group = it }, placeholder = "Push", max = 30)
            val groups = d.exercises.map { it.group }.filter { it.isNotBlank() }.distinct()
            if (groups.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g -> Chip(g, g == group) { group = g } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Sets", sets, { sets = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                Field("Reps from", lo, { lo = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                Field("Reps to", hi, { hi = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            CheckRow("Bodyweight exercise, so the weight is extra load", bw) { bw = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Working weight (${d.unit})", w, { w = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                Field("Add each time (${d.unit})", inc, { inc = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            }
            Muted("When every set reaches the top of the rep range at your working weight, Compound tells you to add this much. Use 0 to move to a harder variation instead.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (existing != null) PillButton("Delete", { confirm = true }, kind = Btn.Danger)
                Spacer(Modifier.weight(1f))
                PillButton(if (existing == null) "Add exercise" else "Save exercise", {
                    if (name.isBlank()) vm.say("Give the exercise a name.") else {
                        var a = (lo.toIntOrNull() ?: 8).coerceIn(1, 100)
                        var b = (hi.toIntOrNull() ?: 12).coerceIn(1, 100)
                        if (a > b) a = b.also { b = a }
                        val ex = Exercise(
                            id = existing?.id ?: newId(), name = name.trim(), group = group.trim(),
                            sets = (sets.toIntOrNull() ?: 3).coerceIn(1, 10), lo = a, hi = b,
                            w = round2((w.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)), inc = round2((inc.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)),
                            bw = bw, since = existing?.since ?: s.today.toString(),
                        )
                        vm.saveExercise(ex)
                        vm.say(if (existing == null) "Exercise added." else "Exercise saved.")
                        close()
                    }
                })
            }
        }
    }
    if (confirm && existing != null) {
        Confirm("Delete this exercise?", "\u201C${existing.name}\u201D will leave your list. Logged sessions still count as votes.", "Delete", {
            vm.deleteExercise(existing.id)
            close()
        }, { confirm = false })
    }
}
