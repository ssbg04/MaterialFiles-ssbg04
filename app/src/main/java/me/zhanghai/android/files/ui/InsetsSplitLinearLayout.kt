package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.view.children

class InsetsSplitLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    @Suppress("DEPRECATION")
    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (childCount >= 2 && getChildAt(1).visibility != View.GONE) {
            val topInsets = insets.replaceSystemWindowInsets(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                0
            )
            getChildAt(0).dispatchApplyWindowInsets(topInsets)
            val bottomInsets = insets.replaceSystemWindowInsets(
                insets.systemWindowInsetLeft,
                0,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            getChildAt(1).dispatchApplyWindowInsets(bottomInsets)
            return insets.consumeSystemWindowInsets()
        }
        for (child in children) {
            child.dispatchApplyWindowInsets(insets)
        }
        return insets.consumeSystemWindowInsets()
    }
}
