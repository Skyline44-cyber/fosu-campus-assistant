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
                 └─ OfficialLauncher

TimetableLoginActivity
  ├─ WebView → 100.fosu.edu.cn → CAS 登录
  ├─ assets/timetable_extract.js
  ├─ FosuBridge (JS ↔ Kotlin)
  └─ Intent result → TimetableScreen / HomeScreen / ProfileScreen
```

## 课表登录与抓取

`TimetableLoginActivity` 是一个独立 Activity：

1. 加载 `https://100.fosu.edu.cn/`；
2. 允许学校域名（`*.fosu.edu.cn`）在 WebView 内导航；
3. 用户在学校 CAS 页面完成登录；
4. 页面加载完成后注入 `timetable_extract.js`；
5. 脚本自动扫描课表，或用户点击「打开课表」「抓取」触发；
6. 脚本通过 `@JavascriptInterface` 回传 JSON；
7. Activity 只接受当前主机为 `100.fosu.edu.cn` 的结果；
8. 用户选择替换或追加后，`setResult` 返回给调用页；
9. 调用页通过 `TimetableTextParser` 校验并写入 `AppStore`。

### WebView 安全设置

- 仅允许 `http/https` 且主机为 `fosu.edu.cn` 或其子域；
- 禁止文件访问和内容访问；
- 禁用地理位置；
- 禁止 HTTPS 页面加载混合内容；
- 证书错误直接取消，不提供忽略选项；
- 页面不能打开新窗口；
- 不注入账号密码，不读取输入框；
- JS Bridge 只暴露课表扫描相关方法。

### 提取器

`timetable_extract.js` 的主要函数：

- `docs()`：收集当前文档与同源 iframe；
- `scanList(table)`：按表头映射课程列表；
- `scanGrid(table)`：按星期列和节次行解析周课表；
- `courseFrom(el)`：从 `.title/.teacher/.room/.week` 或文本行解析单个课程块；
- `normalizeWeeks()` / `weeks()`：解析 `1-16`、`1-8,10-16`、`1-16单`、`1-16双`；
- `__fosuScan(manual)`：扫描并回调 Kotlin；
- `__fosuOpenTimetable()`：尝试点击「学生课表」等入口。

脚本不访问网络，也不把页面内容发到外部。

## 数据

```kotlin
data class AppData(
    val settings: Settings,
    val readings: List<ElectricityReading>,
    val courses: List<Course>
)
```

`AppStore` 序列化为 JSON，写入：

```text
SharedPreferences("fosu_campus_data_v1")
```

没有数据库或后台服务。

## 课表时间轴与节假日

`SectionSchedule` 固定 6 大节 / 14 小节：

```text
第一大节 1-2   08:00-08:40 / 08:45-09:25
第二大节 3-5   09:40-10:20 / 10:25-11:05 / 11:10-11:50
第三大节 6-7   13:30-14:10 / 14:15-14:55
第四大节 8-10  15:10-15:50 / 15:55-16:35 / 16:40-17:20
第五大节 11-12 18:30-19:10 / 19:15-19:55
第六大节 13-14 20:05-20:45 / 20:50-21:30
```

`Course.timeLabel` 由第一小节的开始时间和最后一个小节的结束时间计算，课程卡片同时显示教师、地点、时间和周次。

`TeachingCalendar` 使用日期游标：

```kotlin
nextTeachingDay(date)      // 周末/节假日只前进，不返回
scheduleForWeek(...)       // 把节假日课程顺延到下一教学日
nextCourseDate(...)        // 日期游标找到下一次实际课程
```

`ScheduledCourse(course, date, shifted)` 记录实际日期和是否调课。周视图按 `date.dayOfWeek` 排布，而不是直接使用 `course.weekday`，因此节假日课程不会被画在「休」列，而会出现在顺延后的教学日并标记「调」。

节假日由用户在校历中维护，保存为 `Settings.holidays`。App 不内置法定节假日表，避免学校调休安排变化导致错误排课。

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
- 周课表使用 Compose `Box` + `offset` 定位课程块，横向滚动适配窄屏；固定 7 天、6 大节、14 小节。
- 100 教务登录页使用程序化 Android View（WebView + 顶部操作栏），不依赖 Compose WebView 封装。

## 测试

纯 Kotlin 测试覆盖：

- 周次与单双周；
- 6 大节 / 14 小节与起止时间；
- 节假日日期游标和课程顺延；
- 电费文本解析；
- 课表文本/JSON 解析；
- 抄表区间、月分摊、余额可用天数。

页面提取器另外用 happy-dom 对课程列表和周课表样张做过脚本级验证；真实 100 教务页面仍需在校园网环境做人工验收。
