package app.compound

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.compound.data.Repository
import app.compound.platform.Reminders
import app.compound.ui.AppRoot
import app.compound.ui.components.PillButton
import app.compound.ui.sketch.WatchArt
import app.compound.ui.theme.CompoundTheme
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type

class CompoundApp : Application() {
    val repository: Repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTab = intent?.getStringExtra("tab")
        setContent {
            val vm: AppViewModel = viewModel()
            AppRoot(vm, startTab)
        }
    }
}

/** Shown by Health Connect when someone taps "Read privacy policy" for Compound. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompoundTheme("auto") {
                val p = LocalPalette.current
                Column(
                    Modifier.fillMaxSize().background(p.paper).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                ) {
                    WatchArt(Modifier.padding(horizontal = 40.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("What Compound reads", style = Type.title, color = p.ink)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Compound reads your exercise sessions from Health Connect, such as the workouts Garmin Connect saves there. " +
                            "It uses them to show your training on the Today and Gym screens, to tick training habits, and to chart your weekly minutes.",
                        style = Type.body, color = p.ink2,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Workout data stays on this phone. Compound doesn't send it anywhere and doesn't write anything to Health Connect. " +
                            "You can remove access at any time in Health Connect.",
                        style = Type.body, color = p.ink2,
                    )
                    Spacer(Modifier.height(24.dp))
                    PillButton("Close", { finish() })
                }
            }
        }
    }
}

object BuildConfigInfo {
    const val VERSION = "1.0"
}
