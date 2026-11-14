package com.wthr.app

import android.graphics.*
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun argb(v: Long): Int = v.toInt()

/**
 * Realistic-style skyline scenes drawn in code on the 409x409 design grid (offline, no downloads).
 * Scenes: 0 Moscow photo, 1 KLCC, 2 Merdeka 118, 3 Burj Khalifa, 4 Taipei 101, 5 Shanghai, 6 Empire State,
 * 7 One WTC, 8 The Shard, 9 CN Tower. Changes every 5 minutes.
 * Layer order: sky -> far/mid/near city (haze) -> [big number] -> landmark -> foreground.
 */
object Skyline {
    const val SCENES = 10
    fun sceneNow(): Int = ((System.currentTimeMillis() / 300_000L) % SCENES).toInt()
    fun hourNow(): Float { val c = Calendar.getInstance(); return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f }

    // ---------- small helpers ----------
    fun withAlpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(255, (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt())
    }
    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }
    private fun stroke(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND }
    fun glow(cv: Canvas, x: Float, y: Float, r: Float, c: Int, a: Float) {
        cv.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, r, intArrayOf(withAlpha(c, a), withAlpha(c, a * 0.4f), withAlpha(c, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) })
    }

    // ---------- sky ----------
    class SkyCol(val top: Int, val mid: Int, val hor: Int)

    fun skyCol(day: Boolean, cond: Int, h: Float): SkyCol {
        val base = if (!day) {
            if (h in 18.8f..21f || h in 4.5f..6.2f) SkyCol(argb(0xFF1A2350L), argb(0xFF5A4A7CL), argb(0xFFE0866AL))
            else SkyCol(argb(0xFF050A22L), argb(0xFF132056L), argb(0xFF2A3F7CL))
        } else when {
            h < 7f -> SkyCol(argb(0xFF3E5C9AL), argb(0xFFD39A8AL), argb(0xFFFFD29AL))
            h < 10f -> SkyCol(argb(0xFF4A90E2L), argb(0xFF8FC4EEL), argb(0xFFD6ECF8L))
            h < 16f -> SkyCol(argb(0xFF2F78D2L), argb(0xFF5FA2E6L), argb(0xFFB5DAF3L))
            h < 18f -> SkyCol(argb(0xFF3F74C0L), argb(0xFF9BB4D4L), argb(0xFFF3CE98L))
            else -> SkyCol(argb(0xFF2C3E7AL), argb(0xFFE8735AL), argb(0xFFFFB067L))
        }
        val gt = when (cond) { 0 -> 0.72f; 1 -> 0.9f; 2 -> 0.7f; 5 -> 0.55f; else -> 0f }
        if (gt == 0f) return base
        val gray = if (day) when (cond) { 1 -> argb(0xFF3C4552L); 0 -> argb(0xFF66727FL); 2 -> argb(0xFFB5C1CDL); else -> argb(0xFF8795A3L) }
        else when (cond) { 1 -> argb(0xFF0C1019L); 0 -> argb(0xFF141A28L); 2 -> argb(0xFF2A3350L); else -> argb(0xFF1B2236L) }
        return SkyCol(mix(base.top, gray, gt), mix(base.mid, gray, gt), mix(base.hor, gray, gt * 0.8f))
    }

    /** x, y of the sun (day) or moon (night) along a low arc. */
    fun celestial(day: Boolean, h: Float): FloatArray {
        val t = if (day) ((h - 6f) / 12f).coerceIn(0f, 1f) else (((h - 18f + 24f) % 24f) / 12f).coerceIn(0f, 1f)
        return floatArrayOf(70f + 270f * t, 150f - 85f * sin(PI * t).toFloat())
    }

    private fun moon(cv: Canvas, x: Float, y: Float, vis: Float) {
        val r = 14f
        val ph = ((((System.currentTimeMillis() - 947182440000L) / 86400000.0) % 29.530588) / 29.530588).toFloat()   // 0 new, 0.5 full
        val illum = 0.5f * (1f - cos(2.0 * PI * ph).toFloat())
        glow(cv, x, y, 70f, argb(0xFFCFD8FFL), 0.33f * vis)
        cv.drawCircle(x, y, r, fill(withAlpha(argb(0xFF8A93B8L), 0.35f * vis)))              // earthshine
        val lit = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
        val off = (if (ph < 0.5f) -1f else 1f) * 2f * r * illum
        lit.op(Path().apply { addCircle(x + off, y, r, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        cv.drawPath(lit, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x - 4f, y - 4f, r * 1.6f, withAlpha(argb(0xFFFFFBEAL), vis), withAlpha(argb(0xFFD3D0BCL), vis), Shader.TileMode.CLAMP) })
    }

    fun sky(cv: Canvas, day: Boolean, cond: Int) {
        val h = hourNow(); val s = skyCol(day, cond, h)
        cv.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 380f, intArrayOf(s.top, s.mid, s.hor), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) })
        val cel = celestial(day, h)
        val vis = when (cond) { 3, 4 -> 1f; 5 -> 0.35f; 2 -> 0.2f; else -> 0f }
        if (vis > 0f) {
            if (day) { glow(cv, cel[0], cel[1], 100f, argb(0xFFFFE9A8L), 0.55f * vis); glow(cv, cel[0], cel[1], 34f, argb(0xFFFFFFFFL), 0.7f * vis); cv.drawCircle(cel[0], cel[1], 13f, fill(withAlpha(argb(0xFFFFFBE6L), vis))) }
            else moon(cv, cel[0], cel[1], vis)
        }
        if (!day && (cond == 4 || cond == 5)) {
            val rnd = Random(3); val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(if (cond == 4) 80 else 28) {
                val x = rnd.nextFloat() * 409f; val y = rnd.nextFloat() * 230f; val r = 0.5f + rnd.nextFloat() * 1.1f
                p.color = withAlpha(Color.WHITE, 0.35f + 0.65f * rnd.nextFloat()); cv.drawCircle(x, y, r, p)
            }
        }
    }

    // ---------- clouds (soft volumetric puffs; drawn by the animated overlay) ----------
    fun cloudSpec(cond: Int): Array<FloatArray> = when (cond) {
        0, 1 -> arrayOf(floatArrayOf(30f, 22f, 1.5f), floatArrayOf(150f, 42f, 1.5f), floatArrayOf(260f, 18f, 1.6f), floatArrayOf(370f, 40f, 1.5f), floatArrayOf(90f, 70f, 1.3f), floatArrayOf(220f, 74f, 1.3f), floatArrayOf(340f, 72f, 1.3f))
        2 -> arrayOf(floatArrayOf(40f, 30f, 1.4f), floatArrayOf(180f, 50f, 1.5f), floatArrayOf(300f, 28f, 1.4f), floatArrayOf(380f, 66f, 1.2f))
        5 -> arrayOf(floatArrayOf(40f, 38f, 1.2f), floatArrayOf(160f, 66f, 1.1f), floatArrayOf(280f, 34f, 1.3f), floatArrayOf(380f, 74f, 1.0f), floatArrayOf(110f, 16f, 1.0f))
        else -> arrayOf(floatArrayOf(60f, 46f, 0.8f), floatArrayOf(250f, 30f, 1.0f), floatArrayOf(360f, 84f, 0.7f))
    }

    fun cloudColors(day: Boolean, cond: Int, h: Float): IntArray {
        var l: Int; val s: Int
        if (day) when (cond) {
            0 -> { l = argb(0xFF8E9AA8L); s = argb(0xFF46505EL) }
            1 -> { l = argb(0xFF6A7482L); s = argb(0xFF2C3440L) }
            2 -> { l = argb(0xFFE2E8EEL); s = argb(0xFFA9B6C4L) }
            5 -> { l = argb(0xFFE8EDF2L); s = argb(0xFF9AA8B8L) }
            else -> { l = argb(0xFFFFFFFFL); s = argb(0xFFB9C9DAL) }
        } else when (cond) {
            0 -> { l = argb(0xFF3A455FL); s = argb(0xFF121828L) }
            1 -> { l = argb(0xFF2A3348L); s = argb(0xFF0A0E18L) }
            2 -> { l = argb(0xFF59668AL); s = argb(0xFF252E4AL) }
            5 -> { l = argb(0xFF45507AL); s = argb(0xFF1A2140L) }
            else -> { l = argb(0xFF3A4A78L); s = argb(0xFF161C38L) }
        }
        if (day && (h < 7.8f || h > 16.5f)) l = mix(l, argb(0xFFFFC890L), 0.45f)
        return intArrayOf(l, s)
    }

    fun cloudBlob(cv: Canvas, cx: Float, cy: Float, s: Float, seed: Int, light: Int, shade: Int, a: Float) {
        val n = 9
        for (pass in 0..1) for (i in 0 until n) {
            val u = (i - (n - 1) / 2f) / ((n - 1) / 2f)
            val k = 1f - abs(u) * 0.75f
            val r = (13f + 15f * k) * s * (0.85f + 0.3f * Random(seed * 13 + i).nextFloat())
            val x = cx + u * 46f * s
            val y = cy - k * 10f * s + (if (pass == 0) r * 0.30f else -r * 0.10f)
            val col = if (pass == 0) shade else light
            val al = (if (pass == 0) 0.55f else 0.85f) * a
            cv.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x, y, r, intArrayOf(withAlpha(col, al), withAlpha(col, al * 0.55f), withAlpha(col, 0f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP) })
        }
    }

    // ---------- city layers ----------
    private val winCols = intArrayOf(argb(0xFFFFD98AL), argb(0xFFFFEBB8L), argb(0xFFBFD9FFL), argb(0xFFFFC46BL))
    private fun lights(cv: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rnd: Random, prob: Float, a: Int = 200) {
        val p = Paint()
        var y = y0 + 5f
        while (y < y1 - 4f) {
            var x = x0 + 3.5f
            while (x < x1 - 3.5f) { if (rnd.nextFloat() < prob) { p.color = winCols[rnd.nextInt(winCols.size)]; p.alpha = a - rnd.nextInt(70); cv.drawRect(x, y, x + 2.4f, y + 3.2f, p) }; x += 5.5f }
            y += 7f
        }
    }

    /** Three depth layers blended towards the horizon colour (atmospheric perspective) + low haze. */
    fun far(cv: Canvas, day: Boolean, cond: Int, scene: Int) {
        val h = hourNow(); val sc = skyCol(day, cond, h)
        val baseC = if (day) argb(0xFF5F7794L) else argb(0xFF121A3AL)
        val layers = arrayOf(floatArrayOf(0.78f, 40f, 105f, 13f, 22f), floatArrayOf(0.58f, 55f, 135f, 15f, 26f), floatArrayOf(0.38f, 70f, 165f, 17f, 30f))
        layers.forEachIndexed { li, L ->
            val rnd = Random(scene * 31 + li * 7 + 5)
            val p = fill(mix(baseC, sc.hor, L[0]))
            var x = -20f - li * 9f
            while (x < 430f) {
                val w = L[3] + rnd.nextInt((L[4] - L[3]).toInt()); val hh = L[1] + rnd.nextInt((L[2] - L[1]).toInt())
                cv.drawRect(x, 376f - hh, x + w, 390f, p)
                if (rnd.nextInt(5) == 0) cv.drawRect(x + w / 2f - 0.8f, 376f - hh - 12f, x + w / 2f + 0.8f, 376f - hh, p)
                if (!day) lights(cv, x, 376f - hh, x + w, 376f, rnd, 0.22f + 0.2f * li, 150 + 25 * li)
                x += w + 1.5f
            }
        }
        cv.drawRect(-40f, 230f, 450f, 390f, Paint().apply { shader = LinearGradient(0f, 230f, 0f, 385f, withAlpha(sc.hor, 0f), withAlpha(sc.hor, if (day) 0.55f else 0.3f), Shader.TileMode.CLAMP) })
    }

    fun foreground(cv: Canvas, day: Boolean, cond: Int, scene: Int) {
        val h = hourNow(); val sc = skyCol(day, cond, h); val rnd = Random(77 + scene)
        val c0 = if (day) mix(argb(0xFF3E5169L), sc.hor, 0.12f) else argb(0xFF070B1AL)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 320f, 0f, 410f, mix(c0, Color.WHITE, if (day) 0.12f else 0.05f), c0, Shader.TileMode.CLAMP) }
        var x = -20f
        while (x < 430f) {
            val w = 24f + rnd.nextInt(34); val hh = 6f + rnd.nextInt(26)
            cv.drawRect(x, 346f - hh, x + w, 420f, p)
            if (!day) lights(cv, x, 346f - hh, x + w, 346f, rnd, 0.3f, 150)
            x += w
        }
        cv.drawRect(-40f, 346f, 450f, 420f, p)
        cv.drawRect(-40f, 320f, 450f, 380f, Paint().apply { shader = LinearGradient(0f, 320f, 0f, 380f, withAlpha(sc.hor, 0f), withAlpha(sc.hor, if (day) 0.28f else 0.16f), Shader.TileMode.CLAMP) })
    }

    // ---------- glass facade ----------
    private fun body(day: Boolean, cond: Int, h: Float): IntArray {
        if (!day) return intArrayOf(argb(0xFF4A5886L), argb(0xFF141B38L))
        var lt = if (cond == 0 || cond == 1) argb(0xFFB9C3CFL) else argb(0xFFEAF0F5L)
        var dk = if (cond == 0 || cond == 1) argb(0xFF6C7886L) else argb(0xFF8E9FB3L)
        if (h < 7.8f || h > 16.5f) { lt = mix(lt, argb(0xFFFFC890L), 0.45f); dk = mix(dk, argb(0xFF6A5A7AL), 0.3f) }
        return intArrayOf(lt, dk)
    }

    private fun facade(cv: Canvas, path: Path, r: RectF, day: Boolean, cond: Int, seed: Int, tint: Int, cell: Float) {
        val h = hourNow(); val sc = skyCol(day, cond, h)
        val litLeft = celestial(day, h)[0] < 204f
        val b = body(day, cond, h); var lt = b[0]; var dk = b[1]
        if (tint != 0) { lt = mix(lt, tint, 0.35f); dk = mix(dk, tint, 0.35f) }
        cv.save(); cv.clipPath(path)
        cv.drawRect(r, Paint().apply { shader = LinearGradient(r.left, 0f, r.right, 0f, if (litLeft) lt else dk, if (litLeft) dk else lt, Shader.TileMode.CLAMP) })
        cv.drawRect(r, Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(withAlpha(sc.top, if (day) 0.42f else 0.25f), withAlpha(sc.mid, 0.12f), 0x00000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) })
        val rnd = Random(seed)
        if (day) {
            val q = Paint(); var yy = r.top
            while (yy < r.bottom) { var xx = r.left
                while (xx < r.right) { if (rnd.nextInt(3) == 0) { q.color = if (rnd.nextBoolean()) argb(0x22FFFFFFL) else argb(0x22000000L); cv.drawRect(xx, yy, xx + cell, yy + cell * 1.15f, q) }; xx += cell }
                yy += cell * 1.15f }
        } else lights(cv, r.left, r.top, r.right, r.bottom, rnd, 0.42f)
        val g = stroke(if (day) argb(0x33203040L) else argb(0x40000000L), 0.5f)
        var y = r.top; while (y < r.bottom) { cv.drawLine(r.left, y, r.right, y, g); y += cell * 1.15f }
        var x = r.left; while (x < r.right) { cv.drawLine(x, r.top, x, r.bottom, g); x += cell * 1.2f }
        val ex = if (litLeft) r.left + 1f else r.right - 1f
        cv.drawLine(ex, r.top, ex, r.bottom, stroke(argb(0x99FFFFFFL), 1.6f))
        cv.drawRect(r, Paint().apply { shader = LinearGradient(0f, r.bottom - 90f, 0f, r.bottom, 0x00000000, argb(0x66000000L), Shader.TileMode.CLAMP) })
        cv.restore()
    }

    private fun steel(cv: Canvas, p: Path, x0: Float, x1: Float, day: Boolean, cond: Int) {
        val b = body(day, cond, hourNow())
        cv.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, 0f, x1, 0f, mix(b[0], Color.WHITE, 0.4f), mix(b[1], Color.BLACK, 0.15f), Shader.TileMode.CLAMP) })
    }
    private fun rect(l: Float, t: Float, r: Float, b: Float) = Path().apply { addRect(l, t, r, b, Path.Direction.CW) }
    private fun stepPath(cx: Float, t: Array<FloatArray>) = Path().apply { t.forEach { addRect(cx - it[0], it[1], cx + it[0], it[2], Path.Direction.CW) } }

    // ---------- landmarks ----------
    fun landmark(cv: Canvas, scene: Int, day: Boolean, cond: Int) {
        if (!day) glow(cv, 205f, 300f, 170f, argb(0xFF7A8FE0L), 0.2f)
        when (scene) {
            1 -> klcc(cv, day, cond); 2 -> merdeka(cv, day, cond); 3 -> burj(cv, day, cond); 4 -> taipei(cv, day, cond); 5 -> shanghai(cv, day, cond)
            6 -> empire(cv, day, cond); 7 -> wtc(cv, day, cond); 8 -> shard(cv, day, cond); 9 -> cn(cv, day, cond)
        }
    }

    /** Aviation-light positions (red blinkers) for each scene. */
    fun beacons(scene: Int): Array<FloatArray> = when (scene) {
        1 -> arrayOf(floatArrayOf(160f, 44f), floatArrayOf(250f, 44f)); 2 -> arrayOf(floatArrayOf(205f, 28f)); 3 -> arrayOf(floatArrayOf(205f, 28f))
        4 -> arrayOf(floatArrayOf(205f, 36f)); 5 -> arrayOf(floatArrayOf(150f, 62f), floatArrayOf(244f, 96f)); 6 -> arrayOf(floatArrayOf(205f, 36f))
        7 -> arrayOf(floatArrayOf(205f, 30f)); 8 -> arrayOf(floatArrayOf(209f, 60f)); 9 -> arrayOf(floatArrayOf(205f, 38f)); else -> arrayOf()
    }

    /** Random window positions on the landmark, used for twinkling lights at night. */
    fun windowSpots(scene: Int): List<FloatArray> {
        val r: FloatArray = when (scene) {
            1 -> floatArrayOf(126f, 120f, 284f, 372f); 2 -> floatArrayOf(182f, 130f, 228f, 372f); 3 -> floatArrayOf(180f, 130f, 230f, 372f)
            4 -> floatArrayOf(186f, 120f, 224f, 372f); 5 -> floatArrayOf(228f, 130f, 282f, 372f); 6 -> floatArrayOf(165f, 150f, 245f, 372f)
            7 -> floatArrayOf(182f, 130f, 228f, 372f); 8 -> floatArrayOf(176f, 150f, 234f, 372f); else -> return emptyList()
        }
        val rnd = Random(scene * 17 + 3)
        return List(18) { floatArrayOf(r[0] + rnd.nextFloat() * (r[2] - r[0]), r[1] + rnd.nextFloat() * (r[3] - r[1])) }
    }

    private fun klcc(cv: Canvas, day: Boolean, cond: Int) {
        val t = arrayOf(floatArrayOf(34f, 300f, 380f), floatArrayOf(29f, 242f, 300f), floatArrayOf(25f, 196f, 242f), floatArrayOf(21f, 160f, 196f), floatArrayOf(17f, 130f, 160f), floatArrayOf(13f, 108f, 130f))
        val ribs = stroke(argb(0xAAFFFFFFL), 1.1f)
        for ((n, cx) in floatArrayOf(160f, 250f).withIndex()) {
            facade(cv, stepPath(cx, t), RectF(cx - 34f, 108f, cx + 34f, 380f), day, cond, 100 + n, 0, 5.5f)
            t.forEach { cv.drawLine(cx - it[0], it[1], cx + it[0], it[1], ribs) }
            steel(cv, Path().apply { moveTo(cx - 13f, 108f); quadTo(cx, 78f, cx + 13f, 108f); close() }, cx - 13f, cx + 13f, day, cond)
            steel(cv, rect(cx - 1.8f, 44f, cx + 1.8f, 92f), cx - 2f, cx + 2f, day, cond)
        }
        val br = RectF(185f, 212f, 225f, 226f); facade(cv, rect(br.left, br.top, br.right, br.bottom), br, day, cond, 150, 0, 4f)
        val leg = stroke(mix(body(day, cond, hourNow())[1], Color.WHITE, 0.25f), 3f); cv.drawLine(190f, 226f, 205f, 254f, leg); cv.drawLine(220f, 226f, 205f, 254f, leg)
    }

    // Merdeka 118 (Kuala Lumpur): tapering faceted crown on a diamond-lattice glass body
    private fun merdeka(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 205f
        val pod = Path().apply { addRect(cx - 44f, 338f, cx + 44f, 380f, Path.Direction.CW) }
        facade(cv, pod, RectF(cx - 44f, 338f, cx + 44f, 380f), day, cond, 880, argb(0xFF6FB4C8L), 5f)
        val body = Path().apply { moveTo(cx - 34f, 380f); lineTo(cx - 24f, 250f); lineTo(cx - 17f, 150f); lineTo(cx - 12f, 118f); lineTo(cx, 76f); lineTo(cx + 12f, 118f); lineTo(cx + 17f, 150f); lineTo(cx + 24f, 250f); lineTo(cx + 34f, 380f); close() }
        facade(cv, body, RectF(cx - 34f, 76f, cx + 34f, 380f), day, cond, 900, argb(0xFF6FB4C8L), 5f)
        cv.save(); cv.clipPath(body)
        val dl = stroke(argb(0x66FFFFFFL), 0.9f)
        for (i in -6..28) { val y0 = 76f + i * 14f; cv.drawLine(cx - 40f, y0, cx + 40f, y0 + 56f, dl); cv.drawLine(cx - 40f, y0 + 56f, cx + 40f, y0, dl) }
        val cr = Path().apply { moveTo(cx - 12f, 118f); lineTo(cx, 76f); lineTo(cx + 12f, 118f); close() }
        cv.drawPath(cr, fill(withAlpha(Color.WHITE, if (day) 0.22f else 0.12f)))
        cv.drawLine(cx, 76f, cx - 4f, 118f, stroke(argb(0x88FFFFFFL), 1f)); cv.drawLine(cx, 76f, cx + 4f, 118f, stroke(argb(0x88FFFFFFL), 1f))
        cv.restore()
        steel(cv, rect(cx - 1.4f, 26f, cx + 1.4f, 82f), cx - 2f, cx + 2f, day, cond)
    }

    private fun burj(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 205f
        val t = arrayOf(floatArrayOf(28f, 320f, 380f), floatArrayOf(23f, 275f, 320f), floatArrayOf(19f, 235f, 275f), floatArrayOf(16f, 200f, 235f), floatArrayOf(13f, 170f, 200f), floatArrayOf(10f, 145f, 170f), floatArrayOf(7.5f, 120f, 145f))
        val p = stepPath(cx, t)
        for (s in floatArrayOf(-1f, 1f)) p.addPath(Path().apply { moveTo(cx + s * 28f, 380f); lineTo(cx + s * 46f, 380f); lineTo(cx + s * 28f, 292f); close() })
        facade(cv, p, RectF(cx - 46f, 120f, cx + 46f, 380f), day, cond, 200, 0, 5f)
        steel(cv, Path().apply { moveTo(cx - 6f, 122f); lineTo(cx, 26f); lineTo(cx + 6f, 122f); close() }, cx - 6f, cx + 6f, day, cond)
    }

    private fun taipei(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 205f
        val p = Path().apply { addRect(cx - 24f, 340f, cx + 24f, 380f, Path.Direction.CW)
            for (i in 0..7) { val yb = 336f - 28f * i; val yt = yb - 26f; moveTo(cx - 14f, yb); lineTo(cx + 14f, yb); lineTo(cx + 21f, yt); lineTo(cx - 21f, yt); close() } }
        facade(cv, p, RectF(cx - 24f, 110f, cx + 24f, 380f), day, cond, 300, argb(0xFF5FA89AL), 5f)
        val gold = stroke(argb(0xAAE6C46AL), 1.2f); for (i in 0..7) cv.drawLine(cx - 20f, 336f - 28f * i - 26f, cx + 20f, 336f - 28f * i - 26f, gold)
        steel(cv, stepPath(cx, arrayOf(floatArrayOf(12f, 90f, 112f), floatArrayOf(8f, 72f, 90f), floatArrayOf(1.6f, 36f, 72f))), cx - 12f, cx + 12f, day, cond)
    }

    private fun shanghai(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 150f; val dim = if (day) 0f else 0.55f
        fun ball(x: Float, y: Float, r: Float) = cv.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x - r * 0.35f, y - r * 0.35f, r * 1.5f, mix(argb(0xFFFFC2E8L), argb(0xFF1A1030L), dim), mix(argb(0xFFB23C8AL), argb(0xFF1A1030L), dim), Shader.TileMode.CLAMP) })
        val leg = stroke(mix(body(day, cond, hourNow())[1], Color.WHITE, 0.2f), 5f); cv.drawLine(cx - 22f, 380f, cx - 4f, 300f, leg); cv.drawLine(cx + 22f, 380f, cx + 4f, 300f, leg)
        steel(cv, rect(cx - 3f, 215f, cx + 3f, 272f), cx - 3f, cx + 3f, day, cond); steel(cv, rect(cx - 2f, 150f, cx + 2f, 192f), cx - 2f, cx + 2f, day, cond); steel(cv, rect(cx - 1f, 62f, cx + 1f, 134f), cx - 1f, cx + 1f, day, cond)
        ball(cx, 290f, 20f); ball(cx, 205f, 13f); ball(cx, 140f, 7f)
        val twr = Path().apply { moveTo(226f, 380f); lineTo(284f, 380f); cubicTo(284f, 300f, 266f, 200f, 256f, 112f); lineTo(244f, 96f); lineTo(240f, 112f); cubicTo(232f, 200f, 226f, 300f, 226f, 380f); close() }
        facade(cv, twr, RectF(226f, 96f, 284f, 380f), day, cond, 400, argb(0xFF7FB0D8L), 5.5f)
        cv.drawLine(262f, 380f, 246f, 112f, stroke(argb(0x66FFFFFFL), 1.2f))
    }

    private fun empire(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 205f
        val t = arrayOf(floatArrayOf(40f, 270f, 380f), floatArrayOf(31f, 215f, 270f), floatArrayOf(22f, 170f, 215f), floatArrayOf(15f, 140f, 170f), floatArrayOf(9f, 112f, 140f))
        facade(cv, stepPath(cx, t), RectF(cx - 40f, 112f, cx + 40f, 380f), day, cond, 500, argb(0xFFD8CFB8L), 6f)
        if (!day) glow(cv, cx, 140f, 46f, argb(0xFFFFF0C0L), 0.35f)
        steel(cv, rect(cx - 4f, 80f, cx + 4f, 112f), cx - 4f, cx + 4f, day, cond); steel(cv, rect(cx - 1.2f, 36f, cx + 1.2f, 80f), cx - 1.2f, cx + 1.2f, day, cond)
    }

    private fun wtc(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 205f
        val p = Path().apply { moveTo(cx - 27f, 380f); lineTo(cx + 27f, 380f); lineTo(cx + 21f, 124f); lineTo(cx, 98f); lineTo(cx - 21f, 124f); close() }
        facade(cv, p, RectF(cx - 27f, 98f, cx + 27f, 380f), day, cond, 600, argb(0xFF7FA6D0L), 6f)
        cv.drawPath(Path().apply { moveTo(cx, 380f); lineTo(cx + 27f, 380f); lineTo(cx + 21f, 124f); lineTo(cx, 98f); close() }, fill(argb(0x38000000L)))
        steel(cv, rect(cx - 2.4f, 30f, cx + 2.4f, 102f), cx - 2.4f, cx + 2.4f, day, cond)
    }

    private fun shard(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 205f
        val p = Path().apply { moveTo(cx - 44f, 380f); lineTo(cx + 46f, 380f); lineTo(cx + 6f, 60f); lineTo(cx - 3f, 66f); close() }
        facade(cv, p, RectF(cx - 44f, 60f, cx + 46f, 380f), day, cond, 700, argb(0xFF9DB8D6L), 6f)
        val ln = stroke(argb(0x88FFFFFFL), 1.1f)
        cv.drawLine(cx - 18f, 380f, cx - 2f, 70f, ln); cv.drawLine(cx + 2f, 380f, cx + 4f, 66f, ln); cv.drawLine(cx + 24f, 380f, cx + 6f, 64f, ln)
    }

    private fun cn(cv: Canvas, day: Boolean, cond: Int) {
        val cx = 205f
        steel(cv, Path().apply { moveTo(cx - 22f, 380f); lineTo(cx - 5f, 300f); lineTo(cx + 5f, 300f); lineTo(cx + 22f, 380f); close() }, cx - 22f, cx + 22f, day, cond)
        steel(cv, Path().apply { moveTo(cx - 5f, 300f); lineTo(cx - 3.4f, 188f); lineTo(cx + 3.4f, 188f); lineTo(cx + 5f, 300f); close() }, cx - 5f, cx + 5f, day, cond)
        steel(cv, Path().apply { addOval(RectF(cx - 26f, 168f, cx + 26f, 190f), Path.Direction.CW); addOval(RectF(cx - 17f, 156f, cx + 17f, 172f), Path.Direction.CW) }, cx - 26f, cx + 26f, day, cond)
        steel(cv, rect(cx - 3f, 126f, cx + 3f, 160f), cx - 3f, cx + 3f, day, cond); steel(cv, rect(cx - 1.6f, 38f, cx + 1.6f, 126f), cx - 1.6f, cx + 1.6f, day, cond)
        cv.drawLine(cx - 24f, 179f, cx + 24f, 179f, stroke(if (day) argb(0x88203040L) else argb(0xCCFFD27AL), 2.4f))
    }
}
