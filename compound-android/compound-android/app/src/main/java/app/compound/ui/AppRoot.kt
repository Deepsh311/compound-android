package app.compound.ui

import android.app.Activity
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.compound.AppViewModel
import app.compound.UiState
import app.compound.ui.components.PillButton
import app.compound.ui.components.Btn
import app.compound.ui.gym.ExerciseEditor
import app.compound.ui.gym.GymScreen
import app.compound.ui.gym.LoggerSheet
import app.compound.ui.onboarding.OnboardingScreen
import app.compound.ui.progress.ProgressScreen
import app.compound.ui.settings.CommitmentEditor
import app.compound.ui.settings.SettingsScreen
import app.compound.ui.sketch.Glyph
import app.compound.ui.sketch.SketchIcon
import app.compound.ui.system.GoalEditor
import app.compound.ui.system.HabitEditor
import app.compound.ui.system.ReviewSheet
import app.compound.ui.system.ScorecardSheet
import app.compound.ui.system.SystemScreen
import app.compound.ui.theme.CompoundTheme
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import app.compound.ui.today.TodayScreen
import app.compound.ui.why.WhyEditor
import app.compound.ui.why.WhyScreen
import kotlinx.coroutines.delay

enum class Tab(val label: String, val glyph: Glyph) {
    Today("Today", Glyph.Today),
    System("System", Glyph.System),
    Gym("Gym", Glyph.Gym),
    Progress("Progress", Glyph.Progress),
    Why("Why", Glyph.Why),
}

/** Every bottom sheet the app can open. */
sealed interface SheetReq {
    data class HabitEdit(val id: String?) : SheetReq
    data class GoalEdit(val id: String?) : SheetReq
    data class WhyEdit(val id: String) : SheetReq
    data class ExerciseEdit(val id: String?) : SheetReq
    data class Logger(val id: String) : SheetReq
    data class CommitEdit(val id: String?) : SheetReq
    data object Scorecard : SheetReq
    data object Review : SheetReq
}

@Composable
fun AppRoot(vm: AppViewModel, startTab: String?) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val s = state
    CompoundTheme(s?.data?.theme ?: "auto") {
        val p = LocalPalette.current
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !p.dark
                    isAppearanceLightNavigationBars = !p.dark
                }
            }
        }
        LifecycleResumeEffect(Unit) {
            vm.onResume()
            onPauseOrDispose { }
        }
        Box(Modifier.fillMaxSize().background(p.paper)) {
            when {
                s == null -> Unit
                !s.data.onboarded -> OnboardingScreen(onStart = { starter, identity, name -> vm.onboard(starter, identity, name) })
                else -> MainShell(vm, s, startTab)
            }
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel, s: UiState, startTab: String?) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(if (startTab == "why") Tab.Why else Tab.Today) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetReq?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }
    BackHandler(enabled = settings) { settings = false }
    BackHandler(enabled = !settings && tab != Tab.Today) { tab = Tab.Today }

    Scaffold(
        containerColor = p.paper,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(data, containerColor = p.ink, contentColor = p.paper, shape = RoundedCornerShape(16.dp))
            }
        },
        bottomBar = {
            if (!settings) {
                NavigationBar(containerColor = p.surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        val on = t == tab
                        NavigationBarItem(
                            selected = on,
                            onClick = { tab = t },
                            icon = { SketchIcon(t.glyph, if (on) p.hiInk else p.ink3) },
                            label = { Text(t.label, style = Type.small.copy(fontSize = Type.small.fontSize * 0.88f), color = if (on) p.ink else p.ink3) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = p.hi),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            if (settings) {
                SettingsScreen(vm, s, onBack = { settings = false }, open = { sheet = it })
            } else {
                when (tab) {
                    Tab.Today -> TodayScreen(vm, s, open = { sheet = it }, openSettings = { settings = true }, goTo = { tab = it })
                    Tab.System -> SystemScreen(s, open = { sheet = it })
                    Tab.Gym -> GymScreen(vm, s, open = { sheet = it }, openSettings = { settings = true })
                    Tab.Progress -> ProgressScreen(s)
                    Tab.Why -> WhyScreen(vm, s, open = { sheet = it }, goTo = { tab = it })
                }
            }
        }
    }
    RestPill(vm)

    val close = { sheet = null }
    when (val req = sheet) {
        null -> Unit
        is SheetReq.HabitEdit -> HabitEditor(vm, s, req.id, close)
        is SheetReq.GoalEdit -> GoalEditor(vm, s, req.id, close, openWhy = { sheet = SheetReq.WhyEdit(it) })
        is SheetReq.WhyEdit -> WhyEditor(vm, s, req.id, close)
        is SheetReq.ExerciseEdit -> ExerciseEditor(vm, s, req.id, close)
        is SheetReq.Logger -> LoggerSheet(vm, s, req.id, close, edit = { sheet = SheetReq.ExerciseEdit(it) })
        is SheetReq.CommitEdit -> CommitmentEditor(vm, s, req.id, close)
        SheetReq.Scorecard -> ScorecardSheet(vm, s, close)
        SheetReq.Review -> ReviewSheet(vm, s, close)
    }
}

/** The rest timer floats at the top so it stays visible while you log sets. */
@Composable
private fun RestPill(vm: AppViewModel) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val endsAt by vm.restEndsAt.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var buzzed by remember(endsAt) { mutableStateOf(false) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            val left = endsAt!! - now
            if (left <= 0 && !buzzed) {
                buzzed = true
                runCatching {
                    ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 90, 180), -1))
                }
            }
            if (left < -8000) { vm.stopRest(); break }
            delay(250)
        }
    }
    AnimatedVisibility(
        visible = endsAt != null,
        enter = slideInVertically { -it * 2 },
        exit = slideOutVertically { -it * 2 },
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val left = ((endsAt ?: now) - now)
            val done = left <= 0
            Surface(shape = RoundedCornerShape(50), color = if (done) p.hi else p.ink, shadowElevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
                    val secs = ((left + 999) / 1000).coerceAtLeast(0)
                    Text(
                        if (done) "Rest done. Next set." else "Rest ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}",
                        style = Type.label.copy(fontSize = Type.label.fontSize * 1.1f),
                        color = if (done) p.hiInk else p.paper,
                    )
                    Spacer(Modifier.width(12.dp))
                    PillButton(if (done) "OK" else "Stop", { vm.stopRest() }, kind = if (done) Btn.Primary else Btn.Hi, small = true)
                }
            }
        }
    }
}
