/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.theme.custom

import android.app.Activity
import com.google.android.material.color.DynamicColors
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

object DynamicColorHelper {
    /** Applies Material 3 dynamic colors to [activity] when M3 and device support are enabled. */
    fun applyIfEnabled(activity: Activity) {
        if (Settings.MATERIAL_DESIGN_3.valueCompat && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }
}
