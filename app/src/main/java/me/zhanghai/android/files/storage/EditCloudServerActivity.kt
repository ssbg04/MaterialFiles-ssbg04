package me.zhanghai.android.files.storage

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs

class EditCloudServerActivity : AppActivity() {
    private val args by args<EditCloudServerFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = EditCloudServerFragment().putArgs(args)
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        }
        intent?.data?.let { uri ->
            window.decorView.post {
                val fragment = supportFragmentManager.findFragmentById(android.R.id.content) as? EditCloudServerFragment
                fragment?.handleCallbackUri(uri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            val fragment = supportFragmentManager.findFragmentById(android.R.id.content) as? EditCloudServerFragment
            fragment?.handleCallbackUri(uri)
        }
    }
}

