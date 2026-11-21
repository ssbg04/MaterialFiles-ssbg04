/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.settings

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.fileautomation.FileRule
import me.zhanghai.android.files.fileautomation.FileRuleListActivity
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe

class FileRulesPreference : Preference {
    private var emptySummary = summary

    private val observer = Observer<List<FileRule>> { onFileRuleListChanged(it) }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        isPersistent = false
    }

    override fun onAttached() {
        super.onAttached()

        Settings.FILE_RULES.observeForever(observer)
    }

    override fun onDetached() {
        super.onDetached()

        Settings.FILE_RULES.removeObserver(observer)
    }

    private fun onFileRuleListChanged(fileRules: List<FileRule>) {
        val summary = if (fileRules.isNotEmpty()) {
            context.getString(R.string.settings_file_rules_summary, fileRules.size)
        } else {
            emptySummary
        }
        setSummary(summary)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val summaryText = holder.findViewById(android.R.id.summary) as? TextView
        summaryText?.ellipsize = TextUtils.TruncateAt.END
        summaryText?.isSingleLine = true
    }

    override fun onClick() {
        context.startActivitySafe(FileRuleListActivity::class.createIntent())
    }
}
