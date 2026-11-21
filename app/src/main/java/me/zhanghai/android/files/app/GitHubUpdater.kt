/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

object GitHubUpdater {
    private const val REPO_OWNER = "ssbg04"
    private const val REPO_NAME = "MaterialFiles-ssbg04"
    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    private val hasCheckedAutomatically = AtomicBoolean(false)

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val apkDownloadUrl: String?
    )

    fun checkForUpdates(activity: AppCompatActivity, manual: Boolean = false) {
        if (!manual && !hasCheckedAutomatically.compareAndSet(false, true)) {
            return
        }
        if (manual) {
            activity.showToast(R.string.update_checking)
        }
        activity.lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
                if (activity.isFinishing || activity.isDestroyed) {
                    return@launch
                }
                if (release != null && compareVersions(release.versionName, BuildConfig.VERSION_NAME) > 0) {
                    showUpdateDialog(activity, release)
                } else if (manual) {
                    activity.showToast(R.string.update_up_to_date)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (manual && !activity.isFinishing && !activity.isDestroyed) {
                    activity.showToast(R.string.update_check_failed)
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MaterialFiles-Updater")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return null
                }
                throw IllegalStateException("GitHub API returned ${response.code}")
            }
            val body = response.body.string()
            val json = JSONObject(body)
            if (json.optBoolean("draft", false)) {
                return null
            }
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                return null
            }
            val versionName = normalizeVersion(tagName)
            val releaseNotes = json.optString("body", "").trim()
            val htmlUrl = json.optString(
                "html_url",
                "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
            )
            val assets = json.optJSONArray("assets")
            var releaseApkUrl: String? = null
            var fallbackApkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                        if (!name.contains("debug", ignoreCase = true) && releaseApkUrl == null) {
                            releaseApkUrl = downloadUrl
                        } else if (fallbackApkUrl == null) {
                            fallbackApkUrl = downloadUrl
                        }
                    }
                }
            }
            return ReleaseInfo(
                tagName = tagName,
                versionName = versionName,
                releaseNotes = releaseNotes,
                htmlUrl = htmlUrl,
                apkDownloadUrl = releaseApkUrl ?: fallbackApkUrl
            )
        }
    }

    private fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")

    internal fun compareVersions(remote: String, local: String): Int {
        val remoteParts = normalizeVersion(remote).split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = normalizeVersion(local).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) {
                return r.compareTo(l)
            }
        }
        return 0
    }

    private fun showUpdateDialog(activity: AppCompatActivity, release: ReleaseInfo) {
        val notes = release.releaseNotes.ifEmpty {
            activity.getString(R.string.update_available_default_message, release.tagName)
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_available_title, release.tagName))
            .setMessage(notes)
            .setNegativeButton(R.string.update_later, null)
            .setNeutralButton(R.string.about_github_title) { _, _ ->
                activity.startActivitySafe(Uri.parse(release.htmlUrl).createViewIntent())
            }
        if (release.apkDownloadUrl != null) {
            builder.setPositiveButton(R.string.update_download_and_install) { _, _ ->
                downloadAndInstallApk(activity, release)
            }
        } else {
            builder.setPositiveButton(R.string.update_open_release) { _, _ ->
                activity.startActivitySafe(Uri.parse(release.htmlUrl).createViewIntent())
            }
        }
        builder.show()
    }

    private fun downloadAndInstallApk(activity: AppCompatActivity, release: ReleaseInfo) {
        val downloadUrl = release.apkDownloadUrl ?: return
        val density = activity.resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, (16 * density).toInt(), padding, (8 * density).toInt())
        }
        val progressBar = ProgressBar(
            activity,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            max = 100
        }
        val statusText = TextView(activity).apply {
            text = activity.getString(R.string.update_downloading)
            gravity = Gravity.START
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        container.addView(progressBar)
        container.addView(statusText)

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_available_title, release.tagName))
            .setView(container)
            .setCancelable(false)
            .show()

        activity.lifecycleScope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    val updatesDir = File(activity.cacheDir, "updates").apply { mkdirs() }
                    updatesDir.listFiles()?.forEach { it.delete() }
                    val targetFile = File(updatesDir, "MaterialFiles-${release.versionName}.apk")
                    val request = Request.Builder()
                        .url(downloadUrl)
                        .header("User-Agent", "MaterialFiles-Updater")
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Download failed: HTTP ${response.code}")
                        }
                        val body = response.body
                        val totalBytes = body.contentLength()
                        var downloadedBytes = 0L
                        var lastPercent = -1
                        body.byteStream().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    downloadedBytes += read
                                    if (totalBytes > 0) {
                                        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                        if (percent != lastPercent) {
                                            lastPercent = percent
                                            withContext(Dispatchers.Main) {
                                                if (progressDialog.isShowing) {
                                                    progressBar.isIndeterminate = false
                                                    progressBar.progress = percent
                                                    statusText.text = "$percent%"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    targetFile
                }
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val apkUri = Paths.get(apkFile.absolutePath).fileProviderUri
                    val installIntent = apkUri.createViewIntent(MimeType.APK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivitySafe(installIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.showToast(R.string.update_download_failed)
                }
            }
        }
    }
}
