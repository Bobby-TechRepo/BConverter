package io.bsoft.bconverter.media.ffmpeg

import java.io.File

/** Joins files with identical streams, listed in [listFile] (see SegmentedRender.concatList), without re-encoding. */
object FfmpegConcat {
    fun command(listFile: File, output: File): FfmpegCommand = FfmpegCommand(
        globalOptions = listOf("-y", "-hide_banner"),
        inputs = listOf(FfmpegInput(listFile.absolutePath, FfmpegOptions(listOf("-f" to "concat", "-safe" to "0")))),
        output = FfmpegOutput(output.absolutePath, FfmpegOptions(listOf("-map" to "0", "-c" to "copy", "-f" to "matroska"))),
    )
}
