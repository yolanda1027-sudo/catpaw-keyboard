package io.github.yolanda1027.catpaw

import android.view.View
import android.view.ViewGroup

/*
 * 測試共用的小工具（以前每個測試檔各寫一份）。
 */

/** 這個畫面裡所有的 View（自己＋裡面每一層） */
fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
    out += v
    if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
    return out
}

/** 找第一個符合條件的 View（找不到是 null） */
fun find(v: View, pred: (View) -> Boolean): View? {
    if (pred(v)) return v
    if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
    return null
}

/** 鍵盤的 Listener：全部什麼都不做，測試只覆寫要看的那幾個 */
open class TestListener : CatKeyboardView.Listener {
    override fun onKey(key: Key, longPress: Boolean) {}
    override fun onTabChinese() {}
    override fun onTabEnglish() {}
    override fun onHide() {}
    override fun onCandidate(index: Int) {}
    override fun onSymbolTab(index: Int) {}
    override fun onSymbolSwipe(direction: Int) {}
    override fun onClipboard() {}
    override fun onQuick(item: String) {}
}
