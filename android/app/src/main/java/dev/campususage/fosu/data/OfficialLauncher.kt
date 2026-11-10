package dev.campususage.fosu.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.core.net.toUri

data class OfficialService(
    val id: String,
    val title: String,
    val subtitle: String,
    val packageName: String,
    val fallbackUrl: String,
    val keyword: String,
    val steps: List<String>
)

object OfficialServices {
    val ECARD = OfficialService(
        id = "ecard",
        title = "佛大一卡通",
        subtitle = "企业微信内的校园卡与电费入口",
        packageName = OfficialLauncher.WEWORK_PACKAGE,
        fallbackUrl = "https://work.weixin.qq.com/",
        keyword = "佛大一卡通",
        steps = listOf(
            "打开佛山大学企业微信",
            "进入「工作台」并找到「佛大一卡通」",
            "选择「缴电费 / 电费查询」，查看房间余额",
            "回到本应用，点击「导入官方结果」或「记一笔」"
        )
    )

    val ALL = listOf(ECARD)
}

object TimetableWebContract {
    const val START_URL = "https://100.fosu.edu.cn/"
    const val EXTRA_COURSES_JSON = "courses_json"
    const val EXTRA_REPLACE = "replace_courses"
    const val EXTRA_MESSAGE = "import_message"
}

object OfficialLauncher {
    const val WEWORK_PACKAGE = "com.tencent.wework"

    const val FOSU_REG = "https://reg.fosu.edu.cn/"
    const val FOSU_AUTH = "https://authserver.fosu.edu.cn/authserver/login"
    const val FOSU_JW = TimetableWebContract.START_URL
    const val FOSU_HELP = "https://help.fosu.edu.cn/point2"

    fun openService(context: Context, service: OfficialService): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(service.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(launch)
                true
            }.getOrDefault(false)
        }
        return openUrl(context, service.fallbackUrl)
    }

    fun openUrl(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrElse {
        Toast.makeText(context, "没有找到可打开该链接的应用", Toast.LENGTH_SHORT).show()
        false
    }

    fun copy(context: Context, text: String, label: String = "已复制") {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    fun share(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    /** Clears the WebView cookie jar used by the 100 teaching-system login. */
    fun clearTimetableLogin(context: Context) {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies {
            cookies.flush()
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(context, "已清除 100 教务登录状态", Toast.LENGTH_SHORT).show()
        }
    }
}
