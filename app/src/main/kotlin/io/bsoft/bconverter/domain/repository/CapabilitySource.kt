package io.bsoft.bconverter.domain.repository

/** Provides the (possibly lazily discovered) capability repository. */
fun interface CapabilitySource {
    suspend fun repository(): MediaCapabilityRepository
}
