package org.fossify.keyboard.activities

import android.content.Intent
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityMainBinding
import org.fossify.keyboard.extensions.inputMethodManager

class MainActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()

        binding.apply {
            // Phase 10: explicitly set the toolbar title so it always
            // reads "MoodScript". `setupTopAppBar` from Commons does not
            // reliably set a title for activities without an explicit
            // `android:label`, leaving the toolbar empty / stale on some
            // configurations.
            mainToolbar.title = getString(R.string.app_launcher_name)

            setupEdgeToEdge(padBottomSystem = listOf(mainNestedScrollview))
            setupMaterialScrollListener(binding.mainNestedScrollview, binding.mainAppbar)

            changeKeyboardHolder.setOnClickListener {
                inputMethodManager.showInputMethodPicker()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.mainAppbar)
        if (!isKeyboardEnabled()) {
            ConfirmationAdvancedDialog(
                activity = this,
                messageId = R.string.redirection_note,
                positive = R.string.ok,
                negative = 0
            ) { success ->
                if (success) {
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(this)
                    }
                } else {
                    finish()
                }
            }
        }

        updateTextColors(binding.mainNestedScrollview)
        updateChangeKeyboardColor()
    }

    private fun setupOptionsMenu() {
        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> launchSettings()
                R.id.dashboard -> startActivity(Intent(this, DashboardActivity::class.java))
                R.id.sessions_history -> startActivity(Intent(this, SessionsListActivity::class.java))
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        // Phase 10 — launch the local MoodScript AboutActivity instead of the
        // Commons Compose-based one. The Commons class is final + Compose-only
        // with no public extension hooks, so we build a small XML-based screen
        // that surfaces MoodScript-led copy plus a Fossify upstream credit.
        startActivity(Intent(this, AboutActivity::class.java))
    }

    private fun updateChangeKeyboardColor() {
        val applyBackground =
            resources.getDrawable(R.drawable.button_background_rounded, theme) as RippleDrawable
        (applyBackground as LayerDrawable).findDrawableByLayerId(R.id.button_background_holder)
            .applyColorFilter(getProperPrimaryColor())
        binding.changeKeyboard.apply {
            background = applyBackground
            setTextColor(getProperPrimaryColor().getContrastColor())
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        return inputMethodManager.enabledInputMethodList.any {
            it.settingsActivity == SettingsActivity::class.java.canonicalName
        }
    }
}
