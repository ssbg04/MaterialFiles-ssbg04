package me.zhanghai.android.files.provider.cloud

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import me.zhanghai.android.files.provider.cloud.client.Authority
import me.zhanghai.android.files.provider.cloud.client.Client
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.UriAuthority
import me.zhanghai.android.files.util.readParcelable
import java.io.File
import java.io.IOException

internal class CloudPath : ByteStringListPath<CloudPath>, Client.Path {
    private val fileSystem: CloudFileSystem

    constructor(
        fileSystem: CloudFileSystem,
        path: ByteString
    ) : super(CloudFileSystem.SEPARATOR, path) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: CloudFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(CloudFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == CloudFileSystem.SEPARATOR

    override fun createPath(path: ByteString): CloudPath = CloudPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): CloudPath =
        CloudPath(fileSystem, absolute, segments)

    override val uriScheme: String
        get() = fileSystem.authority.provider.scheme

    override val uriAuthority: UriAuthority
        get() = fileSystem.authority.toUriAuthority()

    override val defaultDirectory: CloudPath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): FileSystem = fileSystem

    override fun getRoot(): CloudPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): CloudPath {
        throw UnsupportedOperationException()
    }

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        if (watcher !is LocalWatchService) {
            throw ProviderMismatchException(watcher.toString())
        }
        return watcher.register(this, events, *modifiers)
    }

    override val authority: Authority
        get() = fileSystem.authority

    override val pathString: String
        get() = toString()

    override val fileNameString: String?
        get() = fileName?.toString()

    override val parentPath: Client.Path?
        get() = parent

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<CloudPath> {
            override fun createFromParcel(source: Parcel): CloudPath = CloudPath(source)

            override fun newArray(size: Int): Array<CloudPath?> = arrayOfNulls(size)
        }
    }
}

val Path.isCloudPath: Boolean
    get() = this is CloudPath

