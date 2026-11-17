package io.bsoft.bconverter.feature.about

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import io.bsoft.bconverter.core.ui.IconBadge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.diagnostics.DiagnosticsReport
import io.bsoft.bconverter.core.ui.QuietButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.bsoft.bconverter.domain.about.BundledLicense
import io.bsoft.bconverter.domain.about.BundledLicenses
import io.bsoft.bconverter.core.common.AppConfig
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.domain.about.AppInfo
import io.bsoft.bconverter.domain.about.ThirdPartyNotice
import io.bsoft.bconverter.domain.about.ThirdPartyNotices

@Composable
fun AboutRoute(onBack: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val info by viewModel.info.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    AboutContent(
        info, ThirdPartyNotices.all, onBack, onOpenLink = { runCatching { uriHandler.openUri(it) } },
        onCopyDiagnostics = {
            val app = context.applicationContext as io.bsoft.bconverter.app.BConverterApplication
            val dir = java.io.File(context.filesDir, io.bsoft.bconverter.app.BConverterApplication.DIAGNOSTICS_DIR)
            val report = DiagnosticsReport.build(app.deviceLine(), java.io.File(dir, "last-crash.txt"), java.io.File(dir, "ffmpeg-failures.log"))
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("BConverter diagnostics", report))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    info: AppInfo?,
    notices: List<ThirdPartyNotice>,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    licenses: List<BundledLicense> = BundledLicenses.all,
    /** Reads a licence file from `assets/licenses/`; null when it cannot be read. */
    loadLicense: ((String) -> String?)? = null,
    /** Copies the on-device diagnostics report to the clipboard; null hides the section. */
    onCopyDiagnostics: (() -> Unit)? = null,
) {
    var diagnosticsCopied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val load: (String) -> String? = loadLicense ?: { name -> runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrNull() }
    var openLicense by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.home_about)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.padding(padding).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            IconBadge(R.drawable.ic_convert_video, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            Text(AppConfig.APP_NAME, style = MaterialTheme.typography.headlineMedium)
            info?.let { Text(stringResource(R.string.about_version, it.versionLine), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.about_tagline), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.about_engine), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            info?.let { Text(stringResource(R.string.about_engine_version, it.engineVersionOrUnknown), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.about_engine_licence), style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.about_notices), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_notices_intro), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
            notices.forEach { n ->
                Card(
                    onClick = { onOpenLink(n.url) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.name, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.about_notice_license, n.license), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(n.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.about_licence_texts), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_licence_texts_intro), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
            licenses.forEach { l ->
                Card(
                    onClick = { openLicense = l.title },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(l.title, style = MaterialTheme.typography.bodyLarge)
                        Text(l.appliesTo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (onCopyDiagnostics != null) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(stringResource(R.string.about_diagnostics), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_diagnostics_intro), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
                QuietButton(stringResource(R.string.about_diagnostics_copy), { onCopyDiagnostics(); diagnosticsCopied = true })
                if (diagnosticsCopied) {
                    Text(
                        stringResource(R.string.about_diagnostics_copied), Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    licenses.firstOrNull { it.title == openLicense }?.let { l ->
        val text = remember(l) { l.assetFiles.mapNotNull(load).joinToString("\n\n") }
        LicenseTextDialog(l.title, text.ifBlank { stringResource(R.string.about_licence_unavailable) }) { openLicense = null }
    }
}

/** A licence's full text, full screen and scrollable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseTextDialog(title: String, text: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 2, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = { BackButton(onClose) },
                )
            },
        ) { padding ->
            SelectionContainer {
                Text(
                    text,
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}
