package io.github.yolanda1027.catpaw

import android.graphics.Typeface
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 上傳字體：用內建的粉圓檔案假裝成使用者從手機選的檔案 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FontsTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun fileUri(name: String, bytes: ByteArray): Uri {
        val f = File(ctx.cacheDir, name)
        f.writeBytes(bytes)
        return Uri.fromFile(f)
    }

    @Test fun importValidFontAddsItToTheList() {
        val bytes = File("src/main/assets/fonts/jf-openhuninn.ttf").readBytes()
        val r = Fonts.import(ctx, fileUri("我的可愛字體.ttf", bytes))
        assertNotNull(r.message, r.id)
        assertTrue(r.message, r.message.contains("有注音也有中文"))
        assertTrue(Fonts.options(ctx).any { it.first == r.id && it.second == "我的字體：我的可愛字體" })
        assertTrue(Fonts.get(ctx, r.id!!) !== Typeface.DEFAULT)
        Fonts.delete(ctx, r.id!!)
        assertTrue(Fonts.options(ctx).none { it.first == r.id })
        assertEquals(Typeface.DEFAULT, Fonts.get(ctx, r.id!!))
    }

    @Test fun rejectsNonFontFiles() {
        val r = Fonts.import(ctx, fileUri("not-a-font.ttf", "hello".toByteArray()))
        assertNull(r.id)
        val r2 = Fonts.import(ctx, fileUri("photo.jpg", ByteArray(10)))
        assertNull(r2.id)
    }
}
