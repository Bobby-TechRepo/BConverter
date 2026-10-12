package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository

class FakeCapabilitySource(var repo: MediaCapabilityRepository = TestMedia.allEncoders()) : CapabilitySource {
    override suspend fun repository(): MediaCapabilityRepository = repo
}
