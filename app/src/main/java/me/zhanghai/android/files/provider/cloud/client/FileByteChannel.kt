package me.zhanghai.android.files.provider.cloud.client

import me.zhanghai.android.files.provider.common.AbstractFileByteChannel
import me.zhanghai.android.files.provider.common.EMPTY
import me.zhanghai.android.files.provider.common.readFully
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class FileByteChannel(
    private val path: Client.Path,
    isAppend: Boolean
) : AbstractFileByteChannel(isAppend) {
    private var nextSequentialWritePosition = 0L
    private var sequentialWriteOutputStream: OutputStream? = null

    @Throws(IOException::class)
    override fun onRead(position: Long, size: Int): ByteBuffer {
        val inputStream = Client.getRange(path, position, size)
        val destination = ByteBuffer.allocate(size)
        val limit = inputStream.use {
            it.readFully(destination.array(), destination.arrayOffset(), size)
        }
        if (limit == 0) {
            return ByteBuffer::class.EMPTY
        }
        destination.limit(limit)
        return destination
    }

    @Throws(IOException::class)
    override fun onWrite(position: Long, source: ByteBuffer) {
        if (position != nextSequentialWritePosition) {
            throw IOException("Unsupported non-sequential write")
        }
        val outputStream = sequentialWriteOutputStream
            ?: Client.put(path).also { sequentialWriteOutputStream = it }
        val remaining = source.remaining()
        outputStream.write(
            source.array(),
            source.arrayOffset() + source.position(),
            remaining
        )
        source.position(source.limit())
        nextSequentialWritePosition += remaining
    }

    @Throws(IOException::class)
    override fun onTruncate(size: Long) {
        if (size == 0L) {
            Client.makeFile(path)
        } else {
            throw IOException("Unsupported truncate to non-zero size")
        }
    }

    @Throws(IOException::class)
    override fun onSize(): Long = Client.getMetadata(path).size

    @Throws(IOException::class)
    override fun onClose() {
        sequentialWriteOutputStream?.close()
    }
}

