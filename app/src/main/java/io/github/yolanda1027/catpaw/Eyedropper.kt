package io.github.yolanda1027.catpaw

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 汲色器（滴管）：
 * - 從畫面汲色：把目前的編輯畫面拍下來（不含選色視窗本身）
 * - 從圖片汲色：挑一張圖，只讀進記憶體用一次，不會存到任何地方
 * 兩種都會打開同一個全螢幕畫面：手指拖到想要的顏色，放大鏡會顯示那一格。
 */
object Eyedropper {
    const val REQ_IMAGE = 7701
    /** 臨時圖片最長邊（夠汲色就好，不會吃太多記憶體） */
    const val MAX_PX = 2048

    private var pending: ((Bitmap?) -> Unit)? = null

    /** 把 Activity 目前的畫面拍下來（對話框是另一個視窗，不會被拍進去） */
    fun captureScreen(a: Activity, done: (Bitmap?) -> Unit) {
        val win = a.window
        val v = win.decorView
        if (v.width <= 0 || v.height <= 0) { done(null); return }
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        fun fallback() {
            done(try { v.draw(Canvas(bmp)); bmp } catch (_: Exception) { null })
        }
        try {
            PixelCopy.request(win, bmp, { r -> if (r == PixelCopy.SUCCESS) done(bmp) else fallback() }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            fallback()
        }
    }

    /** 挑一張圖（結果要由 Activity 的 onActivityResult 轉給 [onResult]） */
    fun pickImage(a: Activity, done: (Bitmap?) -> Unit) {
        pending = done
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            @Suppress("DEPRECATION")
            a.startActivityForResult(i, REQ_IMAGE)
        } catch (_: Exception) {
            pending = null
            done(null)
        }
    }

    /** Activity 的 onActivityResult 先問這裡；是汲色的圖就回 true */
    fun onResult(ctx: Context, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_IMAGE) return false
        val cb = pending ?: return true
        pending = null
        val uri = data?.data
        cb(if (resultCode == Activity.RESULT_OK && uri != null) decode(ctx, uri) else null)
        return true
    }

    /** 讀成一般的（可以讀像素的）Bitmap，太大就縮小；GIF 只拿第一格 */
    fun decode(ctx: Context, uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val s = minOf(1f, MAX_PX.toFloat() / maxOf(w, h))
            if (s < 1f) d.setTargetSize(maxOf(1, (w * s).toInt()), maxOf(1, (h * s).toInt()))
        }
    } catch (_: Exception) {
        null
    }

    /** 全螢幕汲色畫面；按「用這個顏色」回傳顏色，取消回傳 null */
    fun show(a: Activity, src: Bitmap, hint: String, done: (Int?) -> Unit): Dialog {
        val dp = a.resources.displayMetrics.density
        val d = Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1B1A.toInt())
        }
        root.addView(TextView(a).apply {
            text = hint
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            val p = (12 * dp).toInt()
            setPadding(p, p, p, p)
        })
        val view = EyedropView(a, src)
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = (12 * dp).toInt()
            setPadding(p, p, p, p)
        }
        val chip = View(a).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setStroke((2 * dp).toInt(), Color.WHITE)
            }
        }
        bar.addView(chip, LinearLayout.LayoutParams((48 * dp).toInt(), (40 * dp).toInt()))
        val hex = TextView(a).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding((10 * dp).toInt(), 0, 0, 0)
        }
        bar.addView(hex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        var result: Int? = null
        bar.addView(Button(a).apply {
            text = "取消"
            setOnClickListener { d.dismiss() }
        })
        bar.addView(Button(a).apply {
            text = "用這個顏色"
            setOnClickListener { result = view.color; d.dismiss() }
        })
        root.addView(bar)

        fun update(c: Int) {
            (chip.background as GradientDrawable).setColor(c)
            hex.text = String.format("#%06X", 0xFFFFFF and c)
        }
        view.onColor = { update(it) }
        update(view.color)

        d.setContentView(root)
        d.setOnDismissListener { done(result) }
        d.show()
        Ui.spaceButtons(root, (8 * dp).toInt())
        return d
    }
}

/** 顯示圖片＋放大鏡；選到的那一格就是 [color] */
@SuppressLint("ViewConstructor")
class EyedropView(ctx: Context, val bmp: Bitmap) : View(ctx) {
    var onColor: ((Int) -> Unit)? = null

    /** 選到的像素（圖片座標） */
    var px = bmp.width / 2
        private set
    var py = bmp.height / 2
        private set
    val color: Int get() = bmp.getPixel(px, py) or 0xFF000000.toInt()

    private val dp = resources.displayMetrics.density
    private val img = RectF()
    private val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixelPaint = Paint() // 放大鏡裡不要糊掉，一格一格看得清楚
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clip = Path()
    private val src = android.graphics.Rect()
    private val dst = RectF()
    private val loupeR = 62 * dp
    private val cells = 13 // 放大鏡裡看到 13×13 格

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
        val iw = bmp.width * s
        val ih = bmp.height * s
        img.set((w - iw) / 2, (h - ih) / 2, (w + iw) / 2, (h + ih) / 2)
    }

    /** 螢幕上的點 → 圖片的像素 */
    fun select(x: Float, y: Float) {
        if (img.isEmpty) return
        px = ((x - img.left) / img.width() * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        py = ((y - img.top) / img.height() * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        onColor?.invoke(color)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                select(e.x, e.y)
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawBitmap(bmp, null, img, imgPaint)
        if (img.isEmpty) return
        // 選到的點
        val sx = img.left + (px + 0.5f) / bmp.width * img.width()
        val sy = img.top + (py + 0.5f) / bmp.height * img.height()
        ring.strokeWidth = 2 * dp
        ring.color = Color.WHITE
        c.drawCircle(sx, sy, 6 * dp, ring)
        ring.strokeWidth = 1 * dp
        ring.color = Color.BLACK
        c.drawCircle(sx, sy, 7.5f * dp, ring)

        // 放大鏡：放在手指上面，太靠上就放下面
        val gap = loupeR + 34 * dp
        val lx = sx.coerceIn(loupeR + 4 * dp, width - loupeR - 4 * dp)
        val ly = if (sy - gap - loupeR >= 0) sy - gap else sy + gap
        val cell = loupeR * 2 / cells
        c.save()
        clip.reset()
        clip.addCircle(lx, ly, loupeR, Path.Direction.CW)
        c.clipPath(clip)
        c.drawColor(0xFF1E1B1A.toInt())
        // 只畫放大鏡看得到的那一小塊
        val n = cells / 2 + 1
        src.set((px - n).coerceAtLeast(0), (py - n).coerceAtLeast(0), (px + n + 1).coerceAtMost(bmp.width), (py + n + 1).coerceAtMost(bmp.height))
        dst.set(
            lx + (src.left - px - 0.5f) * cell, ly + (src.top - py - 0.5f) * cell,
            lx + (src.right - px - 0.5f) * cell, ly + (src.bottom - py - 0.5f) * cell,
        )
        c.drawBitmap(bmp, src, dst, pixelPaint)
        c.restore()
        // 中間那一格
        val half = cell / 2
        ring.strokeWidth = 2 * dp
        ring.color = Color.WHITE
        c.drawRect(lx - half, ly - half, lx + half, ly + half, ring)
        ring.strokeWidth = 1 * dp
        ring.color = Color.BLACK
        c.drawRect(lx - half - 1.5f * dp, ly - half - 1.5f * dp, lx + half + 1.5f * dp, ly + half + 1.5f * dp, ring)
        // 外圈 = 選到的顏色
        ring.strokeWidth = 8 * dp
        ring.color = color
        c.drawCircle(lx, ly, loupeR, ring)
        ring.strokeWidth = 2 * dp
        ring.color = Color.WHITE
        c.drawCircle(lx, ly, loupeR + 4 * dp, ring)
    }
}
