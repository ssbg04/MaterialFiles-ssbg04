package me.zhanghai.android.files.provider.cloud.client

import java.time.Instant

data class CloudFile(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedTime: Instant,
    val creationTime: Instant
)

