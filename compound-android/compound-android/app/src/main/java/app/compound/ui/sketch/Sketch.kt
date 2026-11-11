package app.compound.ui.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.compound.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/*
 * A tiny "rough" renderer, in the spirit of pencil sketches: every line is drawn twice with a little
 * deliberate wobble, shapes overshoot where they close, and colour fills sit slightly off the ink line.
 * Seeds keep the wobble identical on every frame, so the drawings stay accurate and calm.
 */

private class Jitter(seed: Int) {
    private val r = Random(seed)
    fun n(amount: Float) = (r.nextFloat() * 2f - 1f) * amount
}

fun DrawScope.roughLine(a: Offset, b: Offset, color: Color, width: Float, seed: Int, rough: Float = 1f, passes: Int = 2) {
    val j = Jitter(seed)
    val len = (b - a).getDistance().coerceAtLeast(1f)
    val dev = (len * 0.012f).coerceIn(0.35f, 2f) * rough
    val dir = (b - a) / len
    val perp = Offset(-dir.y, dir.x)
    repeat(passes) { pass ->
        val s = a + Offset(j.n(dev), j.n(dev)) - dir * (dev * 0.6f)
        val e = b + Offset(j.n(dev), j.n(dev)) + dir * (dev * 0.6f)
        val c1 = s + (e - s) * (0.3f + j.n(0.04f)) + perp * j.n(dev * 1.7f)
        val c2 = s + (e - s) * (0.7f + j.n(0.04f)) + perp * j.n(dev * 1.7f)
        val p = Path().apply { moveTo(s.x, s.y); cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y) }
        drawPath(p, color, style = Stroke(width = if (pass == 0) width else width * 0.62f, cap = StrokeCap.Round))
    }
}

/** A smooth path through the points (Catmull-Rom converted to cubic Béziers). */
fun smoothPath(pts: List<Offset>, closed: Boolean = false): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts[0].x, pts[0].y)
    val n = pts.size
    val count = if (closed) n else n - 1
    for (i in 0 until count) {
        val p0 = pts[if (closed) (i - 1 + n) % n else (i - 1).coerceAtLeast(0)]
        val p1 = pts[i % n]
        val p2 = pts[(i + 1) % n]
        val p3 = pts[if (closed) (i + 2) % n else (i + 2).coerceAtMost(n - 1)]
        val c1 = p1 + (p2 - p0) / 6f
        val c2 = p2 - (p3 - p1) / 6f
        p.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    if (closed) p.close()
    return p
}

fun DrawScope.roughCurve(pts: List<Offset>, color: Color, width: Float, seed: Int, rough: Float = 1f, closed: Boolean = false, passes: Int = 2) {
    val j = Jitter(seed)
    repeat(passes) { pass ->
        val moved = pts.map { it + Offset(j.n(1.1f * rough), j.n(1.1f * rough)) }
        drawPath(smoothPath(moved, closed), color, style = Stroke(width = if (pass == 0) width else width * 0.62f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

fun ellipsePoints(c: Offset, rx: Float, ry: Float, n: Int = 24, start: Float = 0f, sweep: Float = (2 * PI).toFloat()): List<Offset> =
    (0..n).map { i ->
        val t = start + sweep * i / n
        Offset(c.x + rx * cos(t), c.y + ry * sin(t))
    }

fun ellipsePath(c: Offset, rx: Float, ry: Float): Path = smoothPath(ellipsePoints(c, rx, ry, 24).dropLast(1), closed = true)

fun DrawScope.roughEllipse(c: Offset, rx: Float, ry: Float, color: Color, width: Float, seed: Int, rough: Float = 1f, passes: Int = 2) {
    val j = Jitter(seed)
    repeat(passes) { pass ->
        val start = j.n(PI.toFloat())
        val overlap = 0.35f + j.n(0.12f)
        val pts = ellipsePoints(c, rx * (1 + j.n(0.025f * rough)), ry * (1 + j.n(0.025f * rough)), 22, start, (2 * PI).toFloat() + overlap)
            .map { it + Offset(j.n(0.7f * rough), j.n(0.7f * rough)) }
        drawPath(smoothPath(pts), color, style = Stroke(width = if (pass == 0) width else width * 0.62f, cap = StrokeCap.Round))
    }
}

fun DrawScope.roughRect(r: Rect, color: Color, width: Float, seed: Int, rough: Float = 1f) {
    roughLine(r.topLeft, r.topRight, color, width, seed, rough)
    roughLine(r.topRight, r.bottomRight, color, width, seed + 1, rough)
    roughLine(r.bottomRight, r.bottomLeft, color, width, seed + 2, rough)
    roughLine(r.bottomLeft, r.topLeft, color, width, seed + 3, rough)
}

fun rectPath(r: Rect): Path = Path().apply { addRect(r) }

/** Colour sits a little off the ink, like a print where the layers are slightly misregistered. */
fun DrawScope.fill(path: Path, color: Color, dx: Float = 3f, dy: Float = 2.5f) {
    translate(dx, dy) { drawPath(path, color) }
}

/** Pencil shading: parallel strokes clipped to a shape. */
fun DrawScope.hachure(path: Path, color: Color, gap: Float, width: Float, seed: Int, angleDeg: Float = -40f) {
    val b = path.getBounds()
    val diag = hypot(b.width, b.height)
    val a = angleDeg * PI.toFloat() / 180f
    val dir = Offset(cos(a), sin(a))
    val nrm = Offset(-dir.y, dir.x)
    val c = b.center
    clipPath(path) {
        var k = -diag / 2
        var i = 0
        while (k <= diag / 2) {
            val mid = c + nrm * k
            roughLine(mid - dir * diag, mid + dir * diag, color, width, seed + i, rough = 0.5f, passes = 1)
            k += gap
            i++
        }
    }
}

/** Draws in a fixed coordinate space (viewW x viewH) scaled to the available width. */
@Composable
fun SketchCanvas(viewW: Float, viewH: Float, modifier: Modifier = Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier.fillMaxWidth().aspectRatio(viewW / viewH)) {
        val s = size.width / viewW
        withTransform({ scale(s, s, Offset.Zero) }) { draw() }
    }
}

/* ---------------- Icons ---------------- */

enum class Glyph { Today, System, Gym, Progress, Why, Settings, Plus, Close, Check, Chevron, Minus, Sync, Share, Back }

@Composable
fun SketchIcon(icon: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, weight: Float = 1.8f) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        withTransform({ scale(s, s, Offset.Zero) }) {
            fun l(x1: Float, y1: Float, x2: Float, y2: Float, seed: Int) = roughLine(Offset(x1, y1), Offset(x2, y2), tint, weight, seed, rough = 0.25f, passes = 1)
            when (icon) {
                Glyph.Today -> {
                    l(3f, 18f, 21f, 18f, 1)
                    drawPath(smoothPath(ellipsePoints(Offset(12f, 18f), 5.2f, 5.2f, 10, PI.toFloat(), PI.toFloat())), tint, style = Stroke(weight, cap = StrokeCap.Round))
                    l(12f, 8.6f, 12f, 5.4f, 2); l(6.4f, 11.6f, 4.4f, 9.8f, 3); l(17.6f, 11.6f, 19.6f, 9.8f, 4)
                }
                Glyph.System -> {
                    drawPath(smoothPath(ellipsePoints(Offset(12f, 12f), 7.5f, 7.5f, 16, -1.2f, 4.9f)), tint, style = Stroke(weight, cap = StrokeCap.Round))
                    val end = Offset(12f + 7.5f * cos(3.7f), 12f + 7.5f * sin(3.7f))
                    l(end.x, end.y, end.x + 3.4f, end.y - 0.4f, 5); l(end.x, end.y, end.x + 0.6f, end.y + 3.4f, 6)
                }
                Glyph.Gym -> {
                    l(6.5f, 7f, 6.5f, 17f, 7); l(17.5f, 7f, 17.5f, 17f, 8); l(3.5f, 9.5f, 3.5f, 14.5f, 9); l(20.5f, 9.5f, 20.5f, 14.5f, 10); l(6.5f, 12f, 17.5f, 12f, 11)
                }
                Glyph.Progress -> {
                    l(3.5f, 20f, 20.5f, 20f, 12); l(7f, 20f, 7f, 14f, 13); l(12f, 20f, 12f, 10f, 14); l(17f, 20f, 17f, 5.5f, 15)
                }
                Glyph.Why -> {
                    drawPath(smoothPath(listOf(Offset(3f, 20f), Offset(8f, 15.5f), Offset(12f, 13.5f), Offset(16f, 15.5f), Offset(21f, 20f))), tint, style = Stroke(weight, cap = StrokeCap.Round))
                    l(12f, 13.5f, 12f, 3.8f, 16)
                    drawPath(Path().apply { moveTo(12f, 3.8f); lineTo(18.5f, 6f); lineTo(12f, 8.4f) }, tint, style = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Glyph.Settings -> {
                    l(4f, 7f, 20f, 7f, 17); l(4f, 12f, 20f, 12f, 18); l(4f, 17f, 20f, 17f, 19)
                    drawCircle(tint, 2.3f, Offset(9f, 7f)); drawCircle(tint, 2.3f, Offset(15.5f, 12f)); drawCircle(tint, 2.3f, Offset(7.5f, 17f))
                }
                Glyph.Plus -> { l(12f, 5f, 12f, 19f, 20); l(5f, 12f, 19f, 12f, 21) }
                Glyph.Minus -> l(5f, 12f, 19f, 12f, 22)
                Glyph.Close -> { l(6f, 6f, 18f, 18f, 23); l(18f, 6f, 6f, 18f, 24) }
                Glyph.Check -> drawPath(Path().apply { moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f) }, tint, style = Stroke(weight + 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                Glyph.Chevron -> drawPath(Path().apply { moveTo(9f, 5.5f); lineTo(15.5f, 12f); lineTo(9f, 18.5f) }, tint, style = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round))
                Glyph.Back -> drawPath(Path().apply { moveTo(15f, 5.5f); lineTo(8.5f, 12f); lineTo(15f, 18.5f) }, tint, style = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round))
                Glyph.Sync -> {
                    drawPath(smoothPath(ellipsePoints(Offset(12f, 12f), 7f, 7f, 10, 3.6f, 2.4f)), tint, style = Stroke(weight, cap = StrokeCap.Round))
                    drawPath(smoothPath(ellipsePoints(Offset(12f, 12f), 7f, 7f, 10, 0.45f, 2.4f)), tint, style = Stroke(weight, cap = StrokeCap.Round))
                    l(18.2f, 4.6f, 18.6f, 8.3f, 25); l(18.6f, 8.3f, 15f, 8.6f, 26)
                    l(5.8f, 19.4f, 5.4f, 15.7f, 27); l(5.4f, 15.7f, 9f, 15.4f, 28)
                }
                Glyph.Share -> {
                    l(12f, 15f, 12f, 3.5f, 29); l(12f, 3.5f, 8f, 7.5f, 30); l(12f, 3.5f, 16f, 7.5f, 31)
                    drawPath(Path().apply { moveTo(7f, 10f); lineTo(4.5f, 10f); lineTo(4.5f, 20.5f); lineTo(19.5f, 20.5f); lineTo(19.5f, 10f); lineTo(17f, 10f) }, tint, style = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/* ---------------- Illustrations ---------------- */

/** A tally of votes on a hill, with a seedling growing beside it. */
@Composable
fun SeedlingArt(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SketchCanvas(320f, 210f, modifier) {
        val ink = p.ink
        // sun
        fill(ellipsePath(Offset(252f, 56f), 26f, 26f), p.hi, 4f, 3f)
        roughEllipse(Offset(252f, 56f), 26f, 26f, ink, 2.2f, 11)
        // hill
        val hillTop = listOf(Offset(-4f, 172f), Offset(70f, 160f), Offset(160f, 152f), Offset(250f, 160f), Offset(324f, 170f))
        val hill = smoothPath(hillTop).apply { lineTo(324f, 214f); lineTo(-4f, 214f); close() }
        fill(hill, p.mint, 0f, 3f)
        hachure(hill, p.moss.copy(alpha = 0.55f), 9f, 1.1f, 40)
        roughCurve(hillTop, ink, 2.2f, 12)
        // highlighter behind the tally
        fill(Path().apply { moveTo(88f, 128f); lineTo(150f, 123f); lineTo(151f, 147f); lineTo(89f, 151f); close() }, p.hi, 0f, 0f)
        for (i in 0 until 4) {
            val x = 100f + i * 12f
            roughLine(Offset(x, 108f + (i % 2) * 2f), Offset(x + 1f, 160f - (i % 2)), ink, 3f, 20 + i)
        }
        roughLine(Offset(90f, 151f), Offset(148f, 118f), ink, 3f, 30)
        // seedling
        roughCurve(listOf(Offset(186f, 156f), Offset(184f, 138f), Offset(187f, 120f), Offset(190f, 104f)), ink, 2.4f, 31)
        val leftLeaf = listOf(Offset(188f, 112f), Offset(174f, 109f), Offset(161f, 99f), Offset(157f, 90f), Offset(170f, 92f), Offset(182f, 101f))
        val rightLeaf = listOf(Offset(190f, 106f), Offset(202f, 97f), Offset(216f, 84f), Offset(224f, 78f), Offset(217f, 94f), Offset(201f, 104f))
        fill(smoothPath(leftLeaf, true), p.moss)
        fill(smoothPath(rightLeaf, true), p.moss)
        roughCurve(leftLeaf, ink, 2f, 32, closed = true)
        roughCurve(rightLeaf, ink, 2f, 33, closed = true)
        roughLine(Offset(188f, 110f), Offset(166f, 97f), ink, 1.2f, 34, passes = 1)
        roughLine(Offset(191f, 104f), Offset(214f, 85f), ink, 1.2f, 35, passes = 1)
        // grass ticks
        listOf(40f, 58f, 238f, 286f).forEachIndexed { i, x ->
            roughLine(Offset(x, 166f - i % 2 * 3), Offset(x - 3f, 156f - i % 2 * 3), ink, 1.6f, 50 + i, passes = 1)
            roughLine(Offset(x + 4f, 166f - i % 2 * 3), Offset(x + 6f, 158f - i % 2 * 3), ink, 1.6f, 60 + i, passes = 1)
        }
    }
}

/** A winding path up a hill to a flag: where you are going and why. */
@Composable
fun PathArt(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SketchCanvas(320f, 190f, modifier) {
        val ink = p.ink
        val far = listOf(Offset(-4f, 140f), Offset(90f, 96f), Offset(200f, 128f), Offset(324f, 104f))
        fill(smoothPath(far).apply { lineTo(324f, 194f); lineTo(-4f, 194f); close() }, p.sky, 0f, 0f)
        roughCurve(far, ink.copy(alpha = 0.55f), 1.6f, 70)
        val top = listOf(Offset(-4f, 180f), Offset(70f, 160f), Offset(150f, 92f), Offset(178f, 66f), Offset(206f, 92f), Offset(280f, 150f), Offset(324f, 164f))
        val hill = smoothPath(top).apply { lineTo(324f, 194f); lineTo(-4f, 194f); close() }
        fill(hill, p.mint, 0f, 2f)
        hachure(hill, p.moss.copy(alpha = 0.5f), 10f, 1.1f, 71, angleDeg = -32f)
        roughCurve(top, ink, 2.2f, 72)
        // dashed path
        val route = listOf(Offset(70f, 192f), Offset(112f, 176f), Offset(100f, 154f), Offset(140f, 136f), Offset(150f, 112f), Offset(170f, 92f), Offset(178f, 72f))
        val pts = ellipseFreeSample(route, 26)
        for (i in 0 until pts.size - 1 step 2) roughLine(pts[i], pts[i + 1], ink, 2f, 80 + i, rough = 0.6f, passes = 1)
        // flag
        roughLine(Offset(179f, 68f), Offset(179f, 22f), ink, 2.4f, 90)
        val flag = Path().apply { moveTo(179f, 23f); lineTo(214f, 31f); lineTo(179f, 42f); close() }
        fill(flag, p.hi)
        roughCurve(listOf(Offset(179f, 23f), Offset(214f, 31f), Offset(179f, 42f)), ink, 2f, 91)
        // birds
        roughCurve(listOf(Offset(60f, 46f), Offset(66f, 41f), Offset(71f, 46f), Offset(76f, 41f), Offset(82f, 46f)), ink, 1.6f, 92, passes = 1)
        roughCurve(listOf(Offset(92f, 60f), Offset(96f, 56f), Offset(100f, 60f), Offset(104f, 56f), Offset(108f, 60f)), ink, 1.4f, 93, passes = 1)
    }
}

/** Points spread evenly along a smooth route (used for dashed lines). */
fun ellipseFreeSample(route: List<Offset>, count: Int): List<Offset> {
    val fine = mutableListOf<Offset>()
    val n = route.size
    for (i in 0 until n - 1) {
        val p0 = route[(i - 1).coerceAtLeast(0)]
        val p1 = route[i]
        val p2 = route[i + 1]
        val p3 = route[(i + 2).coerceAtMost(n - 1)]
        for (k in 0 until 12) {
            val t = k / 12f
            val t2 = t * t
            val t3 = t2 * t
            fine += (p1 * 2f + (p2 - p0) * t + (p0 * 2f - p1 * 5f + p2 * 4f - p3) * t2 + (p1 * 3f - p0 - p2 * 3f + p3) * t3) * 0.5f
        }
    }
    fine += route.last()
    val lengths = mutableListOf(0f)
    for (i in 1 until fine.size) lengths += lengths.last() + (fine[i] - fine[i - 1]).getDistance()
    val total = lengths.last()
    return (0..count).map { k ->
        val target = total * k / count
        val idx = lengths.indexOfFirst { it >= target }.coerceAtLeast(1)
        val seg = (lengths[idx] - lengths[idx - 1]).coerceAtLeast(0.001f)
        val f = (target - lengths[idx - 1]) / seg
        fine[idx - 1] + (fine[idx] - fine[idx - 1]) * f
    }
}

/**
 * Effort and results: the dashed line is what we expect, the curve is what usually happens.
 * The shaded gap is the stretch where results lag behind the work.
 */
@Composable
fun PlateauArt(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SketchCanvas(320f, 176f, modifier) {
        val ink = p.ink
        roughLine(Offset(30f, 14f), Offset(30f, 160f), ink, 2f, 100)
        roughLine(Offset(30f, 160f), Offset(304f, 160f), ink, 2f, 101)
        val expectA = Offset(30f, 160f)
        val expectB = Offset(296f, 40f)
        val curve = listOf(Offset(30f, 160f), Offset(90f, 157f), Offset(150f, 150f), Offset(200f, 134f), Offset(238f, 106f), Offset(262f, 80f), Offset(282f, 50f), Offset(296f, 22f))
        // the gap between expectation and reality, up to where they cross (~x = 270)
        val cross = Offset(271f, 51f)
        val gap = Path().apply {
            moveTo(expectA.x, expectA.y); lineTo(cross.x, cross.y)
            val back = curve.filter { it.x <= cross.x }.reversed()
            back.forEach { lineTo(it.x, it.y) }
            close()
        }
        fill(gap, p.mint, 0f, 0f)
        hachure(gap, p.moss, 7f, 1.2f, 102, angleDeg = -55f)
        // expected (dashed)
        val dashes = 16
        for (i in 0 until dashes step 1) {
            if (i % 2 == 1) continue
            val a = expectA + (expectB - expectA) * (i / dashes.toFloat())
            val b = expectA + (expectB - expectA) * ((i + 1) / dashes.toFloat())
            roughLine(a, b, p.ink3, 2f, 110 + i, rough = 0.5f, passes = 1)
        }
        roughCurve(curve, ink, 3f, 103)
        fill(ellipsePath(cross, 7f, 7f), p.hi, 0f, 0f)
        roughEllipse(cross, 7f, 7f, ink, 1.8f, 104)
    }
}

/** A loaded barbell with an arrow pointing up. */
@Composable
fun BarbellArt(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SketchCanvas(320f, 140f, modifier) {
        val ink = p.ink
        roughCurve(listOf(Offset(10f, 124f), Offset(160f, 121f), Offset(310f, 124f)), ink, 1.8f, 120)
        roughLine(Offset(34f, 78f), Offset(286f, 78f), ink, 4f, 121)
        val plates = listOf(
            Rect(66f, 36f, 90f, 120f) to p.moss, Rect(90f, 50f, 103f, 106f) to p.mint,
            Rect(230f, 36f, 254f, 120f) to p.moss, Rect(217f, 50f, 230f, 106f) to p.mint,
        )
        plates.forEachIndexed { i, (r, c) ->
            fill(rectPath(r), c)
            roughRect(r, ink, 2.2f, 130 + i * 4)
        }
        fill(Path().apply { moveTo(146f, 24f); lineTo(174f, 22f); lineTo(176f, 46f); lineTo(145f, 47f); close() }, p.hi, 0f, 0f)
        roughLine(Offset(160f, 62f), Offset(160f, 14f), ink, 2.6f, 150)
        roughLine(Offset(160f, 14f), Offset(148f, 27f), ink, 2.6f, 151)
        roughLine(Offset(160f, 14f), Offset(172f, 27f), ink, 2.6f, 152)
    }
}

/** A sports watch with a heart-rate line. */
@Composable
fun WatchArt(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SketchCanvas(200f, 150f, modifier) {
        val ink = p.ink
        val strapTop = Rect(80f, 2f, 120f, 34f)
        val strapBottom = Rect(80f, 116f, 120f, 148f)
        fill(rectPath(strapTop), p.sand); fill(rectPath(strapBottom), p.sand)
        roughRect(strapTop, ink, 2f, 160); roughRect(strapBottom, ink, 2f, 164)
        val c = Offset(100f, 75f)
        fill(ellipsePath(c, 44f, 44f), p.mint)
        roughEllipse(c, 44f, 44f, ink, 2.4f, 170)
        roughEllipse(c, 35f, 35f, ink.copy(alpha = 0.7f), 1.4f, 171, passes = 1)
        roughLine(Offset(144f, 60f), Offset(151f, 60f), ink, 2.4f, 172)
        roughLine(Offset(144f, 90f), Offset(151f, 90f), ink, 2.4f, 173)
        val pulse = listOf(Offset(70f, 78f), Offset(84f, 78f), Offset(90f, 63f), Offset(98f, 94f), Offset(106f, 68f), Offset(112f, 78f), Offset(130f, 78f))
        for (i in 0 until pulse.size - 1) roughLine(pulse[i], pulse[i + 1], ink, 2.2f, 180 + i, rough = 0.5f, passes = 1)
        fill(ellipsePath(Offset(100f, 102f), 6f, 6f), p.hi, 0f, 0f)
    }
}

/** A calendar page with one day ticked. */
@Composable
fun CalendarArt(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SketchCanvas(200f, 150f, modifier) {
        val ink = p.ink
        val page = Rect(30f, 24f, 170f, 136f)
        fill(rectPath(page), p.sand)
        val head = Rect(30f, 24f, 170f, 50f)
        fill(rectPath(head), p.moss, 0f, 0f)
        roughRect(page, ink, 2.2f, 200)
        roughLine(Offset(30f, 50f), Offset(170f, 50f), ink, 1.8f, 205)
        roughLine(Offset(66f, 12f), Offset(66f, 34f), ink, 2.6f, 206)
        roughLine(Offset(134f, 12f), Offset(134f, 34f), ink, 2.6f, 207)
        for (row in 0 until 3) for (col in 0 until 5) {
            val x = 48f + col * 26f
            val y = 70f + row * 24f
            if (row == 1 && col == 3) continue
            roughLine(Offset(x - 4f, y), Offset(x + 4f, y), ink.copy(alpha = 0.6f), 1.6f, 210 + row * 5 + col, passes = 1)
        }
        val cell = Offset(126f, 94f)
        fill(ellipsePath(cell, 13f, 11f), p.hi, 0f, 0f)
        drawPath(Path().apply { moveTo(cell.x - 8f, cell.y); lineTo(cell.x - 2f, cell.y + 6f); lineTo(cell.x + 9f, cell.y - 8f) }, ink, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** An open notebook with a pencil: for the scorecard and weekly review. */
@Composable
fun NotebookArt(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SketchCanvas(240f, 130f, modifier) {
        val ink = p.ink
        val left = listOf(Offset(30f, 30f), Offset(118f, 38f), Offset(118f, 118f), Offset(30f, 110f))
        val right = listOf(Offset(118f, 38f), Offset(206f, 30f), Offset(206f, 110f), Offset(118f, 118f))
        fill(Path().apply { moveTo(left[0].x, left[0].y); left.drop(1).forEach { lineTo(it.x, it.y) }; close() }, p.raised)
        fill(Path().apply { moveTo(right[0].x, right[0].y); right.drop(1).forEach { lineTo(it.x, it.y) }; close() }, p.mint)
        for (i in 0 until 4) {
            roughLine(left[i], left[(i + 1) % 4], ink, 2f, 220 + i)
            roughLine(right[i], right[(i + 1) % 4], ink, 2f, 230 + i)
        }
        for (k in 0 until 4) {
            val y = 54f + k * 14f
            roughLine(Offset(42f, y), Offset(104f, y + 3f), ink.copy(alpha = 0.55f), 1.4f, 240 + k, passes = 1)
        }
        roughLine(Offset(132f, 58f), Offset(148f, 58f), ink, 2f, 250); roughLine(Offset(140f, 50f), Offset(140f, 66f), ink, 2f, 251)
        roughLine(Offset(132f, 86f), Offset(148f, 86f), ink, 2f, 252)
        fill(Path().apply { moveTo(156f, 52f); lineTo(196f, 48f); lineTo(197f, 64f); lineTo(157f, 67f); close() }, p.hi, 0f, 0f)
        // pencil
        roughLine(Offset(168f, 120f), Offset(222f, 74f), ink, 7f, 260, rough = 0.4f, passes = 1)
        roughLine(Offset(168f, 120f), Offset(222f, 74f), p.hi, 4f, 261, rough = 0.4f, passes = 1)
        roughLine(Offset(162f, 126f), Offset(168f, 120f), ink, 2.4f, 262)
    }
}
