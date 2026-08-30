package com.remoteconfig.override.ui.editor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.os.SystemClock
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Scroller
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.atomic.AtomicLong
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

/**
 * Native editor used for large JSON documents.
 *
 * EditText owns the text layout, caret, selection and IME interaction.  The
 * surrounding View only draws the gutter and applies a temporary hardware
 * scale during a pinch; no Compose text tree is rebuilt while scrolling.
 */
class NativeJsonEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val GUTTER_DP = 40f
        private const val TEXT_START_DP = 8f
        private const val TEXT_PADDING_DP = 8f
        private const val MIN_TEXT_SIZE_SP = 8f
        private const val MAX_TEXT_SIZE_SP = 32f
        // The legal lower bound is the absolute 8sp text size below. Keep the
        // per-gesture floor low enough that a zoom-in can be undone in one
        // continuous pinch instead of stopping at 0.75x and requiring a
        // second gesture.
        private const val MIN_GESTURE_SCALE = 0.25f
        private const val MAX_GESTURE_SCALE = 2.5f
        private const val MAX_HIGHLIGHT_CHARS = 64_000
        // Keep the caret clearly above the keyboard so the IME does not apply
        // a second automatic bring-into-view shift. This remains virtual and
        // does not add blank rows to the document's actual scroll range.
        private const val IME_EXTRA_MARGIN_DP = 120f
        // Keep the reveal visible on 60Hz screens while returning quickly to
        // editing; the target-following animator absorbs intermediate IME
        // inset frames without restarting the transition.
        private const val IME_REVEAL_ANIMATION_MS = 200
        // EditText already consumes one unit of drag.  The extra 1.50 units
        // below make the total direct drag about 2.5x, matching the requested
        // scroll speed without changing fling velocity.
        private const val DIRECT_DRAG_BOOST = 1.50f
        // 手势开始位置距离文档底部不足该值时，手势结束后的字号重排会让
        // 合法滚动范围变小，旧 scrollY 可能短暂越过新的最大值。该常量只
        // 决定稳定期是否追加边界钳制（continueZoomBoundaryClamp），不再
        // 影响捏合过程本身走哪条缩放路径。
        private const val ZOOM_BOTTOM_GUARD_DP = 96f
        private const val TRACE_TAG = "RemoteConfigZoom"
        private const val TRACE_MAX_BYTES = 2L * 1024L * 1024L
    }

    private val density = resources.displayMetrics.density
    // Diagnostic tracing is useful while developing, but must be completely
    // inert in Release: scroll callbacks can arrive once per frame and even a
    // bounded background write adds avoidable allocation/dispatch overhead.
    private val traceEnabled =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val gutterWidth = dp(GUTTER_DP)
    private val imeExtraMargin = dp(IME_EXTRA_MARGIN_DP)
    private val basePaddingBottom = dp(TEXT_PADDING_DP)
    private val content = FrameLayout(context)
    private val backgroundLight = Color.WHITE
    private val backgroundDark = Color.rgb(30, 30, 30)
    private val lineBackgroundLight = Color.rgb(245, 245, 245)
    private val lineBackgroundDark = Color.rgb(37, 37, 38)
    private val textLight = Color.rgb(51, 51, 51)
    private val textDark = Color.rgb(212, 212, 212)
    private val lineLight = Color.rgb(154, 160, 166)
    private val lineDark = Color.rgb(133, 133, 133)
    private var initializationComplete = false
    private val editor = EditorEditText(context)
    private val editorScroller = Scroller(context)
    private val gutter = GutterView(context)
    private var highlightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var traceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val traceLock = Any()
    private var traceWriter: BufferedWriter? = null
    private var traceFile: File? = null
    private var traceBytes = 0L
    private val traceEventId = AtomicLong(0L)
    private var traceScaleSamples = 0
    private val highlightGeneration = AtomicLong(0L)
    private var highlightJob: Job? = null
    private var suppressTextCallback = false
    private var darkTheme = false
    private var fontSizeSp = 13f
    private var zooming = false
    private var consumeTouchUntilUp = false
    private var gestureScale = 1f
    private var gestureBaseFontSize = 13f
    private var gestureMinScale = MIN_GESTURE_SCALE
    private var gestureMaxScale = MAX_GESTURE_SCALE
    private var gesturePivotX = 0f
    private var gesturePivotY = 0f
    private var gestureLastFocusX = 0f
    private var gestureLastFocusY = 0f
    private var zoomAnchorOffset = -1
    private var zoomAnchorScreenY = 0
    // Preserve the finger's fractional position inside the anchor line. A
    // line-top-only restore can move the first post-IME pinch by part of a
    // line after TextView rebuilds its layout at the new text size.
    private var zoomAnchorLineFraction = 0f
    private var zoomAnchorScrollX = 0
    private var zoomAnchorAtDocumentEnd = false
    private var zoomClampAtDocumentBoundary = false
    private var zoomRelayoutPending = false
    private var zoomRestoreToken = 0
    private var imeVisible = false
    private var composeImeVisible = false
    private var composeImeInsetPx = 0
    private var platformImeVisible = false
    private var platformImeInsetPx = 0
    private var imeBottomInsetPx = 0
    private var lastImeHiddenHeight = 0
    private var imeRevealAnimationPending = false
    private var imeRevealAnimator: ValueAnimator? = null
    private var imeRevealStartY = 0
    private var imeRevealTargetY = 0
    private var fontSizeClampToken = 0
    // While the IME close animation changes the viewport height, keep the
    // user's current document position.  This is a settling guard only; it
    // deliberately does not restore the pre-IME scroll position.
    private var imeClosePending = false
    private var imeCloseToken = 0
    private var velocityTracker: VelocityTracker? = null
    private var velocityTracking = false
    private var dragDistanceY = 0f
    private var lastTouchY = 0f
    private var zoomGestureActive = false
    private var touchScrollActive = false
    private var touchMoved = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchSelectionStart = 0
    private var touchSelectionEnd = 0
    // While the IME is hidden, delay EditText's implicit show request until
    // the touch is known to be a tap.  A drag that begins over a selection
    // must remain a pure scroll gesture and never pop the keyboard midway.
    private var deferImeForTouch = false
    private var flingActive = false

    var onTextChanged: ((String) -> Unit)? = null
    var onFontSizeChanged: ((Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                zooming = true
                consumeTouchUntilUp = true
                zoomGestureActive = true
                cancelVelocityTracking()
                gestureScale = 1f
                gestureBaseFontSize = fontSizeSp
                zoomAnchorAtDocumentEnd = isAtDocumentEnd()
                zoomRelayoutPending = false
                zoomRestoreToken++
                // 只记录手势开始时是否贴近（或正处于）文档底部：它决定
                // 手势结束后重排稳定期的边界钳制强度，不再切换捏合路径。
                val layoutHeight = editor.layout?.height ?: 0
                val viewportHeight = (editor.height - editor.totalPaddingTop - editor.totalPaddingBottom)
                    .coerceAtLeast(0)
                val currentMaxY = (layoutHeight - viewportHeight).coerceAtLeast(0)
                val distanceToDocumentEnd = (currentMaxY - editor.scrollY).coerceAtLeast(0)
                zoomClampAtDocumentBoundary = zoomAnchorAtDocumentEnd ||
                    (layoutHeight > viewportHeight &&
                        distanceToDocumentEnd <= dp(ZOOM_BOTTOM_GUARD_DP))
                // Do not derive a second minimum from the current
                // content/viewport ratio.  That ratio made a short document
                // at the bottom stop shrinking at (for example) 0.85x, so a
                // zoom-in could not be undone in one pinch.  The native
                // TextView is the source of truth for the legal scroll range:
                // scrollTo() clamps against the real layout on every pass,
                // so removing this artificial floor is safe even for an
                // end-anchored gesture.
                gestureMinScale = max(
                    MIN_GESTURE_SCALE,
                    MIN_TEXT_SIZE_SP / fontSizeSp
                )
                gestureMaxScale = min(MAX_GESTURE_SCALE, MAX_TEXT_SIZE_SP / fontSizeSp)
                // 放大与缩小统一走硬件层预览：捏合期间只改 content 的
                // scale/translation，绝不触发 TextView 重排。旧实现在缩小
                // （或从文档底部附近开始）时改为每个采样调用 setFontSize()，
                // setTextSize() 造成的全文重排正是双指缩小掉帧的根因。
                // 最终字号在 onScaleEnd() 一次性落地，位置由锚点恢复。
                gesturePivotX = detector.focusX.coerceIn(0f, width.toFloat())
                gesturePivotY = detector.focusY.coerceIn(0f, height.toFloat())
                gestureLastFocusX = detector.focusX
                gestureLastFocusY = detector.focusY
                captureZoomAnchor(detector.focusX, detector.focusY)
                content.pivotX = gesturePivotX
                content.pivotY = gesturePivotY
                content.scaleX = 1f
                content.scaleY = 1f
                content.translationX = 0f
                content.translationY = 0f
                content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                syncGutterPreviewTransform()
                parent?.requestDisallowInterceptTouchEvent(true)
                traceScaleSamples = 0
                traceState("scale_begin focus=${detector.focusX},${detector.focusY} factor=${detector.scaleFactor}")
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!zooming) return false
                // ScaleGestureDetector's focus can move while the fingers are
                // pinching.  Carry that pan into the temporary layer so text
                // (and the caret) stays under the same finger position.  靠近
                // 文档底部也无需跳过竖直平移：clampTemporaryTransform() 的
                // 文档末端钳制会自动把平移挡在最后一行之内。
                content.translationX += detector.focusX - gestureLastFocusX
                content.translationY += detector.focusY - gestureLastFocusY
                gestureLastFocusX = detector.focusX
                gestureLastFocusY = detector.focusY
                gestureScale = (gestureScale * detector.scaleFactor)
                    .coerceIn(gestureMinScale, gestureMaxScale)
                traceScaleSamples++
                if (traceScaleSamples <= 3 || traceScaleSamples % 8 == 0) {
                    traceState("scale_sample n=$traceScaleSamples focus=${detector.focusX},${detector.focusY} factor=${detector.scaleFactor}")
                }
                // 放大与缩小都只更新硬件层变换，绝不在这里调用
                // setFontSize()：每个手势采样触发一次 setTextSize() 全文
                // 重排正是双指缩小掉帧的根因。缩小时层比视口小，
                // clampTemporaryTransform() 会把平移钳回 0，避免暴露层外
                // 空白；被缩掉的相邻内容由手势结束后的真实重排展现。
                content.scaleX = gestureScale
                content.scaleY = gestureScale
                clampTemporaryTransform()
                syncGutterPreviewTransform()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (!zooming) return
                val factor = gestureScale
                traceState("scale_end_pre factor=$factor focus=${detector.focusX},${detector.focusY}")
                // Capture the point that is actually under the final pinch
                // focus before removing the temporary hardware transform.
                // Restoring only the begin anchor loses any focus movement and
                // is most noticeable on the first pinch after an IME resize.
                if (!zoomAnchorAtDocumentEnd) {
                    captureZoomPreviewAnchor(gestureLastFocusX, gestureLastFocusY)
                }
                zooming = false
                zoomRelayoutPending = true
                val restoreToken = ++zoomRestoreToken
                // Keep consuming the remainder of this pointer sequence.  If
                // the second finger leaves first, the child must not receive a
                // synthetic one-finger scroll event.
                post {
                    content.scaleX = 1f
                    content.scaleY = 1f
                    content.translationX = 0f
                    content.translationY = 0f
                    content.setLayerType(View.LAYER_TYPE_NONE, null)
                    resetGutterPreviewTransform()
                    // 硬件层预览期间真实字号未动，这里一次性落地最终值，
                    // 整个手势只发生一次全文重排；位置由 restoreZoomAnchor()
                    // 在新布局上恢复。
                    if (abs(factor - 1f) > 0.01f) {
                        val next = (gestureBaseFontSize * factor)
                            .coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
                        if (abs(next - fontSizeSp) > 0.01f) {
                            setFontSize(next)
                            onFontSizeChanged?.invoke(next)
                        }
                    }
                    restoreZoomAnchor()
                    if (zoomClampAtDocumentBoundary && !zoomAnchorAtDocumentEnd) {
                        // A near-end gesture preserves its logical line rather
                        // than snapping to the end, but its new layout can
                        // still settle over several frames. Keep the current
                        // position clamped during that tail as well.
                        continueZoomBoundaryClamp(8)
                    }
                    // The parent intercepts the tail of a two-finger
                    // sequence, so EditorEditText may not receive the final
                    // ACTION_UP that normally restores cursor visibility.
                    // Always restore it here after the zoom transaction.
                    editor.setCursorVisible(true)
                    editor.showSoftInputOnFocus = true
                    deferImeForTouch = false
                    // setTextSize() may finish its final layout after this
                    // posted transaction. Keep the anchor guard alive until
                    // that tail has had time to settle, then let normal
                    // selection/IME scrolling resume.
                    postDelayed({
                        if (restoreToken == zoomRestoreToken) {
                            zoomRelayoutPending = false
                        }
                    }, 240L)
                    gutter.invalidate()
                    traceState("scale_end_post factor=$factor")
                }
            }
        }
    )

    init {
        setWillNotDraw(false)
        clipChildren = true
        clipToPadding = true
        isFocusable = true

        content.clipChildren = true
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        editor.setBackgroundColor(Color.TRANSPARENT)
        editor.setTextColor(textLight)
        editor.setHintTextColor(textLight)
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        editor.typeface = Typeface.MONOSPACE
        editor.gravity = Gravity.TOP or Gravity.START
        editor.includeFontPadding = true
        editor.setPadding(
            gutterWidth + dp(TEXT_START_DP),
            dp(TEXT_PADDING_DP),
            dp(TEXT_PADDING_DP),
            basePaddingBottom
        )
        editor.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        editor.imeOptions = editor.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        editor.setHorizontallyScrolling(true)
        editor.isSingleLine = false
        editor.overScrollMode = View.OVER_SCROLL_NEVER
        editor.isVerticalScrollBarEnabled = true
        editor.isHorizontalScrollBarEnabled = false
        editor.setSelectAllOnFocus(false)
        // Increase friction slightly so a release has less inertial travel;
        // direct finger movement is accelerated independently below.
        editorScroller.setFriction(ViewConfiguration.getScrollFriction() * 1.20f)
        editor.setScroller(editorScroller)

        content.addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Keep the gutter outside the transformed content. Otherwise a
        // horizontal pinch pan moves the row numbers together with the text,
        // allowing the text to slide underneath/into the gutter area.
        addView(gutter, LayoutParams(gutterWidth, LayoutParams.MATCH_PARENT))

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                gutter.invalidate()
                if (!suppressTextCallback) {
                    onTextChanged?.invoke(s?.toString().orEmpty())
                    scheduleHighlight()
                }
            }
        })
        editor.setOnScrollChangeListener { _, scrollX, scrollY, oldScrollX, oldScrollY ->
            gutter.invalidate()
            traceState("scroll_changed x=$scrollX y=$scrollY oldX=$oldScrollX oldY=$oldScrollY")
        }
        // Merge the platform IME signal with Compose's inset value.  Some
        // keyboards dispatch one source a frame earlier than the other.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            updatePlatformImeInsets(insets)
            insets
        }
        applyThemeColors()
        initializationComplete = true
    }

    fun setDarkTheme(dark: Boolean) {
        if (darkTheme == dark) return
        darkTheme = dark
        applyThemeColors()
        scheduleHighlight()
    }

    fun setEditorTextIfChanged(value: String) {
        if (editor.text.toString() == value) return
        suppressTextCallback = true
        editor.setText(value)
        // A newly opened configuration always starts at the first line.  Do
        // not put the selection at the end: EditText would immediately scroll
        // the whole document to the bottom to reveal that caret.
        editor.setSelection(0)
        editor.scrollTo(0, 0)
        suppressTextCallback = false
        scheduleHighlight()
        gutter.invalidate()
        editor.post {
            editor.scrollTo(0, 0)
            gutter.invalidate()
        }
    }

    fun setFontSize(sizeSp: Float) {
        val next = sizeSp.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
        if (abs(next - fontSizeSp) < 0.01f) return
        traceState("set_font_size from=$fontSizeSp to=$next")
        // Toolbar +/- changes use this method outside a pinch. Capture the
        // current logical position before TextView rebuilds its layout; if it
        // was at the document end, keep it at the new end instead of leaving
        // the old (larger) scrollY beyond the reduced max and exposing blank
        // space below the file.
        val toolbarFontChange = !zooming && !zoomRelayoutPending
        val savedScrollX = editor.scrollX
        val savedScrollY = editor.scrollY
        val savedAtDocumentEnd = toolbarFontChange && isAtDocumentEnd()
        fontSizeSp = next
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, next)
        if (toolbarFontChange) {
            editor.scrollTo(
                savedScrollX,
                if (savedAtDocumentEnd) Int.MAX_VALUE else savedScrollY
            )
            val token = ++fontSizeClampToken
            continueFontSizeClamp(savedScrollX, savedScrollY, savedAtDocumentEnd, 5, token)
        }
        gutter.invalidate()
        // During a pinch, TextView may relayout several times.  Revealing the
        // caret on each relayout is what used to snap a one-finger remainder
        // of the gesture to the top.  The final zoom pass restores an anchor
        // instead; toolbar/IME changes still reveal the caret normally.
        if (!zooming && !consumeTouchUntilUp && !imeClosePending) {
            editor.post { ensureSelectionVisible() }
        }
    }

    fun currentFontSize(): Float = fontSizeSp

    fun setImeBottomInset(bottomInset: Int) {
        val inset = bottomInset.coerceAtLeast(0)
        traceState("compose_ime_inset bottom=$inset")
        composeImeVisible = inset > 0
        composeImeInsetPx = inset
        imeBottomInsetPx = max(composeImeInsetPx, platformImeInsetPx)
        // On this device Compose reports bottom=0 even while adjustResize has
        // already shrunk the editor. Treat that measurable height reduction as
        // an active IME signal; otherwise the zero inset is interpreted as an
        // IME close and restores the pre-keyboard scroll during a pinch.
        val visible = composeImeVisible || platformImeVisible || isImeResizeActive()
        val stateChanged = imeVisible != visible
        updateImeVisibility(visible)
        // IME insets are animated.  Once visibility is already true, later
        // height samples still move the keyboard boundary and need another
        // frame of caret positioning; the old code only handled the first
        // non-zero sample.
        if (!stateChanged && imeVisible && !zooming && !zoomRelayoutPending) {
            editor.postOnAnimation { ensureSelectionVisible() }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // The editor remains full-width so native horizontal scrolling and
        // pinch anchoring keep their existing coordinates. At the largest
        // preview scale a transformed glyph can nevertheless be rasterized
        // across the fixed gutter edge. Clip the editor to the text region
        // and draw the gutter afterwards, making the row-number column an
        // actual occlusion boundary even with a hardware layer active.
        val frameTime = drawingTime
        val save = canvas.save()
        canvas.clipRect(gutterWidth.toFloat(), 0f, width.toFloat(), height.toFloat())
        drawChild(canvas, content, frameTime)
        canvas.restoreToCount(save)
        drawChild(canvas, gutter, frameTime)
    }

    private fun updatePlatformImeInsets(insets: WindowInsetsCompat) {
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        traceState("platform_ime_inset bottom=${imeInsets.bottom} visible=${insets.isVisible(WindowInsetsCompat.Type.ime())}")
        platformImeInsetPx = imeInsets.bottom.coerceAtLeast(0)
        platformImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) &&
            platformImeInsetPx > 0
        imeBottomInsetPx = max(composeImeInsetPx, platformImeInsetPx)
        updateImeVisibility(composeImeVisible || platformImeVisible || isImeResizeActive())
        if (imeVisible && !zooming && !zoomRelayoutPending) {
            editor.postOnAnimation { ensureSelectionVisible() }
        }
    }

    /**
     * Updates IME state from either Compose insets or adjustResize.  Some
     * vendor windows do not dispatch the final IME inset, while their resized
     * content height is still reliable; onSizeChanged calls this same method
     * as a fallback.
     */
    private fun updateImeVisibility(visible: Boolean) {
        if (imeVisible == visible) return
        traceState("ime_visibility from=$imeVisible to=$visible")
        if (visible) {
            imeClosePending = false
            imeCloseToken++
        }
        imeVisible = visible
        // Keep the real document padding unchanged.  The editor's native
        // scroll range remains exactly the document range, so dismissing the
        // IME can never leave a synthetic blank strip at the bottom.
        editor.setPadding(
            editor.paddingLeft,
            editor.paddingTop,
            editor.paddingRight,
            basePaddingBottom
        )
        if (!visible) {
            imeBottomInsetPx = 0
            imeRevealAnimationPending = false
            cancelImeRevealAnimation()
            // Keep the position reached while the keyboard was visible.  The
            // viewport grows during the close animation, so only clamp the
            // current value to the real TextView range; never jump back to a
            // saved pre-IME coordinate.
            imeClosePending = true
            val token = ++imeCloseToken
            clampEditorScroll()
            continueImeCloseClamp(8, token)
            return
        }
        // The first caret reveal after the IME opens should ease into place;
        // later inset frames can continue using the normal direct clamp.
        imeRevealAnimationPending = true
        editor.post { ensureSelectionVisible(animateReveal = true) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginVelocityTracking(event)
            MotionEvent.ACTION_POINTER_DOWN -> cancelVelocityTracking()
            MotionEvent.ACTION_MOVE -> addVelocityMovement(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> addVelocityMovement(event)
        }
        // Feed the detector exactly once per event, before interception decides
        // whether the EditText should continue receiving the gesture.
        scaleDetector.onTouchEvent(event)
        val scrollBefore = editor.scrollY
        val handled = super.dispatchTouchEvent(event)
        // EditText follows a finger at 1:1. The previous Compose editor felt
        // faster because its scroll state consumed a little more distance per
        // move. Add a small post-dispatch compensation based on the distance
        // EditText actually consumed; taps and selection moves that do not
        // scroll are left untouched, and multi-touch is excluded for zooming.
        if (event.actionMasked == MotionEvent.ACTION_MOVE &&
            event.pointerCount == 1 &&
            !zooming &&
            !consumeTouchUntilUp
        ) {
            val consumed = editor.scrollY - scrollBefore
            if (consumed != 0) {
                editor.scrollBy(0, (consumed * DIRECT_DRAG_BOOST).roundToInt())
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            if (event.actionMasked == MotionEvent.ACTION_UP && !zoomGestureActive) {
                finishVelocityTracking(allowFling = true)
            } else {
                cancelVelocityTracking()
            }
            zoomGestureActive = false
            consumeTouchUntilUp = false
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return false
        if (consumeTouchUntilUp || zooming || event.pointerCount >= 2) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ScaleGestureDetector already consumed this event in dispatchTouchEvent.
        return zooming || consumeTouchUntilUp || super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        dismissInput()
        cancelVelocityTracking()
        editorScroller.forceFinished(true)
        highlightJob?.cancel()
        highlightScope.cancel()
        stopDiagnosticTrace()
        super.onDetachedFromWindow()
    }

    /**
     * End the native editing session without changing the document. Clearing
     * focus as well as hiding the IME prevents Android from restoring this
     * EditText's keyboard when the activity returns from the background.
     */
    fun dismissInput() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        manager?.hideSoftInputFromWindow(
            editor.windowToken ?: windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
        editor.clearFocus()
        clearFocus()
        deferImeForTouch = false
        consumeTouchUntilUp = false
        zoomGestureActive = false
        cancelImeRevealAnimation()
        cancelVelocityTracking()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startDiagnosticTrace()
        ViewCompat.requestApplyInsets(this)
        if (!highlightScope.isActive) {
            highlightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scheduleHighlight()
        }
    }

    /**
     * Starts a small, bounded trace as soon as the native editor is attached.
     * Only timing/geometry/state is recorded; the JSON text itself is never
     * written to the trace. The file lives in the app-specific external
     * directory so it can be pulled without broad storage permissions.
     */
    private fun startDiagnosticTrace() {
        if (!traceEnabled) return
        if (traceWriter != null) return
        try {
            val directory = context.getExternalFilesDir("diagnostics") ?: context.filesDir
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, "zoom-trace.log")
            traceWriter = BufferedWriter(
                OutputStreamWriter(FileOutputStream(file, false), Charsets.UTF_8)
            )
            traceFile = file
            traceBytes = 0L
            traceEventId.set(0L)
            traceScaleSamples = 0
            traceState("session_start path=${file.absolutePath}")
        } catch (error: Exception) {
            Log.w(TRACE_TAG, "trace_start_failed=${error.javaClass.simpleName}")
            traceWriter = null
            traceFile = null
        }
    }

    private fun stopDiagnosticTrace() {
        if (!traceEnabled) return
        if (traceWriter == null) return
        traceState("session_end")
        synchronized(traceLock) {
            try {
                traceWriter?.flush()
                traceWriter?.close()
            } catch (_: Exception) {
                // Diagnostic output must never affect editor lifecycle.
            } finally {
                traceWriter = null
            }
        }
    }

    /** Returns the current trace path for callers/tools that want to export it. */
    fun diagnosticLogPath(): String? = traceFile?.absolutePath

    private fun traceState(event: String) {
        if (!traceEnabled) return
        val writerPresent = traceWriter != null
        if (!writerPresent) return
        val layout = editor.layout
        val viewportHeight = (editor.height - editor.totalPaddingTop - editor.totalPaddingBottom)
            .coerceAtLeast(0)
        val maxY = ((layout?.height ?: 0) - viewportHeight).coerceAtLeast(0)
        val id = traceEventId.incrementAndGet()
        val line = buildString(512) {
            append(SystemClock.uptimeMillis())
            append(" #").append(id)
            append(" ").append(event)
            append(" ime=").append(imeVisible)
            append(" inset=").append(imeBottomInsetPx)
            append(" viewH=").append(editor.height)
            append(" layoutH=").append(layout?.height ?: -1)
            append(" scrollY=").append(editor.scrollY)
            append(" maxY=").append(maxY)
            append(" zoom=").append(zooming)
            append(" pending=").append(zoomRelayoutPending)
            append(" scale=").append("%.4f".format(java.util.Locale.US, gestureScale))
            append(" viewScale=").append("%.4f".format(java.util.Locale.US, content.scaleY))
            append(" transY=").append("%.2f".format(java.util.Locale.US, content.translationY))
            append(" focusY=").append("%.2f".format(java.util.Locale.US, gestureLastFocusY))
            append(" anchor=").append(zoomAnchorOffset)
        }.replace('\n', ' ')
        Log.i(TRACE_TAG, line)
        val bytes = line.toByteArray(Charsets.UTF_8).size + 1
        traceScope.launch {
            synchronized(traceLock) {
                val writer = traceWriter ?: return@launch
                if (traceBytes >= TRACE_MAX_BYTES) return@launch
                try {
                    writer.write(line)
                    writer.newLine()
                    writer.flush()
                    traceBytes += bytes
                } catch (_: Exception) {
                    // A diagnostic failure must not affect editing.
                }
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (height != oldHeight || width != oldWidth) {
            traceState("size_changed ${oldWidth}x${oldHeight}->${width}x${height}")
        }
        // adjustResize normally arrives together with WindowInsets.ime, but a
        // few Android/vendor combinations omit the final inset callback.  A
        // large viewport delta is an unambiguous keyboard transition for this
        // full-screen editor, so use it as a fallback state signal.
        if (oldHeight > 0 && height != oldHeight) {
            val keyboardDelta = dp(80f)
            when {
                height < oldHeight - keyboardDelta && !imeVisible -> updateImeVisibility(true)
                height > oldHeight + keyboardDelta && imeVisible -> updateImeVisibility(false)
            }
        }
        if (!imeVisible && height > 0) {
            lastImeHiddenHeight = height
        }
        if (width != oldWidth || height != oldHeight) {
            // A resize caused by closing the IME can leave one stale scroll
            // value until TextView's next layout pass. Clamp on the next two
            // frames so the real document end is restored without requiring a
            // manual drag.
            postOnAnimation {
                clampEditorScroll()
                if (!imeVisible && !imeClosePending) ensureSelectionVisible()
                postOnAnimation { clampEditorScroll() }
            }
        }
    }

    private fun clampTemporaryTransform() {
        if (width <= 0 || height <= 0) return
        val scale = gestureScale
        // Keep the transformed viewport from exposing content outside the
        // editor. 缩小方向同样走硬件层预览：层比视口小，任何平移都会立刻
        // 暴露层外的空白，因此 scale < 1 时把平移钳到 0，位置误差统一交给
        // 手势结束后的锚点恢复逻辑处理。
        var txMin: Float
        var txMax: Float
        var tyMin: Float
        var tyMax: Float
        if (scale >= 1f) {
            txMin = -(width - gesturePivotX) * (scale - 1f)
            txMax = gesturePivotX * (scale - 1f)
            tyMin = -(height - gesturePivotY) * (scale - 1f)
            tyMax = gesturePivotY * (scale - 1f)
        } else {
            txMin = 0f
            txMax = 0f
            tyMin = 0f
            tyMax = 0f
        }
        val viewportTyMin = tyMin
        val viewportTyMax = tyMax

        // The layer is viewport-sized, while the last line can end a few
        // pixels above the viewport bottom because of EditText padding.  At
        // the document end that small difference gets multiplied by the
        // pinch scale and becomes a visible blank strip.  Tighten only the
        // bottom bound using the actual TextView layout; this does not add
        // scrollable space and is skipped for documents shorter than the
        // viewport, where a natural empty area is unavoidable.
        val textLayout = editor.layout
        val viewportHeight = (editor.height - editor.totalPaddingTop - editor.totalPaddingBottom)
            .coerceAtLeast(0)
        if (scale >= 1f && textLayout != null && textLayout.height > viewportHeight) {
            val lastLine = (textLayout.lineCount - 1).coerceAtLeast(0)
            val documentBottom = editor.paddingTop + textLayout.getLineBottom(lastLine) - editor.scrollY
            val transformedBottomWithoutTranslation =
                gesturePivotY + (documentBottom - gesturePivotY) * scale
            val documentTyMin = height - transformedBottomWithoutTranslation
            tyMin = max(tyMin, documentTyMin)
        }

        // Normally the viewport bounds and document-end bound overlap.  In
        // the few frames where line-bottom rounding makes them disjoint,
        // preserve the document-end constraint for an end-anchored pinch;
        // falling back to the viewport-only range is what allowed a thin
        // blank strip to appear at the bottom.  Short documents never enter
        // the document-aware branch above and retain their natural empty
        // viewport area.
        if (tyMin > tyMax) {
            if (zoomAnchorAtDocumentEnd && textLayout != null && textLayout.height > viewportHeight) {
                tyMax = tyMin
            } else {
                tyMin = viewportTyMin
                tyMax = viewportTyMax
            }
        }
        // The gutter is fixed at the left edge of the parent. Prevent the
        // first text column from crossing its right boundary while the whole
        // editor viewport is being translated/scaled horizontally.
        if (scale >= 1f) {
            val textStartX = editor.paddingLeft.toFloat() - editor.scrollX.toFloat()
            val transformedTextStart =
                gesturePivotX + (textStartX - gesturePivotX) * scale
            txMin = max(txMin, gutterWidth.toFloat() - transformedTextStart)
        }
        // A document that was already horizontally scrolled can make the
        // text/gutter constraint narrower than the viewport-only range. Keep
        // the interval valid; the native horizontal scroll position remains
        // the authoritative content clamp in that corner case.
        if (txMin > txMax) txMin = txMax
        content.translationX = content.translationX.coerceIn(txMin, txMax)
        content.translationY = content.translationY.coerceIn(tyMin, tyMax)
    }

    /** Keep line numbers vertically aligned with the transformed text while
     * leaving their horizontal gutter fixed and non-pannable. */
    private fun syncGutterPreviewTransform() {
        if (!zooming) {
            resetGutterPreviewTransform()
            return
        }
        gutter.pivotX = 0f
        gutter.pivotY = gesturePivotY
        gutter.scaleX = 1f
        gutter.scaleY = content.scaleY
        gutter.translationX = 0f
        gutter.translationY = content.translationY
    }

    private fun resetGutterPreviewTransform() {
        gutter.pivotX = 0f
        gutter.pivotY = 0f
        gutter.scaleX = 1f
        gutter.scaleY = 1f
        gutter.translationX = 0f
        gutter.translationY = 0f
    }

    private fun beginVelocityTracking(event: MotionEvent) {
        cancelVelocityTracking()
        cancelImeRevealAnimation()
        if (zooming || consumeTouchUntilUp || event.pointerCount != 1) return
        flingActive = false
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
        velocityTracking = true
        dragDistanceY = 0f
        lastTouchY = event.y
        editorScroller.forceFinished(true)
    }

    private fun addVelocityMovement(event: MotionEvent) {
        if (!velocityTracking || event.pointerCount != 1) return
        velocityTracker?.addMovement(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            dragDistanceY += abs(event.y - lastTouchY)
            lastTouchY = event.y
        }
    }

    private fun finishVelocityTracking(allowFling: Boolean) {
        val tracker = velocityTracker ?: return
        if (allowFling && velocityTracking && !zoomGestureActive && dragDistanceY >
            ViewConfiguration.get(context).scaledTouchSlop
        ) {
            tracker.computeCurrentVelocity(1000)
            val velocityY = tracker.yVelocity
            val minVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
            if (abs(velocityY) >= minVelocity) {
                startFling(-velocityY)
            }
        }
        tracker.recycle()
        velocityTracker = null
        velocityTracking = false
        dragDistanceY = 0f
        lastTouchY = 0f
    }

    private fun cancelVelocityTracking() {
        velocityTracker?.recycle()
        velocityTracker = null
        velocityTracking = false
        dragDistanceY = 0f
        lastTouchY = 0f
    }

    private fun startFling(velocityY: Float) {
        val layout = editor.layout ?: return
        val viewportHeight = (editor.height - editor.totalPaddingTop - editor.totalPaddingBottom)
            .coerceAtLeast(0)
        val maxY = (layout.height - viewportHeight).coerceAtLeast(0)
        if (maxY <= 0) return
        flingActive = true
        editor.setCursorVisible(false)
        editorScroller.fling(
            editor.scrollX,
            editor.scrollY,
            0,
            velocityY.roundToInt(),
            editor.scrollX,
            editor.scrollX,
            0,
            maxY
        )
        editor.postInvalidateOnAnimation()
    }

    private fun applyThemeColors() {
        val bg = if (darkTheme) backgroundDark else backgroundLight
        val text = if (darkTheme) textDark else textLight
        setBackgroundColor(bg)
        content.setBackgroundColor(bg)
        editor.setTextColor(text)
        editor.setHintTextColor(text)
        gutter.setColors(
            if (darkTheme) lineBackgroundDark else lineBackgroundLight,
            if (darkTheme) lineDark else lineLight
        )
    }

    private fun scheduleHighlight() {
        val snapshot = editor.text.toString()
        val generation = highlightGeneration.incrementAndGet()
        highlightJob?.cancel()
        if (snapshot.length > MAX_HIGHLIGHT_CHARS) {
            clearHighlightSpans()
            return
        }
        val dark = darkTheme
        highlightJob = highlightScope.launch {
            try {
                delay(100)
                val spans = parseJsonColors(snapshot, dark)
                ensureActive()
                post {
                    if (highlightGeneration.get() != generation || editor.text.toString() != snapshot) return@post
                    applyHighlightSpans(spans)
                }
            } catch (_: CancellationException) {
                // A newer edit superseded this parse.
            }
        }
    }

    private fun clearHighlightSpans() {
        val editable = editor.text
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .forEach(editable::removeSpan)
    }

    private fun applyHighlightSpans(spans: List<ColorSpan>) {
        val editable = editor.text
        suppressTextCallback = true
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .forEach(editable::removeSpan)
        spans.forEach { span ->
            editable.setSpan(
                ForegroundColorSpan(span.color),
                span.start,
                span.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        suppressTextCallback = false
        editor.invalidate()
    }

    private fun animateEditorScrollBy(deltaY: Int) {
        if (deltaY == 0) return
        val nextTarget = editor.scrollY + deltaY
        val running = imeRevealAnimator?.isRunning == true
        if (running) {
            // Window/inset callbacks can arrive several times during the IME
            // animation. Move only the destination; restarting from the
            // current frame is what caused the earlier visible judder.
            imeRevealTargetY = nextTarget
            return
        }
        imeRevealStartY = editor.scrollY
        imeRevealTargetY = nextTarget
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = IME_REVEAL_ANIMATION_MS.toLong()
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val fraction = it.animatedFraction
                val y = (imeRevealStartY +
                    (imeRevealTargetY - imeRevealStartY) * fraction).roundToInt()
                editor.scrollTo(editor.scrollX, y)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (imeRevealAnimator === this@apply) {
                        imeRevealAnimator = null
                    }
                }
            })
        }
        imeRevealAnimator = animator
        animator.start()
    }

    private fun cancelImeRevealAnimation() {
        imeRevealAnimator?.cancel()
        imeRevealAnimator = null
    }

    private fun ensureSelectionVisible(animateReveal: Boolean = false) {
        if (zooming || zoomRelayoutPending || consumeTouchUntilUp || imeClosePending ||
            !editor.hasFocus() || editor.layout == null
        ) return
        val shouldAnimate = animateReveal || imeRevealAnimationPending ||
            imeRevealAnimator?.isRunning == true
        val offset = editor.selectionStart.coerceIn(0, editor.text.length)
        val line = editor.layout.getLineForOffset(offset)
        val top = editor.paddingTop + editor.layout.getLineTop(line)
        val bottom = editor.paddingTop + editor.layout.getLineBottom(line)
        // Do not move the document merely because the user tapped near the
        // bottom while the keyboard is hidden; that makes the caret appear
        // away from the finger.  Once the IME is visible, a virtual bottom
        // margin keeps the last line above the keyboard without blank rows.
        val margin = if (imeVisible) imeExtraMargin else 0
        val viewportTop = editor.scrollY + editor.paddingTop
        val rootViewportBottom = editor.scrollY + editor.height - editor.paddingBottom
        // With adjustResize the editor height already excludes the IME.  If
        // the window has not resized yet, use the actual inset to pre-position
        // the caret before Android's automatic bring-into-view pass runs.
        val resizedForIme = lastImeHiddenHeight > 0 &&
            lastImeHiddenHeight > editor.height + dp(80f)
        val insetViewportBottom = if (
            imeVisible &&
            imeBottomInsetPx > 0 &&
            !resizedForIme
        ) {
            editor.scrollY + editor.height - imeBottomInsetPx
        } else {
            rootViewportBottom
        }
        val actualViewportBottom = min(rootViewportBottom, insetViewportBottom)
        // Apply the breathing room only as a virtual target.  Native
        // scrollTo() clamps at the real document bounds, which is intentional:
        // at the absolute last line we prefer no blank rows over inventing a
        // second content range just to increase the gap above the keyboard.
        val targetBottom = actualViewportBottom - margin
        when {
            bottom > targetBottom -> {
                val delta = bottom - targetBottom
                traceState("selection_reveal_down delta=$delta margin=$margin animated=$shouldAnimate")
                if (shouldAnimate) animateEditorScrollBy(delta) else editor.scrollBy(0, delta)
                imeRevealAnimationPending = false
            }
            top < viewportTop -> {
                val delta = top - viewportTop
                traceState("selection_reveal_up delta=$delta margin=$margin animated=$shouldAnimate")
                if (shouldAnimate) animateEditorScrollBy(delta) else editor.scrollBy(0, delta)
                imeRevealAnimationPending = false
            }
        }
        // The editor itself owns the complete scroll range, so asking the
        // Compose parent to reveal the same rectangle can add a second small
        // pan after we already positioned the caret.  Direct scrolling above
        // is sufficient with adjustResize and avoids that extra drift.
    }

    private fun dp(value: Float): Int = (value * density + 0.5f).toInt()

    /**
     * Fallback IME signal for adjustResize windows whose inset dispatch is
     * missing or temporarily reports zero during the IME animation.
     */
    private fun isImeResizeActive(): Boolean {
        val baseline = lastImeHiddenHeight
        val current = editor.height
        return baseline > 0 && current > 0 && current < baseline - dp(80f)
    }

    private fun continueImeCloseClamp(remainingFrames: Int, token: Int) {
        editor.postOnAnimation {
            if (token != imeCloseToken) return@postOnAnimation
            if (!imeVisible) {
                // Keep the live position.  A viewport expansion can lower
                // the legal max scroll at the document end; native scrollTo
                // performs that clamp without restoring an old coordinate.
                clampEditorScroll()
                gutter.invalidate()
            }
            if (remainingFrames > 0) {
                continueImeCloseClamp(remainingFrames - 1, token)
            } else {
                // Keep the guard active during the tail of the resize
                // animation so selection callbacks cannot move the caret
                // while the window is still settling.
                editor.postDelayed({
                    if (token == imeCloseToken && !imeVisible) {
                        clampEditorScroll()
                        imeClosePending = false
                    }
                }, 120L)
            }
        }
    }

    private fun continueFontSizeClamp(
        x: Int,
        y: Int,
        atDocumentEnd: Boolean,
        remainingFrames: Int,
        token: Int
    ) {
        editor.postOnAnimation {
            if (token != fontSizeClampToken || zooming || zoomRelayoutPending) return@postOnAnimation
            editor.scrollTo(x, if (atDocumentEnd) Int.MAX_VALUE else y)
            gutter.invalidate()
            if (remainingFrames > 0) {
                continueFontSizeClamp(x, y, atDocumentEnd, remainingFrames - 1, token)
            }
        }
    }

    private fun clampEditorScroll() {
        editor.scrollTo(editor.scrollX, editor.scrollY)
        gutter.invalidate()
    }

    private fun captureZoomAnchor(focusX: Float, focusY: Float) {
        val layout = editor.layout ?: run {
            zoomAnchorOffset = -1
            return
        }
        val contentY = (editor.scrollY + focusY - editor.paddingTop).coerceAtLeast(0f)
        val line = layout.getLineForVertical(contentY.toInt()).coerceIn(0, layout.lineCount - 1)
        val contentX = editor.scrollX + focusX - editor.paddingLeft
        zoomAnchorOffset = layout.getOffsetForHorizontal(line, contentX)
            .coerceIn(0, editor.text.length)
        zoomAnchorScreenY = focusY.roundToInt()
        val lineTop = layout.getLineTop(line)
        val lineHeight = (layout.getLineBottom(line) - lineTop).coerceAtLeast(1)
        zoomAnchorLineFraction =
            ((contentY - lineTop.toFloat()) / lineHeight.toFloat()).coerceIn(0f, 1f)
        zoomAnchorScrollX = editor.scrollX
        traceState("anchor_begin focus=$focusX,$focusY line=$line")
    }

    /**
     * Capture the logical point under the final scale focus while the
     * temporary hardware transform is still installed.
     */
    private fun captureZoomPreviewAnchor(focusX: Float, focusY: Float) {
        val layout = editor.layout ?: return
        if (layout.lineCount <= 0) return
        val scale = content.scaleX.takeIf { it > 0.001f } ?: 1f
        val localX = gesturePivotX + (focusX - gesturePivotX - content.translationX) / scale
        val localY = gesturePivotY + (focusY - gesturePivotY - content.translationY) / scale
        val documentY = (editor.scrollY + localY - editor.paddingTop).coerceAtLeast(0f)
        val line = layout.getLineForVertical(documentY.toInt()).coerceIn(0, layout.lineCount - 1)
        val documentX = editor.scrollX + localX - editor.paddingLeft
        zoomAnchorOffset = layout.getOffsetForHorizontal(line, documentX)
            .coerceIn(0, editor.text.length)
        zoomAnchorScreenY = focusY.roundToInt()
        val lineTop = layout.getLineTop(line)
        val lineHeight = (layout.getLineBottom(line) - lineTop).coerceAtLeast(1)
        zoomAnchorLineFraction =
            ((documentY - lineTop.toFloat()) / lineHeight.toFloat()).coerceIn(0f, 1f)
        // Keep horizontal panning performed by a moving pinch focus. The
        // native EditText clamps this to its actual content range.
        zoomAnchorScrollX =
            (documentX - (focusX - editor.paddingLeft)).roundToInt().coerceAtLeast(0)
        traceState("anchor_preview focus=$focusX,$focusY line=$line documentY=$documentY")
    }

    private fun restoreZoomLayoutAnchor() {
        val layout = editor.layout ?: return
        if (layout.lineCount <= 0) return
        if (zoomAnchorAtDocumentEnd) {
            editor.scrollTo(zoomAnchorScrollX, Int.MAX_VALUE)
        } else {
            val line = layout.getLineForOffset(zoomAnchorOffset.coerceAtMost(editor.text.length))
            val lineTop = layout.getLineTop(line)
            val lineHeight = (layout.getLineBottom(line) - lineTop).coerceAtLeast(1)
            val lineDelta = (lineHeight * zoomAnchorLineFraction).roundToInt()
            val targetY = editor.paddingTop + lineTop + lineDelta - zoomAnchorScreenY
            editor.scrollTo(zoomAnchorScrollX, targetY)
        }
        traceState("anchor_restore")
        gutter.invalidate()
    }

    private fun restoreZoomAnchor() {
        val offset = zoomAnchorOffset
        if (offset < 0) return
        // setTextSize() requests a layout.  Waiting for the next frame keeps
        // the same logical line at the same screen coordinate after relayout.
        editor.postOnAnimation {
            if (editor.layout != null && editor.layout.lineCount > 0) {
                restoreZoomLayoutAnchor()
                if (zoomAnchorAtDocumentEnd) {
                    // When the gesture began at the end, preserve the end
                    // anchor instead of restoring the finger line. The
                    // latter can be below the new max scroll after a font
                    // resize and leaves a visible blank area underneath.
                    // Allow the TextView a few extra frames to settle its
                    // line layout before the final end clamp, especially on
                    // slower devices where a pinch can otherwise leave one
                    // stale blank frame at the bottom.
                    restoreZoomDocumentEnd(8)
                }
            }
        }
    }

    private fun restoreZoomDocumentEnd(remainingFrames: Int) {
        editor.postOnAnimation {
            // Recompute the real maximum on every frame.  This is important
            // while the IME is visible: the reduced viewport and TextView's
            // relayout can settle on different frames after setTextSize().
            editor.scrollTo(zoomAnchorScrollX, Int.MAX_VALUE)
            gutter.invalidate()
            if (remainingFrames > 0) {
                restoreZoomDocumentEnd(remainingFrames - 1)
            }
        }
    }

    private fun continueZoomBoundaryClamp(remainingFrames: Int) {
        editor.postOnAnimation {
            if (!zooming) {
                editor.scrollTo(editor.scrollX, editor.scrollY)
                gutter.invalidate()
            }
            if (remainingFrames > 0) {
                continueZoomBoundaryClamp(remainingFrames - 1)
            }
        }
    }

    private fun isAtDocumentEnd(): Boolean {
        val textLayout = editor.layout ?: return false
        val viewportHeight = (editor.height - editor.totalPaddingTop - editor.totalPaddingBottom)
            .coerceAtLeast(0)
        val maxY = (textLayout.height - viewportHeight).coerceAtLeast(0)
        // A small tolerance covers the final pixel rounding performed by
        // TextView while a fling is settling at the end.
        return editor.scrollY >= maxY - dp(2f)
    }

    private data class ColorSpan(val start: Int, val end: Int, val color: Int)

    private fun parseJsonColors(text: String, dark: Boolean): List<ColorSpan> {
        val stringColor = if (dark) Color.rgb(206, 145, 120) else Color.rgb(4, 81, 165)
        val numberColor = if (dark) Color.rgb(181, 206, 168) else Color.rgb(9, 134, 88)
        val keyColor = if (dark) Color.rgb(156, 220, 254) else Color.rgb(136, 18, 128)
        val boolColor = if (dark) Color.rgb(86, 156, 214) else Color.rgb(38, 127, 153)
        val nullColor = if (dark) Color.rgb(244, 71, 71) else Color.rgb(229, 20, 0)
        val braceColor = if (dark) Color.YELLOW else Color.rgb(128, 0, 0)
        val punctColor = if (dark) Color.rgb(128, 128, 128) else Color.rgb(160, 160, 160)
        val result = ArrayList<ColorSpan>(min(text.length / 4, 4096))
        var index = 0
        while (index < text.length) {
            if ((index and 4095) == 0) highlightScope.ensureActive()
            when {
                text[index] == '"' -> {
                    val start = index++
                    while (index < text.length && !(text[index] == '"' && text[index - 1] != '\\')) index++
                    if (index < text.length) index++
                    var lookahead = index
                    while (lookahead < text.length && text[lookahead].isWhitespace()) lookahead++
                    val isKey = lookahead < text.length && text[lookahead] == ':'
                    result += ColorSpan(start, index, if (isKey) keyColor else stringColor)
                }
                text[index] == '-' || text[index].isDigit() -> {
                    val start = index
                    if (text[index] == '-') index++
                    while (index < text.length && (text[index].isDigit() || text[index] == '.' || text[index] == 'e' || text[index] == 'E' || text[index] == '+' || text[index] == '-')) {
                        if ((text[index] == '+' || text[index] == '-') && index > start + 1 && text[index - 1] != 'e' && text[index - 1] != 'E') break
                        index++
                    }
                    result += ColorSpan(start, index, numberColor)
                }
                text.startsWith("true", index) -> { result += ColorSpan(index, index + 4, boolColor); index += 4 }
                text.startsWith("false", index) -> { result += ColorSpan(index, index + 5, boolColor); index += 5 }
                text.startsWith("null", index) -> { result += ColorSpan(index, index + 4, nullColor); index += 4 }
                text[index] == '{' || text[index] == '}' || text[index] == '[' || text[index] == ']' -> {
                    result += ColorSpan(index, index + 1, braceColor)
                    index++
                }
                text[index] == ':' || text[index] == ',' -> {
                    result += ColorSpan(index, index + 1, punctColor)
                    index++
                }
                else -> index++
            }
        }
        return result
    }

    private inner class EditorEditText(context: Context) : EditText(context) {
        // TextView normally calls bringPointIntoView() after every selection
        // change.  With adjustResize that platform pass runs after our own
        // IME-aware positioning and moves the caret a second time (usually
        // to only one or two lines above the keyboard).  Selection visibility
        // is handled by ensureSelectionVisible(), so suppress the duplicate
        // parent/window request and keep the caret at the intended position.
        override fun bringPointIntoView(offset: Int): Boolean = false

        override fun requestRectangleOnScreen(rectangle: Rect, immediate: Boolean): Boolean = false

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            // A pinch-out changes the text layout over several frames.  If
            // the gesture started at the document end, the old scrollY can
            // be larger than the newly reduced max after each relayout. Clamp
            // during the relayout itself, not only in onScaleEnd(), so no
            // transient or final blank strip can appear below the last line.
            if (zoomRelayoutPending && !zooming && zoomAnchorOffset >= 0) {
                traceState("editor_layout_restore")
                // setTextSize() can rebuild Layout and reset scrollY before
                // the new line metrics are available. Restore the logical
                // pinch anchor on every such pass, including the commit pass
                // after a hardware-layer pinch, so the document never jumps
                // to line 1 while the new size is being installed.
                restoreZoomLayoutAnchor()
            }
        }

        override fun computeScroll() {
            // Drive the same Scroller that is used for direct EditText
            // scrolling.  Calling postInvalidateOnAnimation() keeps the
            // fling frame-paced without introducing a second animation loop.
            if (editorScroller.computeScrollOffset()) {
                scrollTo(editorScroller.currX, editorScroller.currY)
                postInvalidateOnAnimation()
            } else if (flingActive) {
                flingActive = false
                if (!touchScrollActive && !zooming) setCursorVisible(true)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchScrollActive = false
                    touchMoved = false
                    touchDownX = event.x
                    touchDownY = event.y
                    touchSelectionStart = selectionStart.coerceAtLeast(0)
                    touchSelectionEnd = selectionEnd.coerceAtLeast(0)
                    // Do not let EditText open the IME before we know whether
                    // this is a tap or a scroll. With an existing selection,
                    // a downward/upward drag can otherwise be interpreted as
                    // a click for one frame and trigger keyboard panning.
                    deferImeForTouch = !imeVisible
                    if (deferImeForTouch) showSoftInputOnFocus = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!touchMoved) {
                        val dx = event.x - touchDownX
                        val dy = event.y - touchDownY
                        val slop = ViewConfiguration.get(context).scaledTouchSlop
                        if (dx * dx + dy * dy > slop * slop) {
                            touchMoved = true
                            touchScrollActive = true
                            // Scrolling should not continuously place the
                            // caret under the moving finger. Hide the caret
                            // for the drag and keep the pre-drag selection.
                            setCursorVisible(false)
                            setSelection(touchSelectionStart, touchSelectionEnd)
                            if (deferImeForTouch) hideImeAfterScroll()
                        }
                    }
                }
            }

            val handled = super.onTouchEvent(event)
            if (touchMoved) {
                // TextView may update its selection during the same event
                // that scrolls. Restore the original range after dispatch so
                // the caret cannot flicker or jump with every move sample.
                if (selectionStart != touchSelectionStart || selectionEnd != touchSelectionEnd) {
                    setSelection(touchSelectionStart, touchSelectionEnd)
                }
            }
            // Handle both taps and drags.  The previous implementation kept
            // this block under `if (touchMoved)`, leaving a deferred tap in
            // the disabled-IME state forever because a tap never set that
            // flag.  Finalising every terminal event keeps normal taps able
            // to open the keyboard while still cancelling it for drags.
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                val wasTap = !touchMoved && event.actionMasked == MotionEvent.ACTION_UP
                touchScrollActive = false
                if (!flingActive) setCursorVisible(true)
                finishDeferredIme(wasTap)
                invalidate()
            }
            return handled
        }

        private fun hideImeAfterScroll() {
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            manager?.hideSoftInputFromWindow(windowToken, 0)
        }

        private fun finishDeferredIme(showForTap: Boolean) {
            if (!deferImeForTouch) return
            deferImeForTouch = false
            showSoftInputOnFocus = true
            if (showForTap) {
                post {
                    if (!hasFocus()) requestFocus()
                    if (hasFocus() && !imeVisible) {
                        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                            as? InputMethodManager
                        manager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }

        override fun scrollTo(x: Int, y: Int) {
            val textLayout = layout
            // TextView clears its Layout briefly while setTextSize() is
            // rebuilding it. During a bottom-anchored pinch, clamping against
            // that transient null layout would turn any scroll request into
            // y=0 and visibly jump to the top. Keep the previous scroll until
            // onLayout() has a real document height and performs the exact
            // boundary/anchor restore.
            if (textLayout == null && (zooming || zoomRelayoutPending)) {
                return
            }
            val viewportHeight = (height - totalPaddingTop - totalPaddingBottom).coerceAtLeast(0)
            val maxY = if (textLayout == null) 0 else
                (textLayout.height - viewportHeight).coerceAtLeast(0)
            // TextView's fling and our direct-drag compensation both funnel
            // through scrollTo().  Clamping here prevents overscroll/bounce and
            // stale IME padding from exposing a blank strip at either end.
            super.scrollTo(x.coerceAtLeast(0), y.coerceIn(0, maxY))
        }

        override fun onSelectionChanged(selStart: Int, selEnd: Int) {
            super.onSelectionChanged(selStart, selEnd)
            if (!initializationComplete) return
            gutter.invalidate()
            if (!zooming && !consumeTouchUntilUp && !touchScrollActive) {
                post { ensureSelectionVisible() }
            }
        }
    }

    private inner class GutterView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
        private val backgroundPaint = Paint()
        private var gutterBackground = lineBackgroundLight
        private var numberColor = lineLight

        fun setColors(background: Int, text: Int) {
            gutterBackground = background
            numberColor = text
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            backgroundPaint.color = gutterBackground
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            val layout = editor.layout ?: return
            if (layout.lineCount == 0) return
            val scrollY = editor.scrollY
            // 逐行取 Layout 的真实基线。旧实现用 editor.lineHeight 这个"标称
            // 行高"乘以行号来估位置，而 TextView 的实际行高受折行、
            // includeFontPadding 与行距影响，误差会随行号线性累积——越往下
            // 行号越对不上正文。
            val viewTop = (scrollY - editor.totalPaddingTop).coerceAtLeast(0)
            val firstLine = layout.getLineForVertical(viewTop)
            val lastLine = layout.getLineForVertical(
                (viewTop + height).coerceAtMost(layout.height),
            )
            paint.color = numberColor
            paint.textSize = editor.textSize * 0.85f
            // The gutter stays a fixed-width column while the editor can be
            // enlarged up to 32sp.  At that size a three/four digit number
            // can be wider than 40dp; right-aligning it would clip its first
            // digit at the left edge (for example, rendering "52" instead of
            // "352"). Fit the largest line-number sample to the available
            // width without changing the document's text size.
            val digitCount = max(1, layout.lineCount).toString().length
            val availableWidth = (width - dp(8f)).coerceAtLeast(dp(8f)).toFloat()
            val sampleWidth = paint.measureText("8".repeat(digitCount))
            if (sampleWidth > availableWidth && sampleWidth > 0f) {
                paint.textSize *= availableWidth / sampleWidth
            }
            paint.textAlign = Paint.Align.RIGHT
            val right = width - dp(4f)
            for (line in firstLine..lastLine) {
                val baseline = layout.getLineBaseline(line) +
                    editor.totalPaddingTop - scrollY
                if (baseline + paint.ascent() > height || baseline + paint.descent() < 0) continue
                canvas.drawText((line + 1).toString(), right.toFloat(), baseline.toFloat(), paint)
            }
        }
    }
}
