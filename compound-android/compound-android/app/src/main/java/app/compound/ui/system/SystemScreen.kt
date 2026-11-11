package app.compound.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.compound.AppViewModel
import app.compound.UiState
import app.compound.data.AppData
import app.compound.data.Goal
import app.compound.data.Habit
import app.compound.data.KIND_BREAK
import app.compound.data.KIND_BUILD
import app.compound.data.newId
import app.compound.logic.activeDays
import app.compound.logic.dayInitial
import app.compound.logic.daysText
import app.compound.logic.fmtDur
import app.compound.logic.isDone
import app.compound.logic.plural
import app.compound.logic.rate
import app.compound.logic.scheduledOn
import app.compound.logic.streak
import app.compound.logic.weekStart
import app.compound.ui.SheetReq
import app.compound.ui.components.Btn
import app.compound.ui.components.CheckRow
import app.compound.ui.components.Confirm
import app.compound.ui.components.DaysPicker
import app.compound.ui.components.Field
import app.compound.ui.components.HairLine
import app.compound.ui.components.Muted
import app.compound.ui.components.PillButton
import app.compound.ui.components.ProgressLine
import app.compound.ui.components.ScreenPadding
import app.compound.ui.components.SectionHeader
import app.compound.ui.components.Segmented
import app.compound.ui.components.Sheet
import app.compound.ui.components.TimeField
import app.compound.ui.sketch.Glyph
import app.compound.ui.sketch.NotebookArt
import app.compound.ui.sketch.SketchIcon
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import java.time.LocalDate
import java.util.Locale

private val SEG_NAMES = mapOf("morning" to "Morning", "afternoon" to "Afternoon", "evening" to "Evening", "any" to "Any time")

@Composable
fun SystemScreen(s: UiState, open: (SheetReq) -> Unit) {
    val p = LocalPalette.current
    val d = s.data
    val active = remember(d) { activeDays(d) }
    val building = d.habits.filter { it.kind != KIND_BREAK }
    val breaking = d.habits.filter { it.kind == KIND_BREAK }
    LazyColumn(contentPadding = ScreenPadding) {
        item { Text("Your system", style = Type.title, color = p.ink, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Column(Modifier.padding(top = 14.dp)) {
                Text("\u201C\u2026you fall to the level of your systems.\u201D", style = Type.quote.copy(fontSize = 22.sp), color = p.ink)
                Muted("James Clear, Atomic Habits", Modifier.padding(top = 4.dp))
                Text(
                    "Goals point the way. The system is what you do each day, so that\u2019s what Compound tracks and scores.",
                    style = Type.body, color = p.ink2, modifier = Modifier.padding(top = 12.dp),
                )
                if (active > 0) {
                    val factor = String.format(Locale.ROOT, "%.2f", Math.pow(1.01, active.toDouble()))
                    Muted("You\u2019ve shown up on ${plural(active, "day")}. At 1% better each time, that compounds to $factor\u00D7.", Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Goals") { PillButton("Add goal", { open(SheetReq.GoalEdit(null)) }, small = true) }
                if (d.goals.isEmpty()) Muted("A goal is a direction. Add one, then choose the habits that move you towards it.")
            }
        }
        items(d.goals, key = { it.id }) { g -> GoalRow(g, d, s.today) { open(SheetReq.GoalEdit(g.id)) } }
        item {
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Habits you\u2019re building") { PillButton("Add habit", { open(SheetReq.HabitEdit(null)) }, small = true) }
                if (building.isEmpty()) Muted("Start with something that takes less than two minutes.")
            }
        }
        items(building, key = { it.id }) { h -> HabitRow(h, d, s.today) { open(SheetReq.HabitEdit(h.id)) } }
        if (breaking.isNotEmpty()) {
            item { SectionHeader("Habits you\u2019re breaking", Modifier.padding(top = 26.dp)) }
            items(breaking, key = { it.id }) { h -> HabitRow(h, d, s.today) { open(SheetReq.HabitEdit(h.id)) } }
        }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Tools")
                ToolRow("Habits scorecard", "List what you already do each day and mark whether it helps.") { open(SheetReq.Scorecard) }
                HairLine()
                ToolRow("Week in review", if (d.reviews.isEmpty()) "Three questions to tune the system each week." else "${plural(d.reviews.size, "review")} so far. Open this week\u2019s.") { open(SheetReq.Review) }
            }
        }
    }
}

@Composable
private fun ToolRow(title: String, text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.item.copy(fontWeight = Type.label.fontWeight), color = p.ink)
            Muted(text)
        }
        SketchIcon(Glyph.Chevron, p.ink3, size = 20.dp)
    }
}

@Composable
private fun GoalRow(g: Goal, d: AppData, today: LocalDate, onClick: () -> Unit) {
    val p = LocalPalette.current
    val hs = g.habits.mapNotNull { id -> d.habits.firstOrNull { it.id == id } }
    val r = if (hs.isEmpty()) null else rate(d, hs, 14, today)
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp)) {
        Text(g.title, style = Type.item.copy(fontSize = 18.sp, fontWeight = Type.label.fontWeight), color = p.ink)
        if (g.why.isNotBlank()) Text(g.why, style = Type.small.copy(fontSize = 14.5.sp), color = p.ink2)
        if (hs.isEmpty()) {
            Muted("No system yet. Tap to link the habits that serve this goal.", Modifier.padding(top = 6.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                ProgressLine((r ?: 0.0).toFloat(), Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(if (r == null) "\u2013" else "${Math.round(r * 100)}%", style = Type.number.copy(fontSize = 24.sp), color = p.ink)
            }
            Muted("${if (r == null) "Your system starts today" else "System kept, last 14 days"}: ${hs.joinToString(", ") { it.name }}", Modifier.padding(top = 4.dp))
        }
    }
    HairLine()
}

@Composable
private fun HabitRow(h: Habit, d: AppData, today: LocalDate, onClick: () -> Unit) {
    val p = LocalPalette.current
    val run = streak(d, h, today)
    val r = rate(d, listOf(h), 30, today)
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(h.name, style = Type.item.copy(fontWeight = Type.label.fontWeight), color = p.ink)
                val when0 = buildString {
                    append(SEG_NAMES[h.seg] ?: "Morning")
                    if (h.time.isNotBlank()) append(" at ${h.time}")
                    if (h.dur > 0 && h.kind != KIND_BREAK) append(", ${fmtDur(h.dur)}")
                    append(", ${daysText(h.days)}")
                }
                Muted(when0)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$run", style = Type.number, color = p.ink)
                Muted(if (h.kind == KIND_BREAK) "days clean" else "in a row", style = Type.small.copy(fontSize = 12.sp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "Last seven days" }) {
                for (i in 6 downTo 0) {
                    val x = today.minusDays(i.toLong())
                    val sch = scheduledOn(h, x)
                    val dn = isDone(d, h.id, x)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            dn -> Box(Modifier.size(16.dp).clip(CircleShape).background(p.ink))
                            !sch -> Box(Modifier.padding(vertical = 7.dp).size(width = 10.dp, height = 2.dp).background(p.line2))
                            i == 0 -> Box(Modifier.size(16.dp).border(1.5.dp, p.ink3, CircleShape))
                            else -> Box(Modifier.size(16.dp).border(1.5.dp, p.warn, CircleShape))
                        }
                        Text(dayInitial(x.dayOfWeek.value), style = Type.small.copy(fontSize = 11.sp), color = p.ink3)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Muted(if (r == null) "New" else "${Math.round(r * 100)}% of the last 30 days", style = Type.small.copy(fontSize = 13.sp))
        }
    }
    HairLine()
}

/* ---------------- Habit editor: the four laws ---------------- */

@Composable
fun HabitEditor(vm: AppViewModel, s: UiState, id: String?, close: () -> Unit) {
    val p = LocalPalette.current
    val existing = s.data.habits.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: KIND_BUILD) }
    var seg by remember { mutableStateOf(existing?.seg ?: "morning") }
    var dur by remember { mutableStateOf((existing?.dur ?: 0).toString()) }
    var time by remember { mutableStateOf(existing?.time ?: "") }
    var days by remember { mutableStateOf(existing?.days ?: listOf(1, 2, 3, 4, 5, 6, 7)) }
    var cue by remember { mutableStateOf(existing?.cue ?: "") }
    var bundle by remember { mutableStateOf(existing?.bundle ?: "") }
    var prep by remember { mutableStateOf(existing?.prep ?: "") }
    var two by remember { mutableStateOf(existing?.two ?: "") }
    var reward by remember { mutableStateOf(existing?.reward ?: "") }
    var gym by remember { mutableStateOf(existing?.gym ?: false) }
    var confirm by remember { mutableStateOf(false) }
    val breaking = kind == KIND_BREAK

    Sheet(if (existing == null) "New habit" else "Edit habit", close) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Segmented(listOf(KIND_BUILD to "Build a habit", KIND_BREAK to "Break a habit"), kind, { kind = it })
            Field(if (breaking) "Habit to break" else "Habit", name, { name = it }, placeholder = if (breaking) "Scrolling in bed" else "Read 10 pages", max = 60)
            Column {
                Text("Part of the day", style = Type.label, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp))
                Segmented(SEG_NAMES.toList(), seg, { seg = it })
            }
            if (!breaking) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Minutes", dur, { dur = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                    TimeField("Fixed time", time, { time = it }, Modifier.weight(1f), clearable = true)
                }
                Muted("Leave the time empty and Compound finds a free slot in that part of the day. Use 0 minutes for quick ticks.")
            }
            Column {
                Text("Days", style = Type.label, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp))
                DaysPicker(days) { days = it }
            }
            Text(if (breaking) "Break it with the four laws, inverted" else "Design it with the four laws", style = Type.heading, color = p.ink, modifier = Modifier.padding(top = 8.dp))
            Field(if (breaking) "1. Make it invisible: remove the cue" else "1. Make it obvious: after I\u2026", cue, { cue = it },
                placeholder = if (breaking) "Phone charges in the kitchen" else "After I pour my coffee", max = 100)
            Field(if (breaking) "2. Make it unattractive: reframe it" else "2. Make it attractive: pair it with something you enjoy", bundle, { bundle = it },
                placeholder = if (breaking) "It steals tomorrow morning\u2019s energy" else "Only listen to my podcast while I do it", max = 100)
            Field(if (breaking) "3. Make it difficult: add friction" else "3. Make it easy: set up the space", prep, { prep = it },
                placeholder = if (breaking) "Log out of the app every night" else "Book on the pillow the night before", max = 100)
            if (!breaking) {
                Field("Two-minute version", two, { two = it }, placeholder = "Read one page", max = 80)
                Muted("On a busy day, or after a miss, Compound suggests this so the chain doesn\u2019t break.")
            }
            Field(if (breaking) "4. Make it unsatisfying: a cost or a witness" else "4. Make it satisfying: an immediate reward", reward, { reward = it },
                placeholder = if (breaking) "Tell Sam; a slip costs me a coffee" else "Ten minutes of guitar", max = 100)
            if (!breaking) CheckRow("Tick this when I log a workout or Garmin syncs one", gym) { gym = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                if (existing != null) PillButton("Delete", { confirm = true }, kind = Btn.Danger)
                Spacer(Modifier.weight(1f))
                PillButton(if (existing == null) "Add habit" else "Save habit", {
                    when {
                        name.isBlank() -> vm.say("Give the habit a name.")
                        days.isEmpty() -> vm.say("Pick at least one day.")
                        else -> {
                            val h = Habit(
                                id = existing?.id ?: newId(), name = name.trim(), kind = kind, seg = seg,
                                time = if (breaking) "" else time, dur = if (breaking) 0 else (dur.toIntOrNull() ?: 0).coerceIn(0, 600),
                                days = days.sorted(), cue = cue.trim(), bundle = bundle.trim(), prep = prep.trim(), two = two.trim(),
                                reward = reward.trim(), gym = gym && !breaking, created = existing?.created ?: s.today.toString(),
                            )
                            vm.saveHabit(h)
                            vm.say(if (existing == null) "Habit added." else "Habit saved.")
                            close()
                        }
                    }
                })
            }
        }
    }
    if (confirm && existing != null) {
        Confirm("Delete this habit?", "\u201C${existing.name}\u201D will leave your routine. Ticks you\u2019ve already made still count as votes.", "Delete habit", {
            vm.deleteHabit(existing.id)
            close()
        }, { confirm = false })
    }
}

/* ---------------- Goal editor ---------------- */

@Composable
fun GoalEditor(vm: AppViewModel, s: UiState, id: String?, close: () -> Unit, openWhy: (String) -> Unit) {
    val existing = s.data.goals.firstOrNull { it.id == id }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var why by remember { mutableStateOf(existing?.why ?: "") }
    var linked by remember { mutableStateOf(existing?.habits ?: emptyList()) }
    var confirm by remember { mutableStateOf(false) }
    Sheet(if (existing == null) "New goal" else "Edit goal", close) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Goal", title, { title = it }, placeholder = "Run a half marathon", max = 80)
            Field("Why it matters", why, { why = it }, placeholder = "To prove to myself I can", max = 140)
            Column {
                Text("The system: habits that move you towards it", style = Type.label, color = LocalPalette.current.ink2)
                if (s.data.habits.isEmpty()) Muted("Add habits first, then link them here.")
                s.data.habits.forEach { h -> CheckRow(h.name, h.id in linked) { on -> linked = if (on) linked + h.id else linked - h.id } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (existing != null) PillButton("Delete", { confirm = true }, kind = Btn.Danger)
                Spacer(Modifier.weight(1f))
                PillButton(if (existing == null) "Add goal" else "Save goal", {
                    if (title.isBlank()) vm.say("Give the goal a name.") else {
                        val g = (existing ?: Goal(newId(), title.trim(), created = s.today.toString())).copy(title = title.trim(), why = why.trim(), habits = linked)
                        vm.saveGoal(g)
                        if (existing == null) openWhy(g.id) else close()
                    }
                })
            }
            if (existing == null) Muted("Next, you\u2019ll write down why this matters, so Compound can remind you on the hard days.")
        }
    }
    if (confirm && existing != null) {
        Confirm("Delete this goal?", "\u201C${existing.title}\u201D and its reasons will be removed. Its habits stay in your routine.", "Delete goal", {
            vm.deleteGoal(existing.id)
            close()
        }, { confirm = false })
    }
}

/* ---------------- Habits scorecard ---------------- */

@Composable
fun ScorecardSheet(vm: AppViewModel, s: UiState, close: () -> Unit) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf("") }
    Sheet("Habits scorecard", close) {
        NotebookArt(Modifier.padding(horizontal = 30.dp, vertical = 4.dp))
        Text(
            "Write down what you already do in a normal day, in order. Then mark each one: does it help the person you want to be, work against them, or neither? Awareness comes before change.",
            style = Type.body, color = p.ink2,
        )
        Spacer(Modifier.height(12.dp))
        s.data.scorecard.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(item.text, style = Type.item, color = p.ink, modifier = Modifier.weight(1f))
                listOf(1 to "+", 0 to "=", -1 to "\u2212").forEach { (v, label) ->
                    val on = item.rating == v
                    val c = when (v) { 1 -> p.moss; -1 -> p.warn; else -> p.ink3 }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape)
                            .background(if (on) c else Color.Transparent).border(1.5.dp, c, CircleShape)
                            .clickable(onClickLabel = when (v) { 1 -> "Helps"; -1 -> "Works against me"; else -> "Neutral" }) { vm.rateScore(item.id, v) },
                    ) { Text(label, style = Type.label.copy(fontSize = 18.sp), color = if (on) p.paper else c) }
                }
                Box(Modifier.padding(start = 4.dp).size(36.dp).clip(CircleShape).clickable(onClickLabel = "Remove ${item.text}") { vm.deleteScore(item.id) }, contentAlignment = Alignment.Center) {
                    SketchIcon(Glyph.Close, p.ink3, size = 14.dp)
                }
            }
            HairLine()
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 12.dp)) {
            Field("", text, { text = it }, Modifier.weight(1f), placeholder = "Make coffee", max = 80)
            Spacer(Modifier.width(8.dp))
            PillButton("Add", { vm.addScore(text); text = "" }, small = true)
        }
        val plus = s.data.scorecard.count { it.rating > 0 }
        val minus = s.data.scorecard.count { it.rating < 0 }
        if (s.data.scorecard.isNotEmpty()) Muted("$plus helping, $minus working against you. Pick one minus to turn into a habit to break.", Modifier.padding(top = 10.dp))
    }
}

/* ---------------- Weekly review ---------------- */

@Composable
fun ReviewSheet(vm: AppViewModel, s: UiState, close: () -> Unit) {
    val p = LocalPalette.current
    val week = weekStart(s.today).toString()
    val current = s.data.reviews.firstOrNull { it.week == week }
    var well by remember { mutableStateOf(current?.well ?: "") }
    var hard by remember { mutableStateOf(current?.hard ?: "") }
    var change by remember { mutableStateOf(current?.change ?: "") }
    val r = rate(s.data, s.data.habits, 7, s.today)
    Sheet("Week in review", close) {
        Muted(if (r == null) "No habits were due this week." else "Your system ran at ${Math.round(r * 100)}% over the last seven days.")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("What went well?", well, { well = it }, singleLine = false, minLines = 2, max = 400)
            Field("What got in the way?", hard, { hard = it }, singleLine = false, minLines = 2, max = 400)
            Field("One small change for next week", change, { change = it }, singleLine = false, minLines = 2, max = 400,
                placeholder = "Move reading to the train ride")
            PillButton("Save review", { vm.saveReview(well, hard, change); close() }, Modifier.fillMaxWidth())
        }
        val past = s.data.reviews.filter { it.week != week }.take(6)
        if (past.isNotEmpty()) {
            Text("Earlier weeks", style = Type.heading, color = p.ink, modifier = Modifier.padding(top = 26.dp, bottom = 6.dp))
            past.forEach { rv ->
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text("Week of ${LocalDate.parse(rv.week).let { app.compound.logic.shortDate(it) }}", style = Type.label, color = p.ink2)
                    if (rv.change.isNotBlank()) Text("Change: ${rv.change}", style = Type.body, color = p.ink)
                    if (rv.well.isNotBlank()) Muted("Went well: ${rv.well}")
                }
                HairLine()
            }
        }
    }
}
