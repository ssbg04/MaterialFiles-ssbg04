/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.content.Context
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.provider.common.isDirectory
import me.zhanghai.android.files.provider.common.newDirectoryStream
import java.io.IOException

object FileRuleEngine {

    suspend fun runRule(rule: FileRule, context: Context): Int =
        runRuleDetailed(rule, context).size

    suspend fun findMatchingFiles(rule: FileRule): List<Path> = withContext(Dispatchers.IO) {
        val matchingPaths = mutableListOf<Path>()
        collectMatchingFiles(
            directory = rule.sourceDirectory,
            rule = rule,
            recursive = rule.includeSubdirectories,
            result = matchingPaths
        )
        matchingPaths
    }

    suspend fun runRuleDetailed(rule: FileRule, context: Context): List<Path> {
        val matchingPaths = findMatchingFiles(rule)
        if (matchingPaths.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                when (rule.action) {
                    RuleAction.MOVE -> FileJobService.move(matchingPaths, rule.targetDirectory, context)
                    RuleAction.COPY -> FileJobService.copy(matchingPaths, rule.targetDirectory, context)
                }
            }
        }
        return matchingPaths
    }

    fun undoMove(
        movedSourcePaths: List<Path>,
        targetDirectory: Path,
        originalSourceDirectory: Path,
        context: Context
    ) {
        if (movedSourcePaths.isEmpty()) {
            return
        }
        val movedTargets = movedSourcePaths.map { targetDirectory.resolve(it.name) }
        FileJobService.move(movedTargets, originalSourceDirectory, context)
    }

    private fun collectMatchingFiles(
        directory: Path,
        rule: FileRule,
        recursive: Boolean,
        result: MutableList<Path>
    ) {
        val excludeTarget = rule.targetDirectory != rule.sourceDirectory &&
            rule.targetDirectory.fileSystem == rule.sourceDirectory.fileSystem &&
            rule.targetDirectory.startsWith(rule.sourceDirectory)
        try {
            directory.newDirectoryStream().use { stream ->
                for (entry in stream) {
                    try {
                        if (excludeTarget && (entry == rule.targetDirectory ||
                                (entry.fileSystem == rule.targetDirectory.fileSystem &&
                                    entry.startsWith(rule.targetDirectory)))
                        ) {
                            continue
                        }
                        val isDir = try {
                            entry.isDirectory()
                        } catch (e: Exception) {
                            false
                        }
                        if (isDir) {
                            if (recursive) {
                                collectMatchingFiles(entry, rule, true, result)
                            }
                        } else {
                            if (matchesRule(entry, rule)) {
                                result.add(entry)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun matchesRule(path: Path, rule: FileRule): Boolean {
        val fileName = path.name

        // Check extensions
        if (rule.extensions.isNotBlank()) {
            val exts = rule.extensions
                .split(',', ';', ' ', '\t')
                .map { it.trim().trimStart('*').trimStart('.').lowercase() }
                .filter { it.isNotEmpty() }
            if (exts.isNotEmpty()) {
                val fileExt = fileName.substringAfterLast('.', "").lowercase()
                if (fileExt !in exts) {
                    return false
                }
            }
        }

        // Check name pattern
        if (rule.namePattern.isNotBlank()) {
            val pattern = rule.namePattern.trim()
            if (rule.isRegex) {
                try {
                    if (!Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(fileName)) {
                        return false
                    }
                } catch (e: Exception) {
                    return false
                }
            } else if (pattern.contains('*') || pattern.contains('?')) {
                val regex = globToRegex(pattern)
                if (!regex.matches(fileName)) {
                    return false
                }
            } else {
                if (!fileName.contains(pattern, ignoreCase = true)) {
                    return false
                }
            }
        }

        return true
    }

    private fun globToRegex(glob: String): Regex {
        val regexStr = buildString {
            append("^")
            for (c in glob) {
                when (c) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> append("\\").append(c)
                    else -> append(c)
                }
            }
            append("$")
        }
        return Regex(regexStr, RegexOption.IGNORE_CASE)
    }
}
