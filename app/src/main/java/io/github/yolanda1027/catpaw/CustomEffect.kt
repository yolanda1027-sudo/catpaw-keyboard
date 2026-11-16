package io.github.yolanda1027.catpaw

import org.json.JSONObject

/**
 * 自己調的按鍵動畫（主題工作室）。
 *
 * @param motion rise 往上飄｜burst 往外散開｜fall 往下落｜fountain 噴泉｜pop 原地閃一下
 * @param particle 飛出來的東西：「shape:heart」內建圖案，或「file:decal_xxx.png」自己的圖
 * @param rainbow true = 每個粒子不同顏色（彩虹）
 * @param size 粒子大小（dp，半徑）
 * @param speed 速度倍率（越大越快、也消失得越快）
 */
data class CustomEffect(
    val motion: String = "rise",
    val particle: String = "shape:heart",
    val color: Int = 0xFFFF7EAA.toInt(),
    val rainbow: Boolean = false,
    val count: Int = 5,
    val size: Float = 9f,
    val speed: Float = 1f,
    val spin: Boolean = false,
    val bounce: Boolean = false,
) {
    val isImage get() = particle.startsWith(Decal.FILE)
    val fileName get() = particle.removePrefix(Decal.FILE)
    val shapeName get() = particle.removePrefix(Decal.SHAPE)

    fun toJson(): JSONObject = JSONObject()
        .put("motion", motion).put("particle", particle)
        .put("color", String.format("#%08X", color)).put("rainbow", rainbow)
        .put("count", count).put("size", size.toDouble()).put("speed", speed.toDouble())
        .put("spin", spin).put("bounce", bounce)

    companion object {
        val MOTIONS = listOf(
            "rise" to "往上飄",
            "burst" to "往外散開",
            "fall" to "往下落",
            "fountain" to "噴泉",
            "pop" to "原地閃一下",
        )

        fun fromJson(o: JSONObject?): CustomEffect? {
            if (o == null) return null
            val particle = o.optString("particle", "shape:heart").let {
                when {
                    it.startsWith(Decal.SHAPE) -> it
                    it.startsWith(Decal.FILE) && Decals.safeName(it.removePrefix(Decal.FILE)) -> it
                    else -> "shape:heart"
                }
            }
            return CustomEffect(
                motion = o.optString("motion", "rise").takeIf { m -> MOTIONS.any { it.first == m } } ?: "rise",
                particle = particle,
                color = try { android.graphics.Color.parseColor(o.optString("color", "#FFFF7EAA")) } catch (_: Exception) { 0xFFFF7EAA.toInt() },
                rainbow = o.optBoolean("rainbow", false),
                count = o.optInt("count", 5).coerceIn(1, 15),
                size = o.optDouble("size", 9.0).toFloat().coerceIn(4f, 40f),
                speed = o.optDouble("speed", 1.0).toFloat().coerceIn(0.4f, 2.5f),
                spin = o.optBoolean("spin", false),
                bounce = o.optBoolean("bounce", false),
            )
        }
    }
}

/**
 * 某顆鍵（或一整類鍵）的按鍵動畫：
 * kind = 內建動畫的代號（hearts、spell…）、"none"（不要動畫）、"custom"（用 custom 的設定）
 */
data class KeyEffect(val kind: String, val custom: CustomEffect? = null) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind).apply { custom?.let { put("custom", it.toJson()) } }

    companion object {
        fun fromJson(o: JSONObject?): KeyEffect? {
            if (o == null) return null
            val kind = o.optString("kind")
            if (kind != "custom" && PressEffects.ALL.none { it.first == kind }) return null
            val c = CustomEffect.fromJson(o.optJSONObject("custom"))
            return KeyEffect(kind, if (kind == "custom") c ?: CustomEffect() else null)
        }
    }
}
