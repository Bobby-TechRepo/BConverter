package io.bsoft.bconverter.domain.timeline

/** What the preview can show of a transition: a fade through black is drawn exactly, as a black layer over the picture. */
object TransitionPreview {
    /** 0 (nothing) to 1 (fully black) at timeline time [positionMs]: ramps up over the first half of a fade through black and down over the second. */
    fun blackAlpha(timeline: Timeline, positionMs: Long): Float {
        for (i in 1 until timeline.videoClips.size) {
            val join = timeline.joinBefore(i)
            if (join.type != TransitionType.FADE_BLACK) continue
            val half = join.durationMs / 2f
            val distance = kotlin.math.abs(positionMs - timeline.videoClips[i].startTimeMs)
            if (distance < half) return 1f - distance / half
        }
        return 0f
    }
}
