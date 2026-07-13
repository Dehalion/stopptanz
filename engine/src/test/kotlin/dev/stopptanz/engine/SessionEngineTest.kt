package dev.stopptanz.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionEngineTest {

    private fun engine(
        mode: Mode,
        minMillis: Long = 5_000,
        maxMillis: Long = 15_000,
        pauseDurationMillis: Long = 5_000,
        playlist: Playlist = Playlist(tracks = listOf("track1.mp3", "track2.mp3")),
        randomSource: RandomSource = RandomSource { min, max -> min + (max - min) / 2 },
    ) = SessionEngine(
        playlist = playlist,
        mode = mode,
        stopInterval = StopInterval(minMillis, maxMillis),
        pauseDurationMillis = pauseDurationMillis,
        randomSource = randomSource,
    )

    @Test
    fun `next stop delay is within configured interval bounds`() {
        val delay = engine(Mode.FREEZE_DANCE).nextStopDelayMillis()
        assertTrue(delay in 5_000..15_000)
    }

    @Test
    fun `stop transitions from Playing to Stopped`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        assertEquals(SessionState.Stopped, e.state)
    }

    @Test
    fun `musical chairs mode allows manual resume from Stopped`() {
        val e = engine(Mode.MUSICAL_CHAIRS)
        e.stop()
        e.resume()
        assertEquals(SessionState.Playing, e.state)
    }

    @Test
    fun `freeze dance mode rejects manual resume`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        assertFailsWith<IllegalStateException> { e.resume() }
    }

    @Test
    fun `freeze dance mode auto-resumes on pause elapsed`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        e.onPauseElapsed()
        assertEquals(SessionState.Playing, e.state)
    }

    @Test
    fun `musical chairs mode rejects auto-resume`() {
        val e = engine(Mode.MUSICAL_CHAIRS)
        e.stop()
        assertFailsWith<IllegalStateException> { e.onPauseElapsed() }
    }

    @Test
    fun `onPauseElapsed rejects when not Stopped`() {
        val e = engine(Mode.FREEZE_DANCE)
        assertFailsWith<IllegalStateException> { e.onPauseElapsed() }
    }

    @Test
    fun `pauseDurationMillis is exposed for adapter scheduling`() {
        val e = engine(Mode.FREEZE_DANCE, pauseDurationMillis = 7_000)
        assertEquals(7_000, e.pauseDurationMillis)
    }

    @Test
    fun `orderedTracks preserves playlist order when shuffle is off`() {
        val playlist = Playlist(tracks = listOf("a.mp3", "b.mp3", "c.mp3"), shuffle = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(listOf("a.mp3", "b.mp3", "c.mp3"), e.orderedTracks)
    }

    @Test
    fun `orderedTracks is a seeded permutation when shuffle is on`() {
        val playlist = Playlist(tracks = listOf("a.mp3", "b.mp3", "c.mp3", "d.mp3"), shuffle = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist, randomSource = RandomSource { min, _ -> min })
        assertEquals(listOf("b.mp3", "c.mp3", "d.mp3", "a.mp3"), e.orderedTracks)
    }

    @Test
    fun `onPlaylistEnded transitions to Finished when loop is off`() {
        val playlist = Playlist(tracks = listOf("a.mp3"), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onPlaylistEnded()
        assertEquals(SessionState.Finished, e.state)
    }

    @Test
    fun `onPlaylistEnded stays Playing when loop is on`() {
        val playlist = Playlist(tracks = listOf("a.mp3"), loop = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onPlaylistEnded()
        assertEquals(SessionState.Playing, e.state)
    }

    @Test
    fun `close transitions from Playing to Closed`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.close()
        assertEquals(SessionState.Closed, e.state)
    }

    @Test
    fun `close transitions from Stopped to Closed`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        e.close()
        assertEquals(SessionState.Closed, e.state)
    }

    @Test
    fun `close transitions from Finished to Closed`() {
        val playlist = Playlist(tracks = listOf("a.mp3"), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onPlaylistEnded()
        e.close()
        assertEquals(SessionState.Closed, e.state)
    }

    @Test
    fun `close rejects when already Closed`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.close()
        assertFailsWith<IllegalStateException> { e.close() }
    }

    @Test
    fun `Finished state rejects Stop, Resume, and onPauseElapsed`() {
        val playlist = Playlist(tracks = listOf("a.mp3"), loop = false)
        val e = engine(Mode.MUSICAL_CHAIRS, playlist = playlist)
        e.onPlaylistEnded()
        assertFailsWith<IllegalStateException> { e.stop() }
        assertFailsWith<IllegalStateException> { e.resume() }
        assertFailsWith<IllegalStateException> { e.onPauseElapsed() }
    }
}
