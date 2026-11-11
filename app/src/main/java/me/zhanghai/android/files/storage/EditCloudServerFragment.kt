package me.zhanghai.android.files.storage

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.EditCloudServerFragmentBinding
import me.zhanghai.android.files.provider.cloud.client.Authentication
import me.zhanghai.android.files.provider.cloud.client.Authority
import me.zhanghai.android.files.provider.cloud.client.CloudProvider
import me.zhanghai.android.files.ui.UnfilteredArrayAdapter
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.fadeToVisibilityUnsafe
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.getTextArray
import me.zhanghai.android.files.util.hideTextInputLayoutErrorOnTextChange
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.setResult
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.takeIfNotEmpty
import me.zhanghai.android.files.util.viewModels

class EditCloudServerFragment : Fragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { EditCloudServerViewModel() } }

    private lateinit var binding: EditCloudServerFragmentBinding

    private var tokenExpiryMillis: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenStarted {
            launch { viewModel.connectState.collect { onConnectStateChanged(it) } }
            launch { viewModel.oauthState.collect { onOAuthStateChanged(it) } }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        EditCloudServerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            activity.setTitle(
                if (args.server != null) {
                    R.string.storage_edit_cloud_server_title_edit
                } else {
                    R.string.storage_edit_cloud_server_title_add
                }
            )
        }

        binding.providerEdit.setAdapter(
            UnfilteredArrayAdapter(
                binding.providerEdit.context,
                R.layout.dropdown_item,
                objects = getTextArray(R.array.storage_edit_cloud_server_provider_entries)
            )
        )
        provider = args.provider ?: CloudProvider.GDRIVE
        binding.providerEdit.doAfterTextChanged {
            onProviderChanged(provider)
            updateNamePlaceholder()
        }

        binding.clientIdEdit.hideTextInputLayoutErrorOnTextChange(binding.clientIdLayout)
        binding.accessTokenEdit.hideTextInputLayoutErrorOnTextChange(binding.accessTokenLayout)
        binding.usernameEdit.doAfterTextChanged { updateNamePlaceholder() }
        binding.pathEdit.doAfterTextChanged { updateNamePlaceholder() }

        binding.oauthSignInButton.setOnClickListener {
            startOAuthSignIn()
        }

        binding.saveOrConnectAndAddButton.setText(
            if (args.server != null) {
                R.string.save
            } else {
                R.string.storage_edit_cloud_server_connect_and_add
            }
        )
        binding.saveOrConnectAndAddButton.setOnClickListener {
            if (args.server != null) {
                saveOrAdd()
            } else {
                connectAndAdd()
            }
        }
        binding.cancelButton.setOnClickListener {
            viewModel.cancelOAuthSignIn()
            finish()
        }
        binding.removeOrAddButton.setText(
            if (args.server != null) R.string.remove else R.string.storage_edit_cloud_server_add
        )
        binding.removeOrAddButton.setOnClickListener {
            if (args.server != null) {
                remove()
            } else {
                saveOrAdd()
            }
        }

        if (savedInstanceState == null) {
            val server = args.server
            if (server != null) {
                val authority = server.authority
                provider = authority.provider
                binding.usernameEdit.setText(authority.username)
                val authentication = server.authentication
                binding.clientIdEdit.setText(
                    authentication.clientId.ifEmpty { authority.provider.defaultClientId }
                )
                binding.clientSecretEdit.setText(
                    authentication.clientSecret.ifEmpty { authority.provider.defaultClientSecret }
                )
                binding.accessTokenEdit.setText(authentication.accessToken)
                binding.refreshTokenEdit.setText(authentication.refreshToken)
                tokenExpiryMillis = authentication.accessTokenExpiryMillis
                binding.pathEdit.setText(server.relativePath)
                binding.nameEdit.setText(server.customName)
            } else {
                onProviderChanged(provider)
            }
            updateNamePlaceholder()
        }
    }

    private fun onProviderChanged(newProvider: CloudProvider) {
        if (binding.clientIdEdit.text.isNullOrEmpty()) {
            binding.clientIdEdit.setText(newProvider.defaultClientId)
        }
        if (binding.clientSecretEdit.text.isNullOrEmpty()) {
            binding.clientSecretEdit.setText(newProvider.defaultClientSecret)
        }
    }

    private fun updateNamePlaceholder() {
        val currentProvider = provider
        val username = binding.usernameEdit.text.toString().trim()
        val path = binding.pathEdit.text.toString().trim()
        val base = if (username.isNotEmpty()) {
            "${currentProvider.displayName} ($username)"
        } else {
            currentProvider.displayName
        }
        binding.nameLayout.placeholderText = if (path.isNotEmpty()) "$base/$path" else base
    }

    private var provider: CloudProvider
        get() {
            val adapter = binding.providerEdit.adapter
            val items = List(adapter.count) { adapter.getItem(it) as CharSequence }
            val selectedItem = binding.providerEdit.text
            val selectedIndex = items.indexOfFirst { TextUtils.equals(it, selectedItem) }
                .coerceAtLeast(0)
            return CloudProvider.entries[selectedIndex]
        }
        set(value) {
            val adapter = binding.providerEdit.adapter
            val item = adapter.getItem(value.ordinal) as CharSequence
            binding.providerEdit.setText(item, false)
        }

    private fun startOAuthSignIn() {
        val currentProvider = provider
        val clientId = binding.clientIdEdit.text.toString().trim()
            .ifEmpty { currentProvider.defaultClientId }
        if (clientId.isEmpty()) {
            binding.clientIdLayout.error =
                getString(R.string.storage_edit_cloud_server_client_id_error_empty)
            binding.clientIdEdit.requestFocus()
            return
        }
        val clientSecret = binding.clientSecretEdit.text.toString().trim()
            .ifEmpty { currentProvider.defaultClientSecret }
        viewModel.startOAuthSignIn(currentProvider, clientId, clientSecret) { authUri ->
            startActivitySafe(Intent(Intent.ACTION_VIEW, authUri))
        }
    }

    private fun onOAuthStateChanged(
        state: ActionState<CloudProvider, EditCloudServerViewModel.OAuthResult>
    ) {
        when (state) {
            is ActionState.Ready -> {
                binding.oauthSignInButton.isEnabled = true
                binding.oauthSignInButton.setText(R.string.storage_edit_cloud_server_oauth_sign_in)
            }
            is ActionState.Running -> {
                binding.oauthSignInButton.isEnabled = false
                binding.oauthSignInButton.setText(
                    R.string.storage_edit_cloud_server_oauth_signing_in
                )
            }
            is ActionState.Success -> {
                binding.oauthSignInButton.isEnabled = true
                binding.oauthSignInButton.setText(R.string.storage_edit_cloud_server_oauth_sign_in)
                val result = state.result
                if (binding.usernameEdit.text.isNullOrEmpty() && result.username.isNotEmpty()) {
                    binding.usernameEdit.setText(result.username)
                }
                binding.accessTokenEdit.setText(result.authentication.accessToken)
                if (result.authentication.refreshToken.isNotEmpty()) {
                    binding.refreshTokenEdit.setText(result.authentication.refreshToken)
                }
                tokenExpiryMillis = result.authentication.accessTokenExpiryMillis
                viewModel.finishOAuthSignIn()
            }
            is ActionState.Error -> {
                binding.oauthSignInButton.isEnabled = true
                binding.oauthSignInButton.setText(R.string.storage_edit_cloud_server_oauth_sign_in)
                val throwable = state.throwable
                throwable.printStackTrace()
                showToast(throwable.toString())
                viewModel.finishOAuthSignIn()
            }
        }
    }

    private fun saveOrAdd() {
        val server = getServerOrSetError() ?: return
        Storages.addOrReplace(server)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun connectAndAdd() {
        if (!viewModel.connectState.value.isReady) {
            return
        }
        val server = getServerOrSetError() ?: return
        viewModel.connect(server)
    }

    private fun onConnectStateChanged(state: ActionState<CloudServer, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> {
                val isConnecting = state is ActionState.Running
                binding.progress.fadeToVisibilityUnsafe(isConnecting)
                binding.scrollView.fadeToVisibilityUnsafe(!isConnecting)
                binding.saveOrConnectAndAddButton.isEnabled = !isConnecting
                binding.removeOrAddButton.isEnabled = !isConnecting
            }
            is ActionState.Success -> {
                Storages.addOrReplace(state.argument)
                setResult(Activity.RESULT_OK)
                finish()
            }
            is ActionState.Error -> {
                val throwable = state.throwable
                throwable.printStackTrace()
                showToast(throwable.toString())
                viewModel.finishConnecting()
            }
        }
    }

    private fun remove() {
        Storages.remove(args.server!!)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun getServerOrSetError(): CloudServer? {
        var errorEdit: TextInputEditText? = null
        val currentProvider = provider
        val clientId = binding.clientIdEdit.text.toString().trim()
            .ifEmpty { currentProvider.defaultClientId }
        val clientSecret = binding.clientSecretEdit.text.toString().trim()
            .ifEmpty { currentProvider.defaultClientSecret }
        val username = binding.usernameEdit.text.toString().trim()
        val accessToken = binding.accessTokenEdit.text.toString().trim().takeIfNotEmpty()
        val refreshToken = binding.refreshTokenEdit.text.toString().trim()
        if (accessToken == null && refreshToken.isEmpty()) {
            binding.accessTokenLayout.error =
                getString(R.string.storage_edit_cloud_server_access_token_error_empty)
            if (errorEdit == null) {
                errorEdit = binding.accessTokenEdit
            }
        }
        if (refreshToken.isNotEmpty() && clientId.isEmpty()) {
            binding.clientIdLayout.error =
                getString(R.string.storage_edit_cloud_server_client_id_error_empty)
            if (errorEdit == null) {
                errorEdit = binding.clientIdEdit
            }
        }
        if (errorEdit != null) {
            errorEdit.requestFocus()
            return null
        }
        val path = binding.pathEdit.text.toString().trim()
        val name = binding.nameEdit.text.toString().takeIfNotEmpty()
        val authority = Authority(currentProvider, username)
        val authentication = Authentication(
            clientId = clientId,
            clientSecret = clientSecret,
            accessToken = accessToken.orEmpty(),
            refreshToken = refreshToken,
            accessTokenExpiryMillis = tokenExpiryMillis
        )
        return CloudServer(args.server?.id, name, authority, authentication, path)
    }

    @Parcelize
    class Args(
        val server: CloudServer? = null,
        val provider: CloudProvider? = null
    ) : ParcelableArgs
}

