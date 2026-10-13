package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.core.storage.FreeSpaceProvider
import io.bsoft.bconverter.core.storage.StagingDirectory

sealed interface StorageCheck {
    data object Sufficient : StorageCheck
    data class Insufficient(val requiredBytes: Long, val availableBytes: Long) : StorageCheck
}

/** Checks the volume holding the temporary output (the app cache) before a job starts. */
class StorageGuard(private val freeSpace: FreeSpaceProvider, private val staging: StagingDirectory) {
    fun check(estimatedOutputBytes: Long): StorageCheck {
        val required = estimatedOutputBytes + HEADROOM_BYTES
        val available = freeSpace.availableBytes(staging.root)
        return if (available >= required) StorageCheck.Sufficient else StorageCheck.Insufficient(required, available)
    }

    companion object { const val HEADROOM_BYTES = 32L * 1024 * 1024 }
}
