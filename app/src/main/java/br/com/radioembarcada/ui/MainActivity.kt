package br.com.radioembarcada.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import br.com.radioembarcada.model.ConnectionState
import br.com.radioembarcada.ui.theme.RadioTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val model: RadioViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val requested by model.playRequested.collectAsStateWithLifecycle()
            val ready by model.ready.collectAsStateWithLifecycle()
            val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
            RadioTheme {
                RadioPlayerScreen(
                    radioName = model.tenant.radioName,
                    playing = state == ConnectionState.LIVE,
                    playRequested = requested,
                    controlsEnabled = ready,
                    animateWaveform = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
                    onTogglePlayback = model::togglePlayback,
                    onVolumeDown = model::lowerVolume,
                    onVolumeUp = model::raiseVolume,
                )
            }
        }
    }
}
