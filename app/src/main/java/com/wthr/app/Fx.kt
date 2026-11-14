package com.wthr.app

import android.content.Context
import android.graphics.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Animated weather, played inside the widget by two ViewFlippers (no app process needed):
 *  - SLOW layer (8 frames, ~0.4 s each): drifting volumetric clouds, mist, sun glow/lens flare, twinkling stars,
 *    twinkling windows, blinking aviation lights, Merdeka 118 LED crown.
 *  - FAST layer (12 frames, 70 ms each): rain (2 depths + splashes), thunderstorm with lightning, snow (2 depths).
 * Every effect loops seamlessly.
 */
object Fx {
    const val FAST = 12
    const val SLOW = 8
    private const val P = 204.5f

    class Layers(val fast: List<Bitmap>, val slow: List<Bitmap>)

    // cond: 0 rain, 1 storm, 2 snow, 3 sunny, 4 clear night, 5 cloudy / fog
    fun build(c: Context, spec: CardShape.Spec, cond: Int, code: Int, day: Boolean, scene: Int): Layers {
        val needFast = cond == 0 || cond == 1 || cond == 2
        val dm = c.resources.displayMetrics
        val budget = dm.widthPixels.toDouble() * dm.heightPixels * 4.0 * 1.5 * 0.6        // stay well under the RemoteViews bitmap limit
        val per = (budget - spec.w.toDouble() * spec.h * 4.0) / (SLOW + (if (needFast) FAST else 0))
        val ratio = per / (spec.w.toDouble() * spec.h * 4.0)
        val s = if (ratio <= 0.0) 0.25f else sqrt(ratio).coerceIn(0.25, 0.55).toFloat()
        val sw = (spec.w * s).toInt().coerceAtLeast(40); val sh = (spec.h * s).toInt().coerceAtLeast(44)
        val sp = CardShape.Spec(sw, sh, spec.r * s)
        val h = Skyline.hourNow()
        fun frame(draw: (Canvas) -> Unit): Bitmap {
            val b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            cv.clipPath(CardShape.path(sp))
            CardShape.fit(cv, sp, 409f, 0.5f)
            draw(cv)
            return b
        }
        val slow = List(SLOW) { f -> frame { slowFx(it, f, cond, day, scene, h) } }
        val fast = if (needFast) List(FAST) { f -> frame { fastFx(it, f, cond, code, day) } } else emptyList()
        return Layers(fast, slow)
    }

    // ---------------- slow layer ----------------
    private fun mist(cv: Canvas, x: Float, y: Float, r: Float, col: Int) {
        cv.save(); cv.scale(1f, 0.28f, x, y)
        cv.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP) })
        cv.restore()
    }

    private fun slowFx(cv: Canvas, f: Int, cond: Int, day: Boolean, scene: Int, h: Float) {
        val ph = (2.0 * PI * f / SLOW).toFloat()
        val night = !day
        val sc = Skyline.skyCol(day, cond, h)

        // volumetric clouds drifting gently (ping-pong so the loop never jumps)
        val cc = Skyline.cloudColors(day, cond, h)
        Skyline.cloudSpec(cond).forEachIndexed { i, c ->
            Skyline.cloudBlob(cv, c[0] + 16f * sin(ph + i * 1.3f), c[1] + 3f * sin(2f * ph + i), c[2], 40 + i, cc[0], cc[1], 0.88f)
        }

        // low mist drifting over the city
        if (cond == 0 || cond == 1 || cond == 2 || cond == 5 || h < 7f) {
            val a = when (cond) { 1 -> 0.30f; 0 -> 0.26f; 2 -> 0.22f; 5 -> 0.16f; else -> 0.14f }
            for (i in 0..2) mist(cv, 80f + i * 120f + 22f * sin(ph + i), 318f + i * 8f, 150f, Skyline.withAlpha(sc.hor, a))
        }

        // sun glow + lens flare
        if (day && cond == 3) {
            val cel = Skyline.celestial(day, h); val pulse = 0.5f + 0.5f * cos(ph)
            Skyline.glow(cv, cel[0], cel[1], 70f + 14f * pulse, argb(0xFFFFF3B0L), 0.30f + 0.18f * pulse)
            for (k in 1..3) {
                val t = 0.22f * k
                cv.drawCircle(cel[0] + (204f - cel[0]) * t, cel[1] + (200f - cel[1]) * t, 5f + 3f * k,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Skyline.withAlpha(argb(0xFFFFE9B0L), (0.10f + 0.06f * pulse) / k) })
            }
        }

        // twinkling stars
        if (night && (cond == 4 || cond == 5)) {
            val rnd = Random(9); val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(16) {
                val x = rnd.nextFloat() * 409f; val y = rnd.nextFloat() * 210f; val r = 1f + rnd.nextFloat() * 1.4f; val q = rnd.nextFloat() * 6.28f
                p.color = Skyline.withAlpha(Color.WHITE, (0.25f + 0.75f * (0.5f + 0.5f * sin(ph + q))) * (if (cond == 4) 1f else 0.5f))
                cv.drawCircle(x, y, r, p)
            }
        }

        // windows switching on and off at night
        if (night) {
            val p = Paint()
            Skyline.windowSpots(scene).forEachIndexed { i, s ->
                if ((f + i) % 8 < 3) { p.color = argb(0xFFFFD98AL); p.alpha = 215; cv.drawRect(s[0], s[1], s[0] + 2.6f, s[1] + 3.4f, p) }
            }
        }

        // Merdeka 118: colour-shifting LED crown at dusk and night
        if (scene == 2 && (night || h > 18f)) {
            Skyline.glow(cv, 205f, 100f, 56f, Color.HSVToColor(floatArrayOf((f * 45f) % 360f, 0.35f, 1f)), 0.38f)
        }

        // blinking aviation lights
        for (b in Skyline.beacons(scene)) {
            if (f % 4 < 2) {
                Skyline.glow(cv, b[0], b[1], 9f, argb(0xFFFF2A2AL), if (night) 0.9f else 0.5f)
                cv.drawCircle(b[0], b[1], 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0xFFFF3B3BL) })
            }
        }
    }

    // ---------------- fast layer ----------------
    private fun fastFx(cv: Canvas, f: Int, cond: Int, code: Int, day: Boolean) {
        when (cond) {
            0, 1 -> {
                rain(cv, f, if (cond == 1) 70 else if (code in 51..57) 28 else if (code == 65 || code == 82) 68 else 46, day)
                if (cond == 1) lightning(cv, f)
            }
            2 -> snow(cv, f, day)
        }
    }

    private fun rain(cv: Canvas, f: Int, n: Int, day: Boolean) {
        val rnd = Random(11); val col = argb(if (day) 0xFFFFFFFFL else 0xFFB8C6FFL)
        val far = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Skyline.withAlpha(col, 0.32f); style = Paint.Style.STROKE; strokeWidth = 1.0f; strokeCap = Paint.Cap.ROUND }
        val near = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Skyline.withAlpha(col, 0.62f); style = Paint.Style.STROKE; strokeWidth = 1.9f; strokeCap = Paint.Cap.ROUND }
        repeat(n) { i ->
            val isNear = i % 2 == 0
            val x = rnd.nextFloat() * 460f - 20f; val y0 = rnd.nextFloat() * P
            val k = if (isNear) 2 else 1
            val len = if (isNear) 22f + rnd.nextFloat() * 12f else 10f + rnd.nextFloat() * 8f
            for (rep in 0..2) {
                val y = ((y0 + f * (P / FAST) * k) % P) + rep * P - 30f
                cv.drawLine(x, y, x - len * 0.2f, y + len, if (isNear) near else far)
            }
        }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.9f }
        val r2 = Random(31)
        repeat(n / 2) { i ->
            val x = r2.nextFloat() * 409f; val y = 352f + r2.nextFloat() * 52f
            val t = ((f + i * 5) % FAST) / FAST.toFloat(); val r = 1.5f + 6f * t
            sp.color = Skyline.withAlpha(col, 0.55f * (1f - t))
            cv.drawOval(RectF(x - r, y - r * 0.32f, x + r, y + r * 0.32f), sp)
        }
    }

    private fun lightning(cv: Canvas, f: Int) {
        val k = when (f) { 3 -> 1f; 4 -> 0.45f; 8 -> 0.7f; 9 -> 0.25f; else -> 0f }
        if (k == 0f) return
        val dx = if (f >= 8) -130f else 0f
        Skyline.glow(cv, 290f + dx, 60f, 190f, argb(0xFFDCE6FFL), 0.55f * k)
        cv.drawColor(Skyline.withAlpha(Color.WHITE, 0.16f * k))
        if (f == 3 || f == 8) {
            cv.save(); cv.translate(dx, 0f)
            val main = Path().apply { moveTo(300f, -5f); lineTo(286f, 50f); lineTo(298f, 58f); lineTo(274f, 120f); lineTo(286f, 126f); lineTo(258f, 205f); lineTo(266f, 208f); lineTo(246f, 262f) }
            val branch = Path().apply { moveTo(274f, 120f); lineTo(306f, 150f); lineTo(300f, 176f) }
            for (pth in arrayOf(main, branch)) {
                cv.drawPath(pth, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0x55CFE0FFL); style = Paint.Style.STROKE; strokeWidth = 9f; strokeJoin = Paint.Join.ROUND })
                cv.drawPath(pth, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = if (pth === main) 2.6f else 1.6f; strokeJoin = Paint.Join.ROUND })
            }
            cv.restore()
        }
    }

    private fun snow(cv: Canvas, f: Int, day: Boolean) {
        val rnd = Random(21); val col = argb(if (day) 0xFFFFFFFFL else 0xFFDDE6FFL)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); val halo = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(56) { i ->
            val near = i % 3 == 0
            val x0 = rnd.nextFloat() * 409f; val y0 = rnd.nextFloat() * P; val ph = rnd.nextFloat()
            val r = if (near) 2.6f + rnd.nextFloat() * 1.8f else 1.2f + rnd.nextFloat() * 1.0f
            val per = if (near) P else P / 2f
            val sway = (if (near) 8f else 4f) * sin(2.0 * PI * (f / FAST.toFloat() + ph)).toFloat()
            for (rep in 0 until (if (near) 3 else 5)) {
                val y = ((y0 + f * (per / FAST)) % per) + rep * per - 8f
                halo.color = Skyline.withAlpha(col, if (near) 0.22f else 0.12f); cv.drawCircle(x0 + sway, y, r * 2f, halo)
                p.color = Skyline.withAlpha(col, if (near) 0.95f else 0.7f); cv.drawCircle(x0 + sway, y, r, p)
            }
        }
    }
}
