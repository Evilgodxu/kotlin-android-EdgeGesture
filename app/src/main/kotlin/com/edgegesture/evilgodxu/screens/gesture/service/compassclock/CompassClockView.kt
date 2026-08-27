package com.edgegesture.evilgodxu.screens.gesture.service.compassclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 罗盘时钟视图，按 docs/罗盘时钟.html 的设计一比一渲染。
 *
 * 布局：中心为年份，外围七个同心环依次为 月/日/星期/时辰/时/分/秒，
 * 当前值位于 3 点钟方向，文字沿径向排列；普通文字 #C0C0C0，当前值使用深色主题主色。
 *
 * 动画节奏（对应 HTML 的入场序列）：
 * 1. 各环依次出现并向四周展开（月→日→星期→时辰→时→分→秒）；
 * 2. 整体绕中心旋转 720°；
 * 3. 进入实时走时：秒环每秒走一步（0.5s ease-in-out），分/时/时辰/星期/日/月在对应单位
 *    切换时各自走一步。
 */
class CompassClockView(context: Context) : View(context) {

    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NORMAL_COLOR
        typeface = Typeface.DEFAULT_BOLD
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CURRENT_COLOR
        typeface = Typeface.DEFAULT_BOLD
    }

    private val handler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    private val calendar = Calendar.getInstance()

    private var centerX = 0f
    private var centerY = 0f
    private var scale = 1f
    private val radii = FloatArray(7)
    private var baselineY = 0f
    private var openedAt = 0L
    // 关闭动画：开始时间（-1 表示未在关闭），动画结束后回调
    private var closeStartTime = -1L
    private var dismissListener: (() -> Unit)? = null

    // 各环文本序列（简体中文风格，对应 HTML 中 type=1 的转换结果）
    private val monthTexts = (1..12).map { numToSimp(it) + "月" }
    private val weekTexts = listOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
    private val shichenTexts = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
        .map { it + "时" }
    private val hourTexts = (0..23).map { numToSimp(it) + "时" }
    private val minuteTexts = (0..59).map { numToSimp(it) + "分" }
    private val secondTexts = (0..59).map { numToSimp(it) + "秒" }
    private val dayTexts = (1..31).map { numToSimp(it) + "日" }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        scale = min(w, h) / DESIGN_DIAMETER
        for (i in radii.indices) {
            radii[i] = DESIGN_RADII[i] * scale
        }
        val textSize = DESIGN_TEXT_SIZE * scale
        normalPaint.textSize = textSize
        currentPaint.textSize = textSize
        val fm = normalPaint.fontMetrics
        baselineY = -(fm.ascent + fm.descent) / 2f
    }

    fun start() {
        openedAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(frameRunnable)
        handler.post(frameRunnable)
        invalidate()
    }

    fun stop() {
        handler.removeCallbacks(frameRunnable)
    }

    /** 播放反向关闭动画（环收缩 + 整体反转 + 淡出），动画结束后回调 [onFinished] */
    fun startClose(onFinished: () -> Unit) {
        if (closeStartTime >= 0) return
        closeStartTime = SystemClock.uptimeMillis()
        dismissListener = onFinished
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val nowMs = System.currentTimeMillis()
        val uptime = (SystemClock.uptimeMillis() - openedAt).toFloat()

        // 关闭动画进度：结束后通知移除视图
        val closeProgress = if (closeStartTime >= 0) {
            ((SystemClock.uptimeMillis() - closeStartTime).toFloat() / CLOSE_DURATION_MS).coerceIn(0f, 1f)
        } else 0f
        if (closeProgress >= 1f) {
            closeStartTime = -1L
            dismissListener?.invoke()
            dismissListener = null
            return
        }

        // 关闭时整体淡出
        if (closeProgress > 0f) {
            canvas.saveLayerAlpha(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                (255 * (1 - easeInOut(closeProgress))).toInt()
            )
        }

        // 半透明深色遮罩，保证灰色文字在任意背景下可读
        canvas.drawColor(SCRIM_COLOR)

        calendar.timeInMillis = nowMs
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val week = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val msInSecond = nowMs % 1000
        val msInMinute = nowMs % 60000
        val msInHour = nowMs % 3600000
        val msInDay = nowMs % 86400000

        // 整体旋转：入场正转 720°（720° 与 0° 等价，结束后无缝衔接实时走时），
        // 关闭时反向转 720°
        val globalSpin = if (uptime >= SPIN_START_MS) {
            easeInOut(((uptime - SPIN_START_MS) / SPIN_DURATION_MS).coerceIn(0f, 1f)) * 720f
        } else 0f
        val reverseSpin = if (closeProgress > 0f) -720f * easeInOut(closeProgress) else 0f
        val totalRotation = globalSpin + reverseSpin
        if (totalRotation != 0f) {
            canvas.rotate(totalRotation, centerX, centerY)
        }

        val daysInMonth = daysInMonth(year, month)
        val isMidnight = hour == 0 && minute == 0 && second == 0

        drawRing(
            canvas = canvas, radius = radii[0], texts = monthTexts,
            currentIndex = month - 1, step = 360f / 12,
            p = motionProgress(msInDay, day == 1 && isMidnight), ringIndex = 0, uptime = uptime,
            closeProgress = closeProgress
        )
        drawRing(
            canvas = canvas, radius = radii[1], texts = dayTexts.subList(0, daysInMonth),
            currentIndex = day - 1, step = 360f / daysInMonth,
            p = motionProgress(msInDay, isMidnight), ringIndex = 1, uptime = uptime,
            closeProgress = closeProgress
        )
        drawRing(
            canvas = canvas, radius = radii[2], texts = weekTexts,
            currentIndex = week, step = 360f / 7,
            p = motionProgress(msInDay, isMidnight), ringIndex = 2, uptime = uptime,
            closeProgress = closeProgress
        )
        drawRing(
            canvas = canvas, radius = radii[3], texts = shichenTexts,
            currentIndex = shichenIndex(hour), step = 360f / 12,
            p = motionProgress(msInHour, hour % 2 == 1 && minute == 0 && second == 0),
            ringIndex = 3, uptime = uptime, closeProgress = closeProgress
        )
        drawRing(
            canvas = canvas, radius = radii[4], texts = hourTexts,
            currentIndex = hour, step = 360f / 24,
            p = motionProgress(msInHour, minute == 0 && second == 0),
            ringIndex = 4, uptime = uptime, closeProgress = closeProgress
        )
        drawRing(
            canvas = canvas, radius = radii[5], texts = minuteTexts,
            currentIndex = minute, step = 360f / 60,
            p = motionProgress(msInMinute, second == 0), ringIndex = 5, uptime = uptime,
            closeProgress = closeProgress
        )
        drawRing(
            canvas = canvas, radius = radii[6], texts = secondTexts,
            currentIndex = second, step = 360f / 60,
            p = motionProgress(msInSecond, true), ringIndex = 6, uptime = uptime,
            closeProgress = closeProgress
        )

        drawYear(canvas, yearText(year))

        if (closeProgress > 0f) {
            canvas.restore()
        }
    }

    /**
     * 绘制单个环。
     * 当前值（currentIndex 对应的文本）位于 3 点钟方向并用当前色高亮；
     * 其余值按 step 角度逆时针排开，文字沿径向排列。
     * [p] 为当前单位内的走步进度（0→1），用于实时走时时的平滑转动。
     * [closeProgress] 大于 0 时环从展开位置收缩回中心（关闭动画）。
     */
    private fun drawRing(
        canvas: Canvas,
        radius: Float,
        texts: List<String>,
        currentIndex: Int,
        step: Float,
        p: Float,
        ringIndex: Int,
        uptime: Float,
        closeProgress: Float = 0f
    ) {
        val count = texts.size
        val appearAt = ringIndex * RING_STAGGER_MS
        if (closeProgress <= 0f && uptime < appearAt) return
        val entrance = easeOut(((uptime - appearAt) / RING_SPREAD_MS).coerceIn(0f, 1f))

        for (k in 0 until count) {
            val angleDeg = when {
                closeProgress > 0f -> -step * k * (1f - easeInOut(closeProgress))
                entrance < 1f -> -step * k * entrance
                else -> -step * (k + 1 - p)
            }
            val rad = Math.toRadians(angleDeg.toDouble())
            val x = centerX + radius * cos(rad).toFloat()
            val y = centerY + radius * sin(rad).toFloat()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(angleDeg)
            canvas.drawText(texts[(currentIndex + k) % count], 0f, baselineY, if (k == 0) currentPaint else normalPaint)
            canvas.restore()
        }
    }

    /**
     * 年份：与其他当前值一样静止显示，文字中心与各环当前值位于同一水平线（屏幕中心）。
     * 颜色同当前值（#696969）。
     */
    private fun drawYear(canvas: Canvas, text: String) {
        val x = centerX + 2f * scale
        val y = centerY
        drawCenteredText(canvas, text, x, y, 0f)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        if (rotation != 0f) canvas.rotate(rotation)
        canvas.drawText(text, -currentPaint.measureText(text) / 2f, baselineY, currentPaint)
        canvas.restore()
    }

    /** 单位切换后的前 0.5s 内完成一步旋转（ease-in-out），其余时间保持静止 */
    private fun motionProgress(msSinceUnitStart: Long, advanced: Boolean): Float {
        if (!advanced) return 1f
        return easeInOut((msSinceUnitStart / TICK_MOTION_MS).coerceIn(0f, 1f))
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && year % 100 != 0 || year % 400 == 0) 29 else 28
        else -> 30
    }

    /** 时辰索引：23-0 子时、1-2 丑时 …… 21-22 亥时 */
    private fun shichenIndex(hour: Int): Int = (hour + 1) / 2 % 12

    private fun yearText(year: Int): String =
        numToSimp(year / 1000) + numToSimp(year / 100 % 10) + numToSimp(year / 10 % 10) + numToSimp(year % 10) + "年"

    private fun easeInOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) {
            4f * x * x * x
        } else {
            val inv = -2f * x + 2f
            1f - inv * inv * inv / 2f
        }
    }

    private fun easeOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val inv = 1f - x
        return 1f - inv * inv * inv
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 33L
        // 入场节奏：环间隔 250ms，单环展开 500ms；随后整体旋转 1s
        const val RING_STAGGER_MS = 250f
        const val RING_SPREAD_MS = 500f
        const val SPIN_START_MS = 2100f
        const val SPIN_DURATION_MS = 1000f
        const val SPIN_END_MS = SPIN_START_MS + SPIN_DURATION_MS
        // 每个单位切换时一步旋转耗时
        const val TICK_MOTION_MS = 500f
        // 关闭动画时长
        const val CLOSE_DURATION_MS = 800f

        const val DESIGN_TEXT_SIZE = 12f
        // 设计直径：外环(秒, 370) + 半行文字 ≈ 840
        const val DESIGN_DIAMETER = 840f
        // 各环半径（月 日 星期 时辰 时 分 秒），对应 HTML 中环容器的 left 偏移
        val DESIGN_RADII = floatArrayOf(40f, 85f, 145f, 200f, 240f, 300f, 370f)

        const val NORMAL_COLOR = 0xFFC0C0C0.toInt()
        const val SCRIM_COLOR = 0x99000000.toInt()
        // 深色主题主色：遮罩始终为深色，浅色主色在其上可读性差，固定使用深色主色
        const val CURRENT_COLOR = 0xFFB1B8DF.toInt()
    }
}

/** 简体中文数字转换，对应 HTML 中的 numToSimp：0→零 10→十 23→二十三 */
private fun numToSimp(n: Int): String {
    val trans = "零一二三四五六七八九十"
    val tens = n / 10
    val units = n % 10
    return buildString {
        if (tens > 1) append(trans[tens])
        if (tens != 0) append('十')
        if (units != 0) append(trans[units])
        if (tens == 0 && units == 0) append(trans[0])
    }
}
