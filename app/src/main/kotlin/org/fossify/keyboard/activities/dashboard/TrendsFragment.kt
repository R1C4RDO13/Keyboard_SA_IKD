package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardTrendsBinding

/**
 * Phase 9.11: Trends tab. Three IKD line charts (Speed / IKD / Error
 * rate) plus the Phase 9.2 gyro / accel global trend charts. Each chart
 * card hides itself + its title when its data is empty; the tab renders
 * an empty placeholder when *every* chart is empty.
 */
class TrendsFragment : DashboardFragment() {

    private var _binding: FragmentDashboardTrendsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val ctx = context ?: return
        val view = _binding ?: return

        val snap = payload.ikd
        val sensor = payload.sensor

        val labels = snap.buckets.map { DashboardLabelFormat.formatBucketLabel(ctx, it.label, snap.range) }
        val wpmValues = snap.buckets.map { it.wpm?.toFloat() }
        val ikdValues = snap.buckets.map { it.avgIkdMs?.toFloat() }
        val errorValues = snap.buckets.map { it.errorRatePct?.toFloat() }

        view.dashboardChartSpeed.setData(labels, wpmValues, getString(R.string.dashboard_chart_speed))
        view.dashboardChartIkd.setData(labels, ikdValues, getString(R.string.dashboard_chart_ikd))
        view.dashboardChartError.setData(labels, errorValues, getString(R.string.dashboard_chart_error))

        // Phase 9.2: gyro / accel global trend charts. Hidden along with
        // their titles when no buckets carry that sensor (e.g., user
        // disabled gyro or all sessions in range are sensor-free).
        val sensorLabels = sensor.buckets.map {
            DashboardLabelFormat.formatBucketLabel(ctx, it.label, sensor.range)
        }
        val gyroValues = sensor.buckets.map { it.gyroMag?.toFloat() }
        val accelValues = sensor.buckets.map { it.accelMag?.toFloat() }

        val hasGyro = gyroValues.any { it != null }
        val hasAccel = accelValues.any { it != null }

        view.dashboardChartGyroTitle.beVisibleIf(hasGyro)
        view.dashboardChartGyro.beVisibleIf(hasGyro)
        if (hasGyro) {
            view.dashboardChartGyro.setData(
                sensorLabels,
                gyroValues,
                getString(R.string.dashboard_chart_gyro_y_label),
            )
        }

        view.dashboardChartAccelTitle.beVisibleIf(hasAccel)
        view.dashboardChartAccel.beVisibleIf(hasAccel)
        if (hasAccel) {
            view.dashboardChartAccel.setData(
                sensorLabels,
                accelValues,
                getString(R.string.dashboard_chart_accel_y_label),
            )
        }

        // Trends tab empty placeholder: only when *all* of speed / ikd /
        // error / gyro / accel have no data. Speed / IKD / error always
        // render labels even if values are null (line breaks); the
        // empty placeholder fires only when the snapshot has no buckets
        // at all, which already implies the global empty state on the
        // host activity is taking over.
        val anyData = snap.buckets.isNotEmpty()
        view.fragmentTrendsEmptyMessage.beVisibleIf(!anyData)
    }
}
