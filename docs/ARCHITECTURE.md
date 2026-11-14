# 工程架构

## 总体结构

```text
MainActivity
  └─ FosuTheme
      └─ FosuApp (Scaffold + 底部导航)
          ├─ HomeScreen
          ├─ ElectricityScreen
          ├─ TimetableScreen
          └─ ProfileScreen
                 ↕
             FosuViewModel
                 ├─ AppStore
                 ├─ ElectricityTextParser / TimetableTextParser
                 ├─ ElectricityMath / WeekSpec / TimetableMath
                 ├─ ChaoxingParser
                 └─ OfficialLauncher

ChaoxingLoginActivity
  ├─ WebView → passport2.chaoxing.com / i.chaoxing.com
  ├─ CookieManager（登录 Cookie）
  ├─ ChaoxingClient（原生 HTTPS 请求）
  └─ Intent result（ChaoxingTimetable JSON）
```

## 学习通登录与同步

`ChaoxingLoginActivity` 是独立 Activity：

1. 加载 `https://i.chaoxing.com/`；未登录时会跳转到学习通手机版登录页。
2. 只允许 `chaoxing.com` 及其子域在 WebView 内导航。
3. 用户在学习通官方页面输入手机号 / 超星号、密码或使用短信/扫码登录。
4. 页面回到 `i.chaoxing.com` 或 `kb.chaoxing.com` 后，Activity 从 `CookieManager` 读取 Cookie。
5. 在后台线程调用 `ChaoxingClient`，不使用页面 HTML 抓取。
6. 用户选择「替换」或「追加」后，`setResult` 返回编码后的 `ChaoxingTimetable`。
7. 调用页通过 `FosuViewModel.importChaoxing()` 写入 `AppStore`。

### WebView 安全设置

- 仅允许 `http/https` 且主机为 `chaoxing.com` 或其子域；
- 禁止文件访问和内容访问；
- 禁用地理位置；
- 禁止 HTTPS 页面加载混合内容；
- 证书错误直接取消，不提供忽略选项；
- 页面不能打开新窗口；
- 不注入账号密码，不读取输入框；
- 没有 JS Bridge；登录页只负责建立 Cookie。

### 课表接口客户端

`ChaoxingClient` 使用 `HttpsURLConnection`：

```text
POST https://kb.chaoxing.com/pc/curriculum/getMyLessons
Content-Type: application/x-www-form-urlencoded
Cookie: <WebView 中的学习通 Cookie>

week=1
```

流程：

1. 先发一次空 `week` 请求，读取 `data.curriculum.maxWeek`。
2. 对 `1..maxWeek` 逐周请求。
3. `ChaoxingParser` 从每周响应中读取 `lessonArray`。
4. 按 `lessonId + 星期 + 起止节次 + 教室 + 教师 + 课程名` 合并同一门课。
5. `weeks` 压缩为 `1-16`、`1-8,10-16` 等字符串。
6. 学期第一周周一取 `curriculum.firstWeekDate`，节次时间取 `lessonTimeConfigArray`。

不读取备注、成绩、账号资料或页面 HTML，也不调用学习通的其他接口。

## 数据

```kotlin
data class AppData(
    val settings: Settings,
    val readings: List<ElectricityReading>,
    val courses: List<Course>
)

data class Settings(
    val termStart: LocalDate,
    val totalWeeks: Int,
    val sectionTimes: List<String>, // "08:00-08:45"
    // 房间、校区、电价、引导状态
)
```

`AppStore` 序列化为 JSON，写入：

```text
SharedPreferences("fosu_campus_data_v1")
```

没有数据库或后台服务。旧版备份中的 `holidays`、`sectionsPerDay` 字段会被忽略；`sectionTimes` 缺失时使用 12 节默认时间。

## 课表显示

- `TimetableMath` 负责周次、周起止日期和下一次上课日期。
- `TimetableSections` 保存学校真实节次时间，默认 12 节；导入后使用学习通返回的时间表。
- `TimetableScreen` 的 `ChaoxingWeekGrid` 使用 7 天列、左侧节次/时间栏和 `Box + offset` 课程块。
- 课程块高度 = 跨越节次数 × 单节高度。
- 今天所在列使用学习通蓝色高亮。
- 节次数 = `max(sectionTimes.size, 课程最大结束节次, 12)`，上限 20。
- 不再包含节假日日期游标、顺延或「休/调」逻辑。

## 电费

- 读数记录构成时间序列；
- 区间用量 = 后一次读数 − 前一次读数；
- 读数回退视为换表/异常，不生成负用量；
- 月用量按区间与自然月的重叠天数分摊；
- 近 7 日日均只使用窗口内区间；
- 预计可用天数 = 余额 ÷（电价 × 日均用电）。

## UI

- 深色“星野”主题，颜色集中在 `ui/theme/Theme.kt`。
- 通用卡片、标题、指标、空状态在 `ui/Ui.kt`。
- 周课表使用 Compose `Box` + `offset` 定位课程块，横向滚动适配窄屏。
- 学习通登录页使用程序化 Android View（WebView + 顶部操作栏）。

## 测试

纯 Kotlin 测试覆盖：

- 周次范围、单双周与压缩；
- 12 节默认时间、学习通时间表标准化；
- 学习通课表 JSON 解析、逐周合并、失败响应；
- 课表编码/解码；
- 下一次上课日期；
- 电费文本解析；
- 课表文本/JSON 解析；
- 抄表区间、月分摊、余额可用天数。

真实学习通账号的登录和接口联调仍需在设备上人工验收。
