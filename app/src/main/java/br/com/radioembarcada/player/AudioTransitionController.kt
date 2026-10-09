package br.com.radioembarcada.player

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType

/** Atua apenas no ganho. Nunca seek/stop/prepare: peças terminam pelo avanço natural do Media3. */
@UnstableApi
internal class AudioTransitionController(
    private val player: Player,
    private val current: () -> ProgramItem?,
    private val next: () -> ProgramItem?,
    private val readiness: (ProgramItem) -> PlaybackReadiness.Snapshot,
    private val diagnostic: (String) -> Unit = {},
    private val removedPrefixMs: (ProgramItem) -> Long = { 0 },
) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private var previous: ProgramItem? = null
    private var estimatedEndMs: Long? = null
    private var fadeLogged: String? = null
    private var durationLogged: String? = null
    private var finalLogged: String? = null
    private var bufferingSince: Long? = null
    private val tick = object : Runnable {
        override fun run() {
            updateVolume()
            if (player.isPlaying) {
                val item = current()
                val position = (player.currentPosition - (item?.let(removedPrefixMs) ?: 0)).coerceAtLeast(0)
                val duration = player.duration
                val ramp = if (item?.type == ProgramItemType.MUSIC) TransitionConfiguration.MUSIC_ENTRY_MS else TransitionConfiguration.INSERT_ENTRY_MS
                val fading = item?.type == ProgramItemType.MUSIC && next()?.type?.let {
                    TransitionPolicy.resolve(item.type, it) == TransitionType.MUSIC_TO_INSERT
                } == true && duration > 0 && duration - position <= TransitionConfiguration.MUSIC_FADE_OUT_MS
                handler.postDelayed(this, if (position < ramp || fading) TransitionConfiguration.UPDATE_MS
                    else TransitionConfiguration.IDLE_UPDATE_MS)
            }
        }
    }

    init { player.addListener(this); updateVolume() }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val item = current()
        val from = previous
        if (item != null && from != null && from.id != item.id) {
            diagnostic("Transition: ${from.type} -> ${item.type}; mode=${TransitionPolicy.resolve(from.type, item.type)}; item=${item.contentId}; advancement=$reason; overlap=0 ms")
            if (from.type != item.type) {
                val estimate = estimatedEndMs?.let { (android.os.SystemClock.elapsedRealtime() - it).coerceAtLeast(0) }
                diagnostic("Estimated scheduler gap: ${estimate?.let { "$it ms" } ?: "indisponível"}; acoustic gap: não medido")
            }
            // Não confundir evento de timeline com gap audível medido no dispositivo.
            if (from.type != ProgramItemType.MUSIC) diagnostic("Peça concluída por avanço natural=${reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO}; próximo conteúdo pré-carregado=${readiness(item).ready}")
        }
        previous = item
        estimatedEndMs = null
        fadeLogged = null
        finalLogged = null
        durationLogged = null
        updateVolume()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        // Um evento de volume gerado pelo próprio envelope não deve reiniciar seu timer.
        if (!events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_TIMELINE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) return
        if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
            if (bufferingSince == null) bufferingSince = android.os.SystemClock.elapsedRealtime()
        } else bufferingSince?.let {
            diagnostic("Espera observada pelo player: ${android.os.SystemClock.elapsedRealtime() - it} ms; item=${player.currentMediaItem?.mediaId}")
            bufferingSince = null
        }
        if (!player.playWhenReady) estimatedEndMs = null
        handler.removeCallbacks(tick)
        updateVolume()
        if (player.isPlaying) handler.post(tick)
    }

    private fun updateVolume() {
        val item = current() ?: return
        val following = next()
        if (item.type != ProgramItemType.MUSIC && player.duration > 0 && durationLogged != item.id) {
            durationLogged = item.id
            diagnostic("Peça=${item.contentId}; duração real=${player.duration} ms; próximo conteúdo pré-carregado=${following?.let { readiness(it).ready } == true}")
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: item.durationMs
        if (player.isPlaying && duration > 0) {
            estimatedEndMs = android.os.SystemClock.elapsedRealtime() + (duration - player.currentPosition).coerceAtLeast(0)
        }
        val envelopePosition = (player.currentPosition - removedPrefixMs(item)).coerceAtLeast(0)
        player.volume = TransitionPolicy.volume(item.type, following?.type, envelopePosition, duration)
        if (item.type == ProgramItemType.MUSIC && following != null &&
            TransitionPolicy.resolve(item.type, following.type) == TransitionType.MUSIC_TO_INSERT &&
            duration > 0 && duration - player.currentPosition <= TransitionConfiguration.MUSIC_FADE_OUT_MS &&
            fadeLogged != item.id) {
            fadeLogged = item.id
            logNext(item, following, duration - player.currentPosition)
            diagnostic("Transition: MUSIC -> ${following.type}; music fade: ${TransitionConfiguration.MUSIC_FADE_OUT_MS} ms; inserção=${following.contentId}; duração conhecida=${following.durationMs} ms; next item preloaded=${readiness(following).ready}")
        }
        if (item.type == ProgramItemType.MUSIC && following != null && duration > 0 &&
            duration - player.currentPosition <= 500 && finalLogged != item.id) {
            finalLogged = item.id
            logNext(item, following, duration - player.currentPosition)
        }
    }

    private fun logNext(item: ProgramItem, following: ProgramItem, remainingMs: Long) {
        val status = readiness(following)
        val inTimeline = (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == following.id }
        diagnostic("Current: ${item.type}; Next type: ${following.type}; Next MediaItem in timeline: $inTimeline; Next source prepared: ${status.prepared}; Next cache/preload started: ${status.started}; Next ready for immediate playback: ${status.ready}; bufferedUs=${status.bufferedUs}; Remaining MUSIC: $remainingMs ms")
    }

    fun release() { handler.removeCallbacksAndMessages(null); player.removeListener(this) }
}
