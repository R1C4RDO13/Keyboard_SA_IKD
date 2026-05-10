package org.fossify.keyboard.activities.dashboard

import org.fossify.keyboard.helpers.IkdActivityAggregator
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.IkdDistributionAggregator
import org.fossify.keyboard.helpers.IkdHabitsAggregator
import org.fossify.keyboard.helpers.IkdMoodAggregator
import org.fossify.keyboard.helpers.IkdOrientationAggregator
import org.fossify.keyboard.helpers.IkdQualityAggregator
import org.fossify.keyboard.helpers.IkdSensorAggregator

/**
 * Phase 9.11: payload bundle handed from the activity-level data loader
 * to the per-tab fragments. Lifted out of `DashboardActivity` so the
 * fragments can reference it without an inner-class dependency.
 *
 * One instance per `loadSnapshot()` invocation — the activity holds the
 * latest as a private field so a freshly-attached fragment can render
 * itself at any point in the activity's lifecycle.
 */
data class DashboardPayload(
    val ikd: IkdAggregator.Snapshot,
    val mood: IkdMoodAggregator.MoodSnapshot,
    val moodMix: IkdMoodAggregator.MoodMixSnapshot,
    val sensor: IkdSensorAggregator.Snapshot,
    val habits: IkdHabitsAggregator.HabitsSnapshot,
    val activity: IkdActivityAggregator.ActivitySnapshot,
    val distribution: IkdDistributionAggregator.DistributionSnapshot,
    val orientation: IkdOrientationAggregator.OrientationSnapshot,
    val quality: IkdQualityAggregator.QualitySnapshot,
)
