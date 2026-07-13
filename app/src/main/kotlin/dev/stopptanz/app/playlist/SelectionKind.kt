package dev.stopptanz.app.playlist

enum class SelectionKind {
    FOLDER,
    TRACK;

    companion object {
        /** Blank or unrecognized values default to FOLDER so pre-existing persisted folder URIs keep loading. */
        fun fromStored(value: String): SelectionKind = entries.firstOrNull { it.name == value } ?: FOLDER
    }
}
