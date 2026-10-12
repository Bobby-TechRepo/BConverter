package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.core.storage.FreeSpaceProvider
import java.io.File

class FakeFreeSpaceProvider(var available: Long = Long.MAX_VALUE) : FreeSpaceProvider {
    var lastDir: File? = null
    override fun availableBytes(dir: File): Long { lastDir = dir; return available }
}
