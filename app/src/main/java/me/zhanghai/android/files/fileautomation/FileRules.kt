/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import me.zhanghai.android.files.compat.removeFirstCompat
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

object FileRules {
    fun add(fileRule: FileRule) {
        val rules = Settings.FILE_RULES.valueCompat.toMutableList().apply { add(fileRule) }
        Settings.FILE_RULES.putValue(rules)
    }

    fun replace(fileRule: FileRule) {
        val rules = Settings.FILE_RULES.valueCompat.toMutableList().apply {
            val index = indexOfFirst { it.id == fileRule.id }
            if (index != -1) {
                this[index] = fileRule
            } else {
                add(fileRule)
            }
        }
        Settings.FILE_RULES.putValue(rules)
    }

    fun remove(fileRule: FileRule) {
        val rules = Settings.FILE_RULES.valueCompat.toMutableList().apply {
            val index = indexOfFirst { it.id == fileRule.id }
            if (index != -1) {
                removeAt(index)
            }
        }
        Settings.FILE_RULES.putValue(rules)
    }

    fun move(fromPosition: Int, toPosition: Int) {
        val rules = Settings.FILE_RULES.valueCompat.toMutableList().apply {
            add(toPosition, removeAt(fromPosition))
        }
        Settings.FILE_RULES.putValue(rules)
    }
}
