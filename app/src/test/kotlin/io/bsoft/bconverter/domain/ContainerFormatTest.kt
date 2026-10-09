package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ContainerFormatTest {
    @Test fun file_extensions() = assertEquals(
        listOf("mp4", "mkv", "3gp", "webm", "mov", "avi", "ts"), ContainerFormat.entries.map { it.extension },
    )
}
