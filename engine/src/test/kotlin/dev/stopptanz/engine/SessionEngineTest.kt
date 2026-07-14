package dev.stopptanz.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionEngineTest {

    private fun track(name: String) = Track(uri = "content://$name.mp3", name = name)

    private fun engine(
        mode: Mode,
        minMillis: Long = 5_000,
        maxMillis: Long = 15_000,
        pauseDurationMillis: Long = 5_000,
        playlist: Playlist = Playlist(tracks = listOf(track("track1"), track("track2"))),
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
    fun `freeze dance mode allows manual resume from Stopped`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        e.resume()
        assertEquals(SessionState.Playing, e.state)
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
    fun `setStopInterval does not alter a delay already computed, only the next call`() {
        val e = engine(Mode.FREEZE_DANCE, minMillis = 5_000, maxMillis = 5_000)
        val inFlightDelay = e.nextStopDelayMillis()
        e.setStopInterval(StopInterval(20_000, 20_000))
        assertEquals(5_000, inFlightDelay)
        assertEquals(20_000, e.nextStopDelayMillis())
    }

    @Test
    fun `setPauseDurationMillis does not alter a value already captured, only the next read`() {
        val e = engine(Mode.FREEZE_DANCE, pauseDurationMillis = 5_000)
        val capturedForInFlightPause = e.pauseDurationMillis
        e.setPauseDurationMillis(9_000)
        assertEquals(5_000, capturedForInFlightPause)
        assertEquals(9_000, e.pauseDurationMillis)
    }

    @Test
    fun `orderedTracks preserves playlist order when shuffle is off`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")), shuffle = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(listOf(track("a"), track("b"), track("c")), e.orderedTracks)
    }

    @Test
    fun `orderedTracks is a seeded permutation when shuffle is on`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c"), track("d")), shuffle = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist, randomSource = RandomSource { min, _ -> min })
        assertEquals(listOf(track("b"), track("c"), track("d"), track("a")), e.orderedTracks)
    }

    @Test
    fun `onPlaylistEnded transitions to Finished when loop is off`() {
        val playlist = Playlist(tracks = listOf(track("a")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onPlaylistEnded()
        assertEquals(SessionState.Finished, e.state)
    }

    @Test
    fun `onPlaylistEnded stays Playing when loop is on`() {
        val playlist = Playlist(tracks = listOf(track("a")), loop = true)
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
        val playlist = Playlist(tracks = listOf(track("a")), loop = false)
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
        val playlist = Playlist(tracks = listOf(track("a")), loop = false)
        val e = engine(Mode.MUSICAL_CHAIRS, playlist = playlist)
        e.onPlaylistEnded()
        assertFailsWith<IllegalStateException> { e.stop() }
        assertFailsWith<IllegalStateException> { e.resume() }
        assertFailsWith<IllegalStateException> { e.onPauseElapsed() }
    }

    @Test
    fun `currentTrack starts at the first orderedTrack`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")))
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(track("a"), e.currentTrack)
    }

    @Test
    fun `onTrackAdvanced moves currentTrack to the next orderedTrack`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")))
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onTrackAdvanced()
        assertEquals(track("b"), e.currentTrack)
    }

    @Test
    fun `onTrackAdvanced rejects advancing past the last orderedTrack when loop is off`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onTrackAdvanced()
        assertFailsWith<IllegalStateException> { e.onTrackAdvanced() }
    }

    @Test
    fun `onTrackAdvanced wraps back to the first orderedTrack when looping`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b")), loop = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onTrackAdvanced()
        e.onTrackAdvanced()
        assertEquals(track("a"), e.currentTrack)
    }

    @Test
    fun `onTrackAdvanced rejects when not Playing`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        assertFailsWith<IllegalStateException> { e.onTrackAdvanced() }
    }

    @Test
    fun `nextTrack is the following orderedTrack, null on the last Track`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b")))
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(track("b"), e.nextTrack)
        e.onTrackAdvanced()
        assertEquals(null, e.nextTrack)
    }

    @Test
    fun `remainingTracks is a true countdown when loop is off`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(TrackRemaining.Countdown(2), e.remainingTracks)
        e.onTrackAdvanced()
        assertEquals(TrackRemaining.Countdown(1), e.remainingTracks)
    }

    @Test
    fun `remainingTracks is a Track-of-N position when loop is on`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")), loop = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(TrackRemaining.Position(1, 3), e.remainingTracks)
        e.onTrackAdvanced()
        assertEquals(TrackRemaining.Position(2, 3), e.remainingTracks)
    }

    @Test
    fun `skipToNext moves currentTrack forward`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")))
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.skipToNext()
        assertEquals(track("b"), e.currentTrack)
    }

    @Test
    fun `skipToPrevious moves currentTrack backward`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")))
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.skipToNext()
        e.skipToPrevious()
        assertEquals(track("a"), e.currentTrack)
    }

    @Test
    fun `skipToNext is a no-op at the last orderedTrack when loop is off`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.skipToNext()
        e.skipToNext()
        assertEquals(track("b"), e.currentTrack)
    }

    @Test
    fun `skipToPrevious is a no-op at the first orderedTrack when loop is off`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.skipToPrevious()
        assertEquals(track("a"), e.currentTrack)
    }

    @Test
    fun `skipToNext wraps to the first orderedTrack when looping`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b")), loop = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.skipToNext()
        e.skipToNext()
        assertEquals(track("a"), e.currentTrack)
    }

    @Test
    fun `skipToPrevious wraps to the last orderedTrack when looping`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b")), loop = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.skipToPrevious()
        assertEquals(track("b"), e.currentTrack)
    }

    @Test
    fun `skipToNext and skipToPrevious leave state unchanged`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")))
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.skipToNext()
        assertEquals(SessionState.Playing, e.state)
        e.stop()
        e.skipToPrevious()
        assertEquals(SessionState.Stopped, e.state)
    }

    @Test
    fun `skipToNext and skipToPrevious work while Stopped`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")))
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.stop()
        e.skipToNext()
        assertEquals(track("b"), e.currentTrack)
    }

    @Test
    fun `skipToNext and skipToPrevious reject when not Playing or Stopped`() {
        val playlist = Playlist(tracks = listOf(track("a")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onPlaylistEnded()
        assertFailsWith<IllegalStateException> { e.skipToNext() }
        assertFailsWith<IllegalStateException> { e.skipToPrevious() }
    }

    @Test
    fun `canSkipPrevious and canSkipNext are false at the ends when loop is off`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(false, e.canSkipPrevious)
        assertEquals(true, e.canSkipNext)
        e.skipToNext()
        e.skipToNext()
        assertEquals(true, e.canSkipPrevious)
        assertEquals(false, e.canSkipNext)
    }

    @Test
    fun `canSkipPrevious and canSkipNext are true at the ends when loop is on`() {
        val playlist = Playlist(tracks = listOf(track("a"), track("b"), track("c")), loop = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(true, e.canSkipPrevious)
        assertEquals(true, e.canSkipNext)
    }

    @Test
    fun `canSkipPrevious and canSkipNext are false with a single Track regardless of loop`() {
        val playlist = Playlist(tracks = listOf(track("a")), loop = true)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        assertEquals(false, e.canSkipPrevious)
        assertEquals(false, e.canSkipNext)
    }

    @Test
    fun `pause from Playing then resumeFromPause returns to Playing`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.pause()
        assertEquals(SessionState.Paused(SessionState.Playing), e.state)
        val remaining = e.resumeFromPause()
        assertEquals(SessionState.Playing, e.state)
        assertEquals(null, remaining)
    }

    @Test
    fun `pause from Stopped mid-countdown then resumeFromPause restores Stopped with remaining time`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        e.pause(remainingFreezeMillis = 3_000)
        assertEquals(SessionState.Paused(SessionState.Stopped, 3_000), e.state)
        val remaining = e.resumeFromPause()
        assertEquals(SessionState.Stopped, e.state)
        assertEquals(3_000, remaining)
    }

    @Test
    fun `onPauseElapsed rejects while Paused, so the freeze auto-resume timer cannot fire`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        e.pause(remainingFreezeMillis = 3_000)
        assertFailsWith<IllegalStateException> { e.onPauseElapsed() }
    }

    @Test
    fun `pause rejects when not Playing or Stopped`() {
        val playlist = Playlist(tracks = listOf(track("a")), loop = false)
        val e = engine(Mode.FREEZE_DANCE, playlist = playlist)
        e.onPlaylistEnded()
        assertFailsWith<IllegalStateException> { e.pause() }
    }

    @Test
    fun `pause rejects when already Paused`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.pause()
        assertFailsWith<IllegalStateException> { e.pause() }
    }

    @Test
    fun `resumeFromPause rejects when not Paused`() {
        val e = engine(Mode.FREEZE_DANCE)
        assertFailsWith<IllegalStateException> { e.resumeFromPause() }
    }

    @Test
    fun `pause from Stopped in Freeze Dance mode requires remainingFreezeMillis so the countdown can't be silently lost`() {
        val e = engine(Mode.FREEZE_DANCE)
        e.stop()
        assertFailsWith<IllegalStateException> { e.pause() }
    }

    @Test
    fun `pause rejects remainingFreezeMillis when pausing from Playing`() {
        val e = engine(Mode.FREEZE_DANCE)
        assertFailsWith<IllegalStateException> { e.pause(remainingFreezeMillis = 3_000) }
    }

    @Test
    fun `pause rejects remainingFreezeMillis when pausing from Stopped in Musical Chairs mode`() {
        val e = engine(Mode.MUSICAL_CHAIRS)
        e.stop()
        assertFailsWith<IllegalStateException> { e.pause(remainingFreezeMillis = 3_000) }
    }
}
