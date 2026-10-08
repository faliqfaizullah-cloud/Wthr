package com.wthr.app

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class Weather(
    val city: String, val country: String, val temp: Int, val hi: Int, val lo: Int,
    val code: Int, val isDay: Boolean, val hours: List<String>, val hTemps: List<Int>, val hCodes: List<Int>
) { val kind: Int get() = kindOf(code, isDay) }

/** 0 Sunny, 1 Rain, 2 Clear (night), 3 Cloudy */
fun kindOf(code: Int, isDay: Boolean): Int = when (code) {
    0, 1 -> if (isDay) 0 else 2
    in 51..67, in 80..82, in 95..99 -> 1
    else -> 3
}
fun glyph(kind: Int) = arrayOf("☀", "☂", "☾", "☁")[kind]
private fun fmtHour(h: Int) = "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}"

object WeatherRepo {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    fun cached(c: Context): Weather? = try {
        c.getSharedPreferences("wthr", 0).getString("weather", null)?.let { parse(JSONObject(it)) }
    } catch (e: Exception) { null }

    /** Blocking: country/city from the user's network location, then live weather from Open-Meteo. Call off the main thread. */
    fun fetch(c: Context): Weather? = try {
        val loc = JSONObject(get("https://ipwho.is/"))
        val lat = loc.getDouble("latitude"); val lon = loc.getDouble("longitude")
        val w = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto"))
        val cur = w.getJSONObject("current")
        val h = cur.getString("time").substring(11, 13).toInt()
        val hr = w.getJSONObject("hourly")
        val times = hr.getJSONArray("time"); val temps = hr.getJSONArray("temperature_2m"); val codes = hr.getJSONArray("weather_code")
        val hours = ArrayList<String>(); val hT = ArrayList<Int>(); val hC = ArrayList<Int>()
        for (i in 1..9) {
            val idx = (h + i).coerceAtMost(times.length() - 1)
            hours.add(fmtHour(times.getString(idx).substring(11, 13).toInt()))
            hT.add(Math.round(temps.getDouble(idx)).toInt()); hC.add(codes.getInt(idx))
        }
        val d = w.getJSONObject("daily")
        val res = Weather(
            loc.optString("city", ""), loc.optString("country", ""),
            Math.round(cur.getDouble("temperature_2m")).toInt(),
            Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            cur.getInt("weather_code"), cur.getInt("is_day") == 1, hours, hT, hC
        )
        c.getSharedPreferences("wthr", 0).edit().putString("weather", toJson(res).toString()).apply()
        res
    } catch (e: Exception) { null }

    private fun toJson(w: Weather) = JSONObject().put("city", w.city).put("country", w.country).put("temp", w.temp)
        .put("hi", w.hi).put("lo", w.lo).put("code", w.code).put("isDay", w.isDay)
        .put("hours", JSONArray(w.hours)).put("hT", JSONArray(w.hTemps)).put("hC", JSONArray(w.hCodes))

    private fun parse(o: JSONObject): Weather {
        fun strs(k: String) = o.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }
        fun ints(k: String) = o.getJSONArray(k).let { a -> List(a.length()) { a.getInt(it) } }
        return Weather(o.getString("city"), o.getString("country"), o.getInt("temp"), o.getInt("hi"), o.getInt("lo"),
            o.getInt("code"), o.getBoolean("isDay"), strs("hours"), ints("hT"), ints("hC"))
    }
}

/** Runs in the background (even when the app is closed) and refreshes the weather + widget. */
class WeatherWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val w = withContext(Dispatchers.IO) { WeatherRepo.fetch(applicationContext) }
        WthrWidget.updateAll(applicationContext)
        return if (w != null) Result.success() else Result.retry()
    }

    companion object {
        private fun net() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun schedule(c: Context) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("wthr_periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES).setConstraints(net()).build())
        }
        fun now(c: Context) {
            WorkManager.getInstance(c).enqueueUniqueWork("wthr_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(net()).build())
        }
    }
}
