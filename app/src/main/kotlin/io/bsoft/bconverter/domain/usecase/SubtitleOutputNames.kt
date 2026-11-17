package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.SubtitleOutput
import io.bsoft.bconverter.domain.model.SubtitleStream

/** `<base>.<lang>[.forced].<ext>`, made unique within [used] by inserting `.2`, `.3`… before the extension. */
object SubtitleOutputNames {
    fun fileName(base: String, stream: SubtitleStream, ordinal: Int, output: SubtitleOutput, used: MutableSet<String>): String {
        val stem = buildString {
            append(base)
            stream.language?.takeIf { it.isNotBlank() }?.let { append('.').append(it) }
            if (stream.isForced) append(".forced")
        }
        var candidate = "$stem.${output.extension}"
        var n = 2
        while (!used.add(candidate.lowercase())) candidate = "$stem.${n++}.${output.extension}"
        return candidate
    }
}
