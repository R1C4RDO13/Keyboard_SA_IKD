package org.fossify.keyboard.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.DialogWidgetInfoBinding

/**
 * Phase 9.13: presents a [WidgetInfo] in a themed [MaterialAlertDialogBuilder].
 *
 * Theme discipline matches Phase 6 / 9.12: every text/heading colour goes
 * through `getProperTextColor()` / `getProperPrimaryColor()` so the
 * popup tracks the user's Fossify theme on both light and dark builds.
 *
 * Layout: `dialog_widget_info.xml`. The formula block stays GONE when
 * [WidgetInfo.formulaRes] is null.
 */
fun Context.showWidgetInfo(info: WidgetInfo) {
    val binding = DialogWidgetInfoBinding.inflate(LayoutInflater.from(this))
    val textColor = getProperTextColor()
    val primaryColor = getProperPrimaryColor()
    val backgroundColor = getProperBackgroundColor()

    binding.widgetInfoTitle.apply {
        text = getString(info.titleRes)
        setTextColor(textColor)
    }
    binding.widgetInfoDescription.apply {
        text = getString(info.descriptionRes)
        setTextColor(textColor)
    }
    binding.widgetInfoInterpretationHeader.setTextColor(primaryColor)
    binding.widgetInfoInterpretation.apply {
        text = getString(info.interpretationRes)
        setTextColor(textColor)
    }

    val formulaRes = info.formulaRes
    if (formulaRes != null) {
        binding.widgetInfoFormulaHeader.visibility = View.VISIBLE
        binding.widgetInfoFormula.visibility = View.VISIBLE
        binding.widgetInfoFormulaHeader.setTextColor(primaryColor)
        binding.widgetInfoFormula.apply {
            text = getString(formulaRes)
            setTextColor(textColor)
        }
    } else {
        binding.widgetInfoFormulaHeader.visibility = View.GONE
        binding.widgetInfoFormula.visibility = View.GONE
    }
    binding.widgetInfoRoot.setBackgroundColor(backgroundColor)

    MaterialAlertDialogBuilder(this)
        .setView(binding.root)
        .setPositiveButton(R.string.widget_info_close, null)
        .show()
}

/**
 * Convenience: flip any view into an info trigger for the given [WidgetInfo].
 * The receiver becomes clickable and opens [showWidgetInfo] on tap.
 */
fun View.attachWidgetInfo(info: WidgetInfo) {
    setOnClickListener { context.showWidgetInfo(info) }
}
