package io.github.yolanda1027.catpaw

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.CompoundButton

/** 小工具：畫面排版共用的東西 */
object Ui {
    /**
     * 三星（One UI）的預設按鈕沒有留邊，上下兩顆放在一起會黏住。
     * 這裡在每次畫面要畫之前檢查一遍：沒有設定邊距的按鈕，四周補一點空隙。
     * 已經自己設了邊距的按鈕不會動到。
     */
    fun autoSpaceButtons(a: Activity) {
        val root = a.window.decorView
        val gap = (3 * a.resources.displayMetrics.density).toInt()
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean = !spaceButtons(root, gap)
        })
    }

    /** @return 有沒有改到東西（改了就先不畫，等重新排版） */
    fun spaceButtons(v: View, gap: Int): Boolean {
        var changed = false
        if (v is Button && v !is CompoundButton) {
            val lp = v.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null && lp.leftMargin == 0 && lp.topMargin == 0 && lp.rightMargin == 0 && lp.bottomMargin == 0) {
                lp.setMargins(gap, gap, gap, gap)
                v.layoutParams = lp
                changed = true
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) changed = spaceButtons(v.getChildAt(i), gap) or changed
        return changed
    }
}
