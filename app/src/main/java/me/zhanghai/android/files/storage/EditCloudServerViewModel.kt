package me.zhanghai.android.files.storage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.provider.cloud.client.Authentication
import me.zhanghai.android.files.provider.cloud.client.CloudProvider
import me.zhanghai.android.files.provider.cloud.client.OAuth2Helper
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.isFinished
import me.zhanghai.android.files.util.isReady
import java.net.ServerSocket

class EditCloudServerViewModel : ViewModel() {
    data class OAuthResult(
        val authentication: Authentication,
        val username: String
    )

    private val _connectState =
        MutableStateFlow<ActionState<CloudServer, Unit>>(ActionState.Ready())
    val connectState = _connectState.asStateFlow()

    private val _oauthState =
        MutableStateFlow<ActionState<CloudProvider, OAuthResult>>(ActionState.Ready())
    val oauthState = _oauthState.asStateFlow()

    private var activeServerSocket: ServerSocket? = null
    private var oauthJob: Job? = null

    fun startOAuthSignIn(
        provider: CloudProvider,
        clientId: String,
        clientSecret: String,
        onLaunchBrowser: (Uri) -> Unit
    ) {
        cancelOAuthSignIn()
        oauthJob = viewModelScope.launch {
            _oauthState.value = ActionState.Running(provider)
            _oauthState.value = try {
                val serverSocket = withContext(Dispatchers.IO) {
                    OAuth2Helper.createLoopbackServer()
                }
                activeServerSocket = serverSocket
                val redirectUri = OAuth2Helper.getRedirectUri(serverSocket.localPort)
                val codeVerifier = OAuth2Helper.generateCodeVerifier()
                val codeChallenge = OAuth2Helper.generateCodeChallenge(codeVerifier)
                val state = OAuth2Helper.generateState()
                val authUri = OAuth2Helper.buildAuthorizationUrl(
                    provider,
                    clientId,
                    redirectUri,
                    codeChallenge,
                    state
                )
                onLaunchBrowser(authUri)
                val result = withContext(Dispatchers.IO) {
                    val code = OAuth2Helper.awaitLoopbackAuthCode(serverSocket, state)
                    val auth = OAuth2Helper.exchangeAuthorizationCode(
                        provider,
                        clientId,
                        clientSecret,
                        code,
                        redirectUri,
                        codeVerifier
                    )
                    val username = OAuth2Helper.fetchAccountUsername(provider, auth.accessToken)
                    OAuthResult(auth, username)
                }
                ActionState.Success(provider, result)
            } catch (e: Exception) {
                ActionState.Error(provider, e)
            } finally {
                closeActiveSocket()
            }
        }
    }

    fun cancelOAuthSignIn() {
        closeActiveSocket()
        oauthJob?.cancel()
        oauthJob = null
        _oauthState.value = ActionState.Ready()
    }

    fun finishOAuthSignIn() {
        if (_oauthState.value.isFinished) {
            _oauthState.value = ActionState.Ready()
        }
    }

    private fun closeActiveSocket() {
        try {
            activeServerSocket?.close()
        } catch (ignored: Exception) {
        }
        activeServerSocket = null
    }

    fun connect(server: CloudServer) {
        viewModelScope.launch {
            check(_connectState.value.isReady)
            _connectState.value = ActionState.Running(server)
            _connectState.value = try {
                runInterruptible(Dispatchers.IO) {
                    CloudServerAuthenticator.addTransientServer(server)
                    try {
                        val path = server.path
                        path.fileSystem.use {
                            path.newDirectoryStream().toList()
                        }
                    } finally {
                        CloudServerAuthenticator.removeTransientServer(server)
                    }
                }
                ActionState.Success(server, Unit)
            } catch (e: Exception) {
                ActionState.Error(server, e)
            }
        }
    }

    fun finishConnecting() {
        viewModelScope.launch {
            check(_connectState.value.isFinished)
            _connectState.value = ActionState.Ready()
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeActiveSocket()
    }
}

