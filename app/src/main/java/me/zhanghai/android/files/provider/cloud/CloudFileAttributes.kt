package me.zhanghai.android.files.provider.cloud

import android.os.Parcelable
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.provider.cloud.client.CloudFile
import me.zhanghai.android.files.provider.common.AbstractBasicFileAttributes
import me.zhanghai.android.files.provider.common.BasicFileType
import me.zhanghai.android.files.provider.common.FileTimeParceler

@Parcelize
internal data class CloudFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: BasicFileType,
    override val size: Long,
    override val fileKey: Parcelable
) : AbstractBasicFileAttributes() {
    companion object {
        fun from(file: CloudFile, path: CloudPath): CloudFileAttributes {
            val lastModifiedTime = FileTime.from(file.lastModifiedTime)
            val lastAccessTime = lastModifiedTime
            val creationTime = FileTime.from(file.creationTime)
            val type = if (file.isDirectory) {
                BasicFileType.DIRECTORY
            } else {
                BasicFileType.REGULAR_FILE
            }
            return CloudFileAttributes(
                lastModifiedTime = lastModifiedTime,
                lastAccessTime = lastAccessTime,
                creationTime = creationTime,
                type = type,
                size = file.size,
                fileKey = path
            )
        }
    }
}

