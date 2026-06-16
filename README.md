# DayMark - 习惯打卡应用

<div align="center">

**一款简洁优雅的习惯养成与打卡管理应用**

[![Android](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com)
[![API](https://img.shields.io/badge/API-23%2B-blue.svg)](https://android-arsenal.com/api?level=23)
[![Version](https://img.shields.io/badge/Version-1.4-orange.svg)](https://github.com)
[![Build](https://img.shields.io/badge/Build-Passing-success.svg)](https://github.com)

</div>

---

## 📱 应用简介

DayMark 是一款专注于习惯养成和日常打卡的 Android 应用。通过直观的数据可视化和流畅的交互体验，帮助用户建立良好习惯，追踪个人成长。

### ✨ 核心特性

- 📝 **习惯管理** - 创建、编辑、删除习惯事件，支持图片、分类、时间设置
- ✅ **打卡记录** - 一键打卡，支持添加备注，记录每一次坚持
- 📊 **数据统计** - 多维度数据分析，饼状图可视化分类占比
- 🔥 **热力图** - GitHub风格打卡热力图，直观展示坚持轨迹
- 🏅 **成就系统** - 多级成就勋章，激励持续打卡
- 👤 **个人中心** - 用户资料、头像、数据概览、勋章展示
- 💾 **数据备份** - JSON格式导入/导出，支持跨设备数据迁移
- 🎨 **动画效果** - 饼状图展开动画，提升视觉体验
- 🔐 **数据安全** - EncryptedSharedPreferences加密存储敏感信息


---

## 🎯 主要功能

### 1. 习惯管理
- 📌 习惯标题和详细说明
- 🖼️ 自定义习惯图片（拍照或相册选择）
- 🏷️ 分类标签（学习、运动、生活、工作、健康、其他）
- ⏰ 打卡时间和提醒设置
- 🎯 目标天数和频率配置

### 2. 打卡记录
- 一键完成今日打卡
- 支持添加打卡备注
- 自动更新连续天数
- 查看所有打卡历史

### 3. 数据统计
- 总体数据：事件数、完成率、累计打卡、最高连续天数
- **饼状图**：各分类打卡次数占比，带800ms展开动画
- 本周概览：每日打卡次数

### 4. 打卡热力图
- GitHub风格的热力图（最近27周）
- 5级颜色深度表示打卡强度
- **可点击**：点击任意日期查看当天打卡详情

### 5. 成就系统
- 🥉 初级成就：初次打卡、初创事件、连续三天
- 🥈 中级成就：坚持一周、多样化、分类探索
- 🥇 高级成就：月度坚持、百日打卡、目标达成

### 6. 个人中心
- 自定义头像（上传或纯色默认头像）
- 数据概览（2x2卡片布局）
- 打卡热力图（可点击查看详情）
- **分类统计**（可点击跳转到详细统计页）
- 个人勋章预览

### 7. 数据管理
- 导出：TXT文本 + JSON备份
- **导入**：从JSON备份文件恢复数据
- 支持跨设备数据迁移

---

## 🏗️ 技术架构

### 核心技术栈
- **开发语言**: Java 11
- **最低API**: Android 6.0 (API 23)
- **目标API**: Android 14 (API 34)
- **数据库**: SQLite (WAL模式)
- **UI框架**: Material Design 3
- **加密存储**: AndroidX Security Crypto

### 数据库设计
**数据库版本**: 4  
**WAL模式**: 启用（支持并发读写）

**主要表结构**：
- `users` - 用户表（账号、密码哈希、昵称、头像）
- `habits` - 习惯表（标题、分类、图片、提醒等）
- `check_records` - 打卡记录表（习惯ID、时间、备注）

### 线程模型
- **AppExecutors线程池**：3个IO线程（数据库+图片加载）
- **@WorkerThread注解**：约30个数据库方法，静态检查防止主线程阻塞

### 自定义View
- **PieChartView**：饼状图，支持动画
- **HeatmapView**：热力图，支持点击交互
- **ImageLoader**：图片加载，LruCache缓存

---

## 🚀 开发历程

### v1.4 - 交互增强版 (2026-06-16)
- ✨ 饼状图添加800ms展开动画
- 👆 分类统计卡片可点击跳转
- 🔍 热力图方块可点击查看详情
- 🔧 修复热力图点击不生效问题
- ⚡ 升级Java版本至11，消除编译警告

### v1.3 - 数据可视化版 (2026-06-16)
- 📊 打卡统计页面添加饼状图
- 🎨 自定义PieChartView实现
- 📈 分类打卡次数占比展示

### v1.2 - UI优化版 (2026-06-16)
- 🔄 "账号"按钮改为"个人中心"
- 📂 导入备份功能移至个人中心
- 🗂️ 新增"数据管理"分类

### v1.1 - Bug修复版 (2026-06-16)
- ✅ 添加@WorkerThread注解（约30个方法）
- ⚡ 线程池优化（单线程 → 3线程池）
- 🗑️ 图片文件自动清理机制
- 💾 数据导入/恢复功能实现

---

## 🔧 构建说明

### 环境要求
- **JDK**: 11+
- **Android Studio**: 2023.1+
- **Gradle**: 8.0+
- **Android SDK**: API 34

### 构建步骤

**构建Debug版本**：
```bash
./gradlew assembleDebug
```
输出: `app/build/outputs/apk/debug/app-debug.apk`

**构建Release版本**：
```bash
./gradlew assembleRelease
```
输出: `app/build/outputs/apk/release/app-release-unsigned.apk` (4.8 MB)

**清理构建**：
```bash
./gradlew clean
```

---

## 📝 使用指南

### 初次使用
1. **注册账号** - 输入用户名和密码（至少6位）
2. **创建习惯** - 点击"+"按钮，填写习惯信息
3. **开始打卡** - 点击习惯卡片的"✓"按钮

### 数据迁移
**导出数据**：主页 → 导出按钮 → 生成JSON备份文件  
**导入数据**：个人中心 → 导入备份 → 选择JSON文件

### 查看统计
- **总体统计**：主页 → 统计按钮
- **热力图详情**：个人中心 → 点击热力图方块
- **分类详情**：个人中心 → 点击分类统计卡片

---

## 📂 项目结构

```
DayMarkApp/
├── app/src/main/java/com/example/daymark/
│   ├── MainActivity.java           # 主页面
│   ├── LoginActivity.java          # 登录页面
│   ├── StatsActivity.java          # 统计页面
│   ├── ProfileActivity.java        # 个人中心
│   ├── DayMarkDbHelper.java        # 数据库管理
│   ├── PieChartView.java           # 饼状图View
│   ├── HeatmapView.java            # 热力图View
│   ├── ImageLoader.java            # 图片加载器
│   ├── AppExecutors.java           # 线程池管理
│   └── ...
├── BUG_REPORT.md                   # Bug报告
├── FIXES_COMPLETED.md              # Bug修复报告
├── UI_ADJUSTMENTS.md               # UI调整报告
├── PIE_CHART_FEATURE.md            # 饼状图功能报告
└── README.md                       # 项目说明
```

---

## 📊 代码统计

- **Java文件**: 25+
- **代码行数**: 约5000+
- **自定义View**: 3个
- **Activity**: 9个

**v1.1-v1.4增量**：+723行代码

---

## 🔒 安全性

- ✅ 登录会话使用EncryptedSharedPreferences加密
- ✅ 密码使用PBKDF2WithHmacSHA256加盐哈希
- ✅ 文件使用FileProvider保护URI
- ✅ 所有数据本地存储，不上传外部服务器

---

## 📈 未来计划

### 短期优化
- [ ] 习惯拖拽排序
- [ ] 多时段提醒
- [ ] 更多图表类型
- [ ] 深色模式

### 长期目标
- [ ] 云同步功能
- [ ] Widget桌面小部件
- [ ] 好友监督系统
- [ ] AI智能建议

---

## 👨‍💻 开发信息

**开发工具**: Claude Code (Opus 4.8)  
**开发时间**: 2026年6月  
**当前版本**: v1.4  
**构建状态**: ✅ BUILD SUCCESSFUL

---

## 📄 许可证

本项目仅供学习和个人使用。

---

<div align="center">

**⭐ 如果觉得项目不错，欢迎Star支持！**

Made with ❤️ by Claude Code

</div>
