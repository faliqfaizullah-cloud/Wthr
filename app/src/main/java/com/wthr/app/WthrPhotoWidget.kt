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

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { WeatherWorker.schedule(c); Ticker.schedule(c); ids.forEach { render(c, m, it) } }

    override fun onEnabled(c: Context) { WeatherWorker.schedule(c); WeatherWorker.now(c); Ticker.schedule(c) }

    override fun onDisabled(c: Context) { Ticker.cancel(c) }

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
            val spec = CardShape.size(c, m, id)
            val w = WeatherRepo.cached(c)
            val rv = RemoteViews(c.packageName, R.layout.widget_photo)
            rv.setImageViewBitmap(R.id.widget_img, draw(c, spec, w))
            val cond = if (w == null) 5 else condOf(w.code, w.isDay)
            val frames = Fx.frames(spec, cond, w?.code ?: 3, w?.isDay ?: true)
            val ids = intArrayOf(R.id.anim_0, R.id.anim_1, R.id.anim_2, R.id.anim_3, R.id.anim_4, R.id.anim_5, R.id.anim_6, R.id.anim_7)
            frames.forEachIndexed { i, bm -> rv.setImageViewBitmap(ids[i], bm) }
            val pi = PendingIntent.getBroadcast(c, 2, Intent(c, WthrPhotoWidget::class.java).setAction(WthrWidget.ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            m.updateAppWidget(id, rv)
        }

        private fun load(c: Context, id: Int): Bitmap {
            val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            return BitmapFactory.decodeResource(c.resources, id, o)
        }

        // Everything is laid out on the 409x409 grid of the reference artwork, then scaled to the widget.
        private fun draw(c: Context, spec: CardShape.Spec, w: Weather?): Bitmap {
            val day = w?.isDay ?: true
            val cond = if (w == null) 5 else condOf(w.code, w.isDay)
            val scene = Skyline.sceneNow()          // 0 = Moscow photo, 1..8 = skylines (changes every 5 minutes)
            val bmp = Bitmap.createBitmap(spec.w, spec.h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            cv.clipPath(CardShape.path(spec))
            CardShape.fit(cv, spec, 409f, 0.5f)
            val fp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; typeface = c.resources.getFont(R.font.inter_display_bold); textSize = 252f; textAlign = Paint.Align.CENTER }
            val num = w?.temp?.toString() ?: "--"
            val nw = big.measureText(num)
            if (nw > 307f) big.textSize = 252f * 307f / nw

            if (scene == 0) {
                val sky = load(c, if (day) R.drawable.w3_sky_day else R.drawable.w3_sky_night)
                val fg = load(c, if (day) R.drawable.w3_fg_day else R.drawable.w3_fg_night)
                cv.drawBitmap(sky, 0f, 0f, fp)
                cv.drawText(num, 208f, 282f, big)
                cv.drawBitmap(fg, 0f, 0f, fp)
                sky.recycle(); fg.recycle()
            } else {
                Skyline.sky(cv, day, cond)
                Skyline.far(cv, day, cond, scene)
                big.setShadowLayer(10f, 0f, 4f, argb(0x55000000L))
                cv.drawText(num, 208f, 282f, big)
                big.clearShadowLayer()
                Skyline.landmark(cv, scene, day, cond)
                Skyline.foreground(cv, day, cond)
            }

            // 3) city and high / low
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = c.resources.getFont(R.font.inter_semibold); textAlign = Paint.Align.CENTER }
            val city = (w?.city ?: "").ifBlank { w?.country ?: "" }.ifBlank { "Wthr" }
            t.textSize = 29.5f
            while (t.measureText(city) > minOf(280f, 409f * spec.w / spec.h - 50f) && t.textSize > 14f) t.textSize -= 0.5f
            cv.drawText(city, 204.5f, 53.5f, t)
            t.textSize = 30f
            cv.drawText(if (w == null) "H: --°  L: --°" else "H: ${w.hi}°  L: ${w.lo}°", 204.5f, 364.5f, t)

            return bmp
        }
    }
}
