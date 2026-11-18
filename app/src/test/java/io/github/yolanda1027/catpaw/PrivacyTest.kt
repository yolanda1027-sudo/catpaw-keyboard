package io.github.yolanda1027.catpaw

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.yolanda1027.catpaw.engine.Entry
import io.github.yolanda1027.catpaw.engine.UserScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 隱私權政策寫到的事：無痕模式不學字、剪貼簿紀錄不備份 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PrivacyTest {
    private val ctx = RuntimeEnvironment.getApplication()

    @Test fun pausedScoresLearnNothing() {
        val f = File.createTempFile("scores", ".txt").apply { delete() }
        val u = UserScores(f)
        u.paused = true
        assertFalse(u.addPhrase("妳好", "ㄋㄧˇ ㄏㄠˇ"))
        u.pick(Entry("妳", "ㄋㄧˇ", 1f), 2f)
        assertFalse("暫停時不寫檔", f.exists())
        u.paused = false
        assertTrue(u.addPhrase("妳好", "ㄋㄧˇ ㄏㄠˇ"))
        assertTrue(f.exists())
    }

    @Test fun incognitoAndPasswordFieldsDontLearn() {
        val s = Robolectric.buildService(CatImeService::class.java).create().get()
        s.onCreateInputView()
        fun start(type: Int, options: Int = 0) = s.onStartInputView(EditorInfo().apply { inputType = type; imeOptions = options }, false)
        start(InputType.TYPE_CLASS_TEXT)
        assertFalse(s.noLearning)
        start(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertTrue("無痕模式", s.noLearning)
        start(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertTrue("密碼欄", s.noLearning)
        start(InputType.TYPE_CLASS_TEXT)
        assertFalse(s.noLearning)
    }

    @Test fun clipboardHistoryIsExcludedFromBackup() {
        val xml = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertEquals(2, Regex("""<exclude domain="file" path="clips.json" />""").findAll(xml).count())
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("@xml/data_extraction_rules"))
        // 剪貼簿檔名要跟程式裡用的一樣（改檔名時這裡會提醒）
        assertTrue(File("src/main/java/io/github/yolanda1027/catpaw/CatImeService.kt").readText().contains("File(filesDir, \"clips.json\")"))
    }

    /** 隱私權政策說「沒有網路權限」：以後誰加了網路權限，這裡會擋下來提醒要先改政策 */
    @Test fun noInternetPermission() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val perms = Regex("""uses-permission android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.VIBRATE"), perms)
        val pm = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertFalse((pm.requestedPermissions ?: emptyArray()).contains("android.permission.INTERNET"))
    }

    @Test fun privacyPageAndPolicyFile() {
        assertTrue(File("../docs/privacy-policy.md").readText().contains("沒有網路權限"))
        assertTrue(SettingsActivity.PRIVACY_URL.endsWith("/docs/privacy-policy.md"))
        val a = Robolectric.buildActivity(SettingsActivity::class.java, android.content.Intent().putExtra("page", "privacy")).setup().get()
        val texts = ArrayList<String>()
        fun walk(v: android.view.View) {
            if (v is android.widget.TextView) texts += v.text.toString()
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(a.window.decorView)
        assertTrue(texts.any { it.contains("沒有網路權限") })
        assertTrue(texts.any { it.contains("完整的隱私權政策") })
        // 截圖
        val root = a.window.decorView
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        File("build/renders").mkdirs()
        File("build/renders/privacy-page.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
