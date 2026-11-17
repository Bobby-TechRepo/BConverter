package io.bsoft.bconverter.feature.editor

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.render.RenderError
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.usecase.ExportRejection
import io.bsoft.bconverter.feature.pickinput.inputErrorMessage

/** Plain-language text for every editor message; never exposes technical detail (spec §46). */
@Composable
fun EditorMessage.text(): String = when (this) {
    is EditorMessage.ProbeFailed -> when (val f = failure) {
        is ProbeFailure.Input -> inputErrorMessage(f.error)
        else -> stringResource(R.string.msg_probe_failed)
    }
    EditorMessage.NoVideoInFile -> stringResource(R.string.msg_editor_no_video)
    EditorMessage.NoAudioInFile -> stringResource(R.string.msg_editor_no_audio)
    EditorMessage.UnknownDuration -> stringResource(R.string.msg_editor_unknown_duration)
    is EditorMessage.EditFailed -> stringResource(
        when (error) {
            EditError.NothingSelected -> R.string.msg_edit_nothing_selected
            EditError.SplitOutsideClip -> R.string.msg_edit_split_outside
            EditError.InvalidTrimRange -> R.string.msg_edit_trim_range
            EditError.InvalidSpeed -> R.string.msg_edit_speed
            EditError.InvalidVolume -> R.string.msg_edit_volume
            EditError.InvalidFade -> R.string.msg_edit_fade
            EditError.InvalidKeyframes -> R.string.msg_edit_keyframes
            EditError.NoPreviousClip -> R.string.msg_edit_no_previous
            EditError.InvalidTransition -> R.string.msg_edit_transition
            EditError.AlreadyDetached -> R.string.msg_edit_already_detached
            EditError.AudioOverlap -> R.string.msg_edit_overlap
            EditError.NotForFreezeFrame -> R.string.msg_edit_freeze_frame
            EditError.WrongClipType -> R.string.msg_edit_wrong_clip_type
            else -> R.string.msg_edit_generic
        },
    )
    EditorMessage.InvalidProjectName -> stringResource(R.string.msg_project_name_invalid)
    EditorMessage.NoExportEncoders -> stringResource(R.string.msg_no_encoders)
    is EditorMessage.ExportRejected -> when (val r = reason) {
        is ExportRejection.NotRenderable -> when (val e = r.error) {
            is RenderError.ReverseTooLong -> stringResource(R.string.msg_reverse_too_long, e.maxDurationMs / 1000f)
            RenderError.NoVideo -> stringResource(R.string.msg_export_no_video)
            else -> stringResource(R.string.msg_export_missing_media)
        }
        is ExportRejection.SubtitlesNotSupported -> stringResource(R.string.msg_export_subtitles_unsupported)
        ExportRejection.SubtitleFilesFailed -> stringResource(R.string.msg_export_subtitles_failed)
        is ExportRejection.InsufficientStorage -> {
            val context = LocalContext.current
            stringResource(R.string.issue_insufficient_storage, Formatter.formatFileSize(context, r.requiredBytes), Formatter.formatFileSize(context, r.availableBytes))
        }
    }
    EditorMessage.ExportFailed -> stringResource(R.string.msg_export_failed)
    EditorMessage.ExportSaveFailed -> stringResource(R.string.msg_export_save_failed)
    EditorMessage.ExportOverlaysFailed -> stringResource(R.string.msg_export_overlays_failed)
    EditorMessage.ImageUnreadable -> stringResource(R.string.msg_image_unreadable)
    EditorMessage.ImageTooLarge -> stringResource(R.string.msg_image_too_large)
    is EditorMessage.ReverseTooLong -> stringResource(R.string.msg_reverse_too_long, maxDurationMs / 1000f)
    EditorMessage.ProjectNotFound -> stringResource(R.string.msg_project_not_found)
    EditorMessage.ProjectCorrupt -> stringResource(R.string.msg_project_corrupt)
    EditorMessage.ProjectSaveFailed -> stringResource(R.string.msg_project_save_failed)
    is EditorMessage.SubtitleImportFailed -> when (val r = reason) {
        is io.bsoft.bconverter.domain.usecase.SubtitleImportFailure.Parse -> stringResource(
            if (r.error == io.bsoft.bconverter.domain.subtitle.SubtitleError.UnknownFormat) R.string.msg_subtitle_unknown_format else R.string.msg_subtitle_unreadable_content,
        )
        io.bsoft.bconverter.domain.usecase.SubtitleImportFailure.TooLarge -> stringResource(R.string.msg_subtitle_too_large)
        io.bsoft.bconverter.domain.usecase.SubtitleImportFailure.Unreadable -> stringResource(R.string.msg_subtitle_unreadable_file)
    }
    is EditorMessage.SubtitleImportedWithWarnings -> stringResource(R.string.msg_subtitle_warnings, count)
    is EditorMessage.OverlayEditRejected -> stringResource(
        when (problem) {
            io.bsoft.bconverter.domain.overlay.OverlayProblem.InvalidTiming -> R.string.subtitles_time_invalid
            io.bsoft.bconverter.domain.overlay.OverlayProblem.InvalidValue -> R.string.text_value_invalid
            else -> R.string.msg_edit_generic
        },
    )
    is EditorMessage.SubtitleEditRejected -> stringResource(
        when (problem) {
            io.bsoft.bconverter.domain.subtitle.EditProblem.InvalidTiming -> R.string.subtitles_time_invalid
            else -> R.string.msg_edit_generic
        },
    )
}
