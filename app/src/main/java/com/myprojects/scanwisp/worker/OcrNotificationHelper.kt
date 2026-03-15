package com.myprojects.scanwisp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.myprojects.scanwisp.R

const val OCR_CHANNEL_ID = "ocr_progress_channel"
const val OCR_NOTIFICATION_ID = 1001

object OcrNotificationHelper {

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            OCR_CHANNEL_ID,
            context.getString(R.string.ocr_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW  // LOW = без звука, без всплывания
        ).apply {
            description = context.getString(R.string.ocr_notification_channel_desc)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildProgressNotification(
        context: Context,
        documentTitle: String,
        currentPage: Int,
        totalPages: Int
    ): android.app.Notification {
        return NotificationCompat.Builder(context, OCR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ocr)
            .setContentTitle(context.getString(R.string.ocr_notification_title))
            .setContentText(
                context.getString(
                    R.string.ocr_notification_progress,
                    documentTitle,
                    currentPage,
                    totalPages
                )
            )
            .setProgress(totalPages, currentPage, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun buildDoneNotification(
        context: Context,
        documentTitle: String,
        pageCount: Int
    ): android.app.Notification {
        return NotificationCompat.Builder(context, OCR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ocr)
            .setContentTitle(context.getString(R.string.ocr_notification_done_title))
            .setContentText(
                context.getString(
                    R.string.ocr_notification_done_text,
                    documentTitle,
                    pageCount
                )
            )
            .setAutoCancel(true)
            .build()
    }
}