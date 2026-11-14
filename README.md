# 佛大校园助手（非官方）

面向佛山大学的 Android 本地优先校园工具，当前版本 **1.3.0**，支持 **Android 8.0（API 26）及以上**。

- **课表**：在 App 内登录 **超星学习通**，自动同步学生课表，按学习通的周课表样式展示。
- **电费**：从企业微信「佛大一卡通」查看宿舍余额后，导入或手记，自动计算抄表区间用电、月度估算和余额可用天数。
- **本地**：课程、电费记录、房间和学期设置保存在本机，支持 JSON 备份。

工程由 [`sajuby/hust-campus-usage`](https://github.com/sajuby/hust-campus-usage) 的 Compose 结构重做而来，已移除华中科技大学的水费、能源费、校园网、武汉路线和设计工作台。

- [下载调试 APK](dist/fosu-campus-assistant-1.3.0-debug.apk)
- [SHA-256 校验文件](dist/SHA256SUMS.txt)
- [GitHub 仓库](https://github.com/Skyline44-cyber/fosu-campus-assistant)
- [GitHub Release v1.3.0](https://github.com/Skyline44-cyber/fosu-campus-assistant/releases/tag/v1.3.0)

> 本项目为非官方个人工具，与佛山大学、佛大一卡通及超星学习通均无隶属关系。

## 课表：登录学习通同步

### 使用步骤

1. 打开 App →「课表」→「登录学习通同步课表」。
2. 在 App 内的学习通官方页面使用手机号 / 超星号和学习通密码登录；也可以使用学习通支持的扫码或短信登录方式。
3. 登录成功后 App 会自动读取学生课表，也可以点顶部「同步」手动触发。
4. 同步完成后选择「替换并导入」或「追加」。
5. 课程写入本机，之后可离线查看；在「我的 → 清除学习通登录」可删除 WebView 中的登录 Cookie。

### 同步原理

- 登录由 `passport2.chaoxing.com` / `i.chaoxing.com` 官方页面完成，App 不读取、不保存学习通密码。
- 登录 Cookie 由 Android WebView 的 `CookieManager` 保存。
- App 使用同一个 Cookie 调用学习通课表接口：

```text
POST https://kb.chaoxing.com/pc/curriculum/getMyLessons
form: week=1..N
```

- 该接口不带 `week` 时只返回当前周；App 先读取 `curriculum.maxWeek`，再逐周请求并合并同一门课。
- 只使用接口返回的 `name`、`teacherName`、`location`、`dayOfWeek`、`beginNumber`、`length`、`weeks` 和 `curriculum.lessonTimeConfigArray`，不抓取备注信息。
- 学期第一周周一来自 `curriculum.firstWeekDate`，节次时间来自 `lessonTimeConfigArray`，不使用本地猜测的节假日表。

### 课表样式

- 7 天横向周课表，周一到周日完整显示。
- 左侧为节次和学校真实开始/结束时间。
- 顶部日期栏高亮今天。
- 课程块按节次高度铺排，使用学习通风格的彩色块，显示课程名、地点和教师。
- 课程清单显示教师、地点、节次、时间、周次和下一次上课日期。
- 支持 1-16、1-8,10-16、1-16单、1-16双等周次写法。

上一版新增的「6 大节 14 小节」「日期游标跳过节假日」「节假日调课」逻辑已撤销；本版不再自动推算节假日，课表以学习通返回的周次和节次为准。

## 课表手动导入

学习通同步失败时，仍可手动粘贴：

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

- App 申请 `INTERNET` 权限，仅用于学习通 WebView、学习通课表接口和企业微信官方入口。
- 不读取、不保存学习通密码；不包含广告、统计 SDK 或自建服务器。
- 课程、电费记录和设置只写入本机 `SharedPreferences`。
- 清除学习通登录会清空 WebView Cookie 和网页存储，但不会删除已同步的课程。

## 构建

```bash
cd android
./gradlew assembleDebug
```

- `compileSdk = 36`，`minSdk = 26`，JDK 17。
- 单元测试：`./gradlew testDebugUnitTest`
- Lint：`./gradlew lintDebug`
- 调试包输出：`android/app/build/outputs/apk/debug/app-debug.apk`

## 已知限制

- 未使用真实学习通账号完成端到端验收；登录页和课表接口可能随学习通升级变化。
- 学习通账号必须已绑定学生课表；教师账号或未绑定课表的账号可能返回空列表。
- 未内置法定节假日/调休表，课表按学习通返回的周次显示。
- 校外访问学习通可能需要校园网或学校 VPN。
- 调试 APK 使用 debug 签名，不适合作为应用商店正式包。
