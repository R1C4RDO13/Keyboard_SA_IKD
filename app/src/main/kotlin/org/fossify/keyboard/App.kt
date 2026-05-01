package org.fossify.keyboard

import android.app.Application
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
        if (!isDeviceInDirectBootMode) {
            checkUseEnglish()
            LiveCaptureSessionStore.init(this)
            scheduleIkdRetentionWorker()
        }
        setupEmojiCompat()
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
