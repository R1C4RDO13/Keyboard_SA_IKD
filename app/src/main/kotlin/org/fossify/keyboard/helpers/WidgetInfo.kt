package org.fossify.keyboard.helpers

import androidx.annotation.StringRes

/**
 * Phase 9.13: declarative description of one Insights widget — the title
 * shown in the popup, the body paragraph, the "How to read it" hint and
 * an optional formula block.
 *
 * `formulaRes` is nullable on purpose: count-only widgets (Sessions,
 * Distribution panels) don't have math worth showing, while metrics like
 * WPM and Error rate do.
 *
 * The icon next to each widget is a tap target that resolves to one of
 * these via `View.attachWidgetInfo` (see [WidgetInfoDialog]). All copy
 * lives in `res/values/strings_widget_info.xml` so future translation is
 * a string-resource pass.
 */
data class WidgetInfo(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val interpretationRes: Int,
    @StringRes val formulaRes: Int?,
)
