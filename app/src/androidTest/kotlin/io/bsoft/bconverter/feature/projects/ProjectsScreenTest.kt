package io.bsoft.bconverter.feature.projects

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import io.bsoft.bconverter.domain.project.ProjectActionResult
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.domain.project.ProjectSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val items = listOf(ProjectSummary("a", "Holiday", 2_000, 3, 75_000), ProjectSummary("b", "Wedding", 1_000, 1, 5_000))

    private fun show(
        state: ProjectsUiState,
        onOpen: (String) -> Unit = {},
        onDelete: (String) -> Unit = {},
        onRename: (String, String) -> Unit = { _, _ -> },
        onDuplicate: (String) -> Unit = {},
    ) = rule.setContent {
        ProjectsScreen(
            state, onBack = {}, onOpen = onOpen, onDelete = onDelete, onRetry = {}, onDismissDeleteFailed = {},
            onRename = onRename, onDuplicate = onDuplicate,
        )
    }

    @Test fun lists_each_project_with_its_clip_count_and_duration() {
        show(ProjectsUiState(loading = false, projects = items))
        rule.onNodeWithText("Holiday").assertIsDisplayed()
        rule.onNodeWithText("Wedding").assertIsDisplayed()
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.projects_clip_count, 3, 3) + " · 1:15").assertIsDisplayed()
    }

    @Test fun an_empty_list_explains_itself() {
        show(ProjectsUiState(loading = false))
        rule.onNodeWithText(context.getString(R.string.projects_empty)).assertIsDisplayed()
    }

    @Test fun a_load_failure_offers_a_retry() {
        show(ProjectsUiState(loading = false, loadFailed = true))
        rule.onNodeWithText(context.getString(R.string.projects_load_failed)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.projects_retry)).assertIsDisplayed()
    }

    @Test fun tapping_a_project_opens_it() {
        var opened: String? = null
        show(ProjectsUiState(loading = false, projects = items), onOpen = { opened = it })
        rule.onNodeWithText("Wedding").performClick()
        assertEquals("b", opened)
    }

    @Test fun deleting_asks_for_confirmation_first() {
        var deleted: String? = null
        show(ProjectsUiState(loading = false, projects = items), onDelete = { deleted = it })
        rule.onNodeWithContentDescription(context.getString(R.string.projects_delete_named, "Holiday")).performClick()
        assertEquals(null, deleted)
        rule.onNodeWithText(context.getString(R.string.projects_delete_confirm_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.projects_delete)).performClick()
        assertEquals("a", deleted)
    }

    @Test fun cancelling_the_confirmation_deletes_nothing() {
        var deleted: String? = null
        show(ProjectsUiState(loading = false, projects = items), onDelete = { deleted = it })
        rule.onNodeWithContentDescription(context.getString(R.string.projects_delete_named, "Holiday")).performClick()
        rule.onNodeWithText(context.getString(R.string.projects_cancel)).performClick()
        assertEquals(null, deleted)
    }

    // ---- file management ----
    private fun menuOf(name: String) = rule.onNodeWithContentDescription(context.getString(R.string.projects_more_named, name)).performClick()

    @Test fun a_project_can_be_duplicated_from_its_menu() {
        var duplicated: String? = null
        show(ProjectsUiState(loading = false, projects = items), onDuplicate = { duplicated = it })
        menuOf("Holiday")
        rule.onNodeWithText(context.getString(R.string.projects_duplicate)).performClick()
        assertEquals("a", duplicated)
    }

    @Test fun a_project_can_be_renamed_from_its_menu() {
        var renamed: Pair<String, String>? = null
        show(ProjectsUiState(loading = false, projects = items), onRename = { id, name -> renamed = id to name })
        menuOf("Holiday")
        rule.onNodeWithText(context.getString(R.string.projects_rename)).performClick()
        rule.onNodeWithText(context.getString(R.string.projects_rename_title)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextReplacement("Summer trip")
        rule.onNode(hasText(context.getString(R.string.projects_rename)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals("a" to "Summer trip", renamed)
    }

    @Test fun cancelling_the_rename_changes_nothing() {
        var renamed = false
        show(ProjectsUiState(loading = false, projects = items), onRename = { _, _ -> renamed = true })
        menuOf("Holiday")
        rule.onNodeWithText(context.getString(R.string.projects_rename)).performClick()
        rule.onNodeWithText(context.getString(R.string.projects_cancel)).performClick()
        assertEquals(false, renamed)
    }

    @Test fun action_errors_are_explained_in_plain_language() {
        show(ProjectsUiState(loading = false, projects = items, actionError = ProjectActionResult.InvalidName))
        rule.onNodeWithText(context.getString(R.string.projects_error_invalid_name)).assertIsDisplayed()
    }
}
