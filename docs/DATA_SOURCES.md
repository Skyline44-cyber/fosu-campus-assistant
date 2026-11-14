# 学习通接入说明

## 入口与登录

- 学习通登录入口：<https://i.chaoxing.com/>
- 实际登录页：`https://passport2.chaoxing.com/mlogin?fid=&newversion=true&refer=...`
- 课表页面：<https://kb.chaoxing.com/pc/curriculum>
- 课表接口：`POST https://kb.chaoxing.com/pc/curriculum/getMyLessons`

App 使用 WebView 加载学习通官方登录页，由用户输入手机号 / 超星号和学习通密码，或使用学习通支持的短信、扫码登录。App 不复制登录表单、不读取输入框，也不保存密码。

学习通全部使用 HTTPS；App 不再放行任何明文流量。

## 登录态

- 登录 Cookie 由 Android `CookieManager` 保存，用于后续继续访问学习通。
- 用户可在「我的 → 清除学习通登录」删除 Cookie 和 WebStorage。
- 不把 Cookie、密码或页面 HTML 上传到任何服务器。
- 同步课表时，Cookie 只在 App 进程内传给 `ChaoxingClient`，随后用于学习通的 HTTPS 请求。

## 课表接口

请求：

```http
POST /pc/curriculum/getMyLessons HTTP/1.1
Host: kb.chaoxing.com
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
Cookie: <学习通 Cookie>
Referer: https://kb.chaoxing.com/pc/curriculum

week=6
```

- 不带 `week` 时只返回当前周；要获取全学期，需要按 `1..maxWeek` 逐周请求。
- `data.curriculum.maxWeek` 给出学期总周数。
- `data.curriculum.firstWeekDate` 是第 1 周周一的毫秒时间戳（北京时间）。
- `data.curriculum.lessonTimeConfigArray` 是学校真实节次时间，例如 `["8:00-8:50", ...]`。
- `data.lessonArray` 是当周课程，主要字段：

| 字段 | 含义 |
| --- | --- |
| `lessonId` | 课程安排 ID |
| `name` | 课程名称 |
| `teacherName` | 授课教师 |
| `location` | 上课地点 |
| `dayOfWeek` | 星期（1=周一 … 7=周日） |
| `beginNumber` | 开始节次（1 基） |
| `length` | 连续节数 |
| `weeks` | 当前请求周的周次；逐周合并后压缩为 `1-16` 等 |

App 只保留以下字段：

- 课程名称
- 授课教师
- 上课地点
- 星期
- 起止节次
- 周次

**不抓取备注、成绩、账号资料或其他页面内容。**

## 合并策略

每周响应中同一门课只出现一次，`weeks` 就是该周。App 以：

```text
lessonId + dayOfWeek + beginNumber + length + location + teacherName + name
```

作为合并键，把多周出现合并为一门课，再用 `WeekSpec.compress()` 压缩周次。教室或教师中途变化会拆成不同课程块。

## 节次与显示

- 节次时间直接使用 `lessonTimeConfigArray`，不使用本地猜测的 6 大节 / 14 小节。
- 默认显示 12 节；如果学校时间表更长，按实际节次数显示，上限 20。
- 周课表始终显示周一至周日，今天列高亮。
- 不自动推算法定节假日或调休；学习通返回哪一周有课，就显示哪一周。

## 已知限制

- 学习通登录页和课表接口可能随官方升级变化。
- 账号必须已绑定学生课表；教师账号或未绑定课表的账号可能返回空列表。
- 学习通可能要求验证码、短信或扫码；这些步骤由官方页面处理，App 不绕过。
- 校外访问学习通可能需要校园网或学校 VPN。
- App 不做密码保存、验证码识别、接口签名伪造或绕过授权。

## 手动导入

同步失败时，可在「课表 → 手动导入」粘贴：

```text
课程名|教师|地点|周一|1-2|1-16
```

或 JSON 数组，字段见 README。

## 佛大一卡通

电费仍通过企业微信「佛大一卡通」官方入口查看，再由用户导入余额或读数。该项目不是一卡通系统的一部分，也不接触一卡通账号。
