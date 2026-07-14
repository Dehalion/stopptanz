package dev.stopptanz.app.playlist

/** Pure `.m3u` formatting for the "Save as Playlist File" action (#22). */
object PlaylistFileWriter {

    /** Appends the `.m3u` extension unless [filename] already has it (case-insensitively). */
    fun normalizeFilename(filename: String): String {
        val trimmed = filename.trim()
        return if (trimmed.substringAfterLast('.', "").equals("m3u", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed.m3u"
        }
    }

    /** Builds minimal `.m3u` content: an `#EXTM3U` header, then one filename per line. */
    fun format(filenames: List<String>): String = buildString {
        append("#EXTM3U\n")
        filenames.forEach { append(it); append('\n') }
    }
}
