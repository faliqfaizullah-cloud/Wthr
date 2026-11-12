package com.wthr.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import kotlin.math.cos
import kotlin.math.sin

/** Black 2x2 "sentence" widget, 28dp corners: "24° now / in New York / feels 18° / rain next / 2 hrs". Live data. */
class WthrTextWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { WeatherWorker.schedule(c); ids.forEach { render(c, m, it) } }

    override fun onEnabled(c: Context) { WeatherWorker.schedule(c); WeatherWorker.now(c) }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: android.os.Bundle) = render(c, m, id)

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == WthrWidget.ACTION_REFRESH) { Haptics.init(c); Haptics.click(); WeatherWorker.now(c) }
    }

    companion object {
        private val WHITE = 0xFFFFFFFF.toInt()
        private val GRAY = 0xFF767676.toInt()

        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, WthrTextWidget::class.java)).forEach { render(c, m, it) }
        }

        fun render(c: Context, m: AppWidgetManager, id: Int) {
            val d = c.resources.displayMetrics.density
            val o = m.getAppWidgetOptions(id)
            val dp = minOf(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)).coerceAtLeast(110)
            val px = (dp * d).toInt().coerceAtMost(520)
            val rv = RemoteViews(c.packageName, R.layout.widget_wthr)
            rv.setImageViewBitmap(R.id.widget_img, draw(px, 28f * px / dp, WeatherRepo.cached(c)))
            val pi = PendingIntent.getBroadcast(c, 1, Intent(c, WthrTextWidget::class.java).setAction(WthrWidget.ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            m.updateAppWidget(id, rv)
        }

        // 100x100 unit grid; line centres and left margin measured from the reference picture
        private fun draw(px: Int, radiusPx: Float, w: Weather?): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp); val s = px / 100f
            cv.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), radiusPx, radiusPx, Path.Direction.CW) })
            cv.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, px.toFloat(), 0xFF171717.toInt(), 0xFF0A0A0A.toInt(), Shader.TileMode.CLAMP) })
            cv.scale(s, s)

            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.02f }
            val x0 = 12.7f; val maxW = 100f - 2 * x0 + 2f

            fun line(cy: Float, segs: List<Pair<String, Int>>, icon: Int = -1) {
                var size = 13.3f
                fun width(): Float { t.textSize = size; return (if (icon >= 0) 14.7f * size / 13.3f else 0f) + segs.fold(0f) { a, p -> a + t.measureText(p.first) } }
                while (width() > maxW && size > 6f) size -= 0.3f
                t.textSize = size
                var x = x0
                if (icon >= 0) { val sc = size / 13.3f; cv.save(); cv.translate(x0 + 6f * sc, cy); cv.scale(sc, sc); icon(cv, icon); cv.restore(); x += 14.7f * sc }
                segs.forEach { (txt, col) -> t.color = col; cv.drawText(txt, x, cy + 0.355f * size, t); x += t.measureText(txt) }
            }

            val city = (w?.city ?: "").ifBlank { w?.country ?: "" }.ifBlank { "…" }
            line(21.3f, listOf((if (w == null) "--°" else "${w.temp}°") to WHITE, " now" to GRAY))
            line(36.0f, listOf("in " to GRAY, city to WHITE))
            line(50.7f, listOf("feels " to GRAY, (if (w == null) "--°" else "${w.feels}°") to WHITE))
            if (w == null) {
                line(65.4f, listOf("loading" to WHITE), 5)
                line(80.1f, listOf("tap to refresh" to GRAY))
            } else {
                val cond = widgetCond(w)
                line(65.4f, listOf(condWord[cond] to WHITE, " next" to GRAY), cond)
                line(80.1f, listOf("2 hrs" to GRAY))
            }
            return bmp
        }

        // ---- white icons, drawn around (0,0), about 12 units wide ----
        private fun cloud(cv: Canvas, p: Paint, dy: Float) {
            cv.drawCircle(-3.0f, dy + 0.3f, 2.6f, p); cv.drawCircle(0f, dy - 1.4f, 3.4f, p); cv.drawCircle(3.2f, dy + 0.4f, 2.5f, p)
            cv.drawRoundRect(-5.6f, dy + 0.2f, 5.7f, dy + 2.9f, 1.4f, 1.4f, p)
        }

        private fun icon(cv: Canvas, kind: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE }
            val l = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; style = Paint.Style.STROKE; strokeWidth = 1.2f; strokeCap = Paint.Cap.ROUND }
            when (kind) {
                3 -> { cv.drawCircle(0f, 0f, 2.8f, p)
                    for (i in 0 until 8) { val a = i * Math.PI / 4; cv.drawLine((4.0 * cos(a)).toFloat(), (4.0 * sin(a)).toFloat(), (5.6 * cos(a)).toFloat(), (5.6 * sin(a)).toFloat(), l) } }
                4 -> { val moon = Path().apply { addCircle(-0.4f, 0f, 4.6f, Path.Direction.CW) }
                    moon.op(Path().apply { addCircle(2.0f, -1.8f, 3.9f, Path.Direction.CW) }, Path.Op.DIFFERENCE); cv.drawPath(moon, p) }
                5 -> cloud(cv, p, 0.6f)
                else -> {
                    cloud(cv, p, -1.8f)
                    when (kind) {
                        0 -> for (i in -1..1) cv.drawLine(i * 3.2f + 0.8f, 2.4f, i * 3.2f - 0.4f, 5.4f, l)
                        1 -> { cv.drawLine(-4.2f, 2.6f, -5.0f, 5.0f, l); cv.drawLine(5.0f, 2.6f, 4.2f, 5.0f, l)
                            cv.drawPath(Path().apply { moveTo(1.4f, 1.6f); lineTo(-1.2f, 4.2f); lineTo(0.4f, 4.2f); lineTo(-0.9f, 6.4f); lineTo(2.4f, 3.2f); lineTo(0.8f, 3.2f); close() }, p) }
                        else -> { cv.drawCircle(-3f, 4.0f, 0.9f, p); cv.drawCircle(0f, 5.2f, 0.9f, p); cv.drawCircle(3f, 4.0f, 0.9f, p) }
                    }
                }
            }
        }
    }
}
