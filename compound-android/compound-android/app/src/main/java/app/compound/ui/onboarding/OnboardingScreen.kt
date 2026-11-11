package app.compound.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.compound.logic.longDate
import app.compound.ui.components.Btn
import app.compound.ui.components.Field
import app.compound.ui.components.Muted
import app.compound.ui.components.PillButton
import app.compound.ui.sketch.SeedlingArt
import app.compound.ui.theme.LocalPalette
import app.compound.ui.theme.Type
import java.time.LocalDate

@Composable
fun OnboardingScreen(onStart: (starter: Boolean, identity: String, name: String) -> Unit) {
    val p = LocalPalette.current
    var identity by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Muted(longDate(LocalDate.now()), style = Type.small.copy(fontSize = 15.sp))
        SeedlingArt(Modifier.padding(top = 12.dp))
        Text("Build a system you can keep.", style = Type.display.copy(fontSize = 52.sp, lineHeight = 50.sp), color = p.ink, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Goals tell you where you want to go. A system is the handful of things you do every day to get there. " +
                "Compound plans your day around your calendar, tracks your habits and lifts, and counts every tick as a vote for the person you\u2019re becoming.",
            style = Type.body.copy(fontSize = 17.sp, lineHeight = 25.sp), color = p.ink2, modifier = Modifier.padding(top = 14.dp),
        )
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Your first name (optional)", name, { name = it }, max = 40)
            Field("Finish the sentence: I\u2019m someone who\u2026", identity, { identity = it }, singleLine = false, minLines = 2,
                placeholder = "trains four times a week and finishes what I start")
            PillButton("Use the starter routine", { onStart(true, identity, name) }, Modifier.fillMaxWidth())
            PillButton("Start empty", { onStart(false, identity, name) }, Modifier.fillMaxWidth(), kind = Btn.Ghost)
            Muted("The starter routine adds seven habits (one to break), two goals and six lifts across push, pull and legs. Change or delete any of it later.")
        }
        Spacer(Modifier.height(24.dp))
    }
}
