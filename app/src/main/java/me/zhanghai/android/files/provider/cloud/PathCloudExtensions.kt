package me.zhanghai.android.files.provider.cloud

import java8.nio.file.Path
import me.zhanghai.android.files.provider.cloud.client.Authority

fun Authority.createCloudRootPath(): Path =
    CloudFileSystemProvider.getOrNewFileSystem(this).rootDirectory

