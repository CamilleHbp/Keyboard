// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils

private enum class SetupStep(val title: Int, val instruction: Int, val action: Int) {
    Enable(R.string.setup_step1_title, R.string.setup_step1_instruction, R.string.setup_step1_action),
    Select(R.string.setup_step2_title, R.string.setup_step2_instruction, R.string.setup_step2_action),
    Ready(R.string.setup_step3_title, R.string.setup_step3_instruction, R.string.setup_finish_action)
}

@Composable
fun WelcomeWizard(close: () -> Unit, finish: () -> Unit) {
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun currentStep() = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(context, imm) -> SetupStep.Enable
        !UncachedInputMethodManagerUtils.isThisImeCurrent(context, imm) -> SetupStep.Select
        else -> SetupStep.Ready
    }
    var step by remember(context) { mutableStateOf(currentStep()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        step = currentStep()
    }
    // The system picker changes the default keyboard without returning an activity result.
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                step = currentStep()
            }
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observer)
        step = currentStep()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val appName = stringResource(context.applicationInfo.labelRes)
    val primaryAction: () -> Unit = {
        when (step) {
            SetupStep.Enable -> launcher.launch(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            SetupStep.Select -> imm.showInputMethodPicker()
            SetupStep.Ready -> finish()
        }
    }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            val wide = maxWidth >= 680.dp
            val content = Modifier.widthIn(max = if (wide) 800.dp else 480.dp)
                .fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)
            if (wide) {
                Row(content, horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SetupHeading(step, appName) }
                    Box(Modifier.weight(1.3f)) { SetupActions(step, appName, primaryAction, close) }
                }
            } else {
                Column(content) {
                    SetupHeading(step, appName)
                    Spacer(Modifier.height(16.dp))
                    SetupActions(step, appName, primaryAction, close)
                }
            }
        }
    }
}

@Composable
private fun SetupHeading(step: SetupStep, appName: String) {
    Column {
        Image(painterResource(R.drawable.hellboard_launcher_art), null,
            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(step.title, appName),
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.headlineMedium
        )
        if (step != SetupStep.Ready) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.setup_progress, if (step == SetupStep.Enable) 1 else 2),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SetupActions(step: SetupStep, appName: String, primaryAction: () -> Unit, configure: () -> Unit) {
    Column {
        Text(
            stringResource(step.instruction, appName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(primaryAction, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(step.action))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(configure, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.setup_step3_action))
        }
    }
}
