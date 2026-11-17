package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.media.WaveformFailure
import io.bsoft.bconverter.domain.media.WaveformGenerator
import io.bsoft.bconverter.domain.media.WaveformReducer
import io.bsoft.bconverter.domain.media.WaveformResult
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Decodes the audio to low-rate mono PCM with FFmpeg, then reduces it to peaks. The PCM file is temporary. */
class FfmpegWaveformGenerator(
    private val executor: FfmpegExecutor,
    private val tempDir: File,
    private val sampleRate: Int = 8_000,
    private val peaksPerSecond: Int = 20,
) : WaveformGenerator {
    override suspend fun generate(source: File): WaveformResult {
        if (!source.isFile) return WaveformResult.Failure(WaveformFailure.SourceMissing)
        tempDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val pcm = File(tempDir, "wf-$id.pcm")
        val args = listOf("-nostdin", "-y", "-i", source.absolutePath, "-vn", "-ac", "1", "-ar", sampleRate.toString(), "-f", "s16le", pcm.absolutePath)
        return try {
            when (executor.execute("waveform-$id", args) { _, _ -> }) {
                FfmpegExecResult.Success ->
                    if (pcm.isFile) pcm.inputStream().buffered().use { WaveformResult.Success(WaveformReducer.reduce(it, sampleRate, peaksPerSecond)) }
                    else WaveformResult.Failure(WaveformFailure.Unreadable)
                is FfmpegExecResult.Failure -> WaveformResult.Failure(WaveformFailure.Unreadable)
                FfmpegExecResult.Cancelled -> throw CancellationException("waveform analysis cancelled")
            }
        } catch (e: IOException) {
            WaveformResult.Failure(WaveformFailure.Unknown)
        } finally {
            pcm.delete()
        }
    }
}
