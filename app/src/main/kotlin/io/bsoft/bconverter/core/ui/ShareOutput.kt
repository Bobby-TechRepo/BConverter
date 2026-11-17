package io.bsoft.bconverter.core.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.SavedOutput

/** The system "share" request for a saved result: the file is handed over by its content Uri with read permission, never copied. */
fun shareIntent(output: SavedOutput, chooserTitle: CharSequence): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = output.mimeType
        putExtra(Intent.EXTRA_STREAM, output.uri)
        clipData = ClipData.newRawUri(null, output.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, chooserTitle)
}

/** Opens the system share sheet for a saved result; nothing happens (and nothing crashes) if no app can take it. */
@Composable
fun rememberShareAction(): (SavedOutput) -> Unit {
    val context = LocalContext.current
    return remember(context) { { output -> share(context, output) } }
}

private fun share(context: Context, output: SavedOutput) {
    try {
        context.startActivity(shareIntent(output, context.getString(R.string.share_chooser_title)))
    } catch (_: ActivityNotFoundException) {
        // No app can receive it; the file is still saved where the user chose.
    }
}
