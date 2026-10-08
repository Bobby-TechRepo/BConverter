package io.bsoft.bconverter.core.storage

import java.io.File

fun interface FreeSpaceProvider {
    fun availableBytes(dir: File): Long
}
