package io.bsoft.bconverter.domain.model

/** What the user picked in the export dialog for a project, remembered with it. The container lives in the project's export settings. */
data class ExportChoices(val quality: ExportPreset = ExportPreset.BALANCED, val size: ExportSize = ExportSize.AUTO) {
    val isDefault: Boolean get() = this == ExportChoices()
}
