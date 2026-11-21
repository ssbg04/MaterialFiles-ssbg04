/*
 * Copyright (c) 2026 Material Files Fork
 */

package me.zhanghai.android.files.fileautomation

import android.view.ViewGroup
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.isTransformedTouchPointInViewCompat
import me.zhanghai.android.files.databinding.FileRuleItemBinding
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.ui.SimpleAdapter
import me.zhanghai.android.files.util.layoutInflater

class FileRuleListAdapter(
    private val listener: Listener
) : SimpleAdapter<FileRule, FileRuleListAdapter.ViewHolder>(),
    DraggableItemAdapter<FileRuleListAdapter.ViewHolder> {

    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            FileRuleItemBinding.inflate(parent.context.layoutInflater, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = getItem(position)
        val binding = holder.binding

        binding.root.foregroundCompat!!.mutate().setVisible(!holder.dragState.isActive, false)
        binding.root.setOnClickListener { listener.editRule(rule) }

        binding.nameText.text = rule.displayName
        binding.routeText.text = "${rule.sourceDirectory.toUserFriendlyString()} → ${rule.targetDirectory.toUserFriendlyString()}"
        binding.detailsText.text = rule.detailsDescription

        binding.enabledSwitch.setOnCheckedChangeListener(null)
        binding.enabledSwitch.isChecked = rule.enabled
        binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            listener.toggleRule(rule, isChecked)
        }

        binding.runButton.setOnClickListener { listener.runRule(rule) }
    }

    override fun onCheckCanStartDrag(holder: ViewHolder, position: Int, x: Int, y: Int): Boolean =
        (holder.binding.root as ViewGroup).isTransformedTouchPointInViewCompat(
            x.toFloat(), y.toFloat(), holder.binding.dragHandleView, null
        )

    override fun onGetItemDraggableRange(holder: ViewHolder, position: Int): ItemDraggableRange? =
        null

    override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean = true

    override fun onItemDragStarted(position: Int) {
        notifyDataSetChanged()
    }

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        notifyDataSetChanged()
    }

    override fun onMoveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) {
            return
        }
        listener.moveRule(fromPosition, toPosition)
    }

    class ViewHolder(val binding: FileRuleItemBinding) : AbstractDraggableItemViewHolder(
        binding.root
    )

    interface Listener {
        fun editRule(rule: FileRule)
        fun moveRule(fromPosition: Int, toPosition: Int)
        fun toggleRule(rule: FileRule, enabled: Boolean)
        fun runRule(rule: FileRule)
    }
}
