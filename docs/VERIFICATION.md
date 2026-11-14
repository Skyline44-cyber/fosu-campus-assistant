# 验证记录

日期：2026-11-14

## 构建环境

```text
JDK     17.0.21 (Temurin)
Gradle  8.13
AGP     8.13.2
compileSdk 36
build-tools 36.0.0
minSdk 26
targetSdk 36
```

构建在 ASCII 路径 `D:\fosu-build` 下执行，避免中文路径导致 Gradle 原生库加载失败。

## 单元测试

命令：

```bash
gradle testDebugUnitTest
```

结果：**21 个测试，0 failures，0 errors，0 skipped**。

覆盖：

- 周次范围、单双周、周次压缩；
- 12 节默认时间、学习通节次时间标准化；
- 学习通课表 JSON 解析；
- 逐周合并与周次压缩；
- 学习通登录失败响应；
- 课表编码/解码；
- 下一次上课日期；
- 电费文本解析；
- 手动课表文本/JSON 解析；
- 抄表区间、月分摊、余额可用天数。

## Lint

命令：

```bash
gradle lintDebug
```

结果：

```text
No issues found.
```

## APK

命令：

```bash
gradle assembleDebug
```

结果：**BUILD SUCCESSFUL**。

```text
文件：fosu-campus-assistant-1.3.0-debug.apk
大小：16,644,788 bytes
包名：dev.campususage.fosu.debug
版本：1.3.0-dev（versionCode 4）
minSdk：26
targetSdk：36
SHA-256：67cf98a3209ab59ff02c062be2bf4d26e21aa19e8b8fcaeff2496c17d350f4c1
```

签名：

```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Signer: CN=Android Debug
```

权限：

```text
android.permission.INTERNET
dev.campususage.fosu.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 由 AndroidX 自动合并，不是应用主动申请的敏感权限。

## 包内容检查

- 未包含 `assets/timetable_extract.js`。
- 未包含 `TimetableLoginActivity`。
- 包含 `ChaoxingLoginActivity`、`ChaoxingClient` 和 `getMyLessons` 课表接口逻辑。
- 未申请位置、通讯录、相机、存储或读取应用列表权限。

## 接口探测

未登录时请求：

```text
POST https://kb.chaoxing.com/pc/curriculum/getMyLessons
```

返回：

```json
{"data":"用户信息异常，请登录后再试","msg":"用户信息异常，请登录后再试","result":0}
```

单元测试覆盖了该失败响应，登录失效时会显示提示，不会写入空课表。

## 尚未完成的验收

- 没有真实学习通学生账号，未完成登录 → 同步 → 替换/追加 → 课表渲染的端到端验收。
- 没有连接 Android 真机或模拟器（`adb devices` 为空），未做点击级 UI 验收。
- 学习通登录页、接口字段或风控策略变化时，可能需要更新 `ChaoxingClient` / `ChaoxingParser`。

在真实设备验收时重点检查：

1. 登录后是否自动跳回 `i.chaoxing.com` 并触发同步；
2. 同步对话框的课程数和周数是否正确；
3. 7 天课表是否按学习通节次时间对齐；
4. 替换/追加是否只修改本机课程；
5. 「我的 → 清除学习通登录」后是否要求重新登录。
