package com.edgegesture.evilgodxu.screens.gesture.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.edgegesture.evilgodxu.data.gesture.GestureAction
import com.edgegesture.evilgodxu.data.gesture.GestureSettingsState
import com.edgegesture.evilgodxu.data.gesture.EdgePosition
import kotlin.math.abs

class AccessibilityGestureDetector(
    private val callback: GestureCallback
) {

    interface GestureCallback {
        fun onSwipeAction(action: GestureAction)
    }

    enum class SwipeDirection {
        UP, DOWN, LEFT, RIGHT
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupGestureDetection(
        view: View,
        position: EdgePosition,
        segmentIndex: Int,
        settingsProvider: () -> GestureSettingsState
    ) {
        var startX = 0f
        var startY = 0f
        var isLongPressTriggered = false
        var isSwipeStarted = false
        var swipeDirection: SwipeDirection? = null
        // 二次滑动模式下的待确认方向，跨触摸会话持久保持
        var pendingDirection: SwipeDirection? = null
        val swipeThreshold = 80f // 滑动触发阈值（像素）
        val longPressThreshold = 200L // 长按触发阈值（毫秒）

        val handler = Handler(Looper.getMainLooper())
        var longPressRunnable: Runnable? = null

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isLongPressTriggered = false
                    isSwipeStarted = false
                    swipeDirection = null

                    val runnable = Runnable {
                        isLongPressTriggered = true
                        val settings = settingsProvider()
                        if (settings.vibrationEnabled) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }
                        if (settings.doubleSwipeEnabled) {
                            // 二次滑动模式：只有待确认方向匹配时才触发长按操作
                            swipeDirection?.let { direction ->
                                if (pendingDirection == direction) {
                                    val action = resolveAction(position, segmentIndex, direction, true, settings)
                                    if (action != GestureAction.NONE) {
                                        callback.onSwipeAction(action)
                                    }
                                    pendingDirection = null
                                }
                                // 方向不匹配时静默消耗，不设置待确认方向
                            }
                        } else {
                            swipeDirection?.let { direction ->
                                val action = resolveAction(position, segmentIndex, direction, true, settings)
                                if (action != GestureAction.NONE) {
                                    callback.onSwipeAction(action)
                                }
                            }
                        }
                    }
                    longPressRunnable = runnable
                    handler.postDelayed(runnable, longPressThreshold)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    val deltaY = event.rawY - startY

                    if (!isLongPressTriggered) {
                        if (isSwipeStarted) {
                            // 越过阈值后按当前主方向刷新，避免初始抖动锁定错误方向
                            swipeDirection = resolveSwipeDirection(deltaX, deltaY)
                        } else if (abs(deltaX) > swipeThreshold || abs(deltaY) > swipeThreshold) {
                            isSwipeStarted = true
                            swipeDirection = resolveSwipeDirection(deltaX, deltaY)
                        }
                    }
                    isSwipeStarted
                }
                MotionEvent.ACTION_UP -> {
                    val endX = event.rawX
                    val endY = event.rawY
                    val deltaX = endX - startX
                    val deltaY = endY - startY
                    val absDeltaX = abs(deltaX)
                    val absDeltaY = abs(deltaY)

                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    if (isLongPressTriggered && isSwipeStarted) {
                        return@setOnTouchListener true
                    }

                    val isSwipe = absDeltaX > swipeThreshold || absDeltaY > swipeThreshold

                    if (isSwipe) {
                        // 以起止点总位移裁定向，与 MOVE 阶段共用同一判向基准
                        val direction = resolveSwipeDirection(deltaX, deltaY)
                        val settings = settingsProvider()
                        if (settings.doubleSwipeEnabled) {
                            if (pendingDirection == direction) {
                                // 第二次滑动相同方向：触发操作并清除待确认状态
                                val action = resolveAction(position, segmentIndex, direction, false, settings)
                                if (action != GestureAction.NONE) {
                                    callback.onSwipeAction(action)
                                }
                                pendingDirection = null
                            } else {
                                // 第一次滑动（或方向变更）：设为待确认，不触发操作
                                pendingDirection = direction
                            }
                        } else {
                            val action = resolveAction(position, segmentIndex, direction, false, settings)
                            if (action != GestureAction.NONE) {
                                callback.onSwipeAction(action)
                            }
                        }
                        true
                    } else {
                        true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    pendingDirection = null
                    false
                }
                else -> false
            }
        }
    }

    // 依据起止点位移判定主方向，纵向/横向互斥
    private fun resolveSwipeDirection(deltaX: Float, deltaY: Float): SwipeDirection {
        return if (abs(deltaY) > abs(deltaX)) {
            if (deltaY > 0) SwipeDirection.DOWN else SwipeDirection.UP
        } else {
            if (deltaX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        }
    }

    private fun resolveAction(
        position: EdgePosition,
        segmentIndex: Int,
        direction: SwipeDirection,
        isLongPress: Boolean,
        settings: GestureSettingsState
    ): GestureAction {
        return when (position) {
            EdgePosition.LEFT -> resolveLeftEdgeAction(segmentIndex, direction, isLongPress, settings)
            EdgePosition.RIGHT -> resolveRightEdgeAction(segmentIndex, direction, isLongPress, settings)
            EdgePosition.BOTTOM -> resolveBottomEdgeAction(segmentIndex, direction, isLongPress, settings)
        }
    }

    private fun resolveLeftEdgeAction(
        segmentIndex: Int,
        direction: SwipeDirection,
        isLongPress: Boolean,
        settings: GestureSettingsState
    ): GestureAction {
        val config = when (segmentIndex) {
            0 -> settings.leftEdge
            1 -> settings.leftEdgeSegment2
            2 -> settings.leftEdgeSegment3
            else -> settings.leftEdge
        }
        return when (direction) {
            SwipeDirection.RIGHT -> if (isLongPress) config.swipeRightLong else config.swipeRight
            SwipeDirection.UP -> if (isLongPress) config.swipeUpLong else config.swipeUp
            SwipeDirection.DOWN -> if (isLongPress) config.swipeDownLong else config.swipeDown
            else -> GestureAction.NONE
        }
    }

    private fun resolveRightEdgeAction(
        segmentIndex: Int,
        direction: SwipeDirection,
        isLongPress: Boolean,
        settings: GestureSettingsState
    ): GestureAction {
        val config = when (segmentIndex) {
            0 -> settings.rightEdge
            1 -> settings.rightEdgeSegment2
            2 -> settings.rightEdgeSegment3
            else -> settings.rightEdge
        }
        return when (direction) {
            SwipeDirection.LEFT -> if (isLongPress) config.swipeLeftLong else config.swipeLeft
            SwipeDirection.UP -> if (isLongPress) config.swipeUpLong else config.swipeUp
            SwipeDirection.DOWN -> if (isLongPress) config.swipeDownLong else config.swipeDown
            else -> GestureAction.NONE
        }
    }

    private fun resolveBottomEdgeAction(
        segmentIndex: Int,
        direction: SwipeDirection,
        isLongPress: Boolean,
        settings: GestureSettingsState
    ): GestureAction {
        val config = when (segmentIndex) {
            0 -> settings.bottomEdge
            1 -> settings.bottomEdgeSegment2
            2 -> settings.bottomEdgeSegment3
            else -> settings.bottomEdge
        }
        return when (direction) {
            SwipeDirection.UP -> if (isLongPress) config.swipeUpLong else config.swipeUp
            SwipeDirection.LEFT -> if (isLongPress) config.swipeLeftLong else config.swipeLeft
            SwipeDirection.RIGHT -> if (isLongPress) config.swipeRightLong else config.swipeRight
            else -> GestureAction.NONE
        }
    }

}
