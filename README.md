# DayMark 每日打卡习惯追踪 APP

DayMark 是一个基于原生 Android 的每日打卡习惯追踪应用，使用 Java + SQLite 实现，无第三方依赖。支持账号登录、习惯打卡、分类管理、每日提醒、数据统计与可视化，以及拍照和相册选择图片。

## 开发环境

- Android Studio Panda 2 | 2025.3.2
- Gradle 8.4
- Android Gradle Plugin 8.3.2
- JDK 17
- minSdk 23，targetSdk 34

## 默认账号

- 用户名：demo
- 密码：123456

也可以在登录页点击“注册新账号”创建账号。

## 主要功能

### 账号

- 登录：账号密码校验，登录成功进入主页。
- 注册：本地新建账号，用户名唯一。
- 记住密码：勾选后保存账号密码，下次自动填充；不勾选时默认填入 demo / 123456。

### 打卡事件管理

- 新增、编辑、删除打卡事件。
- 每个事件包含：标题、内容、时间说明、分类、提醒时间、图片。
- 分类：学习、运动、生活、工作、健康、其他。
- 打卡：记一次打卡，累计次数加一，可附带当次备注。
- 追加备注：不打卡也能单独补一条备注。
- 列表展示：今日状态、分类、连续天数、累计次数、提醒时间、最近备注、图片缩略图。

### 搜索与筛选

- 关键词搜索：匹配标题、内容、分类。
- 筛选：全部 / 今日待完成 / 今日已完成。
- 顶部汇总：事件总数、今日完成数、累计打卡次数、最高连续天数。

### 图片功能

- 调用系统相机拍照，或从相册选择图片，保存到应用私有目录。
- 点击列表缩略图可全屏预览。

### 数据统计与可视化

- 日历视图：当月网格，显示每天的打卡项数。
- 统计页：总事件数、今日完成数、总打卡记录数、今日完成率、最高连续打卡天数，以及最近 7 天每日打卡情况。
- 连续天数（streak）按自然日计算。

### 每日提醒

- 为事件设置提醒时间（HH:mm 格式），保存后通过 AlarmManager 调度每日通知。
- 删除事件时自动取消对应提醒。
- 提醒采用非精确闹钟（setInexactRepeating），在 Android 12+ 上无需申请精确闹钟权限。
- Android 13+ 需用户授予通知权限（POST_NOTIFICATIONS）后通知才会显示。

### 数据导出

- 一键导出全部事件与打卡记录为 txt 文件，保存在应用私有 Documents 目录。

### 本地存储

- 使用 SQLite 保存用户、事件、打卡记录三张表。
- 使用 SharedPreferences 保存记住密码状态。

## 项目结构

```text
app/src/main/java/com/example/daymark/
├── LoginActivity.java          登录 / 注册
├── MainActivity.java           主页：列表、搜索、筛选、汇总、入口
├── EditHabitActivity.java      新增 / 编辑事件
├── CalendarActivity.java       月历视图
├── StatsActivity.java          统计概览
├── ImagePreviewActivity.java   图片全屏预览
├── ReminderReceiver.java       提醒广播接收与调度
├── HabitAdapter.java           列表适配器（打卡 / 备注 / 编辑 / 删除）
├── DayMarkDbHelper.java        SQLite 数据层
├── Habit.java                  事件模型
├── CheckRecord.java            打卡记录模型
└── DateUtils.java              日期工具（自然日、连续天数等）
```

## 数据库表

- `users`：id、username、password。
- `habits`：id、title、content、time_text、image_uri、category、reminder_time、check_count、last_check_at、created_at。
- `check_records`：id、habit_id、note、checked_at。

## 运行方式

1. 用 Android Studio 打开项目根目录。
2. 等待 Gradle 同步完成。
3. 连接手机或启动模拟器。
4. 点击 Run 运行，或执行：

```powershell
.\gradlew.bat assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```
