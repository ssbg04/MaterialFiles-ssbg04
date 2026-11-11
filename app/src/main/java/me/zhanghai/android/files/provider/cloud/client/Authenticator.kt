package me.zhanghai.android.files.provider.cloud.client

interface Authenticator {
    fun getAuthentication(authority: Authority): Authentication?

    fun onAuthenticationRefreshed(authority: Authority, authentication: Authentication)
}

