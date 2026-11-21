/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.os.Parcelable
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.util.ParcelableParceler
import java.util.Random

enum class RuleAction {
    MOVE,
    COPY
}

@Parcelize
data class FileRule(
    val id: Long = Random().nextLong(),
    val name: String,
    val enabled: Boolean = true,
    val sourceDirectory: @WriteWith<ParcelableParceler> Path,
    val includeSubdirectories: Boolean = false,
    val extensions: String = "",
    val namePattern: String = "",
    val isRegex: Boolean = false,
    val action: RuleAction = RuleAction.MOVE,
    val targetDirectory: @WriteWith<ParcelableParceler> Path,
    val autoRunOnOpen: Boolean = false
) : Parcelable {

    val displayName: String
        get() = name.ifEmpty { "${sourceDirectory.name} → ${targetDirectory.name}" }

    val detailsDescription: String
        get() = buildString {
            append(if (action == RuleAction.MOVE) "Move" else "Copy")
            val filters = mutableListOf<String>()
            if (extensions.isNotBlank()) {
                val cleaned = extensions.split(',', ';', ' ', '\t')
                    .map { it.trim().trimStart('*').trimStart('.') }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ") { "*.$it" }
                if (cleaned.isNotEmpty()) {
                    filters.add(cleaned)
                }
            }
            if (namePattern.isNotBlank()) {
                filters.add(if (isRegex) "Regex: $namePattern" else "Name: $namePattern")
            }
            if (filters.isNotEmpty()) {
                append(" • ")
                append(filters.joinToString(" • "))
            }
            if (includeSubdirectories) {
                append(" • Recursive")
            }
            if (autoRunOnOpen) {
                append(" • Auto-run")
            }
        }
}
