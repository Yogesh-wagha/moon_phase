package com.devesh.moonphase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max

/**
 * Draws the lunar disc on a plain android.graphics.Canvas, so exactly the same
 * pixels are produced in the Compose UI and in the RemoteViews widget bitmap.
 *
 * Terminator geometry: the terminator projects onto the disc as an ellipse whose
 * semi-minor axis is r * |2k - 1|, where k is the illuminated fraction. It bows
 * towards the bright limb for a crescent and away from it for a gibbous phase.
 */
object MoonGraphics {

    const val DARK_SIDE = 0xFF1A1E29.toInt()
    const val RIM = 0x5A96A5C8

    // Maria and craters, in units of the disc radius: x, y, size.
    private val features = arrayOf(
        floatArrayOf(-0.30f, -0.36f, 0.21f),
        floatArrayOf(0.19f, -0.11f, 0.27f),
        floatArrayOf(-0.06f, 0.36f, 0.18f),
        floatArrayOf(0.43f, 0.29f, 0.12f),
        floatArrayOf(-0.49f, 0.13f, 0.11f),
        floatArrayOf(0.06f, -0.56f, 0.10f),
        floatArrayOf(0.55f, -0.31f, 0.08f),
        floatArrayOf(-0.23f, 0.61f, 0.07f),
        floatArrayOf(0.30f, 0.58f, 0.06f)
    )

    /** Outline of the sunlit region of the disc. Empty at new moon. */
    fun litPath(cx: Float, cy: Float, r: Float, illumination: Double, waxing: Boolean): Path {
        val k = illumination.coerceIn(0.0, 1.0)
        val path = Path()
        if (k <= 0.0006) return path
        if (k >= 0.9994) {
            path.addCircle(cx, cy, r, Path.Direction.CW)
            return path
        }

        // Signed terminator semi-axis / r. Positive bows towards the bright limb.
        val c = (1.0 - 2.0 * k).toFloat()
        val outer = RectF(cx - r, cy - r, cx + r, cy + r)

        path.moveTo(cx, cy - r)
        path.arcTo(outer, 270f, 180f) // bright limb: top -> right -> bottom
        if (abs(c) < 0.0015f) {
            path.lineTo(cx, cy - r) // exact half moon
        } else {
            val inner = RectF(cx - abs(c) * r, cy - r, cx + abs(c) * r, cy + r)
            if (c > 0f) path.arcTo(inner, 90f, -180f) // crescent: terminator bows right
            else path.arcTo(inner, 90f, 180f)         // gibbous: terminator bows left
        }
        path.close()

        if (!waxing) path.transform(Matrix().apply { setScale(-1f, 1f, cx, cy) })
        return path
    }

    /**
     * @param detail draw glow and surface features; turn off for thumbnails.
     */
    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        illumination: Double,
        waxing: Boolean,
        detail: Boolean = true
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val k = illumination.coerceIn(0.0, 1.0)

        if (detail && k > 0.02) {
            val glow = (100 * k).toInt().coerceIn(0, 255)
            paint.shader = RadialGradient(
                cx, cy, r * 1.7f,
                intArrayOf(Color.argb(glow, 176, 200, 240), Color.TRANSPARENT),
                floatArrayOf(0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r * 1.7f, paint)
            paint.shader = null
        }

        // Night side, faintly lifted by earthshine.
        paint.style = Paint.Style.FILL
        paint.color = DARK_SIDE
        canvas.drawCircle(cx, cy, r, paint)

        val lit = litPath(cx, cy, r, k, waxing)
        if (!lit.isEmpty) {
            val lx = cx + (if (waxing) 0.25f else -0.25f) * r
            paint.shader = RadialGradient(
                lx, cy - 0.25f * r, r * 1.5f,
                Color.rgb(255, 252, 240), Color.rgb(198, 198, 190),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(lit, paint)
            paint.shader = null

            if (detail && r > 22f) {
                val save = canvas.save()
                canvas.clipPath(lit)
                paint.color = Color.argb(44, 92, 96, 110)
                for (ft in features) canvas.drawCircle(cx + ft[0] * r, cy + ft[1] * r, ft[2] * r, paint)
                paint.color = Color.argb(26, 255, 255, 255)
                for (ft in features) {
                    canvas.drawCircle(
                        cx + ft[0] * r - 0.02f * r,
                        cy + ft[1] * r - 0.03f * r,
                        ft[2] * r * 0.8f,
                        paint
                    )
                }
                canvas.restoreToCount(save)
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, r * 0.018f)
        paint.color = RIM
        canvas.drawCircle(cx, cy, r, paint)
    }

    /** Square bitmap for the home-screen widget. */
    fun bitmap(sizePx: Int, illumination: Double, waxing: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val half = sizePx / 2f
        draw(canvas, half, half, half * 0.66f, illumination, waxing, detail = true)
        return bmp
    }
}
