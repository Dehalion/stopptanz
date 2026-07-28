// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.playlist

import com.stopptanz.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackReviewTest {

    private fun track(name: String) = Track(uri = "content://$name", name = name)

    private val tracks = listOf(track("a"), track("b"), track("c"))

    @Test
    fun `moveUp swaps with previous track`() {
        assertEquals(listOf(track("b"), track("a"), track("c")), TrackReview.moveUp(tracks, 1))
    }

    @Test
    fun `moveUp at index 0 is a no-op`() {
        assertEquals(tracks, TrackReview.moveUp(tracks, 0))
    }

    @Test
    fun `moveUp with out-of-range index is a no-op`() {
        assertEquals(tracks, TrackReview.moveUp(tracks, 3))
        assertEquals(tracks, TrackReview.moveUp(tracks, -1))
    }

    @Test
    fun `moveDown swaps with next track`() {
        assertEquals(listOf(track("a"), track("c"), track("b")), TrackReview.moveDown(tracks, 1))
    }

    @Test
    fun `moveDown at last index is a no-op`() {
        assertEquals(tracks, TrackReview.moveDown(tracks, 2))
    }

    @Test
    fun `moveDown with out-of-range index is a no-op`() {
        assertEquals(tracks, TrackReview.moveDown(tracks, 3))
        assertEquals(tracks, TrackReview.moveDown(tracks, -1))
    }

    @Test
    fun `remove drops the track at index`() {
        assertEquals(listOf(track("a"), track("c")), TrackReview.remove(tracks, 1))
    }

    @Test
    fun `remove with out-of-range index is a no-op`() {
        assertEquals(tracks, TrackReview.remove(tracks, 3))
        assertEquals(tracks, TrackReview.remove(tracks, -1))
    }

    @Test
    fun `operations on an empty list are no-ops`() {
        assertEquals(emptyList(), TrackReview.moveUp(emptyList(), 0))
        assertEquals(emptyList(), TrackReview.moveDown(emptyList(), 0))
        assertEquals(emptyList(), TrackReview.remove(emptyList(), 0))
    }
}
