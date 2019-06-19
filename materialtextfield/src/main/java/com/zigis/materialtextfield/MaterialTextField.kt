package com.zigis.materialtextfield

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.animation.AnimatorSet
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.animation.AccelerateInterpolator
import androidx.core.content.ContextCompat
import android.widget.TextView
import androidx.annotation.ColorInt
import java.lang.Math.max
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.text.*
import android.view.MotionEvent
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.zigis.materialtextfield.custom.AnimatedRectF
import com.zigis.materialtextfield.custom.WrapDrawable

open class MaterialTextField : EditText {

    //  Setting vars

    private var defaultHintColor = Color.parseColor("#7C8894")
    private var activeHintColor = Color.parseColor("#019CDE")
    private var defaultUnderlineColor = Color.parseColor("#D0D4D5")
    private var activeUnderlineColor = Color.parseColor("#019CDE")
    private var cursorDrawableColor = Color.parseColor("#019CDE")
    private var clearButtonColor = Color.parseColor("#7B8590")
    private var errorColor = Color.parseColor("#F24E4E")
    private var isClearEnabled = true

    //  Private vars

    private var errorText: String? = null

    private var originalHeight = 0
    private var originalPaddingBottom = 0

    private var clearButton: Bitmap? = null
    private var clearButtonSize = 0f
    private var isClearButtonClickActive = false
    private var isClearButtonTouchActive = false

    private var staticUnderline: RectF? = null
    private var errorUnderline: RectF? = null
    private var animatedUnderline: AnimatedRectF? = null
    private var cursorDrawable: WrapDrawable? = null

    private var warningIcon: Bitmap? = null
    private var warningIconSize = 0f

    private var errorSpacing = dp(15)
    private var clearButtonSpacing = dp(14)
    private var underlineHeight = dp(2)
    private var bottomUnderlineOffset = dp(3)
    private var isUnderlineAnimating = false

    private var underlineBackgroundPaint = Paint(ANTI_ALIAS_FLAG)
    private var underlineForegroundPaint = Paint(ANTI_ALIAS_FLAG)
    private var floatingHintPaint = TextPaint(ANTI_ALIAS_FLAG)
    private var errorPaint = TextPaint(ANTI_ALIAS_FLAG)
    private var clearIconPaint = Paint(ANTI_ALIAS_FLAG)

    private var isFocusPending = false
    private var floatingLabelFraction = 0f
    private var errorSpaceFraction = 0f

    private var innerFocusChangeListener: OnFocusChangeListener? = null

    private var floatingLabelAnimator: ObjectAnimator? = null
        get() {
            if (field == null) {
                field = ObjectAnimator.ofFloat(this, "floatingLabelFraction", 0f, 1f)
            }
            field?.duration = 250
            return field
        }
    private var errorSpaceAnimator: ObjectAnimator? = null
        get() {
            if (field == null) {
                field = ObjectAnimator.ofFloat(this, "errorSpaceFraction", 0f, 1f)
            }
            field?.duration = 250
            return field
        }

    //  Constructors

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    //  Initialization

    private fun init(context: Context, attrs: AttributeSet?) {
        if (isInEditMode) return

        val styledAttributes = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.MaterialTextField,
            0,
            0
        )

        defaultHintColor = styledAttributes.getColor(R.styleable.MaterialTextField_defaultHintColor, defaultHintColor)
        activeHintColor = styledAttributes.getColor(R.styleable.MaterialTextField_activeHintColor, activeHintColor)
        defaultUnderlineColor = styledAttributes.getColor(R.styleable.MaterialTextField_defaultUnderlineColor, defaultUnderlineColor)
        activeUnderlineColor = styledAttributes.getColor(R.styleable.MaterialTextField_activeUnderlineColor, activeUnderlineColor)
        cursorDrawableColor = styledAttributes.getColor(R.styleable.MaterialTextField_cursorDrawableColor, cursorDrawableColor)
        clearButtonColor = styledAttributes.getColor(R.styleable.MaterialTextField_clearButtonColor, clearButtonColor)
        errorColor = styledAttributes.getColor(R.styleable.MaterialTextField_errorColor, errorColor)
        isClearEnabled = styledAttributes.getBoolean(R.styleable.MaterialTextField_isClearEnabled, isClearEnabled)

        context.getDrawable(R.drawable.ic_clear)?.let {
            clearButton = drawableToBitmap(it)
            clearButtonSize = clearButton?.height?.toFloat() ?: 0f
            setPadding(
                paddingStart,
                paddingTop,
                paddingEnd + clearButtonSize.toInt() + (clearButtonSpacing * 1.5f).toInt(),
                paddingBottom
            )
        }
        context.getDrawable(R.drawable.ic_warning)?.let {
            warningIcon = drawableToBitmap(it)
            warningIconSize = warningIcon?.height?.toFloat() ?: 0f
        }

        originalPaddingBottom = paddingBottom

        setDefaultSettings()

        clearIconPaint.colorFilter = PorterDuffColorFilter(clearButtonColor, PorterDuff.Mode.SRC_IN)

        errorPaint.color = errorColor
        errorPaint.colorFilter = PorterDuffColorFilter(errorColor, PorterDuff.Mode.SRC_IN)
        errorPaint.textSize = dp(12)
        underlineBackgroundPaint.color = defaultUnderlineColor
        underlineForegroundPaint.color = activeUnderlineColor
        floatingHintPaint.textSize = dp(12)

        if (text.isNotEmpty()) {
            floatingLabelFraction = 1f
        }

        addTextChangeListener()
        initFocusChangeListener()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        staticUnderline?.let {
            it.left = paddingStart.toFloat() + scrollX.toFloat()
            it.right = measuredWidth.toFloat() - paddingStart + scrollX.toFloat()
            canvas.drawRect(it, underlineBackgroundPaint)
        }
        animatedUnderline?.let {
            if (!isUnderlineAnimating && hasFocus()) {
                it.left = paddingStart.toFloat() + scrollX.toFloat()
                it.right = measuredWidth.toFloat() - paddingStart + scrollX.toFloat()
            }
            canvas.drawRect(it, underlineForegroundPaint)
        }
        hint?.toString()?.let {
            floatingHintPaint.color = if (isFocused) activeHintColor else defaultHintColor
            floatingHintPaint.alpha = (floatingLabelFraction * 255f).toInt()
            canvas.drawText(
                it,
                paddingStart.toFloat() + scrollX.toFloat(),
                max(paddingTop.toFloat(), (measuredHeight / 3 * (1 - floatingLabelFraction))),
                floatingHintPaint
            )
        }
        if (isFocusPending) {
            animateUnderline(false)
            isFocusPending = false
        }
        if (hasFocus() && text.isNotEmpty() && clearButton != null && isClearEnabled) {
            canvas.drawBitmap(
                clearButton!!,
                measuredWidth - clearButtonSize - clearButtonSpacing + scrollX,
                (originalHeight - clearButtonSize + underlineHeight) / 2,
                clearIconPaint
            )
        }
        if (!errorText.isNullOrEmpty()) {
            errorPaint.alpha = (errorSpaceFraction * 255f).toInt()
            canvas.drawText(
                errorText!!,
                paddingStart.toFloat() + scrollX.toFloat(),
                measuredHeight.toFloat() - dp(3),
                errorPaint
            )
            canvas.drawBitmap(
                warningIcon!!,
                measuredWidth - warningIconSize - clearButtonSpacing + scrollX,
                (measuredHeight.toFloat() - warningIconSize),
                errorPaint
            )
            errorUnderline?.let {
                it.left = paddingStart.toFloat() + scrollX.toFloat()
                it.right = measuredWidth.toFloat() - paddingStart + scrollX.toFloat()
                canvas.drawRect(it, errorPaint)
            }
        }
        if (errorSpaceFraction > 0 && errorSpaceFraction < 1) {
            val additionalSpacing = (errorSpacing * errorSpaceFraction).toInt()
            height = originalHeight + additionalSpacing
            setPadding(paddingStart, paddingTop, paddingEnd, originalPaddingBottom + additionalSpacing)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        initStaticUnderline()
        initAnimatedUnderline()
        initErrorUnderline()
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cursorDrawable?.setBounds(0, 0, dp(2).toInt(), measuredHeight)
            textCursorDrawable = cursorDrawable
        }
        if (originalHeight == 0) {
            originalHeight = measuredHeight
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (hasFocus() && isEnabled && isClearEnabled) {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isTouchInsideClearButtonArea(event)) {
                        isClearButtonClickActive = true
                        isClearButtonTouchActive = true
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isClearButtonClickActive && !isTouchInsideClearButtonArea(event)) {
                        isClearButtonClickActive = false
                    }
                    if (isClearButtonTouchActive) return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClearButtonClickActive) {
                        setText("")
                        isClearButtonClickActive = false
                    }
                    if (isClearButtonTouchActive) {
                        isClearButtonTouchActive = false
                        return true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    isClearButtonTouchActive = false
                    isClearButtonClickActive = false
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun setOnFocusChangeListener(l: OnFocusChangeListener?) {
        if (innerFocusChangeListener == null) {
            super.setOnFocusChangeListener(l)
        }
    }

    override fun setError(error: CharSequence?) {
        if (!error.isNullOrEmpty()) {
            errorText = error.toString()
            if (errorSpaceFraction == 0f) {
                errorSpaceAnimator?.doOnEnd {
                    errorText = error.toString()
                }
                errorSpaceAnimator?.start()
            }
        } else {
            if (errorSpaceFraction == 1f) {
                errorSpaceAnimator?.doOnEnd {
                    errorText = null
                }
                errorSpaceAnimator?.reverse()
            }
        }
    }

    override fun getError(): CharSequence {
        errorText?.let {
            return it
        }
        return super.getError()
    }

    private fun setDefaultSettings() {
        background = null
        isSingleLine = true
        textSize = 16f
        height = dp(56).toInt()
        setCursorColor(cursorDrawableColor)
        setHintTextColor(defaultHintColor)
        highlightColor = setColorAlpha(0.2f, cursorDrawableColor)
        setSelectionHandleColor(cursorDrawableColor)

        if (inputType == InputType.TYPE_TEXT_VARIATION_PASSWORD || inputType == 129) {
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
    }

    private fun addTextChangeListener() {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }

            override fun afterTextChanged(editable: Editable?) {
                if (editable.isNullOrEmpty()) {
                    floatingLabelAnimator?.reverse()
                } else if (floatingLabelFraction == 0f) {
                    floatingLabelAnimator?.start()
                }
                if (!errorText.isNullOrEmpty()) {
                    error = ""
                }
            }
        })
    }

    //  Underline

    private fun initStaticUnderline() {
        if (staticUnderline != null) return
        staticUnderline = RectF(
            paddingStart.toFloat(),
            measuredHeight.toFloat() - underlineHeight / 2 - paddingBottom + bottomUnderlineOffset,
            measuredWidth.toFloat() - paddingStart,
            measuredHeight.toFloat() - paddingBottom + bottomUnderlineOffset
        )
    }

    private fun initErrorUnderline() {
        if (errorUnderline != null) return
        errorUnderline = RectF(
            paddingStart.toFloat(),
            measuredHeight.toFloat() - underlineHeight - paddingBottom + bottomUnderlineOffset,
            measuredWidth.toFloat() - paddingStart,
            measuredHeight.toFloat() - paddingBottom + bottomUnderlineOffset
        )
    }

    private fun initAnimatedUnderline() {
        if (animatedUnderline != null) return
        animatedUnderline = AnimatedRectF(
            measuredWidth.toFloat() / 2,
            measuredHeight.toFloat() - underlineHeight - paddingBottom + bottomUnderlineOffset,
            measuredWidth.toFloat() / 2,
            measuredHeight.toFloat() - paddingBottom + bottomUnderlineOffset
        )
    }

    private fun animateUnderline(collapse: Boolean) {
        if (animatedUnderline == null) return
        val animateLeft = ObjectAnimator.ofFloat(
            animatedUnderline,
            "left",
            animatedUnderline!!.left,
            if (collapse) measuredWidth.toFloat() / 2 + scrollX.toFloat() else paddingStart.toFloat() + scrollX.toFloat()
        )
        animateLeft.interpolator = AccelerateInterpolator()
        val animateRight = ObjectAnimator.ofFloat(
            animatedUnderline,
            "right",
            animatedUnderline!!.right,
            if (collapse) measuredWidth.toFloat() / 2 + scrollX.toFloat() else measuredWidth.toFloat() - paddingStart + scrollX.toFloat()
        )
        animateRight.interpolator = AccelerateInterpolator()
        animateRight.addUpdateListener { postInvalidate() }
        animateRight.doOnStart { isUnderlineAnimating = true }
        animateRight.doOnEnd { isUnderlineAnimating = false }

        val rectAnimation = AnimatorSet()
        rectAnimation.playTogether(animateLeft, animateRight)
        rectAnimation.setDuration(250).start()
    }

    //  Floating label

    fun getFloatingLabelFraction(): Float {
        return floatingLabelFraction
    }

    fun setFloatingLabelFraction(floatingLabelFraction: Float) {
        this.floatingLabelFraction = floatingLabelFraction
        invalidate()
    }

    fun getErrorSpaceFraction(): Float {
        return errorSpaceFraction
    }

    fun setErrorSpaceFraction(errorSpaceFraction: Float) {
        this.errorSpaceFraction = errorSpaceFraction
        invalidate()
    }

    //  Focus

    private fun initFocusChangeListener() {
        innerFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                isFocusPending = (animatedUnderline == null)
            }
            animateUnderline(!hasFocus)
        }
        super.setOnFocusChangeListener(innerFocusChangeListener)
    }

    //

    private fun isTouchInsideClearButtonArea(event: MotionEvent): Boolean {
        val clearPositionX = measuredWidth - clearButtonSize - clearButtonSpacing * 2 - paddingStart
        return event.x >= clearPositionX && event.x < (measuredWidth - paddingStart)
    }

    //  Helper methods

    private fun dp(dp: Int): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)
    }

    private fun setSelectionHandleColor(color: Int) {
        try {
            val editorField = TextView::class.java.getDeclaredField("mEditor")
            if (!editorField.isAccessible) {
                editorField.isAccessible = true
            }

            val editor = editorField.get(this)
            val editorClass = editor.javaClass

            val handleNames = arrayOf("mSelectHandleLeft", "mSelectHandleRight", "mSelectHandleCenter")
            val resNames = arrayOf("mTextSelectHandleLeftRes", "mTextSelectHandleRightRes", "mTextSelectHandleRes")

            for (i in handleNames.indices) {
                val handleField = editorClass.getDeclaredField(handleNames[i])
                if (!handleField.isAccessible) {
                    handleField.isAccessible = true
                }
                var handleDrawable = handleField.get(editor) as? Drawable
                if (handleDrawable == null) {
                    val resField = TextView::class.java.getDeclaredField(resNames[i])
                    if (!resField.isAccessible) {
                        resField.isAccessible = true
                    }
                    val resId = resField.getInt(this)
                    handleDrawable = ContextCompat.getDrawable(context, resId)
                }
                if (handleDrawable != null) {
                    val drawable = handleDrawable.mutate()
                    drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                    handleField.set(editor, drawable)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setCursorColor(@ColorInt color: Int) {
        try {
            var field = TextView::class.java.getDeclaredField("mCursorDrawableRes")
            field.isAccessible = true
            val drawableResId = field.getInt(this)

            field = TextView::class.java.getDeclaredField("mEditor")
            field.isAccessible = true
            val editor = field.get(this)

            val drawable = ContextCompat.getDrawable(context, drawableResId)
            drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            val drawables = arrayOf(drawable, drawable)

            field = editor.javaClass.getDeclaredField("mCursorDrawable")
            field.isAccessible = true
            field.set(editor, drawables)
        } catch (ex: Exception) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                cursorDrawable = WrapDrawable(ColorDrawable(color))
            }
        }
    }

    private fun setColorAlpha(alpha: Float, color: Int): Int {
        val opacity = Math.round(Color.alpha(color) * alpha)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(opacity, red, green, blue)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}