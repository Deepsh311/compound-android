package app.compound.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.compound.ui.sketch.roughLine
import app.compound.ui.sketch.smoothPath
import app.compound.ui.theme.Archivo
import app.compound.ui.theme.LocalPalette
import java.time.LocalDate
import kotlin.math.max

data class ChartSeries(val name: String, val values: List<Double?>, val color: Color)

private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float, color: Color, alignEnd: Boolean = false, center: Boolean = false) {
    val style = TextStyle(fontFamily = Archivo, fontSize = 11.sp, color = color)
    val layout = tm.measure(text, style)
    val dx = when {
        alignEnd -> -layout.size.width.toFloat()
        center -> -layout.size.width / 2f
        else -> 0f
    }
    drawText(layout, topLeft = Offset(x + dx, y))
}

/** Lines over weeks, 0–100%. Gaps where nothing was scheduled. */
@Composable
fun LineChart(series: List<ChartSeries>, xLabels: List<String>, description: String, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val left = 34.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 6.dp.toPx()
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = bottom - (bottom - top) * f
            drawLine(p.line, Offset(left, y), Offset(right, y), 1.dp.toPx())
            label(tm, "${(f * 100).toInt()}%", left - 6.dp.toPx(), y - 7.dp.toPx(), p.ink3, alignEnd = true)
        }
        val n = series.maxOfOrNull { it.values.size } ?: 0
        if (n == 0) return@Canvas
        fun x(i: Int) = if (n == 1) (left + right) / 2 else left + (right - left) * i / (n - 1)
        for (s in series) {
            var run = mutableListOf<Offset>()
            fun flush() {
                if (run.size >= 2) drawPath(smoothPath(run), s.color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                run.forEach { drawCircle(s.color, 3.dp.toPx(), it) }
                run = mutableListOf()
            }
            s.values.forEachIndexed { i, v ->
                if (v == null) flush() else run += Offset(x(i), bottom - (bottom - top) * v.toFloat().coerceIn(0f, 1f))
            }
            flush()
        }
        if (xLabels.isNotEmpty()) {
            label(tm, xLabels.first(), left, bottom + 4.dp.toPx(), p.ink3)
            label(tm, xLabels.last(), right, bottom + 4.dp.toPx(), p.ink3, alignEnd = true)
        }
    }
}

/** Weekly bars; the current week is drawn with the highlighter. */
@Composable
fun BarChart(values: List<Double>, xLabels: List<String>, description: String, format: (Double) -> String, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val bottom = size.height - 20.dp.toPx()
        val top = 18.dp.toPx()
        val maxV = max(values.maxOrNull() ?: 0.0, 1e-9)
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val w = slot * 0.62f
        values.forEachIndexed { i, v ->
            val h = ((bottom - top) * (v / maxV)).toFloat()
            val x = slot * i + (slot - w) / 2
            val last = i == values.lastIndex
            if (v > 0) {
                drawRoundRect(if (last) p.hi else p.moss, Offset(x + 2f, bottom - h + 2f), Size(w, h), CornerRadius(3.dp.toPx()))
                roughLine(Offset(x, bottom - h), Offset(x + w, bottom - h), p.ink, 1.6.dp.toPx(), 900 + i, rough = 0.4f, passes = 1)
                roughLine(Offset(x, bottom - h), Offset(x, bottom), p.ink, 1.4.dp.toPx(), 950 + i, rough = 0.4f, passes = 1)
                roughLine(Offset(x + w, bottom - h), Offset(x + w, bottom), p.ink, 1.4.dp.toPx(), 990 + i, rough = 0.4f, passes = 1)
            }
            if (last && v > 0) label(tm, format(v), x + w / 2, bottom - h - 16.dp.toPx(), p.ink2, center = true)
        }
        drawLine(p.ink, Offset(0f, bottom), Offset(size.width, bottom), 1.5.dp.toPx())
        if (xLabels.isNotEmpty()) {
            label(tm, xLabels.first(), 0f, bottom + 4.dp.toPx(), p.ink3)
            label(tm, xLabels.last(), size.width, bottom + 4.dp.toPx(), p.ink3, alignEnd = true)
        }
    }
}

/** A calendar heatmap: one column per week, Monday at the top. Darker means more of the day's habits were done. */
@Composable
fun Heatmap(days: List<Pair<LocalDate, Double?>>, description: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val tm = rememberTextMeasurer()
    if (days.isEmpty()) return
    val first = days.first().first
    val offset = first.dayOfWeek.value - 1
    val cols = (offset + days.size + 6) / 7
    Canvas(modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
        val labelW = 18.dp.toPx()
        val gap = 3.dp.toPx()
        val cell = minOf((size.width - labelW - gap * cols) / cols, (size.height - gap * 7) / 7)
        listOf(0 to "M", 2 to "W", 4 to "F", 6 to "S").forEach { (r, t) ->
            label(tm, t, 0f, r * (cell + gap) + cell / 2 - 7.dp.toPx(), p.ink3)
        }
        days.forEachIndexed { i, (_, f) ->
            val idx = i + offset
            val c = idx / 7
            val r = idx % 7
            val x = labelW + c * (cell + gap)
            val y = r * (cell + gap)
            val color = when {
                f == null -> p.line.copy(alpha = 0.45f)
                f <= 0.0 -> p.line
                else -> p.moss.copy(alpha = (0.3f + 0.7f * f.toFloat()).coerceIn(0f, 1f))
            }
            drawRoundRect(color, Offset(x, y), Size(cell, cell), CornerRadius(3.dp.toPx()))
            if (f != null && f >= 1.0) drawRoundRect(p.ink, Offset(x, y), Size(cell, cell), CornerRadius(3.dp.toPx()), style = Stroke(1.2.dp.toPx()))
        }
    }
}

/** A small strength line: one point per session. */
@Composable
fun Sparkline(values: List<Double>, description: String, modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val p = LocalPalette.current
    if (values.size < 2) return
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val pad = 8.dp.toPx()
        val lo = values.min()
        val hi = values.max()
        fun pt(i: Int, v: Double) = Offset(
            pad + (size.width - 2 * pad) * i / (values.size - 1),
            if (hi == lo) size.height / 2 else (size.height - pad - (size.height - 2 * pad) * ((v - lo) / (hi - lo))).toFloat(),
        )
        val pts = values.mapIndexed { i, v -> pt(i, v) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, p.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEachIndexed { i, o ->
            if (i == pts.lastIndex) {
                drawCircle(p.hi, 5.dp.toPx(), o)
                drawCircle(p.ink, 5.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
            } else drawCircle(p.ink, 3.dp.toPx(), o)
        }
    }
}

/** A dashed guide line used in legends. */
fun DrawScope.dashed(a: Offset, b: Offset, color: Color, width: Float) =
    drawLine(color, a, b, width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
