package app.compound.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.compound.UiState
import app.compound.data.AppData
import app.compound.logic.activeDays
import app.compound.logic.bestStreak
import app.compound.logic.dailyFractions
import app.compound.logic.fmtDur
import app.compound.logic.fmtW
import app.compound.logic.plural
import app.compound.logic.rate
import app.compound.logic.shortDate
import app.compound.logic.strengthSeries
import app.compound.logic.votes
import app.compound.logic.weekStart
import app.compound.logic.weeklyRates
import app.compound.logic.weeklyVolume
import app.compound.logic.weeklyVotes
import app.compound.logic.weeklyWorkoutMinutes
import app.compound.ui.components.BarChart
import app.compound.ui.components.ChartSeries
import app.compound.ui.components.Chip
import app.compound.ui.components.Heatmap
import app.compound.ui.components.LineChart
import app.compound.ui.components.Muted
import app.compound.ui.components.ScreenPadding
import app.compound.ui.components.SectionHeader
import app.compound.ui.components.Sparkline
import app.compound.ui.components.dashed
import app.compound.ui.sketch.PlateauArt
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import java.time.LocalDate
import kotlin.math.roundToInt

private const val WEEKS = 12

@Composable
fun ProgressScreen(s: UiState) {
    val p = LocalPalette.current
    val d = s.data
    val today = s.today
    val labels = remember(today) { listOf(shortDate(weekStart(today).minusWeeks(WEEKS - 1L)), "This week") }
    LazyColumn(contentPadding = ScreenPadding) {
        item { Text("Progress", style = Type.title, color = p.ink, modifier = Modifier.padding(top = 8.dp)) }
        item { Stats(d, today) }
        item {
            Column(Modifier.padding(top = 22.dp)) {
                PlateauArt()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Legend(p.ink3, "What we expect", dashed = true)
                    Legend(p.ink, "What usually happens")
                }
                Text(
                    "Results lag behind effort at first. The shaded stretch is where most people give up. Keep the system running and the curve bends upward.",
                    style = Type.small.copy(fontSize = 14.5.sp), color = p.ink2, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { Consistency(d, today, labels) }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Every day, last 12 weeks")
                Muted("Each square is a day. Darker means more of that day\u2019s habits were done; an outline means all of them.")
                val days = remember(d, today) { dailyFractions(d, WEEKS * 7 - (6 - (today.dayOfWeek.value - 1)), today) }
                Heatmap(days, "Daily habit completion for the last 12 weeks", Modifier.padding(top = 12.dp))
            }
        }
        item {
            Column(Modifier.padding(top = 30.dp)) {
                SectionHeader("Votes per week")
                val vs = remember(d, today) { weeklyVotes(d, WEEKS, today) }
                Muted("Ticked habits, finished priorities and logged exercises.")
                BarChart(vs.map { it.toDouble() }, labels, "Votes per week", { "${it.roundToInt()}" }, Modifier.padding(top = 12.dp))
            }
        }
        item { Strength(d, today, labels) }
        if (d.garmin.enabled || d.workouts.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 30.dp)) {
                    SectionHeader("Training minutes")
                    val mins = remember(d, today) { weeklyWorkoutMinutes(d, WEEKS, today) }
                    Muted("Workouts from Garmin Connect via Health Connect, per week.")
                    if (mins.all { it == 0 }) Muted("Nothing synced yet.", Modifier.padding(top = 8.dp))
                    else BarChart(mins.map { it.toDouble() }, labels, "Training minutes per week", { fmtDur(it.roundToInt()) }, Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String, dashed: Boolean = false) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
            if (dashed) dashed(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), color, 2.dp.toPx())
            else drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), StrokeCap.Round)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.small.copy(fontSize = 12.5.sp), color = p.ink2)
    }
}

@Composable
private fun Stats(d: AppData, today: LocalDate) {
    val p = LocalPalette.current
    val v = votes(d, today)
    val best = bestStreak(d, today)
    val r = rate(d, d.habits, 30, today)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat("${v.week}", "votes this week", Modifier.weight(1f))
        Stat(if (r == null) "\u2013" else "${(r * 100).roundToInt()}%", "kept, 30 days", Modifier.weight(1f))
        Stat("${best?.second ?: 0}", if (best != null) "best run: ${best.first}" else "best run", Modifier.weight(1f))
    }
    Muted("${plural(activeDays(d), "day")} with at least one vote since you started.", Modifier.padding(top = 10.dp))
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(value, style = Type.number.copy(fontSize = 34.sp, lineHeight = 34.sp), color = p.ink)
        Text(label, style = Type.small.copy(fontSize = 12.5.sp), color = p.ink2, maxLines = 2)
    }
}

@Composable
private fun Consistency(d: AppData, today: LocalDate, labels: List<String>) {
    val p = LocalPalette.current
    var goalId by rememberSaveable { mutableStateOf("all") }
    val palette = listOf(p.ink, p.moss, Color(0xFFC9A227), p.warn)
    Column(Modifier.padding(top = 30.dp)) {
        SectionHeader("Goal consistency")
        Muted("How much of each goal\u2019s system you kept, week by week.")
        if (d.goals.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All habits", goalId == "all") { goalId = "all" }
                d.goals.forEach { g -> Chip(g.title, goalId == g.id) { goalId = g.id } }
            }
        }
        val series = remember(d, today, goalId) {
            if (goalId == "all") {
                listOf(ChartSeries("All habits", weeklyRates(d, d.habits, WEEKS, today), p.ink))
            } else {
                val g = d.goals.firstOrNull { it.id == goalId }
                val hs = g?.habits?.mapNotNull { id -> d.habits.firstOrNull { it.id == id } }.orEmpty()
                listOf(ChartSeries(g?.title ?: "", weeklyRates(d, hs, WEEKS, today), p.ink)) +
                    hs.take(3).mapIndexed { i, h -> ChartSeries(h.name, weeklyRates(d, listOf(h), WEEKS, today), palette[(i + 1) % palette.size]) }
            }
        }
        if (series.all { s -> s.values.all { it == null } }) {
            Muted("Nothing to chart yet. Tick a few habits and come back.", Modifier.padding(top = 10.dp))
        } else {
            LineChart(series, labels, "Weekly consistency", Modifier.padding(top = 12.dp))
            if (series.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    series.forEachIndexed { i, s -> Legend(s.color, if (i == 0) "Whole system" else s.name) }
                }
            }
        }
    }
}

@Composable
private fun Strength(d: AppData, today: LocalDate, labels: List<String>) {
    val p = LocalPalette.current
    val withData = d.exercises.filter { e -> d.sessions.any { it.x == e.id } }
    var pick by rememberSaveable { mutableStateOf(withData.firstOrNull()?.id ?: "") }
    Column(Modifier.padding(top = 30.dp)) {
        SectionHeader("Strength")
        if (withData.isEmpty()) {
            Muted("Log a few sessions in Gym and your lifts will chart here.")
            return@Column
        }
        if (withData.none { it.id == pick }) pick = withData.first().id
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            withData.forEach { e -> Chip(e.name, e.id == pick) { pick = e.id } }
        }
        val e = withData.first { it.id == pick }
        val series = remember(d, pick) { strengthSeries(d, e, 24) }
        if (series.size < 2) {
            Muted("One session so far. The line appears after your second.", Modifier.padding(top = 10.dp))
        } else {
            Sparkline(series.map { it.second }, "Strength for ${e.name}", Modifier.padding(top = 12.dp), height = 120.dp)
            val first = series.first().second
            val last = series.last().second
            val unitLabel = if (series.any { it.second > 0 } && d.sessions.any { it.x == e.id && it.w > 0 }) d.unit else "reps"
            Muted(
                if (unitLabel == "reps") "Total reps per session: ${fmtW(first)} to ${fmtW(last)}."
                else "Estimated one-rep max: ${fmtW((first * 2).roundToInt() / 2.0)} to ${fmtW((last * 2).roundToInt() / 2.0)} ${d.unit} over ${plural(series.size, "session")}.",
                Modifier.padding(top = 6.dp),
            )
        }
        val vol = remember(d, today) { weeklyVolume(d, WEEKS, today) }
        if (vol.any { it > 0 }) {
            Text("Weekly volume", style = Type.label, color = p.ink2, modifier = Modifier.padding(top = 18.dp))
            BarChart(vol, labels, "Weekly volume lifted", { "${fmtW(it.roundToInt().toDouble())} ${d.unit}" }, Modifier.padding(top = 8.dp))
        }
    }
}
