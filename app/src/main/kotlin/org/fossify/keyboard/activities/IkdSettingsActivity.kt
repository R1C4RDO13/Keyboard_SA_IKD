package org.fossify.keyboard.activities

import android.hardware.SensorManager
import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityIkdSettingsBinding
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.helpers.KinematicSensorHelper
import org.fossify.keyboard.helpers.RETENTION_DAYS_14
import org.fossify.keyboard.helpers.RETENTION_DAYS_60
import org.fossify.keyboard.helpers.RETENTION_DAYS_7
import org.fossify.keyboard.helpers.RETENTION_DAYS_90
import org.fossify.keyboard.helpers.RETENTION_DAYS_DEFAULT
import org.fossify.keyboard.helpers.RETENTION_FOREVER

class IkdSettingsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityIkdSettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(ikdSettingsNestedScrollview))
            setupMaterialScrollListener(ikdSettingsNestedScrollview, ikdSettingsAppbar)
        }

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.ikdSettingsAppbar, NavigationIcon.Arrow)
        applySensorAvailability()
        applyControlState()
        applySectionLabelColors()
        binding.apply {
            updateTextColors(ikdSettingsNestedScrollview)
        }
    }

    private fun applySectionLabelColors() {
        val color = getProperPrimaryColor()
        binding.apply {
            ikdSectionPrivacyLabel.setTextColor(color)
            ikdSectionSensorsLabel.setTextColor(color)
            ikdSectionSamplingLabel.setTextColor(color)
            ikdSectionMetadataLabel.setTextColor(color)
            ikdSectionRetentionLabel.setTextColor(color)
            ikdSectionStorageLabel.setTextColor(color)
        }
    }

    private fun applySensorAvailability() {
        val helper = KinematicSensorHelper(
            context = this,
            getSessionId = { "" }
        ) { /* no-op probe */ }
        binding.ikdCollectGyroHolder.beVisibleIf(helper.hasGyro)
        binding.ikdCollectAccelHolder.beVisibleIf(helper.hasAccel)
    }

    private fun applyControlState() {
        binding.ikdPrivacyModeSwitch.isChecked = config.privacyModeEnabled
        binding.ikdCollectGyroCheckbox.isChecked = config.collectGyro
        binding.ikdCollectAccelCheckbox.isChecked = config.collectAccel

        val samplingRadioId = when (config.sensorSamplingRate) {
            SensorManager.SENSOR_DELAY_FASTEST -> R.id.ikd_sampling_fastest
            SensorManager.SENSOR_DELAY_GAME -> R.id.ikd_sampling_game
            SensorManager.SENSOR_DELAY_UI -> R.id.ikd_sampling_ui
            SensorManager.SENSOR_DELAY_NORMAL -> R.id.ikd_sampling_normal
            else -> R.id.ikd_sampling_game
        }
        binding.ikdSamplingRateGroup.check(samplingRadioId)

        binding.ikdCaptureOrientationCheckbox.isChecked = config.captureOrientation
        binding.ikdCaptureLocaleCheckbox.isChecked = config.captureLocale
        binding.ikdCaptureBatteryCheckbox.isChecked = config.captureBattery

        val retentionRadioId = when (config.retentionDays) {
            RETENTION_FOREVER -> R.id.ikd_retention_forever
            RETENTION_DAYS_7 -> R.id.ikd_retention_7
            RETENTION_DAYS_14 -> R.id.ikd_retention_14
            RETENTION_DAYS_DEFAULT -> R.id.ikd_retention_30
            RETENTION_DAYS_60 -> R.id.ikd_retention_60
            RETENTION_DAYS_90 -> R.id.ikd_retention_90
            else -> R.id.ikd_retention_30
        }
        binding.ikdRetentionGroup.check(retentionRadioId)

        val placeholder = getString(R.string.diagnostics_value_none)
        binding.ikdStorageDbSizeValue.text = placeholder
        binding.ikdStorageSessionCountValue.text = placeholder
        binding.ikdStorageEventCountValue.text = placeholder
        binding.ikdStorageSensorCountValue.text = placeholder
    }

    private fun setupListeners() {
        binding.apply {
            ikdPrivacyModeHolder.setOnClickListener { ikdPrivacyModeSwitch.toggle() }
            ikdPrivacyModeSwitch.setOnCheckedChangeListener { _, checked ->
                config.privacyModeEnabled = checked
            }

            ikdCollectGyroHolder.setOnClickListener { ikdCollectGyroCheckbox.toggle() }
            ikdCollectGyroCheckbox.setOnCheckedChangeListener { _, checked ->
                config.collectGyro = checked
            }

            ikdCollectAccelHolder.setOnClickListener { ikdCollectAccelCheckbox.toggle() }
            ikdCollectAccelCheckbox.setOnCheckedChangeListener { _, checked ->
                config.collectAccel = checked
            }

            ikdSamplingRateGroup.setOnCheckedChangeListener { _, checkedId ->
                config.sensorSamplingRate = when (checkedId) {
                    R.id.ikd_sampling_fastest -> SensorManager.SENSOR_DELAY_FASTEST
                    R.id.ikd_sampling_game -> SensorManager.SENSOR_DELAY_GAME
                    R.id.ikd_sampling_ui -> SensorManager.SENSOR_DELAY_UI
                    R.id.ikd_sampling_normal -> SensorManager.SENSOR_DELAY_NORMAL
                    else -> SensorManager.SENSOR_DELAY_GAME
                }
            }

            ikdCaptureOrientationHolder.setOnClickListener { ikdCaptureOrientationCheckbox.toggle() }
            ikdCaptureOrientationCheckbox.setOnCheckedChangeListener { _, checked ->
                config.captureOrientation = checked
            }

            ikdCaptureLocaleHolder.setOnClickListener { ikdCaptureLocaleCheckbox.toggle() }
            ikdCaptureLocaleCheckbox.setOnCheckedChangeListener { _, checked ->
                config.captureLocale = checked
            }

            ikdCaptureBatteryHolder.setOnClickListener { ikdCaptureBatteryCheckbox.toggle() }
            ikdCaptureBatteryCheckbox.setOnCheckedChangeListener { _, checked ->
                config.captureBattery = checked
            }

            ikdRetentionGroup.setOnCheckedChangeListener { _, checkedId ->
                config.retentionDays = when (checkedId) {
                    R.id.ikd_retention_forever -> RETENTION_FOREVER
                    R.id.ikd_retention_7 -> RETENTION_DAYS_7
                    R.id.ikd_retention_14 -> RETENTION_DAYS_14
                    R.id.ikd_retention_30 -> RETENTION_DAYS_DEFAULT
                    R.id.ikd_retention_60 -> RETENTION_DAYS_60
                    R.id.ikd_retention_90 -> RETENTION_DAYS_90
                    else -> RETENTION_DAYS_DEFAULT
                }
            }

            ikdViewSessionsButton.setOnClickListener { toast(R.string.coming_soon) }
            ikdExportAllButton.setOnClickListener { toast(R.string.coming_soon) }
            ikdDeleteAllButton.setOnClickListener { toast(R.string.coming_soon) }
        }
    }
}
