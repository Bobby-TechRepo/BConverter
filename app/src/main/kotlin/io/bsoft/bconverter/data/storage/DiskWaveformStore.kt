package io.bsoft.bconverter.data.storage

import io.bsoft.bconverter.domain.media.Waveform
import io.bsoft.bconverter.domain.media.WaveformCodec
import io.bsoft.bconverter.domain.media.WaveformStore
import java.io.File
import java.security.MessageDigest

/** Waveforms as tiny files in a cache directory; unreadable entries count as misses. */
class DiskWaveformStore(private val dir: File) : WaveformStore {
    private fun fileFor(key: String): File {
        dir.mkdirs()
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.wave")
    }

    override fun get(key: String): Waveform? = try {
        val f = fileFor(key)
        if (f.isFile) WaveformCodec.decode(f.readBytes()) else null
    } catch (e: java.io.IOException) { null }

    override fun put(key: String, waveform: Waveform) {
        val f = fileFor(key)
        val temp = File(f.path + ".part")
        temp.writeBytes(WaveformCodec.encode(waveform))
        f.delete()
        temp.renameTo(f)
    }
}
