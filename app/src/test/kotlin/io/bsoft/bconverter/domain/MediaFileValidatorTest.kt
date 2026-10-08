package io.bsoft.bconverter.domain.usecase

import io.bsoft.bconverter.domain.model.ContentInfo
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFileValidatorTest {
    private val validator = MediaFileValidator()
    private fun check(name: String?, mime: String?, size: Long? = 100) =
        validator.validate(ContentInfo(name, mime, size))

    @Test fun accepts_video_mime() = assertEquals(ValidationResult.Valid, check("a.mp4", "video/mp4"))
    @Test fun accepts_audio_mime() = assertEquals(ValidationResult.Valid, check("a.mp3", "audio/mpeg"))
    @Test fun accepts_matroska_application_mime() =
        assertEquals(ValidationResult.Valid, check("a.mkv", "application/x-matroska"))

    @Test fun rejects_image_mime() =
        assertEquals(ValidationResult.Invalid(InputError.UnsupportedType("image/png")), check("a.png", "image/png"))

    @Test fun rejects_text_mime_even_with_media_extension() =
        assertEquals(ValidationResult.Invalid(InputError.UnsupportedType("text/plain")), check("a.mp4", "text/plain"))

    @Test fun null_mime_falls_back_to_known_extension() = assertEquals(ValidationResult.Valid, check("a.mkv", null))

    @Test fun octet_stream_falls_back_to_known_extension() =
        assertEquals(ValidationResult.Valid, check("a.avi", "application/octet-stream"))

    @Test fun octet_stream_with_unknown_extension_is_rejected() =
        assertEquals(
            ValidationResult.Invalid(InputError.UnsupportedType("application/octet-stream")),
            check("a.txt", "application/octet-stream"),
        )

    @Test fun null_mime_without_extension_is_rejected() =
        assertEquals(ValidationResult.Invalid(InputError.UnsupportedType(null)), check("clip", null))

    @Test fun extension_match_is_case_insensitive() = assertEquals(ValidationResult.Valid, check("A.MP4", null))

    @Test fun zero_size_is_empty() =
        assertEquals(ValidationResult.Invalid(InputError.Empty), check("a.mp4", "video/mp4", size = 0))

    @Test fun unknown_size_is_accepted() = assertEquals(ValidationResult.Valid, check("a.mp4", "video/mp4", size = null))
}
