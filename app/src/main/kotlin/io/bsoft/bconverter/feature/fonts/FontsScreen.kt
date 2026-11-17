package io.bsoft.bconverter.feature.fonts

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.domain.font.CatalogFont
import io.bsoft.bconverter.domain.font.FontFileRef
import io.bsoft.bconverter.domain.font.FontLicense
import io.bsoft.bconverter.domain.font.FontVariant
import io.bsoft.bconverter.domain.font.InstalledFamily
import java.io.File

@Composable
fun FontsRoute(onBack: () -> Unit, viewModel: FontsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FontsScreen(
        state, onBack, viewModel::selectTab, viewModel::setQuery, viewModel::setCategory, viewModel::retryCatalog,
        viewModel::download, viewModel::delete, viewModel::clearDownloads, viewModel::dismissMessage,
    )
}

/** A typeface for showing a name in its own font; null when the file cannot be read (the text then uses the default font). */
@Composable
private fun rememberTypeface(file: FontFileRef?): FontFamily? = remember(file?.path) {
    file?.let { runCatching { FontFamily(Font(File(it.path), FontWeight(it.variant.weight), if (it.variant.italic) FontStyle.Italic else FontStyle.Normal)) }.getOrNull() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontsScreen(
    state: FontsUiState,
    onBack: () -> Unit,
    onTab: (FontsTab) -> Unit,
    onQuery: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onRetry: () -> Unit,
    onDownload: (CatalogFont, List<FontVariant>) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val text = state.message?.let {
        stringResource(when (it) {
            FontsMessage.Downloaded -> R.string.fonts_msg_downloaded
            FontsMessage.Offline -> R.string.fonts_msg_offline
            FontsMessage.BadResponse -> R.string.fonts_msg_failed
            FontsMessage.Removed -> R.string.fonts_msg_removed
            FontsMessage.CacheCleared -> R.string.fonts_msg_cleared
        })
    }
    LaunchedEffect(state.message) { if (text != null) { snackbar.showSnackbar(text); onDismissMessage() } }
    var dialogFont by remember { mutableStateOf<CatalogFont?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.fonts_title)) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth()) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(state.tab == FontsTab.INSTALLED, { onTab(FontsTab.INSTALLED) }, Modifier.heightIn(min = 48.dp), text = { Text(stringResource(R.string.fonts_tab_installed)) })
                Tab(state.tab == FontsTab.GOOGLE, { onTab(FontsTab.GOOGLE) }, Modifier.heightIn(min = 48.dp), text = { Text(stringResource(R.string.fonts_tab_google)) })
            }
            when (state.tab) {
                FontsTab.INSTALLED -> InstalledTab(state, onDelete, onClear)
                FontsTab.GOOGLE -> GoogleTab(state, onQuery, onCategory, onRetry, { f -> onDownload(f, emptyList()) }, { dialogFont = it })
            }
        }
    }

    dialogFont?.let { font ->
        VariantDialog(font, state.variantsInstalled(font.family), onDismiss = { dialogFont = null }, onConfirm = { chosen -> onDownload(font, chosen); dialogFont = null })
    }
}

@Composable
private fun InstalledTab(state: FontsUiState, onDelete: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.recents.isNotEmpty()) {
            item {
                Text(stringResource(R.string.fonts_recent), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                Text(state.recents.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Text(stringResource(R.string.fonts_licence_note), style = MaterialTheme.typography.bodySmall) }
        items(state.installed, key = { it.name }) { f -> InstalledRow(f, onDelete) }
        item {
            val size = Formatter.formatShortFileSize(LocalContext.current, state.cacheBytes)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.fonts_downloaded_size, size), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClear, Modifier.heightIn(min = 48.dp), enabled = state.cacheBytes > 0) { Text(stringResource(R.string.fonts_clear)) }
            }
        }
    }
}

@Composable
private fun InstalledRow(f: InstalledFamily, onDelete: (String) -> Unit) {
    val face = rememberTypeface(f.sample)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.name, fontFamily = face, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.fonts_sample), fontFamily = face, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (f.downloaded) R.string.fonts_source_downloaded else R.string.fonts_source_system, f.variants.size, licenseLabel(f.license)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (f.downloaded) {
                IconButton({ onDelete(f.name) }, Modifier.heightIn(min = 48.dp)) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.fonts_delete_named, f.name)) }
            }
        }
    }
}

@Composable
private fun licenseLabel(l: FontLicense) = l.label

@Composable
private fun GoogleTab(
    state: FontsUiState, onQuery: (String) -> Unit, onCategory: (String?) -> Unit, onRetry: () -> Unit,
    onPreview: (CatalogFont) -> Unit, onChoose: (CatalogFont) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(state.query, onQuery, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text(stringResource(R.string.fonts_search_hint)) })
        if (state.categories.isNotEmpty()) {
            LazyRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(state.category == null, { onCategory(null) }, { Text(stringResource(R.string.fonts_all_categories)) }) }
                items(state.categories) { c -> FilterChip(state.category == c, { onCategory(if (state.category == c) null else c) }, { Text(c) }) }
            }
        }
        when (val c = state.catalog) {
            CatalogUi.Loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            CatalogUi.Unavailable -> Column(Modifier.padding(vertical = 24.dp)) {
                Text(stringResource(R.string.fonts_catalog_unavailable), style = MaterialTheme.typography.bodyLarge)
                TextButton(onRetry, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.fonts_retry)) }
            }
            is CatalogUi.Ready -> {
                if (c.offlineCopy) Text(stringResource(R.string.fonts_catalog_offline_copy), Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                if (state.results.isEmpty()) Text(stringResource(R.string.fonts_no_results), Modifier.padding(vertical = 16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                    items(state.results, key = { it.family }) { f -> CatalogRow(f, state, onPreview, onChoose) }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(f: CatalogFont, state: FontsUiState, onPreview: (CatalogFont) -> Unit, onChoose: (CatalogFont) -> Unit) {
    val installed = state.installed.firstOrNull { io.bsoft.bconverter.domain.font.FontNames.key(it.name) == io.bsoft.bconverter.domain.font.FontNames.key(f.family) }
    val face = rememberTypeface(installed?.sample)
    val busy = f.family in state.busy
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.family, fontFamily = face, style = MaterialTheme.typography.titleMedium)
                if (installed != null) Text(stringResource(R.string.fonts_sample), fontFamily = face, style = MaterialTheme.typography.bodyMedium)
                Text("${f.category} · ${f.variants.size} · ${f.license.label}", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) CircularProgressIndicator(Modifier.padding(12.dp))
            else {
                if (installed == null) TextButton({ onPreview(f) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.fonts_preview)) }
                TextButton({ onChoose(f) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.fonts_download)) }
            }
        }
    }
}

@Composable
private fun VariantDialog(font: CatalogFont, have: Set<FontVariant>, onDismiss: () -> Unit, onConfirm: (List<FontVariant>) -> Unit) {
    val chosen = remember(font.family) { mutableStateListOf<FontVariant>().also { l -> l.addAll(font.variants.filter { it.weight in setOf(400, 700) && !it.italic && it !in have }.ifEmpty { font.variants.take(1).filter { it !in have } }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fonts_choose_weights, font.family)) },
        text = {
            Column {
                Text(stringResource(R.string.fonts_licence_for, font.license.label), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(font.variants, key = { it.key }) { v ->
                        val has = v in have
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(has || v in chosen, { on -> if (on) chosen.add(v) else chosen.remove(v) }, enabled = !has)
                            Text(weightLabel(v) + if (has) " ✓" else "", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(chosen.toList()) }, enabled = chosen.isNotEmpty()) { Text(stringResource(R.string.fonts_download)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

private fun weightLabel(v: FontVariant): String {
    val n = when (v.weight) { 100 -> "Thin"; 200 -> "ExtraLight"; 300 -> "Light"; 400 -> "Regular"; 500 -> "Medium"; 600 -> "SemiBold"; 700 -> "Bold"; 800 -> "ExtraBold"; else -> "Black" }
    return "$n ${v.weight}" + if (v.italic) " Italic" else ""
}
