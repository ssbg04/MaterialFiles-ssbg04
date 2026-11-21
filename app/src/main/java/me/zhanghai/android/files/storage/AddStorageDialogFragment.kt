/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.AddStorageBottomSheetBinding
import me.zhanghai.android.files.databinding.AddStorageBottomSheetItemBinding
import me.zhanghai.android.files.file.asExternalStorageUri
import me.zhanghai.android.files.provider.cloud.client.CloudProvider
import me.zhanghai.android.files.provider.document.resolver.ExternalStorageProviderHacks
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.dpToDimensionPixelSize
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe

/**
 * Bottom sheet dialog for selecting a storage type to add, grouped into Cloud Storage,
 * Network & NAS, and Local & External sections.
 */
class AddStorageDialogFragment : BottomSheetDialogFragment() {
    private var _binding: AddStorageBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener { configureBottomSheet() }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AddStorageBottomSheetBinding.inflate(inflater, container, false)
            .also { _binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populateSections(LayoutInflater.from(view.context))
    }

    override fun onStart() {
        super.onStart()

        configureBottomSheet()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        finish()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        activity?.let {
            if (!it.isFinishing && !it.isChangingConfigurations) {
                it.finish()
            }
        }
    }

    private fun configureBottomSheet() {
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        bottomSheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        clearBottomSheetBackground(bottomSheet)
        // BottomSheetBehavior.onLayoutChild applies its default MaterialShapeDrawable on layout.
        bottomSheet.doOnLayout { clearBottomSheetBackground(it) }
        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun clearBottomSheetBackground(bottomSheet: View) {
        (bottomSheet.background as? MaterialShapeDrawable)?.apply {
            fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setTint(Color.TRANSPARENT)
            elevation = 0f
            strokeWidth = 0f
        }
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun populateSections(inflater: LayoutInflater) {
        val container = binding.sectionsContainer
        val context = container.context
        val horizontalPadding = context.dpToDimensionPixelSize(20)
        val topPadding = context.dpToDimensionPixelSize(12)
        val bottomPadding = context.dpToDimensionPixelSize(4)
        val headerColor = context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)

        for (section in SECTIONS) {
            val headerView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPaddingRelative(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
                setText(section.titleRes)
                setTextColor(headerColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, Typeface.BOLD)
            }
            container.addView(headerView)

            for (item in section.items) {
                val itemBinding = AddStorageBottomSheetItemBinding.inflate(
                    inflater, container, false
                )
                itemBinding.iconImage.setImageResource(item.iconRes)
                itemBinding.titleText.setText(item.titleRes)
                itemBinding.root.setOnClickListener {
                    if (requireActivity().isFinishing) {
                        return@setOnClickListener
                    }
                    startActivitySafe(item.createIntent())
                    finish()
                }
                container.addView(itemBinding.root)
            }
        }
    }

    private class StorageSection(
        @StringRes val titleRes: Int,
        val items: List<StorageItem>
    )

    private class StorageItem(
        @StringRes val titleRes: Int,
        @DrawableRes val iconRes: Int,
        val createIntent: () -> Intent
    )

    companion object {
        private val SECTIONS = listOf(
            StorageSection(
                R.string.storage_add_section_cloud,
                listOf(
                    StorageItem(
                        R.string.storage_add_storage_gdrive_server,
                        R.drawable.cloud_icon_white_24dp
                    ) {
                        EditCloudServerActivity::class.createIntent().putArgs(
                            EditCloudServerFragment.Args(provider = CloudProvider.GDRIVE)
                        )
                    },
                    StorageItem(
                        R.string.storage_add_storage_onedrive_server,
                        R.drawable.cloud_icon_white_24dp
                    ) {
                        EditCloudServerActivity::class.createIntent().putArgs(
                            EditCloudServerFragment.Args(provider = CloudProvider.ONEDRIVE)
                        )
                    },
                    StorageItem(
                        R.string.storage_add_storage_dropbox_server,
                        R.drawable.cloud_icon_white_24dp
                    ) {
                        EditCloudServerActivity::class.createIntent().putArgs(
                            EditCloudServerFragment.Args(provider = CloudProvider.DROPBOX)
                        )
                    }
                )
            ),
            StorageSection(
                R.string.storage_add_section_network,
                listOf(
                    StorageItem(
                        R.string.storage_add_storage_smb_server,
                        R.drawable.computer_icon_white_24dp
                    ) {
                        AddLanSmbServerActivity::class.createIntent()
                    },
                    StorageItem(
                        R.string.storage_add_storage_sftp_server,
                        R.drawable.computer_icon_white_24dp
                    ) {
                        EditSftpServerActivity::class.createIntent().putArgs(
                            EditSftpServerFragment.Args()
                        )
                    },
                    StorageItem(
                        R.string.storage_add_storage_ftp_server,
                        R.drawable.computer_icon_white_24dp
                    ) {
                        EditFtpServerActivity::class.createIntent().putArgs(
                            EditFtpServerFragment.Args()
                        )
                    },
                    StorageItem(
                        R.string.storage_add_storage_webdav_server,
                        R.drawable.computer_icon_white_24dp
                    ) {
                        EditWebDavServerActivity::class.createIntent().putArgs(
                            EditWebDavServerFragment.Args()
                        )
                    }
                )
            ),
            StorageSection(
                R.string.storage_add_section_local,
                listOfNotNull(
                    StorageItem(
                        R.string.storage_add_storage_document_tree,
                        R.drawable.sd_card_icon_white_24dp
                    ) {
                        AddDocumentTreeActivity::class.createIntent()
                    },
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        StorageItem(
                            R.string.storage_add_storage_android_data,
                            R.drawable.directory_icon_white_24dp
                        ) {
                            AddExternalStorageShortcutActivity::class.createIntent().putArgs(
                                AddExternalStorageShortcutFragment.Args(
                                    R.string.storage_add_storage_android_data,
                                    ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_DATA
                                        .asExternalStorageUri()
                                )
                            )
                        }
                    } else null,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        StorageItem(
                            R.string.storage_add_storage_android_obb,
                            R.drawable.directory_icon_white_24dp
                        ) {
                            AddExternalStorageShortcutActivity::class.createIntent().putArgs(
                                AddExternalStorageShortcutFragment.Args(
                                    R.string.storage_add_storage_android_obb,
                                    ExternalStorageProviderHacks.DOCUMENT_URI_ANDROID_OBB
                                        .asExternalStorageUri()
                                )
                            )
                        }
                    } else null
                )
            )
        )
    }
}
