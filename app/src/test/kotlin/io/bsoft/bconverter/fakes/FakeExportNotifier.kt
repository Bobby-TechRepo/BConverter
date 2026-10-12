package io.bsoft.bconverter.fakes

import android.app.Notification
import androidx.work.ForegroundInfo
import io.bsoft.bconverter.domain.model.JobStatus
import io.bsoft.bconverter.feature.export.ExportNotifier
import java.util.UUID

class FakeExportNotifier : ExportNotifier {
    data class Foreground(val title: String, val progress: Float?)
    data class Result(val title: String, val status: JobStatus)

    val foreground = mutableListOf<Foreground>()
    val results = mutableListOf<Result>()

    override fun foregroundInfo(workId: UUID, title: String, progress: Float?): ForegroundInfo {
        foreground += Foreground(title, progress)
        return ForegroundInfo(1, Notification())
    }

    override fun showResult(title: String, status: JobStatus) { results += Result(title, status) }
}
