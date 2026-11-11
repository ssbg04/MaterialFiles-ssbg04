package me.zhanghai.android.files.provider.cloud.client

import android.net.Uri
import android.util.Base64
import me.zhanghai.android.files.app.okHttpClient
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

object OAuth2Helper {
    const val DEFAULT_LOOPBACK_PORT = 53682
    private const val LOOPBACK_HOST = "127.0.0.1"
    private const val SOCKET_TIMEOUT_MILLIS = 300_000

    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    @Throws(IOException::class)
    fun createLoopbackServer(): ServerSocket {
        val loopbackAddress = InetAddress.getByName(LOOPBACK_HOST)
        return try {
            ServerSocket().apply {
                reuseAddress = true
                soTimeout = SOCKET_TIMEOUT_MILLIS
                bind(InetSocketAddress(loopbackAddress, DEFAULT_LOOPBACK_PORT))
            }
        } catch (e: IOException) {
            ServerSocket().apply {
                reuseAddress = true
                soTimeout = SOCKET_TIMEOUT_MILLIS
                bind(InetSocketAddress(loopbackAddress, 0))
            }
        }
    }

    fun getRedirectUri(port: Int): String = "http://$LOOPBACK_HOST:$port/"

    fun buildAuthorizationUrl(
        provider: CloudProvider,
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String
    ): Uri {
        val builder = provider.authEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
        when (provider) {
            CloudProvider.GDRIVE -> {
                builder.addQueryParameter("scope", provider.scope)
                builder.addQueryParameter("access_type", "offline")
                builder.addQueryParameter("prompt", "consent")
            }
            CloudProvider.ONEDRIVE -> {
                builder.addQueryParameter("scope", provider.scope)
                builder.addQueryParameter("prompt", "consent")
            }
            CloudProvider.DROPBOX -> {
                builder.addQueryParameter("token_access_type", "offline")
            }
        }
        return Uri.parse(builder.build().toString())
    }

    @Throws(IOException::class)
    fun awaitLoopbackAuthCode(serverSocket: ServerSocket, expectedState: String): String {
        serverSocket.use { listener ->
            while (true) {
                listener.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = BufferedReader(
                        InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    )
                    val requestLine = reader.readLine() ?: continue
                    val parts = requestLine.split(" ")
                    if (parts.size < 2) {
                        continue
                    }
                    val pathAndQuery = parts[1]
                    if (pathAndQuery.startsWith("/favicon.ico")) {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                        socket.getOutputStream().write(notFound.toByteArray(StandardCharsets.UTF_8))
                        continue
                    }
                    val uri = Uri.parse("http://$LOOPBACK_HOST$pathAndQuery")
                    val error = uri.getQueryParameter("error")
                    val errorDescription = uri.getQueryParameter("error_description")
                    val code = uri.getQueryParameter("code")
                    val state = uri.getQueryParameter("state")

                    val isSuccess = error == null && !code.isNullOrEmpty() && state == expectedState
                    val htmlBody = if (isSuccess) {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="utf-8"><title>Material Files</title></head>
                        <body style="font-family:sans-serif;text-align:center;padding:40px;">
                        <h2>Authentication Successful</h2>
                        <p>You can close this tab and return to Material Files.</p>
                        <script>window.close();</script>
                        </body>
                        </html>
                        """.trimIndent()
                    } else {
                        val msg = errorDescription ?: error ?: "Invalid OAuth state or missing code"
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="utf-8"><title>Material Files</title></head>
                        <body style="font-family:sans-serif;text-align:center;padding:40px;">
                        <h2>Authentication Failed</h2>
                        <p>$msg</p>
                        </body>
                        </html>
                        """.trimIndent()
                    }
                    val bodyBytes = htmlBody.toByteArray(StandardCharsets.UTF_8)
                    val responseHeader = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/html; charset=utf-8\r\n")
                        append("Content-Length: ${bodyBytes.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    val output = socket.getOutputStream()
                    output.write(responseHeader.toByteArray(StandardCharsets.UTF_8))
                    output.write(bodyBytes)
                    output.flush()

                    if (error != null) {
                        throw IOException("OAuth error: ${errorDescription ?: error}")
                    }
                    if (state != expectedState) {
                        throw IOException("OAuth state mismatch")
                    }
                    if (code.isNullOrEmpty()) {
                        throw IOException("OAuth authorization code missing")
                    }
                    return code
                }
            }
        }
    }

    @Throws(IOException::class)
    fun exchangeAuthorizationCode(
        provider: CloudProvider,
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
        codeVerifier: String
    ): Authentication {
        val formBuilder = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .add("code_verifier", codeVerifier)
        if (clientSecret.isNotEmpty()) {
            formBuilder.add("client_secret", clientSecret)
        }
        val request = Request.Builder()
            .url(provider.tokenEndpoint)
            .post(formBuilder.build())
            .header("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("Token exchange failed (${response.code}): $responseBody")
            }
            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.optLong("expires_in", 3600L)
            val expiryMillis = System.currentTimeMillis() + expiresIn * 1000L
            return Authentication(
                clientId = clientId,
                clientSecret = clientSecret,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpiryMillis = expiryMillis
            )
        }
    }

    @Throws(IOException::class)
    fun refreshAccessToken(
        provider: CloudProvider,
        authentication: Authentication
    ): Authentication {
        if (authentication.refreshToken.isEmpty()) {
            throw IOException("Cannot refresh token: refresh_token is empty")
        }
        val clientId = authentication.clientId.ifEmpty { provider.defaultClientId }
        val clientSecret = authentication.clientSecret.ifEmpty { provider.defaultClientSecret }
        val formBuilder = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", authentication.refreshToken)
            .add("grant_type", "refresh_token")
        if (clientSecret.isNotEmpty()) {
            formBuilder.add("client_secret", clientSecret)
        }
        val request = Request.Builder()
            .url(provider.tokenEndpoint)
            .post(formBuilder.build())
            .header("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("Token refresh failed (${response.code}): $responseBody")
            }
            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val newRefreshToken = json.optString("refresh_token", authentication.refreshToken)
                .ifEmpty { authentication.refreshToken }
            val expiresIn = json.optLong("expires_in", 3600L)
            val expiryMillis = System.currentTimeMillis() + expiresIn * 1000L
            return authentication.copy(
                accessToken = accessToken,
                refreshToken = newRefreshToken,
                accessTokenExpiryMillis = expiryMillis
            )
        }
    }

    @Throws(IOException::class)
    fun fetchAccountUsername(provider: CloudProvider, accessToken: String): String {
        val request = when (provider) {
            CloudProvider.GDRIVE ->
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)")
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
            CloudProvider.ONEDRIVE ->
                Request.Builder()
                    .url("https://graph.microsoft.com/v1.0/me")
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
            CloudProvider.DROPBOX ->
                Request.Builder()
                    .url("https://api.dropboxapi.com/2/users/get_current_account")
                    .post("null".toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $accessToken")
                    .build()
        }
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                return provider.displayName
            }
            val json = JSONObject(body)
            return when (provider) {
                CloudProvider.GDRIVE -> {
                    val user = json.optJSONObject("user")
                    user?.optString("emailAddress")?.takeIf { it.isNotEmpty() }
                        ?: user?.optString("displayName")?.takeIf { it.isNotEmpty() }
                        ?: provider.displayName
                }
                CloudProvider.ONEDRIVE -> {
                    json.optString("userPrincipalName").takeIf { it.isNotEmpty() }
                        ?: json.optString("mail").takeIf { it.isNotEmpty() }
                        ?: json.optString("displayName").takeIf { it.isNotEmpty() }
                        ?: provider.displayName
                }
                CloudProvider.DROPBOX -> {
                    json.optString("email").takeIf { it.isNotEmpty() }
                        ?: json.optJSONObject("name")?.optString("display_name")
                            ?.takeIf { it.isNotEmpty() }
                        ?: provider.displayName
                }
            }
        }
    }
}

