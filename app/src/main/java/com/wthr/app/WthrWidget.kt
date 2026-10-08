package com.wthr.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** 2x2 home-screen widget, 28dp corners, live weather for the user's location. Tap = refresh now (with haptic). */
class WthrWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { WeatherWorker.schedule(c); ids.forEach { render(c, m, it) } }

    override fun onEnabled(c: Context) { WeatherWorker.schedule(c); WeatherWorker.now(c) }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: android.os.Bundle) = render(c, m, id)

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == ACTION_REFRESH) { Haptics.init(c); Haptics.click(); WeatherWorker.now(c) }
    }

    companion object {
        const val ACTION_REFRESH = "com.wthr.app.REFRESH"

        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, WthrWidget::class.java)).forEach { render(c, m, it) }
        }

        fun render(c: Context, m: AppWidgetManager, id: Int) {
            val d = c.resources.displayMetrics.density
            val o = m.getAppWidgetOptions(id)
            val dp = minOf(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)).coerceAtLeast(110)
            val px = (dp * d).toInt().coerceAtMost(520)
            val rv = RemoteViews(c.packageName, R.layout.widget_wthr)
            rv.setImageViewBitmap(R.id.widget_img, draw(px, 28f * px / dp, WeatherRepo.cached(c)))
            val pi = PendingIntent.getBroadcast(c, 0, Intent(c, WthrWidget::class.java).setAction(ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            m.updateAppWidget(id, rv)
        }

        // ---- drawing, on a 100x100 unit grid so it scales with the widget ----
        private fun draw(px: Int, radiusPx: Float, w: Weather?): Bitmap {
            val kind = w?.kind ?: 0
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp); val s = px / 100f
            cv.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), radiusPx, radiusPx, Path.Direction.CW) })
            cv.drawColor(intArrayOf(0xFFFFFFFF.toInt(), 0xFF3E4046.toInt(), 0xFF14151A.toInt(), 0xFF3E4046.toInt())[kind])
            val fg = if (kind == 0) Color.BLACK else Color.WHITE
            cv.scale(s, s)
            when (kind) { 0 -> sunDoodle(cv); 1 -> rainDoodle(cv, true); 2 -> clearDoodle(cv); else -> rainDoodle(cv, false) }

            val city = (w?.city ?: "Wthr").ifBlank { w?.country ?: "Wthr" }
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; typeface = Typeface.create("casual", Typeface.BOLD); textAlign = Paint.Align.RIGHT }
            t.textSize = 7.6f
            while (t.measureText(city) > 54f && t.textSize > 4f) t.textSize -= 0.4f
            cv.drawText(city, 90f, 15f, t)
            navArrow(cv, 90f - t.measureText(city) - 7f, 9.6f, fg)
            t.textSize = 31f; cv.drawText(if (w == null) "--°" else "${w.temp}°", 90f, 43f, t)
            t.textSize = 7.6f; cv.drawText(arrayOf("Sunny", "Rain", "Clear", "Cloudy")[kind], 90f, 77f, t)
            smallIcon(cv, kind, 83f, 61f)
            val hi = if (w == null) "--°" else "${w.hi}°"; val lo = if (w == null) "--°" else "${w.lo}°"
            t.textSize = 6.6f
            val xLo = 90f; cv.drawText(lo, xLo, 88f, t)
            val xHi = xLo - t.measureText(lo) - 11f; cv.drawText(hi, xHi, 88f, t)
            arrow(cv, xHi - t.measureText(hi) - 3f, 85.4f, true, fg); arrow(cv, xLo - t.measureText(lo) - 3f, 85.4f, false, fg)
            return bmp
        }

        private fun stroke(col: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

        private fun navArrow(cv: Canvas, x: Float, y: Float, col: Int) {
            val p = Path().apply { moveTo(x + 5f, y - 4f); lineTo(x - 1f, y + 0.5f); lineTo(x + 2f, y + 0.8f); lineTo(x + 2.4f, y + 4f); close() }
            cv.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
        }

        private fun arrow(cv: Canvas, x: Float, y: Float, up: Boolean, col: Int) {
            val p = stroke(col, 0.9f); val d = if (up) 1f else -1f
            cv.drawLine(x, y + 3f * d, x, y - 3f * d, p)
            cv.drawLine(x, y - 3f * d, x - 1.8f, y - 1.2f * d, p); cv.drawLine(x, y - 3f * d, x + 1.8f, y - 1.2f * d, p)
        }

        private fun smallIcon(cv: Canvas, state: Int, cx: Float, cy: Float) {
            when (state) {
                0 -> { val o = 0xFFFFA500.toInt(); cv.drawCircle(cx, cy, 2.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = o })
                    for (i in 0 until 8) { val a = i * Math.PI / 4; cv.drawLine((cx + 4 * cos(a)).toFloat(), (cy + 4 * sin(a)).toFloat(), (cx + 5.4 * cos(a)).toFloat(), (cy + 5.4 * sin(a)).toFloat(), stroke(o, 0.8f)) } }
                1, 3 -> { val g = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB0B3B8.toInt() }
                    cv.drawCircle(cx - 1.5f, cy - 0.5f, 2.6f, g); cv.drawCircle(cx + 1.5f, cy - 1.8f, 3.2f, g); cv.drawRoundRect(cx - 4.5f, cy - 0.5f, cx + 5f, cy + 2.6f, 1.6f, 1.6f, g)
                    if (state == 1) for (i in 0..2) cv.drawLine(cx - 2.5f + i * 2.6f, cy + 4f, cx - 3.4f + i * 2.6f, cy + 6.2f, stroke(0xFF4FC3F7.toInt(), 0.7f)) }
                else -> { val w = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                    val moon = diff(Path().apply { addCircle(cx - 0.5f, cy, 3.6f, Path.Direction.CW) }, Path().apply { addCircle(cx + 1.6f, cy - 1.4f, 3.1f, Path.Direction.CW) })
                    cv.drawPath(moon, w); sparkle(cv, cx + 3.6f, cy - 3.6f, 1.6f, Color.WHITE); sparkle(cv, cx + 1.2f, cy - 5.4f, 0.9f, Color.WHITE) }
            }
        }

        private fun diff(a: Path, b: Path) = Path(a).also { it.op(b, Path.Op.DIFFERENCE) }

        private fun sparkle(cv: Canvas, x: Float, y: Float, r: Float, col: Int) {
            val p = Path().apply { moveTo(x, y - r); quadTo(x, y, x + r, y); quadTo(x, y, x, y + r); quadTo(x, y, x - r, y); quadTo(x, y, x, y - r) }
            cv.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
        }

        // ---- doodles ----
        private fun sunDoodle(cv: Canvas) {
            val o = 0xFFFFA500.toInt()
            // hatched quarter-sun in the bottom-left corner
            for (r in 6..36 step 3) { val rect = RectF(8f - r, 100f - r - 4f, 8f + r, 100f + r - 4f)
                cv.drawArc(rect, -92f + (r % 6), 78f + (r % 5), false, stroke(o, 2.4f)) }
            cv.drawLine(2f, 61f, 4f, 76f, stroke(o, 1.9f)); cv.drawLine(11f, 74f, 11f, 80f, stroke(o, 1.9f))
            val rays = arrayOf(floatArrayOf(14f, 58f, 17f, 70f), floatArrayOf(22f, 71f, 23f, 77f), floatArrayOf(37f, 59f, 33f, 70f), floatArrayOf(52f, 68f, 45f, 77f), floatArrayOf(50f, 79f, 43f, 84f), floatArrayOf(56f, 85f, 46f, 87f), floatArrayOf(52f, 93f, 44f, 94f))
            rays.forEach { cv.drawLine(it[0], it[1], it[2], it[3], stroke(o, 1.9f)) }
        }

        private fun rainDoodle(cv: Canvas, drops: Boolean) {
            val w = stroke(Color.WHITE, 1.1f)
            // scribbled cloud: outline + hatch strokes
            val cloud = Path().apply { moveTo(0f, 24f); cubicTo(-2f, 10f, 12f, 2f, 26f, 8f); cubicTo(36f, 10f, 40f, 16f, 44f, 22f); cubicTo(52f, 22f, 54f, 32f, 46f, 36f); cubicTo(38f, 40f, 12f, 40f, 4f, 36f) }
            cv.drawPath(cloud, stroke(Color.WHITE, 1.9f))
            for (i in 0..16) { val x = 1f + i * 2.5f; cv.drawLine(x, 6f + (i % 3), x - 3f, 30f + (i % 4) * 2f, w) }
            for (i in 0..7) cv.drawLine(14f + i * 3.4f, 21f, 12f + i * 3.4f, 32f, w)
            if (!drops) return
            val b = stroke(0xFF4FC3F7.toInt(), 1.4f)
            val xs = floatArrayOf(5f, 10f, 17f, 25f, 33f, 40f, 12f, 21f, 37f, 6f, 28f, 41f)
            val ys = floatArrayOf(50f, 58f, 50f, 52f, 70f, 60f, 82f, 76f, 88f, 68f, 90f, 80f)
            for (i in xs.indices) cv.drawLine(xs[i], ys[i], xs[i] - 0.6f, ys[i] + 9f + (i % 3) * 2f, b)
        }

        private fun clearDoodle(cv: Canvas) {
            val y = 0xFFFFE08A.toInt(); val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = y }
            arrayOf(floatArrayOf(14f, 4f), floatArrayOf(48f, 3f), floatArrayOf(6f, 22f), floatArrayOf(28f, 31f), floatArrayOf(57f, 41f), floatArrayOf(38f, 51f)).forEach { cv.drawCircle(it[0], it[1], 0.7f, dot) }
            arrayOf(floatArrayOf(17f, 22f, 4.2f), floatArrayOf(6f, 34f, 3.6f), floatArrayOf(25f, 42f, 4.4f), floatArrayOf(41f, 56f, 3.8f)).forEach { star(cv, it[0], it[1], it[2], y) }
            // white hatched swoosh bottom-left
            for (i in 0..8) { val p = Path().apply { moveTo(1f + i * 1.2f, 98f - i * 0.3f); cubicTo(2f + i * 3f, 80f, 14f + i * 3f, 66f, 30f + i * 3.4f, 58f + i * 2.6f) }
                cv.drawPath(p, stroke(Color.WHITE, 2.1f)) }
        }

        private fun star(cv: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
            val p = Path()
            for (i in 0 until 10) { val rr = if (i % 2 == 0) r else r * 0.45f; val a = -Math.PI / 2 + i * Math.PI / 5
                val x = (cx + rr * cos(a)).toFloat(); val y = (cy + rr * sin(a)).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            p.close(); cv.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; strokeJoin = Paint.Join.ROUND; style = Paint.Style.FILL_AND_STROKE; strokeWidth = 0.8f })
        }
    }
}
