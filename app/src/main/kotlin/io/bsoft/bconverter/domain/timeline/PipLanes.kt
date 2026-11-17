package io.bsoft.bconverter.domain.timeline

/** Packs picture-in-picture clips into timeline rows so that clips overlapping in time never share a row. */
object PipLanes {
    /** Row index (0 = top) of every clip, by id: each clip takes the first row whose last clip has ended. Touching clips share a row. */
    fun assign(clips: List<PipClip>): Map<String, Int> {
        val rowEnds = mutableListOf<Long>()
        val rows = LinkedHashMap<String, Int>()
        clips.sortedWith(compareBy({ it.startTimeMs }, { it.id })).forEach { clip ->
            val row = rowEnds.indexOfFirst { it <= clip.startTimeMs }.let { if (it >= 0) it else rowEnds.size.also { rowEnds += 0L } }
            rowEnds[row] = clip.endTimeMs
            rows[clip.id] = row
        }
        return rows
    }
}
