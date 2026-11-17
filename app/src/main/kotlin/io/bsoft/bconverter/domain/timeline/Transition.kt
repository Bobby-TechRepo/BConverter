package io.bsoft.bconverter.domain.timeline

enum class TransitionType {
    /** A plain cut. */
    NONE,
    /** The first clip fades out to black and the next fades in from black; the timeline length does not change. */
    FADE_BLACK,
    /** The end of the first clip and the start of the next overlap and blend; the timeline becomes shorter by the overlap. */
    CROSSFADE,
}

/** What the user chose for the cut *before* a clip. [durationMs] is the whole transition; it is shortened when the clips are too short for it. */
data class Transition(val type: TransitionType = TransitionType.NONE, val durationMs: Long = DEFAULT_MS) {
    init { require(durationMs in MIN_MS..MAX_MS) { "transition length must be within $MIN_MS..$MAX_MS ms" } }

    val isNone: Boolean get() = type == TransitionType.NONE

    companion object {
        const val MIN_MS = 250L
        const val MAX_MS = 2_000L
        const val DEFAULT_MS = 500L
        val None = Transition(TransitionType.NONE)
    }
}

/** What is really applied at a cut once clip lengths are taken into account. */
data class JoinEffect(val type: TransitionType, val durationMs: Long) {
    val isActive: Boolean get() = type != TransitionType.NONE && durationMs > 0
    /** How far the second clip moves back over the first: only a crossfade overlaps. */
    val overlapMs: Long get() = if (type == TransitionType.CROSSFADE) durationMs else 0L

    companion object { val None = JoinEffect(TransitionType.NONE, 0) }
}
