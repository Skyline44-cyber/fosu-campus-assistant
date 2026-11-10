# 构建与验证记录

日期：2026-11-11（Asia/Shanghai）

## 环境

| 项目 | 版本 |
| --- | --- |
| JDK | Amazon Corretto 17.0.21 |
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| Build-Tools | 36.0.0 |

## 结果

```text
:app:testDebugUnitTest    PASS（18 个测试，0 失败）
:app:assembleDebug        PASS
:app:lintDebug            PASS（No issues found）
```

测试覆盖：

- 周次范围、单双周、分段单双周、中文格式
- 电费余额/读数/房间/日期文本解析
- 课表管道文本与 JSON 解析
- 抄表区间、月用量按重叠天数分摊、余额可用天数
- 6 大节 / 14 小节结构与真实起止时间
- 节假日日期游标、节假日课程顺延、普通日期不顺延

页面提取器另外用 happy-dom 做了脚本级样张验证：

- 课程列表表格（课程名称/教师/地点/星期/节次/周次）
- 节次 × 星期周课表（含 `rowspan` 合并单元格）
- 单双周与 `.week` 字段
- 旧式 `<br>` 分隔课程单元格
- 解析结果通过 `FosuBridge.onCourses` 回传

## APK

```text
文件：dist/fosu-campus-assistant-1.2.0-debug.apk
大小：约 16 MB
包名：dev.campususage.fosu.debug
版本：1.2.0-dev（versionCode 3）
SHA-256：9fb4fc33909976d5742a0d834e07ddc174305ac35f2a8d18d9ab1491bfd19040
```

签名：Android Debug，APK Signature Scheme v2 验证通过。

权限检查：

```text
package: dev.campususage.fosu.debug
uses-permission: name='android.permission.INTERNET'
permission: dev.campususage.fosu.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='dev.campususage.fosu.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

`INTERNET` 仅用于 100 教务 WebView 和学校认证页面；`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 由 AndroidX 自动合并。

## 尚未完成 / 已知风险

当前环境没有 Android 模拟器、真机或学校测试账号，因此未完成：

1. 真实账号登录 100 教务的端到端验收；
2. 真实课表页面的 DOM 兼容性验证；
3. 节次从 100 教务到 14 小节的实际映射验证；
4. 窄屏、大字体和输入法遮挡的点击级 UI 验收；
5. GitHub 发布仓库的远程推送（等待仓库地址和授权）。

发布前建议在 Android 12+ 真机、校园网或 VPN 环境检查：

1. 100 教务 CAS 登录、验证码、登录后跳转；
2. 「打开课表」是否能定位学生课表入口；
3. 「抓取」是否识别实际页面；替换/追加是否正确；
4. 课程块是否显示名称、教师、地点、时间、周次；
5. 添加节假日，确认节假日课程标「调」并顺延；
6. 14 个小节、6 个大节及时间是否与校历一致；
7. 清除教务登录后是否回到登录页；
8. 360dp 窄屏和深色主题。

若学校升级 100 教务页面结构，抓取器可能需要更新；手动导入始终可用。
