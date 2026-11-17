package io.bsoft.bconverter.domain.audio

/** How one clip's sound is shaped: its level and the fades at its two ends. The same for a video clip's own sound and an audio clip. */
data class ClipSound(val volume: Float = 1f, val fadeInMs: Long = 0, val fadeOutMs: Long = 0) {
    init {
        require(volume.isFinite() && volume in MIN_VOLUME..MAX_VOLUME) { "volume must be within $MIN_VOLUME..$MAX_VOLUME" }
        require(fadeInMs in 0..MAX_FADE_MS && fadeOutMs in 0..MAX_FADE_MS) { "fades must be within 0..$MAX_FADE_MS ms" }
    }

    val isDefault: Boolean get() = this == Default

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 2f
        const val MAX_FADE_MS = 10_000L
        val Default = ClipSound()

        /** The fades that fit a clip of [durationMs]: each at most the clip, and together at most the clip (the fade-in wins). */
        fun fit(fadeInMs: Long, fadeOutMs: Long, durationMs: Long): Pair<Long, Long> {
            val inMs = fadeInMs.coerceIn(0, maxOf(durationMs, 0))
            val outMs = fadeOutMs.coerceIn(0, maxOf(durationMs - inMs, 0))
            return inMs to outMs
        }
    }
}
