package br.com.radioembarcada.ui

import android.os.Bundle
import androidx.core.graphics.toColorInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import br.com.radioembarcada.model.ConnectionState

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: RadioViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val requested by model.playRequested.collectAsStateWithLifecycle()
            val ready by model.ready.collectAsStateWithLifecycle()
            val current by model.nowPlaying.collectAsStateWithLifecycle()
            val connected by model.connected.collectAsStateWithLifecycle()
            val message by model.message.collectAsStateWithLifecycle()
            val uriHandler = LocalUriHandler.current
            val tenant = model.tenant
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(tenant.primaryColor.toColorInt()),
                onPrimary = Color.White,
            )) {
                Scaffold { insets ->
                    Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tenant.radioName, style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold)
                        Text(tenant.companyName, style = MaterialTheme.typography.titleMedium)
                        Text(if (state == ConnectionState.LIVE) "● AO VIVO" else "RÁDIO AO VIVO",
                            color = if (state == ConnectionState.LIVE) Color(0xFF146C2E)
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = model::togglePlayback, enabled = ready,
                            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 88.dp)) {
                            Text(if (requested) "Pausar" else "Play",
                                style = MaterialTheme.typography.headlineSmall)
                        }
                        if (!connected && state == ConnectionState.LIVE) {
                            Text("Sem internet — reproduzindo conteúdo preparado.")
                        }
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                        Text(state.label, modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        }, style = MaterialTheme.typography.titleMedium)
                        current?.let { track ->
                            (track.artworkData ?: track.artworkUrl)?.let { artwork ->
                                AsyncImage(model = artwork, contentDescription = "Capa de ${track.title}",
                                    modifier = Modifier.size(160.dp))
                            }
                            Text(track.title, style = MaterialTheme.typography.titleLarge)
                            if (track.artist.isNotBlank()) Text(track.artist, style = MaterialTheme.typography.bodyLarge)
                            track.durationMs?.let { duration ->
                                Text("Duração: ${duration / 60_000}:${((duration / 1_000) % 60).toString().padStart(2, '0')}")
                            }
                            if (track.sourceUrl.isNotBlank()) TextButton(onClick = { uriHandler.openUri(track.sourceUrl) }) {
                                Text("${track.source} • página da faixa")
                            }
                            if (track.license.isNotBlank()) TextButton(onClick = { uriHandler.openUri(track.license) }) {
                                Text("Licença Creative Commons • teste não comercial")
                            }
                        }
                    }
                }
            }
        }
    }
}
