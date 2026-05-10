package org.fossify.keyboard.helpers

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isBlackAndWhiteTheme
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.updateTextColors
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.DialogInsightsFiltersBinding
import org.fossify.keyboard.extensions.moodDB

/**
 * Phase 9.14.3: bottom sheet that consolidates the dashboard's range +
 * mood filters behind the toolbar Filters action. Replaces the
 * standalone range toggle and mood-chip row that previously lived in
 * the activity chrome.
 *
 * Communication with the host activity goes through
 * [setFragmentResult]: the activity registers a listener under
 * [REQUEST_KEY] and reads the new `(range, moodFilter)` from the
 * returned bundle (`KEY_RANGE` carries an [IkdAggregator.Range] enum
 * name; `KEY_MOOD_FILTER` carries the score 1..6 or
 * [MOOD_FILTER_NONE]).
 *
 * Theming follows the Phase 9.13 dialog discipline — the dialog
 * window's background drawable is set on `onStart` to one of the
 * Fossify dialog drawables (`black_dialog_background` /
 * `dialog_you_background` / `dialog_bg`) tinted with the current theme
 * background colour, so the sheet matches the Insights screen on every
 * built-in Fossify theme.
 */
class InsightsFiltersBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogInsightsFiltersBinding? = null
    private val binding get() = _binding!!

    private var pendingRange: IkdAggregator.Range = IkdAggregator.Range.WEEK
    private var pendingMood: Int? = null
    private val moodChips: MutableList<Chip> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogInsightsFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        pendingRange = args?.getString(ARG_RANGE)
            ?.let { runCatching { IkdAggregator.Range.valueOf(it) }.getOrNull() }
            ?: IkdAggregator.Range.WEEK
        val storedMood = args?.getInt(ARG_MOOD_FILTER, MOOD_FILTER_NONE) ?: MOOD_FILTER_NONE
        pendingMood = if (storedMood == MOOD_FILTER_NONE) null else storedMood

        applyTheme()
        bindRangeGroup()
        bindMoodChips()
        wireActionButtons()
    }

    /**
     * Phase 9.13 dialog discipline: re-tint the sheet body so text and
     * primary-coloured headings track the user's Fossify theme. Mirrors
     * `Context.showWidgetInfo` in `WidgetInfoDialog`.
     */
    private fun applyTheme() {
        val ctx = requireContext()
        val textColor = ctx.getProperTextColor()
        val primaryColor = ctx.getProperPrimaryColor()
        val bgColor = ctx.getProperBackgroundColor()

        binding.insightsFiltersRoot.setBackgroundColor(bgColor)
        ctx.updateTextColors(binding.insightsFiltersRoot)
        binding.insightsFiltersTitle.setTextColor(textColor)
        binding.insightsFiltersRangeHeader.setTextColor(primaryColor)
        binding.insightsFiltersMoodHeader.setTextColor(primaryColor)

        applyToggleGroupColors(binding.insightsFiltersRangeGroup, primaryColor)

        // Reset (outlined) + Apply (filled) action buttons. Both pull
        // tones from the user-selected primary so they read against the
        // sheet body on every Fossify theme.
        val onPrimary = primaryColor.getContrastColor()
        binding.insightsFiltersReset.apply {
            setTextColor(primaryColor)
            strokeColor = ColorStateList.valueOf(primaryColor)
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        }
        binding.insightsFiltersApply.apply {
            setTextColor(onPrimary)
            backgroundTintList = ColorStateList.valueOf(primaryColor)
        }
    }

    /**
     * Reuses the same primary-tint-on-checked / transparent-on-unchecked
     * pattern as `DashboardActivity.applyToggleGroupColors` so the sheet
     * looks identical to the toggle group it replaced on the chrome.
     */
    private fun applyToggleGroupColors(group: MaterialButtonToggleGroup, primary: Int) {
        val onPrimary = primary.getContrastColor()
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val uncheckedState = intArrayOf(-android.R.attr.state_checked)
        val states = arrayOf(checkedState, uncheckedState)

        val textColors = ColorStateList(states, intArrayOf(onPrimary, primary))
        val bgColors = ColorStateList(states, intArrayOf(primary, android.graphics.Color.TRANSPARENT))
        val strokeColors = ColorStateList(states, intArrayOf(primary, primary))

        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) as? MaterialButton ?: continue
            child.setTextColor(textColors)
            child.backgroundTintList = bgColors
            child.strokeColor = strokeColors
        }
    }

    private fun bindRangeGroup() {
        binding.insightsFiltersRangeGroup.check(rangeButtonId(pendingRange))
        binding.insightsFiltersRangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            pendingRange = idToRange(checkedId)
        }
    }

    /**
     * Mood chips (All + Ekman 6). Mood section is hidden until
     * [moodDB] reports at least one entry — same gate as the
     * Phase 9.4 chip-row visibility (Decision #11).
     */
    private fun bindMoodChips() {
        val ctx = requireContext()
        val container = binding.insightsFiltersMoodContainer
        val inflater = LayoutInflater.from(ctx)
        moodChips.clear()
        container.removeAllViews()

        val allChip = inflater.inflate(R.layout.item_mood_filter_chip, container, false) as Chip
        allChip.text = ctx.getString(R.string.dashboard_mood_filter_all)
        allChip.contentDescription = ctx.getString(R.string.dashboard_mood_filter_all)
        allChip.setOnClickListener {
            pendingMood = null
            refreshMoodChipState()
            applyMoodChipColors()
        }
        container.addView(allChip)
        moodChips.add(allChip)

        for (score in MoodEmoji.displayOrder()) {
            val chip = inflater.inflate(R.layout.item_mood_filter_chip, container, false) as Chip
            val emoji = MoodEmoji.emojiFor(score)
            val label = ctx.getString(MoodEmoji.labelResFor(score))
            chip.text = ctx.getString(R.string.insights_filters_mood_chip_format, emoji, label)
            chip.contentDescription = label
            chip.setOnClickListener {
                pendingMood = if (pendingMood == score) null else score
                refreshMoodChipState()
                applyMoodChipColors()
            }
            container.addView(chip)
            moodChips.add(chip)
        }
        refreshMoodChipState()
        applyMoodChipColors()

        // Mood section gate (Decision #11).
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) { ctx.moodDB.hasAnyMoodEntry() }
            binding.insightsFiltersMoodHeader.beVisibleIf(available)
            binding.insightsFiltersMoodScroll.beVisibleIf(available)
            if (!available && pendingMood != null) {
                pendingMood = null
                refreshMoodChipState()
                applyMoodChipColors()
            }
        }
    }

    private fun refreshMoodChipState() {
        if (moodChips.isEmpty()) return
        moodChips[0].isChecked = pendingMood == null
        val displayOrder = MoodEmoji.displayOrder()
        for (idx in displayOrder.indices) {
            val chipIdx = idx + 1
            if (chipIdx < moodChips.size) {
                moodChips[chipIdx].isChecked = pendingMood == displayOrder[idx]
            }
        }
    }

    private fun applyMoodChipColors() {
        val ctx = requireContext()
        val primary = ctx.getProperPrimaryColor()
        val background = ctx.getProperBackgroundColor()
        val onPrimary = primary.getContrastColor()
        val textColor = ctx.getProperTextColor()
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val uncheckedState = intArrayOf(-android.R.attr.state_checked)
        val states = arrayOf(checkedState, uncheckedState)

        val bgColors = ColorStateList(states, intArrayOf(primary, background))
        val txtColors = ColorStateList(states, intArrayOf(onPrimary, textColor))
        val strokeColors = ColorStateList(states, intArrayOf(primary, primary))
        val strokeWidth = ctx.resources.getDimension(R.dimen.chip_stroke_width)
        for (chip in moodChips) {
            chip.chipBackgroundColor = bgColors
            chip.setTextColor(txtColors)
            chip.chipStrokeColor = strokeColors
            chip.chipStrokeWidth = strokeWidth
        }
    }

    private fun wireActionButtons() {
        binding.insightsFiltersReset.setOnClickListener {
            pendingRange = IkdAggregator.Range.WEEK
            pendingMood = null
            binding.insightsFiltersRangeGroup.check(rangeButtonId(pendingRange))
            refreshMoodChipState()
            applyMoodChipColors()
        }
        binding.insightsFiltersApply.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    KEY_RANGE to pendingRange.name,
                    KEY_MOOD_FILTER to (pendingMood ?: MOOD_FILTER_NONE),
                ),
            )
            dismiss()
        }
    }

    /**
     * Phase 9.13 lesson — set the dialog window's background drawable
     * after `show()` so the bottom sheet matches the Fossify theme
     * instead of falling back to Material's default surface.
     */
    override fun onStart() {
        super.onStart()
        val ctx = requireContext()
        val bgDrawable = when {
            ctx.isBlackAndWhiteTheme() -> ResourcesCompat.getDrawable(
                ctx.resources, R.drawable.black_dialog_background, ctx.theme,
            )
            ctx.isDynamicTheme() -> ResourcesCompat.getDrawable(
                ctx.resources, R.drawable.dialog_you_background, ctx.theme,
            )
            else -> ctx.resources.getColoredDrawableWithColor(
                drawableId = R.drawable.dialog_bg,
                color = ctx.baseConfig.backgroundColor,
            )
        }
        dialog?.window?.setBackgroundDrawable(bgDrawable)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun rangeButtonId(range: IkdAggregator.Range): Int = when (range) {
        IkdAggregator.Range.TODAY -> R.id.insights_filters_range_today
        IkdAggregator.Range.WEEK -> R.id.insights_filters_range_week
        IkdAggregator.Range.MONTH -> R.id.insights_filters_range_month
        IkdAggregator.Range.ALL_TIME -> R.id.insights_filters_range_all
    }

    private fun idToRange(id: Int): IkdAggregator.Range = when (id) {
        R.id.insights_filters_range_today -> IkdAggregator.Range.TODAY
        R.id.insights_filters_range_week -> IkdAggregator.Range.WEEK
        R.id.insights_filters_range_month -> IkdAggregator.Range.MONTH
        R.id.insights_filters_range_all -> IkdAggregator.Range.ALL_TIME
        else -> IkdAggregator.Range.WEEK
    }

    companion object {
        const val TAG = "InsightsFiltersBottomSheet"
        const val REQUEST_KEY = "insights_filters_result"
        const val KEY_RANGE = "range"
        const val KEY_MOOD_FILTER = "mood_filter"
        const val MOOD_FILTER_NONE = -1

        private const val ARG_RANGE = "arg_range"
        private const val ARG_MOOD_FILTER = "arg_mood_filter"

        fun newInstance(
            range: IkdAggregator.Range,
            moodFilter: Int?,
        ): InsightsFiltersBottomSheet = InsightsFiltersBottomSheet().apply {
            arguments = bundleOf(
                ARG_RANGE to range.name,
                ARG_MOOD_FILTER to (moodFilter ?: MOOD_FILTER_NONE),
            )
        }
    }
}
