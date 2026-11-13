package com.wthr.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*

import android.widget.RemoteViews

/**
 * Photo widget: city, huge live temperature and H/L over a sky photo.
 * The number sits BEHIND the church and tulips (separate foreground layer).
 * Morning/day shows the blue sky; night shows a starry night version.
 */
class WthrPhotoWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { WeatherWorker.schedule(c); ids.forEach { render(c, m, it) } }

    override fun onEnabled(c: Context) { WeatherWorker.schedule(c); WeatherWorker.now(c) }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: android.os.Bundle) = render(c, m, id)

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == WthrWidget.ACTION_REFRESH) { Haptics.init(c); Haptics.click(); WeatherWorker.now(c) }
    }

    companion object {
        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, WthrPhotoWidget::class.java)).forEach { render(c, m, it) }
        }

        fun render(c: Context, m: AppWidgetManager, id: Int) {
            val d = c.resources.displayMetrics.density
            val o = m.getAppWidgetOptions(id)
            val dp = minOf(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)).coerceAtLeast(110)
            val px = (dp * d).toInt().coerceAtMost(520)
            val rv = RemoteViews(c.packageName, R.layout.widget_wthr)
            rv.setImageViewBitmap(R.id.widget_img, draw(c, px, 28f * px / dp, WeatherRepo.cached(c)))
            val pi = PendingIntent.getBroadcast(c, 2, Intent(c, WthrPhotoWidget::class.java).setAction(WthrWidget.ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            m.updateAppWidget(id, rv)
        }

        private fun load(c: Context, id: Int): Bitmap {
            val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            return BitmapFactory.decodeResource(c.resources, id, o)
        }

        // Everything is laid out on the 409x409 grid of the reference artwork, then scaled to the widget.
        private fun draw(c: Context, px: Int, radiusPx: Float, w: Weather?): Bitmap {
            val day = w?.isDay ?: true
            val sky = load(c, if (day) R.drawable.w3_sky_day else R.drawable.w3_sky_night)
            val fg = load(c, if (day) R.drawable.w3_fg_day else R.drawable.w3_fg_night)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            cv.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), radiusPx, radiusPx, Path.Direction.CW) })
            cv.scale(px / 409f, px / 409f)
            val fp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            cv.drawBitmap(sky, 0f, 0f, fp)

            // 1) huge temperature, behind the foreground
            val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; typeface = c.resources.getFont(R.font.inter_display_bold); textSize = 252f; textAlign = Paint.Align.CENTER }
            val num = w?.temp?.toString() ?: "--"
            val nw = big.measureText(num)
            if (nw > 307f) big.textSize = 252f * 307f / nw
            cv.drawText(num, 208f, 282f, big)

            // 2) church + tulips + flower bed in front of it
            cv.drawBitmap(fg, 0f, 0f, fp)

            // 3) city and high / low
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = c.resources.getFont(R.font.inter_semibold); textAlign = Paint.Align.CENTER }
            val city = (w?.city ?: "").ifBlank { w?.country ?: "" }.ifBlank { "Wthr" }
            t.textSize = 29.5f
            while (t.measureText(city) > 280f && t.textSize > 14f) t.textSize -= 0.5f
            cv.drawText(city, 204.5f, 53.5f, t)
            t.textSize = 30f
            cv.drawText(if (w == null) "H: --°  L: --°" else "H: ${w.hi}°  L: ${w.lo}°", 204.5f, 364.5f, t)

            sky.recycle(); fg.recycle()
            return bmp
        }
    }
}
