/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getArgsOrNull
import me.zhanghai.android.files.util.putArgs

class EditFileRuleActivity : AppActivity() {
    private val args: EditFileRuleFragment.Args? by lazy {
        intent.extras?.getArgsOrNull(EditFileRuleFragment.Args::class)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = EditFileRuleFragment().putArgs(args ?: EditFileRuleFragment.Args())
            supportFragmentManager.commit {
                add(android.R.id.content, fragment)
            }
        }
    }
}
