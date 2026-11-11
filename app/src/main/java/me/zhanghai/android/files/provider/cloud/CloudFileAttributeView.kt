package me.zhanghai.android.files.provider.cloud

import java8.nio.file.LinkOption
import java8.nio.file.attribute.BasicFileAttributeView
import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.provider.cloud.client.Client
import java.io.IOException

internal class CloudFileAttributeView(
    private val path: CloudPath,
    private val noFollowLinks: Boolean
) : BasicFileAttributeView {
    override fun name(): String = NAME

    @Throws(IOException::class)
    override fun readAttributes(): CloudFileAttributes {
        val file = Client.getMetadata(path)
        return CloudFileAttributes.from(file, path)
    }

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        if (lastModifiedTime == null) {
            if (lastAccessTime != null) {
                throw UnsupportedOperationException("lastAccessTime")
            }
            if (createTime != null) {
                throw UnsupportedOperationException("createTime")
            }
            return
        }
        if (noFollowLinks) {
            throw UnsupportedOperationException(LinkOption.NOFOLLOW_LINKS.toString())
        }
    }

    companion object {
        private val NAME = CloudFileSystemProvider.scheme

        val SUPPORTED_NAMES = setOf("basic", NAME)
    }
}

