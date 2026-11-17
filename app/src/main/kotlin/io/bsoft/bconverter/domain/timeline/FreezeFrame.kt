package io.bsoft.bconverter.domain.timeline

/** A freeze frame is a video clip that shows one picture of its source for a set time, in silence. */
object FreezeFrame {
    const val MIN_MS = 500L
    const val MAX_MS = 10_000L
    const val DEFAULT_MS = 2_000L

    /** How much of the source a freeze clip keeps as its input, so the export can read the picture at the chosen time. */
    const val INPUT_WINDOW_MS = 200L

    /** The preview cannot hold a picture, so it plays this share of the freeze's length in slow motion, at [PREVIEW_SPEED]. */
    const val PREVIEW_SPEED = 0.1f
}
