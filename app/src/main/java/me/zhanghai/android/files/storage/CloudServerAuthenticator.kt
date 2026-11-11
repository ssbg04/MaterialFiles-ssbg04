package me.zhanghai.android.files.storage

import me.zhanghai.android.files.app.mainExecutor
import me.zhanghai.android.files.provider.cloud.client.Authentication
import me.zhanghai.android.files.provider.cloud.client.Authenticator
import me.zhanghai.android.files.provider.cloud.client.Authority
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import java.util.concurrent.ConcurrentHashMap

object CloudServerAuthenticator : Authenticator {
    private val transientServers = mutableSetOf<CloudServer>()
    private val refreshedTokens = ConcurrentHashMap<Authority, Authentication>()

    override fun getAuthentication(authority: Authority): Authentication? {
        refreshedTokens[authority]?.let { return it }
        val server = synchronized(transientServers) {
            transientServers.find { it.authority == authority }
        } ?: Settings.STORAGES.valueCompat.find {
            it is CloudServer && it.authority == authority
        } as CloudServer?
        return server?.authentication
    }

    override fun onAuthenticationRefreshed(authority: Authority, authentication: Authentication) {
        refreshedTokens[authority] = authentication
        mainExecutor.execute {
            val existing = Settings.STORAGES.valueCompat.find {
                it is CloudServer && it.authority == authority
            } as CloudServer?
            if (existing != null) {
                Storages.replace(
                    CloudServer(
                        existing.id,
                        existing.customName,
                        existing.authority,
                        authentication,
                        existing.relativePath
                    )
                )
            }
        }
    }

    fun addTransientServer(server: CloudServer) {
        refreshedTokens.remove(server.authority)
        synchronized(transientServers) { transientServers += server }
    }

    fun removeTransientServer(server: CloudServer) {
        synchronized(transientServers) { transientServers -= server }
    }
}

