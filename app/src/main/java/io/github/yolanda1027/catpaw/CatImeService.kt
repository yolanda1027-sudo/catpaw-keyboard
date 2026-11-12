package io.github.yolanda1027.catpaw

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.os.Build
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import io.github.yolanda1027.catpaw.engine.Bpmf
import io.github.yolanda1027.catpaw.engine.Candidate
import io.github.yolanda1027.catpaw.engine.UserScores
import io.github.yolanda1027.catpaw.engine.ZhuyinDict
import io.github.yolanda1027.catpaw.engine.ZhuyinEngine
import java.io.File

/**
 * 輸入法本體：處理按鍵、把注音交給引擎轉成中文、管理選字列。
 */
class CatImeService : InputMethodService(), CatKeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private enum class Page { MAIN, SYMBOL, EMOJI, CLIP, NUMPAD }

    private lateinit var prefs: Prefs
    private lateinit var haptics: Haptics
    private var kv: CatKeyboardView? = null
    private val main = Handler(Looper.getMainLooper())

    private var chinese = true
    private var page = Page.MAIN
    private var shift = 0 // 0 = 小寫, 1 = 大寫一次, 2 = 大寫鎖定
    private var lastShiftTap = 0L

    /** 注音引擎（詞庫在背景載入，載好之前是 null） */
    private var engine: ZhuyinEngine? = null
    private var dict: ZhuyinDict? = null
    /** 詞庫還沒載好時先記下打的注音，載好再補打 */
    private val pending = StringBuilder()

    /** 符號鍵盤目前的分類和頁數 */
    private var symCat = 1
    private var symPage = 0
    /** 剪貼簿紀錄 */
    private lateinit var clips: ClipHistory
    private var clipPage = 0
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    /** 顏文字的小分類：0 = 最近使用、1 以後 = 貓咪、動物… */
    private var kaoTab = 1

    private var candidates: List<Candidate> = emptyList()
    /** 聯想詞（剛送出一個字之後，猜你接下來要打的） */
    private var associated: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.migrate()
        haptics = Haptics(this)
        chinese = prefs.chinese
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        Symbols.loadKaomoji(assets.open("kaomoji.txt").bufferedReader().use { it.readText() })
        clips = ClipHistory(File(filesDir, "clips.json"))
        getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener)
        // 讓鍵盤上方放注音小框框的那條區域是透明的
        window.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        loadDictionary()
    }

    /** true = 注音直接打在輸入框（舊方式）；false = 像 Google 注音，放在鍵盤上方 */
    private val inline get() = prefs.inlineComposing

    /**
     * 告訴系統：鍵盤真正的上緣在透明區域的下面。
     * 這樣後面的 App 不會被往上推太多，點透明區域也會點到後面的 App。
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val v = kv ?: return
        if (isFullscreenMode || !v.isShown) return
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        val top = loc[1] + v.floatH.toInt()
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
    }

    private fun loadDictionary() {
        Thread {
            val boosts = ZhuyinDict.parseBoosts(assets.open("boost.txt").bufferedReader().use { it.readText() })
            val d = assets.open("zhuyin.dict").use { ZhuyinDict.load(it, boosts) }
            val user = UserScores(File(filesDir, "user_scores.txt"))
            main.post {
                dict = d
                engine = ZhuyinEngine(d, user)
                if (pending.isNotEmpty()) {
                    val keys = pending.toString()
                    pending.setLength(0)
                    keys.forEach { typeZhuyin(it) }
                }
            }
        }.start()
    }

    override fun onDestroy() {
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            "chinese", "recent_symbols", "recent_kaomoji" -> Unit
            "clear_learning" -> reloadUserScores() // 設定頁按了「清除學習紀錄」
            "clear_clips" -> { // 設定頁按了「清除剪貼簿紀錄」
                File(filesDir, "clips.json").delete()
                clips = ClipHistory(File(filesDir, "clips.json"))
            }
            else -> kv?.reloadPrefs()
        }
    }

    private fun reloadUserScores() {
        val d = dict ?: return
        engine = ZhuyinEngine(d, UserScores(File(filesDir, "user_scores.txt")))
    }

    override fun onCreateInputView(): View {
        val v = CatKeyboardView(this, prefs, haptics)
        v.listener = this
        kv = v
        refresh()
        return v
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetComposing()
        shift = 0
        page = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            // 電話、驗證碼這類數字欄位：直接開數字鍵盤
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.NUMPAD
            else -> Page.MAIN
        }
        chinese = prefs.chinese && !isPassword(info)
        captureClipboard() // 鍵盤打開時順便看一下剛剛有沒有複製新東西
        kv?.reloadPrefs()
        refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.finishComposingText()
        resetComposing()
        super.onFinishInputView(finishingInput)
    }

    private fun resetComposing() {
        engine?.clear()
        pending.setLength(0)
        candidates = emptyList()
        associated = emptyList()
    }

    private val isComposing get() = (engine?.isEmpty == false) || pending.isNotEmpty()

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // 只有「直接打在輸入框」模式需要：注音不在輸入框裡時，游標移動不影響組字
        if (inline && isComposing && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // 你自己把游標點去別的地方 → 畫面上的字就這樣留著，不再組字
            currentInputConnection?.finishComposingText()
            resetComposing()
            refresh()
        }
    }

    private fun isPassword(info: EditorInfo): Boolean {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }

    private fun refresh() {
        val v = kv ?: return
        v.rows = when (page) {
            Page.MAIN -> if (chinese) Layouts.ZHUYIN else Layouts.ENGLISH
            Page.SYMBOL -> Symbols.rows(symbolItems(), Symbols.categories[symCat].wide)
            Page.EMOJI -> Layouts.EMOJI
            Page.CLIP -> Layouts.clipRows(clips.all(), clipPage)
            Page.NUMPAD -> Layouts.NUMPAD
        }
        if (page == Page.SYMBOL && symCat == Symbols.KAOMOJI) {
            v.symbolTabs = kaoTabs()
            v.symbolTab = kaoTab + 1
            v.symbolPageCount = symbolPageCount()
            v.symbolPage = symPage
        } else if (page == Page.SYMBOL) {
            v.symbolTabs = Symbols.categories.map { it.tab }
            v.symbolTab = symCat
            v.symbolPageCount = symbolPageCount()
            v.symbolPage = symPage
        } else if (page == Page.NUMPAD) {
            v.symbolTabs = listOf("數字")
            v.symbolTab = 0
            v.symbolPageCount = 1
            v.symbolPage = 0
        } else if (page == Page.CLIP) {
            v.symbolTabs = listOf("剪貼簿　點一下貼上，長按釘選")
            v.symbolTab = 0
            v.symbolPageCount = clipPageCount()
            v.symbolPage = clipPage
        } else {
            v.symbolTabs = null
        }
        v.isChinese = chinese
        v.shiftState = shift
        v.composing = if (inline) pending.toString() else ""
        v.candidates = if (candidates.isNotEmpty()) candidates.map { it.text } else associated
        val e = engine
        v.reading = when {
            inline -> ""
            e != null && !e.isEmpty -> e.readingText()
            else -> pending.toString()
        }
    }

    // ---------- 注音 ----------

    private fun typeZhuyin(c: Char) {
        val e = engine
        if (e == null) {
            pending.append(c)
            if (inline) currentInputConnection?.setComposingText(pending, 1)
            refresh()
            return
        }
        associated = emptyList()
        if (Bpmf.isTone(c)) {
            if (!e.typeTone(c.toString())) {
                commitText(c.toString()) // 前面沒有注音，就直接打出聲調符號
                return
            }
        } else {
            e.typeSymbol(c)
        }
        updateComposing()
    }

    /** 把引擎目前的狀態更新到編輯區和選字列 */
    private fun updateComposing() {
        val e = engine ?: return
        val ic = currentInputConnection
        if (e.isEmpty) {
            if (inline) {
                ic?.setComposingText("", 1)
                ic?.finishComposingText()
            }
            candidates = emptyList()
        } else {
            if (inline) ic?.setComposingText(e.composingText(), 1)
            candidates = e.candidates().take(MAX_CANDIDATES)
        }
        refresh()
    }

    /** 把正在組的字全部轉成中文送出 */
    private fun commitComposingAll() {
        val e = engine
        if (e != null && !e.isEmpty) {
            val text = e.commitAll()
            currentInputConnection?.commitText(text, 1)
            candidates = emptyList()
            showAssociated(text)
        } else if (pending.isNotEmpty()) {
            currentInputConnection?.commitText(pending, 1)
            pending.setLength(0)
        }
        refresh()
    }

    private fun showAssociated(committed: String) {
        val last = committed.lastOrNull()
        associated = if (last != null) dict?.associated(last) ?: emptyList() else emptyList()
    }

    override fun onCandidate(index: Int) {
        val ic = currentInputConnection ?: return
        val e = engine ?: return
        if (candidates.isNotEmpty()) {
            val c = candidates.getOrNull(index) ?: return
            val text = e.select(c)
            // 送出選的字（直接打在輸入框模式下，會換掉底線那段）；如果只選了前半段，後面的繼續組字
            ic.commitText(text, 1)
            if (e.isEmpty) {
                candidates = emptyList()
                showAssociated(text)
                refresh()
            } else {
                updateComposing()
            }
        } else {
            val text = associated.getOrNull(index) ?: return
            ic.commitText(text, 1)
            showAssociated(text)
            refresh()
        }
    }

    // ---------- 按鍵處理 ----------

    override fun onKey(key: Key, longPress: Boolean) {
        val ic = currentInputConnection ?: return
        if (longPress) {
            when (key.act) {
                Act.SPACE -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                Act.CLIP -> if (key.text.isNotEmpty()) {
                    clips.togglePin(key.text)
                    refresh()
                }
                else -> key.sub?.let { commitText(it) }
            }
            return
        }
        when (key.act) {
            Act.ZHUYIN -> typeZhuyin(key.text[0])
            Act.TEXT -> {
                if (page == Page.SYMBOL) {
                    if (symCat == Symbols.KAOMOJI) addRecentKaomoji(key.text) else addRecentSymbol(key.text)
                }
                val upper = !chinese && page == Page.MAIN && shift > 0
                commitText(if (upper) key.text.uppercase() else key.text)
                if (shift == 1 && page == Page.MAIN) {
                    shift = 0
                    refresh()
                }
            }
            Act.SPACE -> {
                val e = engine
                when {
                    // 正在打的字 + 空白 = 一聲
                    e != null && e.hasPartial -> { e.typeTone(""); updateComposing() }
                    isComposing -> commitComposingAll()
                    else -> commitText(" ")
                }
            }
            Act.ENTER -> if (isComposing) commitComposingAll() else {
                associated = emptyList()
                refresh()
                sendKeyChar('\n')
            }
            Act.BACKSPACE -> {
                val e = engine
                when {
                    e != null && !e.isEmpty -> { e.backspace(); updateComposing() }
                    pending.isNotEmpty() -> {
                        pending.setLength(pending.length - 1)
                        if (inline) ic.setComposingText(pending, 1)
                        refresh()
                    }
                    else -> {
                        if (associated.isNotEmpty()) { associated = emptyList(); refresh() }
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    }
                }
            }
            Act.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when (shift) {
                    0 -> 1
                    1 -> if (now - lastShiftTap < 400) 2 else 0
                    else -> 0
                }
                lastShiftTap = now
                refresh()
            }
            Act.TO_SYMBOL -> {
                if (page == Page.NUMPAD) { symCat = 1; symPage = 0 } // 從數字鍵盤按 @?#：回到常用符號
                page = Page.SYMBOL
                refresh()
            }
            Act.TO_NUMPAD -> { page = Page.NUMPAD; refresh() }
            Act.TO_EMOJI -> { page = Page.EMOJI; refresh() }
            Act.TO_MAIN -> { page = Page.MAIN; refresh() }
            Act.GAP -> Unit
            Act.CLIP -> if (key.text.isNotEmpty()) {
                commitText(key.text)
                page = Page.MAIN
                refresh()
            }
            Act.CLIP_CLEAR -> {
                clips.clearUnpinned()
                clipPage = 0
                refresh()
            }
        }
    }

    /** 送出一般文字（標點、英文、表情符號）：如果正在組字，先把組好的中文送出 */
    private fun commitText(t: String) {
        if (isComposing) commitComposingAll()
        associated = emptyList()
        currentInputConnection?.commitText(t, 1)
        refresh()
    }

    override fun onTabChinese() {
        if (!chinese || page != Page.MAIN) {
            chinese = true
            prefs.chinese = true
            page = Page.MAIN
            refresh()
        }
    }

    override fun onTabEnglish() {
        if (chinese || page != Page.MAIN) {
            if (isComposing) commitComposingAll()
            associated = emptyList()
            chinese = false
            prefs.chinese = false
            page = Page.MAIN
            shift = 0
            refresh()
        }
    }

    // ---------- 符號鍵盤 ----------

    private fun recentSymbols(): List<String> =
        prefs.recentSymbols.split('\u0001').filter { it.isNotEmpty() }

    private fun addRecentSymbol(s: String) {
        val list = listOf(s) + recentSymbols().filter { it != s }
        prefs.recentSymbols = list.take(Symbols.PER_PAGE).joinToString("\u0001")
    }

    private fun recentKaomoji(): List<String> =
        prefs.recentKaomoji.split('\u0001').filter { it.isNotEmpty() }

    private fun addRecentKaomoji(s: String) {
        val list = listOf(s) + recentKaomoji().filter { it != s }
        prefs.recentKaomoji = list.take(Symbols.PER_PAGE_WIDE).joinToString("\u0001")
    }

    /** 顏文字模式的分類列：◁ 回去、最近使用（時鐘）、各小分類 */
    private fun kaoTabs(): List<String?> = listOf("◁", null) + Symbols.kaomojiGroups.map { it.first }

    private fun kaomojiAll(): List<String> =
        if (kaoTab == 0) recentKaomoji() else Symbols.kaomojiGroups.getOrNull(kaoTab - 1)?.second ?: emptyList()

    private fun symbolItems(): List<String> = when (symCat) {
        Symbols.RECENT -> recentSymbols()
        Symbols.KAOMOJI -> kaomojiAll().chunked(Symbols.PER_PAGE_WIDE).getOrElse(symPage) { emptyList() }
        else -> Symbols.categories[symCat].pages.getOrElse(symPage) { emptyList() }
    }

    private fun symbolPageCount(): Int = when (symCat) {
        Symbols.RECENT -> 1
        Symbols.KAOMOJI -> ((kaomojiAll().size + Symbols.PER_PAGE_WIDE - 1) / Symbols.PER_PAGE_WIDE).coerceAtLeast(1)
        else -> Symbols.categories[symCat].pages.size
    }

    // ---------- 剪貼簿 ----------

    private fun clipPageCount() =
        ((clips.all().size + Layouts.CLIPS_PER_PAGE - 1) / Layouts.CLIPS_PER_PAGE).coerceAtLeast(1)

    /** 把系統剪貼簿目前的文字記下來（標成機密的不記，例如密碼管理 App 複製的） */
    private fun captureClipboard() {
        if (!prefs.clipboardHistory) return
        try {
            val cm = getSystemService(ClipboardManager::class.java) ?: return
            val clip = cm.primaryClip ?: return
            val desc = clip.description
            if (Build.VERSION.SDK_INT >= 33 &&
                desc.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            ) return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            clips.add(text)
            if (page == Page.CLIP) refresh()
        } catch (_: Exception) {
            // 沒有權限讀剪貼簿（例如鍵盤不是目前的輸入法）就算了
        }
    }

    override fun onClipboard() {
        if (isComposing) commitComposingAll()
        captureClipboard()
        page = Page.CLIP
        clipPage = 0
        refresh()
    }

    override fun onSymbolTab(index: Int) {
        if (page == Page.CLIP || page == Page.NUMPAD) return
        if (symCat == Symbols.KAOMOJI) {
            if (index == 0) symCat = 1 // ◁ 回到一般符號
            else kaoTab = index - 1
        } else {
            symCat = index
        }
        symPage = 0
        refresh()
    }

    override fun onSymbolSwipe(direction: Int) {
        if (page == Page.NUMPAD) return
        if (page == Page.CLIP) {
            val next = (clipPage + direction).coerceIn(0, clipPageCount() - 1)
            if (next != clipPage) { clipPage = next; haptics.perform(kv, prefs); refresh() }
            return
        }
        val next = (symPage + direction).coerceIn(0, symbolPageCount() - 1)
        if (next != symPage) {
            symPage = next
            haptics.perform(kv, prefs)
            refresh()
        }
    }

    override fun onHide() {
        requestHideSelf(0)
    }

    companion object {
        /** 選字列最多放幾個（很常見的音可能有上千個字，太多會拖慢） */
        private const val MAX_CANDIDATES = 400
    }
}
