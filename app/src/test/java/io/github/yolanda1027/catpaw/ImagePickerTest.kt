package io.github.yolanda1027.catpaw

import android.content.Intent
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 選圖片用相片挑選器（按一次返回就關掉），不是檔案選擇器 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ImagePickerTest {

    @Test fun intents() {
        assertTrue(ImagePicker.available())
        val one = ImagePicker.intent()
        assertEquals(MediaStore.ACTION_PICK_IMAGES, one.action)
        assertEquals("image/*", one.type)
        val many = ImagePicker.intent(multiple = true)
        assertTrue(many.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 0) > 1)
    }

    @Test fun settingsButtonsUsePhotoPicker() {
        // 功能鍵貼圖「選圖片」
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "stickers")).setup().get()
        (views(a.window.decorView).first { it is Button && it.text == "選圖片" } as Button).performClick()
        assertEquals(MediaStore.ACTION_PICK_IMAGES, shadowOf(a).nextStartedActivity.action)
        // 我的貼圖：一次選好幾張
        val b = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "mystickers")).setup().get()
        (views(b.window.decorView).first { it is Button && it.text.startsWith("＋ 上傳圖片") } as Button).performClick()
        val next = shadowOf(b).nextStartedActivity
        assertEquals(MediaStore.ACTION_PICK_IMAGES, next.action)
        assertTrue(next.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 0) > 1)
        // 字體、主題檔不是圖片：還是用檔案選擇器
        val c = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("page", "font")).setup().get()
        (views(c.window.decorView).first { it is Button && it.text == "上傳字體檔" } as Button).performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, shadowOf(c).nextStartedActivity.action)
    }

    @Test fun eyedropperUsesPhotoPicker() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        Eyedropper.pickImage(a) {}
        assertEquals(MediaStore.ACTION_PICK_IMAGES, shadowOf(a).nextStartedActivity.action)
    }
}
