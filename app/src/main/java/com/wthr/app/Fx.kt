package com.wthr.app

import android.graphics.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated weather overlay: N frames played in a loop by a ViewFlipper inside the widget.
 * Every effect is periodic over the N frames so the loop is seamless.
 */
object Fx {
    const val N = 8
    private const val P = 204.5f     // loop period (design units) for falling things

    fun frames(spec: CardShape.Spec, cond: Int, code: Int, day: Boolean): List<Bitmap> {
        val sw = (spec.w * 0.55f).toInt().coerceAtLeast(40); val sh = (spec.h * 0.55f).toInt().coerceAtLeast(44)
        val s = CardShape.Spec(sw, sh, spec.r * 0.55f)
        return List(N) { f ->
            val b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            cv.clipPath(CardShape.path(s))
            CardShape.fit(cv, s, 409f, 0.5f)
            draw(cv, f, cond, code, day)
            b
        }
    }

    // cond: 0 rain, 1 storm, 2 snow, 3 sunny, 4 clear night, 5 cloudy / fog
    private fun draw(cv: Canvas, f: Int, cond: Int, code: Int, day: Boolean) {
        val t = f / N.toFloat()
        when (cond) {
            0 -> { clouds(cv, t, 0x66202833L, 30f); rain(cv, f, if (code in 51..57) 24 else if (code == 65 || code == 82) 62 else 42) }
            1 -> { clouds(cv, t, 0x88141A24L, 30f); rain(cv, f, 64)
                if (f == 3) { flash(cv, 0x48FFFFFFL); bolt(cv) } else if (f == 4) flash(cv, 0x22FFFFFFL) }
            2 -> { clouds(cv, t, if (day) 0x55FFFFFFL else 0x33AAB4D0L, 26f); snow(cv, f) }
            3 -> sun(cv, f)
            4 -> stars(cv, f)
            else -> clouds(cv, t, if (day) 0x66FFFFFFL else 0x55202840L, 50f)
        }
    }

    private fun cloud(cv: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        cv.drawCircle(x, y, 22f * s, p); cv.drawCircle(x + 26f * s, y - 12f * s, 28f * s, p); cv.drawCircle(x + 58f * s, y, 22f * s, p)
        cv.drawRoundRect(x - 22f * s, y, x + 80f * s, y + 22f * s, 11f * s, 11f * s, p)
    }

    private fun clouds(cv: Canvas, t: Float, color: Long, topY: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb(color) }
        val shift = t * 340f
        for (k in -1..1) {
            cloud(cv, ((30f + shift) % 340f) + k * 340f, topY, 1f, p)
            cloud(cv, ((200f + shift) % 340f) + k * 340f, topY + 34f, 0.8f, p)
        }
    }

    private fun rain(cv: Canvas, f: Int, n: Int) {
        val rnd = Random(11)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0x99FFFFFFL); style = Paint.Style.STROKE; strokeWidth = 1.7f; strokeCap = Paint.Cap.ROUND }
        repeat(n) {
            val x = rnd.nextFloat() * 409f; val y0 = rnd.nextFloat() * P; val k = if (rnd.nextBoolean()) 1 else 2; val len = 14f + rnd.nextFloat() * 14f
            for (rep in 0..2) { val y = ((y0 + f * (P / N) * k) % P) + rep * P - 20f; cv.drawLine(x, y, x - 3f, y + len, p) }
        }
    }

    private fun snow(cv: Canvas, f: Int) {
        val rnd = Random(21)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0xDDFFFFFFL) }
        repeat(44) {
            val x = rnd.nextFloat() * 409f; val y0 = rnd.nextFloat() * P; val r = 1.6f + rnd.nextFloat() * 1.8f; val ph = rnd.nextFloat()
            val sway = 7f * sin(2.0 * PI * (f / N.toFloat() + ph)).toFloat()
            for (rep in 0..2) cv.drawCircle(x + sway, ((y0 + f * (P / N)) % P) + rep * P - 6f, r, p)
        }
    }

    private fun flash(cv: Canvas, color: Long) { cv.drawColor(argb(color)) }

    private fun bolt(cv: Canvas) {
        val path = Path().apply { moveTo(300f, -5f); lineTo(284f, 60f); lineTo(298f, 66f); lineTo(270f, 150f); lineTo(282f, 154f); lineTo(250f, 240f) }
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0x55FFFFFFL); style = Paint.Style.STROKE; strokeWidth = 9f; strokeJoin = Paint.Join.ROUND })
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0xFFFFFFFFL); style = Paint.Style.STROKE; strokeWidth = 3f; strokeJoin = Paint.Join.ROUND })
    }

    private fun sun(cv: Canvas, f: Int) {
        val pulse = 0.5f + 0.5f * cos(2.0 * PI * f / N).toFloat()
        val a = (60 + 70 * pulse).toInt()
        cv.drawCircle(340f, 66f, 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(340f, 66f, 80f, (a shl 24) or 0xFFF3B0, 0x00FFF3B0, Shader.TileMode.CLAMP) })
        val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0x88FFF3B0L); style = Paint.Style.STROKE; strokeWidth = 2.2f; strokeCap = Paint.Cap.ROUND }
        val rot = f * 30.0 / N
        for (i in 0 until 12) { val ang = Math.toRadians(i * 30.0 + rot)
            cv.drawLine(340f + (24 * cos(ang)).toFloat(), 66f + (24 * sin(ang)).toFloat(), 340f + (46 * cos(ang)).toFloat(), 66f + (46 * sin(ang)).toFloat(), ray) }
    }

    private fun stars(cv: Canvas, f: Int) {
        val rnd = Random(5)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        repeat(22) {
            val x = rnd.nextFloat() * 409f; val y = rnd.nextFloat() * 230f; val r = 1.1f + rnd.nextFloat() * 1.5f; val ph = rnd.nextFloat() * 6.2832f
            p.alpha = (110 + 145 * (0.5f + 0.5f * sin(2.0 * PI * f / N + ph).toFloat())).toInt().coerceIn(0, 255)
            cv.drawCircle(x, y, r, p)
        }
    }
}
