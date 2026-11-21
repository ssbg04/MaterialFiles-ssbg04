package me.zhanghai.android.files.provider.cloud.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class CloudQuota(
    val usedBytes: Long,
    val totalBytes: Long
) {
    val progressPercent: Int
        get() = if (totalBytes > 0L) {
            ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else {
            -1
        }
}

object CloudQuotaCache {
    private val cache = ConcurrentHashMap<Authority, CloudQuota>()
    private val inFlight = Collections.newSetFromMap(ConcurrentHashMap<Authority, Boolean>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getCached(authority: Authority): CloudQuota? = cache[authority]

    fun fetchAsync(authority: Authority, onUpdated: () -> Unit) {
        if (cache.containsKey(authority) || !inFlight.add(authority)) {
            return
        }
        if (cache.containsKey(authority)) {
            inFlight.remove(authority)
            return
        }
        scope.launch {
            try {
                val quota = fetchQuota(authority)
                if (quota != null && quota.usedBytes >= 0L) {
                    cache[authority] = quota
                    withContext(Dispatchers.Main) {
                        onUpdated()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore quota fetch errors; UI remains without quota info.
            } finally {
                inFlight.remove(authority)
            }
        }
    }

    private fun fetchQuota(authority: Authority): CloudQuota? {
        val request = when (authority.provider) {
            CloudProvider.GDRIVE ->
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
                    .get()
                    .build()
            CloudProvider.ONEDRIVE ->
                Request.Builder()
                    .url("https://graph.microsoft.com/v1.0/me/drive?%24select=quota")
                    .get()
                    .build()
            CloudProvider.DROPBOX ->
                Request.Builder()
                    .url("https://api.dropboxapi.com/2/users/get_space_usage")
                    .post("".toRequestBody(null))
                    .build()
        }
        val client = Client.getHttpClient(authority)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            val body = response.body.string()
            if (body.isEmpty()) {
                return null
            }
            val json = JSONObject(body)
            return parseQuota(authority.provider, json)
        }
    }

    private fun parseQuota(provider: CloudProvider, json: JSONObject): CloudQuota? {
        return when (provider) {
            CloudProvider.GDRIVE -> {
                val storageQuota = json.optJSONObject("storageQuota") ?: return null
                CloudQuota(
                    usedBytes = storageQuota.optLongCompat("usage"),
                    totalBytes = storageQuota.optLongCompat("limit")
                )
            }
            CloudProvider.ONEDRIVE -> {
                val quota = json.optJSONObject("quota") ?: return null
                CloudQuota(
                    usedBytes = quota.optLongCompat("used"),
                    totalBytes = quota.optLongCompat("total")
                )
            }
            CloudProvider.DROPBOX -> {
                val allocation = json.optJSONObject("allocation")
                CloudQuota(
                    usedBytes = json.optLongCompat("used"),
                    totalBytes = allocation?.optLongCompat("allocated") ?: -1L
                )
            }
        }
    }

    private fun JSONObject.optLongCompat(key: String, default: Long = -1L): Long {
        if (isNull(key)) {
            return default
        }
        return optString(key).toLongOrNull() ?: optLong(key, default)
    }
}
