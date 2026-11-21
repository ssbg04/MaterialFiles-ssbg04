/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.EditFileRuleFragmentBinding
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.launchSafe
import me.zhanghai.android.files.util.showToast

class EditFileRuleFragment : Fragment() {

    private val args by args<Args>()

    private val openSourceDirectoryLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract()) { onOpenSourceDirectoryResult(it) }

    private val openTargetDirectoryLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract()) { onOpenTargetDirectoryResult(it) }

    private lateinit var binding: EditFileRuleFragmentBinding

    private var sourceDirectory: Path? = null
    private var targetDirectory: Path? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        EditFileRuleFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        val isEditing = args.rule != null
        activity.setTitle(
            if (isEditing) R.string.file_rule_edit_title_edit else R.string.file_rule_edit_title_add
        )

        binding.toolbar.inflateMenu(R.menu.edit_file_rule)
        binding.toolbar.setOnMenuItemClickListener { onMenuItemClick(it) }

        val existingRule = args.rule
        if (existingRule != null) {
            sourceDirectory = existingRule.sourceDirectory
            targetDirectory = existingRule.targetDirectory
            binding.nameEdit.setText(existingRule.name)
            binding.enabledSwitch.isChecked = existingRule.enabled
            binding.autoRunOnOpenSwitch.isChecked = existingRule.autoRunOnOpen
            binding.includeSubdirectoriesSwitch.isChecked = existingRule.includeSubdirectories
            binding.extensionsEdit.setText(existingRule.extensions)
            binding.namePatternEdit.setText(existingRule.namePattern)
            binding.isRegexCheckBox.isChecked = existingRule.isRegex
            if (existingRule.action == RuleAction.MOVE) {
                binding.actionMoveRadio.isChecked = true
            } else {
                binding.actionCopyRadio.isChecked = true
            }
            binding.deleteButton.visibility = View.VISIBLE
            binding.deleteButton.setOnClickListener { onDelete() }
        } else {
            sourceDirectory = args.initialSourceDirectory
            binding.deleteButton.visibility = View.GONE
        }

        updateDirectoryTexts()

        binding.sourcePathEdit.setOnClickListener { onPickSourceDirectory() }
        binding.sourcePathLayout.setEndIconOnClickListener { onPickSourceDirectory() }

        binding.targetPathEdit.setOnClickListener { onPickTargetDirectory() }
        binding.targetPathLayout.setEndIconOnClickListener { onPickTargetDirectory() }

        binding.runNowButton.setOnClickListener { onRunNow() }
    }

    private fun onMenuItemClick(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_save -> {
                save()
                true
            }
            else -> false
        }

    private fun onPickSourceDirectory() {
        openSourceDirectoryLauncher.launchSafe(sourceDirectory, this)
    }

    private fun onOpenSourceDirectoryResult(result: Path?) {
        result ?: return
        sourceDirectory = result
        binding.sourcePathLayout.error = null
        updateDirectoryTexts()
    }

    private fun onPickTargetDirectory() {
        openTargetDirectoryLauncher.launchSafe(targetDirectory, this)
    }

    private fun onOpenTargetDirectoryResult(result: Path?) {
        result ?: return
        targetDirectory = result
        binding.targetPathLayout.error = null
        updateDirectoryTexts()
    }

    private fun updateDirectoryTexts() {
        binding.sourcePathEdit.setText(sourceDirectory?.toUserFriendlyString().orEmpty())
        binding.targetPathEdit.setText(targetDirectory?.toUserFriendlyString().orEmpty())
    }

    private fun buildRule(): FileRule? {
        val src = sourceDirectory
        if (src == null) {
            binding.sourcePathLayout.error = getString(R.string.file_rule_error_source_required)
            return null
        }
        val tgt = targetDirectory
        if (tgt == null) {
            binding.targetPathLayout.error = getString(R.string.file_rule_error_target_required)
            return null
        }
        if (src == tgt) {
            binding.targetPathLayout.error = getString(R.string.file_rule_error_same_directory)
            return null
        }

        val name = binding.nameEdit.text.toString().trim()
        val ruleName = name.ifEmpty { "${src.name} → ${tgt.name}" }
        val enabled = binding.enabledSwitch.isChecked
        val autoRun = binding.autoRunOnOpenSwitch.isChecked
        val includeSubdirs = binding.includeSubdirectoriesSwitch.isChecked
        val extensions = binding.extensionsEdit.text.toString().trim()
        val namePattern = binding.namePatternEdit.text.toString().trim()
        val isRegex = binding.isRegexCheckBox.isChecked
        val action = if (binding.actionMoveRadio.isChecked) RuleAction.MOVE else RuleAction.COPY

        val id = args.rule?.id ?: java.util.Random().nextLong()
        return FileRule(
            id = id,
            name = ruleName,
            enabled = enabled,
            sourceDirectory = src,
            includeSubdirectories = includeSubdirs,
            extensions = extensions,
            namePattern = namePattern,
            isRegex = isRegex,
            action = action,
            targetDirectory = tgt,
            autoRunOnOpen = autoRun
        )
    }

    private fun save() {
        val rule = buildRule() ?: return
        FileRules.replace(rule)
        showToast(R.string.file_rule_saved)
        finish()
    }

    private fun onDelete() {
        val existingRule = args.rule ?: return
        FileRules.remove(existingRule)
        showToast(R.string.file_rule_deleted)
        finish()
    }

    private fun onRunNow() {
        val rule = buildRule() ?: return
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

    @Parcelize
    class Args(
        val rule: FileRule? = null,
        val initialSourceDirectory: @WriteWith<ParcelableParceler> Path? = null
    ) : ParcelableArgs
}
