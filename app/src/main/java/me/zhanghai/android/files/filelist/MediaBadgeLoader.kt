/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.filelist

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.widget.TextView
import androidx.core.view.isVisible
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.use
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.provider.linux.isLinuxPath

object MediaBadgeLoader {
    private const val CACHE_MAX_ENTRIES = 256

    // Empty string acts as a non-null sentinel in LruCache to avoid re-probing failed/unsupported files.
    private const val SENTINEL_NONE = ""

    private val cache = LruCache<String, String>(CACHE_MAX_ENTRIES)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Binds a video duration or image resolution badge onto [textView] for local regular media files. */
    fun bind(textView: TextView, file: FileItem) {
        val previousJob = textView.getTag(R.id.media_badge_job_tag) as? Job
        if (!isEligibleMediaFile(file)) {
            previousJob?.cancel()
            textView.setTag(R.id.media_badge_job_tag, null)
            textView.setTag(R.id.mediaBadgeText, null)
            applyBadgeText(textView, "")
            return
        }

        val cacheKey = "${file.path}|${file.attributes.lastModifiedTime().toMillis()}"
        val cached = cache.get(cacheKey)
        if (cached != null) {
            previousJob?.cancel()
            textView.setTag(R.id.media_badge_job_tag, null)
            textView.setTag(R.id.mediaBadgeText, cacheKey)
            applyBadgeText(textView, cached)
            return
        }

        if (textView.getTag(R.id.mediaBadgeText) == cacheKey && previousJob?.isActive == true) {
            return
        }

        previousJob?.cancel()
        textView.setTag(R.id.mediaBadgeText, cacheKey)
        applyBadgeText(textView, "")

        val job = scope.launch {
            val badgeText = extractBadgeText(file) ?: SENTINEL_NONE
            cache.put(cacheKey, badgeText)
            val currentJob = coroutineContext[Job]
            withContext(Dispatchers.Main) {
                if (textView.getTag(R.id.media_badge_job_tag) == currentJob) {
                    textView.setTag(R.id.media_badge_job_tag, null)
                }
                if (isActive && textView.getTag(R.id.mediaBadgeText) == cacheKey) {
                    applyBadgeText(textView, badgeText)
                }
            }
        }
        textView.setTag(R.id.media_badge_job_tag, job)
    }

    private fun applyBadgeText(textView: TextView, badgeText: String) {
        textView.text = badgeText.ifEmpty { null }
        textView.isVisible = badgeText.isNotEmpty()
    }

    private fun isEligibleMediaFile(file: FileItem): Boolean =
        file.path.isLinuxPath && file.attributes.isRegularFile &&
            (file.mimeType.isVideo || file.mimeType.isImage)

    private fun extractBadgeText(file: FileItem): String? =
        try {
            when {
                file.mimeType.isVideo -> extractVideoDuration(file)
                file.mimeType.isImage -> extractImageDimensions(file)
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun extractVideoDuration(file: FileItem): String? =
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.path.toFile().path)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()
            if (durationMs != null && durationMs > 0L) {
                Duration.ofMillis(durationMs).format()
            } else {
                null
            }
        }

    private fun extractImageDimensions(file: FileItem): String? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path.toFile().path, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            "${options.outWidth}×${options.outHeight}"
        } else {
            null
        }
    }
}
