package io.github.yolanda1027.catpaw

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * 貓爪主題工作室：在鍵盤預覽上直接點按鍵來改樣子。
 * 改了就馬上存起來，鍵盤也會跟著變（不用另外按套用）。
 */
class StudioActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var kv: CatKeyboardView
    private lateinit var panel: LinearLayout
    private lateinit var tabZhuyin: Button
    private lateinit var tabEnglish: Button
    /** 放大的單顆按鍵（蓋在鍵盤上面） */
    private lateinit var zoom: KeyZoomView
    private lateinit var zoomBox: android.widget.FrameLayout

    private var styles = KeyStyles.EMPTY
    private var selected: Key? = null
    /** true = 改「同一類的全部按鍵」，false = 只改選到的這顆 */
    private var groupScope = false
    private var english = false

    private var decals: List<Decal> = emptyList()
    /** 正在編輯鍵盤背景（沒選按鍵） */
    private var bgSelected = false
    /** 選到的圖案 */
    private var decalSel: String? = null
    /** 從相簿選圖片時，要貼到哪 */
    private var pendingTarget: String? = null

    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.autoSpaceButtons(this)
        prefs = Prefs(this)
        styles = KeyStyles.load(prefs)
        decals = Decals.load(prefs)
        english = savedInstanceState?.getBoolean("english") ?: false
        actionBar?.title = "貓爪主題工作室"

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 要編輯哪一種鍵盤
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(12f), px(4f), px(12f), px(4f))
        }
        tabZhuyin = tabButton("注音鍵盤") { switchLayout(false) }
        tabEnglish = tabButton("英文鍵盤") { switchLayout(true) }
        tabs.addView(tabZhuyin, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(tabEnglish, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(tabs)

        kv = CatKeyboardView(this, prefs, Haptics(this)).apply {
            quickItems = emptyList()
            editPicker = { key ->
                if (selected?.let { KeyStyles.idOf(it) } != KeyStyles.idOf(key)) groupScope = false
                selected = key
                bgSelected = false
                decalSel = null
                refresh()
            }
            onDecalPicked = { d -> selectDecal(d) }
            onDecalChanged = { d, done ->
                decals = decals.map { if (it.id == d.id) d else it }
                if (done) {
                    Decals.save(prefs, decals)
                    refresh()
                }
            }
        }
        // 鍵盤和「放大的按鍵」疊在一起：選到單顆鍵時，放大的那個蓋在鍵盤上
        val stage = android.widget.FrameLayout(this)
        stage.addView(kv, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT))
        zoom = KeyZoomView(this, kv).apply {
            decals = { this@StudioActivity.decals }
            onPick = { d ->
                decalSel = d?.id
                panel.post { refresh() }
            }
            onChange = { d, done ->
                this@StudioActivity.decals = this@StudioActivity.decals.map { if (it.id == d.id) d else it }
                kv.setDecalsLive(this@StudioActivity.decals)
                if (done) {
                    Decals.save(prefs, this@StudioActivity.decals)
                    panel.post { refresh() }
                }
            }
        }
        zoomBox = android.widget.FrameLayout(this).apply {
            addView(zoom, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
            fun corner(label: String, gravity: Int, onClick: () -> Unit) = addView(Button(this@StudioActivity).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                alpha = 0.92f
                setOnClickListener { onClick() }
            }, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, gravity).apply {
                setMargins(px(6f), px(6f), px(6f), px(26f))
            })
            corner("← 整個鍵盤", Gravity.TOP or Gravity.START) {
                selected = null
                decalSel = null
                refresh()
            }
            corner("＋ 加圖案", Gravity.BOTTOM or Gravity.END) {
                selected?.let { showAddDialog(KeyStyles.idOf(it)) }
            }
            visibility = View.GONE
        }
        stage.addView(zoomBox, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        outer.addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(8f), px(20f), px(48f))
        }
        outer.addView(ScrollView(this).apply { addView(panel) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(outer)
        switchLayout(english)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("english", english)
    }

    private fun tabButton(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun switchLayout(en: Boolean) {
        english = en
        kv.rows = if (en) Layouts.ENGLISH else Layouts.ZHUYIN
        kv.isChinese = !en
        tabZhuyin.alpha = if (en) 0.5f else 1f
        tabEnglish.alpha = if (en) 1f else 0.5f
        selected = null
        groupScope = false
        bgSelected = false
        decalSel = null
        refresh()
    }

    /** 目前在改的是哪個代號（單顆鍵或一整類） */
    private fun currentId(): String? {
        val key = selected ?: return null
        return if (groupScope) KeyStyles.groupOf(key).id else KeyStyles.idOf(key)
    }

    private fun update(change: (KeyStyle) -> KeyStyle?, rebuild: Boolean = true) {
        val id = currentId() ?: return
        styles = styles.with(id, change(styles.map[id] ?: KeyStyle()))
        KeyStyles.save(prefs, styles)
        kv.reloadPrefs()
        if (rebuild) refresh()
    }

    // ---------- 下面的編輯面板 ----------

    private fun refresh() {
        panel.removeAllViews()
        // 只改一顆鍵（或那顆鍵上的圖案）時，顯示放大的按鍵
        val zoomKey = selected?.takeIf { !bgSelected && !groupScope }
        zoomBox.visibility = if (zoomKey != null) View.VISIBLE else View.GONE
        zoom.key = zoomKey
        zoom.selectedId = decalSel
        kv.decalEditId = decalSel
        decals.firstOrNull { it.id == decalSel }?.let { decalPanel(it); return }
        decalSel = null
        if (bgSelected) { bgPanel(); return }
        val key = selected
        if (key == null) {
            kv.editHighlight = null
            text("👆 點一下上面鍵盤的任何一顆按鍵，就可以開始改它的樣子", 16f).setPadding(0, px(12f), 0, px(8f))
            text("改好會直接套用到鍵盤上。想換整體主題、字體，到「貓爪鍵盤」設定的外觀頁。", 13f, dim = true)
            button("🖼️ 在鍵盤背景貼圖案") { bgSelected = true; refresh() }
            if (styles.map.isNotEmpty()) {
                text("目前有 ${styles.map.size} 組按鍵設定", 14f).setPadding(0, px(20f), 0, 0)
                clearAllButton()
            }
            if (decals.isNotEmpty()) clearDecalsButton()
            return
        }
        val group = KeyStyles.groupOf(key)
        val selId = KeyStyles.idOf(key)
        kv.editHighlight = if (groupScope) { k -> KeyStyles.groupOf(k) == group } else { k -> KeyStyles.idOf(k) == selId }

        text("正在編輯：" + KeyStyles.nameOf(key), 18f, bold = true).setPadding(0, px(10f), 0, px(4f))

        // 空白鍵、Enter 本來就只有一顆，不用選範圍
        if (group == KeyStyles.Group.ZHUYIN || group == KeyStyles.Group.CHAR || group == KeyStyles.Group.FUNC) {
            val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
            val one = RadioButton(this).apply { id = View.generateViewId(); text = "只改這一顆"; isChecked = !groupScope }
            val all = RadioButton(this).apply { id = View.generateViewId(); text = group.title; isChecked = groupScope }
            rg.addView(one)
            rg.addView(all)
            rg.setOnCheckedChangeListener { _, id ->
                groupScope = id == all.id
                refresh()
            }
            panel.addView(rg)
        }

        val cur = styles.map[currentId()] ?: KeyStyle()
        // 沒自訂的顏色：用這顆鍵現在畫面上的顏色
        val now = kv.sampleColors(key)
        colorRow("底色", cur.bg, now?.bg ?: Color.WHITE) { v -> update({ it.copy(bg = v) }) }
        colorRow("文字顏色", cur.text, now?.text ?: Color.BLACK) { v -> update({ it.copy(text = v) }) }
        colorRow("外框顏色", cur.border, now?.border ?: Color.GRAY) { v -> update({ it.copy(border = v) }) }

        // 圓角
        val radius = ((cur.radius ?: 0.3f) * 100).toInt()
        val radiusLabel = text("圓角：$radius%", 15f)
        radiusLabel.setPadding(0, px(14f), 0, 0)
        panel.addView(SeekBar(this).apply {
            max = 100
            progress = radius
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    radiusLabel.text = "圓角：$p%"
                    update({ it.copy(radius = p / 100f) }, rebuild = false)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        text("0% 是直角，100% 是膠囊形。改了底色、外框或圓角，這顆鍵會換成自訂的形狀（羊皮紙這類紋理會變成接近的單色）。", 12f, dim = true)

        panel.addView(CheckBox(this).apply {
            text = "不顯示文字（只留底色和外框）"
            isChecked = cur.hideLabel == true
            setPadding(0, px(6f), 0, px(6f))
            setOnCheckedChangeListener { _, on -> update({ it.copy(hideLabel = if (on) true else null) }) }
        })

        if (groupScope && styles.map.containsKey(selId)) {
            text("💡 這顆鍵自己還有另外的設定，會優先用它自己的。", 13f, dim = true)
        }

        val what = if (groupScope) group.title else "這顆鍵"
        button("清除${what}的設定") { update({ null }) }

        section("這顆鍵上的圖案")
        decalList(selId)

        clearAllButton()
    }

    // ---------- 圖案 ----------

    private fun section(s: String) = text(s, 17f, bold = true).setPadding(0, px(22f), 0, px(4f))

    private fun selectDecal(d: Decal) {
        decalSel = d.id
        if (d.onBackground) {
            bgSelected = true
            selected = null
        } else {
            bgSelected = false
            selected = kv.rows.flatten().firstOrNull { KeyStyles.idOf(it) == d.target } ?: selected
        }
        refresh()
    }

    private fun saveDecals(list: List<Decal>, rebuild: Boolean = true) {
        decals = list
        Decals.save(prefs, list)
        kv.setDecalsLive(list)
        if (rebuild) refresh()
    }

    private fun addDecal(d: Decal) {
        decalSel = d.id
        saveDecals(decals + d)
    }

    /** 新圖案的預設位置：按鍵上放左上角（像你畫的小貓）、背景放正中間 */
    private fun newDecal(target: String, img: String) = if (target == Decal.BG)
        Decal(Decals.newId(), target, img, x = 0.5f, y = 0.5f, size = 0.3f)
    else
        Decal(Decals.newId(), target, img, x = 0.24f, y = 0.27f, size = 0.36f)

    private fun bgPanel() {
        kv.editHighlight = null
        text("正在編輯：鍵盤背景", 18f, bold = true).setPadding(0, px(10f), 0, px(4f))
        text("背景的圖案預設在按鍵後面（可以放在鍵和鍵之間的空隙），也可以設定成蓋在按鍵上面。", 13f, dim = true)
        section("背景上的圖案")
        decalList(Decal.BG)
        button("← 回到選按鍵") { bgSelected = false; refresh() }
    }

    private fun shapeName(d: Decal) = if (d.isShape) Decals.SHAPES.firstOrNull { it.first == d.shapeName }?.second ?: "圖案" else "我的圖片"

    /** 某個位置上的圖案清單，點了就選到它；最下面有「加圖案」 */
    private fun decalList(target: String) {
        val mine = decals.filter { it.target == target }
        if (mine.isEmpty()) text("還沒有圖案", 13f, dim = true)
        val bmps = Decals.bitmaps(this, mine)
        for ((i, d) in mine.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(4f), 0, px(4f))
                setOnClickListener { selectDecal(d) }
            }
            row.addView(DecalThumb(this, d, bmps[d.fileName]), LinearLayout.LayoutParams(px(48f), px(48f)))
            row.addView(TextView(this).apply {
                text = "${i + 1}. ${shapeName(d)}" + if (d.onBackground && d.front) "（蓋在按鍵上）" else ""
                textSize = 15f
                setPadding(px(12f), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply { text = "編輯 ›"; textSize = 14f; alpha = 0.7f })
            panel.addView(row)
        }
        button("＋ 加圖案") { showAddDialog(target) }
    }

    private fun showAddDialog(target: String) {
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 3
            setPadding(px(16f), px(8f), px(16f), 0)
        }
        var dialog: AlertDialog? = null
        for ((id, name) in Decals.SHAPES) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(px(6f), px(6f), px(6f), px(6f))
                setOnClickListener {
                    dialog?.dismiss()
                    addDecal(newDecal(target, Decal.SHAPE + id))
                }
            }
            cell.addView(DecalThumb(this, Decal("", target, Decal.SHAPE + id)), LinearLayout.LayoutParams(px(64f), px(64f)))
            cell.addView(TextView(this).apply { text = name; textSize = 12f; gravity = Gravity.CENTER })
            grid.addView(cell)
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(grid)
        box.addView(Button(this).apply {
            text = "📷 從相簿選圖片"
            isAllCaps = false
            setOnClickListener {
                dialog?.dismiss()
                pendingTarget = target
                val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_IMAGE)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(16f), px(8f), px(16f), 0)
        })
        dialog = AlertDialog.Builder(this)
            .setTitle("加圖案")
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMAGE) return
        val target = pendingTarget
        pendingTarget = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || target == null) return
        val name = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 40 * 1024 * 1024) null else Decals.storeBytes(this, bytes)
            }
        } catch (_: Exception) { null }
        if (name == null) {
            android.widget.Toast.makeText(this, "這個檔案讀不出來，換一張圖試試", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        addDecal(newDecal(target, Decal.FILE + name))
    }

    /** 選到一個圖案時的面板 */
    private fun decalPanel(d: Decal) {
        kv.editHighlight = null
        val where = if (d.onBackground) "鍵盤背景" else kv.rows.flatten().firstOrNull { KeyStyles.idOf(it) == d.target }?.let { KeyStyles.nameOf(it) } ?: d.target
        text("正在編輯：${shapeName(d)}", 18f, bold = true).setPadding(0, px(10f), 0, 0)
        text("貼在：$where", 14f, dim = true)
        val tip = if (d.onBackground) "👆 在鍵盤上用一隻手指拖曳＝移動，兩隻手指＝放大縮小、旋轉"
        else "👆 在上面放大的按鍵上：一隻手指拖曳＝移動，兩隻手指＝放大縮小、旋轉"
        text(tip, 14f).setPadding(0, px(8f), 0, px(4f))

        fun change(f: (Decal) -> Decal, rebuild: Boolean = false) {
            val cur = decals.firstOrNull { it.id == d.id } ?: return
            saveDecals(decals.map { if (it.id == d.id) f(cur) else it }, rebuild)
        }

        // 大小：按鍵上 5%–300%（相對按鍵高度）、背景 3%–150%（相對鍵盤高度）
        val maxSize = if (d.onBackground) 150 else 300
        val minSize = if (d.onBackground) 3 else 5
        slider("大小", (d.size * 100).toInt().coerceIn(minSize, maxSize), minSize, maxSize, { "$it%" }) { v -> change({ it.copy(size = v / 100f) }) }
        val rot = (((d.rotation % 360) + 540) % 360 - 180).toInt()
        slider("旋轉", rot, -180, 180, { "$it°" }) { v -> change({ it.copy(rotation = v.toFloat()) }) }
        slider("透明度", (d.alpha * 100).toInt(), 10, 100, { "$it%" }) { v -> change({ it.copy(alpha = v / 100f) }) }

        if (d.isShape) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(10f), 0, px(6f))
            }
            row.addView(TextView(this).apply { text = "顏色"; textSize = 15f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(View(this).apply {
                background = GradientDrawable().apply { cornerRadius = 10 * dp; setColor(d.color); setStroke(px(1.5f), 0x33000000) }
                setOnClickListener { ColorDialog.show(this@StudioActivity, "圖案顏色", d.color) { c -> change({ it.copy(color = c) }, rebuild = true) } }
            }, LinearLayout.LayoutParams(px(72f), px(40f)))
            panel.addView(row)
        }
        if (d.onBackground) {
            panel.addView(CheckBox(this).apply {
                text = "蓋在按鍵上面（不勾＝在按鍵後面）"
                isChecked = d.front
                setPadding(0, px(6f), 0, px(6f))
                setOnCheckedChangeListener { _, on -> change({ it.copy(front = on) }, rebuild = true) }
            })
        }

        // 圖層、複製、刪除
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(8f), 0, 0) }
        fun small(s: String, onClick: () -> Unit) = row.addView(Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        small("往上一層") { moveLayer(d, +1) }
        small("往下一層") { moveLayer(d, -1) }
        small("複製") {
            val copy = d.copy(id = Decals.newId(), x = d.x + 0.06f, y = d.y + 0.06f)
            addDecal(copy)
        }
        panel.addView(row)
        button("🗑 刪除這個圖案") {
            decalSel = null
            saveDecals(decals.filter { it.id != d.id })
            Decals.cleanup(this, decals)
        }
        button(if (d.onBackground) "← 回到鍵盤背景" else "← 回到$where") { decalSel = null; refresh() }
    }

    /** 跟同一個位置上的下一個（或上一個）圖案交換順序：後面的畫在上面 */
    private fun moveLayer(d: Decal, dir: Int) {
        val list = decals.toMutableList()
        val i = list.indexOfFirst { it.id == d.id }
        if (i < 0) return
        var j = i + dir
        while (j in list.indices && list[j].target != d.target) j += dir
        if (j !in list.indices) {
            android.widget.Toast.makeText(this, if (dir > 0) "已經在最上面了" else "已經在最下面了", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        list[i] = list[j].also { list[j] = list[i] }
        saveDecals(list)
    }

    private fun slider(label: String, value: Int, min: Int, max: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val t = text("$label：${fmt(value)}", 15f)
        t.setPadding(0, px(10f), 0, 0)
        panel.addView(SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    t.text = "$label：${fmt(p + min)}"
                    onChange(p + min)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }

    private fun clearDecalsButton() {
        button("清除所有圖案") {
            AlertDialog.Builder(this)
                .setMessage("要把貼上去的圖案全部拿掉嗎？")
                .setPositiveButton("全部清除") { _, _ ->
                    decalSel = null
                    saveDecals(emptyList())
                    Decals.cleanup(this, decals)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    companion object {
        private const val REQ_IMAGE = 500
    }

    private fun clearAllButton() {
        button("清除所有按鍵設定") {
            AlertDialog.Builder(this)
                .setMessage("要把所有按鍵都恢復成主題原本的樣子嗎？")
                .setPositiveButton("全部清除") { _, _ ->
                    styles = KeyStyles.EMPTY
                    KeyStyles.save(prefs, styles)
                    kv.reloadPrefs()
                    refresh()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 一列：名稱（下面小字寫是不是自訂的）、色塊（顯示現在的顏色，點了選顏色）、還原
     * @param now 沒自訂時，這顆鍵現在畫面上的顏色
     */
    private fun colorRow(label: String, value: Int?, now: Int, onPick: (Int?) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6f), 0, px(6f))
        }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(TextView(this).apply { text = label; textSize = 15f })
        names.addView(TextView(this).apply {
            text = if (value == null) "主題原本的顏色" else "已自訂"
            textSize = 12f
            alpha = 0.65f
        })
        row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val shown = value ?: now
        val chip = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(shown)
                setStroke(px(1.5f), 0x33000000)
            }
            contentDescription = "$label：" + String.format("#%06X", 0xFFFFFF and shown)
            setOnClickListener { ColorDialog.show(this@StudioActivity, label, shown) { onPick(it) } }
        }
        row.addView(chip, LinearLayout.LayoutParams(px(72f), px(40f)))
        val reset = Button(this).apply {
            text = "改回原色"
            isAllCaps = false
            visibility = if (value == null) View.INVISIBLE else View.VISIBLE
            setOnClickListener { onPick(null) }
        }
        row.addView(reset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = px(8f) })
        panel.addView(row)
    }

    private fun text(s: String, size: Float, bold: Boolean = false, dim: Boolean = false): TextView = TextView(this).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (dim) alpha = 0.7f
        panel.addView(this)
    }

    private fun button(s: String, onClick: () -> Unit) = panel.addView(Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    })
}
