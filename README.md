# DayMark 每日打卡习惯追踪 APP

DayMark 是一个基于原生 Android 的每日打卡习惯追踪应用，支持账号登录、记住密码、事件增删改查、事件信息展示，以及拍照和相册选择图片。

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

- 登录功能：支持账号密码登录，支持记住密码。
- 注册功能：可选扩展功能，支持本地注册。
- 事件管理：支持新增、编辑、删除打卡事件。
- 事件展示：列表显示事件标题、内容、时间、累计打卡次数和图片。
- 打卡功能：点击事件中的“打卡”按钮会增加累计打卡次数并记录最近打卡时间。
- 图片功能：新增或编辑事件时可调用摄像头拍照，也可从相册选择图片。
- 本地保存：使用 SQLite 保存用户和事件数据，使用 SharedPreferences 保存记住密码状态。

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

