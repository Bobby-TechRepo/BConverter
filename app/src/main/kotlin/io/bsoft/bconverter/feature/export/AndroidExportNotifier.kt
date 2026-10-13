package io.bsoft.bconverter.feature.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import io.bsoft.bconverter.R
import io.bsoft.bconverter.app.MainActivity
import io.bsoft.bconverter.domain.model.JobStatus
import java.util.UUID
import kotlin.math.roundToInt

class AndroidExportNotifier(private val context: Context) : ExportNotifier {
    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.export_channel_name), NotificationManager.IMPORTANCE_LOW)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun foregroundInfo(workId: UUID, title: String, progress: Float?): ForegroundInfo {
        val percent = ((progress ?: 0f) * 100).roundToInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_export)
            .setContentTitle(title)
            .setContentText(
                if (progress == null) context.getString(R.string.export_running_indeterminate)
                else context.getString(R.string.export_running, percent),
            )
            .setProgress(100, percent, progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.converter_cancel), WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
        val id = workId.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(id, notification, foregroundServiceType())
        else ForegroundInfo(id, notification)
    }

    /** Android 15 added a dedicated type for transcoding; earlier versions use data sync. */
    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

    override fun showResult(title: String, status: JobStatus) {
        val text = when (status) {
            JobStatus.Completed -> R.string.export_done
            is JobStatus.Failed -> R.string.export_failed
            else -> return
        }
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_export)
            .setContentTitle(title)
            .setContentText(context.getString(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        try { manager.notify(System.currentTimeMillis().toInt(), notification) } catch (_: SecurityException) { /* permission revoked */ }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object { const val CHANNEL_ID = "export" }
}
