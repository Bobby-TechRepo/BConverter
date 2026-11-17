package io.bsoft.bconverter.feature.projects

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.ui.text.style.TextAlign
import io.bsoft.bconverter.core.ui.ErrorBanner
import io.bsoft.bconverter.core.ui.IconBadge
import io.bsoft.bconverter.domain.project.ProjectActionResult
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import io.bsoft.bconverter.core.ui.LocalWindowLayout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BackButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.core.ui.BrandBackdrop
import io.bsoft.bconverter.core.ui.GradientButton
import io.bsoft.bconverter.core.ui.PremiumCard
import io.bsoft.bconverter.core.ui.readableWidth
import io.bsoft.bconverter.domain.project.ProjectSummary
import io.bsoft.bconverter.feature.converter.formatDuration
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectsRoute(onBack: () -> Unit, onOpen: (String) -> Unit, vm: ProjectsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    ProjectsScreen(state, onBack, onOpen, vm::delete, vm::refresh, vm::dismissDeleteFailed, vm::rename, vm::duplicate, vm::dismissActionError)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissDeleteFailed: () -> Unit,
    onRename: (String, String) -> Unit = { _, _ -> },
    onDuplicate: (String) -> Unit = {},
    onDismissActionError: () -> Unit = {},
) {
    var pendingRename by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    BrandBackdrop()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.home_recent_projects)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadFailed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorBanner(stringResource(R.string.projects_load_failed))
                    GradientButton(stringResource(R.string.projects_retry), onRetry, Modifier.padding(top = 16.dp))
                }
                state.projects.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(R.drawable.ic_projects, size = 96.dp)
                    Text(stringResource(R.string.projects_empty), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // One column on a phone; as many 340 dp columns as fit in a wide window (step 21).
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.readableWidth(LocalWindowLayout.current.contentMaxWidth),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectRow(
                            project, onOpen = { onOpen(project.id) }, onDelete = { pendingDelete = project.id },
                            onRename = { pendingRename = project.id }, onDuplicate = { onDuplicate(project.id) },
                        )
                    }
                }
            }
        }
    }
    }

    pendingDelete?.let { id ->
        val name = state.projects.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.projects_delete_confirm_title)) },
            text = { Text(stringResource(R.string.projects_delete_confirm_text, name)) },
            confirmButton = { TextButton({ pendingDelete = null; onDelete(id) }) { Text(stringResource(R.string.projects_delete)) } },
            dismissButton = { TextButton({ pendingDelete = null }) { Text(stringResource(R.string.projects_cancel)) } },
        )
    }
    pendingRename?.let { id ->
        val current = state.projects.firstOrNull { it.id == id }?.name.orEmpty()
        var name by rememberSaveable(id) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.projects_rename_title)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.projects_name_label)) }, singleLine = true) },
            confirmButton = { TextButton({ pendingRename = null; onRename(id, name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.projects_rename)) } },
            dismissButton = { TextButton({ pendingRename = null }) { Text(stringResource(R.string.projects_cancel)) } },
        )
    }
    state.actionError?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissActionError,
            text = {
                Text(
                    stringResource(
                        when (error) {
                            ProjectActionResult.InvalidName -> R.string.projects_error_invalid_name
                            ProjectActionResult.NotFound -> R.string.projects_error_not_found
                            ProjectActionResult.Corrupt -> R.string.projects_error_corrupt
                            else -> R.string.projects_error_failed
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onDismissActionError) { Text(stringResource(R.string.projects_ok)) } },
        )
    }
    if (state.deleteFailed) {
        AlertDialog(
            onDismissRequest = onDismissDeleteFailed,
            text = { Text(stringResource(R.string.projects_delete_failed)) },
            confirmButton = { TextButton(onDismissDeleteFailed) { Text(stringResource(R.string.projects_ok)) } },
        )
    }
}

@Composable
private fun ProjectRow(project: ProjectSummary, onOpen: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val modified = remember(project.modifiedAtMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.modifiedAtMs))
    }
    val brand = BConverterTheme.brand
    PremiumCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A monogram tile on the brand wash stands in for a cover frame.
            Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(brand.signatureWash), contentAlignment = Alignment.Center) {
                Text(project.name.trim().take(1).uppercase().ifEmpty { "•" }, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.projects_clip_count, project.clipCount, project.clipCount) + " · " + formatDuration(project.durationMs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(modified, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton({ menuOpen = true }) { Icon(painterResource(R.drawable.ic_more_vertical), stringResource(R.string.projects_more_named, project.name)) }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.projects_rename)) }, { menuOpen = false; onRename() })
                    DropdownMenuItem({ Text(stringResource(R.string.projects_duplicate)) }, { menuOpen = false; onDuplicate() })
                }
            }
            IconButton(onDelete) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.projects_delete_named, project.name)) }
        }
    }
}
