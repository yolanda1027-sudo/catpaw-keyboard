package io.github.yolanda1027.catpaw

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

/**
 * 輸入法本體。
 *
 * 第一階段：注音鍵會把符號放進「組字區」（底線那段），
 * 按空白或 Enter 送出。下一階段會在這裡接上注音引擎做選字。
 */
class CatImeService : InputMethodService(), CatKeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private enum class Page { MAIN, SYMBOL, EMOJI }

    private lateinit var prefs: Prefs
    private lateinit var haptics: Haptics
    private var kv: CatKeyboardView? = null

    private var chinese = true
    private var page = Page.MAIN
    private var shift = 0 // 0 = 小寫, 1 = 大寫一次, 2 = 大寫鎖定
    private var lastShiftTap = 0L
    private val composing = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.migrate()
        haptics = Haptics(this)
        chinese = prefs.chinese
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key != "chinese") kv?.reloadPrefs()
    }

    override fun onCreateInputView(): View {
        val v = CatKeyboardView(this, prefs, haptics)
        v.listener = this
        kv = v
        refresh()
        return v
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composing.clear()
        shift = 0
        page = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.SYMBOL
            else -> Page.MAIN
        }
        chinese = prefs.chinese && !isPassword(info)
        kv?.reloadPrefs()
        refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.finishComposingText()
        composing.clear()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // 使用者自己把游標點去別的地方 → 放棄組字
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composing.clear()
            currentInputConnection?.finishComposingText()
            refresh()
        }
    }

    private fun isPassword(info: EditorInfo): Boolean {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }

    private fun refresh() {
        val v = kv ?: return
        v.rows = when (page) {
            Page.MAIN -> if (chinese) Layouts.ZHUYIN else Layouts.ENGLISH
            Page.SYMBOL -> Layouts.SYMBOLS
            Page.EMOJI -> Layouts.EMOJI
        }
        v.isChinese = chinese
        v.shiftState = shift
        v.composing = composing.toString()
    }

    // ---------- 按鍵處理 ----------

    override fun onKey(key: Key, longPress: Boolean) {
        val ic = currentInputConnection ?: return
        if (longPress) {
            when (key.act) {
                Act.SPACE -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                else -> key.sub?.let { commitText(it) }
            }
            return
        }
        when (key.act) {
            Act.ZHUYIN -> {
                composing.append(key.text)
                ic.setComposingText(composing, 1)
                refresh()
            }
            Act.TEXT -> {
                val upper = !chinese && page == Page.MAIN && shift > 0
                commitText(if (upper) key.text.uppercase() else key.text)
                if (shift == 1 && page == Page.MAIN) {
                    shift = 0
                    refresh()
                }
            }
            Act.SPACE -> if (composing.isNotEmpty()) commitComposing() else commitText(" ")
            Act.ENTER -> if (composing.isNotEmpty()) commitComposing() else sendKeyChar('\n')
            Act.BACKSPACE -> {
                if (composing.isNotEmpty()) {
                    composing.deleteCharAt(composing.length - 1)
                    ic.setComposingText(composing, 1)
                    if (composing.isEmpty()) ic.finishComposingText()
                    refresh()
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            }
            Act.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when (shift) {
                    0 -> 1
                    1 -> if (now - lastShiftTap < 400) 2 else 0
                    else -> 0
                }
                lastShiftTap = now
                refresh()
            }
            Act.TO_SYMBOL -> { page = Page.SYMBOL; refresh() }
            Act.TO_EMOJI -> { page = Page.EMOJI; refresh() }
            Act.TO_MAIN -> { page = Page.MAIN; refresh() }
            Act.GAP -> Unit
        }
    }

    private fun commitText(t: String) {
        if (composing.isNotEmpty()) commitComposing()
        currentInputConnection?.commitText(t, 1)
    }

    private fun commitComposing() {
        currentInputConnection?.commitText(composing, 1)
        composing.clear()
        refresh()
    }

    override fun onTabChinese() {
        if (!chinese || page != Page.MAIN) {
            chinese = true
            prefs.chinese = true
            page = Page.MAIN
            refresh()
        }
    }

    override fun onTabEnglish() {
        if (chinese || page != Page.MAIN) {
            if (composing.isNotEmpty()) commitComposing()
            chinese = false
            prefs.chinese = false
            page = Page.MAIN
            shift = 0
            refresh()
        }
    }

    override fun onHide() {
        requestHideSelf(0)
    }
}
