package dev.campususage.fosu

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
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
import dev.campususage.fosu.data.ChaoxingClient
import dev.campususage.fosu.data.ChaoxingContract
import dev.campususage.fosu.data.ChaoxingParser
import dev.campususage.fosu.data.ChaoxingTimetable

@SuppressLint("SetTextI18n")
class ChaoxingLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var syncButton: Button
    private var syncing = false
    private var autoSyncTried = false

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
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress in 1..99 && !syncing) {
                    progress.visibility = View.VISIBLE
                    progress.progress = newProgress
                } else if (newProgress >= 100 && !syncing) {
                    progress.visibility = View.GONE
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host.orEmpty()
                val allowed = uri.scheme == "https" &&
                    (host == "chaoxing.com" || host.endsWith(".chaoxing.com"))
                if (!allowed) {
                    Toast.makeText(this@ChaoxingLoginActivity, "仅允许访问学习通页面", Toast.LENGTH_SHORT).show()
                }
                return !allowed
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!syncing) {
                    progress.visibility = View.VISIBLE
                    status.text = "正在加载学习通…"
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (syncing) return
                progress.visibility = View.GONE
                val uri = runCatching { url.orEmpty().toUri() }.getOrDefault(Uri.EMPTY)
                val host = uri.host.orEmpty()
                val path = uri.path.orEmpty()
                if (host == "passport2.chaoxing.com" || path.contains("login", ignoreCase = true)) {
                    status.text = "请使用手机号 / 超星号和学习通密码登录。登录由学习通官方页面处理，App 不读取密码。"
                    autoSyncTried = false
                    return
                }
                if (host == "i.chaoxing.com" || host == "kb.chaoxing.com" || host == "www.chaoxing.com") {
                    status.text = "已登录学习通，正在同步学生课表…"
                    if (!autoSyncTried) {
                        autoSyncTried = true
                        view.postDelayed({
                            if (!isFinishing && !isDestroyed) startSync(manual = false)
                        }, 1_200)
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !syncing) {
                    progress.visibility = View.GONE
                    status.text = "学习通页面加载失败，请检查网络后重试。"
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
                progress.visibility = View.GONE
                status.text = "学习通页面证书校验未通过，已停止加载。"
            }
        }
        webView.loadUrl(ChaoxingContract.START_URL)
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
            text = "学习通 · 课表同步"
            textSize = 17f
            setTextColor(TEXT)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })

        syncButton = actionButton("同步") { startSync(manual = true) }
        toolbar.addView(syncButton)
        toolbar.addView(actionButton("退出") { logout() })
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        status = TextView(this).apply {
            textSize = 11f
            setTextColor(MUTED)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setBackgroundColor(SURFACE)
            text = "请登录学习通。登录后会自动读取学生课表，也可以点「同步」。"
        }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(BLUE)
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val hint = TextView(this).apply {
            text = "登录 Cookie 只保存在本机 WebView；课程写入本机课表，不保存学习通密码。"
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
            backgroundTintList = ColorStateList.valueOf(BLUE)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                marginStart = dp(5)
            }
        }
    }

    private fun logout() {
        if (syncing) return
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies {
            cookies.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            runOnUiThread {
                autoSyncTried = false
                status.text = "已退出学习通，请重新登录。"
                webView.loadUrl(ChaoxingContract.START_URL)
            }
        }
    }

    private fun startSync(manual: Boolean) {
        if (syncing) return
        val cookies = cookieHeader()
        if (cookies.isBlank()) {
            status.text = "还没有登录学习通，请先在页面中完成登录。"
            autoSyncTried = false
            if (manual) Toast.makeText(this, "请先登录学习通", Toast.LENGTH_SHORT).show()
            return
        }

        syncing = true
        syncButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "正在从学习通同步课表…"
        val userAgent = webView.settings.userAgentString

        Thread {
            try {
                val timetable = ChaoxingClient(cookies, userAgent).fetchAll { week, total ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            status.text = "正在同步第 $week / $total 周…"
                        }
                    }
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        syncing = false
                        syncButton.isEnabled = true
                        progress.visibility = View.GONE
                        status.text = "已读取 ${timetable.courses.size} 门课程，请选择导入方式。"
                        showImportDialog(timetable)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        syncing = false
                        syncButton.isEnabled = true
                        progress.visibility = View.GONE
                        autoSyncTried = false
                        status.text = error.message ?: "同步失败，请稍后重试。"
                        if (manual) {
                            Toast.makeText(
                                this@ChaoxingLoginActivity,
                                error.message ?: "同步失败",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }.start()
    }

    private fun showImportDialog(timetable: ChaoxingTimetable) {
        val json = ChaoxingParser.encode(timetable)
        AlertDialog.Builder(this)
            .setTitle("同步学习通课表")
            .setMessage(
                "已读取 ${timetable.courses.size} 门课程，共 ${timetable.maxWeek} 周。\n\n" +
                    "选择「替换」会覆盖本机现有课程；选择「追加」会保留并合并。"
            )
            .setPositiveButton("替换并导入") { _, _ -> finishWith(json, true) }
            .setNeutralButton("追加") { _, _ -> finishWith(json, false) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun finishWith(json: String, replace: Boolean) {
        val result = Intent()
            .putExtra(ChaoxingContract.EXTRA_COURSES_JSON, json)
            .putExtra(ChaoxingContract.EXTRA_REPLACE, replace)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun cookieHeader(): String {
        val manager = CookieManager.getInstance()
        manager.flush()
        val values = linkedMapOf<String, String>()
        val urls = listOf(
            "https://kb.chaoxing.com/",
            "https://i.chaoxing.com/",
            "https://passport2.chaoxing.com/"
        )
        for (url in urls) {
            val cookie = manager.getCookie(url) ?: continue
            for (part in cookie.split(';')) {
                val index = part.indexOf('=')
                if (index <= 0) continue
                val name = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                if (name.isNotEmpty()) values[name] = value
            }
        }
        return values.entries.joinToString("; ") { "${it.key}=${it.value}" }
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
        private val BLUE = Color.rgb(191, 230, 255)
        private val INK = Color.rgb(20, 26, 43)
    }
}
