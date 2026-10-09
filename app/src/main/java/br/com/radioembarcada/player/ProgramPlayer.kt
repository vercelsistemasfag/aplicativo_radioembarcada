package br.com.radioembarcada.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MediaSource
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType

/** Toca conteúdo genérico. Não consulta catálogo nem decide a ordem musical. */
@UnstableApi
class ProgramPlayer(context: Context, private val localSource: Boolean = false,
    private val diagnostic: (String) -> Unit = {}) {
    private data class Entry(val program: ProgramItem, val media: MediaItem)
    private val entries = mutableListOf<Entry>()
        private val database = StandaloneDatabaseProvider(context)
    private val cache: SimpleCache
    private val readiness = PlaybackReadiness(diagnostic)
    private val loadControl: RadioLoadControl
    private val transitions: AudioTransitionController
    val player: ExoPlayer
    val items: List<ProgramItem> get() = entries.map { it.program }
    val mediaItems: List<MediaItem> get() = entries.map { it.media }

    init {
        val directory = context.cacheDir.resolve("operational_audio")
        // Fora da pasta indexada: SimpleCache remove arquivos que não sejam spans/índice.
        val marker = context.cacheDir.resolve("operational_audio.created")
        if (!directory.exists() || !marker.exists() ||
                System.currentTimeMillis() - marker.lastModified() > PlaybackConfiguration.CACHE_MAX_AGE_MS) {
            directory.deleteRecursively()
            marker.delete()
        }
        directory.mkdirs()
        if (!marker.exists()) marker.writeText("")
        cache = SimpleCache(directory, LeastRecentlyUsedCacheEvictor(PlaybackConfiguration.CACHE_BYTES), database)
        val dataSource = CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(PlaybackConfiguration.CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(PlaybackConfiguration.READ_TIMEOUT_MS))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val upstream = ProgressiveMediaSource.Factory(dataSource)
            .setContinueLoadingCheckIntervalBytes(PlaybackConfiguration.LOADING_CHECK_INTERVAL_BYTES)
            .setLoadErrorHandlingPolicy(RadioLoadErrorPolicy())
        val local = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
            .setContinueLoadingCheckIntervalBytes(PlaybackConfiguration.LOADING_CHECK_INTERVAL_BYTES)
            .setLoadErrorHandlingPolicy(RadioLoadErrorPolicy())
        val observedFactory = object : MediaSource.Factory by upstream {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource = readiness.observe(
                if (mediaItem.localConfiguration?.uri?.scheme == "asset") local.createMediaSource(mediaItem)
                else upstream.createMediaSource(mediaItem))
        }
        loadControl = if (localSource) RadioLoadControl(DefaultLoadControl.Builder()
            .setBufferDurationsMs(PlaybackConfiguration.LOCAL_MIN_BUFFER_MS, PlaybackConfiguration.LOCAL_MAX_BUFFER_MS,
                PlaybackConfiguration.LOCAL_START_BUFFER_MS, PlaybackConfiguration.LOCAL_REBUFFER_MS)
            .setPrioritizeTimeOverSizeThresholds(true).build(), localSource = true, playlistPreload = true) else RadioLoadControl(playlistPreload = true)
        loadControl.currentAudio = readiness::get
        player = ExoPlayer.Builder(context).setMediaSourceFactory(observedFactory).setLoadControl(loadControl).build().apply {
            setPreloadConfiguration(ExoPlayer.PreloadConfiguration(PlaybackConfiguration.NEXT_TRACK_PRELOAD_MS * 1_000))
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(if (localSource) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
        }
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadControl.currentMediaId = mediaItem?.mediaId
            }
        })
        transitions = AudioTransitionController(player,
            current = { entries.getOrNull(player.currentMediaItemIndex)?.program
                ?.takeIf { it.id == player.currentMediaItem?.mediaId } },
            next = { entries.getOrNull(player.currentMediaItemIndex + 1)?.program },
            readiness = { readiness.get(it.id) }, diagnostic = diagnostic)
    }

    fun isCached(item: ProgramItem): Boolean = cache.getCachedSpans(audioCacheKey(item))
        .any { it.position == 0L && it.length > 0 }

    fun register(batch: List<ProgramItem>): List<MediaItem> {
        require((items.takeLast(1) + batch).zipWithNext().none { (a, b) ->
            TransitionPolicy.resolve(a.type, b.type) == TransitionType.FORBIDDEN
        }) { "Peças curtas consecutivas não são permitidas" }
        return batch.map { program ->
            require(entries.none { it.program.id == program.id }) { "Conteúdo já presente na fila" }
            val media = mediaItem(program)
            entries += Entry(program, media)
            media
        }
    }

    fun append(batch: List<ProgramItem>) {
        val media = register(batch)
        player.addMediaItems(media)
    }

    fun updatePreload(@Suppress("UNUSED_PARAMETER") sourceAvailable: Boolean) {
        // O preload pertence à timeline do ExoPlayer; pausa/rede não removem buffers.
        loadControl.currentMediaId = player.currentMediaItem?.mediaId
    }

    /** Remover somente faixas já tocadas; manter o item/posição/buffer atuais. */
    fun trimPlayed() {
        val count = player.currentMediaItemIndex.coerceAtLeast(0)
        if (count == 0 || count >= entries.size) return
        repeat(count) { entries.removeAt(0) }
        player.removeMediaItems(0, count)
    }

    fun clearEndedQueue() {
        player.clearMediaItems()
        entries.clear()
    }

    fun release() {
        transitions.release()
        player.release()
        cache.release()
        database.close()
    }

    private fun mediaItem(item: ProgramItem): MediaItem = MediaItem.Builder()
        .setMediaId(item.id).setUri(item.audioUrl).setCustomCacheKey(audioCacheKey(item))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
            .setArtworkUri(item.artworkUrl?.let(Uri::parse))
            .setArtworkData(item.artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER).setDurationMs(item.durationMs.takeIf { it > 0 })
            .setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(if (item.type == ProgramItemType.MUSIC) MediaMetadata.MEDIA_TYPE_MUSIC
                else MediaMetadata.MEDIA_TYPE_MIXED)
            .setExtras(Bundle().apply {
                putString("license", item.license)
                putString("source", item.source)
                putString("sourceUrl", item.sourceUrl)
                putString("programType", item.type.name)
                putString("contentId", item.contentId)
            }).build()).build()
}

/** URL alterada com o mesmo ID deve usar outro conteúdo no cache, sem expor URLs em chaves. */
internal fun audioCacheKey(item: ProgramItem): String = item.contentId + ":" +
    java.security.MessageDigest.getInstance("SHA-256").digest(item.audioUrl.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
