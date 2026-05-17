package org.fossify.keyboard.views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.updateMarginsRelative
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiCompat.EMOJI_SUPPORTED
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beInvisibleIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.darkenColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.lightenColor
import org.fossify.commons.extensions.removeUnderlines
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.HIGHER_ALPHA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isPiePlus
import org.fossify.keyboard.R
import org.fossify.keyboard.activities.ManageClipboardItemsActivity
import org.fossify.keyboard.activities.DashboardActivity
import org.fossify.keyboard.activities.SettingsActivity
import org.fossify.keyboard.adapters.ClipsKeyboardAdapter
import org.fossify.keyboard.adapters.EmojisAdapter
import org.fossify.keyboard.databinding.ItemEmojiCategoryBinding
import org.fossify.keyboard.databinding.KeyboardKeyPreviewBinding
import org.fossify.keyboard.databinding.KeyboardPopupKeyboardBinding
import org.fossify.keyboard.databinding.KeyboardViewKeyboardBinding
import org.fossify.keyboard.dialogs.SwitchLanguageDialog
import org.fossify.keyboard.extensions.clipsDB
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.extensions.getCurrentClip
import org.fossify.keyboard.extensions.getCurrentVoiceInputMethod
import org.fossify.keyboard.extensions.getKeyboardBackgroundColor
import org.fossify.keyboard.extensions.getStrokeColor
import org.fossify.keyboard.extensions.isDeviceLocked
import org.fossify.keyboard.extensions.onScroll
import org.fossify.keyboard.extensions.safeStorageContext
import org.fossify.keyboard.extensions.ikdMoodBarController
import org.fossify.keyboard.helpers.AccessHelper
import org.fossify.keyboard.helpers.EMOJI_SPEC_FILE_PATH
import org.fossify.keyboard.helpers.EmojiData
import org.fossify.keyboard.helpers.IkdMoodBarController
import org.fossify.keyboard.helpers.KeyboardFeedbackManager
import org.fossify.keyboard.helpers.LANGUAGE_TURKISH_Q
import org.fossify.keyboard.helpers.LANGUAGE_VIETNAMESE_TELEX
import org.fossify.keyboard.helpers.LANGUAGE_VN_TELEX
import org.fossify.keyboard.helpers.LiveCaptureSessionStore
import org.fossify.keyboard.helpers.MOOD_CURATED_CATEGORY
import org.fossify.keyboard.helpers.MOOD_INACTIVITY_TIMEOUT_MS
import org.fossify.keyboard.helpers.MoodEmoji
import org.fossify.keyboard.helpers.MAX_KEYS_PER_MINI_ROW
import org.fossify.keyboard.helpers.MyKeyboard
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_DELETE
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_EMOJI_OR_LANGUAGE
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_ENTER
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_MODE_CHANGE
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_POPUP_EMOJI
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_POPUP_SETTINGS
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_SHIFT
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_SPACE
import org.fossify.keyboard.helpers.MyKeyboard.Companion.KEYCODE_SYMBOLS_MODE_CHANGE
import org.fossify.keyboard.helpers.RECENTLY_USED_EMOJIS
import org.fossify.keyboard.helpers.ShiftState
import org.fossify.keyboard.helpers.cachedVNTelexData
import org.fossify.keyboard.helpers.getCategoryIconRes
import org.fossify.keyboard.helpers.parseRawEmojiSpecsFile
import org.fossify.keyboard.helpers.parseRawJsonSpecsFile
import org.fossify.keyboard.interfaces.OnKeyboardActionListener
import org.fossify.keyboard.interfaces.RefreshClipsListener
import org.fossify.keyboard.models.Clip
import org.fossify.keyboard.models.ClipsSectionLabel
import org.fossify.keyboard.models.ListItem
import java.util.Arrays
import java.util.Locale

@SuppressLint("UseCompatLoadingForDrawables", "ClickableViewAccessibility")
class MyKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleRes) {

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return if (accessHelper?.dispatchHoverEvent(event) == true) {
            true
        } else {
            super.dispatchHoverEvent(event)
        }
    }

    private var keyboardPopupBinding: KeyboardPopupKeyboardBinding? = null
    private var keyboardViewBinding: KeyboardViewKeyboardBinding? = null

    private var accessHelper: AccessHelper? = null
    private val feedbackManager by lazy { KeyboardFeedbackManager(context) }

    // Phase 8: mood bar plumbing. The controller writes / clears
    // `mood_entries` rows on `Dispatchers.IO`; this scope is cancelled on
    // detach so a slow Room write cannot keep the view alive.
    private val moodScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val moodController: IkdMoodBarController by lazy { context.ikdMoodBarController }
    /** Currently-highlighted mood-bar slot (1..6 for emotions, null for no highlight, 0 for 🛡️). */
    private var highlightedMoodSlot: Int? = null
    // Phase 8.4: in-flight dance animator for the most-recently-tapped mood
    // slot. Held so a rapid second tap (or a different-slot tap mid-dance)
    // can cancel the prior animation cleanly. Nulled in `onDetachedFromWindow`
    // so a leaked animator can never keep this view alive past detach.
    private var currentMoodDanceAnimator: AnimatorSet? = null

    private var mKeyboard: MyKeyboard? = null
    private var mCurrentKeyIndex: Int = NOT_A_KEY

    private var mLabelTextSize = 0
    private var mKeyTextSize = 0
    private var mSpaceBarTextSize = 0

    private var mTextColor = 0
    private var mBackgroundColor = 0
    private var mKeyboardBackgroundColor = 0
    private var mPrimaryColor = 0
    private var mStrokeColor = 0
    private var mKeyColor = 0
    private var mKeyColorPressed = 0

    private var mPreviewText: TextView? = null
    private val mPreviewPopup: PopupWindow
    private var mPreviewTextSizeLarge = 0
    private var mPreviewHeight = 0

    // Phase 8.2: chat-bubble popup anchored above a tapped mood slot. Lazy
    // because it's only needed when the mood bar is visible; the inflated
    // contentView is reused across taps so we never thrash inflation.
    private var mMoodBubblePopup: PopupWindow? = null
    private var mMoodBubbleText: TextView? = null
    private val mMoodBubbleHandler by lazy { Handler(Looper.getMainLooper()) }
    private val mMoodBubbleDismissRunnable = Runnable { mMoodBubblePopup?.dismiss() }

    // Phase 8.5: deferred auto-collapse after a slot tap (Decision #10).
    // The Runnable runs on the main-thread Handler created above; cancelled
    // in `onDetachedFromWindow` and any time the bar is manually collapsed
    // before the delay elapses.
    private val mMoodCollapseHandler by lazy { Handler(Looper.getMainLooper()) }
    private val mMoodAutoCollapseRunnable = Runnable {
        if (context.config.moodBarExpanded) {
            context.config.moodBarExpanded = false
            applyMoodBarLayout(expanded = false, animate = true)
        }
    }

    private val mCoordinates = IntArray(2)
    private val mPopupKeyboard: PopupWindow
    private var mMiniKeyboardContainer: View? = null
    private var mMiniKeyboard: MyKeyboardView? = null
    private var mMiniKeyboardOnScreen = false
    private var mPopupParent: View
    private var mMiniKeyboardOffsetX = 0
    private var mMiniKeyboardOffsetY = 0
    private val mMiniKeyboardCache: MutableMap<MyKeyboard.Key, View?>
    private var mKeys = ArrayList<MyKeyboard.Key>()
    private var mMiniKeyboardSelectedKeyIndex = -1

    var mOnKeyboardActionListener: OnKeyboardActionListener? = null
    private var mVerticalCorrection = 0
    private var mProximityThreshold = 0
    private var mPopupPreviewX = 0
    private var mPopupPreviewY = 0
    private var mLastX = 0
    private var mLastY = 0

    private val mPaint: Paint
    private var mDownTime = 0L
    private var mLastMoveTime = 0L
    private var mLastKey = 0
    private var mLastCodeX = 0
    private var mLastCodeY = 0
    private var mLastKeyPressedCode = 0
    private var mCurrentKey: Int = NOT_A_KEY
    private var mLastKeyTime = 0L
    private var mCurrentKeyTime = 0L
    private val mKeyIndices = IntArray(12)
    private var mPopupX = 0
    private var mPopupY = 0
    private var mRepeatKeyIndex = NOT_A_KEY
    private var mPopupLayout = 0
    private var mAbortKey = false
    private var mCursorControlActive = false
    private var mLastSpaceMoveX = 0
    private var mPopupMaxMoveDistance = 0f
    private var mTopSmallNumberSize = 0f
    private var mTopSmallNumberMarginWidth = 0f
    private var mTopSmallNumberMarginHeight = 0f
    private val mSpaceMoveThreshold: Int
    private var ignoreTouches = false

    private var mKeyBackground: Drawable? = null
    private var mShowKeyBorders: Boolean = false
    private var mUsingSystemTheme: Boolean = true
    private var mVoiceInputMethod: String = ""

    private var mToolbarHolder: View? = null
    private var mClipboardManagerHolder: View? = null
    private var mEmojiPaletteHolder: View? = null
    private var emojiCompatMetadataVersion = 0

    // For multi-tap
    private var mLastTapTime = 0L

    /** Whether the keyboard bitmap needs to be redrawn before it's blitted.  */
    private var mDrawPending = false

    /** The dirty region in the keyboard bitmap  */
    private val mDirtyRect = Rect()

    /** The keyboard bitmap for faster updates  */
    private var mBuffer: Bitmap? = null

    /** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer.  */
    private var mKeyboardChanged = false

    /** The canvas for the above mutable keyboard bitmap  */
    private var mCanvas: Canvas? = null

    private var mHandler: Handler? = null

    companion object {
        private const val NOT_A_KEY = -1
        private val LONG_PRESSABLE_STATE_SET = intArrayOf(R.attr.state_long_pressable)
        private const val MSG_REMOVE_PREVIEW = 1
        private const val MSG_REPEAT = 2
        private const val MSG_LONGPRESS = 3
        private const val DELAY_AFTER_PREVIEW = 100
        private const val DEBOUNCE_TIME = 70
        private const val REPEAT_INTERVAL = 50 // ~20 keys per second
        private const val REPEAT_START_DELAY = 400
        private val LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout()

        // Phase 8: mood bar — privacy slot is logically score 0 (no DB row);
        // emotion slots use their stored ordinal valence id (1..6).
        private const val MOOD_SLOT_PRIVACY = 0
        private const val MOOD_BAR_ALPHA_SELECTED = 1.0f
        // Phase 8.2: stronger dim on unselected slots so the scale-up on the
        // selected one reads as a clear hierarchy step rather than a subtle
        // alpha tweak.
        private const val MOOD_BAR_ALPHA_DIMMED = 0.45f
        // Phase 8.2: render the selected emoji 25% bigger than the others.
        // Combined with the alpha contrast this gives the "selected" state a
        // distinct visual lift without changing the layout (scaleX/scaleY
        // affects rendering only, so adjacent buttons keep their tap area).
        // Requires `clipChildren="false"` on the mood_bar parent — set in
        // keyboard_view_keyboard.xml so the scaled glyph isn't cropped.
        private const val MOOD_BAR_SCALE_SELECTED = 1.25f
        private const val MOOD_BAR_SCALE_DIMMED = 1.0f
        // Phase 8.4: peak scale at the apex of the dance pop, before the
        // wiggle. Visually distinct from the static `1.25×` highlight so the
        // select-tap reads as celebratory without breaking the bar's
        // silhouette.
        private const val MOOD_BAR_SCALE_DANCE_PEAK = 1.6f
        // Phase 8.4: dance envelope timings (~360 ms total).
        private const val MOOD_BAR_DANCE_POP_MS = 120L
        private const val MOOD_BAR_DANCE_WIGGLE_MS = 220L
        private const val MOOD_BAR_DANCE_SETTLE_MS = 20L
        // Phase 8.4: rotation keyframes (degrees) for the wiggle phase.
        private const val MOOD_BAR_DANCE_ROT_BIG = 12f
        private const val MOOD_BAR_DANCE_ROT_SMALL = 8f
        // Phase 8.4: horizontal jitter keyframes (dp) for the wiggle phase.
        private const val MOOD_BAR_DANCE_JITTER_DP = 3f
        private const val MOOD_BAR_DANCE_JITTER_SMALL_DP = 2f

        // Phase 8.5 + Phase 13: collapse / expand animation cadence. A
        // single duration is reused for the chevron rotation, the
        // indicator/slots cross-fade, AND the AutoTransition that smooths
        // the slot container's GONE↔VISIBLE layout flip — so everything
        // settles on the same frame instead of staggering visibly.
        private const val MOOD_BAR_LAYOUT_FADE_MS = 240L
        // Phase 13: a single shared interpolator for every animation in
        // the bar's open/close cycle. Material's standard "fast out, slow
        // in" curve — quick to start, gentle on landing — reads as smooth
        // even when the bar's width is interpolating simultaneously.
        private val MOOD_BAR_SMOOTH_INTERPOLATOR = AccelerateDecelerateInterpolator()
        // Phase 13: bar is right-anchored, so expansion direction is
        // LEFT. Chevron rotation targets are flipped from Phase 8.5 so
        // the icon points in the direction of motion:
        //   collapsed → `<` (rotation 180°) — "tap to expand leftward"
        //   expanded  → `>` (rotation 0°)   — "tap to collapse rightward"
        private const val MOOD_BAR_CHEVRON_ROT_EXPANDED = 0f
        private const val MOOD_BAR_CHEVRON_ROT_COLLAPSED = 180f
        // Phase 8.5: small delay between a slot tap and the auto-collapse
        // (Decision #10). Lets the user see the highlight settle on the
        // newly-tapped slot before the bar collapses around the chip.
        // Long enough that the Phase 8.4 dance (~360 ms) finishes first
        // when both phases are landed.
        private const val MOOD_BAR_AUTO_COLLAPSE_DELAY_MS = 380L
    }

    init {
        val attributes =
            context.obtainStyledAttributes(attrs, R.styleable.MyKeyboardView, 0, defStyleRes)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val keyTextSize = 0
        val indexCnt = attributes.indexCount

        try {
            for (i in 0 until indexCnt) {
                when (val attr = attributes.getIndex(i)) {
                    R.styleable.MyKeyboardView_keyTextSize -> mKeyTextSize =
                        attributes.getDimensionPixelSize(attr, 18)
                }
            }
        } finally {
            attributes.recycle()
        }

        mPopupLayout = R.layout.keyboard_popup_keyboard
        mKeyBackground = resources.getDrawable(R.drawable.keyboard_key_selector, context.theme)
        mVerticalCorrection = resources.getDimension(R.dimen.vertical_correction).toInt()
        mLabelTextSize = resources.getDimension(R.dimen.label_text_size).toInt()
        mSpaceBarTextSize = resources.getDimension(R.dimen.space_bar_text_size).toInt()
        mPreviewHeight = resources.getDimension(R.dimen.key_height).toInt()
        mSpaceMoveThreshold = resources.getDimension(R.dimen.medium_margin).toInt()

        with(safeStorageContext) {
            mTextColor = getProperTextColor()
            mBackgroundColor = getProperBackgroundColor()
            mKeyboardBackgroundColor = getKeyboardBackgroundColor()
            mPrimaryColor = getProperPrimaryColor()
            mStrokeColor = getStrokeColor()
        }

        mPreviewPopup = PopupWindow(context)
        mPreviewText = KeyboardKeyPreviewBinding.inflate(inflater).root
        mPreviewTextSizeLarge = context.resources.getDimension(R.dimen.preview_text_size).toInt()
        mPreviewPopup.contentView = mPreviewText
        mPreviewPopup.setBackgroundDrawable(null)

        mPreviewPopup.isTouchable = false
        mPopupKeyboard = PopupWindow(context)
        mPopupKeyboard.setBackgroundDrawable(null)
        mPopupParent = this
        mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.textSize = keyTextSize.toFloat()
        mPaint.textAlign = Align.CENTER
        mPaint.alpha = 255
        mMiniKeyboardCache = HashMap()
        mPopupMaxMoveDistance = resources.getDimension(R.dimen.popup_max_move_distance)
        mTopSmallNumberSize = resources.getDimension(R.dimen.small_text_size)
        mTopSmallNumberMarginWidth = resources.getDimension(R.dimen.top_small_number_margin_width)
        mTopSmallNumberMarginHeight = resources.getDimension(R.dimen.top_small_number_margin_height)
    }

    @SuppressLint("HandlerLeak")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mHandler == null) {
            mHandler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_REMOVE_PREVIEW -> mPreviewText!!.visibility = INVISIBLE
                        MSG_REPEAT -> if (repeatKey()) {
                            val repeat = Message.obtain(this, MSG_REPEAT)
                            sendMessageDelayed(repeat, REPEAT_INTERVAL.toLong())
                        }

                        MSG_LONGPRESS -> openPopupIfRequired(msg.obj as MotionEvent)
                    }
                }
            }
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        closeClipboardManager()
        closeEmojiPalette()

        if (visibility == VISIBLE) {
            setupKeyboard(changedView)
            // Phase 8.5: re-apply the persisted expand/collapse layout
            // without animating so the user never sees a frame of the
            // wrong layout on visibility change. `refreshMoodBarFromState`
            // below re-runs the staleness check + highlight derivation.
            applyMoodBarLayout(expanded = context.config.moodBarExpanded, animate = false)
            // Phase 8: re-derive the mood-bar highlight whenever the view
            // becomes visible — covers re-open into the same session id
            // with a previously-tapped emotion (e.g., after rotation).
            refreshMoodBarFromState()
        }
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the view will re-layout itself to accommodate the keyboard.
     * @param keyboard the keyboard to display in this view
     */
    fun setKeyboard(keyboard: MyKeyboard) {
        if (mKeyboard != null) {
            showPreview(NOT_A_KEY)
        }

        closeClipboardManager()
        removeMessages()
        mKeyboard = keyboard
        val keys = mKeyboard!!.mKeys
        mKeys = keys!!.toMutableList() as ArrayList<MyKeyboard.Key>
        requestLayout()
        mKeyboardChanged = true
        invalidateAllKeys()
        computeProximityThreshold(keyboard)
        mMiniKeyboardCache.clear()
        mToolbarHolder?.beInvisibleIf(context.isDeviceLocked)

        accessHelper = AccessHelper(this, mKeyboard?.mKeys.orEmpty())
        ViewCompat.setAccessibilityDelegate(this, accessHelper)

        // Not really necessary to do every time, but will free up views
        // Switching to a different keyboard should abort any pending keys so that the key up
        // doesn't get delivered to the old or new keyboard
        mAbortKey = true // Until the next ACTION_DOWN
    }

    /** Sets the top row above the keyboard containing a couple buttons and the clipboard **/
    fun setKeyboardHolder(binding: KeyboardViewKeyboardBinding) {
        keyboardViewBinding = binding.apply {
            mToolbarHolder = toolbarHolder
            mClipboardManagerHolder = clipboardManagerHolder
            mEmojiPaletteHolder = emojiPaletteHolder

            voiceInputButton.setOnLongClickListener { context.toast(R.string.switch_to_voice_typing); true }
            voiceInputButton.setOnClickListener {
                val inputMethod = context.getCurrentVoiceInputMethod()
                if (inputMethod == null) {
                    context.toast(R.string.no_app_found)
                    return@setOnClickListener
                }

                val (im, type) = inputMethod
                mOnKeyboardActionListener?.changeInputMethod(im.id, type)
            }

            settingsCog.setOnLongClickListener { context.toast(R.string.settings); true; }
            settingsCog.setOnClickListener {
                vibrateIfNeeded()
                Intent(context, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }

            // Phase 13: one-tap shortcut to the Insights dashboard from the
            // keyboard top bar. FLAG_ACTIVITY_NEW_TASK mirrors the existing
            // settings_cog launch path so the dashboard lands as its own
            // task rather than stacking on top of whichever app the user
            // is typing in.
            insightsButton.setOnLongClickListener { context.toast(R.string.dashboard_title); true }
            insightsButton.setOnClickListener {
                vibrateIfNeeded()
                Intent(context, DashboardActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }

            pinnedClipboardItems.setOnLongClickListener { context.toast(R.string.clipboard); true; }
            pinnedClipboardItems.setOnClickListener {
                vibrateIfNeeded()
                openClipboardManager()
            }

            clipboardClear.setOnLongClickListener { context.toast(R.string.clear_clipboard_data); true; }
            clipboardClear.setOnClickListener {
                vibrateIfNeeded()
                clearClipboardContent()
                toggleClipboardVisibility(false)
            }

            // Phase 8: seven-button valence-ordered emotion bar
            // (🛡️ 😊 😲 🤢 😢 😨 😠). Subsumes the Phase 2 standalone
            // privacy_toggle_button — its semantics live on the 🛡️ slot.
            setupMoodBar(this)

            suggestionsHolder.addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View?,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    updateSuggestionsToolbarLayout()
                    binding.suggestionsHolder.removeOnLayoutChangeListener(this)
                }
            })
        }

        val clipboardManager =
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        clipboardManager.addPrimaryClipChangedListener {
            val clipboardContent = clipboardManager.primaryClip?.getItemAt(0)?.text?.trim()
            if (clipboardContent?.isNotEmpty() == true) {
                handleClipboard()
            }
            setupStoredClips()
        }

        binding.apply {
            clipboardManagerClose.setOnClickListener {
                vibrateIfNeeded()
                closeClipboardManager()
            }

            clipboardManagerManage.setOnLongClickListener { context.toast(R.string.manage_clipboard_items); true; }
            clipboardManagerManage.setOnClickListener {
                Intent(context, ManageClipboardItemsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
            }

            emojiPaletteClose.setOnClickListener {
                vibrateIfNeeded()
                closeEmojiPalette()
            }
        }
    }

    fun setEditorInfo(editorInfo: EditorInfo) {
        emojiCompatMetadataVersion =
            editorInfo.extras?.getInt(EmojiCompat.EDITOR_INFO_METAVERSION_KEY, 0) ?: 0
    }

    fun setupKeyboard(changedView: View? = null) {
        with(safeStorageContext) {
            mTextColor = getProperTextColor()
            mBackgroundColor = getProperBackgroundColor()
            mKeyboardBackgroundColor = getKeyboardBackgroundColor()
            mPrimaryColor = getProperPrimaryColor()
            mStrokeColor = getStrokeColor()

            mShowKeyBorders = config.showKeyBorders
            mUsingSystemTheme = isDynamicTheme()
            mVoiceInputMethod = config.voiceInputMethod
        }

        val isMainKeyboard = changedView == null || changedView.id != R.id.mini_keyboard_view
        mKeyColor = getKeyColor()
        mKeyColorPressed = mKeyColor.adjustAlpha(0.2f)
        mKeyBackground = if (mShowKeyBorders && isMainKeyboard) {
            resources.getDrawable(R.drawable.keyboard_key_selector_outlined, context.theme)
        } else {
            resources.getDrawable(R.drawable.keyboard_key_selector, context.theme)
        }

        if (!isMainKeyboard) {
            val previewBackground = background as LayerDrawable
            previewBackground.findDrawableByLayerId(R.id.button_background_shape)
                .applyColorFilter(mBackgroundColor)
            previewBackground.findDrawableByLayerId(R.id.button_background_stroke)
                .applyColorFilter(mStrokeColor)
            background = previewBackground
        } else {
            background.applyColorFilter(mKeyboardBackgroundColor)
        }

        val wasDarkened = mBackgroundColor != mBackgroundColor.darkenColor()
        keyboardViewBinding?.apply {
            topKeyboardDivider.beGoneIf(wasDarkened)
            topKeyboardDivider.background = ColorDrawable(mStrokeColor)
            mToolbarHolder?.background = ColorDrawable(mKeyboardBackgroundColor)

            clipboardValue.apply {
                background =
                    resources.getDrawable(R.drawable.clipboard_background, context.theme).apply {
                        val layerDrawable = (this as RippleDrawable)
                            .findDrawableByLayerId(R.id.clipboard_background_holder) as LayerDrawable
                        layerDrawable.findDrawableByLayerId(R.id.clipboard_background_stroke)
                            .applyColorFilter(mStrokeColor)
                        layerDrawable.findDrawableByLayerId(R.id.clipboard_background_shape)
                            .applyColorFilter(mBackgroundColor)
                    }

                setTextColor(mTextColor)
                setLinkTextColor(mTextColor)
            }

            settingsCog.applyColorFilter(mTextColor)
            insightsButton.applyColorFilter(mTextColor)
            pinnedClipboardItems.applyColorFilter(mTextColor)
            clipboardClear.applyColorFilter(mTextColor)
            // Phase 8: mood bar buttons are TextViews with emoji glyphs;
            // applying a color filter on them would tint the emoji.
            // Highlighted-vs-dim styling is handled per-slot via alpha
            // and background drawable in `applyMoodBarHighlight()`.
            applyMoodBarTint()
            // Phase 8.5: tint the collapse/expand chevron with the
            // keyboard's text colour. The vector's `?attr/colorControlNormal`
            // resolves against the IME context, which on some Fossify
            // themes lands on a colour that's invisible against the
            // toolbar background (user-reported "I don't see < >").
            moodBarToggleChevron.applyColorFilter(mTextColor)
            voiceInputButton.applyColorFilter(mTextColor)
            voiceInputButton.beGoneIf(mVoiceInputMethod.isEmpty())

            mToolbarHolder?.beInvisibleIf(context.isDeviceLocked)

            topClipboardDivider.beGoneIf(wasDarkened)
            topClipboardDivider.background = ColorDrawable(mStrokeColor)
            clipboardManagerTopBar.background = ColorDrawable(mKeyboardBackgroundColor)
            clipboardManagerHolder.background = ColorDrawable(mBackgroundColor)

            clipboardManagerClose.applyColorFilter(mTextColor)
            clipboardManagerManage.applyColorFilter(mTextColor)

            clipboardManagerLabel.setTextColor(mTextColor)
            clipboardContentPlaceholder1.setTextColor(mTextColor)
            clipboardContentPlaceholder2.setTextColor(mTextColor)
        }

        setupEmojiPalette(
            toolbarColor = mKeyboardBackgroundColor,
            backgroundColor = mBackgroundColor,
            textColor = mTextColor
        )
        if (context.config.keyboardLanguage == LANGUAGE_VIETNAMESE_TELEX) {
            setupLanguageTelex()
        } else {
            cachedVNTelexData.clear()
        }
        setupStoredClips()
        // Phase 8.2: re-assert mood-bar visibility — `voiceInputButton.beGoneIf`
        // above would otherwise un-hide the voice button when the mood bar is
        // meant to own the toolbar.
        applyMoodBarVisibility()
    }

    /**
     * Phase 8: bind click handlers and the initial highlighted slot for the
     * seven-button mood bar. Called once from `setKeyboardHolder` after the
     * top-bar binding is materialised; the bar is always visible.
     *
     * Click handlers fire `IkdMoodBarController` on `Dispatchers.IO` and
     * marshal the bar's highlighted slot back to the main thread via the
     * `moodScope` (`Dispatchers.Main.immediate`). UI state is *optimistic*:
     * we move the highlight before the DB write completes so the user gets
     * instant feedback. If the write fails, the highlight is corrected on
     * the next refresh (`refreshMoodBarFromState`).
     */
    private fun setupMoodBar(binding: KeyboardViewKeyboardBinding) {
        binding.apply {
            moodBarPrivacy.setOnLongClickListener {
                context.toast(R.string.privacy_toggle_content_description); true
            }
            moodBarPrivacy.setOnClickListener { view ->
                vibrateIfNeeded()
                onMoodSlotClicked(MOOD_SLOT_PRIVACY, view)
            }

            // Six emotion slots (Ekman 6, valence-ordered). Each click
            // writes/replaces the in-flight session's `MoodEntry` and
            // disables privacy mode if it was on.
            val emotionSlots = listOf(
                moodBarHappiness to MoodEmoji.SCORE_HAPPINESS,
                moodBarSurprise to MoodEmoji.SCORE_SURPRISE,
                moodBarDisgust to MoodEmoji.SCORE_DISGUST,
                moodBarSadness to MoodEmoji.SCORE_SADNESS,
                moodBarFear to MoodEmoji.SCORE_FEAR,
                moodBarAnger to MoodEmoji.SCORE_ANGER,
            )
            emotionSlots.forEach { (view, score) ->
                val labelRes = MoodEmoji.labelResFor(score)
                view.setOnLongClickListener { context.toast(labelRes); true }
                view.setOnClickListener { tappedView ->
                    vibrateIfNeeded()
                    onMoodSlotClicked(score, tappedView)
                }
            }

            // Phase 8.5: collapsed indicator + chevron both toggle the bar.
            // Tapping the indicator is the discoverable expansion path
            // (chip itself is the tap target — Decision #10's
            // "discoverability" line); tapping the chevron is the
            // explicit toggle path. Both vibrate (consistent with the rest
            // of the keyboard top-bar buttons) and route through the same
            // helper so the state machine has a single source of truth.
            moodBarCollapsedIndicator.setOnLongClickListener {
                context.toast(R.string.mood_bar_toggle_content_description); true
            }
            moodBarCollapsedIndicator.setOnClickListener {
                vibrateIfNeeded()
                toggleMoodBarExpanded()
            }
            moodBarToggleChevron.setOnLongClickListener {
                context.toast(R.string.mood_bar_toggle_content_description); true
            }
            moodBarToggleChevron.setOnClickListener {
                vibrateIfNeeded()
                toggleMoodBarExpanded()
            }
        }
        // Phase 8.5: pre-flip the bar to its persisted state without
        // animating so the user never sees a frame of the wrong layout.
        applyMoodBarLayout(expanded = context.config.moodBarExpanded, animate = false)
        // Initial state: 🛡️ highlighted iff privacy is on, no row otherwise.
        refreshMoodBarFromState()
    }

    /**
     * Phase 8.5: flip `Config.moodBarExpanded` and run the layout animation.
     * Cancels any pending auto-collapse before flipping — a manual chevron
     * tap during the auto-collapse delay should be honoured immediately.
     */
    private fun toggleMoodBarExpanded() {
        mMoodCollapseHandler.removeCallbacks(mMoodAutoCollapseRunnable)
        val nextExpanded = !context.config.moodBarExpanded
        context.config.moodBarExpanded = nextExpanded
        applyMoodBarLayout(expanded = nextExpanded, animate = true)
    }

    /**
     * Click dispatcher for the mood bar. `slot` is `MOOD_SLOT_PRIVACY` for
     * the 🛡️ button or 1..6 for the six emotion buttons (matching the
     * stored ordinal valence id). `anchor` is the tapped slot view — used
     * to position the chat-bubble popup directly above it.
     *
     * Phase 8.2: every slot is now a toggle. Tapping a slot that's already
     * highlighted clears it — 🛡️ flips privacy off (capture on, no mood),
     * an emotion deletes its `MoodEntry` row (capture on, no rating). The
     * deselect path is silent: it only updates the highlight and dismisses
     * any in-flight bubble (the highlight change is the feedback). The
     * select path still surfaces a first-person chat bubble unless the user
     * has disabled it via `Config.showMoodPopup`.
     */
    private fun onMoodSlotClicked(slot: Int, anchor: View) {
        val alreadySelected = highlightedMoodSlot == slot
        when (slot) {
            MOOD_SLOT_PRIVACY -> {
                if (alreadySelected) {
                    applyMoodBarHighlight(null)
                    dismissMoodBubble()
                    moodScope.launch { moodController.disablePrivacy() }
                } else {
                    applyMoodBarHighlight(MOOD_SLOT_PRIVACY)
                    // Owner directive: no on-keyboard popup/notification
                    // on mood switch. The highlight change + dance are
                    // the only feedback; the chat bubble is suppressed.
                    dismissMoodBubble()
                    animateMoodSlotDance(anchor, MOOD_BAR_SCALE_SELECTED)
                    moodScope.launch { moodController.enablePrivacyAndClearMood() }
                }
            }
            in MoodEmoji.SCORE_HAPPINESS..MoodEmoji.SCORE_ANGER -> {
                if (alreadySelected) {
                    applyMoodBarHighlight(null)
                    dismissMoodBubble()
                    moodScope.launch { moodController.clearMoodForActiveSession() }
                } else {
                    applyMoodBarHighlight(slot)
                    // Owner directive: no on-keyboard popup/notification
                    // on mood switch. The highlight change + dance are
                    // the only feedback; the chat bubble is suppressed.
                    dismissMoodBubble()
                    animateMoodSlotDance(anchor, MOOD_BAR_SCALE_SELECTED)
                    moodScope.launch { moodController.setMoodForActiveSession(slot) }
                }
            }
        }
        // Phase 8.5: auto-collapse after every slot tap (Decision #10) so
        // the user sees their selection echoed in the chip without an
        // extra chevron tap. Re-collapse for deselect taps too — the
        // chip switches to placeholder 🙂, which is itself the feedback.
        // The delay lets Phase 8.4's dance (~360 ms) play out before the
        // bar collapses; if 8.4 is not landed yet, the small delay is
        // imperceptible but still gives the highlight time to settle.
        scheduleAutoCollapse()
    }

    /**
     * Phase 8.5: schedule a deferred collapse after a slot tap. Cancels
     * any prior pending collapse so a rapid second tap restarts the timer
     * rather than firing a stale collapse mid-dance.
     */
    private fun scheduleAutoCollapse() {
        if (!context.config.moodBarExpanded) return
        mMoodCollapseHandler.removeCallbacks(mMoodAutoCollapseRunnable)
        mMoodCollapseHandler.postDelayed(mMoodAutoCollapseRunnable, MOOD_BAR_AUTO_COLLAPSE_DELAY_MS)
    }

    /**
     * Owner directive (post-Phase 8.4): the on-keyboard chat-bubble popup
     * is no longer shown on a mood switch — the highlight change + the
     * Phase 8.4 dance are the only feedback. The bubble teardown helper
     * below is kept so any popup left over from an older build (or a
     * deselect tap) is still cleaned up safely; the `Config.showMoodPopup`
     * pref is now inert for the keyboard bar.
     */
    private fun dismissMoodBubble() {
        mMoodBubbleHandler.removeCallbacks(mMoodBubbleDismissRunnable)
        mMoodBubblePopup?.dismiss()
    }

    /**
     * Re-derive the mood bar's highlighted slot from `Config.privacyModeEnabled`
     * + the in-flight session's `MoodEntry`. Called on `setKeyboardHolder`,
     * `setupKeyboard` (after a theme refresh), and `onStartInputView` (via
     * the IME's existing setup hooks).
     *
     * Defensive read: under spec a fresh session has no mood row until the
     * user taps. The query is still issued so a re-open into the same
     * session id (which can happen on configuration change) restores the
     * previously-tapped emotion if there is one.
     */
    fun refreshMoodBarFromState() {
        applyMoodBarVisibility()
        // Phase 8.5: secondary on-show staleness check — covers the
        // long-attached-IME edge case where `onStartInputView` doesn't
        // re-fire between sessions. Bounded read + arithmetic + at-most-one
        // Config write, no DAO touch (Decision #9 is "clear the standing
        // rating only; do not flip privacy" — `applyMoodBarHighlight`
        // below honours whichever state remains).
        maybeResetStaleStandingMood()
        if (context.config.privacyModeEnabled) {
            applyMoodBarHighlight(MOOD_SLOT_PRIVACY)
            return
        }
        // Privacy is off — start with the standing rating from Config (so
        // a re-entry into the keyboard pre-highlights the chip), then ask
        // the DB if an explicit `mood_entries` row exists for the in-flight
        // session. The DB row always wins ("row exists" path beats the
        // standing rating; see §3 of `Phase8.5_Plan.md`).
        val standing = context.config.lastMoodScore
        if (MoodEmoji.isStandingScore(standing)) {
            applyMoodBarHighlight(standing)
        } else {
            applyMoodBarHighlight(null)
        }
        moodScope.launch {
            val mood = moodController.getMoodForActiveSession()
            val score = mood?.moodScore
            if (score != null && MoodEmoji.isValidScore(score)) {
                applyMoodBarHighlight(score)
            }
        }
    }

    /**
     * Phase 8.5: secondary inactivity check. Mirrors the IME's
     * `maybeResetStaleMood` so that a refresh triggered by something other
     * than `onStartInputView` (theme change, settings toggle, visibility
     * change) still expires a stale standing rating. No-op when no
     * standing rating exists.
     */
    private fun maybeResetStaleStandingMood() {
        val cfg = context.config
        if (!MoodEmoji.isStandingScore(cfg.lastMoodScore)) return
        val last = cfg.lastMoodActivityTimestamp
        if (last <= 0L) return
        if (System.currentTimeMillis() - last > MOOD_INACTIVITY_TIMEOUT_MS) {
            cfg.lastMoodScore = MoodEmoji.SCORE_NONE
        }
    }

    /**
     * Phase 8 follow-up + Phase 13: gate mood-bar visibility on
     * `Config.showMoodBar`. When false, the bar is `View.GONE`. When true,
     * the bar lives on the trailing edge in an elevated sibling overlay
     * above `toolbar_holder` and `emoji_palette_holder`, and the expanded
     * slot row is allowed to **float over** the right-side toolbar icons
     * (voice / pinned / settings). None of those icons move or hide when
     * the bar expands — the elevation does the visual lifting.
     */
    private fun applyMoodBarVisibility() {
        val binding = keyboardViewBinding ?: return
        val visible = context.config.showMoodBar
        binding.moodBar.visibility = if (visible) View.VISIBLE else View.GONE
        // Phase 13: the bar is an elevated overlay anchored to the trailing
        // edge. The expanded slot row is free to float over `pinned_clipboard`
        // / `voice_input_button` / `settings_cog` — none of those need to
        // move or hide. Suggestions and clipboard_clear stay on the leading
        // edge and are never touched.
        binding.clipboardClear.visibility = View.VISIBLE
        binding.suggestionsHolder.visibility = View.VISIBLE
        binding.voiceInputButton.beGoneIf(mVoiceInputMethod.isEmpty())
        binding.pinnedClipboardItems.visibility = View.VISIBLE
    }

    /**
     * Phase 8.5: flip the bar between collapsed (single-slot chip) and
     * expanded (seven slots + chevron). Mirrors Phase 6's
     * `applySensorExpansion` discipline: takes an `animate` flag so
     * `onVisibilityChanged(VISIBLE)` and `setupMoodBar` can pre-flip the
     * layout to its persisted state without the cross-fade flickering
     * on first paint.
     *
     * Cancels any in-flight slot animations + auto-collapse callbacks
     * before flipping so the cross-fade always starts from a clean state.
     * The chevron rotation, indicator/slots cross-fade, and (the rare
     * case where the user mid-dance flips the bar) the dance animator
     * all compose: `View.animate().cancel()` cancels the chained call,
     * and the dance's `AnimatorSet` is cancelled separately.
     */
    private fun applyMoodBarLayout(expanded: Boolean, animate: Boolean) {
        val binding = keyboardViewBinding ?: return
        val indicator = binding.moodBarCollapsedIndicator
        val slots = binding.moodBarExpandedSlots
        val chevron = binding.moodBarToggleChevron
        cancelMoodLayoutAnimators()
        val rotTarget = if (expanded) MOOD_BAR_CHEVRON_ROT_EXPANDED else MOOD_BAR_CHEVRON_ROT_COLLAPSED
        if (!animate) {
            chevron.rotation = rotTarget
            if (expanded) {
                indicator.alpha = 0f
                indicator.visibility = View.GONE
            } else {
                // Re-derive the indicator's intended emoji + alpha from
                // the current highlight before showing it. Skipping this
                // would leave the placeholder 🙂 at α=1.0 (looks selected).
                applyMoodBarCollapsedIndicator(highlightedMoodSlot)
                indicator.visibility = View.VISIBLE
            }
            slots.alpha = if (expanded) 1f else 0f
            slots.visibility = if (expanded) View.VISIBLE else View.GONE
            return
        }
        // Phase 13: ask the framework to smoothly animate the layout
        // change that's about to happen (slots flipping GONE↔VISIBLE,
        // which would otherwise snap the bar's width instantly). The
        // AutoTransition's default ChangeBounds + Fade gives us a smooth
        // width interpolation tied to the same duration as our explicit
        // chevron + alpha animations, so everything settles together.
        TransitionManager.beginDelayedTransition(
            binding.moodBar,
            AutoTransition().apply {
                duration = MOOD_BAR_LAYOUT_FADE_MS
                interpolator = MOOD_BAR_SMOOTH_INTERPOLATOR
            },
        )
        chevron.animate()
            .rotation(rotTarget)
            .setDuration(MOOD_BAR_LAYOUT_FADE_MS)
            .setInterpolator(MOOD_BAR_SMOOTH_INTERPOLATOR)
            .start()
        if (expanded) {
            // Hide the collapsed chip; cross-fade the expanded slots in.
            indicator.animate()
                .alpha(0f)
                .setDuration(MOOD_BAR_LAYOUT_FADE_MS)
                .setInterpolator(MOOD_BAR_SMOOTH_INTERPOLATOR)
                .withEndAction { indicator.visibility = View.GONE }
                .start()
            slots.alpha = 0f
            slots.visibility = View.VISIBLE
            slots.animate()
                .alpha(1f)
                .setDuration(MOOD_BAR_LAYOUT_FADE_MS)
                .setInterpolator(MOOD_BAR_SMOOTH_INTERPOLATOR)
                .start()
        } else {
            slots.animate()
                .alpha(0f)
                .setDuration(MOOD_BAR_LAYOUT_FADE_MS)
                .setInterpolator(MOOD_BAR_SMOOTH_INTERPOLATOR)
                .withEndAction { slots.visibility = View.GONE }
                .start()
            // Hotfix: re-derive the indicator's intended alpha so the
            // dim placeholder (🙂 at α=0.45) doesn't get cross-faded to
            // α=1.0 — that bug made the deselect path read identically
            // to the select path because the placeholder ended up at full
            // alpha after a deselect-then-auto-collapse.
            applyMoodBarCollapsedIndicator(highlightedMoodSlot)
            val targetAlpha = indicator.alpha
            indicator.alpha = 0f
            indicator.visibility = View.VISIBLE
            indicator.animate()
                .alpha(targetAlpha)
                .setDuration(MOOD_BAR_LAYOUT_FADE_MS)
                .setInterpolator(MOOD_BAR_SMOOTH_INTERPOLATOR)
                .start()
        }
    }

    /**
     * Phase 8.5: cancel only the layout cross-fade animators (collapsed
     * indicator + slots container + chevron rotation). The seven per-slot
     * highlight animations are deliberately NOT cancelled here — they
     * carry the "which slot is selected" feedback and cancelling them
     * mid-flight from a layout change (e.g. tapping the chip during the
     * highlight settle) would freeze a slot at scaleX≈1.05, alpha≈0.5,
     * making the selected mood look unselected on re-expand. Per-slot
     * cancel-before-restart already lives inside `applyMoodBarHighlight`,
     * and the Phase 8.4 dance owns its own cancellation graph.
     */
    private fun cancelMoodLayoutAnimators() {
        val binding = keyboardViewBinding ?: return
        binding.moodBarCollapsedIndicator.animate().cancel()
        binding.moodBarExpandedSlots.animate().cancel()
        binding.moodBarToggleChevron.animate().cancel()
    }

    /**
     * Phase 8.5: full cleanup for `onDetachedFromWindow`. Cancels the
     * layout cross-fade animators AND every per-slot highlight animation
     * so a leaked frame callback can't keep the view alive past detach.
     */
    private fun cancelAllMoodAnimators() {
        cancelMoodLayoutAnimators()
        val binding = keyboardViewBinding ?: return
        listOf(
            binding.moodBarPrivacy,
            binding.moodBarHappiness,
            binding.moodBarSurprise,
            binding.moodBarDisgust,
            binding.moodBarSadness,
            binding.moodBarFear,
            binding.moodBarAnger,
        ).forEach { it.animate().cancel() }
    }

    /**
     * Phase 8.5: recompute the collapsed-indicator emoji + alpha from the
     * current state. Re-runs whenever `applyMoodBarHighlight` is called
     * (i.e., every state transition) so the chip stays in sync with the
     * expanded bar's highlighted slot.
     *
     * Display table (matches §3 of `Phase8.5_Plan.md`):
     *   privacy on                          → 🛡️ at α=1.0
     *   privacy off, no highlight (no rating) → 🙂 at α=0.45 (placeholder)
     *   privacy off, highlight = score 1..6 → emoji at α=1.0
     */
    private fun applyMoodBarCollapsedIndicator(slot: Int?) {
        val binding = keyboardViewBinding ?: return
        val indicator = binding.moodBarCollapsedIndicator
        when {
            slot == MOOD_SLOT_PRIVACY -> {
                indicator.text = resources.getString(R.string.mood_bar_privacy_emoji)
                indicator.alpha = MOOD_BAR_ALPHA_SELECTED
            }
            slot != null && MoodEmoji.isValidScore(slot) -> {
                indicator.text = MoodEmoji.emojiFor(slot)
                indicator.alpha = MOOD_BAR_ALPHA_SELECTED
            }
            else -> {
                indicator.text = resources.getString(R.string.mood_bar_collapsed_placeholder)
                indicator.alpha = MOOD_BAR_ALPHA_DIMMED
            }
        }
    }

    /**
     * Set the highlighted slot. `null` means no slot is highlighted (a valid
     * "privacy off, user has not yet tapped" state — Decision #7).
     */
    private fun applyMoodBarHighlight(slot: Int?) {
        highlightedMoodSlot = slot
        val binding = keyboardViewBinding ?: return
        val all = listOf(
            binding.moodBarPrivacy to MOOD_SLOT_PRIVACY,
            binding.moodBarHappiness to MoodEmoji.SCORE_HAPPINESS,
            binding.moodBarSurprise to MoodEmoji.SCORE_SURPRISE,
            binding.moodBarDisgust to MoodEmoji.SCORE_DISGUST,
            binding.moodBarSadness to MoodEmoji.SCORE_SADNESS,
            binding.moodBarFear to MoodEmoji.SCORE_FEAR,
            binding.moodBarAnger to MoodEmoji.SCORE_ANGER,
        )
        all.forEach { (view, idx) ->
            val isSelected = slot != null && slot == idx
            val targetAlpha = if (isSelected) MOOD_BAR_ALPHA_SELECTED else MOOD_BAR_ALPHA_DIMMED
            val targetScale = if (isSelected) MOOD_BAR_SCALE_SELECTED else MOOD_BAR_SCALE_DIMMED
            // Phase 8.5 hotfix: SNAP to target values directly (no animation).
            // The animated approach was unreliable because slot animations
            // started while the container was GONE didn't always render —
            // the user-reported "selected mood not brighter than others on
            // open" is the symptom. The Phase-8.4 dance still provides
            // tactile feedback for the just-tapped slot; non-tapped slots
            // don't need a transition since they cross-fade with the
            // container alpha animation.
            view.animate().cancel()
            view.alpha = targetAlpha
            view.scaleX = targetScale
            view.scaleY = targetScale
        }
        // Phase 8.5: keep the collapsed-state chip in sync with the
        // expanded bar's highlighted slot.
        applyMoodBarCollapsedIndicator(slot)
    }

    /**
     * Phase 8.4: short celebratory dance played on a mood-bar slot when the
     * user *selects* it (privacy or one of the six emotions). Three-phase
     * envelope (~360 ms): pop to `MOOD_BAR_SCALE_DANCE_PEAK`, wiggle
     * (rotation + horizontal jitter in parallel), then settle back to
     * `settleScale`. Deselect taps don't dance — see `onMoodSlotClicked`.
     *
     * The animator is held in `currentMoodDanceAnimator` so a second tap can
     * cancel a still-running dance. On end (or cancel) we defensively snap
     * the view back to `rotation = 0`, `translationX = 0`, `scale = settleScale`
     * so a cancelled animation cannot leave the slot stuck mid-wiggle.
     */
    private fun animateMoodSlotDance(slot: View, settleScale: Float) {
        slot.animate().cancel()
        currentMoodDanceAnimator?.cancel()

        val pop = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(slot, "scaleX", settleScale, MOOD_BAR_SCALE_DANCE_PEAK),
                ObjectAnimator.ofFloat(slot, "scaleY", settleScale, MOOD_BAR_SCALE_DANCE_PEAK),
            )
            duration = MOOD_BAR_DANCE_POP_MS
            interpolator = AccelerateDecelerateInterpolator()
        }

        val density = resources.displayMetrics.density
        val jitterPx = MOOD_BAR_DANCE_JITTER_DP * density
        val smallJitterPx = MOOD_BAR_DANCE_JITTER_SMALL_DP * density
        val rotBig = MOOD_BAR_DANCE_ROT_BIG
        val rotSmall = MOOD_BAR_DANCE_ROT_SMALL
        val rotationAnim = ObjectAnimator.ofFloat(
            slot, "rotation", 0f, -rotBig, rotBig, -rotSmall, rotSmall, 0f
        )
        val translateAnim = ObjectAnimator.ofFloat(
            slot, "translationX", 0f, -jitterPx, jitterPx, -smallJitterPx, smallJitterPx, 0f
        )
        val wiggle = AnimatorSet().apply {
            playTogether(rotationAnim, translateAnim)
            duration = MOOD_BAR_DANCE_WIGGLE_MS
        }

        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(slot, "scaleX", MOOD_BAR_SCALE_DANCE_PEAK, settleScale),
                ObjectAnimator.ofFloat(slot, "scaleY", MOOD_BAR_SCALE_DANCE_PEAK, settleScale),
            )
            duration = MOOD_BAR_DANCE_SETTLE_MS
            interpolator = AccelerateDecelerateInterpolator()
        }

        val dance = AnimatorSet().apply {
            playSequentially(pop, wiggle, settle)
            doOnEnd {
                slot.rotation = 0f
                slot.translationX = 0f
                slot.scaleX = settleScale
                slot.scaleY = settleScale
                if (currentMoodDanceAnimator === this) {
                    currentMoodDanceAnimator = null
                }
            }
        }
        currentMoodDanceAnimator = dance
        dance.start()
    }

    /**
     * Refresh the mood bar's stretched-key background after a theme change.
     * The bar's drawable is a layer-list (fill + stroke); each layer is
     * colour-filtered with the same palette the keys themselves use —
     * `mKeyColor` for fill so the bar reads as a "big key", `mStrokeColor`
     * for the border. The emoji glyphs themselves render in the system
     * emoji font and must NOT be tinted (a color filter on a TextView's
     * text would clobber the emoji palette); per-slot dim/highlight is
     * handled by `applyMoodBarHighlight()`.
     */
    private fun applyMoodBarTint() {
        val binding = keyboardViewBinding ?: return
        val drawable = binding.moodBar.background as? LayerDrawable ?: return
        drawable.findDrawableByLayerId(R.id.mood_bar_background_shape)
            ?.applyColorFilter(mKeyColor)
        drawable.findDrawableByLayerId(R.id.mood_bar_background_stroke)
            ?.applyColorFilter(mStrokeColor)
    }

    fun vibrateIfNeeded() {
        feedbackManager.vibrateIfNeeded(this)
    }

    fun performKeypressFeedback(keyCode: Int) {
        feedbackManager.performKeypressFeedback(this, keyCode)
    }

    fun performHapticHandleMove() {
        feedbackManager.performHapticHandleMove(this)
    }

    /**
     * Sets the state of the shift key of the keyboard, if any.
     * @param shifted whether or not to enable the state of the shift key
     * @return true if the shift key state changed, false if there was no change
     */
    private fun setShifted(shiftState: ShiftState) {
        if (mKeyboard?.setShifted(shiftState) == true) {
            invalidateAllKeys()
        }
    }

    /**
     * Returns the state of the shift key of the keyboard, if any.
     * @return true if the shift is in a pressed state, false otherwise
     */
    private fun isShifted(): Boolean {
        return (mKeyboard?.mShiftState ?: ShiftState.OFF) > ShiftState.OFF
    }

    private fun setPopupOffset(x: Int, y: Int) {
        mMiniKeyboardOffsetX = x
        mMiniKeyboardOffsetY = y
        if (mPreviewPopup.isShowing) {
            mPreviewPopup.dismiss()
        }
    }

    private fun adjustCase(label: CharSequence): CharSequence? {
        var newLabel: CharSequence? = label
        if (
            !newLabel.isNullOrEmpty()
            && mKeyboard!!.mShiftState != ShiftState.OFF
            && newLabel.length < 3
            && Character.isLowerCase(newLabel[0])
        ) {
            if (context.config.keyboardLanguage == LANGUAGE_TURKISH_Q) {
                newLabel = newLabel.toString().uppercase(Locale.forLanguageTag("tr"))
            } else {
                newLabel = newLabel.toString().uppercase(Locale.getDefault())
            }
        }
        return newLabel
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mKeyboard == null) {
            setMeasuredDimension(0, 0)
        } else {
            var width = mKeyboard!!.mMinWidth
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec)
            }
            setMeasuredDimension(width, mKeyboard!!.mHeight)
        }
    }

    /**
     * Compute the average distance between adjacent keys (horizontally and vertically) and square it to get the proximity threshold. We use a square here and
     * in computing the touch distance from a key's center to avoid taking a square root.
     * @param keyboard
     */
    private fun computeProximityThreshold(keyboard: MyKeyboard?) {
        if (keyboard == null) {
            return
        }

        val keys = mKeys
        val length = keys.size
        var dimensionSum = 0
        for (i in 0 until length) {
            val key = keys[i]
            dimensionSum += Math.min(key.width, key.height) + key.gap
        }

        if (dimensionSum < 0 || length == 0) {
            return
        }

        mProximityThreshold = (dimensionSum * 1.4f / length).toInt()
        mProximityThreshold *= mProximityThreshold // Square it
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mDrawPending || mBuffer == null || mKeyboardChanged) {
            onBufferDraw()
        }
        canvas.drawBitmap(mBuffer!!, 0f, 0f, null)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun onBufferDraw() {
        if (mBuffer == null || mKeyboardChanged) {
            if (mBuffer == null || mKeyboardChanged && (mBuffer!!.width != width || mBuffer!!.height != height)) {
                // Make sure our bitmap is at least 1x1
                val width = Math.max(1, width)
                val height = Math.max(1, height)
                mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                mCanvas = Canvas(mBuffer!!)
            }
            invalidateAllKeys()
            mKeyboardChanged = false
        }

        if (mKeyboard == null) {
            return
        }

        mCanvas!!.save()
        val canvas = mCanvas
        canvas!!.clipRect(mDirtyRect)
        val paint = mPaint
        val keys = mKeys
        paint.color = mTextColor
        val customTypeface = FontHelper.getTypeface(context)
        val smallLetterPaint = Paint().apply {
            set(paint)
            color = paint.color.adjustAlpha(0.8f)
            textSize = mTopSmallNumberSize
            typeface = customTypeface
        }

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        handleClipboard()

        val keyCount = keys.size
        for (i in 0 until keyCount) {
            val key = keys[i]
            val code = key.code
            val label = adjustCase(key.label)?.toString()

            setupKeyBackground(key, code, canvas)
            val textColor = when {
                key.pressed -> mTextColor.adjustAlpha(0.5f)
                code == KEYCODE_SPACE && label.orEmpty().length > 1 -> mTextColor.adjustAlpha(HIGHER_ALPHA)
                else -> mTextColor
            }

            // Switch the character to uppercase if shift is pressed
            if (label?.isNotEmpty() == true) {
                // For characters, use large font. For labels like "Done", use small font.
                if (code == KEYCODE_SPACE && key.label.length > 1) {
                    // Use smaller font size for current language label on space bar
                    paint.textSize = mSpaceBarTextSize.toFloat()
                    paint.typeface = customTypeface
                } else if (label.length > 1) {
                    paint.textSize = mLabelTextSize.toFloat()
                    paint.typeface = Typeface.create(customTypeface, Typeface.BOLD)
                } else {
                    paint.textSize = mKeyTextSize.toFloat()
                    paint.typeface = customTypeface
                }

                paint.color = textColor

                val rows = label.split("\n")
                val textSize = paint.textSize
                val startY =
                    (key.height / 2f) + ((textSize - paint.descent()) / 2f) - ((textSize / 2f) * (rows.size - 1))
                rows.forEachIndexed { index, row ->
                    canvas.drawText(row, key.width / 2f, startY + textSize * index, paint)
                }

                if (
                    key.topSmallNumber.isNotEmpty()
                    && !(context.config.showNumbersRow && Regex("\\d").matches(key.topSmallNumber))
                ) {
                    val bounds = Rect().also {
                        smallLetterPaint.getTextBounds(
                            key.topSmallNumber,
                            0,
                            key.topSmallNumber.length,
                            it
                        )
                    }

                    smallLetterPaint.color = if (key.pressed) {
                        textColor
                    } else {
                        smallLetterPaint.color
                    }

                    canvas.drawText(
                        key.topSmallNumber,
                        key.width - bounds.width() / 2f - mTopSmallNumberMarginWidth,
                        key.y + mTopSmallNumberSize + mTopSmallNumberMarginHeight,
                        smallLetterPaint
                    )
                }

                // Draw secondary icons for label-based keys
                drawSecondaryIcon(key, canvas, textColor)

                // Turn off drop shadow
                paint.setShadowLayer(0f, 0f, 0f, 0)
            } else if (key.icon != null && mKeyboard != null) {
                if (code == KEYCODE_SHIFT) {
                    val drawableId = when (mKeyboard!!.mShiftState) {
                        ShiftState.OFF -> R.drawable.ic_caps_outline_vector
                        ShiftState.ON_ONE_CHAR -> R.drawable.ic_caps_vector
                        else -> R.drawable.ic_caps_underlined_vector
                    }
                    key.icon = resources.getDrawable(drawableId)
                }

                if (code == KEYCODE_ENTER) {
                    val contrastColor = mPrimaryColor.getContrastColor()
                    key.icon!!.applyColorFilter(contrastColor)
                    key.secondaryIcon?.applyColorFilter(contrastColor.adjustAlpha(0.6f))
                } else if (
                    code in arrayOf(
                        KEYCODE_DELETE,
                        KEYCODE_SHIFT,
                        KEYCODE_EMOJI_OR_LANGUAGE,
                        KEYCODE_POPUP_EMOJI,
                        KEYCODE_POPUP_SETTINGS
                    )
                ) {
                    key.icon!!.applyColorFilter(textColor)
                    key.secondaryIcon?.applyColorFilter(
                        if (key.pressed) {
                            textColor
                        } else {
                            mTextColor.adjustAlpha(0.6f)
                        }
                    )
                }

                val keyIcon = key.icon!!
                val secondaryIcon = key.secondaryIcon
                if (secondaryIcon != null) {
                    // When secondary icon exists, shrink main icon to 90%
                    val keyIconWidth = (keyIcon.intrinsicWidth * 0.9f).toInt()
                    val keyIconHeight = (keyIcon.intrinsicHeight * 0.9f).toInt()

                    val centerX = key.width / 2
                    val centerY = key.height / 2

                    val keyIconLeft = centerX - keyIconWidth / 2
                    val keyIconTop = centerY - keyIconHeight / 2

                    keyIcon.setBounds(
                        keyIconLeft,
                        keyIconTop,
                        keyIconLeft + keyIconWidth,
                        keyIconTop + keyIconHeight
                    )
                    keyIcon.draw(canvas)

                    drawSecondaryIcon(key, canvas, textColor)
                } else {
                    val drawableX = (key.width - keyIcon.intrinsicWidth) / 2
                    val drawableY = (key.height - keyIcon.intrinsicHeight) / 2
                    canvas.translate(drawableX.toFloat(), drawableY.toFloat())
                    keyIcon.setBounds(0, 0, keyIcon.intrinsicWidth, keyIcon.intrinsicHeight)
                    keyIcon.draw(canvas)
                    canvas.translate(-drawableX.toFloat(), -drawableY.toFloat())
                }
            }
            canvas.translate(-key.x.toFloat(), -key.y.toFloat())
        }

        // Overlay a dark rectangle to dim the keyboard
        if (mMiniKeyboardOnScreen) {
            paint.color = mKeyboardBackgroundColor.adjustAlpha(0.6f)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        mCanvas!!.restore()
        mDrawPending = false
        mDirtyRect.setEmpty()
    }

    private fun setupKeyBackground(key: MyKeyboard.Key, keyCode: Int, canvas: Canvas) {
        val keyBackground = when {
            keyCode == KEYCODE_SPACE && key.label.length > 1 -> getSpaceKeyBackground()
            keyCode == KEYCODE_ENTER -> getEnterKeyBackground()
            else -> mKeyBackground
        }

        val bounds = keyBackground!!.bounds
        if (key.width != bounds.right || key.height != bounds.bottom) {
            keyBackground.setBounds(0, 0, key.width, key.height)
        }

        keyBackground.state = when {
            key.pressed -> intArrayOf(android.R.attr.state_pressed)
            key.focused -> intArrayOf(android.R.attr.state_focused)
            else -> intArrayOf()
        }

        if (key.focused || keyCode == KEYCODE_ENTER) {
            val keyColor = if (key.pressed) {
                mPrimaryColor.adjustAlpha(0.8f)
            } else {
                mPrimaryColor
            }
            keyBackground.applyColorFilter(keyColor)
        } else if (mShowKeyBorders) {
            val keyColor = if (key.pressed) {
                mKeyColorPressed
            } else {
                mKeyColor
            }

            keyBackground.applyColorFilter(keyColor)
        }

        canvas.translate(key.x.toFloat(), key.y.toFloat())
        keyBackground.draw(canvas)
    }

    private fun getSpaceKeyBackground(): Drawable? {
        if (mShowKeyBorders) {
            return mKeyBackground
        }

        val drawableId = if (mUsingSystemTheme) {
            R.drawable.keyboard_space_background_material
        } else {
            R.drawable.keyboard_space_background
        }

        return resources.getDrawable(drawableId, context.theme)
    }

    private fun getEnterKeyBackground(): Drawable? {
        val drawableId = if (mShowKeyBorders) {
            R.drawable.keyboard_enter_background_outlined
        } else {
            R.drawable.keyboard_enter_background
        }
        return resources.getDrawable(drawableId, context.theme)
    }

    private fun drawSecondaryIcon(key: MyKeyboard.Key, canvas: Canvas, textColor: Int) {
        val secondaryIcon = key.secondaryIcon ?: return
        secondaryIcon.applyColorFilter(
            if (key.pressed) textColor else mTextColor.adjustAlpha(0.6f)
        )
        val secondaryIconWidth = (secondaryIcon.intrinsicWidth * 0.5f).toInt()
        val secondaryIconHeight = (secondaryIcon.intrinsicHeight * 0.5f).toInt()
        val secondaryIconPaddingRight = 10
        val secondaryIconLeft = key.width - secondaryIconPaddingRight - secondaryIconWidth
        val secondaryIconRight = secondaryIconLeft + secondaryIconWidth
        val secondaryIconTop = 14
        val secondaryIconBottom = secondaryIconTop + secondaryIconHeight

        secondaryIcon.setBounds(
            secondaryIconLeft, secondaryIconTop, secondaryIconRight, secondaryIconBottom
        )
        secondaryIcon.draw(canvas)
    }

    private fun handleClipboard() {
        // Phase 8.2: when the mood bar owns the toolbar, the clipboard chip
        // and its clear button must stay hidden — animating them in would
        // defeat the visibility gating in `applyMoodBarVisibility()`.
        if (context.config.showMoodBar) {
            hideClipboardViews()
            return
        }
        if (mToolbarHolder != null && mPopupParent.id != R.id.mini_keyboard_view && context.config.showClipboardContent) {
            val clipboardContent = context.getCurrentClip()
            if (clipboardContent?.isNotEmpty() == true) {
                keyboardViewBinding?.apply {
                    clipboardValue.apply {
                        text = clipboardContent
                        removeUnderlines()
                        setOnClickListener {
                            mOnKeyboardActionListener!!.onText(clipboardContent.toString())
                            vibrateIfNeeded()
                        }
                    }

                    toggleClipboardVisibility(true)
                }
            } else {
                hideClipboardViews()
            }
        } else {
            hideClipboardViews()
        }
    }

    private fun hideClipboardViews() {
        keyboardViewBinding?.apply {
            clipboardValue.beGone()
            clipboardValue.alpha = 0f
            clipboardClear.beGone()
            clipboardClear.alpha = 0f
        }
    }

    private fun clearClipboardContent() {
        val clipboardManager =
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager) ?: return
        if (isPiePlus()) {
            clipboardManager.clearPrimaryClip()
        } else {
            val clip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(clip)
        }
    }

    private fun toggleClipboardVisibility(show: Boolean) {
        if (
            (show && keyboardViewBinding?.clipboardValue!!.alpha == 0f) ||
            (!show && keyboardViewBinding?.clipboardValue!!.alpha == 1f)
        ) {
            val newAlpha = if (show) 1f else 0f
            val animations = ArrayList<ObjectAnimator>()
            val clipboardValueAnimation = ObjectAnimator.ofFloat(
                keyboardViewBinding!!.clipboardValue, "alpha", newAlpha
            )
            animations.add(clipboardValueAnimation)

            val clipboardClearAnimation = ObjectAnimator.ofFloat(
                keyboardViewBinding!!.clipboardClear, "alpha", newAlpha
            )
            animations.add(clipboardClearAnimation)

            val animSet = AnimatorSet()
            animSet.playTogether(*animations.toTypedArray())
            animSet.duration = 150
            animSet.interpolator = AccelerateInterpolator()
            animSet.doOnStart {
                if (show) {
                    keyboardViewBinding?.clipboardValue?.beVisible()
                    keyboardViewBinding?.clipboardClear?.beVisible()
                }
            }
            animSet.doOnEnd {
                if (!show) {
                    keyboardViewBinding?.clipboardValue?.beGone()
                    keyboardViewBinding?.clipboardClear?.beGone()
                }
            }
            animSet.start()
        }
    }

    private fun getPressedKeyIndex(x: Int, y: Int): Int {
        return mKeys.indexOfFirst {
            it.isInside(x, y)
        }
    }

    private fun detectAndSendKey(index: Int, x: Int, y: Int, eventTime: Long) {
        if (index != NOT_A_KEY && index in mKeys.indices) {
            val key = mKeys[index]
            getPressedKeyIndex(x, y)
            mOnKeyboardActionListener!!.onKey(key.code)
            mLastTapTime = eventTime
        }
    }

    private fun showPreview(keyIndex: Int) {
        val oldKeyIndex = mCurrentKeyIndex
        val previewPopup = mPreviewPopup
        mCurrentKeyIndex = keyIndex

        if (!context.config.showPopupOnKeypress) {
            return
        }

        // If key changed and preview is on ...
        if (oldKeyIndex != mCurrentKeyIndex) {
            if (previewPopup.isShowing) {
                if (keyIndex == NOT_A_KEY) {
                    mHandler!!.sendMessageDelayed(
                        mHandler!!.obtainMessage(MSG_REMOVE_PREVIEW), DELAY_AFTER_PREVIEW.toLong()
                    )
                }
            }

            if (keyIndex != NOT_A_KEY) {
                showKey(keyIndex)
            }
        }
    }

    private fun showKey(keyIndex: Int) {
        val previewPopup = mPreviewPopup
        val keys = mKeys
        if (keyIndex < 0 || keyIndex >= mKeys.size) {
            return
        }

        val key = keys[keyIndex]
        if (key.code == KEYCODE_SPACE) return // no popup for the language label
        if (key.icon != null) {
            mPreviewText!!.setCompoundDrawables(null, null, null, key.icon)
        } else {
            val customTypeface = FontHelper.getTypeface(context)
            if (key.label.length > 1) {
                mPreviewText!!.setTextSize(TypedValue.COMPLEX_UNIT_PX, mKeyTextSize.toFloat())
                mPreviewText!!.typeface = Typeface.create(customTypeface, Typeface.BOLD)
            } else {
                mPreviewText!!.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    mPreviewTextSizeLarge.toFloat()
                )
                mPreviewText!!.typeface = customTypeface
            }

            mPreviewText!!.setCompoundDrawables(null, null, null, null)
            try {
                mPreviewText!!.text = adjustCase(key.label)
            } catch (ignored: Exception) {
            }
        }

        val previewBackground = mPreviewText!!.background as LayerDrawable
        previewBackground.findDrawableByLayerId(R.id.button_background_shape)
            .applyColorFilter(mBackgroundColor)
        previewBackground.findDrawableByLayerId(R.id.button_background_stroke)
            .applyColorFilter(mStrokeColor)
        mPreviewText!!.background = previewBackground

        mPreviewText!!.setTextColor(mTextColor)
        mPreviewText!!.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = Math.max(mPreviewText!!.measuredWidth, key.width)
        val popupHeight = mPreviewHeight
        val lp = mPreviewText!!.layoutParams
        lp?.width = popupWidth
        lp?.height = popupHeight

        mPopupPreviewX = key.x
        mPopupPreviewY = key.y - popupHeight

        mHandler!!.removeMessages(MSG_REMOVE_PREVIEW)
        getLocationInWindow(mCoordinates)
        mCoordinates[0] += mMiniKeyboardOffsetX // Offset may be zero
        mCoordinates[1] += mMiniKeyboardOffsetY // Offset may be zero

        // Set the preview background state
        mPreviewText!!.background.state = if (key.popupResId != 0) {
            LONG_PRESSABLE_STATE_SET
        } else {
            EMPTY_STATE_SET
        }

        mPopupPreviewX += mCoordinates[0]
        mPopupPreviewY += mCoordinates[1]

        // If the popup cannot be shown above the key, put it on the side
        getLocationOnScreen(mCoordinates)
        if (mPopupPreviewY + mCoordinates[1] < 0) {
            // If the key you're pressing is on the left side of the keyboard, show the popup on
            // the right, offset by enough to see at least one key to the left/right.
            if (key.x + key.width <= width / 2) {
                mPopupPreviewX += (key.width * 2.5).toInt()
            } else {
                mPopupPreviewX -= (key.width * 2.5).toInt()
            }
            mPopupPreviewY += popupHeight
        }

        previewPopup.dismiss()

        if (
            key.label.isNotEmpty()
            && key.code != KEYCODE_MODE_CHANGE
            && key.code != KEYCODE_SYMBOLS_MODE_CHANGE
            && key.code != KEYCODE_SHIFT
        ) {
            previewPopup.width = popupWidth
            previewPopup.height = popupHeight
            previewPopup.showAtLocation(
                mPopupParent,
                Gravity.NO_GRAVITY,
                mPopupPreviewX,
                mPopupPreviewY
            )
            mPreviewText!!.visibility = VISIBLE
        }
    }

    /**
     * Requests a redraw of the entire keyboard. Calling [.invalidate] is not sufficient because the keyboard renders the keys to an off-screen buffer and
     * an invalidate() only draws the cached buffer.
     */
    fun invalidateAllKeys() {
        mDirtyRect.union(0, 0, width, height)
        mDrawPending = true
        invalidate()
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only one key is changing it's content. Any changes that
     * affect the position or size of the key may not be honored.
     * @param keyIndex the index of the key in the attached [MyKeyboard].
     */
    private fun invalidateKey(keyIndex: Int) {
        if (keyIndex < 0 || keyIndex >= mKeys.size) {
            return
        }

        val key = mKeys[keyIndex]
        mDirtyRect.union(
            key.x, key.y,
            key.x + key.width, key.y + key.height
        )
        onBufferDraw()
        invalidate(
            key.x, key.y,
            key.x + key.width, key.y + key.height
        )
    }

    private fun openPopupIfRequired(me: MotionEvent): Boolean {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return false
        }

        if (mCurrentKey < 0 || mCurrentKey >= mKeys.size) {
            return false
        }

        val popupKey = mKeys[mCurrentKey]
        val result = onLongPress(popupKey, me)
        if (result) {
            mAbortKey = true
            showPreview(NOT_A_KEY)
        }

        return result
    }

    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated with this key through the attributes
     * popupLayout and popupCharacters.
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the method on the base class if the subclass doesn't wish to
     * handle the call.
     */
    private fun onLongPress(popupKey: MyKeyboard.Key, me: MotionEvent): Boolean {
        if (popupKey.code == KEYCODE_SPACE) {
            return onSpaceBarLongPressed()
        } else if (popupKey.code == KEYCODE_EMOJI_OR_LANGUAGE) {
            return onEmojiOrLanguageLongPressed()
        } else {
            val popupKeyboardId = popupKey.popupResId
            if (popupKeyboardId != 0) {
                mMiniKeyboardContainer = mMiniKeyboardCache[popupKey]

                // For 'number' and 'phone' keyboards the count of popup keys might be bigger than count of keys in the main keyboard.
                // And therefore the width of the key might be smaller than width declared in MyKeyboard.Key.width for the main keyboard.
                val popupKeyWidth = if (popupKey.popupCharacters != null) {
                    popupKey.calcKeyWidth(containerWidth = mMiniKeyboardContainer?.measuredWidth ?: width)
                } else {
                    popupKey.width
                }

                if (mMiniKeyboardContainer == null) {
                    val inflater =
                        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    keyboardPopupBinding = KeyboardPopupKeyboardBinding.inflate(inflater).apply {
                        mMiniKeyboardContainer = root
                        mMiniKeyboard = miniKeyboardView
                    }

                    val keyboard = if (popupKey.popupCharacters != null) {
                        MyKeyboard(
                            context = context,
                            layoutTemplateResId = popupKeyboardId,
                            characters = popupKey.popupCharacters!!,
                            keyWidth = popupKeyWidth
                        )
                    } else {
                        MyKeyboard(context, popupKeyboardId, 0)
                    }
                    mMiniKeyboard!!.setKeyboard(keyboard)
                    mPopupParent = this
                    mMiniKeyboardContainer!!.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
                    )
                    mMiniKeyboardCache[popupKey] = mMiniKeyboardContainer
                } else {
                    mMiniKeyboard =
                        mMiniKeyboardCache[popupKey]?.let(KeyboardPopupKeyboardBinding::bind)?.miniKeyboardView
                }

                getLocationInWindow(mCoordinates)
                mPopupX = popupKey.x
                mPopupY = popupKey.y

                // Use popupCharacters length if available, otherwise use actual key count from the loaded keyboard
                val popupKeyCount = popupKey.popupCharacters?.length ?: mMiniKeyboard!!.mKeys.size
                val widthToUse = mMiniKeyboardContainer!!.measuredWidth - (popupKeyCount / 2) * popupKeyWidth
                mPopupX = mPopupX + popupKeyWidth - widthToUse
                mPopupY -= mMiniKeyboardContainer!!.measuredHeight
                val x = mPopupX + mCoordinates[0]
                val y = mPopupY + mCoordinates[1]
                val xOffset = Math.max(0, x)
                mMiniKeyboard!!.setPopupOffset(xOffset, y)

                // make sure we highlight the proper key right after long pressing it, before any ACTION_MOVE event occurs
                val miniKeyboardX = if (xOffset + mMiniKeyboard!!.measuredWidth <= measuredWidth) {
                    xOffset
                } else {
                    measuredWidth - mMiniKeyboard!!.measuredWidth
                }

                val keysCnt = mMiniKeyboard!!.mKeys.size
                var selectedKeyIndex =
                    Math.floor((me.x - miniKeyboardX) / popupKeyWidth.toDouble()).toInt()
                if (keysCnt > MAX_KEYS_PER_MINI_ROW) {
                    selectedKeyIndex += MAX_KEYS_PER_MINI_ROW
                }
                selectedKeyIndex = Math.max(0, Math.min(selectedKeyIndex, keysCnt - 1))

                for (i in 0 until keysCnt) {
                    mMiniKeyboard!!.mKeys[i].focused = i == selectedKeyIndex
                }

                mMiniKeyboardSelectedKeyIndex = selectedKeyIndex
                mMiniKeyboard!!.invalidateAllKeys()

                val miniShiftStatus = if (isShifted()) ShiftState.ON_PERMANENT else ShiftState.OFF
                mMiniKeyboard!!.setShifted(miniShiftStatus)
                mPopupKeyboard.contentView = mMiniKeyboardContainer
                mPopupKeyboard.width = mMiniKeyboardContainer!!.measuredWidth
                mPopupKeyboard.height = mMiniKeyboardContainer!!.measuredHeight
                mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
                mMiniKeyboardOnScreen = true
                invalidateAllKeys()
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.action

        if (ignoreTouches) {
            if (action == MotionEvent.ACTION_UP) {
                ignoreTouches = false

                // fix a glitch with long pressing backspace, then clicking some letter
                if (mRepeatKeyIndex != NOT_A_KEY) {
                    val key = mKeys.getOrNull(mRepeatKeyIndex)
                    if (key?.code == KEYCODE_DELETE) {
                        mHandler?.removeMessages(MSG_REPEAT)
                        mRepeatKeyIndex = NOT_A_KEY
                    }
                }
            }
            return true
        }

        // handle moving between alternative popup characters by swiping
        if (mPopupKeyboard.isShowing) {
            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    if (mMiniKeyboard != null) {
                        val coords = intArrayOf(0, 0)
                        mMiniKeyboard!!.getLocationOnScreen(coords)
                        val keysCnt = mMiniKeyboard!!.mKeys.size
                        val lastRowKeyCount = if (keysCnt > MAX_KEYS_PER_MINI_ROW) {
                            Math.max(keysCnt % MAX_KEYS_PER_MINI_ROW, 1)
                        } else {
                            keysCnt
                        }

                        val widthPerKey = if (keysCnt > MAX_KEYS_PER_MINI_ROW) {
                            mMiniKeyboard!!.width / MAX_KEYS_PER_MINI_ROW
                        } else {
                            mMiniKeyboard!!.width / lastRowKeyCount
                        }

                        var selectedKeyIndex =
                            Math.floor((me.x - coords[0]) / widthPerKey.toDouble()).toInt()
                        if (keysCnt > MAX_KEYS_PER_MINI_ROW) {
                            selectedKeyIndex = Math.max(0, selectedKeyIndex)
                            selectedKeyIndex += MAX_KEYS_PER_MINI_ROW
                        }

                        selectedKeyIndex = Math.max(0, Math.min(selectedKeyIndex, keysCnt - 1))
                        if (selectedKeyIndex != mMiniKeyboardSelectedKeyIndex) {
                            for (i in 0 until keysCnt) {
                                mMiniKeyboard!!.mKeys[i].focused = i == selectedKeyIndex
                            }
                            mMiniKeyboardSelectedKeyIndex = selectedKeyIndex
                            mMiniKeyboard!!.invalidateAllKeys()
                        }

                        if (coords[0] > 0 || coords[1] > 0) {
                            if (coords[0] - me.x > mPopupMaxMoveDistance ||                                         // left
                                me.x - (coords[0] + mMiniKeyboard!!.measuredWidth) > mPopupMaxMoveDistance          // right
                            ) {
                                dismissPopupKeyboard()
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mMiniKeyboard?.mKeys?.firstOrNull { it.focused }?.apply {
                        mOnKeyboardActionListener!!.onKey(code)
                    }
                    mMiniKeyboardSelectedKeyIndex = -1
                    dismissPopupKeyboard()
                }
            }
        }

        return onModifiedTouchEvent(me)
    }

    private fun onModifiedTouchEvent(me: MotionEvent): Boolean {
        var touchX = me.x.toInt()
        var touchY = me.y.toInt()
        if (touchY >= -mVerticalCorrection) {
            touchY += mVerticalCorrection
        }

        val action = me.actionMasked
        val eventTime = me.eventTime
        val keyIndex = getPressedKeyIndex(touchX, touchY)

        // Ignore all motion events until a DOWN.
        if (mAbortKey && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
            return true
        }

        // Needs to be called after the gesture detector gets a turn, as it may have displayed the mini keyboard
        if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
            return true
        }

        when (action) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // if the user presses a key while still holding down the previous, type in both chars and ignore the later gestures
                // can happen at fast typing, easier to reproduce by increasing LONGPRESS_TIMEOUT
                ignoreTouches = true
                mHandler!!.removeMessages(MSG_LONGPRESS)
                dismissPopupKeyboard()
                detectAndSendKey(keyIndex, me.x.toInt(), me.y.toInt(), eventTime)

                val newPointerX = me.getX(1).toInt()
                val newPointerY = me.getY(1).toInt()
                val secondKeyIndex = getPressedKeyIndex(newPointerX, newPointerY)
                showPreview(secondKeyIndex)

                detectAndSendKey(secondKeyIndex, newPointerX, newPointerY, eventTime)

                val secondKeyCode = mKeys.getOrNull(secondKeyIndex)?.code
                if (secondKeyCode != null) {
                    mOnKeyboardActionListener!!.onPress(secondKeyCode)
                }

                showPreview(NOT_A_KEY)
                setCurrentKeyPressed(false)
                return true
            }

            MotionEvent.ACTION_DOWN -> {
                mAbortKey = false
                mLastCodeX = touchX
                mLastCodeY = touchY
                mLastKeyTime = 0
                mCurrentKeyTime = 0
                mLastKey = NOT_A_KEY
                mCurrentKey = keyIndex
                mDownTime = me.eventTime
                mLastMoveTime = mDownTime

                val onPressKey = if (keyIndex != NOT_A_KEY) {
                    mKeys[keyIndex].code
                } else {
                    0
                }
                mOnKeyboardActionListener!!.onPress(onPressKey)
                mLastKeyPressedCode = onPressKey

                var wasHandled = false
                if (mCurrentKey >= 0 && mKeys[mCurrentKey].repeatable) {
                    mRepeatKeyIndex = mCurrentKey

                    val msg = mHandler!!.obtainMessage(MSG_REPEAT)
                    mHandler!!.sendMessageDelayed(msg, REPEAT_START_DELAY.toLong())
                    // if the user long presses space bar, move the cursor after swiping left/right
                    if (mKeys[mCurrentKey].code == KEYCODE_SPACE) {
                        mLastSpaceMoveX = -1
                        mCursorControlActive = false
                    } else {
                        repeatKey()
                    }

                    // Delivering the key could have caused an abort
                    if (mAbortKey) {
                        mRepeatKeyIndex = NOT_A_KEY
                        wasHandled = true
                    }
                }

                if (!wasHandled && mCurrentKey != NOT_A_KEY) {
                    val msg = mHandler!!.obtainMessage(MSG_LONGPRESS, me)
                    mHandler!!.sendMessageDelayed(msg, LONGPRESS_TIMEOUT.toLong())
                }

                if (mPopupParent.id != R.id.mini_keyboard_view) {
                    showPreview(keyIndex)
                    setCurrentKeyPressed(true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                var continueLongPress = false
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex
                        mCurrentKeyTime = eventTime - mDownTime
                    } else {
                        if (keyIndex == mCurrentKey) {
                            mCurrentKeyTime += eventTime - mLastMoveTime
                            continueLongPress = true
                        } else if (mRepeatKeyIndex == NOT_A_KEY) {
                            setCurrentKeyPressed(false)
                            mLastKey = mCurrentKey
                            mLastCodeX = mLastX
                            mLastCodeY = mLastY
                            mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime
                            mCurrentKey = keyIndex
                            mCurrentKeyTime = 0
                            setCurrentKeyPressed(true)
                        }
                    }
                }

                // activate cursor control immediately on sufficient movement
                val currentKey = mKeys.getOrNull(mCurrentKey)
                if (currentKey?.code == KEYCODE_SPACE && mLastSpaceMoveX != 0) {
                    if (mLastSpaceMoveX == -1) {
                        mLastSpaceMoveX = mLastX
                    }

                    val diff = mLastX - mLastSpaceMoveX
                    if (diff < -mSpaceMoveThreshold) {
                        for (i in diff / mSpaceMoveThreshold until 0) {
                            mOnKeyboardActionListener?.moveCursorLeft()
                        }
                        mLastSpaceMoveX = mLastX
                        if (!mCursorControlActive) mHandler?.removeMessages(MSG_LONGPRESS)
                        mCursorControlActive = true
                    } else if (diff > mSpaceMoveThreshold) {
                        for (i in 0 until diff / mSpaceMoveThreshold) {
                            mOnKeyboardActionListener?.moveCursorRight()
                        }
                        mLastSpaceMoveX = mLastX
                        if (!mCursorControlActive) mHandler?.removeMessages(MSG_LONGPRESS)
                        mCursorControlActive = true
                    }
                } else if (!continueLongPress) {
                    // Cancel old long-press
                    mHandler!!.removeMessages(MSG_LONGPRESS)
                    // Start new long-press if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        val msg = mHandler!!.obtainMessage(MSG_LONGPRESS, me)
                        mHandler!!.sendMessageDelayed(msg, LONGPRESS_TIMEOUT.toLong())
                    }

                    if (mPopupParent.id != R.id.mini_keyboard_view) {
                        showPreview(mCurrentKey)
                    }
                    mLastMoveTime = eventTime
                }
            }

            MotionEvent.ACTION_UP -> {
                setCurrentKeyPressed(false)

                mLastSpaceMoveX = 0
                removeMessages()
                if (keyIndex == mCurrentKey) {
                    mCurrentKeyTime += eventTime - mLastMoveTime
                } else {
                    mLastKey = mCurrentKey
                    mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime
                    mCurrentKey = keyIndex
                    mCurrentKeyTime = 0
                }

                if (mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < DEBOUNCE_TIME && mLastKey != NOT_A_KEY) {
                    mCurrentKey = mLastKey
                    touchX = mLastCodeX
                    touchY = mLastCodeY
                }
                showPreview(NOT_A_KEY)
                Arrays.fill(mKeyIndices, NOT_A_KEY)

                val currentKeyCode = mKeys.getOrNull(mCurrentKey)?.code

                // If we're not on a repeating key (which sends on a DOWN event)
                if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
                    detectAndSendKey(mCurrentKey, touchX, touchY, eventTime)
                } else if (currentKeyCode == KEYCODE_SPACE && !mCursorControlActive) {
                    detectAndSendKey(mCurrentKey, touchX, touchY, eventTime)
                }

                mRepeatKeyIndex = NOT_A_KEY
                mOnKeyboardActionListener!!.onActionUp()
                mCursorControlActive = false
            }

            MotionEvent.ACTION_CANCEL -> {
                mCursorControlActive = false
                mLastSpaceMoveX = 0
                removeMessages()
                dismissPopupKeyboard()
                mAbortKey = true
                showPreview(NOT_A_KEY)
                setCurrentKeyPressed(false)
            }
        }

        mLastX = touchX
        mLastY = touchY
        return true
    }

    private fun repeatKey(): Boolean {
        val key = mKeys[mRepeatKeyIndex]
        if (key.code != KEYCODE_SPACE) {
            detectAndSendKey(mCurrentKey, key.x, key.y, mLastTapTime)
        }
        return true
    }

    private fun setCurrentKeyPressed(pressed: Boolean) {
        mKeys.getOrNull(mCurrentKey)?.pressed = pressed
        invalidateKey(mCurrentKey)
    }

    fun closeClipboardManager() {
        keyboardViewBinding?.apply {
            clipboardManagerHolder.beGone()
            suggestionsHolder.showAllInlineContentViews()
        }
    }

    private fun openClipboardManager() {
        keyboardViewBinding?.apply {
            clipboardManagerHolder.beVisible()
            suggestionsHolder.hideAllInlineContentViews()
        }
        setupStoredClips()
    }

    private fun onSpaceBarLongPressed(): Boolean {
        return if (!mCursorControlActive) {
            setCurrentKeyPressed(false)
            mRepeatKeyIndex = NOT_A_KEY
            mHandler?.removeMessages(MSG_REPEAT)
            vibrateIfNeeded()
            SwitchLanguageDialog(this) {
                mOnKeyboardActionListener?.reloadKeyboard()
            }
            true
        } else false
    }

    private fun onEmojiOrLanguageLongPressed(): Boolean {
        setCurrentKeyPressed(false)
        if (context.config.showEmojiKey) {
            openEmojiPalette()
        } else {
            SwitchLanguageDialog(this) {
                mOnKeyboardActionListener?.reloadKeyboard()
            }
        }

        return true
    }

    private fun setupStoredClips() {
        ensureBackgroundThread {
            val clips = ArrayList<ListItem>()
            val clipboardContent = context.getCurrentClip()

            val pinnedClips = context.clipsDB.getClips()
            val isCurrentClipPinnedToo = pinnedClips.any {
                clipboardContent?.isNotEmpty() == true && it.value.trim() == clipboardContent
            }

            if (!isCurrentClipPinnedToo && clipboardContent?.isNotEmpty() == true) {
                val section = ClipsSectionLabel(context.getString(R.string.clipboard_current), true)
                clips.add(section)

                val clip = Clip(-1, clipboardContent)
                clips.add(clip)
            }

            if (!isCurrentClipPinnedToo && clipboardContent?.isNotEmpty() == true) {
                val section = ClipsSectionLabel(context.getString(R.string.clipboard_pinned), false)
                clips.add(section)
            }

            clips.addAll(pinnedClips)
            Handler(Looper.getMainLooper()).post {
                setupClipsAdapter(clips)
            }
        }
    }

    private fun setupClipsAdapter(clips: ArrayList<ListItem>) {
        keyboardViewBinding?.apply {
            clipboardContentPlaceholder1.beVisibleIf(clips.isEmpty())
            clipboardContentPlaceholder2.beVisibleIf(clips.isEmpty())
            clipsList.beVisibleIf(clips.isNotEmpty())
        }

        val refreshClipsListener = object : RefreshClipsListener {
            override fun refreshClips() {
                setupStoredClips()
            }
        }

        val adapter = ClipsKeyboardAdapter(
            context = safeStorageContext,
            items = clips,
            refreshClipsListener = refreshClipsListener
        ) { clip ->
            mOnKeyboardActionListener!!.onText(clip.value)
            vibrateIfNeeded()
        }

        keyboardViewBinding?.clipsList?.adapter = adapter
    }

    private fun setupEmojiPalette(toolbarColor: Int, backgroundColor: Int, textColor: Int) {
        keyboardViewBinding?.apply {
            emojiPaletteTopBar.background = ColorDrawable(toolbarColor)
            emojiPaletteHolder.background = ColorDrawable(backgroundColor)
            emojiPaletteClose.applyColorFilter(textColor)
            emojiPaletteLabel.setTextColor(textColor)

            emojiPaletteBottomBar.background = ColorDrawable(toolbarColor)
            emojiPaletteModeChange.apply {
                setTextColor(textColor)
                setOnClickListener {
                    vibrateIfNeeded()
                    closeEmojiPalette()
                }
            }

            emojiPaletteBackspace.apply {
                applyColorFilter(textColor)
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isPressed = true
                            mRepeatKeyIndex = mKeys.indexOfFirst { it.code == KEYCODE_DELETE }
                            mCurrentKey = mRepeatKeyIndex
                            vibrateIfNeeded()
                            mOnKeyboardActionListener!!.onKey(KEYCODE_DELETE)
                            // setup repeating backspace
                            val msg = mHandler!!.obtainMessage(MSG_REPEAT)
                            mHandler!!.sendMessageDelayed(msg, REPEAT_START_DELAY.toLong())
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            mHandler!!.removeMessages(MSG_REPEAT)
                            mRepeatKeyIndex = NOT_A_KEY
                            isPressed = false
                            false
                        }

                        else -> false
                    }
                }
            }
        }

        setupEmojis()
    }

    fun openEmojiPalette() {
        keyboardViewBinding!!.emojiPaletteHolder.beVisible()
        keyboardViewBinding!!.suggestionsHolder.beGone()
        setupEmojis()
    }

    private fun closeEmojiPalette() {
        keyboardViewBinding?.apply {
            emojiPaletteHolder.beGone()
            emojisList.scrollToPosition(0)
            suggestionsHolder.beVisible()
        }
    }

    /**
     * Phase 13: when a mood-bar tap changes `Config.lastMoodScore` (or
     * clears it) and the emoji drawer is currently visible, rebuild the
     * emoji list so the Phase 12 curated section reflects the new mood.
     *
     * Re-runs `setupEmojis()` which already dispatches its work to a
     * background thread and posts the new adapter back to the main thread.
     * The Phase 12 `addCuratedMoodSection(items)` call at the top of
     * `prepareEmojiItems` reads `Config.lastMoodScore` fresh on every
     * invocation, so a fresh `setupEmojis` call automatically picks up
     * the new curated emoji glyph and list.
     *
     * Called from `SimpleKeyboardIME.onSharedPreferenceChanged` *after*
     * the controller's Config write commits — the listener-driven path
     * decouples the rebuild from the click handler (`IkdMoodBarController`
     * writes on `Dispatchers.IO`; the listener fires after the write so
     * the rebuild reads the freshly-written value). No-ops when the
     * emoji drawer is not currently visible.
     */
    fun notifyEmojiAdapterMoodChanged() {
        val binding = keyboardViewBinding ?: return
        if (binding.emojiPaletteHolder.visibility == View.VISIBLE) {
            binding.emojisList.scrollToPosition(0)
            setupEmojis()
        }
    }

    private fun setupEmojis() {
        ensureBackgroundThread {
            val fullEmojiList = parseRawEmojiSpecsFile(context, EMOJI_SPEC_FILE_PATH)
            val systemFontPaint = Paint().apply {
                typeface = Typeface.DEFAULT
            }

            val emojis = fullEmojiList.filter { emoji ->
                systemFontPaint.hasGlyph(emoji.emoji) || (EmojiCompat.get().loadState == EmojiCompat.LOAD_STATE_SUCCEEDED && EmojiCompat.get()
                    .getEmojiMatch(emoji.emoji, emojiCompatMetadataVersion) == EMOJI_SUPPORTED)
            }

            Handler(Looper.getMainLooper()).post {
                setupEmojiAdapter(emojis)
            }
        }
    }

    // For Vietnamese - Telex
    private fun setupLanguageTelex() {
        ensureBackgroundThread {
            parseRawJsonSpecsFile(context, LANGUAGE_VN_TELEX)
        }
    }

    private fun prepareEmojiCategories(emojis: List<EmojiData>): Map<String, List<EmojiData>> {
        val recentEmojis = context.config.recentlyUsedEmojis
            .mapNotNull { emoji ->
                val emojiData = emojis.firstOrNull { it.emoji == emoji }
                emojiData?.copy(category = RECENTLY_USED_EMOJIS)
            }

        return (recentEmojis + emojis).groupBy { it.category }
    }

    private fun prepareEmojiItems(categories: Map<String, List<EmojiData>>): List<EmojisAdapter.Item> {
        val emojiItems = mutableListOf<EmojisAdapter.Item>()
        // Phase 12: when a standing mood is set, prepend a curated
        // "Mood: <emoji>" section at the top of the drawer. Read once
        // at palette-open time — by design (Phase 12 decision #4) the
        // section does not re-render mid-palette if the user changes
        // their mood while the drawer is open.
        addCuratedMoodSection(emojiItems)
        categories.entries.forEach { (category, emojis) ->
            emojiItems.add(EmojisAdapter.Item.Category(category))
            emojiItems.addAll(emojis.map(EmojisAdapter.Item::Emoji))
        }

        return emojiItems
    }

    /**
     * Phase 12: prepends a curated section of mood-appropriate emojis to
     * [items] when `Config.lastMoodScore` is a standing rating. The
     * pseudo-category key is encoded as `mood_curated:<emoji>` so the
     * adapter's title renderer can split the trailing glyph into the
     * localized "Mood: %1$s" format. Returns immediately when no mood
     * is set — the drawer then renders byte-identically to today.
     *
     * Privacy invariant: the curated codepoints live in [MoodEmoji] only
     * and are never written to `ikd.db`. The Phase-7 `onEmojiText`
     * pipeline records taps as `EMOJI` events without storing the glyph.
     */
    private fun addCuratedMoodSection(items: MutableList<EmojisAdapter.Item>) {
        val score = context.config.lastMoodScore
        if (!MoodEmoji.isStandingScore(score)) {
            return
        }
        val moodGlyph = MoodEmoji.emojiFor(score)
        if (moodGlyph.isEmpty()) {
            return
        }
        val curated = MoodEmoji.curatedEmojisFor(score)
        if (curated.isEmpty()) {
            return
        }
        val categoryKey = "$MOOD_CURATED_CATEGORY:$moodGlyph"
        items.add(EmojisAdapter.Item.Category(categoryKey))
        curated.forEach { codepoint ->
            items.add(
                EmojisAdapter.Item.Emoji(
                    EmojiData(
                        category = categoryKey,
                        emoji = codepoint,
                        variants = emptyList(),
                    )
                )
            )
        }
    }

    private fun setupEmojiAdapter(emojis: List<EmojiData>) {
        val emojiCategories = prepareEmojiCategories(emojis)
        var emojiItems = prepareEmojiItems(emojiCategories)

        val emojiLayoutManager = AutoGridLayoutManager(
            context = context,
            itemWidth = context.resources.getDimensionPixelSize(R.dimen.emoji_item_size)
        ).apply {
            spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (emojiItems[position] is EmojisAdapter.Item.Category) {
                        spanCount
                    } else {
                        1
                    }
                }
            }
        }

        val emojiCategoryIds = mutableMapOf<Int, String>()
        val emojiCategoryColor = mTextColor.adjustAlpha(0.8f)
        keyboardViewBinding?.emojiCategoriesStrip?.apply {
            weightSum = emojiCategories.count().toFloat()
            val strip = this
            removeAllViews()
            emojiCategories.entries.forEach { (category, _) ->
                ItemEmojiCategoryBinding.inflate(
                    LayoutInflater.from(context),
                    this,
                    true
                ).root.apply {
                    id = generateViewId()
                    emojiCategoryIds[id] = category
                    setImageResource(getCategoryIconRes(category))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f
                    )
                    setOnClickListener {
                        strip.children.filterIsInstance<ImageButton>().forEach {
                            it.applyColorFilter(emojiCategoryColor)
                        }
                        applyColorFilter(mPrimaryColor)
                        keyboardViewBinding?.emojisList?.stopScroll()
                        emojiLayoutManager.scrollToPositionWithOffset(
                            emojiItems.indexOfFirst { it is EmojisAdapter.Item.Category && it.value == category },
                            0
                        )
                    }
                    applyColorFilter(emojiCategoryColor)
                }
            }
        }

        keyboardViewBinding?.emojisList?.apply {
            layoutManager = emojiLayoutManager
            adapter = EmojisAdapter(context = safeStorageContext, items = emojiItems) { emoji ->
                mOnKeyboardActionListener!!.onEmojiText(emoji.emoji)
                vibrateIfNeeded()

                context.config.addRecentEmoji(emoji.emoji)
                (adapter as? EmojisAdapter)?.apply {
                    emojiItems = prepareEmojiItems(prepareEmojiCategories(emojis))
                    updateItems(emojiItems)
                }
            }

            clearOnScrollListeners()
            onScroll { offset ->
                keyboardViewBinding!!.emojiPaletteTopBar.elevation = when {
                    offset > 4 -> context.resources.getDimensionPixelSize(R.dimen.one_dp).toFloat()
                    else -> 0f
                }

                emojiLayoutManager.findFirstCompletelyVisibleItemPosition()
                    .also { firstVisibleIndex ->
                        emojiItems
                            .withIndex()
                            .lastOrNull { it.value is EmojisAdapter.Item.Category && it.index <= firstVisibleIndex }
                            ?.also { activeCategory ->
                                // Phase 12: the curated "mood_curated:<emoji>"
                                // pseudo-category has no entry in the
                                // category strip, so its key is absent
                                // from `emojiCategoryIds`. Skip the
                                // highlight update for it instead of
                                // crashing on `.first { ... }`.
                                val activeKey =
                                    (activeCategory.value as EmojisAdapter.Item.Category).value
                                val id = emojiCategoryIds.entries
                                    .firstOrNull { it.value == activeKey }
                                    ?.key ?: return@also

                                keyboardViewBinding
                                    ?.emojiCategoriesStrip
                                    ?.children
                                    ?.filterIsInstance<ImageButton>()
                                    ?.forEach { button ->
                                        val selected = button.id == id
                                        button.applyColorFilter(
                                            if (selected) mPrimaryColor else emojiCategoryColor
                                        )
                                    }
                            }
                    }
            }
        }
    }

    private fun closing() {
        if (mPreviewPopup.isShowing) {
            mPreviewPopup.dismiss()
        }
        removeMessages()
        dismissPopupKeyboard()
        mBuffer = null
        mCanvas = null
        mMiniKeyboardCache.clear()
    }

    private fun removeMessages() {
        mHandler?.apply {
            removeMessages(MSG_REPEAT)
            removeMessages(MSG_LONGPRESS)
        }
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closing()
        // Phase 8: cancel any in-flight mood-bar Room writes so we don't
        // hold the view (and surrounding IME service context) alive past
        // detach. If the view is ever re-attached (uncommon for an IME
        // root) the bar is re-bound on next visibility — not a UX
        // regression because the highlight is derived from
        // `Config.privacyModeEnabled`, not from the cancelled query.
        moodScope.coroutineContext[Job]?.cancel()
        // Phase 8.2: drop any in-flight mood-bubble popup + its dismiss
        // callback so a re-attach doesn't fire on a leaked window token.
        mMoodBubbleHandler.removeCallbacks(mMoodBubbleDismissRunnable)
        mMoodBubblePopup?.dismiss()
        // Phase 8.4: cancel any in-flight dance animator so a leaked frame
        // callback can't keep this view alive past detach.
        currentMoodDanceAnimator?.cancel()
        currentMoodDanceAnimator = null
        // Phase 8.5: drop any pending auto-collapse callback + cancel
        // every animator on the mood-bar surface (cross-fade + per-slot
        // highlights) so a re-attach starts from a clean state.
        mMoodCollapseHandler.removeCallbacks(mMoodAutoCollapseRunnable)
        cancelAllMoodAnimators()
        // Phase 8.5 focus-loss collapse: clear the persisted expansion
        // state ONLY when the view is actually being torn down. Doing
        // this from the IME's onFinishInputView would desync Config and
        // the live view across input-field switches (where the IME stays
        // attached); the next scheduleAutoCollapse would then read
        // moodBarExpanded=false, hit its guard, and never post the
        // collapse runnable — stranding the bar in the expanded state.
        if (context.config.moodBarExpanded) {
            context.config.moodBarExpanded = false
        }
    }

    private fun dismissPopupKeyboard() {
        if (mPopupKeyboard.isShowing) {
            mPopupKeyboard.dismiss()
            mMiniKeyboardOnScreen = false
            setCurrentKeyPressed(false)
            invalidateAllKeys()
        }
    }

    private fun getKeyColor(): Int {
        val backgroundColor = safeStorageContext.getKeyboardBackgroundColor()
        val lighterColor = backgroundColor.lightenColor()
        val keyColor = if (safeStorageContext.isDynamicTheme()) {
            lighterColor
        } else {
            if (backgroundColor == Color.BLACK) {
                backgroundColor.getContrastColor().adjustAlpha(0.1f)
            } else {
                lighterColor
            }
        }
        return keyColor
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun addToClipboardViews(it: InlineContentView, addToFront: Boolean = false) {
        if (keyboardViewBinding?.autofillSuggestionsHolder != null) {
            val newLayoutParams = LinearLayout.LayoutParams(it.layoutParams)
            newLayoutParams.updateMarginsRelative(start = resources.getDimensionPixelSize(R.dimen.normal_margin))
            it.layoutParams = newLayoutParams
            if (addToFront) {
                keyboardViewBinding?.autofillSuggestionsHolder?.addView(it, 0)
            } else {
                keyboardViewBinding?.autofillSuggestionsHolder?.addView(it)
            }
            updateSuggestionsToolbarLayout()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun clearClipboardViews() {
        keyboardViewBinding?.autofillSuggestionsHolder?.removeAllViews()
        updateSuggestionsToolbarLayout()
    }

    private fun updateSuggestionsToolbarLayout() {
        keyboardViewBinding?.apply {
            if (hasInlineViews()) {
                // make room on suggestion toolbar for inline views
                suggestionsItemsHolder.gravity = Gravity.NO_GRAVITY
                clipboardValue.maxWidth =
                    resources.getDimensionPixelSize(R.dimen.suggestion_max_width)
            } else {
                // restore original clipboard toolbar appearance
                suggestionsItemsHolder.gravity = Gravity.CENTER_HORIZONTAL
                suggestionsHolder.measuredWidth.also { maxWidth ->
                    clipboardValue.maxWidth = maxWidth
                }
            }
        }
    }

    /**
     * Returns true if there are [InlineContentView]s in [autofill_suggestions_holder]
     */
    private fun hasInlineViews() =
        (keyboardViewBinding?.autofillSuggestionsHolder?.childCount ?: 0) > 0

    /**
     * Returns: Popup Key width depends on popup keys count
     */
    private fun MyKeyboard.Key.calcKeyWidth(containerWidth: Int): Int {
        val popupKeyCount = this.popupCharacters!!.length

        return if (popupKeyCount > containerWidth / this.width) {
            containerWidth / popupKeyCount
        } else {
            this.width
        }
    }
}
