/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.graphics.drawable.NinePatchDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FileRuleListFragmentBinding
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.ScrollingViewOnApplyWindowInsetsListener
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.fadeToVisibilityUnsafe
import me.zhanghai.android.files.util.getDrawable
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat

class FileRuleListFragment : Fragment(), FileRuleListAdapter.Listener {

    private lateinit var binding: FileRuleListFragmentBinding

    private lateinit var adapter: FileRuleListAdapter
    private lateinit var dragDropManager: RecyclerViewDragDropManager
    private lateinit var wrappedAdapter: RecyclerView.Adapter<*>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FileRuleListFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        activity.setTitle(R.string.settings_file_rule_list_title)

        binding.toolbar.inflateMenu(R.menu.file_rule_list)
        binding.toolbar.setOnMenuItemClickListener { onMenuItemClick(it) }

        binding.recyclerView.layoutManager = LinearLayoutManager(
            activity, RecyclerView.VERTICAL, false
        )
        adapter = FileRuleListAdapter(this)
        dragDropManager = RecyclerViewDragDropManager().apply {
            setDraggingItemShadowDrawable(
                getDrawable(
                    com.h6ah4i.android.materialshadowninepatch.R.drawable.ms9_composite_shadow_z2
                ) as NinePatchDrawable
            )
        }
        wrappedAdapter = dragDropManager.createWrappedAdapter(adapter)
        binding.recyclerView.adapter = wrappedAdapter
        binding.recyclerView.itemAnimator = DraggableItemAnimator()
        dragDropManager.attachRecyclerView(binding.recyclerView)
        binding.recyclerView.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.recyclerView)
        )

        binding.fab.setOnClickListener { onAddRule() }

        Settings.FILE_RULES.observe(viewLifecycleOwner) { onRuleListChanged(it) }
    }

    private fun onMenuItemClick(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_run_all -> {
                runAllRules()
                true
            }
            else -> false
        }

    override fun onPause() {
        super.onPause()
        dragDropManager.cancelDrag()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dragDropManager.release()
        WrapperAdapterUtils.releaseAll(wrappedAdapter)
    }

    private fun onRuleListChanged(rules: List<FileRule>) {
        binding.emptyView.fadeToVisibilityUnsafe(rules.isEmpty())
        adapter.replace(rules)
    }

    private fun onAddRule() {
        startActivitySafe(EditFileRuleActivity::class.createIntent())
    }

    override fun editRule(rule: FileRule) {
        startActivitySafe(
            EditFileRuleActivity::class.createIntent()
                .putArgs(EditFileRuleFragment.Args(rule = rule))
        )
    }

    override fun moveRule(fromPosition: Int, toPosition: Int) {
        FileRules.move(fromPosition, toPosition)
    }

    override fun toggleRule(rule: FileRule, enabled: Boolean) {
        FileRules.replace(rule.copy(enabled = enabled))
    }

    override fun runRule(rule: FileRule) {
        viewLifecycleOwner.lifecycleScope.launch {
            showToast(R.string.file_rule_running)
            val count = FileRuleEngine.runRule(rule, requireContext())
            if (count > 0) {
                showToast(getString(R.string.file_rule_matched_files, count, rule.displayName))
            } else {
                showToast(getString(R.string.file_rule_no_files_matched, rule.displayName))
            }
        }
    }

    private fun runAllRules() {
        val rules = Settings.FILE_RULES.valueCompat.filter { it.enabled }
        if (rules.isEmpty()) {
            showToast(R.string.file_rule_no_enabled_rules)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            showToast(R.string.file_rule_running)
            var totalCount = 0
            for (rule in rules) {
                totalCount += FileRuleEngine.runRule(rule, requireContext())
            }
            if (totalCount > 0) {
                showToast(getString(R.string.file_rule_matched_files_total, totalCount))
            } else {
                showToast(R.string.file_rule_no_files_matched_any)
            }
        }
    }
}
