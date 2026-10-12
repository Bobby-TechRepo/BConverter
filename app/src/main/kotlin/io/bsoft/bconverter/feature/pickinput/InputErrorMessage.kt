package io.bsoft.bconverter.feature.pickinput

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.InputError

@Composable
fun inputErrorMessage(error: InputError): String {
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
