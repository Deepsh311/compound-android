package app.compound.ui.why

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.compound.AppViewModel
import app.compound.UiState
import app.compound.data.AppData
import app.compound.data.Goal
import app.compound.logic.rate
import app.compound.ui.SheetReq
import app.compound.ui.Tab
import app.compound.ui.components.Btn
import app.compound.ui.components.Field
import app.compound.ui.components.HairLine
import app.compound.ui.components.Muted
import app.compound.ui.components.Note
import app.compound.ui.components.PillButton
import app.compound.ui.components.ProgressLine
import app.compound.ui.components.ScreenPadding
import app.compound.ui.components.SectionHeader
import app.compound.ui.components.Sheet
import app.compound.ui.sketch.Glyph
import app.compound.ui.sketch.PathArt
import app.compound.ui.sketch.SketchIcon
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import java.time.LocalDate

/** One line per reason you wrote down, so the "reason of the day" can rotate through all of them. */
private fun reasons(d: AppData): List<Pair<Goal, String>> = d.goals.flatMap { g ->
    listOfNotNull(
        g.why.takeIf { it.isNotBlank() },
        g.becoming.takeIf { it.isNotBlank() }?.let { "This makes me ${it.trimEnd('.')}." },
        g.stakes.takeIf { it.isNotBlank() }?.let { "If I stop: $it" },
    ).map { g to it }
}

@Composable
fun WhyScreen(vm: AppViewModel, s: UiState, open: (SheetReq) -> Unit, goTo: (Tab) -> Unit) {
    val p = LocalPalette.current
    val d = s.data
    val all = remember(d) { reasons(d) }
    var shift by rememberSaveable { mutableIntStateOf(0) }
    val todayReason = all.getOrNull(Math.floorMod(s.today.toEpochDay() + shift, all.size.coerceAtLeast(1).toLong()).toInt())

    LazyColumn(contentPadding = ScreenPadding) {
        item { Text("Why", style = Type.title, color = p.ink, modifier = Modifier.padding(top = 8.dp)) }
        item { PathArt(Modifier.padding(top = 10.dp)) }
        item {
            Text(
                "Motivation fades; reasons last. Write down why each goal matters, and Compound brings it back on the days you need it.",
                style = Type.body, color = p.ink2, modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (todayReason != null) {
            item {
                Column(Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(18.dp)) {
                    Text("Today\u2019s reason: ${todayReason.first.title}", style = Type.label, color = p.ink2)
                    Text(todayReason.second, style = Type.quote.copy(fontSize = 22.sp, lineHeight = 30.sp), color = p.ink, modifier = Modifier.padding(top = 6.dp))
                    if (all.size > 1) {
                        Text(
                            "Show another", style = Type.small, color = p.ink2,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { shift++ }.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
        if (d.identity.isNotBlank()) {
            item {
                Note(Modifier.padding(top = 18.dp), accent = p.moss) {
                    Text("Who you\u2019re becoming", style = Type.label, color = p.ink2)
                    Text("I\u2019m someone who ${d.identity}.", style = Type.body, color = p.ink)
                }
            }
        }
        item {
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Your goals") { PillButton("Add goal", { open(SheetReq.GoalEdit(null)) }, small = true) }
                if (d.goals.isEmpty()) Muted("Add a goal, then write down why it matters to you.")
            }
        }
        items(d.goals, key = { it.id }) { g -> GoalWhy(g, d, s.today) { open(SheetReq.WhyEdit(g.id)) } }
        item {
            val r = d.reminders
            Column(Modifier.padding(top = 26.dp)) {
                SectionHeader("Reminders")
                Muted(
                    if (r.why) "Compound reminds you of one of your reasons every day at ${r.whyAt}. Change it in Settings."
                    else "Turn on a daily reminder of your reasons in Settings.",
                )
            }
        }
    }
}

@Composable
private fun GoalWhy(g: Goal, d: AppData, today: LocalDate, onEdit: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var openLetter by rememberSaveable(g.id) { mutableStateOf(false) }
    val hs = g.habits.mapNotNull { id -> d.habits.firstOrNull { it.id == id } }
    val r = if (hs.isEmpty()) null else rate(d, hs, 14, today)
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.title, style = Type.item.copy(fontSize = 18.sp, fontWeight = Type.label.fontWeight), color = p.ink, modifier = Modifier.weight(1f))
            PillButton("Edit", onEdit, kind = Btn.Ghost, small = true)
        }
        if (r != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                ProgressLine(r.toFloat(), Modifier.weight(1f), height = 6.dp, color = p.moss)
                Text("  ${Math.round(r * 100)}% of the system kept", style = Type.small.copy(fontSize = 13.sp), color = p.ink2)
            }
        }
        val rows = listOf(
            "Why it matters" to g.why,
            "This makes me" to g.becoming.replaceFirstChar { it.uppercase() },
            "If I stop" to g.stakes,
        ).filter { it.second.isNotBlank() }
        if (rows.isEmpty()) Muted("No reasons yet. Tap Edit and write down why this matters.", Modifier.padding(top = 6.dp))
        rows.forEach { (label, text) ->
            Column(Modifier.padding(top = 10.dp)) {
                Text(label, style = Type.label.copy(fontSize = 13.sp), color = p.ink3)
                Text(text, style = Type.body, color = p.ink)
            }
        }
        if (g.letter.isNotBlank()) {
            Text(
                if (openLetter) "Hide note to future me" else "Read note to future me", style = Type.small, color = p.ink2,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { openLetter = !openLetter }.padding(vertical = 6.dp),
            )
            if (openLetter) Note(Modifier.padding(top = 4.dp)) { Text(g.letter, style = Type.quote.copy(fontSize = 17.sp, lineHeight = 25.sp), color = p.ink) }
        }
        if (g.promise.isNotBlank()) {
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface).padding(14.dp)) {
                Text("Habit contract", style = Type.label, color = p.ink2)
                Text(g.promise, style = Type.body, color = p.ink, modifier = Modifier.padding(top = 4.dp))
                if (g.consequence.isNotBlank()) Muted("If I break it: ${g.consequence}")
                if (g.partner.isNotBlank()) Muted("Witness: ${g.partner}")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button) {
                    val text = buildString {
                        append("My habit contract for \u201C${g.title}\u201D\n\n")
                        append(g.promise)
                        if (g.consequence.isNotBlank()) append("\n\nIf I break it: ${g.consequence}")
                        if (g.partner.isNotBlank()) append("\n\n${g.partner}, will you hold me to it?")
                    }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your contract"))
                }.padding(vertical = 6.dp, horizontal = 2.dp)) {
                    SketchIcon(Glyph.Share, p.ink, size = 18.dp)
                    Text("  Send it to ${g.partner.ifBlank { "someone" }}", style = Type.label, color = p.ink)
                }
            }
        }
    }
    HairLine()
}

@Composable
fun WhyEditor(vm: AppViewModel, s: UiState, id: String, close: () -> Unit) {
    val g = s.data.goals.firstOrNull { it.id == id } ?: return
    var why by remember { mutableStateOf(g.why) }
    var becoming by remember { mutableStateOf(g.becoming) }
    var stakes by remember { mutableStateOf(g.stakes) }
    var letter by remember { mutableStateOf(g.letter) }
    var promise by remember { mutableStateOf(g.promise) }
    var partner by remember { mutableStateOf(g.partner) }
    var consequence by remember { mutableStateOf(g.consequence) }
    Sheet("Why: ${g.title}", close) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Why does this matter to you?", why, { why = it }, singleLine = false, minLines = 2, placeholder = "Be strong for life", max = 200)
            Field("This makes me\u2026", becoming, { becoming = it }, placeholder = "someone who shows up, whatever the weather", max = 120)
            Field("What happens if I stop?", stakes, { stakes = it }, singleLine = false, minLines = 2, placeholder = "I\u2019ll lose the progress and the confidence that came with it", max = 200)
            Field("A note to future me, for a hard day", letter, { letter = it }, singleLine = false, minLines = 3, max = 600,
                placeholder = "You won\u2019t feel like it today. Do the two-minute version and leave.")
            Text("Habit contract", style = Type.heading, color = LocalPalette.current.ink, modifier = Modifier.padding(top = 6.dp))
            Muted("A promise is easier to keep when someone else knows about it, and breaking it costs something.")
            Field("My promise", promise, { promise = it }, singleLine = false, minLines = 2, placeholder = "I\u2019ll train four times a week until the end of term", max = 300)
            Field("If I break it", consequence, { consequence = it }, placeholder = "I buy Sam lunch", max = 120)
            Field("Witness", partner, { partner = it }, placeholder = "Sam", max = 40)
            PillButton("Save", {
                vm.saveGoal(g.copy(why = why.trim(), becoming = becoming.trim().removeSuffix("."), stakes = stakes.trim(), letter = letter.trim(),
                    promise = promise.trim(), partner = partner.trim(), consequence = consequence.trim()))
                vm.say("Saved. Compound will bring it back when you need it.")
                close()
            }, Modifier.fillMaxWidth())
        }
    }
}
