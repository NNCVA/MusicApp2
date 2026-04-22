# MusicApp2

一个完全离线运行的 Android 本地音乐播放器，使用 Kotlin 开发，采用 `MVVM + Repository + Service` 架构。项目覆盖本地音频扫描、歌单管理、最近播放、歌词同步、专辑封面加载、通知栏控制，以及迷你播放器与全屏播放器联动。

## 项目概述

MusicApp2 面向 Android 8.0 到 Android 14，核心目标是把“本地媒体管理 + 后台稳定播放 + 复杂播放器交互”做成一条完整链路。

当前项目基线包括：

- 主入口为 `ContainerActivity`，主界面由歌曲、歌单、最近播放、扫描音乐等 Fragment 组成
- `PlaylistDetailActivity` 保留为独立页面，并复用主播放器侧的共享能力
- 播放器实例运行在前台播放服务中，页面通过 `PlayerManager` 统一控制和观察状态
- 全屏播放器采用 `封面 / 歌词 / 播放队列` 三页模型
- 迷你播放器与全屏播放器的上下切换由 `BottomSheetBehavior` 负责
- 全屏播放器内部左右切页由 `PlayerPageSwipeLayout` 负责

## 功能特性

- 完全本地播放，无网络依赖
- 扫描和管理设备中的音频文件
- 支持歌单创建、重命名、删除、批量加歌
- 支持最近播放记录
- 支持顺序播放、随机播放、单曲循环
- 支持外部 `.lrc` 歌词与音频内嵌歌词
- 支持专辑封面加载与本地元数据兜底提取
- 支持底部迷你播放器、全屏播放器、播放队列联动
- 支持通知栏控制、媒体按键响应和后台持续播放

当前扫描链路兼容的主流音频格式：

- `mp3`
- `m4a`
- `ogg`
- `wav`
- `flac`
- `aac`
- `wma`

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 开发语言 | Kotlin 1.9.21 |
| 构建工具 | AGP 8.2.0 / Gradle 8.2 / JDK 17 |
| 最低系统 | Android 8.0 `API 26` |
| 目标系统 | Android 14 `API 34` |
| 架构 | MVVM + Repository |
| 播放 | Media3 ExoPlayer 1.4.0 |
| 数据存储 | Room 2.6.1 |
| 图片加载 | Glide 4.16.0 |
| 元数据解析 | Jaudiotagger 2.2.5 |
| 生命周期与异步 | LiveData / ViewModel / Coroutines |
| 调试质量 | LeakCanary 2.12（debug） |

## 架构说明

项目按三层职责组织：

- 数据层：`Room + DAO + MusicRepository`
- 服务层：`MusicPlaybackService + PlayerManager`
- UI 层：`ContainerActivity + Fragment` 与 `PlaylistDetailActivity`

播放器主链路：

```text
UI
 -> PlayerManager
 -> PlayerServiceConnection
 -> MusicPlaybackService
 -> ExoPlayer
```

核心职责拆分：

- `MusicPlaybackService`：持有播放器实例，负责前台服务、通知、媒体会话、播放控制
- `PlayerManager`：统一服务连接、状态同步、页面侧播放控制入口
- `MusicRepository`：统一管理歌曲、歌单、最近播放等本地数据
- `PlayerLyricsController`：歌词加载、解析、高亮、同步滚动
- `PlayerViewSwipeController`：三页播放器状态与纵向滚动宿主切换
- `QueueSectionBinder`：播放队列绑定、当前项定位、队列区刷新

## 全屏播放器交互基线

全屏播放器当前采用三页非循环横滑模型：

- `封面 <-> 歌词 <-> 播放队列`
- 边界页继续向外滑动只回弹，不跨页
- `btnShowLyrics` 为顺序切页按钮：`封面 -> 歌词 -> 播放队列 -> 封面`

交互规则：

- `BottomSheetBehavior` 负责迷你播放器和全屏播放器之间的上下展开/折叠
- `PlayerPageSwipeLayout` 负责全屏内部左右切页
- 歌词页必须同时支持左右切页、上下滚动歌词、边界时把纵向手势交还给外层
- 播放队列页必须同时支持左右切页、`RecyclerView` 自身上下滚动、边界时允许外层接管

针对播放队列页滚动问题，当前实现已将“当前纵向滚动宿主”切换收敛到共享控制器中，避免 `BottomSheetBehavior` 把错误页面当成当前 `nested scrolling child`。

## 本地媒体处理

项目对歌词和封面都采用了双通道兜底策略：

- 专辑封面：优先走 `MediaStore albumId`，失败后回退到音频文件元数据提取
- 歌词：优先查找外部 `.lrc` 文件，失败后回退到音频文件内嵌歌词
- 歌词定位：解析为带时间戳的有序列表后，使用二分查找定位当前歌词行

这套链路的目标不是追求花哨效果，而是提升本地媒体来源复杂时的兼容性和稳定性。

## 稳定性与质量

项目已沉淀的稳定性实践包括：

- `PlayerManager` 的服务连接绑定 `applicationContext`，避免单例误持有页面对象
- Fragment 的 `RecyclerView` 在 `onDestroyView()` 断开 `adapter`
- 延时任务具名化，并在页面销毁时移除回调
- 页面级播放器控制器提供显式 `release()`，统一释放 observer、adapter、callback 和手势对象
- debug 构建集成 `LeakCanary`，用于排查播放器和页面生命周期泄漏

当前单元测试覆盖的重点逻辑包括：

- `LyricsParser`
- `FormatUtils`
- `PlayMode`
- `Song`
- `PageSettleCalculator`
- `PlayerNestedScrollTargetResolver`

## 工程化验证

仓库已补齐 Gradle Wrapper，并配置 GitHub Actions 持续集成。当前 CI 基础验证链如下：

- `lint`
- `testDebugUnitTest`
- `assembleDebug`

CI 会自动上传两类产物：

- `app-debug`：Debug APK
- `android-verification-reports`：Lint 与单元测试报告

这意味着项目不仅能在本地运行，也具备基础的仓库级验证能力。

## 项目结构

```text
app/src/main/java/com/musicplayer/
├── data/
│   ├── dao/
│   ├── database/
│   ├── model/
│   └── repository/
├── service/
│   ├── MusicPlaybackService.kt
│   └── PlayerManager.kt
├── ui/
│   ├── base/
│   ├── main/
│   ├── playlist/
│   ├── recent/
│   ├── scan/
│   ├── adapter/
│   ├── common/
│   ├── dialog/
│   └── widget/
├── util/
│   ├── media/
│   ├── system/
│   └── ui/
└── MusicPlayerApplication.kt
```

## 构建与运行

常用命令：

```bash
gradlew assembleDebug
gradlew installDebug
gradlew assembleRelease
gradlew testDebugUnitTest
```

Windows：

```bash
gradlew.bat assembleDebug
```

Linux / macOS：

```bash
./gradlew assembleDebug
```

## 权限说明

项目当前涉及的主要权限：

- `READ_MEDIA_AUDIO`：Android 13+ 读取音频文件
- `READ_EXTERNAL_STORAGE`：Android 12 及以下读取音频文件
- `POST_NOTIFICATIONS`：Android 13+ 播放通知
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`

## 代码阅读入口

如果第一次接触这个项目，建议按下面顺序阅读：

1. `app/src/main/java/com/musicplayer/ui/main/ContainerActivity.kt`
2. `app/src/main/java/com/musicplayer/service/PlayerManager.kt`
3. `app/src/main/java/com/musicplayer/service/MusicPlaybackService.kt`
4. `app/src/main/java/com/musicplayer/ui/main/PlayerViewSwipeController.kt`
5. `app/src/main/java/com/musicplayer/ui/widget/PlayerPageSwipeLayout.kt`
6. `app/src/main/java/com/musicplayer/ui/main/PlayerLyricsController.kt`
7. `app/src/main/java/com/musicplayer/ui/main/QueueSectionBinder.kt`
8. `app/src/main/java/com/musicplayer/data/repository/MusicRepository.kt`

仓库内补充文档入口：

- `.zread/wiki/current`
- `AGENTS.md`

## 说明

README 只保留会长期影响实现决策的稳定事实。一次性调试过程、临时现象和局部实验记录不放在这里，相关内容以项目图谱和专题笔记为准。
