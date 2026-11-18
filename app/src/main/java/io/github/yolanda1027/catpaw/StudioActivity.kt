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
    /** 工作副本（ThemeStore.WORK）：在這裡改，按儲存才寫回主題 */
    private lateinit var prefs: Prefs
    /** 鍵盤真正在用的設定 */
    private lateinit var live: Prefs
    /** 正在編輯的主題 */
    private var themeId = "classic"
    private lateinit var kv: CatKeyboardView
    private lateinit var panel: LinearLayout
    /** 上面那排分頁按鈕（注音、英文、符號…） */
    private val tabButtons = LinkedHashMap<String, Button>()
    /** 放大的單顆按鍵（蓋在鍵盤上面） */
    private lateinit var zoom: KeyZoomView
    private lateinit var zoomBox: android.widget.FrameLayout
    /** 放大畫面上的按鈕（「← 整個鍵盤」「＋ 加圖案」），編輯框的工具列要避開 */
    private val zoomButtons = ArrayList<View>()

    private var styles = KeyStyles.EMPTY
    private var selected: Key? = null
    /** true = 改「同一類的全部按鍵」，false = 只改選到的這顆 */
    private var groupScope = false
    /** 正在編輯哪一頁：zh 注音、en 英文、sym 符號、kao 顏文字、emo 表情、stk 我的貼圖、clip 剪貼簿、num 數字 */
    private var tab = "zh"
    /** 這一頁在主題裡的代號（注音、英文都是 ""） */
    private val page get() = if (tab == "zh" || tab == "en") "" else tab
    /** 這一頁的鍵盤背景圖代號 */
    private val kbId get() = KeyStyles.scoped(page, KeyStyles.KEYBOARD)

    private var decals: List<Decal> = emptyList()
    /** 正在編輯鍵盤背景（沒選按鍵） */
    private var bgSelected = false
    /** 正在改上方列（中／En 那一列）的顏色 */
    private var topSelected = false
    /** 正在改快捷符號列的顏色 */
    private var quickSelected = false
    /** 選到的圖案 */
    private var decalSel: String? = null
    /** 從相簿選圖片時，要貼到哪 */
    private var pendingTarget: String? = null
    /** 正在調按鍵動畫：null = 沒有、GLOBAL = 全部按鍵、KEY = 選到的鍵（或那一類） */
    private var animMode: String? = null

    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.autoSpaceButtons(this)
        live = Prefs(this)
        live.migrate()
        themeId = savedInstanceState?.getString(EXTRA_THEME) ?: intent?.getStringExtra(EXTRA_THEME) ?: ThemeStore.activeId(live)
        fromSettings = savedInstanceState?.getBoolean(EXTRA_FROM_SETTINGS) ?: intent?.getBooleanExtra(EXTRA_FROM_SETTINGS, false) ?: false
        if (!ThemeStore.exists(this, themeId)) themeId = ThemeStore.activeId(live)
        // 第一次打開：做一份工作副本；畫面重建（例如旋轉）時接著用原本的工作副本
        prefs = if (savedInstanceState == null) ThemeStore.openWork(this, live, themeId) else Prefs(this, ThemeStore.WORK)
        styles = KeyStyles.load(prefs)
        decals = Decals.load(prefs)
        tab = savedInstanceState?.getString("tab") ?: "zh"
        updateTitle()

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 要編輯哪一頁（每一頁的按鍵、背景、上方列可以各自設計）
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(8f), px(4f), px(8f), px(4f))
        }
        for ((id, title) in TABS) {
            val b = tabButton(title) { switchTab(id) }
            tabButtons[id] = b
            tabs.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        outer.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        })

        kv = CatKeyboardView(this, prefs, Haptics(this)).apply {
            // 快捷符號列有開的話預覽也放上去，看起來才跟實際打字時一樣
            quickItems = if (prefs.quickBar) prefs.quickList() else emptyList()
            editPicker = { key ->
                if (selected?.let { KeyStyles.idOf(it) } != KeyStyles.idOf(key)) groupScope = false
                selected = key
                bgSelected = false
                topSelected = false
                quickSelected = false
                decalSel = null
                if (animMode == GLOBAL) animMode = null
                refresh()
            }
            onDecalPicked = { d -> selectDecal(d) }
            onTopBarPicked = {
                selected = null
                bgSelected = false
                decalSel = null
                animMode = null
                quickSelected = false
                topSelected = true
                refresh()
            }
            onQuickBarPicked = {
                selected = null
                bgSelected = false
                decalSel = null
                animMode = null
                topSelected = false
                quickSelected = true
                refresh()
            }
            onDecalAction = { a, d -> decalAction(a, d) }
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
            handles.onAction = { a, d -> decalAction(a, d) }
            handles.onPick = { d ->
                decalSel = d?.id
                panel.post { refresh() }
            }
            handles.onChange = { d, done ->
                if (d.id.startsWith(LABEL)) {
                    // 拖的是按鍵上的字：換成這個字的位置、大小、角度
                    val k = selected
                    val ls = k?.let { kv.labelStyleFrom(it, d) }
                    if (k != null && ls != null) setLabelStyle(k, d.id.removePrefix(LABEL), ls, rebuild = false)
                } else {
                    this@StudioActivity.decals = this@StudioActivity.decals.map { if (it.id == d.id) d else it }
                    kv.setDecalsLive(this@StudioActivity.decals)
                    if (done) Decals.save(prefs, this@StudioActivity.decals)
                }
                if (done) panel.post { refresh() }
            }
        }
        // 工具列不要蓋到「← 整個鍵盤」「＋ 加圖案」：照按鈕真正的位置算（字放大的手機按鈕會比較寬）
        zoom.handles.blocked = { _, _ ->
            zoomButtons.filter { it.width > 0 }.map {
                android.graphics.RectF(it.left - 4 * dp, it.top - 4 * dp, it.right + 4 * dp, it.bottom + 4 * dp)
            }
        }
        // GIF 圖案換下一格時，放大的按鍵也要重畫
        kv.onAnimFrame = { zoom.invalidate() }
        zoomBox = android.widget.FrameLayout(this).apply {
            addView(zoom, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
            fun corner(label: String, gravity: Int, onClick: () -> Unit) = addView(Button(this@StudioActivity).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                alpha = 0.92f
                setOnClickListener { onClick() }
                zoomButtons.add(this)
            }, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, gravity).apply {
                setMargins(px(6f), px(6f), px(6f), px(26f))
            })
            corner("← 整個鍵盤", Gravity.TOP or Gravity.START) {
                selected = null
                decalSel = null
                animMode = null
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
        Ui.fitSystemBars(outer)
        setContentView(outer)
        Ui.matchSystemBarIcons(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback as android.window.OnBackInvokedCallback,
            )
        }
        // 上次離開時沒清掉的圖檔（例如 App 被系統關掉）先清一清；之後要等離開工作室才清，「上一步」才救得回刪掉的圖
        Decals.cleanup(this, decals)
        committed = snapshot()
        switchTab(tab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tab", tab)
        outState.putString(EXTRA_THEME, themeId)
        outState.putBoolean(EXTRA_FROM_SETTINGS, fromSettings)
    }

    // ---------- 存檔 ----------

    /** 標題：正在編輯哪個主題；有沒存的修改加個「•」 */
    private fun updateTitle() {
        val dirty = ::prefs.isInitialized && ThemeStore.isDirty(prefs)
        actionBar?.title = "✏️ " + ThemeStore.nameOf(this, themeId) + if (dirty) " •" else ""
    }

    private fun save(then: (() -> Unit)? = null) {
        commitHistory()
        ThemeStore.saveWork(this, live, themeId, prefs)
        android.widget.Toast.makeText(this, "已儲存「${ThemeStore.nameOf(this, themeId)}」", android.widget.Toast.LENGTH_SHORT).show()
        updateTitle()
        askApply(then)
    }

    private fun saveAs(then: (() -> Unit)? = null) {
        commitHistory()
        val box = LinearLayout(this).apply { setPadding(px(20f), px(8f), px(20f), 0) }
        val input = android.widget.EditText(this).apply {
            setText(ThemeStore.nameOf(this@StudioActivity, themeId) + " 的副本")
            isSingleLine = true
            selectAll()
        }
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        android.app.AlertDialog.Builder(this)
            .setTitle("另存新檔")
            .setView(box)
            .setPositiveButton("存成新主題") { _, _ ->
                themeId = ThemeStore.create(this, live, input.text.toString(), ThemeStore.snapshot(prefs))
                ThemeStore.markSaved(prefs)
                android.widget.Toast.makeText(this, "已存成「${ThemeStore.nameOf(this, themeId)}」", android.widget.Toast.LENGTH_SHORT).show()
                updateTitle()
                askApply(then)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 存好的不是正在用的主題：問要不要套到鍵盤上 */
    private fun askApply(then: (() -> Unit)?) {
        if (themeId == ThemeStore.activeId(live)) { then?.invoke(); return }
        android.app.AlertDialog.Builder(this)
            .setMessage("要把「${ThemeStore.nameOf(this, themeId)}」套用到鍵盤嗎？")
            .setPositiveButton("套用") { _, _ ->
                ThemeStore.applyTheme(this, live, themeId)
                then?.invoke()
            }
            .setNegativeButton("先不要") { _, _ -> then?.invoke() }
            .setOnCancelListener { then?.invoke() }
            .show()
    }

    /** 有沒存的修改時先問：儲存／不儲存／取消 */
    private fun confirmLeave(onLeave: () -> Unit) {
        commitHistory()
        if (!ThemeStore.isDirty(prefs)) { onLeave(); return }
        android.app.AlertDialog.Builder(this)
            .setTitle("「${ThemeStore.nameOf(this, themeId)}」還沒儲存")
            .setMessage("剛剛改的東西要存起來嗎？")
            .setPositiveButton("儲存") { _, _ -> save(onLeave) }
            .setNeutralButton("不儲存") { _, _ -> onLeave() }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 返回鍵：Android 13 以後用 OnBackInvokedCallback（targetSdk 36 收不到 onBackPressed） */
    private val backCallback: Any? =
        if (android.os.Build.VERSION.SDK_INT >= 33) android.window.OnBackInvokedCallback { back() } else null

    @Deprecated("Android 12 的返回鍵處理；13 以後走 backCallback")
    override fun onBackPressed() {
        back()
    }

    /**
     * 返回鍵：先回上一層（圖案 → 按鍵／背景 → 工作室首頁），在首頁才離開工作室（沒存會先問）。
     */
    private fun back() {
        if (goUp()) return
        confirmLeave { leave() }
    }

    /** 從設定的「⋯ → 在主題工作室編輯」進來的：離開時回到設定的外觀頁 */
    private var fromSettings = false

    /**
     * 離開工作室。工作室在自己的工作裡（桌面有獨立圖示），直接關掉會回到桌面；
     * 從設定進來的話，把原本那個設定頁叫回前面（停在外觀頁，主題清單也重新整理）。
     */
    private fun leave() {
        if (fromSettings) {
            startActivity(
                android.content.Intent(this, SettingsActivity::class.java).putExtra("page", "look").addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
        finish()
    }

    /** 回上一層；已經在首頁就回傳 false */
    private fun goUp(): Boolean {
        when {
            decalSel != null -> decalSel = null // 圖案、按鍵上的字 → 回到那顆鍵（或鍵盤背景）
            animMode != null -> animMode = null // 按鍵動畫 → 回到那顆鍵（或首頁）
            selected != null -> { selected = null; groupScope = false }
            bgSelected || topSelected || quickSelected -> { bgSelected = false; topSelected = false; quickSelected = false }
            else -> return false
        }
        refresh()
        return true
    }

    /** 已經開著工作室時，從設定按了別的主題的「編輯」 */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        fromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
        val id = intent.getStringExtra(EXTRA_THEME) ?: return
        if (id == themeId || !ThemeStore.exists(this, id)) return
        confirmLeave {
            themeId = id
            prefs = ThemeStore.openWork(this, live, id)
            recreate()
        }
    }

    private fun tabButton(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    /** 換到某一頁：預覽換成那一頁的樣子（跟真的打字時一樣的分類列） */
    private fun switchTab(id: String) {
        tab = if (TABS.any { it.first == id }) id else "zh"
        // 表情、顏文字的清單平常是鍵盤自己讀的；工作室單獨打開時還沒讀過
        if (Emojis.groups.isEmpty()) Emojis.load(assets.open("emoji.txt").bufferedReader().use { it.readText() })
        if (Symbols.kaomojiGroups.isEmpty()) Symbols.loadKaomoji(assets.open("kaomoji.txt").bufferedReader().use { it.readText() })
        val p = page
        kv.pageScope = p
        kv.isChinese = tab != "en"
        kv.quickItems = if (p.isEmpty() && prefs.quickBar) prefs.quickList() else emptyList()
        kv.symbolTabs = null
        kv.rows = when (tab) {
            "en" -> Layouts.ENGLISH
            "sym" -> {
                val c = Symbols.categories[1]
                kv.symbolTabs = Symbols.categories.map { it.tab }; kv.symbolTab = 1
                kv.symbolPageCount = c.pages.size; kv.symbolPage = 0
                Symbols.rows(c.pages[0], c.wide)
            }
            "kao" -> {
                kv.symbolTabs = listOf<String?>("◁", null) + Symbols.kaomojiGroups.map { it.first }; kv.symbolTab = 2
                val all = Symbols.kaomojiGroups.firstOrNull()?.second ?: emptyList()
                kv.symbolPageCount = ((all.size + Symbols.PER_PAGE_WIDE - 1) / Symbols.PER_PAGE_WIDE).coerceAtLeast(1); kv.symbolPage = 0
                Symbols.rows(all.take(Symbols.PER_PAGE_WIDE), true)
            }
            "emo", "stk" -> {
                kv.symbolTabs = listOf<String?>(null, "🖼️") + Emojis.groups.map { it.first }
                kv.symbolTab = if (tab == "stk") 1 else 2
                kv.symbolPageCount = 1; kv.symbolPage = 0
                if (tab == "stk") MyStickers.rows(this, 0) else Emojis.rows(Emojis.page(Emojis.groups[0].second, 0))
            }
            "clip" -> {
                kv.symbolTabs = listOf("剪貼簿　點一下貼上，長按釘選、排順序"); kv.symbolTab = 0
                kv.symbolPageCount = 1; kv.symbolPage = 0
                Layouts.clipRows(listOf(
                    ClipHistory.Item("複製過的文字會出現在這裡", 0L, false),
                    ClipHistory.Item("釘選的會排在最前面", 0L, true),
                ), 0)
            }
            "num" -> {
                kv.symbolTabs = listOf("數字"); kv.symbolTab = 0
                kv.symbolPageCount = 1; kv.symbolPage = 0
                Layouts.NUMPAD
            }
            else -> Layouts.ZHUYIN
        }.onPage(p)
        for ((id, b) in tabButtons) b.alpha = if (id == tab) 1f else 0.5f
        selected = null
        groupScope = false
        animMode = null
        bgSelected = false
        topSelected = false
        quickSelected = false
        decalSel = null
        refresh()
    }

    /** 目前在改的是哪個代號（單顆鍵或一整類） */
    private fun currentId(): String? {
        val key = selected ?: return null
        return if (groupScope) KeyStyles.groupIdOf(key) else KeyStyles.idOf(key)
    }

    private fun update(change: (KeyStyle) -> KeyStyle?, rebuild: Boolean = true) {
        val id = currentId() ?: return
        styles = styles.with(id, change(styles.map[id] ?: KeyStyle()))
        KeyStyles.save(prefs, styles)
        kv.setKeyStylesLive(styles)
        zoom.invalidate() // 放大的按鍵也要跟著重畫（拖滑桿時不會重建面板）
        if (rebuild) refresh()
    }

    /** 改「這一顆鍵」自己的樣式（按鍵上的字、順序：不管上面選的是不是整類） */
    private fun updateOwn(key: Key, change: (KeyStyle) -> KeyStyle, rebuild: Boolean = true) {
        val id = KeyStyles.idOf(key)
        styles = styles.with(id, change(styles.map[id] ?: KeyStyle()))
        KeyStyles.save(prefs, styles)
        kv.setKeyStylesLive(styles)
        zoom.invalidate()
        if (rebuild) refresh()
    }

    private fun setLabelStyle(key: Key, name: String, ls: LabelStyle?, rebuild: Boolean = true) = updateOwn(key, { st ->
        val m = (st.labels ?: emptyMap()).toMutableMap()
        if (ls == null || ls.isEmpty) m.remove(name) else m[name] = ls
        st.copy(labels = m.takeIf { it.isNotEmpty() })
    }, rebuild)

    /** 按鍵上的東西（字或圖案）往上或往下一層 */
    private fun moveElement(key: Key, id: String, dir: Int) {
        val order = kv.elementOrder(key).toMutableList()
        val i = order.indexOf(id)
        val j = i + dir
        if (i < 0 || j !in order.indices) {
            android.widget.Toast.makeText(this, if (dir > 0) "已經在最上面了" else "已經在最下面了", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        order[i] = order[j].also { order[j] = order[i] }
        updateOwn(key, { it.copy(order = order) })
    }

    // ---------- 上一步／下一步（最多 3 步） ----------

    /** 工作室改得到的東西：按鍵樣式、圖案、全部按鍵的動畫 */
    private data class Snap(val styles: KeyStyles, val decals: List<Decal>, val effect: String, val custom: CustomEffect)

    private fun snapshot() = Snap(styles, decals, prefs.pressEffect, prefs.customEffect)

    /** 上一次「做完一個動作」時的樣子 */
    private lateinit var committed: Snap
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()

    /**
     * 一個動作做完了（放開手指、放開滑桿、按了按鈕）：跟上一次比，有變就記一步。
     * 拖的過程中不會記，所以拖一次滑桿只算一步。
     */
    private fun commitHistory() {
        if (!::committed.isInitialized) return
        val now = snapshot()
        if (now == committed) return
        undoStack.addLast(committed)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        committed = now
        invalidateOptionsMenu()
        updateTitle()
    }

    private fun undo() {
        commitHistory() // 還沒記到的改動先記起來
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(committed)
        restore(prev)
    }

    private fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(committed)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        restore(next)
    }

    private fun restore(s: Snap) {
        styles = s.styles
        KeyStyles.save(prefs, styles)
        decals = s.decals
        Decals.save(prefs, decals)
        prefs.pressEffect = s.effect
        prefs.customEffect = s.custom
        committed = s
        if (decalSel?.startsWith(LABEL) != true && decals.none { it.id == decalSel }) decalSel = null
        kv.reloadPrefs()
        kv.setDecalsLive(decals)
        zoom.invalidate()
        invalidateOptionsMenu()
        updateTitle()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, MENU_UNDO, 0, "↶ 上一步").apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            isEnabled = undoStack.isNotEmpty()
        }
        menu.add(0, MENU_REDO, 1, "下一步 ↷").apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            isEnabled = redoStack.isNotEmpty()
        }
        menu.add(0, MENU_SAVE, 2, "儲存").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, MENU_SAVE_AS, 3, "另存新檔").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            MENU_UNDO -> undo()
            MENU_REDO -> redo()
            MENU_SAVE -> save()
            MENU_SAVE_AS -> saveAs()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onDestroy() {
        // 離開工作室：丟掉工作副本，清掉沒有任何主題在用的圖檔
        //（在工作室裡刪掉的圖，按上一步還救得回來，所以等到這時候才清）
        if (isFinishing) ThemeStore.closeWork(this)
        super.onDestroy()
    }

    // ---------- 下面的編輯面板 ----------

    private fun refresh() {
        commitHistory()
        panel.removeAllViews()
        kv.editTopBar = topSelected
        kv.editQuickBar = quickSelected
        // 快捷列在設定裡關著的話，改它的顏色時先顯示出來看
        kv.quickItems = if (page.isEmpty() && (prefs.quickBar || quickSelected)) prefs.quickList() else emptyList()
        // 只改一顆鍵（或那顆鍵上的圖案）時，顯示放大的按鍵
        val zoomKey = selected?.takeIf { !bgSelected && !groupScope && animMode != GLOBAL }
        zoomBox.visibility = if (zoomKey != null) View.VISIBLE else View.GONE
        zoom.key = zoomKey
        zoom.selectedId = decalSel
        kv.decalEditId = decalSel
        if (animMode == GLOBAL || (animMode == KEY && selected != null)) { animPanel(); return }
        animMode = null
        val sk = selected
        if (decalSel?.startsWith(LABEL) == true && sk != null && !bgSelected && !groupScope) { labelPanel(sk, decalSel!!.removePrefix(LABEL)); return }
        decals.firstOrNull { it.id == decalSel }?.let { decalPanel(it); return }
        decalSel = null
        if (topSelected) { topBarPanel(); return }
        if (quickSelected) { quickBarPanel(); return }
        if (bgSelected) { bgPanel(); return }
        val key = selected
        if (key == null) {
            kv.editHighlight = null
            text("正在設計：${TABS.first { it.first == tab }.second}頁", 18f, bold = true).setPadding(0, px(10f), 0, 0)
            text("👆 點一下上面鍵盤的任何一顆按鍵（或最上面那一列），就可以開始改它的樣子。每一頁（上面的分頁）各自設計；沒設計過的頁是主題原本的樣子。", 15f).setPadding(0, px(8f), 0, px(8f))
            text("改好按右上角「儲存」才會套用到鍵盤上。想換整體主題、字體，到「${getString(R.string.app_name)}」設定的外觀頁。", 13f, dim = true)
            button("🖼️ 這一頁的鍵盤背景（背景圖片、貼圖案）") { bgSelected = true; refresh() }
            button("🔝 這一頁的上方列顏色") { topSelected = true; refresh() }
            if (page.isEmpty()) button("⌨ 快捷符號列的顏色") { quickSelected = true; refresh() }
            button("✨ 按鍵動畫（全部按鍵）") { animMode = GLOBAL; refresh() }
            if (styles.keyEntries.isNotEmpty()) {
                text("目前有 ${styles.keyEntries.size} 組按鍵設定", 14f).setPadding(0, px(20f), 0, 0)
                clearAllButton()
            }
            if (decals.isNotEmpty()) clearDecalsButton()
            section("重來")
            resetButton()
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
        val cid = currentId()!!
        if (styles.map[cid] != null || decals.any { it.target == cid }) {
            button("📋 這${if (groupScope) "一類" else "顆鍵"}的設計套用到所有頁面") {
                confirmAllPages(if (groupScope) "「${group.title}」的設計" else "${KeyStyles.nameOf(key)}的設計（含上面的圖案）") { copyKeyToAllPages(cid) }
            }
        }

        val cur = styles.map[currentId()] ?: KeyStyle()
        // 沒自訂的顏色：用這顆鍵現在畫面上的顏色
        val now = kv.sampleColors(key)
        // 「照圖片的形狀」：底色、外框、圓角都交給圖片，這幾項變灰
        val eff = if (groupScope) cur else styles.resolve(key) ?: cur
        val shapeMode = eff.bgImage?.ownsShape == true
        greyIf(shapeMode) { colorRow("底色", cur.bg, now?.bg ?: Color.WHITE, allowTransparent = true) { v -> update({ it.copy(bg = v) }) } }
        imageRow(currentId()!!, "底圖", cur.bgImage)
        colorRow("文字顏色", cur.text, now?.text ?: Color.BLACK) { v -> update({ it.copy(text = v) }) }
        greyIf(shapeMode) {
            colorRow("外框顏色", cur.border, now?.border ?: Color.GRAY, allowTransparent = true) { v -> update({ it.copy(border = v) }) }
            borderControls(cur, eff, now)

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
                    override fun onStopTrackingTouch(s: SeekBar?) { commitHistory() }
                })
            })
            text("0% 是直角，100% 是膠囊形。改了底色、外框或圓角，這顆鍵會換成自訂的形狀（羊皮紙這類紋理會變成接近的單色）。", 12f, dim = true)
        }
        if (shapeMode) text("💡 底色、外框、圓角是灰的：現在用「" + (if (eff.bgImage?.nine == true) "固定邊角" else "照圖片的形狀") + "」，這些都交給圖片。", 12f, dim = true)

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

        // 被刪掉的字：可以恢復
        if (!groupScope) {
            val gone = kv.hiddenLabels(key)
            if (gone.isNotEmpty()) {
                section("被刪掉的字")
                for (n in gone) {
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(TextView(this).apply { text = labelName(key, n); textSize = 15f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(Button(this).apply {
                        text = "恢復"
                        isAllCaps = false
                        setOnClickListener {
                            val cur = styles.map[KeyStyles.idOf(key)]?.labels?.get(n) ?: LabelStyle()
                            setLabelStyle(key, n, cur.copy(hidden = false))
                        }
                    })
                    panel.addView(row)
                }
            }
        }

        section("這顆鍵上的圖案")
        if (!groupScope) text("💡 上面放大的按鍵裡，點一下字（例如主字、小字）也可以移動、換顏色、換上下層、刪除。", 13f, dim = true)
        decalList(selId)

        section("按鍵動畫")
        val own = styles.map[currentId()]?.effect
        text("目前：" + (own?.let { effectName(it) } ?: "跟全部按鍵一樣"), 14f, dim = true)
        button("✨ 設定${if (groupScope) group.title else "這顆鍵"}的動畫") { animMode = KEY; refresh() }

        clearAllButton()
    }

    // ---------- 圖案 ----------

    private fun section(s: String) = text(s, 17f, bold = true).setPadding(0, px(22f), 0, px(4f))

    private fun selectDecal(d: Decal) {
        decalSel = d.id
        topSelected = false
        quickSelected = false
        if (d.onBackground) {
            bgSelected = true
            selected = null
        } else {
            bgSelected = false
            selected = kv.rows.flatten().firstOrNull { KeyStyles.idOf(it) == d.target } ?: selected
        }
        refresh()
    }

    // ---------- 套用到所有頁面 ----------

    /** 先問一下（會蓋掉其他頁原本的設計） */
    private fun confirmAllPages(what: String, go: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("套用到所有頁面？")
            .setMessage("把${what}複製到每一頁（注音／英文、符號、顏文字、表情、我的貼圖、剪貼簿、數字），其他頁原本的這部分會被換掉。\n\n複製過去之後，每一頁還是可以各自再調。")
            .setPositiveButton("套用") { _, _ -> go() }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 圖案複製到每一頁：貼在 [fromTarget] 上的圖案，每一頁的同一個位置各放一份（那些頁原本的先清掉）。
     * @return 每一頁的「舊圖案 id → 新圖案 id」（按鍵上字和圖案的上下順序要跟著換）
     */
    private fun copyDecalsToAllPages(fromTarget: String): Map<String, Map<String, String>> {
        val base = KeyStyles.baseOf(fromTarget)
        val mine = decals.filter { it.target == fromTarget }
        val others = KeyStyles.PAGES.map { KeyStyles.scoped(it.id, base) }.filter { it != fromTarget }
        val idMaps = HashMap<String, Map<String, String>>()
        val copies = ArrayList<Decal>()
        for (t in others) {
            val m = HashMap<String, String>()
            for (d in mine) {
                val n = d.copy(id = Decals.newId(), target = t)
                m[d.id] = n.id
                copies.add(n)
            }
            idMaps[t] = m
        }
        saveDecals(decals.filter { it.target !in others } + copies, rebuild = false)
        return idMaps
    }

    /** 這顆鍵（或這一整類）的設計，連同貼在這顆鍵上的圖案，複製到每一頁 */
    private fun copyKeyToAllPages(id: String) {
        commitHistory()
        styles = styles.copyToAllPages(id)
        if (!KeyStyles.baseOf(id).startsWith("@")) {
            val idMaps = copyDecalsToAllPages(id)
            // 按鍵上字和圖案的上下順序：換成那一頁的圖案 id
            var m = styles
            for ((t, map) in idMaps) {
                val st = m.map[t] ?: continue
                val order = st.order ?: continue
                m = m.with(t, st.copy(order = order.map { map[it] ?: it }))
            }
            styles = m
        }
        KeyStyles.save(prefs, styles)
        kv.reloadPrefs()
        kv.setDecalsLive(decals)
        zoom.invalidate()
        android.widget.Toast.makeText(this, "已套用到所有頁面，每一頁還是可以自己再調", android.widget.Toast.LENGTH_SHORT).show()
        refresh()
    }

    /** 這一頁的背景（背景圖片、背景上的圖案）複製到每一頁 */
    private fun copyBackgroundToAllPages() {
        commitHistory()
        styles = styles.copyToAllPages(kbId)
        KeyStyles.save(prefs, styles)
        copyDecalsToAllPages(Decal.bgFor(page))
        kv.reloadPrefs()
        android.widget.Toast.makeText(this, "已套用到所有頁面，每一頁還是可以自己再調", android.widget.Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun copyTopBarToAllPages() {
        commitHistory()
        styles = styles.copyTopBarToAllPages(page)
        KeyStyles.save(prefs, styles)
        kv.reloadPrefs()
        android.widget.Toast.makeText(this, "已套用到所有頁面，每一頁還是可以自己再調", android.widget.Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun saveDecals(list: List<Decal>, rebuild: Boolean = true) {
        decals = list
        Decals.save(prefs, list)
        kv.setDecalsLive(list)
        zoom.invalidate()
        if (rebuild) refresh()
    }

    private fun addDecal(d: Decal) {
        decalSel = d.id
        saveDecals(decals + d)
    }

    /** 新圖案的預設位置：按鍵上放左上角（像你畫的小貓）、背景放正中間 */
    private fun newDecal(target: String, img: String) = if (KeyStyles.baseOf(target) == Decal.BG)
        Decal(Decals.newId(), target, img, x = 0.5f, y = 0.5f, size = 0.3f)
    else
        Decal(Decals.newId(), target, img, x = 0.24f, y = 0.27f, size = 0.36f)

    private fun bgPanel() {
        kv.editHighlight = null
        text("正在編輯：鍵盤背景", 18f, bold = true).setPadding(0, px(10f), 0, px(4f))
        section("背景圖片")
        imageRow(kbId, "", styles.keyboardImageFor(page))
        text("鋪滿選字列下面的整塊（按鍵後面）。", 13f, dim = true)
        section("背景上的圖案")
        text("背景的圖案預設在按鍵後面（可以放在鍵和鍵之間的空隙），也可以設定成蓋在按鍵上面。", 13f, dim = true)
        decalList(Decal.bgFor(page))
        if (styles.keyboardImageFor(page) != null || decals.any { it.target == Decal.bgFor(page) }) {
            button("📋 這一頁的背景套用到所有頁面") { confirmAllPages("這一頁的背景圖片和背景上的圖案") { copyBackgroundToAllPages() } }
        }
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
            text = "📷 從相簿選圖片（GIF 動圖也可以）"
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
        if (Eyedropper.onResult(this, requestCode, resultCode, data)) return
        if (requestCode == REQ_QUICK_FRAME) {
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) return
            // 九宮格只用靜態圖：GIF 也只留第一格
            val name = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > 40 * 1024 * 1024) null else Decals.storeBytes(this, bytes, keepGif = false, maxPx = 1024)
                }
            } catch (_: Exception) { null }
            if (name == null) {
                android.widget.Toast.makeText(this, "這個檔案讀不出來，換一張圖試試", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            // 上傳完直接調整邊角；換一張的話沿用原本的邊角設定
            val prev = styles.quick?.frame
            openNineSlice(NineFrame(name, prev?.sx ?: NineFrame.DEFAULT, prev?.sy ?: NineFrame.DEFAULT))
            return
        }
        if (requestCode == REQ_FILL) {
            val target = pendingFill
            pendingFill = null
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null || target == null) return
            val name = readImage(uri, Decals.FILL_MAX_PX) ?: return
            // 上傳完直接進裁切畫面；按取消就不套用。原本是「照圖片的形狀」就沿用
            val prev = styles.map[target]?.bgImage
            when {
                // 原本是九宮格：沿用邊角設定，直接打開調整邊角
                prev?.nine == true -> ImageFill(name, nine = true, sx = prev.sx, sy = prev.sy).let { setFill(target, it); openKeyNine(target, it) }
                prev?.shape == true -> openCrop(target, ImageFill(name, zoom = ImageFill.FIT, shape = true))
                else -> openCrop(target, ImageFill(name))
            }
            return
        }
        if (requestCode == REQ_EFFECT_IMAGE) {
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) return
            val name = readImage(uri) ?: return
            setCustom(currentCustom().copy(particle = Decal.FILE + name))
            return
        }
        if (requestCode != REQ_IMAGE) return
        val target = pendingTarget
        pendingTarget = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || target == null) return
        val name = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 40 * 1024 * 1024) null else Decals.storeBytes(this, bytes, keepGif = true)
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
        val tip = "👆 拖中間＝移動、拖四個角＝放大縮小、拖四邊的小膠囊＝只改寬或只改高、拖下面的圓圈＝旋轉；框上面的小工具可以複製、翻轉、換上下層、刪除"
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

        if (d.isShape) shapeStyleEditor(d) { f, rebuild -> change({ it.withStyle(f(it.shapeStyle())) }, rebuild) }
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
        small("往上一層") { decalAction("up", d) }
        small("往下一層") { decalAction("down", d) }
        small("左右翻轉") { decalAction("flip", d) }
        panel.addView(row)
        if (d.stretch != 1f) button("↔ 恢復原本的寬高比例") { saveDecals(decals.map { if (it.id == d.id) it.copy(stretch = 1f) else it }) }
        if (!d.onBackground) copyButtons(d)
        button("🗑 刪除這個圖案") {
            decalSel = null
            saveDecals(decals.filter { it.id != d.id })
        }
        button(if (d.onBackground) "← 回到鍵盤背景" else "← 回到$where") { decalSel = null; refresh() }
    }

    /**
     * 內建圖案的樣式（像小畫家）：填色（可以透明）、外框（有／沒有、顏色、樣式、粗細）。
     * 用「圓角方形」＋「縫線」外框，就做得出粉紅縫線那種按鍵。
     */
    private fun shapeStyleEditor(d: Decal, set: ((ShapeStyle) -> ShapeStyle, Boolean) -> Unit) {
        val st = d.shapeStyle()
        section("填色與外框")
        fun colorLine(label: String, caption: String, color: Int, clear: Boolean, onClear: (() -> Unit)?, onPick: (Int) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(6f), 0, px(6f))
            }
            val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            names.addView(TextView(this).apply { text = label; textSize = 15f })
            names.addView(TextView(this).apply { text = caption; textSize = 12f; alpha = 0.65f })
            row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (onClear != null && !clear) row.addView(Button(this).apply {
                text = "透明"
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(px(10f), 0, px(10f), 0)
                setOnClickListener { onClear() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(6f) })
            row.addView(TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                text = if (clear) "透明" else ""
                background = GradientDrawable().apply { cornerRadius = 10 * dp; setColor(if (clear) 0x14888888 else color); setStroke(px(1.5f), 0x33000000) }
                setOnClickListener { ColorDialog.show(this@StudioActivity, label, color or (0xFF shl 24)) { onPick(it) } }
            }, LinearLayout.LayoutParams(px(72f), px(40f)))
            panel.addView(row)
        }

        val fillClear = Color.alpha(st.fill) == 0
        colorLine("填色", if (fillClear) "透明（只剩外框）" else "圖案裡面的顏色", if (fillClear) d.color else st.fill, fillClear,
            onClear = { set({ it.copy(fill = Color.TRANSPARENT) }, true) }) { c -> set({ it.copy(fill = c) }, true) }

        panel.addView(android.widget.Switch(this).apply {
            text = "外框"
            textSize = 15f
            isChecked = st.outline != null
            setPadding(0, px(10f), 0, px(6f))
            setOnCheckedChangeListener { _, on ->
                // 打開外框：沒填色的用原本的顏色，有填色的先用白色（像縫線）
                set({ it.copy(outline = if (on) (if (Color.alpha(it.fill) == 0) d.color or (0xFF shl 24) else Color.WHITE) else null) }, true)
            }
        })
        val outline = st.outline ?: return
        colorLine("外框顏色", "", outline, false, onClear = null) { c -> set({ it.copy(outline = c) }, true) }
        text("外框樣式", 15f).setPadding(0, px(6f), 0, px(2f))
        chipRow(DecalShapes.DASHES.map { it.second }, DecalShapes.DASHES.indexOfFirst { it.first == st.dash }) { i ->
            set({ it.copy(dash = DecalShapes.DASHES[i].first) }, true)
        }
        slider("外框粗細", (st.width * 100).toInt().coerceIn(1, 20), 1, 20, { "$it" }) { v -> set({ it.copy(width = v / 100f) }, false) }
        if (st.dash == "stitch") {
            slider("縫線離邊距離", (st.inset * 100).toInt().coerceIn(0, 25), 0, 25, { if (it == 0) "0（貼在最外面的邊上）" else "$it" }) { v ->
                set({ it.copy(inset = v / 100f) }, false)
            }
        }
        if (d.shapeName != "roundrect") text("💡 想做「粉紅縫線」那種按鍵：加一個「圓角方形」，填粉紅色、外框選「縫線」配白色，再用四邊的拉把拉成跟按鍵一樣大。", 12f, dim = true)
    }

    // ---------- 把圖案複製到同一類的所有鍵 ----------

    /** 複製出去的圖案 id 長這樣：cp_<原本的 id>_<新 id>，再複製一次時用來找到舊的、換成新的 */
    private fun copyRoot(d: Decal) = if (d.id.startsWith("cp_")) d.id.split("_").getOrElse(1) { d.id } else d.id
    private fun copyPrefix(d: Decal) = "cp_" + copyRoot(d) + "_"

    private fun copyButtons(d: Decal) {
        val key = selected ?: return
        val group = KeyStyles.groupOf(key)
        if (group != KeyStyles.Group.ZHUYIN && group != KeyStyles.Group.CHAR && group != KeyStyles.Group.FUNC) return
        val existing = decals.count { it.id.startsWith(copyPrefix(d)) && it.id != d.id }
        section("套用到其他鍵")
        button("📋 複製到" + group.title + if (existing > 0) "（更新）" else "") { copyToGroup(d, key, group) }
        if (existing > 0) {
            text("已經複製到 $existing 顆鍵。改了這個圖案之後再按一次，其他鍵會一起更新。", 12f, dim = true)
            button("移除複製出去的 $existing 個圖案") {
                saveDecals(decals.filter { !(it.id.startsWith(copyPrefix(d)) && it.id != d.id) })
            }
        }
    }

    /** 在同一類的每一顆鍵上放一個一樣的圖案（位置、大小、樣式、上下層都一樣）；之前複製過的會換成新的 */
    private fun copyToGroup(d: Decal, key: Key, group: KeyStyles.Group) {
        val prefix = copyPrefix(d)
        val srcId = KeyStyles.idOf(key)
        val targets = kv.rows.flatten()
            .filter { it.act != Act.GAP && KeyStyles.groupOf(it) == group && KeyStyles.idOf(it) != srcId }
            .distinctBy { KeyStyles.idOf(it) }
        // 原本的圖案壓在字底下的話，複製出去的也壓在字底下
        val order = kv.elementOrder(key)
        val idx = order.indexOf(d.id)
        val below = idx >= 0 && order.any { it.startsWith(LABEL) } && order.take(idx).none { it.startsWith(LABEL) }
        val old = decals.filter { it.id.startsWith(prefix) && it.id != d.id }
        val copies = targets.map { k -> d.copy(id = prefix + Decals.newId(), target = KeyStyles.idOf(k)) }
        // 舊的複製品如果剛好放在原本那顆鍵上（從複製品再複製），也一起換掉
        saveDecals(decals.filter { it !in old } + copies, rebuild = false)
        // 每顆鍵的上下層：舊複製品的位置拿掉；要壓在字底下的放最下面
        var st = styles
        for ((k, cp) in targets.zip(copies)) {
            val id = KeyStyles.idOf(k)
            val cur = st.map[id]
            val saved = cur?.order?.filter { o -> old.none { it.id == o } }
            val newOrder = when {
                below -> listOf(cp.id) + kv.elementOrder(k).filter { it != cp.id }
                saved != null -> saved
                else -> null
            }
            if (newOrder != cur?.order) st = st.with(id, (cur ?: KeyStyle()).copy(order = newOrder))
        }
        styles = st
        KeyStyles.save(prefs, styles)
        kv.setKeyStylesLive(styles)
        zoom.invalidate()
        refresh()
        android.widget.Toast.makeText(this, "已經複製到 ${targets.size} 顆鍵", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 刪掉按鍵上的一個字（藏起來；按鍵照樣能打字，面板上可以恢復） */
    private fun deleteLabel(key: Key, name: String) {
        val cur = styles.map[KeyStyles.idOf(key)]?.labels?.get(name) ?: LabelStyle()
        decalSel = null
        setLabelStyle(key, name, cur.copy(hidden = true))
    }

    /** 被刪掉的字叫什麼（面板上顯示用） */
    private fun labelName(key: Key, name: String) = when {
        name == "sub" -> "小字「${key.sub ?: ""}」"
        key.icon != null -> "圖示"
        else -> "主字「${key.label}」"
    }

    /** 編輯框上面的小工具（圖案：複製、翻轉、上下層、刪除；字：顏色、上下層、重設、刪除） */
    private fun decalAction(action: String, d: Decal) {
        val key = selected
        if (d.id.startsWith(LABEL)) {
            val name = d.id.removePrefix(LABEL)
            if (key == null) return
            when (action) {
                "up" -> moveElement(key, d.id, +1)
                "down" -> moveElement(key, d.id, -1)
                "reset" -> setLabelStyle(key, name, null)
                "color" -> pickLabelColor(key, name)
                "delete" -> deleteLabel(key, name)
            }
            return
        }
        when (action) {
            // 按鍵上的圖案跟字一起排順序；背景的圖案照原本的方式
            "up" -> if (!d.onBackground && key != null) moveElement(key, d.id, +1) else moveLayer(d, +1)
            "down" -> if (!d.onBackground && key != null) moveElement(key, d.id, -1) else moveLayer(d, -1)
            "copy" -> addDecal(d.copy(id = Decals.newId(), x = d.x + 0.08f, y = d.y + 0.08f))
            "flip" -> saveDecals(decals.map { if (it.id == d.id) it.copy(flip = !it.flip) else it })
            "delete" -> {
                decalSel = null
                saveDecals(decals.filter { it.id != d.id })
            }
        }
    }

    private fun pickLabelColor(key: Key, name: String) {
        val now = kv.labelColorOf(key, name) ?: Color.BLACK
        ColorDialog.show(this, "字的顏色", now) { c ->
            val old = styles.map[KeyStyles.idOf(key)]?.labels?.get(name) ?: LabelStyle()
            setLabelStyle(key, name, old.copy(color = c))
        }
    }

    /** 選到按鍵上的一個字（主字、小字）時的面板 */
    private fun labelPanel(key: Key, name: String) {
        kv.editHighlight = null
        val ls = styles.map[KeyStyles.idOf(key)]?.labels?.get(name) ?: LabelStyle()
        text("正在編輯：" + labelName(key, name), 18f, bold = true).setPadding(0, px(10f), 0, 0)
        text("貼在：" + KeyStyles.nameOf(key), 14f, dim = true)
        text("👆 拖中間或上面的十字＝移動、拖四個角＝放大縮小、拖下面的圓圈＝旋轉；框上面的小工具可以換顏色、換上下層、重設、刪除", 14f).setPadding(0, px(8f), 0, px(4f))

        // 顏色
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(10f), 0, px(6f))
        }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(TextView(this).apply { text = "顏色"; textSize = 15f })
        names.addView(TextView(this).apply { text = if (ls.color == null) "照按鍵的文字顏色" else "已自訂"; textSize = 12f; alpha = 0.65f })
        row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val now = kv.labelColorOf(key, name) ?: Color.BLACK
        row.addView(View(this).apply {
            background = GradientDrawable().apply { cornerRadius = 10 * dp; setColor(now); setStroke(px(1.5f), 0x33000000) }
            setOnClickListener { pickLabelColor(key, name) }
        }, LinearLayout.LayoutParams(px(72f), px(40f)))
        if (ls.color != null) row.addView(Button(this).apply {
            text = "改回原色"
            isAllCaps = false
            setOnClickListener { setLabelStyle(key, name, ls.copy(color = null)) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = px(8f) })
        panel.addView(row)

        fun live(f: (LabelStyle) -> LabelStyle) {
            val cur = styles.map[KeyStyles.idOf(key)]?.labels?.get(name) ?: LabelStyle()
            setLabelStyle(key, name, f(cur), rebuild = false)
        }
        slider("透明度", (ls.alpha * 100).toInt(), 0, 100, { if (it == 0) "0%（完全透明）" else "$it%" }) { v -> live { it.copy(alpha = v / 100f) } }
        slider("大小", (ls.scale * 100).toInt().coerceIn(30, 300), 30, 300, { "$it%" }) { v -> live { it.copy(scale = v / 100f) } }
        val rot = (((ls.rotation % 360) + 540) % 360 - 180).toInt()
        slider("旋轉", rot, -180, 180, { "$it°" }) { v -> live { it.copy(rotation = v.toFloat()) } }

        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(8f), 0, 0) }
        fun small(s: String, onClick: () -> Unit) = btns.addView(Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        small("往上一層") { moveElement(key, LABEL + name, +1) }
        small("往下一層") { moveElement(key, LABEL + name, -1) }
        small("重設") { setLabelStyle(key, name, null) }
        panel.addView(btns)
        button("🗑 刪除這個字（按鍵照樣能打字，之後可以恢復）") { deleteLabel(key, name) }
        button("← 回到" + KeyStyles.nameOf(key)) { decalSel = null; refresh() }
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

    private fun slider(label: String, value: Int, min: Int, max: Int, fmt: (Int) -> String, onDone: (() -> Unit)? = null, onChange: (Int) -> Unit) {
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
                override fun onStopTrackingTouch(s: SeekBar?) {
                    commitHistory() // 放開滑桿＝一步
                    onDone?.invoke()
                }
            })
        })
    }

    /** 恢復主題預設：清掉所有圖案和按鍵的單獨設定（要再確認一次） */
    private fun resetButton() {
        // 「我的主題」：恢復成匯入（或建立）時的樣子；內建主題、舊版沒記錄的：清成底色主題的樣子
        val origin = if (ThemeStore.isBuiltIn(themeId)) null else ThemeStore.origin(this, themeId)
        if (origin != null) {
            val kind = if (ThemeStore.originKind(this, themeId) == "import") "匯入時" else "建立時"
            button("↺ 恢復成${kind}的樣子") {
                commitHistory()
                if (!ThemeStore.differsFromOrigin(ThemeStore.snapshot(prefs), origin, ThemeStore.STUDIO_KEYS)) {
                    android.widget.Toast.makeText(this, "目前已經是${kind}的樣子了", android.widget.Toast.LENGTH_SHORT).show()
                    return@button
                }
                val dialog = AlertDialog.Builder(this)
                    .setTitle("恢復成${kind}的樣子？")
                    .setMessage("每一頁的按鍵、背景、圖案、上方列、快捷列和按鍵動畫，都會變回「${ThemeStore.nameOf(this, themeId)}」${kind}的樣子。\n\n字體、配色、按鍵高度不變。還沒按「儲存」之前，鍵盤不會跟著變，也可以按上一步救回來。")
                    .setPositiveButton("恢復") { _, _ -> resetToOrigin(origin, kind) }
                    .setNegativeButton("取消", null)
                    .show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#E5484D"))
            }
            return
        }
        if (!ThemeStore.isBuiltIn(themeId)) {
            text("這個主題是舊版建立的，沒有記下匯入時的樣子，下面的按鈕只能清成底色主題的樣子。重新匯入一次主題檔，之後就能恢復成匯入時的樣子。", 12f, dim = true)
        }
        button("↺ 恢復主題預設") {
            val nDecals = decals.size
            val nStyles = styles.keyEntries.size
            val hasBgImage = styles.map.keys.any { KeyStyles.baseOf(it) == KeyStyles.KEYBOARD }
            val hasTop = styles.topBar != null || styles.pageTops.isNotEmpty()
            val hasQuick = styles.quick != null
            if (nDecals == 0 && nStyles == 0 && !hasBgImage && !hasTop && !hasQuick) {
                android.widget.Toast.makeText(this, "目前已經是主題預設的樣子了", android.widget.Toast.LENGTH_SHORT).show()
                return@button
            }
            val lines = buildList {
                if (nDecals > 0) add("・貼上去的圖案（$nDecals 個）")
                if (nStyles > 0) add("・按鍵的單獨設定（$nStyles 組，包含底圖、單顆鍵的動畫）")
                if (hasBgImage) add("・鍵盤背景圖片")
                if (hasTop) add("・上方列（中／En）的顏色")
                if (hasQuick) add("・快捷符號列的顏色")
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle("恢復主題預設？")
                .setMessage("會清除：\n" + lines.joinToString("\n") +
                    "\n\n整體主題、字體、全部按鍵共用的動畫不會變。\n清除後沒辦法復原，想留著的話可以先到設定的「外觀 → 匯入／匯出」存一份。")
                .setPositiveButton("恢復預設") { _, _ -> resetAll() }
                .setNegativeButton("取消", null)
                .show()
            // 會清掉東西的按鈕用紅色，比較不會按錯
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#E5484D"))
        }
    }

    /** 恢復成匯入／建立時的樣子（只換工作室改得到的部分） */
    private fun resetToOrigin(origin: org.json.JSONObject, kind: String) {
        selected = null
        decalSel = null
        bgSelected = false
        topSelected = false
        quickSelected = false
        animMode = null
        groupScope = false
        ThemeStore.restoreParts(prefs, origin, ThemeStore.STUDIO_KEYS)
        styles = KeyStyles.load(prefs)
        decals = Decals.load(prefs)
        kv.reloadPrefs()
        kv.setDecalsLive(decals)
        zoom.invalidate()
        refresh()
        android.widget.Toast.makeText(this, "已恢復成${kind}的樣子（記得儲存）", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun resetAll() {
        selected = null
        decalSel = null
        bgSelected = false
        topSelected = false
        quickSelected = false
        animMode = null
        groupScope = false
        styles = KeyStyles.EMPTY
        KeyStyles.save(prefs, styles)
        saveDecals(emptyList(), rebuild = false)
        kv.reloadPrefs()
        refresh()
        android.widget.Toast.makeText(this, "已恢復成主題預設", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun clearDecalsButton() {
        button("清除所有圖案") {
            AlertDialog.Builder(this)
                .setMessage("要把貼上去的圖案全部拿掉嗎？")
                .setPositiveButton("全部清除") { _, _ ->
                    decalSel = null
                    saveDecals(emptyList())
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    // ---------- 上方列（中／En、選字列）的顏色 ----------

    private fun setTopBar(change: (TopBarStyle) -> TopBarStyle?) {
        setTopBarLive(change)
        refresh()
    }

    /** 背景圖片鋪的範圍變了：問要不要現在重新裁切 */
    private fun askRecropBackground(bg: ImageFill) {
        val nowClear = kv.keyboardImageFrame().top == 0f
        AlertDialog.Builder(this)
            .setTitle("背景圖片的範圍變了")
            .setMessage(
                if (nowClear) "上方列變透明，背景圖片會往上鋪滿整個鍵盤，比例變高了，圖片可能被放大、左右被裁掉一點。要現在重新調整嗎？"
                else "上方列不透明了，背景圖片只鋪在上方列下面，比例變扁了，圖片可能被放大、上下被裁掉一點。要現在重新調整嗎？",
            )
            .setPositiveButton("調整") { _, _ -> openCrop(kbId, bg) }
            .setNegativeButton("之後再說", null)
            .show()
    }

    /** 拖滑桿時：只更新畫面，不重建面板（放開時滑桿會自己記一步） */
    private fun setTopBarLive(change: (TopBarStyle) -> TopBarStyle?) {
        styles = styles.withTopBarFor(page, change(styles.topBarFor(page) ?: TopBarStyle()))
        KeyStyles.save(prefs, styles)
        kv.setKeyStylesLive(styles)
    }

    private fun topBarPanel() {
        kv.editHighlight = null
        text("正在編輯：上方列", 18f, bold = true).setPadding(0, px(10f), 0, px(4f))
        text("「中／En」那一列。打字時會變成選字列、切到符號時是分類列，顏色都照這裡的。", 13f, dim = true)
        val cur = styles.topBarFor(page) ?: TopBarStyle()
        val now = kv.topBarColors()
        if (styles.topBarFor(page) != null) {
            button("📋 這一頁的上方列套用到所有頁面") { confirmAllPages("這一頁上方列的設計") { copyTopBarToAllPages() } }
        }
        colorRow("底色", cur.bg, now.bg!!, allowTransparent = true) { v ->
            val wasClear = kv.keyboardImageFrame().top == 0f
            setTopBar { it.copy(bg = v) }
            // 上方列透明↔不透明：背景圖片鋪的範圍變了（變高或變扁），問要不要重新調整
            val bg = styles.keyboardImageFor(page)
            if (bg != null && wasClear != (kv.keyboardImageFrame().top == 0f)) askRecropBackground(bg)
        }
        text("透明：看得到下面的鍵盤背景；有背景圖片的話，圖片會往上鋪滿整個鍵盤（範圍變高，可以到「鍵盤背景」按「裁切」重新調整）。", 12f, dim = true)
        colorRow("選中的底色（「中」底下那塊）", cur.tab, now.tab!!, allowTransparent = true) { v -> setTopBar { it.copy(tab = v) } }
        colorRow("選中那塊的外框", cur.tabBorder, now.tabBorder!!, allowTransparent = true) { v -> setTopBar { it.copy(tabBorder = v) } }
        borderEditor(
            style = cur.tabBorderStyle, width = cur.tabBorderWidth, inset = cur.tabBorderInset,
            customized = cur.tabBorderStyle != null || cur.tabBorderWidth != null || cur.tabBorderInset != null,
            hint = if (cur.tabBorder == null) "💡 先在上面選一個外框顏色，樣式和粗細才看得到。打字時第一個候選字、表情的分類也是這一塊。" else null,
            onStyle = { s -> setTopBar { it.copy(tabBorderStyle = s) } },
            onWidth = { w -> setTopBarLive { it.copy(tabBorderWidth = w) } },
            onInset = { d -> setTopBarLive { it.copy(tabBorderInset = d) } },
            onReset = { setTopBar { it.copy(tabBorderStyle = null, tabBorderWidth = null, tabBorderInset = null) } },
        )
        colorRow("文字（選中的「中」、候選字）", cur.text, now.text!!) { v -> setTopBar { it.copy(text = v) } }
        colorRow("文字（沒選中的「En」）", cur.textDim, now.textDim!!) { v -> setTopBar { it.copy(textDim = v) } }
        colorRow("圖示（剪貼簿、▽）", cur.icon, now.icon!!) { v -> setTopBar { it.copy(icon = v) } }
        if (styles.topBarFor(page) != null) button("全部改回主題原本的顏色") { setTopBar { null } }
        button("← 回到選按鍵") { topSelected = false; refresh() }
    }

    // ---------- 按鍵外框：樣式、粗細、縫線離邊距離 ----------

    private fun borderControls(cur: KeyStyle, eff: KeyStyle, now: CatKeyboardView.KeyColors?) {
        val noBorder = eff.border == null && now?.hasBorder != true
        borderEditor(
            style = eff.borderStyle, width = eff.borderWidth, inset = eff.borderInset,
            customized = cur.borderStyle != null || cur.borderWidth != null || cur.borderInset != null,
            hint = if (noBorder) "💡 這顆鍵現在沒有外框，先在上面選一個外框顏色，樣式和粗細才看得到。" else null,
            onStyle = { s -> update({ it.copy(borderStyle = s) }) },
            onWidth = { w -> update({ it.copy(borderWidth = w) }, rebuild = false) },
            onInset = { d -> update({ it.copy(borderInset = d) }, rebuild = false) },
            onReset = { update({ it.copy(borderStyle = null, borderWidth = null, borderInset = null) }) },
        )
    }

    /** 外框樣式、粗細、縫線離邊距離（按鍵和快捷列共用） */
    private fun borderEditor(
        style: String?, width: Float?, inset: Float?, customized: Boolean, hint: String?,
        onStyle: (String) -> Unit, onWidth: (Float) -> Unit, onInset: (Float) -> Unit, onReset: () -> Unit,
    ) {
        val st = style ?: "solid"
        text("外框樣式", 15f).setPadding(0, px(8f), 0, 0)
        val opts = KeyStyle.BORDER_STYLES
        chipRow(opts.map { it.second }, opts.indexOfFirst { it.first == st }.coerceAtLeast(0)) { i -> onStyle(opts[i].first) }
        if (hint != null) text(hint, 12f, dim = true)
        val half = { v: Int -> "%.1f".format(v / 2f).removeSuffix(".0") }
        slider("外框粗細", ((width ?: KeyStyle.DEFAULT_BORDER_WIDTH) * 2).toInt(), 1, 16, half) { v -> onWidth(v / 2f) }
        if (st == "stitch") {
            slider("縫線離邊距離", ((inset ?: KeyStyle.DEFAULT_BORDER_INSET) * 2).toInt(), 0, 24, half) { v -> onInset(v / 2f) }
        }
        if (customized) button("外框樣式改回預設（實線、粗細 2）") { onReset() }
    }

    /** 在 block 裡加到面板的東西：on 的話整塊變灰、按不動 */
    private fun greyIf(on: Boolean, block: () -> Unit) {
        val start = panel.childCount
        block()
        if (!on) return
        for (i in start until panel.childCount) disableAll(panel.getChildAt(i))
    }

    /** 整塊按不動；每個最小的元件各自變淡（色塊、文字、按鈕都一樣淡，不會疊兩次） */
    private fun disableAll(v: View) {
        v.isEnabled = false
        v.isClickable = false
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) disableAll(v.getChildAt(i))
        } else {
            v.alpha = 0.32f
        }
    }

    // ---------- 快捷符號列的顏色 ----------

    private fun setQuick(change: (QuickBarStyle) -> QuickBarStyle?, rebuild: Boolean = true) {
        styles = styles.withQuick(change(styles.quick ?: QuickBarStyle()))
        KeyStyles.save(prefs, styles)
        kv.setKeyStylesLive(styles)
        if (rebuild) refresh()
    }

    private fun quickBarPanel() {
        kv.editHighlight = null
        text("正在編輯：快捷符號列", 18f, bold = true).setPadding(0, px(10f), 0, px(4f))
        if (!prefs.quickBar) text("💡 快捷符號列在設定裡是關著的，這裡先顯示出來給你看。要用的話到「設定 → ⌨ 快捷符號列」打開。", 13f, dim = true)
        val cur = styles.quick ?: QuickBarStyle()
        val now = kv.quickBarColors()
        val hasFrame = cur.frame != null
        colorRow("整列的底色", cur.strip, now.strip!!, allowTransparent = true) { v -> setQuick({ it.copy(strip = v) }) }

        // 按鈕圖片（九宮格）
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, px(6f), 0, 0) }
        head.addView(TextView(this).apply { text = "按鈕圖片"; textSize = 15f })
        head.addView(TextView(this).apply {
            text = if (hasFrame) "已設定（四個角不變形，按鈕變寬只拉長中間）" else "沒有（可以上傳一張框圖，例如雕花框）"
            textSize = 12f
            alpha = 0.65f
        })
        panel.addView(head)
        val frameRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun fb(s: String, onClick: () -> Unit) = frameRow.addView(Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(2f), px(3f), px(2f), px(3f)) })
        fb(if (hasFrame) "📷 換一張" else "📷 上傳圖片") {
            val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_QUICK_FRAME)
        }
        cur.frame?.let { f ->
            fb("📐 調整邊角") { openNineSlice(f) }
            fb("拿掉") { setQuick({ it.copy(frame = null) }) }
        }
        panel.addView(frameRow)

        colorRow("文字顏色", cur.text, now.text!!) { v -> setQuick({ it.copy(text = v) }) }
        greyIf(hasFrame) {
            colorRow("按鈕底色", cur.bg, now.bg!!, allowTransparent = true) { v -> setQuick({ it.copy(bg = v) }) }
            colorRow("外框顏色", cur.border, now.border!!, allowTransparent = true) { v -> setQuick({ it.copy(border = v) }) }
            borderEditor(
                style = cur.borderStyle, width = cur.borderWidth, inset = cur.borderInset,
                customized = cur.borderStyle != null || cur.borderWidth != null || cur.borderInset != null,
                hint = if (cur.border == null && kv.quickBarHasBorder() != true) "💡 快捷列按鈕現在沒有外框，先在上面選一個外框顏色，樣式和粗細才看得到。" else null,
                onStyle = { s -> setQuick({ it.copy(borderStyle = s) }) },
                onWidth = { w -> setQuick({ it.copy(borderWidth = w) }, rebuild = false) },
                onInset = { d -> setQuick({ it.copy(borderInset = d) }, rebuild = false) },
                onReset = { setQuick({ it.copy(borderStyle = null, borderWidth = null, borderInset = null) }) },
            )

            val radius = ((cur.radius ?: 0.3f) * 100).toInt()
            val radiusLabel = text("按鈕圓角：$radius%", 15f)
            radiusLabel.setPadding(0, px(14f), 0, 0)
            panel.addView(SeekBar(this).apply {
                max = 100
                progress = radius
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        radiusLabel.text = "按鈕圓角：$p%"
                        setQuick({ it.copy(radius = p / 100f) }, rebuild = false)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) { commitHistory() }
                })
            })
            text("改了按鈕底色、外框或圓角，按鈕會換成自訂的形狀。「整列的底色」透明＝跟原本一樣看得到鍵盤背景。", 12f, dim = true)
        }
        if (hasFrame) text("💡 按鈕底色、外框、圓角是灰的：現在用按鈕圖片，這些都交給圖片。", 12f, dim = true)
        if (styles.quick != null) button("全部改回主題原本的樣子") { setQuick({ null }) }
        button("← 回到選按鍵") { quickSelected = false; refresh() }
    }

    /** 調整九宮格的邊角（上傳完、按「調整邊角」都會打開） */
    private fun openNineSlice(f: NineFrame) {
        val bmp = java.io.File(Decals.dir(this), f.img).takeIf { Decals.safeName(f.img) && it.exists() }
            ?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
        if (bmp == null) {
            android.widget.Toast.makeText(this, "這張圖讀不出來，換一張試試", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        // 預覽的字：第一個不是箭頭的快捷符號（箭頭太小看不出來）
        val items = prefs.quickList().map { it.replace("|", "") }
        val label = items.firstOrNull { it.none { ch -> ch in "⇤⇥◀▶←→" } } ?: items.firstOrNull() ?: "()"
        val buttonW = kv.width.toFloat() / items.size.coerceAtLeast(1) - 6 * resources.displayMetrics.density
        NineSliceDialog.show(this, bmp, f, label, kv.quickBarColors().text ?: Color.BLACK, buttonW) { done ->
            if (done != null) setQuick({ it.copy(frame = done) })
        }
    }

    // ---------- 底圖（按鍵底圖、鍵盤背景圖） ----------

    /** 上傳的圖要給誰用（按鍵樣式的代號，或 KeyStyles.KEYBOARD） */
    private var pendingFill: String? = null

    /**
     * 一列：名稱＋狀態，下面一排按鈕：上傳（換一張）、調整裁切、拿掉
     * @param target 按鍵樣式的代號（單顆或整類），或 KeyStyles.KEYBOARD
     */
    private fun imageRow(target: String, label: String, fill: ImageFill?) {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(6f), 0, 0)
        }
        if (label.isNotEmpty()) head.addView(TextView(this).apply { text = label; textSize = 15f })
        head.addView(TextView(this).apply {
            text = when {
                fill == null -> "沒有（可以上傳照片或 GIF）"
                fill.isGif -> "已設定（GIF，會動）"
                else -> "已設定"
            }
            textSize = 12f
            alpha = 0.65f
        })
        panel.addView(head)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun b(s: String, onClick: () -> Unit) = row.addView(Button(this).apply {
            text = s
            isAllCaps = false
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(px(2f), px(3f), px(2f), px(3f)) })
        b(if (fill == null) "📷 上傳圖片" else "📷 換一張") {
            pendingFill = target
            val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_FILL)
        }
        if (fill != null) {
            if (fill.nine) b("📐 調整邊角") { openKeyNine(target, fill) }
            else b("✂️ 裁切") { openCrop(target, fill) }
            b("拿掉") { setFill(target, null) }
        }
        panel.addView(row)
        // 按鍵才有：圖片怎麼用（鋪滿裁切／照圖片的形狀／九宮格）
        if (fill != null && KeyStyles.baseOf(target) != KeyStyles.KEYBOARD) {
            text("圖片怎麼用", 14f).setPadding(0, px(6f), 0, 0)
            val modes = listOf(
                "鋪滿裁切（可以放大縮小、選要露出哪一塊）",
                "照圖片的形狀（去背 PNG 透明的地方就是按鍵外面）",
                "固定邊角（四個角不變形，按鍵變寬只拉長中間）",
            )
            val current = when { fill.nine -> 2; fill.shape -> 1; else -> 0 }
            val rg = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
            val ids = modes.map { m ->
                RadioButton(this).apply { id = View.generateViewId(); text = m; textSize = 14f }.also { rg.addView(it) }.id
            }
            rg.check(ids[current])
            rg.setOnCheckedChangeListener { _, id ->
                when (ids.indexOf(id)) {
                    0 -> setFill(target, fill.copy(shape = false, nine = false, zoom = 1f, cx = 0.5f, cy = 0.5f))
                    // 照圖片的形狀：整張放進按鍵裡，毛邊才不會被裁掉
                    1 -> setFill(target, fill.copy(shape = true, nine = false, zoom = ImageFill.FIT, cx = 0.5f, cy = 0.5f))
                    // 九宮格：切換過去就打開調整邊角
                    2 -> fill.copy(shape = false, nine = true).let { setFill(target, it); openKeyNine(target, it) }
                }
            }
            panel.addView(rg)
            if (fill.ownsShape) text("這個模式不畫底色和外框（形狀、邊框都交給圖片），按下去只有圖片本身會變暗。", 12f, dim = true)
        }
    }

    /** 按鍵的九宮格：調整邊角（預覽用這顆鍵實際的大小） */
    private fun openKeyNine(target: String, fill: ImageFill) {
        val bmp = java.io.File(Decals.dir(this), fill.img).takeIf { Decals.safeName(fill.img) && it.exists() }
            ?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
        if (bmp == null) {
            android.widget.Toast.makeText(this, "這張圖讀不出來，換一張試試", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val key = selected ?: return
        val st = (styles.map[target] ?: KeyStyle()).over(styles.resolve(key))
        val box = kv.keyImageFrame(key, st)?.first ?: return
        val label = key.label.ifEmpty { KeyStyles.nameOf(key) }
        val textColor = st.text ?: kv.sampleColors(key)?.text ?: Color.BLACK
        NineSliceDialog.show(
            this, bmp, NineFrame(fill.img, fill.sx, fill.sy), label, textColor,
            buttonW = box.width(), buttonH = box.height(),
            title = "調整按鍵圖片的邊角",
            caption = "預覽：這顆鍵實際的大小，和更窄、更寬的",
        ) { done ->
            if (done != null) setFill(target, fill.copy(nine = true, shape = false, sx = done.sx, sy = done.sy))
        }
    }

    private fun setFill(target: String, fill: ImageFill?) {
        styles = styles.with(target, (styles.map[target] ?: KeyStyle()).copy(bgImage = fill))
        KeyStyles.save(prefs, styles)
        kv.setKeyStylesLive(styles)
        zoom.invalidate()
        refresh()
    }

    /** 打開裁切畫面：框的比例跟那顆鍵（或整個鍵盤背景）一樣 */
    private fun openCrop(target: String, fill: ImageFill) {
        val bmp = java.io.File(Decals.dir(this), fill.img).takeIf { Decals.safeName(fill.img) && it.exists() }
            ?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
        if (bmp == null) {
            android.widget.Toast.makeText(this, "這張圖讀不出來，換一張試試", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val (frame, corner, title) = if (KeyStyles.baseOf(target) == KeyStyles.KEYBOARD) {
            Triple(kv.keyboardImageFrame(), 0f, "裁切鍵盤背景圖片")
        } else {
            val key = selected ?: return
            val st = (styles.map[target] ?: KeyStyle()).over(styles.resolve(key))
            val (box, rad) = kv.keyImageFrame(key, st) ?: return
            Triple(box, if (fill.shape) 0f else rad / minOf(box.width(), box.height()), "裁切" + (if (KeyStyles.baseOf(target).startsWith("@")) KeyStyles.Group.values().firstOrNull { it.id == KeyStyles.baseOf(target) }?.title ?: "" else KeyStyles.nameOf(key)) + "的底圖")
        }
        if (frame.isEmpty) return
        ImageCrop.show(this, bmp, title, frame.width() / frame.height(), corner, fill) { f ->
            if (f != null) setFill(target, f)
        }
    }

    /** 按鍵動畫用的圖：GIF 會照原檔留著（飛出來時會動） */
    private fun readImage(uri: android.net.Uri, maxPx: Int = Decals.MAX_PX): String? {
        var tooBigGif = false
        val name = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                tooBigGif = Decals.isGif(bytes) && bytes.size > Decals.MAX_GIF_BYTES
                if (bytes.size > 40 * 1024 * 1024) null else Decals.storeBytes(this, bytes, keepGif = true, maxPx = maxPx)
            }
        } catch (_: Exception) { null }
        when {
            name == null -> android.widget.Toast.makeText(this, "這個檔案讀不出來，換一張圖試試", android.widget.Toast.LENGTH_LONG).show()
            tooBigGif -> android.widget.Toast.makeText(this, "這個 GIF 太大了（超過 4MB），先用它的第一格（不會動）", android.widget.Toast.LENGTH_LONG).show()
        }
        return name
    }

    // ---------- 按鍵動畫 ----------

    private fun effectName(e: KeyEffect) = PressEffects.ALL.firstOrNull { it.first == e.kind }?.second?.substringBefore("（") ?: e.kind

    /** 正在調的那一份動畫設定（null = 單顆鍵跟全部按鍵一樣） */
    private fun currentEffect(): KeyEffect? =
        if (animMode == GLOBAL) KeyEffect(prefs.pressEffect, prefs.customEffect)
        else styles.map[currentId()]?.effect

    private fun currentCustom(): CustomEffect = currentEffect()?.custom ?: prefs.customEffect

    private fun setEffect(e: KeyEffect?) {
        if (animMode == GLOBAL) {
            if (e != null) {
                prefs.pressEffect = e.kind
                e.custom?.let { prefs.customEffect = it }
            }
            kv.reloadPrefs()
            refresh()
        } else {
            update({ it.copy(effect = e) })
        }
        playPreview()
    }

    /** 自訂動畫改了一個地方：存起來（rebuild = 要不要重畫面板） */
    private fun setCustom(c: CustomEffect, rebuild: Boolean = true) {
        if (animMode == GLOBAL) {
            prefs.pressEffect = "custom"
            prefs.customEffect = c
            kv.reloadPrefs()
            if (rebuild) refresh()
        } else {
            update({ it.copy(effect = KeyEffect("custom", c)) }, rebuild)
        }
    }

    /** 在要試的那顆鍵上放一次動畫 */
    private fun playPreview() {
        if (animMode == GLOBAL) {
            // 全部按鍵：在中間附近的鍵放（用全部按鍵共用的那一種，就算那顆鍵有自己的動畫）
            val key = kv.rows.getOrNull(1)?.getOrNull(4) ?: return
            kv.previewEffect(key, KeyEffect(prefs.pressEffect, prefs.customEffect))
        } else {
            selected?.let { kv.previewEffect(it) }
        }
        zoom.invalidate()
    }

    private fun animPanel() {
        val key = selected
        val global = animMode == GLOBAL
        if (global) kv.editHighlight = null
        val who = when {
            global -> "全部按鍵"
            groupScope && key != null -> KeyStyles.groupOf(key).title
            key != null -> KeyStyles.nameOf(key)
            else -> ""
        }
        text("按鍵動畫：$who", 18f, bold = true).setPadding(0, px(10f), 0, px(4f))
        if (!global) text("單獨設定的鍵會用自己的動畫，其他鍵照「全部按鍵」的設定。", 13f, dim = true)
        button("▶ 試按看看") { playPreview() }

        val cur = currentEffect()
        val options = (if (global) emptyList() else listOf("same" to "跟全部按鍵一樣")) + PressEffects.ALL
        val selKind = cur?.kind ?: "same"
        val rg = RadioGroup(this)
        for ((id, label) in options) {
            rg.addView(RadioButton(this).apply {
                this.id = View.generateViewId()
                text = label
                tag = id
                isChecked = id == selKind
            })
        }
        rg.setOnCheckedChangeListener { g, checked ->
            val kind = g.findViewById<View>(checked).tag as String
            setEffect(when (kind) {
                "same" -> null
                "custom" -> KeyEffect("custom", cur?.custom ?: prefs.customEffect)
                else -> KeyEffect(kind)
            })
        }

        if (cur?.kind == "custom") customEditor(cur.custom ?: CustomEffect())
        section("選一種動畫")
        panel.addView(rg)
        button(if (global) "← 回去" else "← 回到$who") { animMode = null; refresh() }
    }

    /** 自訂動畫的各種調整 */
    private fun customEditor(c: CustomEffect) {
        section("🎨 自訂動畫")

        // 動作
        text("怎麼動", 15f).setPadding(0, px(6f), 0, px(2f))
        chipRow(CustomEffect.MOTIONS.map { it.second }, CustomEffect.MOTIONS.indexOfFirst { it.first == c.motion }) { i ->
            setCustom(c.copy(motion = CustomEffect.MOTIONS[i].first)); playPreview()
        }

        // 飛出來的東西
        text("飛出來的東西", 15f).setPadding(0, px(12f), 0, px(2f))
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val thumbs = Decals.SHAPES.map { Decal.SHAPE + it.first } + listOfNotNull(c.particle.takeIf { c.isImage })
        val bmps = if (c.isImage) Decals.bitmaps(this, listOf(Decal("", "", c.particle))) else emptyMap()
        for (img in thumbs) {
            val d = Decal("", "", img, color = c.color)
            strip.addView(DecalThumb(this, d, bmps[d.fileName]).apply {
                bgColor = if (img == c.particle) 0x55FF8FA9 else 0x14888888
                setOnClickListener { setCustom(c.copy(particle = img)); playPreview() }
            }, LinearLayout.LayoutParams(px(52f), px(52f)).apply { marginEnd = px(6f) })
        }
        strip.addView(Button(this).apply {
            text = "📷 選圖片"
            isAllCaps = false
            setOnClickListener {
                val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_EFFECT_IMAGE)
            }
        })
        panel.addView(android.widget.HorizontalScrollView(this).apply { addView(strip) })
        text("可以選 GIF 動圖，飛出來的時候會一直動（4MB 以內）。" + if (c.isImage && c.fileName.endsWith(".gif")) "　目前用的是 GIF ✓" else "", 12f, dim = true)

        // 顏色（自己的圖不用顏色）
        if (!c.isImage) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(10f), 0, px(4f))
            }
            row.addView(TextView(this).apply { text = "顏色"; textSize = 15f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(CheckBox(this).apply {
                text = "彩虹 🌈"
                isChecked = c.rainbow
                setOnCheckedChangeListener { _, on -> setCustom(c.copy(rainbow = on)); playPreview() }
            })
            row.addView(View(this).apply {
                background = GradientDrawable().apply { cornerRadius = 10 * dp; setColor(c.color); setStroke(px(1.5f), 0x33000000) }
                alpha = if (c.rainbow) 0.35f else 1f
                setOnClickListener { ColorDialog.show(this@StudioActivity, "動畫顏色", c.color) { col -> setCustom(c.copy(color = col, rainbow = false)); playPreview() } }
            }, LinearLayout.LayoutParams(px(72f), px(40f)).apply { marginStart = px(8f) })
            panel.addView(row)
        }

        // 數量、大小、速度：拖的時候先存，放開才試播
        fun live(f: (CustomEffect) -> CustomEffect) = setCustom(f(currentCustom()), rebuild = false)
        slider("數量", c.count, 1, 15, { "$it 個" }, onDone = ::playPreview) { v -> live { it.copy(count = v) } }
        slider("大小", c.size.toInt(), 4, 40, { "$it" }, onDone = ::playPreview) { v -> live { it.copy(size = v.toFloat()) } }
        slider("速度", (c.speed * 10).toInt(), 4, 25, { String.format("%.1f 倍", it / 10f) }, onDone = ::playPreview) { v -> live { it.copy(speed = v / 10f) } }

        panel.addView(CheckBox(this).apply {
            text = "飛的時候會旋轉"
            isChecked = c.spin
            setOnCheckedChangeListener { _, on -> setCustom(c.copy(spin = on)); playPreview() }
        })
        panel.addView(CheckBox(this).apply {
            text = "按鍵也彈一下"
            isChecked = c.bounce
            setOnCheckedChangeListener { _, on -> setCustom(c.copy(bounce = on)); playPreview() }
        })
    }

    /** 一排可以選一個的按鈕（選到的比較亮） */
    private fun chipRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEachIndexed { i, l ->
            row.addView(Button(this).apply {
                text = (if (i == selected) "✓ " else "") + l
                isAllCaps = false
                alpha = if (i == selected) 1f else 0.55f
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(6f) })
        }
        panel.addView(android.widget.HorizontalScrollView(this).apply { addView(row) })
    }

    companion object {
        private const val REQ_IMAGE = 500
        /** 按鍵上的字當作可以選的東西時，id 的開頭 */
        private const val LABEL = "label:"
        private const val MAX_UNDO = 3
        private const val MENU_UNDO = 1
        private const val MENU_REDO = 2
        private const val MENU_SAVE = 3
        private const val MENU_SAVE_AS = 4
        /** 要編輯哪個主題（ThemeStore 的 id）；沒給就是正在用的主題 */
        const val EXTRA_THEME = "theme_id"
        /** true = 從設定打開的，離開時回設定 */
        const val EXTRA_FROM_SETTINGS = "from_settings"
        private const val REQ_EFFECT_IMAGE = 501
        private const val REQ_FILL = 502
        private const val REQ_QUICK_FRAME = 503
        private const val GLOBAL = "global"
        private const val KEY = "key"
        /** 工作室上面的分頁 */
        val TABS = listOf(
            "zh" to "注音", "en" to "英文", "sym" to "符號", "kao" to "顏文字",
            "emo" to "表情", "stk" to "我的貼圖", "clip" to "剪貼簿", "num" to "數字",
        )
    }

    private fun clearAllButton() {
        button("清除所有按鍵設定") {
            AlertDialog.Builder(this)
                .setMessage("要把「${KeyStyles.pageTitle(page)}」這一頁的所有按鍵都恢復成主題原本的樣子嗎？（其他頁、鍵盤背景圖片、上方列和快捷列的顏色會留著）")
                .setPositiveButton("全部清除") { _, _ ->
                    styles = styles.withMap(styles.map.filterKeys { KeyStyles.pageOf(it) != page || KeyStyles.baseOf(it) == KeyStyles.KEYBOARD })
                    KeyStyles.save(prefs, styles)
                    kv.reloadPrefs()
                    refresh()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 一列：名稱（下面小字寫是不是自訂的）、（透明）、色塊（顯示現在的顏色，點了選顏色）、還原
     * @param now 沒自訂時，這顆鍵現在畫面上的顏色
     * @param allowTransparent 多一顆「透明」（底色、外框用：等於拿掉）
     */
    private fun colorRow(label: String, value: Int?, now: Int, allowTransparent: Boolean = false, onPick: (Int?) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6f), 0, px(6f))
        }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(TextView(this).apply { text = label; textSize = 15f })
        val clear = value != null && Color.alpha(value) == 0
        names.addView(TextView(this).apply {
            text = when {
                value == null -> "主題原本的顏色"
                clear -> "透明（拿掉了）"
                else -> "已自訂"
            }
            textSize = 12f
            alpha = 0.65f
        })
        row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (allowTransparent && !clear) row.addView(Button(this).apply {
            text = "透明"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(px(10f), 0, px(10f), 0)
            setOnClickListener { onPick(Color.TRANSPARENT) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(6f) })
        val shown = value ?: now
        // 透明的話，選色器從「本來的顏色」開始
        val start = if (clear) now else shown
        val chip = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            text = if (clear) "透明" else ""
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(if (clear) 0x14888888 else shown)
                setStroke(px(1.5f), 0x33000000)
            }
            contentDescription = "$label：" + if (clear) "透明" else String.format("#%06X", 0xFFFFFF and shown)
            setOnClickListener { ColorDialog.show(this@StudioActivity, label, start or (0xFF shl 24)) { onPick(it) } }
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
