package me.zhanghai.android.files.provider.cloud.client

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

object ResilientDns : Dns {
    private val fallbackDnsIps = mapOf(
        "oauth2.googleapis.com" to listOf(
            "142.250.190.42",
            "172.217.168.202",
            "142.250.72.202",
            "142.250.191.10",
            "108.177.125.95"
        ),
        "www.googleapis.com" to listOf(
            "172.217.112.4",
            "142.250.190.74",
            "172.217.168.202",
            "142.250.72.202",
            "172.217.24.138"
        ),
        "accounts.google.com" to listOf(
            "142.250.190.45",
            "172.217.168.205",
            "142.250.72.205",
            "108.177.125.84"
        ),
        "drive.google.com" to listOf(
            "142.250.190.46",
            "172.217.168.206",
            "142.250.72.206",
            "142.250.191.14"
        ),
        "graph.microsoft.com" to listOf(
            "20.190.159.0",
            "40.126.31.71",
            "20.190.159.2",
            "40.126.31.69"
        ),
        "login.microsoftonline.com" to listOf(
            "20.190.159.0",
            "40.126.31.71",
            "20.190.159.2",
            "40.126.31.69"
        ),
        "api.dropboxapi.com" to listOf(
            "162.125.6.19",
            "162.125.4.19",
            "162.125.1.19"
        ),
        "content.dropboxapi.com" to listOf(
            "162.125.6.19",
            "162.125.4.19",
            "162.125.1.19"
        )
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val lowerHost = hostname.lowercase()
        return try {
            val systemAddresses = Dns.SYSTEM.lookup(lowerHost)
            if (systemAddresses.isNotEmpty()) {
                systemAddresses
            } else {
                resolveFallback(lowerHost) ?: throw UnknownHostException("No address found for $hostname")
            }
        } catch (e: UnknownHostException) {
            resolveFallback(lowerHost) ?: throw e
        } catch (e: Exception) {
            resolveFallback(lowerHost) ?: throw UnknownHostException("Unable to resolve host $hostname: ${e.message}")
        }
    }

    private fun resolveFallback(hostname: String): List<InetAddress>? {
        val ips = fallbackDnsIps[hostname] ?: return null
        return ips.mapNotNull { ip ->
            try {
                InetAddress.getByAddress(hostname, InetAddress.getByName(ip).address)
            } catch (ignored: Exception) {
                null
            }
        }.takeIf { it.isNotEmpty() }
    }
}
