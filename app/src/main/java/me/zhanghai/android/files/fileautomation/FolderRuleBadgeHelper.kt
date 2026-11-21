/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.view.View
import com.google.android.material.chip.Chip
import java8.nio.file.Path
import me.zhanghai.android.files.R

object FolderRuleBadgeHelper {

    fun bind(
        chip: Chip,
        currentPath: Path,
        rules: List<FileRule>,
        onRunClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val matchingRules = rules.filter { it.enabled && it.sourceDirectory == currentPath }
        if (matchingRules.isEmpty()) {
            chip.visibility = View.GONE
            chip.setOnClickListener(null)
            chip.setOnLongClickListener(null)
            return
        }
        chip.visibility = View.VISIBLE
        chip.text = chip.context.getString(R.string.file_rule_folder_badge, matchingRules.size)
        chip.setOnClickListener { onRunClick() }
        chip.setOnLongClickListener {
            onLongClick()
            true
        }
    }
}
