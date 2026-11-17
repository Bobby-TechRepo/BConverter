package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.overlay.ImageImport
import io.bsoft.bconverter.domain.overlay.OverlayImage
import io.bsoft.bconverter.domain.overlay.OverlayImageImporter

class FakeOverlayImageImporter : OverlayImageImporter {
    var result: ImageImport = ImageImport.Imported(OverlayImage("/images/fake.png", 400, 200))
    val requests = mutableListOf<Pair<String, Float>>()
    override suspend fun import(uri: String, frameAspect: Float): ImageImport { requests += uri to frameAspect; return result }
}
