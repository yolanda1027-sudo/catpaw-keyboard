package io.github.yolanda1027.catpaw

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主題工作室的「對齊」：按鍵上的字、圖案，背景上的圖案。
 * - 選 1 個：對齊它所在的框（這顆鍵，或鍵盤背景）
 * - 選 2 個以上：彼此對齊（對齊「選到的全部」圍起來的範圍，像簡報軟體那樣）
 * - 選 3 個以上：還可以水平、垂直等距排開（最左和最右的不動，中間的平均分開）
 * 用的是轉過角度之後「外面框起來」的範圍，所以斜斜的圖案靠左時，最左邊那個角剛好貼齊。
 */
object ElementAlign {
    enum class Op(val label: String) {
        LEFT("靠左"), HCENTER("水平置中"), RIGHT("靠右"),
        TOP("靠上"), VCENTER("垂直置中"), BOTTOM("靠下"),
        HSPACE("水平等距"), VSPACE("垂直等距"),
    }

    /** 一個元素：中心點、轉過角度之後外框的一半寬高（都用畫面上的 px） */
    data class Item(val id: String, val cx: Float, val cy: Float, val hw: Float, val hh: Float) {
        val left get() = cx - hw
        val right get() = cx + hw
        val top get() = cy - hh
        val bottom get() = cy + hh
    }

    /** 寬 w、高 h 的東西轉了 deg 度之後，外面框起來的一半寬高 */
    fun halfExtents(w: Float, h: Float, deg: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg.toDouble())
        val c = abs(cos(a)).toFloat()
        val s = abs(sin(a)).toFloat()
        return (w * c + h * s) / 2f to (w * s + h * c) / 2f
    }

    /** @return 每個元素新的中心點（沒變的也會給） */
    fun apply(items: List<Item>, op: Op, frame: RectF): Map<String, Pair<Float, Float>> {
        if (items.isEmpty()) return emptyMap()
        // 1 個：對齊框；2 個以上：對齊選到的範圍
        val b = if (items.size == 1) frame else RectF(
            items.minOf { it.left }, items.minOf { it.top }, items.maxOf { it.right }, items.maxOf { it.bottom },
        )
        val out = LinkedHashMap<String, Pair<Float, Float>>()
        when (op) {
            Op.LEFT -> items.forEach { out[it.id] = (b.left + it.hw) to it.cy }
            Op.HCENTER -> items.forEach { out[it.id] = b.centerX() to it.cy }
            Op.RIGHT -> items.forEach { out[it.id] = (b.right - it.hw) to it.cy }
            Op.TOP -> items.forEach { out[it.id] = it.cx to (b.top + it.hh) }
            Op.VCENTER -> items.forEach { out[it.id] = it.cx to b.centerY() }
            Op.BOTTOM -> items.forEach { out[it.id] = it.cx to (b.bottom - it.hh) }
            Op.HSPACE -> {
                items.forEach { out[it.id] = it.cx to it.cy }
                if (items.size >= 3) {
                    val s = items.sortedBy { it.cx }
                    val gap = (s.last().right - s.first().left - s.sumOf { (it.hw * 2).toDouble() }.toFloat()) / (s.size - 1)
                    var x = s.first().left
                    for (it in s) { out[it.id] = (x + it.hw) to it.cy; x += it.hw * 2 + gap }
                }
            }
            Op.VSPACE -> {
                items.forEach { out[it.id] = it.cx to it.cy }
                if (items.size >= 3) {
                    val s = items.sortedBy { it.cy }
                    val gap = (s.last().bottom - s.first().top - s.sumOf { (it.hh * 2).toDouble() }.toFloat()) / (s.size - 1)
                    var y = s.first().top
                    for (it in s) { out[it.id] = it.cx to (y + it.hh); y += it.hh * 2 + gap }
                }
            }
        }
        return out
    }
}
