package dev.campususage.fosu

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import dev.campususage.fosu.data.TimetableWebContract
import org.json.JSONArray

@SuppressLint("SetTextI18n")
class TimetableLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var prompting = false
    private var lastPromptedJson: String? = null

    private val extractScript: String by lazy {
        assets.open("timetable_extract.js").bufferedReader().use { it.readText() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        webView = WebView(this)
        setContentView(buildLayout())

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.setBackgroundColor(BG)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            saveFormData = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.addJavascriptInterface(Bridge(), "FosuBridge")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress in 1..99) {
                    progress.visibility = View.VISIBLE
                    progress.progress = newProgress
                } else if (newProgress >= 100) {
                    progress.visibility = View.GONE
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host.orEmpty()
                val allowed = (uri.scheme == "https" || uri.scheme == "http") &&
                    (host == "fosu.edu.cn" || host.endsWith(".fosu.edu.cn"))
                if (!allowed) {
                    Toast.makeText(this@TimetableLoginActivity, "仅允许访问佛山大学教务相关页面", Toast.LENGTH_SHORT).show()
                }
                return !allowed
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                status.text = "正在加载学校页面…"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                val host = runCatching { url.orEmpty().toUri().host.orEmpty() }.getOrDefault("")
                status.text = when {
                    host == "authserver.fosu.edu.cn" -> "请使用学号/手机号和密码登录。验证码由学校页面处理，App 不读取密码。"
                    host == "100.fosu.edu.cn" -> "已进入 100 教务。进入「学生课表」后点「抓取」，或点「打开课表」。"
                    else -> "正在加载学校页面…"
                }
                view.postDelayed({
                    if (!isFinishing && !isDestroyed) injectScan(false)
                }, 900)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    progress.visibility = View.GONE
                    status.text = "页面加载失败。请确认已连接校园网或学校 VPN 后重试。"
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
                progress.visibility = View.GONE
                status.text = "学校页面证书校验未通过，已停止加载。"
            }
        }
        webView.loadUrl(TimetableWebContract.START_URL)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            fitsSystemWindows = true
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(8), dp(4))
            setBackgroundColor(BG)
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            contentDescription = "返回"
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(40), dp(48)))

        val title = TextView(this).apply {
            text = "100 教务 · 课表导入"
            textSize = 17f
            setTextColor(TEXT)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })

        toolbar.addView(actionButton("打开课表") { openTimetable() })
        toolbar.addView(actionButton("抓取") { capture() })
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        status = TextView(this).apply {
            textSize = 11f
            setTextColor(MUTED)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setBackgroundColor(SURFACE)
            text = "请登录 100 教务。登录后进入「学生课表」，再点「抓取」。"
        }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(PINK)
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val hint = TextView(this).apply {
            text = "登录由学校页面处理；课程只导入本机，不保存教务密码。"
            textSize = 9f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        root.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            minWidth = 0
            minimumWidth = 0
            setTextColor(INK)
            backgroundTintList = ColorStateList.valueOf(PINK)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                marginStart = dp(5)
            }
        }
    }

    private fun openTimetable() {
        status.text = "正在查找「学生课表」入口…"
        webView.evaluateJavascript(extractScript + "\n;window.__fosuOpenTimetable();", null)
    }

    private fun capture() {
        status.text = "正在解析当前页面…"
        injectScan(true)
    }

    private fun injectScan(manual: Boolean) {
        val flag = if (manual) "true" else "false"
        webView.evaluateJavascript(extractScript + "\n;window.__fosuScan(" + flag + ");", null)
    }

    private fun handleCourses(json: String) {
        val host = runCatching { webView.url.orEmpty().toUri().host.orEmpty() }.getOrDefault("")
        if (host != "100.fosu.edu.cn") return
        if (prompting || json == lastPromptedJson) return
        val count = runCatching { JSONArray(json).length() }.getOrDefault(0)
        if (count <= 0) return
        prompting = true
        lastPromptedJson = json
        status.text = "已解析到 $count 门课程，请选择导入方式。"
        AlertDialog.Builder(this)
            .setTitle("导入 $count 门课程")
            .setMessage("来源：100 教务\n" + (webView.url ?: "") + "\n\n请选择替换还是追加本机课表。")
            .setPositiveButton("替换本地课表") { _, _ -> finishWith(json, true) }
            .setNeutralButton("追加") { _, _ -> finishWith(json, false) }
            .setNegativeButton("取消") { _, _ ->
                prompting = false
                lastPromptedJson = null
            }
            .setOnCancelListener {
                prompting = false
                lastPromptedJson = null
            }
            .show()
    }

    private fun finishWith(json: String, replace: Boolean) {
        val result = Intent()
            .putExtra(TimetableWebContract.EXTRA_COURSES_JSON, json)
            .putExtra(TimetableWebContract.EXTRA_REPLACE, replace)
        setResult(RESULT_OK, result)
        finish()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onCourses(json: String) {
            runOnUiThread { handleCourses(json) }
        }

        @JavascriptInterface
        fun onScanEmpty() {
            runOnUiThread {
                if (!prompting) status.text = "没有找到课表。请确认已进入「学生课表」页面，再点「抓取」。"
            }
        }

        @JavascriptInterface
        fun onNavigateResult(result: String) {
            runOnUiThread {
                if (result.startsWith("clicked:")) {
                    status.text = "已点击「${result.removePrefix("clicked:")}」，等待页面加载…"
                    webView.postDelayed({
                        if (!isFinishing && !isDestroyed) injectScan(false)
                    }, 1500)
                } else {
                    status.text = "没有找到课表入口。请手动进入「培养管理 → 课表查询 → 学生课表」后点「抓取」。"
                }
            }
        }
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("FosuBridge")
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.rgb(11, 14, 24)
        private val SURFACE = Color.rgb(21, 27, 41)
        private val TEXT = Color.rgb(233, 236, 251)
        private val MUTED = Color.rgb(152, 165, 188)
        private val PINK = Color.rgb(242, 168, 203)
        private val INK = Color.rgb(20, 26, 43)
    }
}
