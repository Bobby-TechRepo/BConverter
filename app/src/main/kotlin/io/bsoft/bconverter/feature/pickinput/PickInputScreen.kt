package io.bsoft.bconverter.feature.pickinput

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.ui.BackButton
import io.bsoft.bconverter.domain.model.InputError

/** Stateful entry point: SAF document picker (MediaStore-backed gallery included) + ViewModel. */
@Composable
fun PickInputScreen(
    kind: PickKind,
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    viewModel: PickInputViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onPicked(it) }
    PickInputContent(
        state = state,
        titleRes = titleRes,
        selectLabelRes = kind.selectLabelRes,
        onSelect = { launcher.launch(kind.mimeTypes.toTypedArray()) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickInputContent(
    state: PickInputState,
    @StringRes titleRes: Int,
    @StringRes selectLabelRes: Int,
    onSelect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                PickInputState.Idle -> Button(onClick = onSelect) { Text(stringResource(selectLabelRes)) }
                PickInputState.Resolving -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.preparing_file),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                is PickInputState.Ready -> {
                    Text(state.input.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(Formatter.formatFileSize(context, state.input.sizeBytes))
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onSelect) { Text(stringResource(R.string.select_another)) }
                }
                is PickInputState.Error -> {
                    Text(
                        errorMessage(state.error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSelect) { Text(stringResource(selectLabelRes)) }
                }
            }
        }
    }
}

@Composable
private fun errorMessage(error: InputError): String {
    val context = LocalContext.current
    return when (error) {
        InputError.PermissionDenied -> stringResource(R.string.error_permission_denied)
        InputError.NotFound -> stringResource(R.string.error_not_found)
        InputError.Unreadable -> stringResource(R.string.error_unreadable)
        is InputError.UnsupportedType -> stringResource(R.string.error_unsupported)
        InputError.Empty -> stringResource(R.string.error_empty)
        is InputError.InsufficientStorage -> stringResource(
            R.string.error_insufficient_storage,
            Formatter.formatFileSize(context, error.requiredBytes),
            Formatter.formatFileSize(context, error.availableBytes),
        )
    }
}
