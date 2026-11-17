package io.bsoft.bconverter.feature.share

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.coroutine.IoDispatcher
import io.bsoft.bconverter.core.storage.ContentReader
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.core.ui.IconTile
import io.bsoft.bconverter.core.ui.PremiumCard
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.domain.share.ShareAction
import io.bsoft.bconverter.domain.share.ShareIntents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The share waiting to be used, with the names of its files for the screen. Scoped to the activity (one for the app). */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val inbox: ShareInbox,
    private val reader: ContentReader,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    val pending: StateFlow<SharedMedia?> = inbox.pending
    private val _names = MutableStateFlow<Map<Uri, String>>(emptyMap())
    /** File names by Uri, as the sending app's provider gives them (filled in shortly after a share arrives). */
    val names: StateFlow<Map<Uri, String>> = _names.asStateFlow()

    init {
        viewModelScope.launch {
            inbox.pending.collect { media ->
                if (media == null) return@collect
                _names.value = withContext(io) {
                    media.uris.associateWith { u -> runCatching { reader.info(u).displayName }.getOrNull() ?: u.lastPathSegment.orEmpty() }
                }
            }
        }
    }

    fun take(): List<Uri>? = inbox.take()
    fun clear() = inbox.clear()
}

/**
 * "Open with BConverter" (step 25): what was shared, and what can be done with it. One video can go to any tool;
 * several can start a project or be converted together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContent(uris: List<Uri>, names: Map<Uri, String>, onAction: (ShareAction) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.share_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).readableWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uris.isEmpty()) {
                Text(stringResource(R.string.share_nothing), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(
                pluralStringResource(R.plurals.share_count, uris.size, uris.size),
                Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
            )
            val shown = uris.take(SHOWN_NAMES)
            shown.forEach { u ->
                Text(names[u] ?: u.lastPathSegment.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (uris.size > shown.size) Text(stringResource(R.string.share_more, uris.size - shown.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ShareIntents.actionsFor(uris.size).forEach { action ->
                val (icon, title, hint) = describe(action)
                ActionCard(icon, title, hint) { onAction(action) }
            }
        }
    }
}

private const val SHOWN_NAMES = 4

private data class ActionText(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val hint: Int)

private fun describe(action: ShareAction): ActionText = when (action) {
    ShareAction.EDIT -> ActionText(R.drawable.ic_video_editor, R.string.share_edit, R.string.share_edit_hint)
    ShareAction.CONVERT -> ActionText(R.drawable.ic_convert_video, R.string.home_convert_video, R.string.home_convert_hint)
    ShareAction.CONVERT_ALL -> ActionText(R.drawable.ic_convert_video, R.string.share_convert_all, R.string.share_convert_all_hint)
    ShareAction.EXTRACT_AUDIO -> ActionText(R.drawable.ic_extract_audio, R.string.home_extract_audio, R.string.home_audio_hint)
    ShareAction.EXTRACT_SUBTITLES -> ActionText(R.drawable.ic_subtitles, R.string.home_extract_subtitles, R.string.home_subtitles_hint)
}

@Composable
private fun ActionCard(@DrawableRes icon: Int, @StringRes title: Int, @StringRes hint: Int, onClick: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth().semantics { role = Role.Button }, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
