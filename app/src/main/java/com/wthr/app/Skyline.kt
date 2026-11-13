package com.wthr.app

import android.graphics.*
import kotlin.random.Random

fun argb(v: Long): Int = v.toInt()

/**
 * Skyscraper scenes drawn in code on the 409x409 design grid (no downloads, works offline).
 * Scene 0 is the Moscow photo; 1..8 are skylines. The scene changes every 5 minutes.
 * Layer order: sky -> far buildings -> [big number] -> landmark -> foreground, so the number sits behind the tower.
 */
object Skyline {
    const val SCENES = 9
    fun sceneNow(): Int = ((System.currentTimeMillis() / 300_000L) % SCENES).toInt()

    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }
    private fun line(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND }
    private fun grad(x0: Float, x1: Float, a: Int, b: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, 0f, x1, 0f, a, b, Shader.TileMode.CLAMP) }

    private fun skyColors(day: Boolean, cond: Int): Pair<Int, Int> = if (day) when (cond) {
        0 -> argb(0xFF5C6B7BL) to argb(0xFF8E9CAAL)
        1 -> argb(0xFF3F4A5AL) to argb(0xFF6B7684L)
        2 -> argb(0xFF9DB0C2L) to argb(0xFFD5DEE6L)
        5 -> argb(0xFF7E93A8L) to argb(0xFFB9C6D2L)
        else -> argb(0xFF3C82D4L) to argb(0xFF8CC2EEL)
    } else when (cond) {
        0 -> argb(0xFF10152AL) to argb(0xFF2B3350L)
        1 -> argb(0xFF0A0E1EL) to argb(0xFF222A42L)
        2 -> argb(0xFF1E2640L) to argb(0xFF4A5473L)
        5 -> argb(0xFF141A30L) to argb(0xFF39425EL)
        else -> argb(0xFF070C26L) to argb(0xFF2A3C7CL)
    }

    private fun body(day: Boolean, cond: Int): Pair<Int, Int> =
        if (!day) argb(0xFF3C4872L) to argb(0xFF161D3AL)
        else if (cond == 0 || cond == 1) argb(0xFFCBD3DBL) to argb(0xFF7D8A97L)
        else argb(0xFFEAEFF3L) to argb(0xFF9FAEBFL)

    fun sky(cv: Canvas, day: Boolean, cond: Int) {
        val (t, b) = skyColors(day, cond)
        cv.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 409f, t, b, Shader.TileMode.CLAMP) })
        if (day && (cond == 3 || cond == 4)) cv.drawCircle(340f, 66f, 16f, fill(argb(0xFFFFF0B0L)))
        if (!day && cond == 4) {
            val moon = Path().apply { addCircle(340f, 66f, 15f, Path.Direction.CW) }
            moon.op(Path().apply { addCircle(346f, 61f, 13f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            cv.drawPath(moon, fill(argb(0xFFF4F1D8L)))
        }
    }

    private fun lights(cv: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rnd: Random) {
        val p = fill(argb(0xFFFFD27AL)); p.alpha = 190
        var y = y0 + 6f
        while (y < y1 - 4f) { var x = x0 + 4f
            while (x < x1 - 4f) { if (rnd.nextInt(100) < 45) cv.drawRect(x, y, x + 2.6f, y + 3.6f, p); x += 7f }
            y += 9f }
    }

    fun far(cv: Canvas, day: Boolean, cond: Int, scene: Int) {
        val rnd = Random(scene * 31 + 5)
        val col = if (day) (if (cond == 0 || cond == 1) 0xFF8593A0L else 0xFF9FB5CBL) else 0xFF1B2447L
        val p = fill(argb(col)); p.alpha = if (day) 150 else 255
        var x = -20f
        while (x < 430f) {
            val w = 16f + rnd.nextInt(18); val h = 50f + rnd.nextInt(90)
            cv.drawRect(x, 372f - h, x + w, 380f, p)
            if (!day) lights(cv, x, 372f - h, x + w, 372f, rnd)
            x += w + 2f
        }
    }

    fun foreground(cv: Canvas, day: Boolean, cond: Int) {
        val rnd = Random(77)
        val c = if (day) (if (cond == 0 || cond == 1) 0xFF45525FL else 0xFF51647AL) else 0xFF080C1BL
        val p = fill(argb(c))
        var x = -20f
        while (x < 430f) { val w = 22f + rnd.nextInt(30); val h = 8f + rnd.nextInt(22); cv.drawRect(x, 346f - h, x + w, 420f, p); x += w }
        cv.drawRect(-40f, 346f, 450f, 420f, p)
    }

    fun landmark(cv: Canvas, scene: Int, day: Boolean, cond: Int) {
        when (scene) {
            1 -> klcc(cv, day, cond); 2 -> burj(cv, day, cond); 3 -> taipei(cv, day, cond); 4 -> shanghai(cv, day, cond)
            5 -> empire(cv, day, cond); 6 -> wtc(cv, day, cond); 7 -> shard(cv, day, cond); 8 -> cn(cv, day, cond)
        }
    }

    private fun wins(cv: Canvas, day: Boolean, x0: Float, y0: Float, x1: Float, y1: Float, seed: Int) { if (!day) lights(cv, x0, y0, x1, y1, Random(seed)) }

    private fun tiers(cv: Canvas, cx: Float, t: Array<FloatArray>, p: Paint, day: Boolean, seed: Int) {
        t.forEachIndexed { i, r -> cv.drawRect(cx - r[0], r[1], cx + r[0], r[2], p); wins(cv, day, cx - r[0], r[1], cx + r[0], r[2], seed + i) }
    }

    // ---- 1. KLCC (Petronas Twin Towers, Kuala Lumpur)
    private fun klcc(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond)
        val t = arrayOf(floatArrayOf(34f, 300f, 380f), floatArrayOf(29f, 242f, 300f), floatArrayOf(25f, 196f, 242f), floatArrayOf(21f, 160f, 196f), floatArrayOf(17f, 130f, 160f), floatArrayOf(13f, 108f, 130f))
        val ln = line(if (day) argb(0x66FFFFFFL) else argb(0x33FFFFFFL), 1f)
        for ((n, cx) in floatArrayOf(160f, 250f).withIndex()) {
            val g = grad(cx - 34f, cx + 34f, lt, dk)
            tiers(cv, cx, t, g, day, 100 + n * 20)
            cv.drawPath(Path().apply { moveTo(cx - 13f, 108f); quadTo(cx, 80f, cx + 13f, 108f); close() }, g)
            cv.drawRect(cx - 1.8f, 46f, cx + 1.8f, 92f, g)
            var y = 120f
            while (y < 372f) { val hw = t.firstOrNull { y >= it[1] && y <= it[2] }?.get(0) ?: 0f; cv.drawLine(cx - hw, y, cx + hw, y, ln); y += 12f }
        }
        val br = grad(185f, 225f, lt, dk)
        cv.drawRect(185f, 212f, 225f, 226f, br)
        val sp = line(dk, 3f); cv.drawLine(190f, 226f, 205f, 254f, sp); cv.drawLine(220f, 226f, 205f, 254f, sp)
    }

    // ---- 2. Burj Khalifa (Dubai)
    private fun burj(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond); val cx = 205f; val g = grad(cx - 30f, cx + 30f, lt, dk)
        val t = arrayOf(floatArrayOf(28f, 320f, 380f), floatArrayOf(23f, 275f, 320f), floatArrayOf(19f, 235f, 275f), floatArrayOf(16f, 200f, 235f), floatArrayOf(13f, 170f, 200f), floatArrayOf(10f, 145f, 170f), floatArrayOf(7.5f, 120f, 145f))
        tiers(cv, cx, t, g, day, 200)
        cv.drawPath(Path().apply { moveTo(cx - 6f, 120f); lineTo(cx, 28f); lineTo(cx + 6f, 120f); close() }, g)
        for (s in floatArrayOf(-1f, 1f)) cv.drawPath(Path().apply { moveTo(cx + s * 28f, 380f); lineTo(cx + s * 46f, 380f); lineTo(cx + s * 28f, 292f); close() }, g)
    }

    // ---- 3. Taipei 101
    private fun taipei(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond); val cx = 205f; val g = grad(cx - 22f, cx + 22f, lt, dk)
        cv.drawRect(cx - 24f, 340f, cx + 24f, 380f, g)
        for (i in 0..7) {
            val yb = 336f - 28f * i; val yt = yb - 26f
            cv.drawPath(Path().apply { moveTo(cx - 14f, yb); lineTo(cx + 14f, yb); lineTo(cx + 21f, yt); lineTo(cx - 21f, yt); close() }, g)
            wins(cv, day, cx - 14f, yt, cx + 14f, yb, 300 + i)
        }
        cv.drawRect(cx - 12f, 90f, cx + 12f, 112f, g); cv.drawRect(cx - 8f, 72f, cx + 8f, 90f, g); cv.drawRect(cx - 1.6f, 36f, cx + 1.6f, 72f, g)
    }

    // ---- 4. Shanghai: Oriental Pearl Tower + Shanghai Tower
    private fun shanghai(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond); val g = grad(110f, 190f, lt, dk); val cx = 150f
        val leg = line(dk, 5f); cv.drawLine(cx - 22f, 380f, cx - 4f, 300f, leg); cv.drawLine(cx + 22f, 380f, cx + 4f, 300f, leg)
        cv.drawCircle(cx, 290f, 20f, g); cv.drawRect(cx - 3f, 215f, cx + 3f, 272f, g); cv.drawCircle(cx, 205f, 13f, g)
        cv.drawRect(cx - 2f, 150f, cx + 2f, 192f, g); cv.drawCircle(cx, 140f, 7f, g); cv.drawRect(cx - 1f, 62f, cx + 1f, 134f, g)
        val g2 = grad(225f, 285f, lt, dk)
        cv.drawPath(Path().apply { moveTo(226f, 380f); lineTo(284f, 380f); cubicTo(284f, 300f, 266f, 200f, 256f, 112f); lineTo(244f, 96f); lineTo(240f, 112f); cubicTo(232f, 200f, 226f, 300f, 226f, 380f); close() }, g2)
        wins(cv, day, 228f, 150f, 282f, 372f, 400)
        cv.drawLine(262f, 380f, 246f, 112f, line(if (day) argb(0x66FFFFFFL) else argb(0x33FFFFFFL), 1.2f))
    }

    // ---- 5. Empire State Building (New York)
    private fun empire(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond); val cx = 205f; val g = grad(cx - 40f, cx + 40f, lt, dk)
        tiers(cv, cx, arrayOf(floatArrayOf(40f, 270f, 380f), floatArrayOf(31f, 215f, 270f), floatArrayOf(22f, 170f, 215f), floatArrayOf(15f, 140f, 170f), floatArrayOf(9f, 112f, 140f)), g, day, 500)
        cv.drawRect(cx - 4f, 80f, cx + 4f, 112f, g); cv.drawRect(cx - 1.2f, 36f, cx + 1.2f, 80f, g)
    }

    // ---- 6. One World Trade Center (New York)
    private fun wtc(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond); val cx = 205f; val g = grad(cx - 27f, cx + 27f, lt, dk)
        cv.drawPath(Path().apply { moveTo(cx - 27f, 380f); lineTo(cx + 27f, 380f); lineTo(cx + 21f, 124f); lineTo(cx, 98f); lineTo(cx - 21f, 124f); close() }, g)
        cv.drawRect(cx - 2.4f, 30f, cx + 2.4f, 102f, g)
        cv.drawPath(Path().apply { moveTo(cx, 380f); lineTo(cx + 27f, 380f); lineTo(cx + 21f, 124f); lineTo(cx, 98f); close() }, fill(argb(0x40000000L)))
        wins(cv, day, cx - 24f, 130f, cx + 24f, 372f, 600)
    }

    // ---- 7. The Shard (London)
    private fun shard(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond); val cx = 205f; val g = grad(cx - 45f, cx + 45f, lt, dk)
        cv.drawPath(Path().apply { moveTo(cx - 44f, 380f); lineTo(cx + 46f, 380f); lineTo(cx + 6f, 60f); lineTo(cx - 3f, 66f); close() }, g)
        val ln = line(if (day) argb(0x77FFFFFFL) else argb(0x44FFFFFFL), 1.1f)
        cv.drawLine(cx - 18f, 380f, cx - 2f, 70f, ln); cv.drawLine(cx + 2f, 380f, cx + 4f, 66f, ln); cv.drawLine(cx + 24f, 380f, cx + 6f, 64f, ln)
        wins(cv, day, cx - 30f, 200f, cx + 30f, 372f, 700)
    }

    // ---- 8. CN Tower (Toronto)
    private fun cn(cv: Canvas, day: Boolean, cond: Int) {
        val (lt, dk) = body(day, cond); val cx = 205f; val g = grad(cx - 26f, cx + 26f, lt, dk)
        cv.drawPath(Path().apply { moveTo(cx - 22f, 380f); lineTo(cx - 5f, 300f); lineTo(cx + 5f, 300f); lineTo(cx + 22f, 380f); close() }, g)
        cv.drawPath(Path().apply { moveTo(cx - 5f, 300f); lineTo(cx - 3.4f, 188f); lineTo(cx + 3.4f, 188f); lineTo(cx + 5f, 300f); close() }, g)
        cv.drawOval(RectF(cx - 26f, 168f, cx + 26f, 190f), g); cv.drawOval(RectF(cx - 17f, 156f, cx + 17f, 172f), g)
        cv.drawRect(cx - 3f, 126f, cx + 3f, 160f, g); cv.drawOval(RectF(cx - 7f, 122f, cx + 7f, 136f), g); cv.drawRect(cx - 1.6f, 38f, cx + 1.6f, 126f, g)
        if (!day) { val p = fill(argb(0xFFFFD27AL)); var x = cx - 22f; while (x < cx + 22f) { cv.drawRect(x, 176f, x + 3f, 180f, p); x += 6f } }
    }
}
