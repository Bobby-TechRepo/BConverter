package io.bsoft.bconverter.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.bsoft.bconverter.domain.project.ProjectActionResult
import io.bsoft.bconverter.domain.project.ProjectManager
import io.bsoft.bconverter.domain.project.ProjectRepository
import io.bsoft.bconverter.domain.project.ProjectSummary
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val loading: Boolean = true,
    val projects: List<ProjectSummary> = emptyList(),
    val loadFailed: Boolean = false,
    val deleteFailed: Boolean = false,
    /** Why the last rename or duplicate did not happen; null when nothing is wrong. */
    val actionError: ProjectActionResult? = null,
)

/** The "Recent Projects" list: newest first, delete with failure reporting. Opening is a navigation concern. */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val manager: ProjectManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadFailed = false) }
            try {
                val list = repository.list()
                _state.update { it.copy(loading = false, projects = list) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                _state.update { s -> s.copy(projects = s.projects.filterNot { it.id == id }) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(deleteFailed = true) }
            }
        }
    }

    fun rename(id: String, name: String) = manage { manager.rename(id, name) }

    fun duplicate(id: String) = manage { manager.duplicate(id) }

    private fun manage(action: suspend () -> ProjectActionResult) {
        viewModelScope.launch {
            val result = action()
            _state.update { it.copy(actionError = if (result == ProjectActionResult.Success) null else result) }
            // Success changes the list; NotFound/Corrupt mean the list was out of date. Either way show what is really stored.
            if (result != ProjectActionResult.InvalidName) reload()
        }
    }

    private suspend fun reload() {
        try {
            val list = repository.list()
            _state.update { it.copy(projects = list) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loadFailed = true) }
        }
    }

    fun dismissActionError() { _state.update { it.copy(actionError = null) } }

    fun dismissDeleteFailed() { _state.update { it.copy(deleteFailed = false) } }
}
