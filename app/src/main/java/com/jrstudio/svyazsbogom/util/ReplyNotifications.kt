package com.jrstudio.svyazsbogom.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jrstudio.svyazsbogom.MainActivity
import com.jrstudio.svyazsbogom.data.ApiClient
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "divine_replies"
private const val WORK_NAME = "reply-watch"

fun ensureReplyChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ответы",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления, когда в диалоге появился ответ"
            }
        )
    }
}

fun scheduleReplyWatch(context: Context) {
    ensureReplyChannel(context)
    val request = PeriodicWorkRequestBuilder<ReplyWatchWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

class ReplyWatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val identity = installIdentity(applicationContext)
            val response = ApiClient.api.conversation(identity.conversationId, identity.installSecret)
            if (!response.ok) return Result.success()

            val latestHuman = response.messages.lastOrNull { it.role == "human" } ?: return Result.success()
            val prefs = applicationContext.getSharedPreferences("divine_reply_state", Context.MODE_PRIVATE)
            val previous = prefs.getString("last_human_id", null)

            if (previous == null) {
                prefs.edit().putString("last_human_id", latestHuman.id).apply()
                return Result.success()
            }

            if (previous != latestHuman.id) {
                showReplyNotification(applicationContext)
                prefs.edit().putString("last_human_id", latestHuman.id).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

private fun showReplyNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val intent = Intent(context, MainActivity::class.java)
    val pending = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.star_big_on)
        .setContentTitle("CONTACT")
        .setContentText("У тебя есть ответ.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()

    NotificationManagerCompat.from(context).notify(222, notification)
}
