package br.com.radioembarcada.player

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.AdPlaybackState
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.legacy.PlaybackStateCompat
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.programming.ProgrammingProvider
import br.com.radioembarcada.network.programming.RemoteProgrammingParser
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.programming.AutomaticProgramming
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class RadioSessionCallbackTest {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val controllers = mutableListOf<MediaController>()

    @Before fun setUp() {
        // Fontes sem codecs/rede: o ExoPlayer real percorre timelines curtas com clock simulado.
        player = ExoPlayer.Builder(RuntimeEnvironment.getApplication(),
            RenderersFactory { _, _, _, _, _ -> arrayOf(FakeRenderer(C.TRACK_TYPE_AUDIO)) })
            .setClock(FakeClock(true)).build()
        session = MediaLibrarySession.Builder(RuntimeEnvironment.getApplication(), player,
            RadioSessionCallback()).build()
        player.setMediaSources(listOf(source(item("music-1")), source(item("music-2"))))
        player.prepare()
        advance(player).untilState(Player.STATE_READY)
    }

    @After fun tearDown() {
        controllers.forEach { it.release() }
        if (::session.isInitialized) session.release()
        if (::player.isInitialized) player.release()
    }

    private fun connect(notification: Boolean = false): MediaController {
        val future = MediaController.Builder(RuntimeEnvironment.getApplication(), session.token)
            .setConnectionHints(Bundle().apply {
                putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, notification)
            }).buildAsync()
        runMainLooperUntil { future.isDone }
        return future.get().also { controllers += it }
    }

    private fun item(id: String, type: ProgramItemType = ProgramItemType.MUSIC) =
        ProgramItem(id, type, "Title $id", "https://example.invalid/$id", 1_000, artist = "Artist")

    private fun source(program: ProgramItem): FakeMediaSource {
        val media = MediaItem.Builder().setMediaId(program.id).setUri(program.audioUrl)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(program.title).setArtist(program.artist)
                .setMediaType(if (program.type == ProgramItemType.MUSIC) MediaMetadata.MEDIA_TYPE_MUSIC
                    else MediaMetadata.MEDIA_TYPE_MIXED).build()).build()
        return FakeMediaSource(FakeTimeline(FakeTimeline.TimelineWindowDefinition(
            1, program.id, true, false, false, false, program.durationMs * 1_000, 0, 0,
            listOf(AdPlaybackState.NONE), media)))
    }

    private fun assertRadioCommands(controller: MediaController) {
        assertTrue(controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(controller.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
        assertTrue(controller.isCommandAvailable(Player.COMMAND_GET_METADATA))
        listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD, Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SPEED_AND_PITCH, Player.COMMAND_STOP).forEach {
            assertFalse("Comando externo $it deve estar indisponível", controller.isCommandAvailable(it))
        }
    }

    @Test fun externalControllerOffersPlayPauseAndMetadataWithoutNavigationOrSeek() {
        assertRadioCommands(connect())
    }

    @Test fun notificationControllerUsesTheSameRadioCommands() {
        val controller = connect(notification = true)
        assertTrue(session.isMediaNotificationController(session.connectedControllers.single()))
        assertRadioCommands(controller)
        assertTrue(controller.mediaButtonPreferences.isEmpty())
    }

    @Test fun nativeAndroidSessionActionsOnlyOfferPlayPauseWithoutSeekOrSkip() {
        val controller = connect(notification = true)
        // Robolectric não implementa o servidor Binder de mídia: verificar o PlaybackState
        // real que Media3 grava na sessão compat e envia à sessão nativa (versão fixada 1.6.1).
        val compat = ReflectionHelpers.callInstanceMethod<Any>(session, "getSessionCompat")
        val implementation = ReflectionHelpers.getField<Any>(compat, "mImpl")
        fun actions() = ReflectionHelpers.getField<PlaybackStateCompat>(implementation, "mPlaybackState").actions
        assertTrue(actions() and PlaybackStateCompat.ACTION_PLAY != 0L)
        assertTrue(actions() and PlaybackStateCompat.ACTION_PLAY_PAUSE != 0L)
        val forbidden = PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_STOP
        assertEquals(0L, actions() and forbidden)
        controller.play()
        runMainLooperUntil { actions() and PlaybackStateCompat.ACTION_PAUSE != 0L }
        assertTrue(actions() and PlaybackStateCompat.ACTION_PLAY_PAUSE != 0L)
        assertEquals(0L, actions() and forbidden)
    }

    @Test fun nativeBluetoothStyleSkipAndSeekRequestsCannotChangeTheTrack() {
        connect(notification = true)
        val implementation = ReflectionHelpers.callInstanceMethod<Any>(session, "getImpl")
        val controller = checkNotNull(session.mediaNotificationControllerInfo)
        // Exercitar o handler real de media buttons, sem o Binder ausente no Robolectric.
        listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND).forEach { key ->
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, key))
            assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(implementation, "onMediaButtonEvent",
                ClassParameter.from(MediaSession.ControllerInfo::class.java, controller),
                ClassParameter.from(Intent::class.java, intent)))
        }
        advance(player).untilPendingCommandsAreFullyHandled()
        assertEquals("music-1", player.currentMediaItem?.mediaId)
        assertEquals(0L, player.currentPosition)
    }

    @Test fun playAndPauseStillReachThePlayer() {
        val controller = connect()
        controller.play()
        runMainLooperUntil { player.playWhenReady }
        controller.pause()
        runMainLooperUntil { !player.playWhenReady }
    }

    @Test fun externalNavigationAndSeekDoNotChangeProgrammingOrPosition() {
        val controller = connect()
        controller.seekToNext()
        controller.seekToNextMediaItem()
        controller.seekToPrevious()
        controller.seekToPreviousMediaItem()
        controller.seekTo(1, 500)
        controller.seekTo(500)
        controller.seekBack()
        controller.seekForward()
        controller.seekToDefaultPosition(1)
        controller.setShuffleModeEnabled(true)
        controller.setRepeatMode(Player.REPEAT_MODE_ONE)
        advance(player).untilPendingCommandsAreFullyHandled()
        assertEquals("music-1", player.currentMediaItem?.mediaId)
        assertEquals(0L, player.currentPosition)
        assertFalse(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }

    @Test fun internalPlayerCanAdvanceWhileExternalNextRemainsUnavailable() {
        val controller = connect()
        assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        player.seekToNextMediaItem()
        advance(player).untilPendingCommandsAreFullyHandled()
        assertEquals("music-2", player.currentMediaItem?.mediaId)
        assertRadioCommands(controller)
    }

    @Test fun metadataIsExposedAndUpdatedAfterInternalAdvance() {
        val controller = connect(notification = true)
        assertEquals("Title music-1", controller.mediaMetadata.title)
        assertEquals("Artist", controller.mediaMetadata.artist)
        player.seekToNextMediaItem()
        runMainLooperUntil { controller.currentMediaItem?.mediaId == "music-2" }
        assertEquals("Title music-2", controller.mediaMetadata.title)
    }

    @Test fun transitionControllerPreservesFullPiecesAndImmediateNaturalAdvance() {
        val items = listOf(item("m1").copy(durationMs = 30_000),
            item("s1", ProgramItemType.STATION_ID).copy(durationMs = 8_000),
            item("m2").copy(durationMs = 30_000),
            item("s2", ProgramItemType.STATION_ID).copy(durationMs = 12_000),
            item("m3").copy(durationMs = 30_000),
            item("j1", ProgramItemType.JINGLE).copy(durationMs = 20_000), item("m4"))
        val logs = mutableListOf<String>()
        val control = AudioTransitionController(player,
            current = { items.find { it.id == player.currentMediaItem?.mediaId } },
            next = { items.getOrNull(player.currentMediaItemIndex + 1) },
            readiness = { PlaybackReadiness.Snapshot() }, diagnostic = logs::add)
        val boundaries = mutableListOf<Pair<String?, Long>>()
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    boundaries += oldPosition.mediaItem?.mediaId to oldPosition.positionMs
                    assertEquals(0L, newPosition.positionMs)
                }
            }
        })
        try {
            player.setMediaSources(items.map(::source)); player.prepare()
            advance(player).untilState(Player.STATE_READY)
            player.play()
            advance(player).untilPosition(0, 20_000)
            player.pause()
            advance(player).untilPendingCommandsAreFullyHandled()
            val pausedPosition = player.currentPosition
            assertEquals("m1", player.currentMediaItem?.mediaId)
            assertEquals(TransitionPolicy.volume(ProgramItemType.MUSIC, ProgramItemType.STATION_ID,
                pausedPosition, 30_000), player.volume, 0.001f)
            player.play()
            advance(player).untilState(Player.STATE_ENDED)
            for (id in listOf("s1", "s2", "j1")) {
                assertEquals(items.single { it.id == id }.durationMs, boundaries.single { it.first == id }.second)
            }
            assertEquals(items.dropLast(1).map { it.id }, boundaries.map { it.first })
            assertTrue(logs.any { "music fade: 15000 ms" in it })
            assertTrue(logs.any { "Peça concluída por avanço natural=true" in it })
        } finally { control.release() }
    }

    @Test fun naturalEndContinuesThroughMusicStationIdAndJingleWithoutExternalSeek() = runBlocking {
        val provider = object : MusicProvider {
            override val providesCompleteCatalog = true
            override suspend fun fetchTracks(limit: Int, offset: Int) = (1..9).map {
                Track("$it", "Music $it", "Artist $it", "$it", null,
                    "https://example.invalid/$it", 1_000, "", "Fixture", "")
            }
        }
        val programming = AutomaticProgramming(provider, programmingProvider = ProgrammingProvider {
            RemoteProgrammingParser.parse(checkNotNull(javaClass.getResource("/programming.json")).readText())
        })
        val program = programming.nextBatch()
        assertEquals(List(9) { if (it % 3 == 2) ProgramItemType.JINGLE else ProgramItemType.STATION_ID },
            program.filter { it.type != ProgramItemType.MUSIC }.map { it.type })
        player.setMediaSources(program.map(::source))
        val transitions = mutableListOf<Pair<String?, Int>>()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitions += mediaItem?.mediaId to reason
            }
        })
        player.prepare()
        val controller = connect(notification = true)
        assertRadioCommands(controller)
        controller.play()
        advance(player).untilState(Player.STATE_ENDED)
        assertEquals(program.drop(1).map { it.id }, transitions.filter {
            it.second == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        }.map { it.first })
        val next = programming.nextBatch(program.last())
        assertTrue(next.isNotEmpty())
        assertNotEquals(program.last().id, next.first().id)
    }
}
