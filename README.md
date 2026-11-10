# 佛大校园助手（非官方）

面向佛山大学的 Android 本地优先校园工具，当前版本 **1.2.0**，支持 **Android 8.0（API 26）及以上**。

- **电费**：从企业微信「佛大一卡通」查看宿舍余额后，导入或手记，自动计算抄表区间用电、月度估算和余额可用天数。
- **课表**：在 App 内登录 **100 教务系统**，进入学生课表后抓取课程，导入为 6 大节 14 小节的 7 天周课表；节假日课程顺延到下一个教学日。
- **本地**：课程、电费记录、房间和学期设置保存在本机，支持 JSON 备份。

工程由 [`sajuby/hust-campus-usage`](https://github.com/sajuby/hust-campus-usage) 的 Compose 结构重做而来，已移除华中科技大学的水费、能源费、校园网、武汉路线和设计工作台。

- [下载调试 APK](dist/fosu-campus-assistant-1.2.0-debug.apk)
- [SHA-256 校验文件](dist/SHA256SUMS.txt)

> 本项目为非官方个人工具，与佛山大学、佛大一卡通及 100 教务均无隶属关系。

## 课表：App 内登录 100 教务

### 使用步骤

1. 手机连接校园网；校外先连接学校 VPN（`vpn.fosu.edu.cn`）。
2. 打开 App →「课表」→「登录 100 教务导入课表」。
3. 在 App 内嵌的学校页面完成统一身份认证。账号、密码和验证码都只输入学校页面。
4. 登录后进入「学生课表」。如果页面没有自动跳转，可点顶部「打开课表」尝试定位入口，或手动进入「培养管理 / 课表查询 / 学生课表」。
5. 页面加载后 App 会自动解析；也可以点「抓取」手动解析当前页。
6. 选择「替换本地课表」或「追加」，课程写入本机周课表。

### 为什么用 WebView 登录

100 教务使用学校统一身份认证（CAS），登录页包含密码加密、验证码和会话跳转。App 不复制这套认证逻辑，也不保存账号密码，而是让学校自己的页面完成登录，再从已登录页面提取课表。这样：

- 密码只经过学校页面，App 无法读取；
- 验证码、统一认证策略变化由学校页面处理；
- App 只解析课表页面的表格，不调用未公开的私有接口。

登录 Cookie 由 Android WebView 保存，便于下次继续使用。可在「我的 → 清除 100 教务登录」删除。

## 课表时间轴与节假日

### 6 大节 / 14 小节

课表固定显示 **6 大节、14 小节**，并始终展示 7 天：

| 大节 | 小节 | 时间 |
| --- | --- | --- |
| 第一大节 | 1-2 | 08:00-08:40 / 08:45-09:25 |
| 第二大节 | 3-5 | 09:40-10:20 / 10:25-11:05 / 11:10-11:50 |
| 第三大节 | 6-7 | 13:30-14:10 / 14:15-14:55 |
| 第四大节 | 8-10 | 15:10-15:50 / 15:55-16:35 / 16:40-17:20 |
| 第五大节 | 11-12 | 18:30-19:10 / 19:15-19:55 |
| 第六大节 | 13-14 | 20:05-20:45 / 20:50-21:30 |

左侧显示大节与小节时间，课程块按小节定位。课程块显示课程名称、授课教师、上课地点、上课时间和持续周数；「本周课程」列表还会显示下一次实际上课日期。

100 教务抓取只保留课名、教师、地点、星期、节次、周次，不抓取备注。

### 节假日与日期游标

在「我的 → 节假日 / 停课日」或课表页的「节假日」中添加校历假期。日期游标按天推进：

- 周末自动跳过；
- 遇到节假日只推进日期，**不消耗课程条目**；
- 节假日当天的课程顺延到下一个教学日，并在课程卡片上标「调」；
- 7 天周视图仍按自然日期展示，节假日列显示「休」，不会把课程画在节假日上。

节假日需要手动维护，因为学校校历和调休安排可能每学期变化，App 不内置不可靠的法定节假日表。

### 抓取原理

`app/src/main/assets/timetable_extract.js` 会在 100 教务页面执行：

1. 遍历当前文档和同源 iframe；
2. 识别“课程列表”型表格，或“节次 × 星期”型课表；
3. 解析课程名、教师、地点、星期、节次、周次；
4. 通过 `FosuBridge` 返回 JSON，Kotlin 端再交给 `TimetableTextParser` 校验和保存。

已适配两种常见页面结构：

- 表头包含「课程名称 / 教师 / 地点 / 星期 / 节次 / 周次」的列表；
- 表头包含「星期一…星期日」、行首为节次的周课表，包括 `rowspan` 合并单元格。

学校页面升级后，如果抓取失败，仍可使用「手动导入」粘贴课程。

### 抓不到怎么办

- 确认已连接校园网或 VPN，页面不是登录页。
- 确认当前页面是「学生课表」，而不是「班级课表」「教师课表」或「教室课表」。
- 点「抓取」重新解析；如果课表在独立 iframe 中，先让该 iframe 加载完成。
- 若页面使用了跨域 iframe，浏览器安全策略会阻止读取，此时请在页面内打开独立课表链接，或使用手动导入。
- 100 教务的页面结构、地址或认证方式可能调整；本项目不做绕过认证的抓取。

## 课表手动导入

```text
课程名|教师|地点|周一|1-2|1-16
高等数学|张三|C5-101|周一|1-2|1-16
大学英语|李四|B2-203|周三|3-4|1-8,10-16
```

也支持 JSON：

```json
[
  {
    "name": "数据结构",
    "teacher": "王五",
    "room": "A3-201",
    "weekday": 2,
    "start": 5,
    "end": 6,
    "weeks": "1-16"
  }
]
```

周次支持 `1-16`、`1-8,10-16`、`1-16单`、`1-16双`。星期支持 `周一`、`星期一`、`1`、`Mon`。

## 电费

- 首次设置校区、房间与估算电价。
- 记录电表读数、账户余额、日期和备注。
- 从「佛大一卡通」复制文本后解析余额、剩余电量、房间和日期。
- 抄表区间用量、区间电费、近 7 日日均、预计可用天数。
- 30 次抄表区间柱状趋势。
- JSON 备份与恢复。

示例文本：

```text
房间：A1-302
余额：45.60 元
剩余电量 128.4 kWh
2026-11-08
```

## 数据与隐私

- App 申请 `INTERNET` 权限，仅用于在 WebView 中访问 `100.fosu.edu.cn` 和学校统一认证。
- 不读取、不保存教务密码；不包含广告、统计 SDK 或自建服务器。
- 100 教务登录 Cookie 由 WebView 保存，可在「我的」清除。
- 电费与课程数据保存在应用私有 `SharedPreferences`，`allowBackup=false`，不进入系统云备份。
- 备份是明文 JSON，分享后请自行管理。

详细说明见 [隐私说明](docs/PRIVACY.md) 和 [数据源说明](docs/DATA_SOURCES.md)。

## 构建

环境要求：

- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- Gradle 8.13（或使用仓库内 Wrapper）

```bash
cd android
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows：

```bat
cd android
set JAVA_HOME=C:\path\to\jdk-17
set ANDROID_HOME=C:\path\to\android-sdk
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK 输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

调试包 ID：`dev.campususage.fosu.debug`；正式包 ID：`dev.campususage.fosu`。

## 工程结构

```text
android/app/src/main/
  assets/timetable_extract.js     100 教务页面课表提取器
  java/dev/campususage/fosu/
    MainActivity.kt
    TimetableLoginActivity.kt     App 内 100 教务 WebView
    data/
      Models.kt                   数据模型、周次与电费计算
      Parsers.kt                  电费文本 / 课表文本 / JSON 解析
      AppStore.kt                 本地持久化与备份
      OfficialLauncher.kt         佛大一卡通入口、清除教务登录
    ui/
      FosuApp.kt / FosuViewModel.kt
      Ui.kt / Charts.kt / Dialogs.kt
      screens/                    首页、电费、课表、我的
      theme/
  test/                           周次、解析、电费计算单元测试
docs/
  DATA_SOURCES.md                 100 教务接入方式与边界
  ARCHITECTURE.md                 页面抓取与数据流
  PRIVACY.md                      权限、Cookie 与本地数据
  VERIFICATION.md                 构建与验证记录
```

## 验证

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug
```

当前验证结果：18 个单元测试通过，lint 0 errors。详细记录见 [验证记录](docs/VERIFICATION.md)。

## 数据源

- 100 教务系统：<https://100.fosu.edu.cn/>（校外通常需要校园 VPN）
- 佛山大学统一身份认证：<https://authserver.fosu.edu.cn/authserver/login>
- 佛山大学企业号注册：<https://reg.fosu.edu.cn/>
- 校园网/账号指引：<https://help.fosu.edu.cn/point2>
- 佛大一卡通：佛山大学企业微信 → 工作台 → 佛大一卡通

## 关于

App 内「我的 → 关于」只显示版本号和功能描述。本 README 与代码中的上游链接仅用于说明技术来源。

- 上游仓库：[sajuby/hust-campus-usage](https://github.com/sajuby/hust-campus-usage)
- “佛山大学”“佛大一卡通”等名称归各自权利人所有。
- 本项目不包含任何学校账号、Cookie、真实账单、课表截图或签名密钥。
