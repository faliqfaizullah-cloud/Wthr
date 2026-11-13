package com.wthr.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF

/**
 * Widget card shape measured from the reference picture:
 * a rounded rectangle 584 x 645 (slightly taller than wide) with corner radius = 19.63% of the width
 * (= 28dp on a standard 2x2 widget, about 143dp wide).
 */
object CardShape {
    const val RW = 584f
    const val RH = 645f
    const val RADIUS = 0.1963f

    class Spec(val w: Int, val h: Int, val r: Float)

    /** Largest card with the reference proportions that fits the widget frame, in pixels. */
    fun size(c: Context, m: AppWidgetManager, id: Int): Spec {
        val o = m.getAppWidgetOptions(id)
        val land = c.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        var wDp = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)).toFloat()
        var hDp = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)).toFloat()
        if (wDp < 40f) wDp = 146f
        if (hDp < 40f) hDp = 161f
        val k = minOf(wDp / RW, hDp / RH)
        val d = c.resources.displayMetrics.density
        var w = RW * k * d
        var h = RH * k * d
        val cap = 560f / maxOf(w, h)
        if (cap < 1f) { w *= cap; h *= cap }
        val wi = w.toInt().coerceAtLeast(60)
        return Spec(wi, h.toInt().coerceAtLeast(66), RADIUS * wi)
    }

    fun path(s: Spec) = Path().apply { addRoundRect(RectF(0f, 0f, s.w.toFloat(), s.h.toFloat()), s.r, s.r, Path.Direction.CW) }

    /**
     * A square design of side `design` is scaled to the card HEIGHT and cropped sideways.
     * anchor 0 = keep the design's left side, 0.5 = centre, 1 = keep its right side.
     */
    fun fit(cv: Canvas, s: Spec, design: Float, anchor: Float) {
        val sc = s.h / design
        cv.translate((s.w - s.h) * anchor, 0f)
        cv.scale(sc, sc)
    }
}
