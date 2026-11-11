package io.github.yolanda1027.catpaw.engine

import java.io.File

/**
 * 記住你選過的字詞：每選一次加一點分，下次會排得比較前面。
 * 存在手機裡的一個小檔案，不會上傳到任何地方。
 */
class UserScores(private val file: File?) {
    private val bonus = HashMap<String, Float>()

    init {
        file?.takeIf { it.exists() }?.forEachLine { line ->
            val i = line.lastIndexOf('\t')
            if (i > 0) line.substring(i + 1).toFloatOrNull()?.let { bonus[line.substring(0, i)] = it }
        }
    }

    private fun key(e: Entry) = e.phrase + "\t" + e.reading

    fun bonus(e: Entry): Float = bonus[key(e)] ?: 0f

    fun learn(e: Entry) {
        val k = key(e)
        bonus[k] = minOf((bonus[k] ?: 0f) + STEP, MAX)
        save()
    }

    fun clearAll() {
        bonus.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        try {
            f.writeText(bonus.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val STEP = 1.0f
        private const val MAX = 3.0f
    }
}
