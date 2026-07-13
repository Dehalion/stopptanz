package dev.stopptanz.app.playlist

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionKindTest {

    @Test
    fun `parses FOLDER`() {
        assertEquals(SelectionKind.FOLDER, SelectionKind.fromStored("FOLDER"))
    }

    @Test
    fun `parses TRACK`() {
        assertEquals(SelectionKind.TRACK, SelectionKind.fromStored("TRACK"))
    }

    @Test
    fun `blank value defaults to FOLDER for pre-existing persisted selections`() {
        assertEquals(SelectionKind.FOLDER, SelectionKind.fromStored(""))
    }

    @Test
    fun `unrecognized value defaults to FOLDER`() {
        assertEquals(SelectionKind.FOLDER, SelectionKind.fromStored("bogus"))
    }
}
