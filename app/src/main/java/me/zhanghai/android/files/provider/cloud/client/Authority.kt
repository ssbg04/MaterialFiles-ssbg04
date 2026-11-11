package me.zhanghai.android.files.provider.cloud.client

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.provider.common.UriAuthority
import me.zhanghai.android.files.util.takeIfNotEmpty

@Parcelize
data class Authority(
    val provider: CloudProvider,
    val username: String
) : Parcelable {
    fun toUriAuthority(): UriAuthority {
        val userInfo = username.takeIfNotEmpty()
        return UriAuthority(userInfo, provider.host, null)
    }

    override fun toString(): String = toUriAuthority().toString()
}

