package io.bsoft.bconverter.feature.fonts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.bsoft.bconverter.domain.font.CatalogFont
import io.bsoft.bconverter.domain.font.CatalogResult
import io.bsoft.bconverter.domain.font.FontCacheStore
import io.bsoft.bconverter.domain.font.FontDownload
import io.bsoft.bconverter.domain.font.FontDownloader
import io.bsoft.bconverter.domain.font.FontLibrary
import io.bsoft.bconverter.domain.font.FontNames
import io.bsoft.bconverter.domain.font.FontSearch
import io.bsoft.bconverter.domain.font.FontVariant
import io.bsoft.bconverter.domain.font.GoogleFontsCatalog
import io.bsoft.bconverter.domain.font.InstalledFamily
import io.bsoft.bconverter.media.ffmpeg.FfmpegFonts
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FontsTab { INSTALLED, GOOGLE }

sealed interface CatalogUi {
    data object Loading : CatalogUi
    data object Unavailable : CatalogUi
    data class Ready(val all: List<CatalogFont>, val offlineCopy: Boolean) : CatalogUi
}

enum class FontsMessage { Downloaded, Offline, BadResponse, Removed, CacheCleared }

data class FontsUiState(
    val tab: FontsTab = FontsTab.INSTALLED,
    val query: String = "",
    val category: String? = null,
    val installed: List<InstalledFamily> = emptyList(),
    val recents: List<String> = emptyList(),
    val catalog: CatalogUi = CatalogUi.Loading,
    val results: List<CatalogFont> = emptyList(),
    val categories: List<String> = emptyList(),
    val busy: Set<String> = emptySet(),
    val cacheBytes: Long = 0,
    val message: FontsMessage? = null,
) {
    /** True when this catalog family is already on the device (downloaded or shipped with it). */
    fun isInstalled(family: String) = installed.any { FontNames.key(it.name) == FontNames.key(family) }
    fun variantsInstalled(family: String): Set<FontVariant> = installed.firstOrNull { FontNames.key(it.name) == FontNames.key(family) }?.variants?.toSet().orEmpty()
}

@HiltViewModel
class FontsViewModel @Inject constructor(
    private val library: FontLibrary,
    private val catalog: GoogleFontsCatalog,
    private val downloader: FontDownloader,
    private val store: FontCacheStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(FontsUiState())
    val state: StateFlow<FontsUiState> = _state.asStateFlow()

    init { refreshInstalled(); loadCatalog(false) }

    fun selectTab(tab: FontsTab) = _state.update { it.copy(tab = tab) }
    fun setQuery(q: String) { _state.update { it.copy(query = q) }; recomputeResults() }
    fun setCategory(c: String?) { _state.update { it.copy(category = c) }; recomputeResults() }
    fun retryCatalog() = loadCatalog(true)
    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun loadCatalog(force: Boolean) {
        _state.update { it.copy(catalog = CatalogUi.Loading) }
        viewModelScope.launch {
            val r = catalog.load(force)
            _state.update {
                when (r) {
                    is CatalogResult.Ready -> it.copy(catalog = CatalogUi.Ready(r.fonts, r.fromCacheOnly), categories = FontSearch.categories(r.fonts))
                    CatalogResult.Unavailable -> it.copy(catalog = CatalogUi.Unavailable, categories = emptyList())
                }
            }
            recomputeResults()
        }
    }

    private fun recomputeResults() = _state.update { s ->
        val c = s.catalog
        s.copy(results = if (c is CatalogUi.Ready) FontSearch.search(c.all, s.query, s.category) else emptyList())
    }

    private fun refreshInstalled() {
        viewModelScope.launch {
            val (installed, recents, bytes) = withContext(Dispatchers.IO) { Triple(library.installedFamilies(), library.recentFamilies(), store.totalBytes()) }
            _state.update { it.copy(installed = installed, recents = recents, cacheBytes = bytes) }
        }
    }

    /** Downloads [variants] of [font]; with none given it fetches the one variant a preview needs. */
    fun download(font: CatalogFont, variants: List<FontVariant> = emptyList()) {
        if (font.family in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + font.family) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                downloader.download(font, variants.ifEmpty { listOf(downloader.previewVariant(font)) }).also { r ->
                    if (r is FontDownload.Done) {
                        store.trim(MAX_CACHE_BYTES, keep = setOf(font.family) + library.recentFamilies())
                        FfmpegFonts.refresh(context)
                    }
                }
            }
            val message = when (result) { is FontDownload.Done -> FontsMessage.Downloaded; FontDownload.Offline -> FontsMessage.Offline; else -> FontsMessage.BadResponse }
            _state.update { it.copy(busy = it.busy - font.family, message = message) }
            refreshInstalled()
        }
    }

    fun delete(family: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.remove(family); FfmpegFonts.refresh(context) }
            _state.update { it.copy(message = FontsMessage.Removed) }
            refreshInstalled()
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clear(); FfmpegFonts.refresh(context) }
            _state.update { it.copy(message = FontsMessage.CacheCleared) }
            refreshInstalled()
        }
    }

    private companion object { const val MAX_CACHE_BYTES = 100L * 1024 * 1024 }
}
