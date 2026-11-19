package io.github.yolanda1027.catpaw

import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.inputmethodservice.InputMethodService
import io.github.yolanda1027.catpaw.engine.EnglishDict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** 英文模式打字：選字列出現建議字，點了換上去、加空白；密碼欄、注音模式不出現 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class EnglishSuggestTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private lateinit var s: CatImeService
    private lateinit var kv: CatKeyboardView
    private lateinit var ic: BaseInputConnection

    private fun f(n: String) = CatImeService::class.java.getDeclaredField(n).apply { isAccessible = true }

    @Before fun setUp() {
        File(ctx.filesDir, CatImeService.EN_USER_FILE).delete()
        Prefs(ctx).chinese = false
        s = Robolectric.buildService(CatImeService::class.java).create().get()
        kv = s.onCreateInputView() as CatKeyboardView
        // 等英文字典在背景載好
        val end = System.currentTimeMillis() + 20_000
        while (f("enDict").get(s) == null && System.currentTimeMillis() < end) {
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }
        assertTrue("英文字典載好了", f("enDict").get(s) != null)
        start(InputType.TYPE_CLASS_TEXT)
    }

    /** 換一個新的輸入框（裡面的字是空的） */
    private fun start(type: Int) {
        ic = BaseInputConnection(View(ctx), true)
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply { isAccessible = true }.set(s, ic)
        s.onStartInputView(EditorInfo().apply { inputType = type }, false)
    }

    private fun text() = ic.editable.toString()

    /** 畫成 PNG 看長相（app/build/renders/） */
    private fun render(name: String) {
        kv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        val bmp = android.graphics.Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        kv.draw(android.graphics.Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun type(str: String) = str.forEach { c ->
        s.onKey(if (c == ' ') Key("English", Act.SPACE, text = " ") else Key(c.toString()), false)
    }

    @Test fun suggestsAndPicks() {
        s.onTabEnglish()
        type("hel")
        assertEquals("hel", text())
        val list = kv.candidates
        assertTrue(list.toString(), "hello" in list)
        render("english-suggest")
        s.onCandidate(list.indexOf("hello"))
        assertEquals("換掉打到一半的字、後面加空白", "hello ", text())
        assertTrue("點完就收起來", kv.candidates.isEmpty())
        type(",")
        assertEquals("逗號貼著字、空白移到後面", "hello, ", text())
    }

    @Test fun typoAndCase() {
        s.onTabEnglish()
        type("Teh")
        assertEquals("The", kv.candidates.first())
        s.onCandidate(0)
        assertEquals("The ", text())
    }

    @Test fun spaceKeepsWhatYouTyped() {
        s.onTabEnglish()
        type("helo ")
        assertEquals("只建議、不自動改", "helo ", text())
        assertTrue(kv.candidates.isEmpty())
    }

    @Test fun learnsWordsTypedTwice() {
        s.onTabEnglish()
        type("catpawz catpawz ")
        type("catp")
        assertTrue(kv.candidates.toString(), "catpawz" in kv.candidates)
    }

    @Test fun noSuggestionsInPasswordOrChinese() {
        start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        s.onTabEnglish()
        type("hel")
        assertTrue("密碼欄", kv.candidates.isEmpty())
        start(InputType.TYPE_CLASS_TEXT)
        s.onTabChinese()
        s.onTabEnglish()
        Prefs(ctx).enSuggest = false
        type("hel")
        assertTrue("設定關掉", kv.candidates.isEmpty())
        Prefs(ctx).enSuggest = true
    }

    @Test fun clearLearningForgetsEnglish() {
        s.onTabEnglish()
        type("zorbly zorbly ")
        s.onFinishInputView(true)
        assertTrue(File(ctx.filesDir, CatImeService.EN_USER_FILE).exists())
        File(ctx.filesDir, CatImeService.EN_USER_FILE).delete()
        Prefs(ctx).sp.edit().putLong("clear_learning", System.currentTimeMillis()).commit()
        start(InputType.TYPE_CLASS_TEXT)
        s.onTabEnglish()
        type("zorb")
        assertFalse(kv.candidates.toString(), "zorbly" in kv.candidates)
        assertTrue(EnglishDict.currentWord(text()) == "zorb")
    }
}
