package me.zhanghai.android.files.filelist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.setTintCompat
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.util.asColor
import me.zhanghai.android.files.util.dpToDimension
import me.zhanghai.android.files.util.dpToDimensionPixelSize
import me.zhanghai.android.files.util.performSelectionHaptic
import me.zhanghai.android.files.util.withModulatedAlpha
import kotlin.math.abs

class FileItemSwipeHelper(
    private val isSwipeEnabled: () -> Boolean,
    private val getFileAt: (Int) -> FileItem?,
    private val onSwipeLeft: (FileItem) -> Unit,
    private val onSwipeRight: (FileItem) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundRect = RectF()
    private var deleteIcon: Drawable? = null
    private var shareIcon: Drawable? = null

    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (!isSwipeEnabled()) {
            return 0
        }
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION || getFileAt(position) == null) {
            return 0
        }
        return super.getSwipeDirs(recyclerView, viewHolder)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        viewHolder.bindingAdapter?.notifyItemChanged(position)
        val file = getFileAt(position) ?: return
        viewHolder.itemView.performSelectionHaptic()
        when (direction) {
            ItemTouchHelper.LEFT -> onSwipeLeft(file)
            ItemTouchHelper.RIGHT -> onSwipeRight(file)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            drawSwipeBackgroundAndIcon(c, recyclerView.context, viewHolder, dX)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawSwipeBackgroundAndIcon(
        canvas: Canvas,
        context: Context,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float
    ) {
        val itemView = viewHolder.itemView
        val isSwipeLeft = dX < 0f
        val baseColor = if (isSwipeLeft) {
            MaterialColors.getColor(
                itemView,
                androidx.appcompat.R.attr.colorError,
                DEFAULT_DELETE_COLOR
            )
        } else {
            MaterialColors.getColor(
                itemView,
                androidx.appcompat.R.attr.colorPrimary,
                DEFAULT_SHARE_COLOR
            )
        }
        backgroundPaint.color = baseColor.asColor().withModulatedAlpha(BACKGROUND_ALPHA).value

        val horizontalInset = context.dpToDimension(4f)
        val verticalInset = context.dpToDimension(2f)
        val cornerRadius = context.dpToDimension(12f)
        val bgTop = itemView.top + verticalInset
        val bgBottom = itemView.bottom - verticalInset
        if (bgBottom <= bgTop) {
            return
        }

        val bgLeft: Float
        val bgRight: Float
        if (isSwipeLeft) {
            val exposedLeft = (itemView.right + dX).coerceAtLeast(itemView.left.toFloat())
            bgRight = itemView.right - horizontalInset
            bgLeft = (exposedLeft + horizontalInset).coerceAtMost(bgRight)
        } else {
            val exposedRight = (itemView.left + dX).coerceAtMost(itemView.right.toFloat())
            bgLeft = itemView.left + horizontalInset
            bgRight = (exposedRight - horizontalInset).coerceAtLeast(bgLeft)
        }
        if (bgRight <= bgLeft) {
            return
        }

        backgroundRect.set(bgLeft, bgTop, bgRight, bgBottom)
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

        val icon = if (isSwipeLeft) getDeleteIcon(context) else getShareIcon(context)
        val defaultIconSize = context.dpToDimensionPixelSize(24)
        val iconWidth = icon.intrinsicWidth.takeIf { it > 0 } ?: defaultIconSize
        val iconHeight = icon.intrinsicHeight.takeIf { it > 0 } ?: defaultIconSize
        val iconMargin = context.dpToDimensionPixelSize(24)
        val iconTop = itemView.top + (itemView.height - iconHeight) / 2
        val iconBottom = iconTop + iconHeight
        val iconLeft: Int
        val iconRight: Int
        if (isSwipeLeft) {
            iconRight = itemView.right - iconMargin
            iconLeft = iconRight - iconWidth
        } else {
            iconLeft = itemView.left + iconMargin
            iconRight = iconLeft + iconWidth
        }

        val revealFraction = (abs(dX) / (iconMargin + iconWidth).coerceAtLeast(1)).coerceIn(0f, 1f)
        icon.alpha = (revealFraction * 255).toInt()
        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

        val saveCount = canvas.save()
        canvas.clipRect(backgroundRect)
        icon.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun getDeleteIcon(context: Context): Drawable =
        deleteIcon ?: context.getDrawableCompat(R.drawable.delete_icon_control_normal_24dp)
            .mutate()
            .apply { setTintCompat(Color.WHITE) }
            .also { deleteIcon = it }

    private fun getShareIcon(context: Context): Drawable =
        shareIcon ?: context.getDrawableCompat(R.drawable.arrow_end_icon_white_24dp)
            .mutate()
            .apply { setTintCompat(Color.WHITE) }
            .also { shareIcon = it }

    companion object {
        private const val BACKGROUND_ALPHA = 0.9f
        private val DEFAULT_DELETE_COLOR = 0xFFB3261E.toInt()
        private val DEFAULT_SHARE_COLOR = 0xFF1976D2.toInt()
    }
}
