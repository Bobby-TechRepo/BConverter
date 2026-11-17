package io.bsoft.bconverter.domain.overlay

sealed interface ImageImport {
    /** The picture was copied into the app's storage and is ready to be placed. */
    data class Imported(val image: OverlayImage) : ImageImport
    /** The file could not be opened or is not a picture. */
    data object Unreadable : ImageImport
    /** The picture is too big to handle. */
    data object TooLarge : ImageImport
}

/** Brings a picture the person chose into the app: a private copy (so the project survives the original moving), upright, within a sane size. */
interface OverlayImageImporter {
    /** [frameAspect] (width / height of the video) sets how big the picture first appears. */
    suspend fun import(uri: String, frameAspect: Float): ImageImport
}
