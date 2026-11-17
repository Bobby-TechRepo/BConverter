package io.bsoft.bconverter.core

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ProbeFailure
import io.bsoft.bconverter.domain.render.RenderError
import io.bsoft.bconverter.domain.timeline.EditError
import io.bsoft.bconverter.domain.usecase.ExportRejection
import io.bsoft.bconverter.feature.converter.ConverterMessage
import io.bsoft.bconverter.feature.converter.MessageText
import io.bsoft.bconverter.feature.editor.EditorMessage
import io.bsoft.bconverter.feature.editor.text
import io.bsoft.bconverter.feature.pickinput.inputErrorMessage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Spec §46: every error a user can meet is shown as a human-readable message, never as a stack trace or an internal name.
 * Each message type is rendered for every variant, so a missing or technical text fails here.
 */
class ErrorMessagesTest {
    @get:Rule val rule = createComposeRule()

    private val inputErrors = listOf(
        InputError.PermissionDenied, InputError.NotFound, InputError.Unreadable, InputError.UnsupportedType("video/x-weird"),
        InputError.Empty, InputError.InsufficientStorage(5_000_000_000, 1_000_000_000),
    )
    private val converterMessages = inputErrors.map { ConverterMessage.InputProblem(it) } + listOf(
        ConverterMessage.ProbeFailed, ConverterMessage.NoVideoStream, ConverterMessage.NoAudioStream,
        ConverterMessage.NoEncodersAvailable, ConverterMessage.ConversionFailed, ConverterMessage.SaveFailed,
    )
    private val missing = MediaSource.FileSource(File("/tmp/x.mp4"))
    private val editorMessages = listOf(
        EditorMessage.NoVideoInFile, EditorMessage.NoAudioInFile, EditorMessage.UnknownDuration, EditorMessage.InvalidProjectName,
        EditorMessage.NoExportEncoders, EditorMessage.ExportFailed, EditorMessage.ExportSaveFailed, EditorMessage.ExportOverlaysFailed, EditorMessage.ImageUnreadable, EditorMessage.ImageTooLarge, EditorMessage.ProjectNotFound,
        EditorMessage.ProjectCorrupt, EditorMessage.ProjectSaveFailed,
        EditorMessage.ProbeFailed(ProbeFailure.InvalidOutput), EditorMessage.ProbeFailed(ProbeFailure.NoMediaStreams),
        EditorMessage.ProbeFailed(ProbeFailure.ToolFailed(1)),
    ) + inputErrors.map { EditorMessage.ProbeFailed(ProbeFailure.Input(it)) } + listOf(
        EditError.ClipNotFound("x"), EditError.SplitOutsideClip, EditError.InvalidTrimRange, EditError.InvalidSpeed, EditError.InvalidVolume,
        EditError.WrongClipType, EditError.InvalidIndex, EditError.InvalidStartTime, EditError.AudioOverlap, EditError.DuplicateId,
        EditError.InvalidSourceDuration, EditError.NothingSelected, EditError.NotForFreezeFrame,
    ).map { EditorMessage.EditFailed(it) } + listOf(
        ExportRejection.NotRenderable(RenderError.NoVideo), ExportRejection.NotRenderable(RenderError.MissingSourceInfo(missing)),
        ExportRejection.NotRenderable(RenderError.SourceNotStaged(missing)), ExportRejection.InsufficientStorage(5_000_000_000, 1_000_000_000),
    ).map { EditorMessage.ExportRejected(it) }

    /** Text that only a developer should ever see. */
    private val technical = listOf("Exception", "Error(", "Failure(", "at io.", "bsoft", "null", "InputError", "ConverterMessage", "EditorMessage")

    private fun check(messages: List<String>) {
        messages.forEach { m ->
            assertTrue("message is blank", m.isNotBlank())
            technical.forEach { assertFalse("\"$m\" exposes \"$it\"", m.contains(it)) }
        }
    }

    private fun collect(render: @Composable (MutableList<String>) -> Unit): List<String> {
        val out = mutableListOf<String>()
        rule.setContent { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { render(out) } }
        rule.waitForIdle()
        return out
    }

    @Test fun every_input_error_has_a_readable_message() {
        val texts = collect { out -> inputErrors.forEach { e -> val t = inputErrorMessage(e); out += t; Text(t) } }
        assertEquals(inputErrors.size, texts.size); check(texts)
        assertEquals("each error says something different", texts.size, texts.toSet().size)
    }

    @Test fun every_converter_message_has_a_readable_message() {
        collect { _ -> converterMessages.forEach { MessageText(it) } }
        val shown = rule.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { s -> s.text } }
        assertEquals(converterMessages.size, shown.size); check(shown)
    }

    @Test fun every_editor_message_has_a_readable_message() {
        val texts = collect { out -> editorMessages.forEach { m -> val t = m.text(); out += t; Text(t) } }
        assertEquals(editorMessages.size, texts.size); check(texts)
    }
}
