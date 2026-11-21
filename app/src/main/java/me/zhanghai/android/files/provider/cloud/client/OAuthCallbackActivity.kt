/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.cloud.client

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import me.zhanghai.android.files.storage.EditCloudServerActivity

class OAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        val targetIntent = Intent(this, EditCloudServerActivity::class.java).apply {
            data = uri
            this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(targetIntent)
    }
}
