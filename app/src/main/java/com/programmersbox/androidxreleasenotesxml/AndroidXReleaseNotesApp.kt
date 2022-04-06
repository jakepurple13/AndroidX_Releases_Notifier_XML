package com.programmersbox.androidxreleasenotesxml

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.icu.text.SimpleDateFormat
import androidx.work.*
import com.google.android.material.color.DynamicColors
import com.programmersbox.helpfulutils.NotificationDslBuilder
import com.programmersbox.helpfulutils.createNotificationChannel
import com.programmersbox.helpfulutils.notificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class AndroidXReleaseNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)

        applicationContext.createNotificationChannel("androidxchecker")

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "androidxChecker",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AndroidXChecker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
            )
    }
}

class AndroidXChecker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    @SuppressLint("SimpleDateFormat")
    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            val last = withContext(Dispatchers.Default) { applicationContext.lastUpdate.first() }

            val info = Jsoup.connect("https://developer.android.com/feeds/androidx-release-notes.xml").get()

            val latest = info.select("updated")
                .firstOrNull()
                ?.text()
                ?.let(format::parse)
                ?.time ?: 0L

            if (latest > last) {

                val latestInfo = info.select("entry")
                    .map {
                        ReleaseNotes(
                            date = it.select("title").text(),
                            updated = it.select("updated").text(),
                            link = it.select("link").attr("href"),
                            content = it.select("content").text()
                        )
                    }
                    .firstOrNull()

                val n = NotificationDslBuilder.builder(
                    applicationContext,
                    "androidxchecker",
                    R.mipmap.ic_launcher
                ) {
                    title = "New AndroidX Update!"
                    latestInfo?.date?.let { subText = it }
                }

                applicationContext.notificationManager.notify(12, n)

                applicationContext.updatePref(LAST_UPDATE, latest)
            }

            return@withContext Result.success()
        }
        return Result.success()
    }
}