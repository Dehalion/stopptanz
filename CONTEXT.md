# Stopptanz

Android app that plays a local music file or playlist and randomly stops playback to drive a stop-dance party game. No player, chair, or score tracking — it's a music/timer engine only, not a scorekeeper.

## Language

**Session**:
One run of the game from start (playback begins) to stop (host ends it). Holds the chosen mode, the playlist, and stop-timing config.
_Avoid_: Game, round

**Freeze Dance mode**:
Music stops at a random point; after a fixed pause, playback resumes automatically without user action. The host can also manually resume early, which cancels the pending automatic resume.
_Avoid_: Musical chairs, elimination mode

**Musical Chairs mode**:
Music stops at a random point; playback stays stopped until the host explicitly resumes it (e.g. taps a button). No chairs or players are tracked by the app — the name only signals the manual-resume rule.
_Avoid_: Freeze dance, auto-resume mode

**Stop**:
A pause-point during playback, triggered either automatically by the engine at a random moment within a configurable interval range, or manually by the host at any time. What happens after a Stop depends on the mode (see Freeze Dance / Musical Chairs).
_Avoid_: Pause, break

**Stop Interval**:
The host-configurable min/max time range the engine picks a random Stop from.
_Avoid_: Timer, delay

**Playlist**:
The ordered list of one or more local music files chosen for a Session. Plays in list order and stops at the end by default; Shuffle and Loop are host-toggleable, both off by default.
_Avoid_: Queue, library

**Playlist File**:
An on-disk file (e.g. `.m3u`) that a Playlist can be opened from or saved to, letting a host reuse or share a curated track order across sessions/devices. Distinct from Playlist itself — the Playlist File is the persisted serialization, not the in-Session ordered list.
_Avoid_: Playlist (reserve for the in-Session list), Queue file

**Finished**:
The Session state reached when the Playlist plays to its end with Loop off. Playback stays stopped until the host explicitly acts (not the same as a Stop — no resume happens automatically or via the mode's normal resume rule).
_Avoid_: Ended, done

**Track**:
A single audio file within a Playlist, identified to the host by its file name with the extension stripped. No file metadata (e.g. ID3 tags) is read.
_Avoid_: Song, file

**Closed**:
The Session state reached when the host explicitly ends a Session outright (End Session action), regardless of what state it was in beforehand. Distinct from Finished (natural Playlist end) and from a Stop (a pause within an ongoing Session) — Closed always returns the host to Playlist setup.
_Avoid_: Ended, Stopped, Quit

**Skip**:
A host action jumping to the previous or next Track in the Playlist immediately, without ending the Session. Resets the Stop Interval countdown (and the pause countdown, if currently Stopped in Freeze Dance). Bound by Loop for wrap-around at the Playlist ends.
_Avoid_: Next/Previous, Advance

**Seek**:
A host action jumping playback position within the current Track forward/back by a fixed 10s step, clamped to Track bounds.
_Avoid_: Scrub, Rewind, Fast-forward
