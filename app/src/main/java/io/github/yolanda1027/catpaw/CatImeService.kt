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
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import io.github.yolanda1027.catpaw.engine.Bpmf
import io.github.yolanda1027.catpaw.engine.Candidate
import io.github.yolanda1027.catpaw.engine.EnglishDict
import io.github.yolanda1027.catpaw.engine.EnglishUser
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

    /** 英文建議字（字典在背景載入，載好之前是 null） */
    private var enDict: EnglishDict? = null
    private var enUser: EnglishUser? = null
    private var enSuggest: List<String> = emptyList()
    /** 正在打英文字（打了字母或按了刪除）：這時游標前面的字才拿來猜；按空白、標點、換頁就停 */
    private var enTyping = false
    /** 剛從建議點了一個字（後面自動加了空白）：接著打逗號、句號會把那個空白移到標點後面 */
    private var justPicked = false
    /** 這個輸入框可以顯示英文建議（密碼欄、App 說不要建議的欄位不行） */
    private var enAllowed = true
    /** 網址、Email 欄位：點建議字後面不加空白 */
    private var enNoSpace = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.migrate()
        haptics = Haptics(this)
        chinese = prefs.chinese
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        Symbols.loadKaomoji(assets.open("kaomoji.txt").bufferedReader().use { it.readText() })
        Emojis.load(assets.open("emoji.txt").bufferedReader().use { it.readText() })
        clips = ClipHistory.open(this)
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
            ChineseConvert.ensure(this) // 簡繁對照表也先讀好，切換時才不會卡一下
            main.post {
                dict = d
                engine = ZhuyinEngine(d, user).also { it.learning = !noLearning }
                if (pending.isNotEmpty()) {
                    val keys = pending.toString()
                    pending.setLength(0)
                    keys.forEach { typeZhuyin(it) }
                }
            }
        }.start()
        // 英文字典另外載，不拖慢注音
        Thread {
            val en = try { assets.open(EN_WORDS).use { EnglishDict.load(it) } } catch (_: Exception) { null }
            val user = EnglishUser(File(filesDir, EN_USER_FILE))
            main.post {
                enDict = en
                enUser = user.also { it.paused = noLearning }
            }
        }.start()
    }

    override fun onDestroy() {
        enUser?.save()
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            "chinese", "recent_symbols", "recent_kaomoji", "recent_emoji", "pinned_symbols", "pinned_emoji" -> Unit
            "simplified" -> refresh() // 簡繁切換：選字列馬上換
            "my_stickers_ver", "my_stickers" -> refresh() // 設定裡加了／刪了貼圖、開關分類
            "quick_bar", "quick_items", "quick_config" -> refresh()
            "clear_learning" -> { // 設定頁按了「清除學習紀錄」
                reloadUserScores()
                enUser = EnglishUser(File(filesDir, EN_USER_FILE)).also { it.paused = noLearning }
            }
            "en_suggest" -> updateEnglish()
            "bottom_pad_dp" -> kv?.requestApplyInsets() // 設定裡調了「鍵盤最下面多留空間」
            "clear_clips" -> { // 設定頁按了「清除剪貼簿紀錄」
                File(filesDir, "clips.json").delete()
                clips = ClipHistory.open(this)
            }
            "clips_changed", "clip_keep_hours" -> { // 設定頁排了釘選的順序、解除釘選、刪除、改了留存時間：重新讀
                clips = ClipHistory.open(this)
                if (page == Page.CLIP) refresh()
            }
            else -> kv?.reloadPrefs()
        }
    }

    private fun reloadUserScores() {
        val d = dict ?: return
        engine = ZhuyinEngine(d, UserScores(File(filesDir, "user_scores.txt"))).also { it.learning = !noLearning }
    }

    override fun onCreateInputView(): View {
        val v = CatKeyboardView(this, prefs, haptics)
        v.listener = this
        // 系統導覽列（手勢條、三顆按鈕）蓋在鍵盤最下面：按鍵往上讓出它的高度。
        // 用「不管現在有沒有顯示」的高度：導覽列一閃而過地藏起來時，按鍵才不會掉到它後面
        v.setOnApplyWindowInsetsListener { view, insets ->
            (view as CatKeyboardView).navInset = bottomInset(insets)
            insets
        }
        setupNavBar(v)
        kv = v
        refresh()
        return v
    }

    /**
     * 鍵盤最下面要讓出多高：
     * - 導覽列（三顆按鈕、手勢條），用「不管現在有沒有顯示」的高度，閃一下藏起來時按鍵才不會掉到後面
     * - 系統畫在鍵盤底下、可以點的按鈕（手勢提示條關掉時的「切換輸入法」「收起鍵盤」）
     * 取比較高的那個，再加上設定裡手動多留的空間。
     */
    internal fun bottomInset(insets: android.view.WindowInsets): Int {
        val nav = insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom
        val tap = maxOf(
            insets.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom,
            insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.tappableElement()).bottom,
        )
        return maxOf(nav, tap) + (prefs.bottomPadDp * resources.displayMetrics.density).toInt()
    }

    /**
     * 導覽列那一條的顏色。Android 15 以後系統不幫鍵盤畫導覽列的底色了，
     * 只在三顆按鈕底下蓋一層灰灰的半透明遮罩（深色、淺色 App 都一樣醜）。
     * 這裡把遮罩關掉，改成鍵盤自己塗上系統原本的導覽列顏色，按鈕圖示的深淺也跟著配。
     */
    private fun setupNavBar(v: CatKeyboardView) {
        val color = systemNavBarColor()
        v.systemNavColor = color
        val w = window.window ?: return
        w.isNavigationBarContrastEnforced = false
        val light = Color.luminance(color) > 0.5f
        w.insetsController?.setSystemBarsAppearance(
            if (light) android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    /** 系統（含手機主題）原本給導覽列的顏色；讀不到或怪怪的，就用三星預設的灰白／深色 */
    internal fun systemNavBarColor(): Int {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val fromTheme = try {
            val ctx = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_DayNight)
            val a = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.navigationBarColor))
            try { a.getColor(0, 0) } finally { a.recycle() }
        } catch (_: Exception) { 0 }
        // 主題給的要是不透明、而且深淺跟現在的模式對得上才用（有些主題給黑色或透明）
        val ok = Color.alpha(fromTheme) == 255 && (Color.luminance(fromTheme) > 0.5f) != night
        return if (ok) fromTheme else if (night) NAV_DARK else NAV_LIGHT
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        kv?.requestApplyInsets() // 換 App 時重新問一次導覽列高度
        resetComposing()
        shift = 0
        page = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            // 電話、驗證碼這類數字欄位：直接開數字鍵盤
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.NUMPAD
            else -> Page.MAIN
        }
        chinese = prefs.chinese && !isPassword(info)
        // 輸入框說「不要記住打的字」（瀏覽器無痕模式等）：這次不學新的選字
        noLearning = isPassword(info) || (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        engine?.learning = !noLearning
        enUser?.paused = noLearning
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        enAllowed = cls == InputType.TYPE_CLASS_TEXT && !isPassword(info) &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        enNoSpace = variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        captureClipboard() // 鍵盤打開時順便看一下剛剛有沒有複製新東西
        kv?.reloadPrefs()
        refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.finishComposingText()
        resetComposing()
        enUser?.save()
        super.onFinishInputView(finishingInput)
    }

    private fun resetComposing() {
        engine?.clear()
        pending.setLength(0)
        candidates = emptyList()
        associated = emptyList()
        enSuggest = emptyList()
        enTyping = false
        justPicked = false
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
        // 英文：每次輸入框的字或游標變了，重新猜游標前面那個字（選取了一段字就不猜）
        if (enTyping || enSuggest.isNotEmpty()) updateEnglish(newSelStart == newSelEnd)
    }

    // ---------- 英文建議字 ----------

    /** 看游標前面正在打的英文字，更新選字列上的建議 */
    private fun updateEnglish(collapsed: Boolean = true) {
        val d = enDict
        val ic = currentInputConnection
        val list = if (!enTyping || chinese || page != Page.MAIN || !enAllowed || !prefs.enSuggest || !collapsed || d == null || ic == null) {
            emptyList()
        } else {
            val w = EnglishDict.currentWord(ic.getTextBeforeCursor(48, 0))
            // 游標在一個字的中間（後面還接著字母）：不猜
            val after = if (w.isEmpty()) null else ic.getTextAfterCursor(1, 0)?.firstOrNull()
            if (w.isEmpty() || (after != null && (EnglishDict.isWordChar(after) || after.isDigit()))) emptyList()
            else d.suggest(w, enUser)
        }
        if (list != enSuggest) {
            enSuggest = list
            refresh()
        }
    }

    /** 點了英文建議字：換掉正在打的那個字，後面加空白 */
    private fun pickEnglish(index: Int) {
        val ic = currentInputConnection ?: return
        val word = enSuggest.getOrNull(index) ?: return
        val typed = EnglishDict.currentWord(ic.getTextBeforeCursor(48, 0))
        ic.beginBatchEdit()
        if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText(if (enNoSpace) word else "$word ", 1)
        ic.endBatchEdit()
        if (enAllowed) enUser?.learn(word)
        justPicked = !enNoSpace
        enTyping = enNoSpace // 網址欄沒加空白：還在同一個字上，繼續猜
        enSuggest = emptyList()
        if (shift == 1) shift = 0
        refresh()
    }

    /** 打完一個英文字（接著打空白、標點、Enter）：記一次，下次排前面 */
    private fun learnEnglishWord() {
        if (chinese || !enAllowed) return
        val u = enUser ?: return
        val w = EnglishDict.currentWord(currentInputConnection?.getTextBeforeCursor(48, 0))
        if (w.isNotEmpty()) u.learn(w)
    }

    /** 點了建議字（後面自動有空白）接著打標點：變成「hello, 」而不是「hello ,」 */
    private fun punctAfterPick(t: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (ic.getTextBeforeCursor(1, 0)?.toString() == " ") ic.deleteSurroundingText(1, 0)
        ic.commitText("$t ", 1)
        ic.endBatchEdit()
        associated = emptyList()
        refresh()
    }

    /** 目前的輸入框不要學習（密碼欄、無痕模式） */
    internal var noLearning = false
        private set

    private fun isPassword(info: EditorInfo): Boolean {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }

    /** 現在這一頁在主題裡的代號（見 KeyStyles.PAGES） */
    private fun pageScope(): String = when (page) {
        Page.MAIN -> if (chinese) "" else "en"
        Page.SYMBOL -> if (symCat == Symbols.KAOMOJI) "kao" else "sym"
        Page.EMOJI -> if (emojiCat == MINE) "stk" else "emo"
        Page.CLIP -> "clip"
        Page.NUMPAD -> "num"
    }

    private fun refresh() {
        val v = kv ?: return
        // 正在看「我的貼圖」時把它關掉：跳到第一個表情分類
        if (emojiCat == MINE && !prefs.myStickers) { emojiCat = MINE + 1; emojiPage = 0 }
        // 每一頁有自己的設計（主題工作室分頁設計）：按鍵標上是哪一頁
        val scope = pageScope()
        v.pageScope = scope
        v.rows = when (page) {
            Page.MAIN -> if (chinese) Layouts.ZHUYIN else Layouts.ENGLISH
            Page.SYMBOL -> Symbols.rows(symbolItems(), Symbols.categories[symCat].wide)
            Page.EMOJI -> if (emojiCat == MINE) MyStickers.rows(this, emojiPage) else Emojis.rows(Emojis.page(emojiItems(), emojiPage))
            Page.CLIP -> Layouts.clipRows(clips.all(), clipPage)
            Page.NUMPAD -> Layouts.NUMPAD
        }.onPage(scope)
        // 快捷符號列：只在注音、英文鍵盤顯示
        v.quickItems = if (prefs.quickBar && page == Page.MAIN) prefs.quickList() else emptyList()
        // 最近使用的符號、表情：長按可以釘選、換位置；釘選的畫小圖釘
        v.itemActions = { k -> itemActionsFor(k) }
        v.isPinned = { k -> recentKind()?.let { kind -> k.act == Act.TEXT && k.text in pinned(kind) } == true }
        // 表情符號：滑到一個分類的最後一頁，還可以繼續滑到下一個分類
        // 剪貼簿：上下滑換頁（像一份清單）；其他頁左右滑
        v.verticalPaging = page == Page.CLIP
        v.morePagesBefore = page == Page.EMOJI && emojiCat > firstEmojiCat()
        v.morePagesAfter = page == Page.EMOJI && emojiCat >= firstEmojiCat() && emojiCat < Emojis.groups.size + 1
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
        } else if (page == Page.EMOJI) {
            // 分類列：🕒 最近使用、🖼️ 我的貼圖（可以關掉）、然後是各分類
            val mine = if (prefs.myStickers) listOf<String?>("🖼️") else emptyList()
            v.symbolTabs = listOf<String?>(null) + mine + Emojis.groups.map { it.first }
            v.symbolTab = emojiTabOf(emojiCat)
            v.symbolPageCount = emojiPageCount()
            v.symbolPage = emojiPage
        } else if (page == Page.NUMPAD) {
            v.symbolTabs = listOf("數字")
            v.symbolTab = 0
            v.symbolPageCount = 1
            v.symbolPage = 0
        } else if (page == Page.CLIP) {
            v.symbolTabs = listOf(CLIP_TAB)
            v.symbolTab = 0
            v.symbolPageCount = clipPageCount()
            v.symbolPage = clipPage
        } else {
            v.symbolTabs = null
        }
        v.isChinese = chinese
        v.shiftState = shift
        v.composing = if (inline) pending.toString() else ""
        // 簡體模式：選字列顯示簡體；兩個繁體轉成同一個簡體（例如「髮」「發」→「发」）只留第一個
        val en = !chinese && page == Page.MAIN && candidates.isEmpty() && enSuggest.isNotEmpty()
        val src = if (candidates.isNotEmpty()) candidates.map { it.text } else if (en) enSuggest else associated
        if (prefs.simplified && !en) {
            val (shown, index) = ChineseConvert.dedupe(src, ::conv)
            v.candidates = shown
            shownIndex = index
        } else {
            v.candidates = src
            shownIndex = src.indices.toList()
        }
        val e = engine
        v.reading = when {
            inline -> ""
            e != null && !e.isEmpty -> conv(e.readingText())
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
            if (inline) ic?.setComposingText(conv(e.composingText()), 1)
            candidates = e.candidates().take(MAX_CANDIDATES)
        }
        refresh()
    }

    /** 把正在組的字全部轉成中文送出 */
    private fun commitComposingAll() {
        val e = engine
        if (e != null && !e.isEmpty) {
            val text = e.commitAll()
            currentInputConnection?.commitText(conv(text), 1)
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

    /** 選字列上顯示的第幾個 → 原本清單的第幾個（簡體模式會合併重複的） */
    private var shownIndex: List<Int> = emptyList()

    /** 簡體模式就轉成簡體（只在顯示、送出的時候轉；選字、學習都還是用繁體） */
    private fun conv(s: String) = if (prefs.simplified) ChineseConvert.toSimplified(this, s) else s

    // ---------- 最近使用：釘選、換位置 ----------

    private fun pinned(kind: String): List<String> =
        (if (kind == SYM) prefs.pinnedSymbols else prefs.pinnedEmoji).split('\u0001').filter { it.isNotEmpty() }

    private fun setPinned(kind: String, list: List<String>) {
        val v = list.joinToString("\u0001")
        if (kind == SYM) prefs.pinnedSymbols = v else prefs.pinnedEmoji = v
    }

    private fun recentShown(pins: List<String>, recent: List<String>, perPage: Int) = Recents.shown(pins, recent, perPage)

    /** 現在在哪一種「最近使用」頁（符號 or 表情）；不在的話是 null */
    private fun recentKind(): String? = when {
        page == Page.SYMBOL && symCat == Symbols.RECENT -> SYM
        page == Page.EMOJI && emojiCat == 0 -> EMO
        else -> null
    }

    /**
     * 長按最近使用的東西、剪貼簿：跳出的選單。
     * 一開始選的是「取消」（直接放開＝什麼都不做，不小心長按到也沒關係），手指滑過去選其他的。
     * 「取消」都放中間：靠左、靠右長按，兩邊的選項都滑得到。
     */
    private fun itemActionsFor(key: Key): List<Pair<String, String>>? {
        // 剪貼簿：排順序到設定裡用 ↑ ↓ 排（長按選單裡的「排順序」直接打開那一頁）
        if (page == Page.CLIP && key.act == Act.CLIP && key.text.isNotEmpty()) {
            val pinned = clips.all().any { it.text == key.text && it.pinned }
            // 釘選的不放「刪除」：要刪先解除釘選，比較不會手滑刪掉重要的
            return if (pinned) listOf("unpin" to "解除釘選", CANCEL, "sort" to "排順序")
            else listOf("pin" to "釘選", CANCEL, "remove" to "刪除")
        }
        val kind = recentKind() ?: return null
        if (key.act != Act.TEXT || key.text.isEmpty()) return null
        // 「取消」放中間：往前在左、往後在右
        return if (key.text in pinned(kind)) listOf("unpin" to "解除釘選", "left" to "往前", CANCEL, "right" to "往後", "remove" to "刪除")
        else listOf("pin" to "釘選", CANCEL, "remove" to "刪除")
    }

    override fun onItemAction(key: Key, action: String) {
        if (action == CANCEL.first) return
        if (page == Page.CLIP && key.act == Act.CLIP && action == "sort") {
            openSettingsPage("clip")
            return
        }
        if (page == Page.CLIP && key.act == Act.CLIP) {
            clips.action(action, key.text)
            // 這一頁刪光了：退回上一頁
            clipPage = clipPage.coerceAtMost(clipPageCount() - 1)
            refresh()
            return
        }
        val kind = recentKind() ?: return
        val max = if (kind == SYM) Symbols.PER_PAGE else Emojis.PER_PAGE
        val recent = if (kind == SYM) recentSymbols() else recentEmoji()
        val res = Recents.apply(action, key.text, pinned(kind), recent, max)
        if (res == null) {
            android.widget.Toast.makeText(this, "釘選的已經滿一頁了，先取消一些再釘", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        setPinned(kind, res.first)
        val r = res.second.joinToString("\u0001")
        if (kind == SYM) prefs.recentSymbols = r else prefs.recentEmoji = r
        refresh()
    }

    override fun onToggleSimplified() {
        prefs.simplified = !prefs.simplified
        refresh()
    }

    override fun onCandidate(shown: Int) {
        val ic = currentInputConnection ?: return
        val index = shownIndex.getOrNull(shown) ?: shown
        if (!chinese && candidates.isEmpty() && enSuggest.isNotEmpty()) {
            pickEnglish(index)
            return
        }
        justPicked = false
        val e = engine ?: return
        if (candidates.isNotEmpty()) {
            val c = candidates.getOrNull(index) ?: return
            val text = e.select(c)
            // 全部選完才送出（直接打在輸入框模式下，會換掉底線那段）；只選了前半段的話，選好的字先留在組字區
            if (text.isNotEmpty()) ic.commitText(conv(text), 1)
            if (e.isEmpty) {
                candidates = emptyList()
                showAssociated(text)
                refresh()
            } else {
                updateComposing()
            }
        } else {
            val text = associated.getOrNull(index) ?: return
            ic.commitText(conv(text), 1)
            showAssociated(text)
            refresh()
        }
    }

    // ---------- 按鍵處理 ----------

    override fun onKey(key: Key, longPress: Boolean) {
        val ic = currentInputConnection ?: return
        val afterPick = justPicked
        justPicked = false
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
                if (page == Page.EMOJI) addRecentEmoji(key.text)
                val upper = !key.exact && !chinese && page == Page.MAIN && shift > 0
                val t = if (upper) key.text.uppercase() else key.text
                if (afterPick && !chinese && t in PUNCT_AFTER_WORD) {
                    punctAfterPick(t)
                } else {
                    // 英文模式打字母：開始猜這個字
                    if (!chinese && page == Page.MAIN && t.length == 1 && EnglishDict.isWordChar(t[0])) enTyping = true
                    commitText(t)
                    if (enTyping) updateEnglish()
                }
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
                learnEnglishWord()
                enTyping = false
                enSuggest = emptyList()
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
                    // 有聯想詞的時候：刪除鍵只把聯想詞關掉，不刪字
                    associated.isNotEmpty() -> { associated = emptyList(); refresh() }
                    else -> {
                        // 英文：刪回前一個字也會出現建議（游標更新後在 onUpdateSelection 猜）
                        if (!chinese && page == Page.MAIN) enTyping = true
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
                // 每次進符號鍵盤都從「1?#」第一頁開始（數字、常用標點最順手）；最近使用在最左邊的時鐘
                symCat = 1
                symPage = 0
                kaoTab = 1
                page = Page.SYMBOL
                refresh()
            }
            Act.TO_NUMPAD -> { page = Page.NUMPAD; refresh() }
            Act.TO_EMOJI -> {
                page = Page.EMOJI
                // 有用過的就先開「最近使用」，沒有的話開第一個分類
                emojiCat = if (recentEmoji().isEmpty() && pinned(EMO).isEmpty()) MINE + 1 else 0
                emojiPage = 0
                refresh()
            }
            Act.TO_MAIN -> { page = Page.MAIN; refresh(); updateEnglish() }
            Act.STICKER -> sendSticker(key.text)
            Act.OPEN_STICKERS -> openStickerSettings()
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
        // 英文字打完了（接著空白、標點、表情）：記下這個字，停止猜
        if (!chinese && t.isNotEmpty() && !EnglishDict.isWordChar(t[0])) {
            learnEnglishWord()
            enTyping = false
            enSuggest = emptyList()
        }
        associated = emptyList()
        currentInputConnection?.commitText(t, 1)
        refresh()
    }

    override fun onTabChinese() {
        if (!chinese || page != Page.MAIN) {
            chinese = true
            prefs.chinese = true
            page = Page.MAIN
            enTyping = false
            enSuggest = emptyList()
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
        Symbols.RECENT -> recentShown(pinned(SYM), recentSymbols(), Symbols.PER_PAGE)
        Symbols.KAOMOJI -> kaomojiAll().chunked(Symbols.PER_PAGE_WIDE).getOrElse(symPage) { emptyList() }
        else -> Symbols.categories[symCat].pages.getOrElse(symPage) { emptyList() }
    }

    private fun symbolPageCount(): Int = when (symCat) {
        Symbols.RECENT -> 1
        Symbols.KAOMOJI -> ((kaomojiAll().size + Symbols.PER_PAGE_WIDE - 1) / Symbols.PER_PAGE_WIDE).coerceAtLeast(1)
        else -> Symbols.categories[symCat].pages.size
    }

    // ---------- 表情符號 ----------

    /** 表情符號的分類：0 = 最近使用，1 = 我的貼圖（MINE），2 之後是 Emojis.groups 的順序 */
    private var emojiCat = 1

    /** 可以左右滑接過去的第一個分類（我的貼圖關掉的話就從第一個表情分類開始） */
    private fun firstEmojiCat() = if (prefs.myStickers) MINE else MINE + 1
    /** 分類列上的第幾格 ↔ emojiCat（我的貼圖關掉時，那一格不在分類列上） */
    private fun emojiTabOf(cat: Int) = if (prefs.myStickers || cat < MINE) cat else cat - 1
    private fun emojiCatOf(tab: Int) = if (prefs.myStickers || tab < MINE) tab else tab + 1
    private var emojiPage = 0

    private fun recentEmoji(): List<String> = prefs.recentEmoji.split('\u0001').filter { it.isNotEmpty() }

    private fun addRecentEmoji(s: String) {
        val list = listOf(s) + recentEmoji().filter { it != s }
        prefs.recentEmoji = list.take(Emojis.PER_PAGE).joinToString("\u0001")
    }

    private fun emojiItems(): List<String> =
        when {
            emojiCat == 0 -> recentShown(pinned(EMO), recentEmoji(), Emojis.PER_PAGE)
            emojiCat == MINE -> emptyList()
            else -> Emojis.groups.getOrNull(emojiCat - MINE - 1)?.second ?: emptyList()
        }

    private fun emojiPageCount() = if (emojiCat == MINE) MyStickers.pageCount(this) else Emojis.pageCount(emojiItems())

    // ---------- 我的貼圖 ----------

    /**
     * 送出一張自己的貼圖：輸入框的 App 說它收圖片的話，直接把圖片放進去；
     * 不收的話複製到剪貼簿，讓使用者長按輸入框「貼上」（很多聊天 App 貼得上圖片）。
     */
    private fun sendSticker(name: String) {
        val f = MyStickers.file(this, name) ?: return
        val mime = MyStickers.mime(f.name)
        val uri = StickerProvider.uri(this, f.name)
        val accepts = currentInputEditorInfo?.contentMimeTypes?.any { ClipDescription.compareMimeTypes(mime, it) } == true
        if (accepts) {
            try {
                val info = android.view.inputmethod.InputContentInfo(uri, ClipDescription("貓爪貼圖", arrayOf(mime)), null)
                val ok = currentInputConnection?.commitContent(info, android.view.inputmethod.InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null) == true
                if (ok) return
            } catch (_: Exception) {
            }
        }
        try {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newUri(contentResolver, "貓爪貼圖", uri))
            android.widget.Toast.makeText(this, "這裡不能直接放圖片，已經複製好了：長按輸入框選「貼上」", android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "這個地方沒辦法放圖片", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** 開設定的「我的貼圖」頁（上傳、刪除） */
    private fun openStickerSettings() = openSettingsPage("mystickers")

    /** 從鍵盤直接打開設定的某一頁（上傳貼圖、剪貼簿排順序） */
    private fun openSettingsPage(page: String) {
        requestHideSelf(0)
        val i = android.content.Intent(this, SettingsActivity::class.java)
            .putExtra("page", page)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(i)
    }

    // ---------- 剪貼簿 ----------

    private fun clipPageCount() =
        ((clips.all().size + Layouts.CLIPS_PER_PAGE - 1) / Layouts.CLIPS_PER_PAGE).coerceAtLeast(1)

    /**
     * 在無痕模式／密碼欄裡看到的剪貼簿內容：記住它，之後回到一般輸入框也不補記
     * （複製的東西會一直留在系統剪貼簿，不擋的話換個 App 打開鍵盤就被記進去了）
     */
    private var skipClip: String? = null

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
            // 圖片（例如自己的貼圖複製到剪貼簿）不記：記下來只會是一串看不懂的網址
            if (clip.getItemAt(0).uri != null && clip.getItemAt(0).text == null) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (noLearning) { skipClip = text; return }
            if (text == skipClip) return
            clips.add(text)
            if (page == Page.CLIP) refresh()
        } catch (_: Exception) {
            // 沒有權限讀剪貼簿（例如鍵盤不是目前的輸入法）就算了
        }
    }

    // ---------- 快捷符號列 ----------

    override fun onQuick(item: String) {
        val ic = currentInputConnection ?: return
        justPicked = false
        if (isComposing) commitComposingAll()
        if (associated.isNotEmpty()) { associated = emptyList(); refresh() }
        when (item) {
            "⇤" -> moveCursorToEdge(start = true)
            "⇥" -> moveCursorToEdge(start = false)
            "◀" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            "▶" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            else -> {
                val (open, close) = splitQuick(item)
                // 有選取文字的話，就把它包起來
                val selected = ic.getSelectedText(0)?.toString() ?: ""
                ic.beginBatchEdit()
                ic.commitText(open + selected, 1)
                // newCursorPosition = 0：游標停在這段文字的前面，也就是兩個符號中間
                if (close.isNotEmpty()) ic.commitText(close, 0)
                ic.endBatchEdit()
            }
        }
    }

    /**
     * 「(|)」→ ("(", ")")：| 是游標位置
     * 「()」兩個字 → 游標放中間
     * 其他 → 整個打出來，游標在最後
     */
    private fun splitQuick(item: String): Pair<String, String> {
        val bar = item.indexOf('|')
        if (bar >= 0) return item.substring(0, bar) to item.substring(bar + 1)
        if (item.codePointCount(0, item.length) == 2) {
            val mid = item.offsetByCodePoints(0, 1)
            return item.substring(0, mid) to item.substring(mid)
        }
        return item to ""
    }

    private fun moveCursorToEdge(start: Boolean) {
        val ic = currentInputConnection ?: return
        val et = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (et?.text != null) {
            val pos = if (start) 0 else et.startOffset + et.text.length
            ic.setSelection(pos, pos)
        } else {
            // 有些 App 不給讀全文：改用 Ctrl+Home／Ctrl+End
            val code = if (start) KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END
            val now = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_CTRL_ON))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, KeyEvent.META_CTRL_ON))
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
        if (page == Page.EMOJI) {
            emojiCat = emojiCatOf(index)
            emojiPage = 0
            refresh()
            return
        }
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
        if (page == Page.EMOJI) {
            // 一個分類滑到底就接到下一個分類（像 Google 的表情鍵盤）
            val count = emojiPageCount()
            when {
                emojiPage + direction in 0 until count -> emojiPage += direction
                direction > 0 && emojiCat >= firstEmojiCat() && emojiCat < Emojis.groups.size + 1 -> { emojiCat++; emojiPage = 0 }
                direction < 0 && emojiCat > firstEmojiCat() -> { emojiCat--; emojiPage = emojiPageCount() - 1 }
                else -> return
            }
            haptics.perform(kv, prefs)
            refresh()
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
        /** 舊版（系統還會幫鍵盤畫導覽列時）在三星手機上量到的導覽列顏色 */
        const val NAV_LIGHT = 0xFFF1F1F3.toInt()
        const val NAV_DARK = 0xFF000000.toInt()
        private const val SYM = "sym"
        /** 表情鍵盤裡「我的貼圖」那一格 */
        private const val MINE = 1
        private const val EMO = "emo"

        /** 選字列最多放幾個（很常見的音可能有上千個字，太多會拖慢） */
        private const val MAX_CANDIDATES = 400
        /** 長按選單的「取消」：放在第一個，直接放開就是它 */
        private val CANCEL = "cancel" to "取消"
        /** 剪貼簿頁最上面那條的說明 */
        const val CLIP_TAB = "剪貼簿　點一下貼上，長按釘選、刪除、排順序"
        private const val EN_WORDS = "en_words.txt"
        /** 英文學過的字（設定裡「清除學習紀錄」會一起刪） */
        const val EN_USER_FILE = "en_user.txt"
        /** 點了建議字之後打這些標點：空白移到標點後面 */
        private val PUNCT_AFTER_WORD = setOf(",", ".", "!", "?", ";", ":")
    }
}
