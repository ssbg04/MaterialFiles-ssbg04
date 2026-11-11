package me.zhanghai.android.files.provider.cloud.client

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessDeniedException
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.FileSystemException
import java8.nio.file.NoSuchFileException
import java8.nio.file.Path as Java8Path
import me.zhanghai.android.files.app.okHttpClient
import me.zhanghai.android.files.provider.common.DelegateOutputStream
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.NotifyEntryModifiedOutputStream
import me.zhanghai.android.files.provider.common.NotifyEntryModifiedSeekableByteChannel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okio.BufferedSink
import okio.Pipe
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

object Client {
    private const val GDRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()

    @Volatile
    lateinit var authenticator: Authenticator

    private val clients = mutableMapOf<Authority, OkHttpClient>()
    private val metadataCache = ConcurrentHashMap<Path, CloudFile>()

    private fun getClient(authority: Authority): OkHttpClient {
        synchronized(clients) {
            var client = clients[authority]
            if (client == null) {
                val authInterceptor = OAuthAuthenticatorInterceptor(authority)
                client = okHttpClient.newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor(authInterceptor)
                    .authenticator(authInterceptor)
                    .build()
                clients[authority] = client
            }
            return client
        }
    }

    private fun rootCloudFile(): CloudFile =
        CloudFile(
            id = "root",
            name = "",
            isDirectory = true,
            size = 0L,
            lastModifiedTime = Instant.EPOCH,
            creationTime = Instant.EPOCH
        )

    @Throws(IOException::class)
    fun getMetadataOrNull(path: Path): CloudFile? =
        try {
            getMetadata(path)
        } catch (e: NoSuchFileException) {
            null
        }

    @Throws(IOException::class)
    fun getMetadata(path: Path): CloudFile {
        if (path.pathString == "/" || path.pathString.isEmpty()) {
            return rootCloudFile()
        }
        metadataCache[path]?.let { return it }
        val file = when (path.authority.provider) {
            CloudProvider.GDRIVE -> getGDriveMetadata(path)
            CloudProvider.ONEDRIVE -> getOneDriveMetadata(path)
            CloudProvider.DROPBOX -> getDropboxMetadata(path)
        }
        metadataCache[path] = file
        return file
    }

    @Throws(IOException::class)
    fun listChildren(directory: Path): List<Path> {
        val children = when (directory.authority.provider) {
            CloudProvider.GDRIVE -> listGDriveChildren(directory)
            CloudProvider.ONEDRIVE -> listOneDriveChildren(directory)
            CloudProvider.DROPBOX -> listDropboxChildren(directory)
        }
        return children.map { childFile ->
            directory.resolve(childFile.name).also { childPath ->
                metadataCache[childPath] = childFile
            }
        }
    }

    @Throws(IOException::class)
    fun createDirectory(directory: Path) {
        val created = when (directory.authority.provider) {
            CloudProvider.GDRIVE -> createGDriveDirectory(directory)
            CloudProvider.ONEDRIVE -> createOneDriveDirectory(directory)
            CloudProvider.DROPBOX -> createDropboxDirectory(directory)
        }
        metadataCache[directory] = created
        LocalWatchService.onEntryCreated(directory as Java8Path)
    }

    @Throws(IOException::class)
    fun makeFile(file: Path) {
        put(file).close()
        LocalWatchService.onEntryCreated(file as Java8Path)
    }

    @Throws(IOException::class)
    fun delete(path: Path) {
        when (path.authority.provider) {
            CloudProvider.GDRIVE -> deleteGDrive(path)
            CloudProvider.ONEDRIVE -> deleteOneDrive(path)
            CloudProvider.DROPBOX -> deleteDropbox(path)
        }
        invalidateCachePrefix(path)
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(IOException::class)
    fun move(source: Path, target: Path) {
        if (source.authority != target.authority) {
            throw IOException("Paths aren't on the same authority")
        }
        when (source.authority.provider) {
            CloudProvider.GDRIVE -> moveGDrive(source, target)
            CloudProvider.ONEDRIVE -> moveOneDrive(source, target)
            CloudProvider.DROPBOX -> moveDropbox(source, target)
        }
        invalidateCachePrefix(source)
        invalidateCachePrefix(target)
        LocalWatchService.onEntryDeleted(source as Java8Path)
        LocalWatchService.onEntryCreated(target as Java8Path)
    }

    @Throws(IOException::class)
    fun get(path: Path): InputStream = openDownloadStream(path, null)

    @Throws(IOException::class)
    fun getRange(path: Path, offset: Long, size: Int): InputStream {
        val lastByte = offset + size - 1
        return openDownloadStream(path, "bytes=$offset-$lastByte")
    }

    @Throws(IOException::class)
    private fun openDownloadStream(path: Path, rangeHeader: String?): InputStream {
        val client = getClient(path.authority)
        val requestBuilder = when (path.authority.provider) {
            CloudProvider.GDRIVE -> {
                val meta = getMetadata(path)
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/${meta.id}?alt=media&supportsAllDrives=true")
                    .get()
            }
            CloudProvider.ONEDRIVE -> {
                val encodedPath = encodeOneDrivePath(path.pathString)
                Request.Builder()
                    .url("https://graph.microsoft.com/v1.0/me/drive/root:$encodedPath:/content")
                    .get()
            }
            CloudProvider.DROPBOX -> {
                val argJson = JSONObject().put("path", path.pathString)
                Request.Builder()
                    .url("https://content.dropboxapi.com/2/files/download")
                    .post(ByteArray(0).toRequestBody( null))
                    .header("Dropbox-API-Arg", toDropboxHeaderJson(argJson))
            }
        }
        if (rangeHeader != null) {
            requestBuilder.header("Range", rangeHeader)
        }
        val response = client.newCall(requestBuilder.build()).execute()
        if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
            response.close()
            return ByteArrayInputStream(ByteArray(0))
        }
        if (!response.isSuccessful) {
            val errorBody = response.body.string()
            val code = response.code
            response.close()
            throw toFileSystemException(path, code, errorBody)
        }
        return response.body.byteStream()
    }

    @Throws(IOException::class)
    fun put(path: Path): OutputStream {
        val client = getClient(path.authority)
        val requestBuilder = when (path.authority.provider) {
            CloudProvider.GDRIVE -> {
                val sessionUrl = initiateGDriveResumableUpload(path)
                Request.Builder().url(sessionUrl)
            }
            CloudProvider.ONEDRIVE -> {
                val encodedPath = encodeOneDrivePath(path.pathString)
                Request.Builder()
                    .url("https://graph.microsoft.com/v1.0/me/drive/root:$encodedPath:/content")
            }
            CloudProvider.DROPBOX -> {
                val argJson = JSONObject()
                    .put("path", path.pathString)
                    .put("mode", "overwrite")
                    .put("mute", false)
                Request.Builder()
                    .url("https://content.dropboxapi.com/2/files/upload")
                    .header("Dropbox-API-Arg", toDropboxHeaderJson(argJson))
                    .header("Content-Type", "application/octet-stream")
            }
        }

        val pipe = Pipe(DEFAULT_BUFFER_SIZE.toLong())
        val body = object : RequestBody() {
            override fun contentType(): MediaType = OCTET_STREAM_MEDIA_TYPE
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                sink.writeAll(pipe.source)
            }
        }
        val request = if (path.authority.provider == CloudProvider.DROPBOX) {
            requestBuilder.post(body).build()
        } else {
            requestBuilder.put(body).build()
        }

        var exceptionRef: IOException? = null
        var responseRef: Response? = null
        val callbackLatch = CountDownLatch(1)
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    exceptionRef = e
                    callbackLatch.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    responseRef = response
                    callbackLatch.countDown()
                }
            }
        )

        val stream = object : DelegateOutputStream(pipe.sink.buffer().outputStream()) {
            private var closed = false

            override fun close() {
                if (closed) {
                    return
                }
                closed = true
                super.close()
                callbackLatch.await()
                exceptionRef?.let { throw it }
                val response = responseRef!!
                response.use {
                    val responseBody = it.body.string()
                    if (!it.isSuccessful) {
                        throw toFileSystemException(path, it.code, responseBody)
                    }
                    metadataCache.remove(path)
                }
            }
        }
        return NotifyEntryModifiedOutputStream(stream, path as Java8Path)
    }

    @Throws(IOException::class)
    fun openByteChannel(path: Path, isAppend: Boolean): SeekableByteChannel =
        NotifyEntryModifiedSeekableByteChannel(
            FileByteChannel(path, isAppend),
            path as Java8Path
        )

    private fun invalidateCachePrefix(path: Path) {
        val prefix = path.pathString
        metadataCache.keys.removeIf { key ->
            key.authority == path.authority &&
                (key.pathString == prefix || key.pathString.startsWith("$prefix/"))
        }
    }

    // ======================== Google Drive Implementation ========================

    @Throws(IOException::class)
    private fun getGDriveMetadata(path: Path): CloudFile {
        val parent = path.parentPath ?: return rootCloudFile()
        val parentMeta = getMetadata(parent)
        val fileName = path.fileNameString ?: throw NoSuchFileException(path.toString())
        val escapedName = fileName.replace("\\", "\\\\").replace("'", "\\'")
        val query = "'${parentMeta.id}' in parents and name = '$escapedName' and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime,createdTime)")
            .addQueryParameter("pageSize", "1")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("includeItemsFromAllDrives", "true")
            .build()
        val request = Request.Builder().url(url).get().build()
        getClient(path.authority).newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toFileSystemException(path, response.code, body)
            }
            val files = JSONObject(body).optJSONArray("files")
            if (files == null || files.length() == 0) {
                throw NoSuchFileException(path.toString())
            }
            return parseGDriveFile(files.getJSONObject(0))
        }
    }

    @Throws(IOException::class)
    private fun listGDriveChildren(directory: Path): List<CloudFile> {
        val dirMeta = getMetadata(directory)
        val result = mutableListOf<CloudFile>()
        val seenNames = mutableSetOf<String>()
        var pageToken: String? = null
        val query = "'${dirMeta.id}' in parents and trashed = false"
        do {
            val urlBuilder = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter(
                    "fields",
                    "nextPageToken,files(id,name,mimeType,size,modifiedTime,createdTime)"
                )
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
            if (pageToken != null) {
                urlBuilder.addQueryParameter("pageToken", pageToken)
            }
            val request = Request.Builder().url(urlBuilder.build()).get().build()
            getClient(directory.authority).newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw toFileSystemException(directory, response.code, body)
                }
                val json = JSONObject(body)
                val files = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until files.length()) {
                    val file = parseGDriveFile(files.getJSONObject(i))
                    if (file.name.isNotEmpty() && !file.name.contains('/') && seenNames.add(file.name)) {
                        result += file
                    }
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
            }
        } while (pageToken != null)
        return result
    }

    private fun parseGDriveFile(json: JSONObject): CloudFile {
        val id = json.getString("id")
        val name = json.optString("name", id)
        val mimeType = json.optString("mimeType", "")
        val isDirectory = mimeType == GDRIVE_FOLDER_MIME_TYPE
        val size = if (isDirectory) 0L else json.optLong("size", 0L)
        val modifiedTime = parseInstantOrEpoch(json.optString("modifiedTime"))
        val createdTime = parseInstantOrEpoch(json.optString("createdTime", json.optString("modifiedTime")))
        return CloudFile(id, name, isDirectory, size, modifiedTime, createdTime)
    }

    @Throws(IOException::class)
    private fun createGDriveDirectory(directory: Path): CloudFile {
        val parent = directory.parentPath ?: throw FileSystemException(directory.toString())
        val parentMeta = getMetadata(parent)
        val name = directory.fileNameString ?: throw FileSystemException(directory.toString())
        val payload = JSONObject()
            .put("name", name)
            .put("mimeType", GDRIVE_FOLDER_MIME_TYPE)
            .put("parents", JSONArray().put(parentMeta.id))
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,size,modifiedTime,createdTime&supportsAllDrives=true")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(directory.authority).newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toFileSystemException(directory, response.code, body)
            }
            return parseGDriveFile(JSONObject(body))
        }
    }

    @Throws(IOException::class)
    private fun initiateGDriveResumableUpload(path: Path): String {
        val existing = getMetadataOrNull(path)
        val request = if (existing != null) {
            val payload = JSONObject().put("name", path.fileNameString)
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/${existing.id}?uploadType=resumable&supportsAllDrives=true")
                .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } else {
            val parent = path.parentPath ?: throw FileSystemException(path.toString())
            val parentMeta = getMetadata(parent)
            val payload = JSONObject()
                .put("name", path.fileNameString)
                .put("parents", JSONArray().put(parentMeta.id))
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }
        getClient(path.authority).newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toFileSystemException(path, response.code, body)
            }
            return response.header("Location")
                ?: throw IOException("Missing resumable upload Location header")
        }
    }

    @Throws(IOException::class)
    private fun deleteGDrive(path: Path) {
        val meta = getMetadata(path)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/${meta.id}?supportsAllDrives=true")
            .delete()
            .build()
        getClient(path.authority).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                throw toFileSystemException(path, response.code, body)
            }
        }
    }

    @Throws(IOException::class)
    private fun moveGDrive(source: Path, target: Path) {
        val sourceMeta = getMetadata(source)
        val sourceParent = source.parentPath ?: throw FileSystemException(source.toString())
        val targetParent = target.parentPath ?: throw FileSystemException(target.toString())
        val sourceParentMeta = getMetadata(sourceParent)
        val targetParentMeta = getMetadata(targetParent)
        val targetName = target.fileNameString ?: throw FileSystemException(target.toString())

        val urlBuilder = "https://www.googleapis.com/drive/v3/files/${sourceMeta.id}".toHttpUrl()
            .newBuilder()
            .addQueryParameter("supportsAllDrives", "true")
        if (sourceParentMeta.id != targetParentMeta.id) {
            urlBuilder.addQueryParameter("addParents", targetParentMeta.id)
            urlBuilder.addQueryParameter("removeParents", sourceParentMeta.id)
        }
        val payload = JSONObject().put("name", targetName)
        val request = Request.Builder()
            .url(urlBuilder.build())
            .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(source.authority).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                throw toFileSystemException(source, response.code, body)
            }
        }
    }

    // ======================== OneDrive Implementation ========================

    private fun encodeOneDrivePath(pathString: String): String {
        val trimmed = pathString.trim('/')
        if (trimmed.isEmpty()) {
            return ""
        }
        val url = "https://graph.microsoft.com/".toHttpUrl().newBuilder()
            .apply { trimmed.split('/').forEach { addPathSegment(it) } }
            .build()
        return url.encodedPath
    }

    @Throws(IOException::class)
    private fun getOneDriveMetadata(path: Path): CloudFile {
        val encodedPath = encodeOneDrivePath(path.pathString)
        val url = if (encodedPath.isEmpty()) {
            "https://graph.microsoft.com/v1.0/me/drive/root?\$select=id,name,size,folder,file,createdDateTime,lastModifiedDateTime"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/root:$encodedPath?\$select=id,name,size,folder,file,createdDateTime,lastModifiedDateTime"
        }
        val request = Request.Builder().url(url).get().build()
        getClient(path.authority).newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toFileSystemException(path, response.code, body)
            }
            return parseOneDriveItem(JSONObject(body))
        }
    }

    @Throws(IOException::class)
    private fun listOneDriveChildren(directory: Path): List<CloudFile> {
        val encodedPath = encodeOneDrivePath(directory.pathString)
        var nextUrl: String? = if (encodedPath.isEmpty()) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children?\$top=1000&\$select=id,name,size,folder,file,createdDateTime,lastModifiedDateTime"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/root:$encodedPath:/children?\$top=1000&\$select=id,name,size,folder,file,createdDateTime,lastModifiedDateTime"
        }
        val result = mutableListOf<CloudFile>()
        while (nextUrl != null) {
            val request = Request.Builder().url(nextUrl).get().build()
            getClient(directory.authority).newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw toFileSystemException(directory, response.code, body)
                }
                val json = JSONObject(body)
                val items = json.optJSONArray("value") ?: JSONArray()
                for (i in 0 until items.length()) {
                    result += parseOneDriveItem(items.getJSONObject(i))
                }
                nextUrl = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
            }
        }
        return result
    }

    private fun parseOneDriveItem(json: JSONObject): CloudFile {
        val id = json.getString("id")
        val name = json.optString("name", id)
        val isDirectory = json.has("folder")
        val size = if (isDirectory) 0L else json.optLong("size", 0L)
        val modifiedTime = parseInstantOrEpoch(json.optString("lastModifiedDateTime"))
        val createdTime = parseInstantOrEpoch(
            json.optString("createdDateTime", json.optString("lastModifiedDateTime"))
        )
        return CloudFile(id, name, isDirectory, size, modifiedTime, createdTime)
    }

    @Throws(IOException::class)
    private fun createOneDriveDirectory(directory: Path): CloudFile {
        val parent = directory.parentPath ?: throw FileSystemException(directory.toString())
        val encodedParent = encodeOneDrivePath(parent.pathString)
        val url = if (encodedParent.isEmpty()) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/root:$encodedParent:/children"
        }
        val name = directory.fileNameString ?: throw FileSystemException(directory.toString())
        val payload = JSONObject()
            .put("name", name)
            .put("folder", JSONObject())
            .put("@microsoft.graph.conflictBehavior", "fail")
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(directory.authority).newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toFileSystemException(directory, response.code, body)
            }
            return parseOneDriveItem(JSONObject(body))
        }
    }

    @Throws(IOException::class)
    private fun deleteOneDrive(path: Path) {
        val encodedPath = encodeOneDrivePath(path.pathString)
        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive/root:$encodedPath")
            .delete()
            .build()
        getClient(path.authority).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                throw toFileSystemException(path, response.code, body)
            }
        }
    }

    @Throws(IOException::class)
    private fun moveOneDrive(source: Path, target: Path) {
        val targetParent = target.parentPath ?: throw FileSystemException(target.toString())
        val targetParentMeta = if (targetParent.pathString == "/" || targetParent.pathString.isEmpty()) {
            getOneDriveMetadata(targetParent)
        } else {
            getMetadata(targetParent)
        }
        val targetName = target.fileNameString ?: throw FileSystemException(target.toString())
        val encodedSource = encodeOneDrivePath(source.pathString)
        val payload = JSONObject()
            .put("name", targetName)
            .put("parentReference", JSONObject().put("id", targetParentMeta.id))
        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive/root:$encodedSource")
            .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(source.authority).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                throw toFileSystemException(source, response.code, body)
            }
        }
    }

    // ======================== Dropbox Implementation ========================

    @Throws(IOException::class)
    private fun getDropboxMetadata(path: Path): CloudFile {
        if (path.pathString == "/" || path.pathString.isEmpty()) {
            return rootCloudFile()
        }
        val payload = JSONObject().put("path", path.pathString)
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/get_metadata")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(path.authority).newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toFileSystemException(path, response.code, body)
            }
            return parseDropboxEntry(JSONObject(body))
        }
    }

    @Throws(IOException::class)
    private fun listDropboxChildren(directory: Path): List<CloudFile> {
        val dropboxPath = if (directory.pathString == "/") "" else directory.pathString
        val result = mutableListOf<CloudFile>()
        val initialPayload = JSONObject()
            .put("path", dropboxPath)
            .put("limit", 2000)
        var request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(initialPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        while (true) {
            getClient(directory.authority).newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw toFileSystemException(directory, response.code, body)
                }
                val json = JSONObject(body)
                val entries = json.optJSONArray("entries") ?: JSONArray()
                for (i in 0 until entries.length()) {
                    val entryJson = entries.getJSONObject(i)
                    val tag = entryJson.optString(".tag")
                    if (tag == "file" || tag == "folder") {
                        result += parseDropboxEntry(entryJson)
                    }
                }
                val hasMore = json.optBoolean("has_more", false)
                if (!hasMore) {
                    return result
                }
                val cursor = json.getString("cursor")
                val continuePayload = JSONObject().put("cursor", cursor)
                request = Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/list_folder/continue")
                    .post(continuePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            }
        }
    }

    private fun parseDropboxEntry(json: JSONObject): CloudFile {
        val tag = json.optString(".tag")
        val id = json.optString("id", json.optString("path_lower", ""))
        val name = json.getString("name")
        val isDirectory = tag == "folder"
        val size = if (isDirectory) 0L else json.optLong("size", 0L)
        val modifiedTime = parseInstantOrEpoch(json.optString("server_modified"))
        val clientModified = parseInstantOrEpoch(
            json.optString("client_modified", json.optString("server_modified"))
        )
        return CloudFile(id, name, isDirectory, size, modifiedTime, clientModified)
    }

    @Throws(IOException::class)
    private fun createDropboxDirectory(directory: Path): CloudFile {
        val payload = JSONObject()
            .put("path", directory.pathString)
            .put("autorename", false)
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/create_folder_v2")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(directory.authority).newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw toFileSystemException(directory, response.code, body)
            }
            val metadata = JSONObject(body).optJSONObject("metadata")
            return CloudFile(
                id = metadata?.optString("id") ?: directory.pathString,
                name = directory.fileNameString.orEmpty(),
                isDirectory = true,
                size = 0L,
                lastModifiedTime = Instant.now(),
                creationTime = Instant.now()
            )
        }
    }

    @Throws(IOException::class)
    private fun deleteDropbox(path: Path) {
        val payload = JSONObject().put("path", path.pathString)
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/delete_v2")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(path.authority).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                throw toFileSystemException(path, response.code, body)
            }
        }
    }

    @Throws(IOException::class)
    private fun moveDropbox(source: Path, target: Path) {
        val payload = JSONObject()
            .put("from_path", source.pathString)
            .put("to_path", target.pathString)
            .put("autorename", false)
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/move_v2")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        getClient(source.authority).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                throw toFileSystemException(source, response.code, body)
            }
        }
    }

    private fun toDropboxHeaderJson(json: JSONObject): String {
        val raw = json.toString()
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch.code >= 0x7F) {
                sb.append(String.format("\\u%04x", ch.code))
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun parseInstantOrEpoch(isoString: String?): Instant {
        if (isoString.isNullOrEmpty()) {
            return Instant.EPOCH
        }
        return try {
            Instant.parse(isoString)
        } catch (e: Exception) {
            Instant.EPOCH
        }
    }

    private fun toFileSystemException(path: Path, code: Int, body: String): FileSystemException {
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            return NoSuchFileException(path.toString())
        }
        if (code == HttpURLConnection.HTTP_CONFLICT) {
            if (body.contains("not_found")) {
                return NoSuchFileException(path.toString())
            }
            if (body.contains("conflict")) {
                return FileAlreadyExistsException(path.toString())
            }
        }
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
            return AccessDeniedException(path.toString(), null, "HTTP $code: $body")
        }
        return FileSystemException(path.toString(), null, "HTTP $code: $body")
    }

    interface Path {
        val authority: Authority
        val pathString: String
        val fileNameString: String?
        val parentPath: Path?
        fun resolve(other: String): Path
    }

    private class OAuthAuthenticatorInterceptor(
        private val authority: Authority
    ) : okhttp3.Authenticator, Interceptor {
        private val refreshLock = Any()

        private fun shouldAttachBearerToken(host: String): Boolean =
            when (authority.provider) {
                CloudProvider.GDRIVE -> host.endsWith("googleapis.com", ignoreCase = true)
                CloudProvider.ONEDRIVE -> host.equals("graph.microsoft.com", ignoreCase = true)
                CloudProvider.DROPBOX -> host.endsWith("dropboxapi.com", ignoreCase = true)
            }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!shouldAttachBearerToken(request.url.host)) {
                return chain.proceed(request)
            }
            var auth = authenticator.getAuthentication(authority)
                ?: throw IOException("No authentication found for $authority")
            if (auth.isExpired() && auth.refreshToken.isNotEmpty()) {
                synchronized(refreshLock) {
                    val current = authenticator.getAuthentication(authority) ?: auth
                    auth = if (current.isExpired() && current.refreshToken.isNotEmpty()) {
                        OAuth2Helper.refreshAccessToken(authority.provider, current).also {
                            authenticator.onAuthenticationRefreshed(authority, it)
                        }
                    } else {
                        current
                    }
                }
            }
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer ${auth.accessToken}")
                .build()
            return chain.proceed(authenticatedRequest)
        }

        override fun authenticate(route: Route?, response: Response): Request? {
            if (!shouldAttachBearerToken(response.request.url.host)) {
                return null
            }
            if (response.request.header("X-OAuth-Retry") != null) {
                return null
            }
            synchronized(refreshLock) {
                val current = authenticator.getAuthentication(authority) ?: return null
                if (current.refreshToken.isEmpty()) {
                    return null
                }
                val usedHeader = response.request.header("Authorization")
                val refreshed = if (usedHeader != "Bearer ${current.accessToken}") {
                    current
                } else {
                    try {
                        OAuth2Helper.refreshAccessToken(authority.provider, current).also {
                            authenticator.onAuthenticationRefreshed(authority, it)
                        }
                    } catch (e: IOException) {
                        return null
                    }
                }
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.accessToken}")
                    .header("X-OAuth-Retry", "true")
                    .build()
            }
        }
    }
}

