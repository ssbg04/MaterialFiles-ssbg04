package me.zhanghai.android.files.ui

import android.graphics.Canvas
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * [RecyclerView.EdgeEffectFactory] that renders vertical overscroll as a damped spring
 * translation rather than the default edge glow/stretch, while deferring top-edge drags to a
 * parent [SwipeRefreshLayout] when present.
 */
class SpringOverscrollEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
    private val springAnimations = WeakHashMap<RecyclerView, WeakReference<SpringAnimation>>()

    override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
        if (direction != DIRECTION_TOP && direction != DIRECTION_BOTTOM) {
            return super.createEdgeEffect(recyclerView, direction)
        }
        val springAnimation = getOrCreateSpringAnimation(recyclerView)
        return SpringEdgeEffect(recyclerView, direction, springAnimation)
    }

    private fun getOrCreateSpringAnimation(recyclerView: RecyclerView): SpringAnimation =
        springAnimations[recyclerView]?.get() ?: SpringAnimation(
            recyclerView,
            DynamicAnimation.TRANSLATION_Y
        ).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
        }.also {
            springAnimations[recyclerView] = WeakReference(it)
        }

    private class SpringEdgeEffect(
        private val recyclerView: RecyclerView,
        private val direction: Int,
        private val springAnimation: SpringAnimation
    ) : EdgeEffect(recyclerView.context) {
        override fun onPull(deltaDistance: Float) {
            handlePull(deltaDistance)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            handlePull(deltaDistance)
        }

        override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
            if (isTopPullBlocked() || deltaDistance <= 0f) {
                return 0f
            }
            handlePull(deltaDistance)
            return deltaDistance
        }

        private fun isTopPullBlocked(): Boolean =
            direction == DIRECTION_TOP && recyclerView.parent is SwipeRefreshLayout

        private fun handlePull(deltaDistance: Float) {
            if (deltaDistance == 0f || isTopPullBlocked()) {
                return
            }
            if (springAnimation.isRunning) {
                springAnimation.cancel()
            }
            val sign = if (direction == DIRECTION_BOTTOM) -1f else 1f
            val translationDelta = sign * recyclerView.height * deltaDistance * PULL_MULTIPLIER
            recyclerView.translationY = (recyclerView.translationY + translationDelta)
                .coerceIn(-MAX_PULL_TRANSLATION, MAX_PULL_TRANSLATION)
        }

        override fun onRelease() {
            if (recyclerView.translationY != 0f) {
                springAnimation.animateToFinalPosition(0f)
            }
        }

        override fun onAbsorb(velocity: Int) {
            val signedVelocity = when (direction) {
                DIRECTION_TOP -> velocity * ABSORB_VELOCITY_MULTIPLIER
                DIRECTION_BOTTOM -> -velocity * ABSORB_VELOCITY_MULTIPLIER
                else -> return
            }.coerceIn(-MAX_ABSORB_VELOCITY, MAX_ABSORB_VELOCITY)
            springAnimation.setStartVelocity(signedVelocity)
            springAnimation.animateToFinalPosition(0f)
        }

        override fun draw(canvas: Canvas): Boolean = false

        override fun isFinished(): Boolean =
            !springAnimation.isRunning && recyclerView.translationY == 0f

        override fun finish() {
            super.finish()
            if (springAnimation.isRunning) {
                springAnimation.cancel()
            }
            recyclerView.translationY = 0f
        }
    }

    companion object {
        private const val PULL_MULTIPLIER = 0.28f
        private const val MAX_PULL_TRANSLATION = 120f
        private const val ABSORB_VELOCITY_MULTIPLIER = 0.45f
        private const val MAX_ABSORB_VELOCITY = 2500f
    }
}
