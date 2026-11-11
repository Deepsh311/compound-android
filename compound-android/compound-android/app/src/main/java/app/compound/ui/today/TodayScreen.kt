package app.compound.ui.today

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.compound.AppViewModel
import app.compound.UiState
import app.compound.data.AppData
import app.compound.data.KIND_BREAK
import app.compound.data.quoteFor
import app.compound.logic.DayPlan
import app.compound.logic.ItemKind
import app.compound.logic.PlanItem
import app.compound.logic.SegmentPlan
import app.compound.logic.dailyNudge
import app.compound.logic.fmtDur
import app.compound.logic.fromMin
import app.compound.logic.greeting
import app.compound.logic.isDone
import app.compound.logic.longDate
import app.compound.logic.missedLast
import app.compound.logic.planDay
import app.compound.logic.plural
import app.compound.logic.streak
import app.compound.logic.votes
import app.compound.logic.weekStart
import app.compound.ui.SheetReq
import app.compound.ui.Tab
import app.compound.ui.components.Btn
import app.compound.ui.components.CheckCircle
import app.compound.ui.components.HairLine
import app.compound.ui.components.IconBtn
import app.compound.ui.components.MarkedText
import app.compound.ui.components.Muted
import app.compound.ui.components.Note
import app.compound.ui.components.PillButton
import app.compound.ui.components.ProgressLine
import app.compound.ui.components.ScreenPadding
import app.compound.ui.components.SectionHeader
import app.compound.ui.components.SegBar
import app.compound.ui.components.Tally
import app.compound.ui.sketch.Glyph
import app.compound.ui.sketch.SeedlingArt
import app.compound.ui.sketch.SketchIcon
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun TodayScreen(vm: AppViewModel, s: UiState, open: (SheetReq) -> Unit, openSettings: () -> Unit, goTo: (Tab) -> Unit) {
    val p = LocalPalette.current
    val d = s.data
    val plan = remember(d, s.today, s.nowMinute, s.events) { planDay(d, s.today, s.events, s.nowMinute, isToday = true) }
    val v = remember(d, s.today) { votes(d, s.today) }
    val shift by vm.quoteShift.collectAsStateWithLifecycle()
    val tick by vm.voteTick.collectAsStateWithLifecycle()
    val quote = quoteFor(s.today, shift)
    val nudge = remember(d, plan, s.nowMinute / 30) { dailyNudge(d, plan, s.today, s.nowMinute) }
    val reasons = d.goals.filter { it.why.isNotBlank() }
    val reason = reasons.getOrNull(Math.floorMod(s.today.toEpochDay(), reasons.size.coerceAtLeast(1).toLong()).toInt())
    val reviewDue = s.today.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
        d.reviews.none { it.week == weekStart(s.today).toString() }

    LazyColumn(contentPadding = ScreenPadding) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(longDate(s.today), style = Type.small.copy(fontSize = 15.sp), color = p.ink2, modifier = Modifier.weight(1f))
                if (plan.total > 0) {
                    Text("${plan.done} of ${plan.total} done", style = Type.small, color = p.ink2)
                    Spacer(Modifier.width(8.dp))
                    ProgressLine(plan.done / plan.total.toFloat(), Modifier.width(52.dp), height = 6.dp)
                    Spacer(Modifier.width(10.dp))
                }
                IconBtn(Glyph.Settings, "Settings", openSettings)
            }
        }
        item {
            val first = d.name.trim().substringBefore(' ')
            Text("${greeting(s.nowMinute / 60)}${if (first.isNotEmpty()) ", $first" else ""}.", style = Type.greet, color = p.ink, modifier = Modifier.padding(top = 10.dp))
        }
        item {
            Column(Modifier.padding(top = 14.dp)) {
                Text("\u201C${quote.text}\u201D", style = Type.quote, color = p.ink)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Muted(quote.author, Modifier.weight(1f))
                    Text(
                        "Another quote", style = Type.small, color = p.ink2,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { vm.nextQuote() }.padding(8.dp),
                    )
                }
            }
        }
        item {
            Note(Modifier.padding(top = 14.dp)) {
                Text("Today\u2019s nudge", style = Type.label, color = p.ink2)
                Text(nudge, style = Type.body, color = p.ink, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (reason != null) {
            item {
                Note(Modifier.padding(top = 14.dp).clickable(role = Role.Button) { goTo(Tab.Why) }, accent = p.moss) {
                    Text("Remember why: ${reason.title}", style = Type.label, color = p.ink2)
                    Text(reason.why, style = Type.body, color = p.ink, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        item { IdentityBlock(d, v.week, v.today, tick, onWrite = openSettings) }
        if (reviewDue) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Week in review", style = Type.label, color = p.ink)
                        Muted("Three questions. Two minutes. Adjust the system, not your ambition.")
                    }
                    Spacer(Modifier.width(10.dp))
                    PillButton("Start", { open(SheetReq.Review) }, small = true)
                }
            }
        }
        item { TopThree(vm, d, s.today) }
        if (d.habits.isEmpty()) {
            item {
                Column(Modifier.padding(top = 24.dp)) {
                    SeedlingArt(Modifier.padding(horizontal = 24.dp))
                    Text("Your routine is empty. Add the habits that make up your day and Compound will fit them around your commitments.", style = Type.body, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
                    PillButton("Add a habit", { open(SheetReq.HabitEdit(null)) }, Modifier.padding(top = 12.dp))
                }
            }
        }
        if (plan.allDay.isNotEmpty()) {
            item { Text("All day: ${plan.allDay.joinToString(", ")}", style = Type.small, color = p.ink2, modifier = Modifier.padding(top = 24.dp)) }
        }
        items(plan.segments, key = { it.id }) { seg -> SegmentBlock(seg, d, s.today, vm, goTo) }
        if (plan.anytime.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 28.dp)) {
                    SectionHeader("Any time today")
                    plan.anytime.forEach { PlanRow(it, d, s.today, vm, goTo) }
                }
            }
        }
        if (!d.calendar.enabled && d.commits.isEmpty()) {
            item {
                Muted(
                    "Connect your phone calendar in Settings and Compound will plan your habits around lectures, shifts and plans.",
                    Modifier.padding(top = 28.dp).clickable(role = Role.Button, onClick = openSettings),
                )
            }
        }
    }
}

@Composable
private fun IdentityBlock(d: AppData, week: Int, today: Int, tick: Int, onWrite: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 26.dp)) {
        HairLine()
        Spacer(Modifier.height(20.dp))
        if (d.identity.isNotBlank()) {
            val idn = d.identity
            Text("I\u2019m someone who $idn${if (idn.last() in ".!?") "" else "."}", style = Type.display, color = p.ink)
        } else {
            Text("Who are you becoming?", style = Type.display, color = p.ink3)
            PillButton("Write it down", onWrite, Modifier.padding(top = 10.dp), kind = Btn.Ghost, small = true)
        }
        if (week > 0) Tally(week, today, tick, Modifier.padding(top = 16.dp))
        Text(
            if (week > 0) "${plural(week, "vote")} this week for the person you\u2019re becoming.${if (today > 0) " $today of them today." else ""}"
            else "No votes yet this week. Every habit you tick and every set you log is a vote for who you\u2019re becoming.",
            style = Type.small.copy(fontSize = 14.5.sp), color = p.ink2, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TopThree(vm: AppViewModel, d: AppData, today: LocalDate) {
    val p = LocalPalette.current
    val pris = d.days[today.toString()]?.p.orEmpty()
    val yesterday = d.days[today.minusDays(1).toString()]?.p.orEmpty().filter { !it.x }
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(top = 28.dp)) {
        SectionHeader("Top three today") { if (pris.isNotEmpty()) Muted("${pris.count { it.x }} of ${pris.size}") }
        if (pris.isEmpty()) Muted("What would make today a win? Write down up to three things.")
        pris.forEach { pr ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                CheckCircle(pr.x, pr.t, { vm.togglePriority(pr.id) })
                MarkedText(pr.t, pr.x, Type.item, Modifier.weight(1f).padding(start = 4.dp).clickable(role = Role.Checkbox) { vm.togglePriority(pr.id) })
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = "Remove ${pr.t}") { vm.deletePriority(pr.id) }, contentAlignment = Alignment.Center) {
                    SketchIcon(Glyph.Close, p.ink3, size = 16.dp)
                }
            }
        }
        if (pris.size < 3) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(90) },
                    placeholder = { Text("Add a priority", style = Type.body, color = p.ink3) },
                    singleLine = true,
                    textStyle = Type.body.copy(color = p.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addPriority(text); text = "" }),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = p.raised, unfocusedContainerColor = p.raised,
                        focusedBorderColor = p.ink, unfocusedBorderColor = p.line2, cursorColor = p.ink,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                PillButton("Add", { vm.addPriority(text); text = "" }, small = true)
            }
        }
        if (pris.isEmpty() && yesterday.isNotEmpty()) {
            Text(
                "Bring over yesterday\u2019s unfinished ones", style = Type.small, color = p.ink2,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { vm.carryOver() }.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SegmentBlock(seg: SegmentPlan, d: AppData, today: LocalDate, vm: AppViewModel, goTo: (Tab) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 28.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(seg.name, style = Type.heading, color = p.ink)
            if (seg.isNow) {
                Text(
                    "Now", style = Type.label.copy(fontSize = 12.sp), color = p.hiInk,
                    modifier = Modifier.padding(start = 8.dp, bottom = 3.dp).clip(RoundedCornerShape(50)).background(p.hi).padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Muted("${fromMin(seg.start)}\u2013${fromMin(seg.end)}")
        }
        SegBar(seg.bar, if (seg.isNow) seg.nowFraction else null, Modifier.padding(top = 8.dp, bottom = 4.dp))
        Muted(if (seg.free > 0) "${fmtDur(seg.free)} free" else "Fully booked", style = Type.small.copy(fontSize = 13.5.sp))
        if (seg.items.isEmpty()) Muted("Nothing planned.", Modifier.padding(top = 6.dp))
        seg.items.forEachIndexed { i, it ->
            PlanRow(it, d, today, vm, goTo)
            if (i < seg.items.lastIndex) HairLine()
        }
    }
}

@Composable
private fun PlanRow(item: PlanItem, d: AppData, today: LocalDate, vm: AppViewModel, goTo: (Tab) -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(item.start?.let { fromMin(it) } ?: "", style = Type.small.copy(fontSize = 13.5.sp), color = p.ink3, modifier = Modifier.width(46.dp).padding(top = 12.dp))
        if (item.kind != ItemKind.HABIT) {
            Box(Modifier.width(44.dp).height(44.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(if (item.kind == ItemKind.FIXED) p.ink3 else if (item.kind == ItemKind.WORKOUT) p.moss else p.ink))
            }
            Column(Modifier.weight(1f).padding(top = 10.dp, start = 4.dp)) {
                Text(item.title, style = Type.item.copy(fontWeight = Type.label.fontWeight), color = p.ink)
                val range = if (item.start != null && item.end != null) "${fromMin(item.start)}\u2013${fromMin(item.end)}" else ""
                Muted(listOf(range, item.detail).filter { it.isNotBlank() }.joinToString(", "))
            }
            return@Row
        }
        val h = item.habit ?: return@Row
        val done = isDone(d, h.id, today)
        val breaking = h.kind == KIND_BREAK
        val run = streak(d, h, today)
        val missed = !done && missedLast(d, h, today)
        CheckCircle(done, if (breaking) "Avoided ${h.name}" else h.name, { vm.toggleHabit(h.id) })
        Column(Modifier.weight(1f).padding(top = 10.dp, start = 4.dp).clickable(role = Role.Checkbox) { vm.toggleHabit(h.id) }) {
            MarkedText(if (breaking) "Avoid: ${h.name}" else h.name, done, Type.item)
            val meta = buildList {
                if (h.dur > 0 && !breaking) add(fmtDur(h.dur))
                if (run >= 2) add(if (breaking) "avoided $run days running" else "$run in a row")
            }
            if (meta.isNotEmpty()) Muted(meta.joinToString(", "))
            if (h.cue.isNotBlank() && !done) Text(h.cue, style = Type.small.copy(fontStyle = Type.quote.fontStyle), color = p.ink3)
            if (missed) {
                Text(
                    "Don\u2019t miss twice", style = Type.label.copy(fontSize = 12.5.sp), color = p.warn,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(p.warn.copy(alpha = 0.1f)).padding(horizontal = 9.dp, vertical = 2.dp),
                )
                if (h.two.isNotBlank() && !breaking) Muted("Two-minute version: ${h.two}", Modifier.padding(top = 2.dp))
            }
            if (item.noSlot && !done) Text("No free slot left today${if (h.two.isNotBlank() && !missed) ". Two-minute version: ${h.two}" else ""}", style = Type.small, color = p.warn)
            if (item.clash.isNotBlank() && !done) Text("Overlaps ${item.clash}", style = Type.small, color = p.warn)
            if (h.gym && !done) PillButton("Log workout", { goTo(Tab.Gym) }, Modifier.padding(top = 8.dp), kind = Btn.Ghost, small = true)
        }
    }
}
