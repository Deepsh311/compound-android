package app.compound.ui.components

import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.compound.logic.dayInitial
import app.compound.logic.fromMin
import app.compound.logic.toMin
import app.compound.ui.sketch.Glyph
import app.compound.ui.sketch.SketchIcon
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import kotlin.math.floor
import kotlin.math.sin

enum class Btn { Primary, Ghost, Hi, Danger }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Btn = Btn.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    val (bg, fg, border) = when (kind) {
        Btn.Primary -> Triple(p.ink, p.paper, p.ink)
        Btn.Ghost -> Triple(Color.Transparent, p.ink, p.ink)
        Btn.Hi -> Triple(p.hi, p.hiInk, p.hi)
        Btn.Danger -> Triple(Color.Transparent, p.warn, p.warn)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.5.dp, border),
        modifier = modifier.heightIn(min = if (small) 38.dp else 48.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = if (small) 14.dp else 20.dp, vertical = 8.dp)) {
            Text(text, style = Type.label.copy(fontSize = if (small) 14.sp else 15.sp), color = fg.copy(alpha = if (enabled) 1f else 0.5f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun IconBtn(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = true) {
    val p = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .then(if (outlined) Modifier.border(1.dp, p.line2, CircleShape) else Modifier)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) { SketchIcon(glyph, p.ink, size = 20.dp) }
}

/** The round tick box. Filled with ink and a check when done. */
@Composable
fun CheckCircle(done: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics {
                contentDescription = label
                stateDescription = if (done) "Done" else "Not done"
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) p.ink else Color.Transparent)
                .border(2.dp, if (done) p.ink else p.ink3, CircleShape),
        ) { if (done) SketchIcon(Glyph.Check, p.paper, size = 16.dp, weight = 2.2f) }
    }
}

/** Text that gets a highlighter stroke when [marked]. Light mode marks the lower half; dark mode fills the line. */
@Composable
fun MarkedText(text: String, marked: Boolean, style: TextStyle, modifier: Modifier = Modifier, color: Color = LocalPalette.current.ink) {
    val p = LocalPalette.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        color = if (marked) (if (p.dark) p.hiInk else p.ink2) else color,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            if (!marked) return@drawBehind
            for (i in 0 until l.lineCount) {
                val left = l.getLineLeft(i) - 3.dp.toPx()
                val right = l.getLineRight(i) + 3.dp.toPx()
                val top = l.getLineTop(i)
                val bottom = l.getLineBottom(i)
                val h = bottom - top
                val y = if (p.dark) top + h * 0.08f else top + h * 0.52f
                val hh = if (p.dark) h * 0.86f else h * 0.4f
                drawRoundRect(p.hi, Offset(left, y), Size(right - left, hh), CornerRadius(2.dp.toPx()))
            }
        },
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.heading, color = LocalPalette.current.ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, style: TextStyle = Type.small) {
    Text(text, style = style, color = LocalPalette.current.ink3, modifier = modifier)
}

@Composable
fun HairLine(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, color: Color = LocalPalette.current.ink) {
    val p = LocalPalette.current
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(p.line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

/** One bar per set, filling from below the rep range to the top of it. */
@Composable
fun SetBars(fills: List<Float>, ready: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        fills.forEach { f ->
            Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(p.line)) {
                Box(Modifier.fillMaxWidth(f).height(12.dp).background(if (ready) p.hi else p.ink))
            }
        }
    }
}

/** The thin timeline of a part of the day: busy blocks, planned habits, finished habits and "now". */
@Composable
fun SegBar(blocks: List<app.compound.logic.BarBlock>, nowFraction: Float?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val h = 8.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(p.line, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2))
        for (b in blocks) {
            val c = when (b.type) { 0 -> p.ink; 1 -> p.plan; else -> p.hi }
            drawRoundRect(c, Offset(b.from * size.width, top), Size((b.width * size.width).coerceAtLeast(2f), h), CornerRadius(2.dp.toPx()))
        }
        if (nowFraction != null) {
            val x = nowFraction.coerceIn(0f, 1f) * size.width
            drawLine(p.warn, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/**
 * The tally: every vote is a stroke, five to a gate. Today's strokes sit on a highlighter band,
 * and the newest one draws itself in when [animateKey] changes.
 */
@Composable
fun Tally(total: Int, today: Int, animateKey: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shown = total.coerceAtMost(70)
    if (shown <= 0) return
    val draw = remember { Animatable(1f) }
    LaunchedEffect(animateKey) {
        if (animateKey > 0) {
            draw.snapTo(0f)
            draw.animateTo(1f, tween(450))
        }
    }
    val groups = (shown + 4) / 5
    val per = 7
    val rows = (groups + per - 1) / per
    val gw = 30f
    val gap = 14f
    val hRow = 40f
    val rowGap = 12f
    val w = minOf(groups, per) * (gw + gap) - gap + 8f
    val hTotal = rows * (hRow + rowGap) - rowGap
    Canvas(modifier.widthIn(max = (w * 1.05f).dp).fillMaxWidth().aspectRatio(w / hTotal)) {
        val s = size.width / w
        fun rnd(i: Int, k: Int): Float {
            val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
            return ((x - floor(x)) * 2 - 1).toFloat()
        }
        val firstToday = shown - today.coerceAtMost(shown)
        val bands = HashMap<Int, FloatArray>()
        data class Stroke2(val a: Offset, val b: Offset, val today: Boolean)
        val strokes = ArrayList<Stroke2>()
        for (i in 0 until shown) {
            val g = i / 5
            val k = i % 5
            val row = g / per
            val x0 = 4f + (g % per) * (gw + gap)
            val y0 = row * (hRow + rowGap)
            val a: Offset
            val b: Offset
            if (k < 4) {
                val x1 = x0 + 2f + k * 7f + rnd(i, 1) * 0.8f
                a = Offset(x1, y0 + 5f + rnd(i, 2) * 1.5f)
                b = Offset(x1 + rnd(i, 3) * 1.8f, y0 + hRow - 5f + rnd(i, 4) * 1.5f)
            } else {
                a = Offset(x0 - 3f + rnd(i, 1), y0 + hRow - 10f + rnd(i, 2) * 2f)
                b = Offset(x0 + 3 * 7f + 7f + rnd(i, 3), y0 + 9f + rnd(i, 4) * 2f)
            }
            val isToday = i >= firstToday
            if (isToday) {
                val band = bands.getOrPut(row) { floatArrayOf(Float.MAX_VALUE, -Float.MAX_VALUE, y0) }
                band[0] = minOf(band[0], a.x, b.x)
                band[1] = maxOf(band[1], a.x, b.x)
            }
            strokes += Stroke2(a, b, isToday)
        }
        for ((_, band) in bands) {
            val path = Path().apply {
                moveTo((band[0] - 5f) * s, (band[2] + hRow * 0.3f) * s)
                lineTo((band[1] + 5f) * s, (band[2] + hRow * 0.25f) * s)
                lineTo((band[1] + 4f) * s, (band[2] + hRow * 0.8f) * s)
                lineTo((band[0] - 4f) * s, (band[2] + hRow * 0.84f) * s)
                close()
            }
            drawPath(path, p.hi)
        }
        strokes.forEachIndexed { i, st ->
            val color = if (st.today) p.hiInk else p.ink
            val path = Path().apply { moveTo(st.a.x * s, st.a.y * s); lineTo(st.b.x * s, st.b.y * s) }
            if (i == strokes.lastIndex && draw.value < 1f) {
                val m = PathMeasure().apply { setPath(path, false) }
                val part = Path()
                m.getSegment(0f, m.length * draw.value, part, true)
                drawPath(part, color, style = Stroke(2.6f * s, cap = StrokeCap.Round))
            } else {
                drawPath(path, color, style = Stroke(2.6f * s, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, onType: (String) -> Unit, decimal: Boolean, label: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(p.raised)
            .border(1.dp, p.line2, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).clickable(onClickLabel = "Less $label", onClick = onMinus), contentAlignment = Alignment.Center) { SketchIcon(Glyph.Minus, p.ink, size = 22.dp) }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { onType(it.filter { c -> c.isDigit() || (decimal && c == '.') }.take(6)) },
            singleLine = true,
            textStyle = Type.number.copy(fontSize = 24.sp, color = p.ink, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.ink),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        Box(Modifier.size(54.dp).clickable(onClickLabel = "More $label", onClick = onPlus), contentAlignment = Alignment.Center) { SketchIcon(Glyph.Plus, p.ink, size = 22.dp) }
    }
}

@Composable
fun DaysPicker(selected: List<Int>, onChange: (List<Int>) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { d ->
            val on = d in selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .widthIn(max = 48.dp)
                    .clip(CircleShape)
                    .background(if (on) p.ink else p.raised)
                    .border(1.dp, if (on) p.ink else p.line2, CircleShape)
                    .clickable(role = Role.Checkbox) { onChange(if (on) selected - d else (selected + d).sorted()) }
                    .semantics { stateDescription = if (on) "On" else "Off" },
            ) { Text(dayInitial(d), style = Type.label, color = if (on) p.paper else p.ink) }
        }
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, p.line2, RoundedCornerShape(10.dp))
            .background(p.raised),
    ) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            if (i > 0) Box(Modifier.width(1.dp).height(44.dp).background(p.line2))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (on) p.ink else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
            ) { Text(label, style = Type.small.copy(fontSize = 14.sp), color = if (on) p.paper else p.ink, maxLines = 1) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) p.ink else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) p.ink else p.line2),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text, style = Type.small, color = if (selected) p.paper else p.ink)
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    max: Int = 160,
) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) Text(label, style = Type.label, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(max)) },
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, style = Type.body, color = p.ink3) },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = Type.body.copy(color = p.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = p.raised, unfocusedContainerColor = p.raised,
                focusedBorderColor = p.ink, unfocusedBorderColor = p.line2, cursorColor = p.ink,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A time picker field: tap to choose; long-press clears when [clearable]. */
@Composable
fun TimeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = false) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Column(modifier) {
        Text(label, style = Type.label, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.raised)
                .border(1.dp, p.line2, RoundedCornerShape(10.dp))
                .clickable(onClickLabel = "Choose $label") {
                    val m = toMin(value) ?: 9 * 60
                    TimePickerDialog(ctx, { _, h, mi -> onChange(fromMin(h * 60 + mi)) }, m / 60, m % 60, true).show()
                }
                .padding(horizontal = 14.dp),
        ) {
            Text(if (value.isBlank()) "Not set" else value, style = Type.body, color = if (value.isBlank()) p.ink3 else p.ink, modifier = Modifier.weight(1f))
            if (clearable && value.isNotBlank()) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Clear $label") { onChange("") }, contentAlignment = Alignment.Center) {
                    SketchIcon(Glyph.Close, p.ink3, size = 16.dp)
                }
            }
        }
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (checked) p.ink else p.raised).border(1.5.dp, if (checked) p.ink else p.line2, RoundedCornerShape(5.dp)),
        ) { if (checked) SketchIcon(Glyph.Check, p.paper, size = 15.dp, weight = 2f) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = Type.body, color = p.ink)
    }
}

/** Bottom sheet with a title and a close button; content scrolls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.surface,
        contentColor = p.ink,
        scrimColor = Color.Black.copy(alpha = if (p.dark) 0.55f else 0.4f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Text(title, style = Type.title.copy(fontSize = 28.sp), color = p.ink, modifier = Modifier.weight(1f))
                IconBtn(Glyph.Close, "Close", onDismiss)
            }
            content()
        }
    }
}

@Composable
fun Confirm(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text(title, style = Type.heading, color = p.ink) },
        text = { Text(text, style = Type.body, color = p.ink2) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(action, style = Type.label, color = p.warn) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = Type.label, color = p.ink) } },
    )
}

/** A quiet accented block used sparingly: the nudge, the reason of the day. */
@Composable
fun Note(modifier: Modifier = Modifier, accent: Color = LocalPalette.current.hi, content: @Composable ColumnScope.() -> Unit) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(start = 12.dp), content = content)
    }
}

val ScreenPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)
