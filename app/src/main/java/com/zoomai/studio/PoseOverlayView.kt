package com.zoomai.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

/**
 * Overlay drawn above the camera preview. It renders, in order:
 *  1. the rule-of-thirds grid (optional),
 *  2. the horizon level line (optional, fed by the tilt sensor),
 *  3. the selected pose template (accent color, one to three figures),
 *  4. the live ML Kit skeleton (yellow) when pose detection is on,
 *  5. a short-lived focus ring where the user tapped.
 */
class PoseOverlayView(context: Context) : View(context) {

    var template: PoseTemplate? = null
        set(value) { field = value; invalidate() }

    var showFrame: Boolean = true
        set(value) { field = value; invalidate() }

    /** Device roll in degrees from the tilt sensor; null hides the level line. */
    var levelDegrees: Float? = null
        set(value) { field = value; invalidate() }

    // Live detection input, all in *image* coordinates (ML Kit output space).
    var livePoints: Array<PointF?>? = null
    var liveImageWidth: Int = 0
    var liveImageHeight: Int = 0
    var liveRotation: Int = 0
    var liveFront: Boolean = false

    fun setLive(points: Array<PointF?>?, imgW: Int, imgH: Int, rotation: Int, front: Boolean) {
        livePoints = points
        liveImageWidth = imgW
        liveImageHeight = imgH
        liveRotation = rotation
        liveFront = front
        invalidate()
    }

    private var focusX = -1f
    private var focusY = -1f
    private var focusAt = 0L

    fun showFocus(x: Float, y: Float) {
        focusX = x
        focusY = y
        focusAt = System.currentTimeMillis()
        invalidate()
        postInvalidateDelayed(1300)
    }

    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 76, 195, 255)
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val joint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(75, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 209, 102)
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val liveJoint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 255, 209, 102)
        style = Paint.Style.FILL
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val levelOkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 76, 195, 255)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showFrame) drawGrid(canvas)
        drawLevel(canvas)
        template?.let { drawTemplate(canvas, it) }
        drawLive(canvas)
        drawFocus(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        for (i in 1..2) {
            canvas.drawLine(width * i / 3f, 0f, width * i / 3f, height.toFloat(), grid)
            canvas.drawLine(0f, height * i / 3f, width.toFloat(), height * i / 3f, grid)
        }
    }

    private fun drawLevel(canvas: Canvas) {
        val deg = levelDegrees ?: return
        val cx = width / 2f
        val cy = height / 2f
        val len = width * 0.32f
        val rad = Math.toRadians((-deg).toDouble())
        val dx = (len * Math.cos(rad)).toFloat()
        val dy = (len * Math.sin(rad)).toFloat()
        val paint = if (Math.abs(deg) < 2f) levelOkPaint else levelPaint
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
        canvas.drawCircle(cx, cy, 5f, paint)
    }

    private val templateSegments = arrayOf(
        0 to 1, 1 to 2, 1 to 3, 2 to 4, 4 to 6,
        3 to 5, 5 to 7, 1 to 8, 1 to 9, 8 to 9, 8 to 10,
        10 to 12, 9 to 11, 11 to 13
    )

    private fun drawTemplate(canvas: Canvas, pose: PoseTemplate) {
        for (figure in pose.figures) {
            fun x(i: Int) = figure[i].first * width
            fun y(i: Int) = figure[i].second * height
            for ((a, b) in templateSegments) canvas.drawLine(x(a), y(a), x(b), y(b), guide)
            for (i in figure.indices) canvas.drawCircle(x(i), y(i), if (i == 0) 11f else 6f, joint)
            canvas.drawCircle(x(0), y(0), width * .055f, guide)
        }
    }

    // ML Kit landmark type indices (PoseLandmark constants).
    private val liveSegments = arrayOf(
        11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 12, 11 to 23, 12 to 24, 23 to 24,
        23 to 25, 25 to 27, 24 to 26, 26 to 28,
        27 to 31, 28 to 32, 0 to 11, 0 to 12
    )

    private fun drawLive(canvas: Canvas) {
        val points = livePoints ?: return
        if (liveImageWidth == 0 || liveImageHeight == 0 || width == 0 || height == 0) return
        val w = liveImageWidth.toFloat()
        val h = liveImageHeight.toFloat()
        val rotated = liveRotation == 90 || liveRotation == 270
        val fw = if (rotated) h else w
        val fh = if (rotated) w else h
        val scale = maxOf(width / fw, height / fh)
        val offX = (width - fw * scale) / 2f
        val offY = (height - fh * scale) / 2f

        fun map(p: PointF): PointF {
            // Rotate the raw image point into the upright frame.
            val rx: Float
            val ry: Float
            when (liveRotation) {
                90 -> { rx = h - p.y; ry = p.x }
                180 -> { rx = w - p.x; ry = h - p.y }
                270 -> { rx = p.y; ry = w - p.x }
                else -> { rx = p.x; ry = p.y }
            }
            var vx = offX + rx * scale
            val vy = offY + ry * scale
            if (liveFront) vx = width - vx
            return PointF(vx, vy)
        }

        val mapped = arrayOfNulls<PointF>(points.size)
        for (i in points.indices) {
            val p = points[i] ?: continue
            mapped[i] = map(p)
        }
        for ((a, b) in liveSegments) {
            val pa = mapped.getOrNull(a) ?: continue
            val pb = mapped.getOrNull(b) ?: continue
            canvas.drawLine(pa.x, pa.y, pb.x, pb.y, livePaint)
        }
        for (p in mapped) if (p != null) canvas.drawCircle(p.x, p.y, 5f, liveJoint)
        mapped.getOrNull(0)?.let { canvas.drawCircle(it.x, it.y, 13f, livePaint) }
    }

    private fun drawFocus(canvas: Canvas) {
        if (focusX < 0) return
        if (System.currentTimeMillis() - focusAt > 1200) return
        canvas.drawCircle(focusX, focusY, 34f, focusPaint)
        canvas.drawCircle(focusX, focusY, 4f, focusPaint)
    }
}
