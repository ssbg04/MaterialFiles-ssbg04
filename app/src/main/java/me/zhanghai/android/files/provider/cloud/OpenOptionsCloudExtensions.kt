package me.zhanghai.android.files.provider.cloud

import java8.nio.file.StandardOpenOption
import me.zhanghai.android.files.provider.common.OpenOptions

internal fun OpenOptions.checkForCloud() {
    if (deleteOnClose) {
        throw UnsupportedOperationException(StandardOpenOption.DELETE_ON_CLOSE.toString())
    }
    if (sync) {
        throw UnsupportedOperationException(StandardOpenOption.SYNC.toString())
    }
    if (dsync) {
        throw UnsupportedOperationException(StandardOpenOption.DSYNC.toString())
    }
}

