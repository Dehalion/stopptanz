// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.session

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackPositionTest {

    @Test
    fun `formats sub-minute duration`() {
        assertEquals("0:05", PlaybackPosition(5_000, 60_000).formatCurrent())
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("1:05", PlaybackPosition(65_000, 200_000).formatCurrent())
    }

    @Test
    fun `pads seconds below ten`() {
        assertEquals("2:03", PlaybackPosition(123_000, 200_000).formatCurrent())
    }

    @Test
    fun `formats total alongside current`() {
        assertEquals("3:21", PlaybackPosition(0, 201_000).formatTotal())
    }

    @Test
    fun `negative total (unknown duration) formats as zero`() {
        assertEquals("0:00", PlaybackPosition(0, -1).formatTotal())
    }
}
