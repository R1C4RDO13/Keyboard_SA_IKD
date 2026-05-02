package org.fossify.keyboard

import android.app.Application
import android.os.StrictMode
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.fossify.commons.extensions.checkUseEnglish
import org.fossify.keyboard.extensions.isDeviceInDirectBootMode
import org.fossify.keyboard.helpers.IkdRetentionWorker
import org.fossify.keyboard.helpers.LiveCaptureSessionStore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            installStrictModeDiskReadDetection()
        }
        if (!isDeviceInDirectBootMode) {
            checkUseEnglish()
            LiveCaptureSessionStore.init(this)
            scheduleIkdRetentionWorker()
        }
        setupEmojiCompat()
    }

    /**
     * Debug-only StrictMode policy that fail-fast logs any disk read on the
     * main thread. This is the dashboard's safety net: aggregator queries
     * ride `Dispatchers.IO`, so a regression that puts a Room read on the
     * UI thread shows up in Logcat immediately on the next debug build.
     */
    private fun installStrictModeDiskReadDetection() {
        val policy = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .penaltyLog()
            .build()
        StrictMode.setThreadPolicy(policy)
    }

    private fun setupEmojiCompat() {
        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)
    }

    private fun scheduleIkdRetentionWorker() {
        val request = PeriodicWorkRequestBuilder<IkdRetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            IkdRetentionWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
