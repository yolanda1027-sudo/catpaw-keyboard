package io.github.yolanda1027.catpaw

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 按鍵震動。提供幾種不同的「手感」讓你挑：
 * - click：清脆的一下（Android 內建的「觸覺原語」，S24 Ultra 的馬達做這個很細緻）
 * - tick：更輕、更短的一下
 * - custom：自己指定長度和強度的傳統震動
 * - system：跟系統的鍵盤震動設定走
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator =
        context.getSystemService(VibratorManager::class.java).defaultVibrator

    fun perform(view: View?, prefs: Prefs) {
        val strength = prefs.strength.coerceIn(1, 100)
        when (prefs.hapticMode) {
            "off" -> Unit
            "system" -> view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            "tick" -> primitive(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.EFFECT_TICK, strength)
            "custom" -> oneShot(prefs.vibrateMs.coerceIn(1, 100), strength)
            else -> primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.EFFECT_CLICK, strength)
        }
    }

    private fun primitive(primitiveId: Int, fallbackEffect: Int, strength: Int) {
        if (!vibrator.hasVibrator()) return
        try {
            if (vibrator.areAllPrimitivesSupported(primitiveId)) {
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(primitiveId, strength / 100f)
                        .compose()
                )
            } else {
                vibrator.vibrate(VibrationEffect.createPredefined(fallbackEffect))
            }
        } catch (_: Exception) {
            // 有些手機不支援，就安靜地不震
        }
    }

    private fun oneShot(ms: Int, strength: Int) {
        if (!vibrator.hasVibrator()) return
        val amp = if (vibrator.hasAmplitudeControl()) {
            (strength * 255 / 100).coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(ms.toLong(), amp))
        } catch (_: Exception) {
        }
    }
}
