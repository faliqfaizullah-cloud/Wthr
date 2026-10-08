package com.wthr.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.random.Random

object Haptics {
    private var v: Vibrator? = null

    fun init(c: Context) {
        v = if (Build.VERSION.SDK_INT >= 31)
            c.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") c.getSystemService(Vibrator::class.java)
    }

    private fun play(e: VibrationEffect) { try { v?.vibrate(e) } catch (e: Exception) {} }
    fun cancel() { try { v?.cancel() } catch (e: Exception) {} }

    /** Light tap: selections, arrows, page change */
    fun tick() = play(
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else VibrationEffect.createOneShot(12, 80)
    )

    /** Firm tap: button presses */
    fun click() = play(
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        else VibrationEffect.createOneShot(25, 160)
    )

    /** One bounce impact. strength 0..1 follows the spring's amplitude, so each bounce is softer. */
    fun bounce(strength: Float) {
        val s = strength.coerceIn(0.08f, 1f)
        val amp = (40 + 215 * s).toInt().coerceIn(1, 255)
        val ms = (8 + 22 * s).toLong()
        play(
            if (v?.hasAmplitudeControl() == true) VibrationEffect.createOneShot(ms, amp)
            else VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /** A single raindrop: very short, random intensity. */
    fun drop() {
        val amp = Random.nextInt(18, 75)
        val ms = Random.nextLong(5, 11)
        play(
            if (v?.hasAmplitudeControl() == true) VibrationEffect.createOneShot(ms, amp)
            else VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /** Thunder rumble for heavy rain bursts. */
    fun rumble() = play(
        if (v?.hasAmplitudeControl() == true)
            VibrationEffect.createWaveform(longArrayOf(0, 40, 30, 60, 30, 90, 40, 50), intArrayOf(0, 90, 0, 140, 0, 190, 0, 60), -1)
        else VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}
