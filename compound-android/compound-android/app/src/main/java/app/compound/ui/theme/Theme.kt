package app.compound.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.compound.R

/** The palette: ledger paper, forest ink and a highlighter for things that are done. */
@Immutable
data class Palette(
    val dark: Boolean,
    val paper: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val line2: Color,
    val hi: Color,
    val hiInk: Color,
    val plan: Color,
    val warn: Color,
    // Soft fills for the illustrations and charts
    val moss: Color,
    val mint: Color,
    val sand: Color,
    val sky: Color,
)

val LightPalette = Palette(
    dark = false,
    paper = Color(0xFFECEFE8), surface = Color(0xFFF6F8F3), raised = Color(0xFFFFFFFF),
    ink = Color(0xFF15261C), ink2 = Color(0xFF44554A), ink3 = Color(0xFF5F6F64),
    line = Color(0xFFD3DACF), line2 = Color(0xFFB8C3B4),
    hi = Color(0xFFFFE15A), hiInk = Color(0xFF15261C),
    plan = Color(0xFF9DB1A3), warn = Color(0xFFA33A17),
    moss = Color(0xFF8DB59A), mint = Color(0xFFCFE3D3), sand = Color(0xFFEDE4CB), sky = Color(0xFFCADFDC),
)

val DarkPalette = Palette(
    dark = true,
    paper = Color(0xFF10261B), surface = Color(0xFF163225), raised = Color(0xFF1C3B2D),
    ink = Color(0xFFE8F0EA), ink2 = Color(0xFFB4C6BA), ink3 = Color(0xFF8AA394),
    line = Color(0xFF284A3A), line2 = Color(0xFF3A6352),
    hi = Color(0xFFFFE15A), hiInk = Color(0xFF13261B),
    plan = Color(0xFF4E7A66), warn = Color(0xFFFFAE8F),
    moss = Color(0xFF5E9273), mint = Color(0xFF2F5A45), sand = Color(0xFF4A4A33), sky = Color(0xFF2D5552),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float = 100f, italic: Boolean = false) = Font(
    resId = if (italic) R.font.archivo_italic else R.font.archivo,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val Archivo = FontFamily(
    archivo(400), archivo(500), archivo(600), archivo(700),
    archivo(400, italic = true), archivo(500, italic = true),
)

/** The narrow, heavy cut used for the identity statement, titles and big numbers. */
val ArchivoCondensed = FontFamily(archivo(800, 64f), archivo(700, 72f))

object Type {
    val display = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight(800), fontSize = 42.sp, lineHeight = 41.sp, letterSpacing = (-0.005).em)
    val title = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight(800), fontSize = 32.sp, lineHeight = 32.sp)
    val number = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight(800), fontSize = 28.sp, lineHeight = 28.sp)
    val heading = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(700), fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em)
    val greet = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(600), fontSize = 26.sp, lineHeight = 30.sp)
    val body = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 23.sp)
    val item = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(500), fontSize = 16.5.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = Archivo, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 18.sp)
    val quote = TextStyle(fontFamily = Archivo, fontStyle = FontStyle.Italic, fontWeight = FontWeight(400), fontSize = 20.sp, lineHeight = 28.sp)
}

@Composable
fun CompoundTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.ink, onPrimary = p.paper,
        secondary = p.moss, onSecondary = p.ink,
        tertiary = p.hi, onTertiary = p.hiInk,
        background = p.paper, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.line, onSurfaceVariant = p.ink2,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerLow = p.surface,
        surfaceContainerHighest = p.raised, surfaceContainerLowest = p.raised,
        outline = p.line2, outlineVariant = p.line,
        error = p.warn, onError = p.paper,
        secondaryContainer = p.hi, onSecondaryContainer = p.hiInk,
    )
    val typography = Typography(
        bodyLarge = Type.body, bodyMedium = Type.small, bodySmall = Type.small,
        titleLarge = Type.heading, titleMedium = Type.label, labelLarge = Type.label,
        labelMedium = Type.small, labelSmall = Type.small.copy(fontSize = 12.sp),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
