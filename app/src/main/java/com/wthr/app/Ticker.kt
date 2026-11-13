package com.wthr.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wakes the photo widget every 5 minutes (on the 5-minute clock mark) so the skyline scene changes. */
object Ticker {
    private fun pi(c: Context) = PendingIntent.getBroadcast(c, 9, Intent(c, TickReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val next = (System.currentTimeMillis() / 300_000L + 1) * 300_000L + 500L
        try { am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi(c)) } catch (e: Exception) {}
    }

    fun cancel(c: Context) { try { c.getSystemService(AlarmManager::class.java)?.cancel(pi(c)) } catch (e: Exception) {} }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Ticker.schedule(c); WthrPhotoWidget.updateAll(c) }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { WeatherWorker.schedule(c); Ticker.schedule(c); WthrPhotoWidget.updateAll(c) }
}
