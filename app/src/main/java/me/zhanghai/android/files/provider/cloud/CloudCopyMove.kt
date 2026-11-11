package me.zhanghai.android.files.provider.cloud

import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.NoSuchFileException
import java8.nio.file.StandardCopyOption
import me.zhanghai.android.files.provider.cloud.client.Client
import me.zhanghai.android.files.provider.common.CopyOptions
import me.zhanghai.android.files.provider.common.copyTo
import java.io.IOException

internal object CloudCopyMove {
    @Throws(IOException::class)
    fun copy(source: CloudPath, target: CloudPath, copyOptions: CopyOptions) {
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceFile = Client.getMetadata(source)
        val targetFile = Client.getMetadataOrNull(target)
        val sourceSize = sourceFile.size
        if (targetFile != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            Client.delete(target)
        }
        if (sourceFile.isDirectory) {
            Client.createDirectory(target)
            copyOptions.progressListener?.invoke(sourceSize)
        } else {
            val sourceInputStream = Client.get(source)
            try {
                val targetOutputStream = Client.put(target)
                var successful = false
                try {
                    sourceInputStream.copyTo(
                        targetOutputStream,
                        copyOptions.progressIntervalMillis,
                        copyOptions.progressListener
                    )
                    successful = true
                } finally {
                    try {
                        targetOutputStream.close()
                    } finally {
                        if (!successful) {
                            try {
                                Client.delete(target)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } finally {
                sourceInputStream.close()
            }
        }
    }

    @Throws(IOException::class)
    fun move(source: CloudPath, target: CloudPath, copyOptions: CopyOptions) {
        val sourceFile = Client.getMetadata(source)
        val targetFile = Client.getMetadataOrNull(target)
        val sourceSize = sourceFile.size
        if (targetFile != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            Client.delete(target)
        }
        var renameSuccessful = false
        try {
            Client.move(source, target)
            renameSuccessful = true
        } catch (e: IOException) {
            if (copyOptions.atomicMove) {
                throw e
            }
        }
        if (renameSuccessful) {
            copyOptions.progressListener?.invoke(sourceSize)
            return
        }
        if (copyOptions.atomicMove) {
            throw AssertionError()
        }
        var effectiveOptions = copyOptions
        if (!effectiveOptions.copyAttributes || !effectiveOptions.noFollowLinks) {
            effectiveOptions = CopyOptions(
                effectiveOptions.replaceExisting,
                true,
                false,
                true,
                effectiveOptions.progressIntervalMillis,
                effectiveOptions.progressListener
            )
        }
        copy(source, target, effectiveOptions)
        try {
            Client.delete(source)
        } catch (e: IOException) {
            if (e !is NoSuchFileException) {
                try {
                    Client.delete(target)
                } catch (e2: IOException) {
                    e.addSuppressed(e2)
                }
            }
            throw e
        }
    }
}

