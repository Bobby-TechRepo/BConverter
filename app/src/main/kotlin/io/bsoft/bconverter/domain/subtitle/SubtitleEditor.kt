package io.bsoft.bconverter.domain.subtitle

enum class EditProblem { NoSuchCue, DuplicateId, InvalidTiming, NoSuchStyle }

sealed interface SubtitleEdit {
    data class Ok(val track: SubtitleTrack) : SubtitleEdit
    data class Rejected(val reason: EditProblem) : SubtitleEdit
}

/** Non-destructive, pure edits of a [SubtitleTrack]: every call returns a new track and leaves the input untouched. */
object SubtitleEditor {
    private fun ok(track: SubtitleTrack, cues: List<SubtitleCue>) = SubtitleEdit.Ok(track.copy(cues = cues.sortedBy { it.startMs }))
    private fun no(p: EditProblem) = SubtitleEdit.Rejected(p)
    private fun SubtitleTrack.find(id: String) = cues.firstOrNull { it.id == id }
    private fun SubtitleTrack.replace(id: String, f: (SubtitleCue) -> SubtitleCue) = cues.map { if (it.id == id) f(it) else it }

    fun addCue(track: SubtitleTrack, cue: SubtitleCue): SubtitleEdit =
        if (track.find(cue.id) != null) no(EditProblem.DuplicateId) else ok(track, track.cues + cue)

    fun editText(track: SubtitleTrack, id: String, text: String): SubtitleEdit =
        if (track.find(id) == null) no(EditProblem.NoSuchCue) else ok(track, track.replace(id) { it.copy(text = text) })

    /** Moves by [deltaMs]; stops at time zero and keeps the cue's duration. */
    fun moveCue(track: SubtitleTrack, id: String, deltaMs: Long): SubtitleEdit {
        val cue = track.find(id) ?: return no(EditProblem.NoSuchCue)
        val start = (cue.startMs + deltaMs).coerceAtLeast(0)
        return ok(track, track.replace(id) { it.copy(startMs = start, endMs = start + cue.durationMs) })
    }

    fun retime(track: SubtitleTrack, id: String, startMs: Long, endMs: Long): SubtitleEdit {
        if (track.find(id) == null) return no(EditProblem.NoSuchCue)
        if (SubtitleTiming.validate(startMs, endMs) != null) return no(EditProblem.InvalidTiming)
        return ok(track, track.replace(id) { it.copy(startMs = startMs, endMs = endMs) })
    }

    /** The copy starts where the original ends and lasts as long. */
    fun duplicateCue(track: SubtitleTrack, id: String, newId: String): SubtitleEdit {
        val cue = track.find(id) ?: return no(EditProblem.NoSuchCue)
        if (track.find(newId) != null) return no(EditProblem.DuplicateId)
        return ok(track, track.cues + cue.copy(id = newId, startMs = cue.endMs, endMs = cue.endMs + cue.durationMs))
    }

    fun deleteCue(track: SubtitleTrack, id: String): SubtitleEdit =
        if (track.find(id) == null) no(EditProblem.NoSuchCue) else ok(track, track.cues.filterNot { it.id == id })

    /** Both halves keep the full text so nothing is lost; the user then trims each. */
    fun splitCue(track: SubtitleTrack, id: String, atMs: Long, newId: String): SubtitleEdit {
        val cue = track.find(id) ?: return no(EditProblem.NoSuchCue)
        if (track.find(newId) != null) return no(EditProblem.DuplicateId)
        if (atMs <= cue.startMs || atMs >= cue.endMs) return no(EditProblem.InvalidTiming)
        return ok(track, track.replace(id) { it.copy(endMs = atMs) } + cue.copy(id = newId, startMs = atMs))
    }

    /** The merged cue keeps the earlier cue's id, style and override; texts are joined with a line break. */
    fun mergeCues(track: SubtitleTrack, firstId: String, secondId: String): SubtitleEdit {
        val x = track.find(firstId) ?: return no(EditProblem.NoSuchCue)
        val y = track.find(secondId) ?: return no(EditProblem.NoSuchCue)
        if (x.id == y.id) return no(EditProblem.InvalidTiming)
        val (first, second) = listOf(x, y).sortedBy { it.startMs }
        val merged = first.copy(endMs = maxOf(first.endMs, second.endMs), text = first.text + "\n" + second.text)
        return ok(track, track.cues.filterNot { it.id == second.id }.map { if (it.id == first.id) merged else it })
    }

    /** The style cues use unless they name another: "Default", else the first style, else a plain default. */
    fun baseStyle(track: SubtitleTrack): SubtitleStyle =
        track.styles[SubtitleStyle.DEFAULT_NAME] ?: track.styles.values.firstOrNull() ?: SubtitleStyle()

    /** Replaces the base style's look; its name stays so cues that point at it keep working. */
    fun updateBaseStyle(track: SubtitleTrack, style: SubtitleStyle): SubtitleEdit {
        val name = if (SubtitleStyle.DEFAULT_NAME in track.styles || track.styles.isEmpty()) SubtitleStyle.DEFAULT_NAME else track.styles.keys.first()
        return SubtitleEdit.Ok(track.copy(styles = LinkedHashMap(track.styles).apply { put(name, style.copy(name = name)) }))
    }

    fun applyPreset(track: SubtitleTrack, preset: SubtitleStylePreset): SubtitleEdit = updateBaseStyle(track, preset.style)

    /** [styleName] null means "use the base style". */
    fun setCueStyle(track: SubtitleTrack, cueId: String, styleName: String?): SubtitleEdit {
        if (track.find(cueId) == null) return no(EditProblem.NoSuchCue)
        if (styleName != null && styleName !in track.styles) return no(EditProblem.NoSuchStyle)
        return ok(track, track.replace(cueId) { it.copy(styleName = styleName) })
    }

    fun setOffset(track: SubtitleTrack, offsetMs: Long): SubtitleEdit = SubtitleEdit.Ok(track.copy(offsetMs = offsetMs))
}
