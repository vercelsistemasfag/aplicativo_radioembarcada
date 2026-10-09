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
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType

/** Toca conteúdo genérico. Não consulta catálogo nem decide a ordem musical. */
@UnstableApi
class ProgramPlayer(context: Context, private val localSource: Boolean = false) {
    private data class Entry(val program: ProgramItem, val media: MediaItem)
    private val entries = mutableListOf<Entry>()
    private var baseRanking = 0
    private var currentRanking = 0
    private var preloadEnabled = false
    private val database = StandaloneDatabaseProvider(context)
    private val cache: SimpleCache
    private val preload: DefaultPreloadManager
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
        var manager: DefaultPreloadManager? = null
        val managedFactory = object : MediaSource.Factory by upstream {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource =
                manager?.getMediaSource(mediaItem) ?: if (mediaItem.localConfiguration?.uri?.scheme == "asset")
                    local.createMediaSource(mediaItem) else upstream.createMediaSource(mediaItem)
        }
        val control = TargetPreloadStatusControl<Int> { ranking ->
            if (!preloadEnabled) null else when (ranking - currentRanking) {
                1 -> DefaultPreloadManager.Status(DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS,
                    if (localSource) PlaybackConfiguration.LOCAL_NEXT_TRACK_PRELOAD_MS else PlaybackConfiguration.NEXT_TRACK_PRELOAD_MS)
                2 -> DefaultPreloadManager.Status(DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS,
                    if (localSource) PlaybackConfiguration.LOCAL_SECOND_TRACK_PRELOAD_MS else PlaybackConfiguration.SECOND_TRACK_PRELOAD_MS)
                else -> null
            }
        }
        val loadControl = if (localSource) RadioLoadControl(DefaultLoadControl.Builder()
            .setBufferDurationsMs(PlaybackConfiguration.LOCAL_MIN_BUFFER_MS, PlaybackConfiguration.LOCAL_MAX_BUFFER_MS,
                PlaybackConfiguration.LOCAL_START_BUFFER_MS, PlaybackConfiguration.LOCAL_REBUFFER_MS)
            .setPrioritizeTimeOverSizeThresholds(true).build(), localSource = true) else RadioLoadControl()
        val builder = DefaultPreloadManager.Builder(context, control)
            .setMediaSourceFactory(managedFactory).setLoadControl(loadControl)
        preload = builder.build()
        manager = preload
        player = builder.buildExoPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(if (localSource) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
        }
    }

    fun isCached(item: ProgramItem): Boolean = cache.getCachedSpans(audioCacheKey(item))
        .any { it.position == 0L && it.length > 0 }

    fun register(batch: List<ProgramItem>): List<MediaItem> = batch.map { program ->
        require(entries.none { it.program.id == program.id }) { "Conteúdo já presente na fila" }
        val media = mediaItem(program)
        preload.add(media, baseRanking + entries.size)
        entries += Entry(program, media)
        media
    }

    fun append(batch: List<ProgramItem>) {
        val media = register(batch)
        player.addMediaSources(media.map { checkNotNull(preload.getMediaSource(it)) })
    }

    fun updatePreload(sourceAvailable: Boolean) {
        val ranking = baseRanking + player.currentMediaItemIndex.coerceAtLeast(0)
        val enabled = sourceAvailable && player.playWhenReady && player.isPlaying &&
            PlaybackConfiguration.canPreload(localSource, player.totalBufferedDuration,
                if (player.duration == C.TIME_UNSET) C.TIME_UNSET else player.duration - player.currentPosition)
        if (ranking != currentRanking || enabled != preloadEnabled) {
            currentRanking = ranking
            preloadEnabled = enabled
            preload.setCurrentPlayingIndex(ranking)
            preload.invalidate()
        }
    }

    /** Remover somente faixas já tocadas; manter o item/posição/buffer atuais. */
    fun trimPlayed() {
        val count = player.currentMediaItemIndex.coerceAtLeast(0)
        if (count == 0 || count >= entries.size) return
        val old = entries.take(count)
        player.removeMediaItems(0, count)
        repeat(count) { entries.removeAt(0) }
        baseRanking += count
        old.forEach { preload.remove(it.media) }
    }

    fun clearEndedQueue() {
        player.clearMediaItems()
        entries.forEach { preload.remove(it.media) }
        baseRanking += entries.size
        entries.clear()
    }

    fun release() {
        // O builder compartilha o looper; liberar preload antes do player encerra seus leitores.
        preload.release()
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
