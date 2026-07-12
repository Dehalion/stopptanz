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
    ) = SessionEngine(
        playlist = Playlist(tracks = listOf("track1.mp3", "track2.mp3")),
        mode = mode,
        stopInterval = StopInterval(minMillis, maxMillis),
        pauseDurationMillis = pauseDurationMillis,
        randomSource = RandomSource { min, max -> min + (max - min) / 2 },
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
}
