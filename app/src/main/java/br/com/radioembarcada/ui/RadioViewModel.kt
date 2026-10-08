package br.com.radioembarcada.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.util.UnstableApi
import br.com.radioembarcada.RadioApplication
import br.com.radioembarcada.player.RadioController

@UnstableApi
class RadioViewModel(application: Application) : AndroidViewModel(application) {
    val tenant = (application as RadioApplication).tenants.activeTenant
    private val controller = RadioController(application)
    val state = controller.state
    val playRequested = controller.playRequested
    val ready = controller.ready
    val nowPlaying = controller.nowPlaying
    val connected = controller.connected
    val message = controller.message
    fun togglePlayback() { controller.toggle() }
    override fun onCleared() { controller.release() }
}
