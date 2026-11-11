package me.zhanghai.android.files.provider.cloud.client

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Authentication(
    val clientId: String,
    val clientSecret: String = "",
    val accessToken: String,
    val refreshToken: String = "",
    val accessTokenExpiryMillis: Long = 0L
) : Parcelable {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        accessToken.isEmpty() || (accessTokenExpiryMillis > 0L && nowMillis >= accessTokenExpiryMillis - 60_000L)
}

