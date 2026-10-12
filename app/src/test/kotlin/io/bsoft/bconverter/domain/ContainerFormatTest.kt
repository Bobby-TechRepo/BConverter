package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ContainerFormatTest {
    @Test fun file_extensions() = assertEquals(
        listOf("mp4", "mkv", "3gp", "webm", "mov", "avi", "ts"), ContainerFormat.entries.map { it.extension },
    )
}

class ContainerMimeTypeTest {
    @Test fun mime_types() = assertEquals(
        listOf("video/mp4", "video/x-matroska", "video/3gpp", "video/webm", "video/quicktime", "video/x-msvideo", "video/mp2t"),
        ContainerFormat.entries.map { it.mimeType },
    )
}
