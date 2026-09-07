<div align="center">

# 重科课表

一款为**重庆科技大学**校友量身定制的开源、无广告、极简课程表 APP。

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://github.com/yuexps/cqust-schedule)

基于开源项目 [拾光课程表](https://github.com/XingHeYuZhuan/shiguangschedule) 定制开发。

</div>

---

## 特性

- **教务直连导入**：输入教务系统学号与密码，全自动登录树维教务系统导入学期课表。
- **自动静默更新**：进入应用时超期后台异步同步教务最新调课，无阻塞，异常静默兜底。
- **本地安全存储**：账号密码与课表数据仅加密保存在本设备，绝不上传任何第三方服务器。
- **Material You & 深色适配**：全面支持深色模式与动态取色，极简纯粹无广告。
- **日历与数据备份**：支持导出系统日历日程 (ICS)、本地 JSON 备份与恢复。

---

## 预览截图

| 周课表视图 | 课表个性化配置 | 桌面小组件 |
| :---: | :---: | :---: |
| ![周课表](/picture/Screenshot_1.png) | ![配置与深色模式](/picture/Screenshot_2.png) | ![小组件预览](/picture/Screenshot_3.png) |

---

## 快速上手

1. **安装**：在 Release 页面下载最新的 APK 安装包（支持 Android 8.0 及以上）。
2. **登录**：首次启动进入登录界面，输入重庆科技大学教务系统的学号与密码，点击“登录”。
3. **使用**：系统将自动拉取本学期课程并完成时间排布，直接进入主课表。
4. **重新同步**：在“我的”页面可随时查看已绑定学号，支持一键重新同步课表与退出登录。

---

## 项目构建

本工程基于 Kotlin Multiplatform (Compose Multiplatform) 开发：

- **开发工具**：Android Studio Ladybug / Koala 或更高版本
- **JDK 版本**：JDK 17+
- **构建命令**：
  ```bash
  # 构建 Android Debug APK
  ./gradlew :androidApp:assembleDebug
  ```

---

## 开源许可与致谢

本软件基于 GNU General Public License v3.0 (GPL-3.0) 开源。

本项目基于 [拾光课程表 (shiguangschedule)](https://github.com/XingHeYuZhuan/shiguangschedule) 衍生并深度定制，感谢原作者 [@XingHeYuZhuan](https://github.com/XingHeYuZhuan) 以及所有开源贡献者们的卓越付出！

### 原项目开发贡献者

<a href="https://github.com/XingHeYuZhuan/shiguangschedule/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=XingHeYuZhuan/shiguangschedule" />
</a>