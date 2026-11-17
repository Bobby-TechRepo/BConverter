package io.bsoft.bconverter.media.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the device FFmpeg's own list of filters (no rendering, so it cannot crash): every filter the clip looks use must be there. */
class FilterAvailabilityTest {
    private val listed: String by lazy { FFmpegKit.execute("-hide_banner -filters").output.orEmpty() }
    private fun has(name: String) = Regex("""^\s*\S+\s+$name\s""", RegexOption.MULTILINE).containsMatchIn(listed)

    @Test fun every_filter_the_looks_use_is_in_this_build() {
        val needed = io.bsoft.bconverter.domain.filter.ClipFilterFilters.USED_FILTERS + io.bsoft.bconverter.domain.transform.ClipTransformFilters.USED_FILTERS + io.bsoft.bconverter.domain.transform.KenBurnsFilters.USED_FILTERS + listOf("scale", "pad", "overlay", "fade", "format", "volume", "afade", "adelay", "amix", "atempo", "reverse", "areverse", "tpad", "trim", "fps", "split", "crop", "gblur", "chromakey", "geq", "rotate")
        val missing = needed.filterNot(::has)
        assertTrue("missing from this FFmpeg build: $missing\n$listed", missing.isEmpty())
    }
}
