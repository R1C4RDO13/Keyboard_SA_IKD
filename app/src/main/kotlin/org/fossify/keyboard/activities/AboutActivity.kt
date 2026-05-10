package org.fossify.keyboard.activities

import android.os.Bundle
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityAboutBinding

/**
 * Phase 10 — local About screen for MoodScript.
 *
 * The Commons `org.fossify.commons.activities.AboutActivity` class is `final`
 * and Compose-based with no public extension points, so subclassing it (the
 * Phase 10 plan's preferred approach) is not possible against commons 6.1.6.
 * Instead, this activity is a small XML-based replacement that surfaces the
 * MoodScript-led intro copy + version + license + Fossify upstream credit.
 *
 * Wired into [MainActivity.launchAbout] directly; the manifest entry exists
 * so the activity is a first-class Android component and the system can
 * resolve it via `parentActivityName` for the up-arrow.
 */
class AboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAboutBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(aboutNestedScrollview))
            setupMaterialScrollListener(aboutNestedScrollview, aboutAppbar)

            aboutVersionValue.text = BuildConfig.VERSION_NAME

            aboutCredit.setOnClickListener {
                launchViewIntent(getString(R.string.moodscript_about_upstream_link))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.aboutNestedScrollview)
    }
}
