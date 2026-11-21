package me.zhanghai.android.files.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Triggers a subtle tick haptic feedback (using [HapticFeedbackConstants.CLOCK_TICK]) while
 * respecting the view and system haptic settings.
 */
fun View.performSubtleTick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/**
 * Triggers a contextual selection haptic feedback (using [HapticFeedbackConstants.CONTEXT_CLICK])
 * while respecting the view and system haptic settings.
 */
fun View.performSelectionHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

/**
 * Triggers a confirmation haptic feedback using [HapticFeedbackConstants.CONFIRM] on Android 11+
 * and falling back to [HapticFeedbackConstants.CLOCK_TICK] on earlier versions.
 */
fun View.performConfirmHaptic() {
    val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(feedbackConstant)
}
