package io.github.yolanda1027.catpaw

import android.content.Intent
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore

/**
 * 選圖片：用 Android 的「相片挑選器」（從下面拉上來的面板，按一次返回或往下滑就關掉；
 * 不會像檔案選擇器那樣誤點進資料夾後要一直按返回）。
 * 舊手機沒有相片挑選器的話，才用檔案選擇器。
 */
object ImagePicker {
    /** 這支手機有沒有相片挑選器（Android 13 以後都有；11、12 要有系統更新） */
    fun available(): Boolean =
        Build.VERSION.SDK_INT >= 33 ||
            (Build.VERSION.SDK_INT >= 30 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2)

    /** 選圖片的 Intent；multiple = 可以一次選好幾張 */
    fun intent(multiple: Boolean = false): Intent {
        if (available()) {
            return Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                if (multiple) putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, minOf(MAX_MULTIPLE, MediaStore.getPickImagesMaxLimit()))
            }
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    private const val MAX_MULTIPLE = 30
}
