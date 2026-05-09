package org.fossify.keyboard.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.keyboard.R

/**
 * Phase 9.5: theme-aware grid heatmap. Each cell is a rounded rect tinted at
 * `getProperPrimaryColor()` with alpha derived from `count / maxIntensity`.
 *
 * Reused unchanged in Phase 9.6 for the hour×weekday circadian heatmap —
 * the activity provides `columns / rows` and X/Y axis labels via
 * [setData] / [setAxisLabels]. Tap-to-toast for cell drilldown comes via
 * [setOnCellClickListener].
 *
 * No hardcoded colour literals — all paints sourced from
 * `getProperPrimaryColor()` / `getProperTextColor()` so theme switches
 * propagate on `applyTheme()` calls.
 */
class IkdHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    /** One cell of the heatmap grid. `column` is X (0-based), `row` is Y (0-based). */
    data class Cell(val column: Int, val row: Int, val count: Int, val label: String)

    /** Listener invoked on tap — passes back the cell that was hit. */
    fun interface OnCellClickListener {
        fun onCellClick(cell: Cell)
    }

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            LABEL_TEXT_SIZE_SP,
            resources.displayMetrics,
        )
    }

    private var cells: List<Cell> = emptyList()
    private var columns: Int = 0
    private var rows: Int = 0
    private var maxIntensity: Int = 0
    private var xAxisLabels: List<String> = emptyList()
    private var yAxisLabels: List<String> = emptyList()
    private var clickListener: OnCellClickListener? = null

    private val cellMarginPx = resources.getDimension(R.dimen.heatmap_cell_margin_dp)
    private val cellCornerPx = resources.getDimension(R.dimen.heatmap_cell_corner_dp)
    private val cellMinPx = resources.getDimension(R.dimen.heatmap_cell_min_size_dp)

    /**
     * Provide the data. `cells` may be sparse — missing cells render as
     * scaffold (5% alpha). `maxIntensity` scales alpha; passing `0` is
     * tolerated and the entire grid renders at the floor alpha.
     */
    fun setData(
        cells: List<Cell>,
        columns: Int,
        rows: Int,
        maxIntensity: Int,
    ) {
        this.cells = cells
        this.columns = columns
        this.rows = rows
        this.maxIntensity = maxIntensity
        applyTheme()
        invalidate()
    }

    fun setAxisLabels(x: List<String>, y: List<String>) {
        this.xAxisLabels = x
        this.yAxisLabels = y
        invalidate()
    }

    fun setOnCellClickListener(listener: OnCellClickListener?) {
        this.clickListener = listener
    }

    private fun applyTheme() {
        labelPaint.color = context.getProperTextColor()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Measure width from the spec; height is computed from the cell count.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val effectiveColumns = columns.coerceAtLeast(1)
        val effectiveRows = rows.coerceAtLeast(1)
        val labelHeightPx = if (xAxisLabels.isNotEmpty()) labelPaint.textSize * X_LABEL_FACTOR else 0f
        val labelWidthPx = if (yAxisLabels.isNotEmpty()) Y_LABEL_WIDTH_DP * resources.displayMetrics.density else 0f
        val gridWidth = width - paddingLeft - paddingRight - labelWidthPx
        val cellSize = (gridWidth / effectiveColumns).coerceAtLeast(cellMinPx)
        val height = (cellSize * effectiveRows + paddingTop + paddingBottom + labelHeightPx).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (columns <= 0 || rows <= 0) return

        val labelHeightPx = if (xAxisLabels.isNotEmpty()) labelPaint.textSize * X_LABEL_FACTOR else 0f
        val labelWidthPx = if (yAxisLabels.isNotEmpty()) Y_LABEL_WIDTH_DP * resources.displayMetrics.density else 0f
        val gridLeft = paddingLeft.toFloat() + labelWidthPx
        val gridTop = paddingTop.toFloat()
        val gridRight = width - paddingRight.toFloat()
        val gridBottom = height - paddingBottom.toFloat() - labelHeightPx
        val cellWidth = (gridRight - gridLeft) / columns
        val cellHeight = (gridBottom - gridTop) / rows

        val primary = context.getProperPrimaryColor()

        // Draw scaffold cells first (every grid position) at the floor alpha;
        // the data cells overdraw with their actual intensity.
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                drawCell(canvas, gridLeft, gridTop, cellWidth, cellHeight, col, row, primary, FLOOR_ALPHA)
            }
        }

        // Index cells for quick lookup so we don't redraw scaffold positions.
        val cellMap = cells.associateBy { it.column to it.row }
        for ((_, cell) in cellMap) {
            val alpha = cellAlpha(cell.count, maxIntensity)
            drawCell(canvas, gridLeft, gridTop, cellWidth, cellHeight, cell.column, cell.row, primary, alpha)
        }

        // X axis labels along the bottom.
        if (xAxisLabels.isNotEmpty()) {
            val y = gridBottom + labelPaint.textSize
            xAxisLabels.forEachIndexed { idx, label ->
                if (idx >= columns) return@forEachIndexed
                val x = gridLeft + cellWidth * (idx + HALF_OFFSET)
                canvas.drawText(label, x, y, labelPaint)
            }
        }
        // Y axis labels at the leading edge.
        if (yAxisLabels.isNotEmpty()) {
            yAxisLabels.forEachIndexed { idx, label ->
                if (idx >= rows) return@forEachIndexed
                val y = gridTop + cellHeight * (idx + HALF_OFFSET) + labelPaint.textSize / Y_LABEL_BASELINE_DIVISOR
                canvas.drawText(label, gridLeft - labelWidthPx / Y_LABEL_BASELINE_DIVISOR, y, labelPaint)
            }
        }
    }

    private fun drawCell(
        canvas: Canvas,
        gridLeft: Float,
        gridTop: Float,
        cellWidth: Float,
        cellHeight: Float,
        col: Int,
        row: Int,
        primary: Int,
        alpha: Float,
    ) {
        val left = gridLeft + col * cellWidth + cellMarginPx
        val top = gridTop + row * cellHeight + cellMarginPx
        val right = gridLeft + (col + 1) * cellWidth - cellMarginPx
        val bottom = gridTop + (row + 1) * cellHeight - cellMarginPx
        cellPaint.color = ColorUtils.setAlphaComponent(primary, (alpha * MAX_BYTE).toInt().coerceIn(0, MAX_BYTE))
        canvas.drawRoundRect(RectF(left, top, right, bottom), cellCornerPx, cellCornerPx, cellPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        val listener = clickListener ?: return super.onTouchEvent(event)

        val labelHeightPx = if (xAxisLabels.isNotEmpty()) labelPaint.textSize * X_LABEL_FACTOR else 0f
        val labelWidthPx = if (yAxisLabels.isNotEmpty()) Y_LABEL_WIDTH_DP * resources.displayMetrics.density else 0f
        val gridLeft = paddingLeft.toFloat() + labelWidthPx
        val gridTop = paddingTop.toFloat()
        val gridRight = width - paddingRight.toFloat()
        val gridBottom = height - paddingBottom.toFloat() - labelHeightPx

        if (event.x < gridLeft || event.x > gridRight || event.y < gridTop || event.y > gridBottom) {
            return super.onTouchEvent(event)
        }

        val cellWidth = (gridRight - gridLeft) / columns.coerceAtLeast(1)
        val cellHeight = (gridBottom - gridTop) / rows.coerceAtLeast(1)
        val col = ((event.x - gridLeft) / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = ((event.y - gridTop) / cellHeight).toInt().coerceIn(0, rows - 1)
        val cell = cells.firstOrNull { it.column == col && it.row == row }
            ?: Cell(column = col, row = row, count = 0, label = "")
        listener.onCellClick(cell)
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    init {
        // Force-set a transparent background so the View doesn't blot the parent card colour.
        setBackgroundColor(Color.TRANSPARENT)
        applyTheme()
    }

    companion object {
        // 5% alpha — visible scaffold for empty cells.
        const val FLOOR_ALPHA: Float = 0.05f
        private const val MAX_BYTE: Int = 255
        private const val LABEL_TEXT_SIZE_SP: Float = 10f
        private const val X_LABEL_FACTOR: Float = 1.6f
        private const val Y_LABEL_WIDTH_DP: Float = 32f
        private const val HALF_OFFSET: Float = 0.5f
        private const val Y_LABEL_BASELINE_DIVISOR: Float = 3f

        /**
         * Pure helper for unit testing — `count / max` clamped to `[FLOOR_ALPHA, 1.0]`.
         * Returns `FLOOR_ALPHA` when `max <= 0` so the all-empty case still renders
         * scaffold colour rather than fully transparent cells.
         */
        fun cellAlpha(count: Int, max: Int): Float {
            if (max <= 0) return FLOOR_ALPHA
            return (count.toFloat() / max.toFloat()).coerceIn(FLOOR_ALPHA, 1.0f)
        }
    }
}
