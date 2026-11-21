/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.add
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity

class FileRuleListActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add<FileRuleListFragment>(android.R.id.content)
            }
        }
    }
}
