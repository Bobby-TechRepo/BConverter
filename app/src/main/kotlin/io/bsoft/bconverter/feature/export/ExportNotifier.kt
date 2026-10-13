package io.bsoft.bconverter.feature.export

import androidx.work.ForegroundInfo
import io.bsoft.bconverter.domain.model.JobStatus
import java.util.UUID

/** Builds the foreground notification for a running job and posts a result notification when it ends. */
interface ExportNotifier {
    /** [progress] is null while indeterminate. */
    fun foregroundInfo(workId: UUID, title: String, progress: Float?): ForegroundInfo

    fun showResult(title: String, status: JobStatus)
}
