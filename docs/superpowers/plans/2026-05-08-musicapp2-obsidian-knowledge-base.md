# MusicApp2 Obsidian Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `MusicApp2/.zread/wiki/current` 指向的 `27` 篇项目文档重组为 Obsidian 可直接浏览的一次性知识库交付，包括有序目录、总索引页和大尺寸高对比度 canvas。

**Architecture:** 执行顺序固定为“源文档映射与目录落位 → 分批生成 Markdown 笔记 → 生成总览 canvas → 进行数量、一致性和可读性验证”。Obsidian 侧始终以 `.zread/wiki/current` 为唯一事实来源，只允许做版式重排、互链增强、顺序导航和知识图谱重绘，不允许新增脱离 zread 的独立事实。

**Tech Stack:** Obsidian Flavored Markdown, JSON Canvas, PowerShell, zread markdown source files, optional Obsidian CLI for read verification

---

## File Map

### Source Of Truth

- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\current`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\wiki.json`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\1-xiang-mu-gai-lan.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\2-huan-jing-da-jian-yu-yun-xing.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\3-san-ceng-jia-gou-zong-lan-shu-ju-ceng-fu-wu-ceng-ui-ceng.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\4-bo-fang-qi-zhu-lian-lu-cong-ui-dao-exoplayer-de-shu-ju-liu.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\5-room-shu-ju-ku-yu-shi-ti-mo-xing-she-ji.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\6-dao-jie-kou-yu-cha-xun-ce-lue.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\7-musicrepository-tong-shu-ju-fang-wen.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\8-musicplaybackservice-qian-tai-bo-fang-fu-wu.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\9-playermanager-bo-fang-zhuang-tai-guan-li-dan-li.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\10-bo-fang-mo-shi-qie-huan-yu-dui-lie-kong-zhi.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\11-containeractivity-zhu-rong-qi-yu-dao-hang.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\12-fragment-ye-mian-yu-shu-ju-guan-cha.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\13-ge-dan-xiang-qing-ye-du-li-jiao-hu-she-ji.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\14-feng-mian-ge-ci-bo-fang-dui-lie-san-ye-heng-hua-mo-xing.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\15-playerpageswipelayout-zi-ding-yi-hua-dong-kong-jian.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\16-shou-shi-zhong-cai-heng-xiang-fan-ye-yu-zong-xiang-gun-dong-de-bian-jie-qie-huan.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\17-mi-ni-bo-fang-qi-yu-quan-ping-bo-fang-qi-shang-xia-qie-huan.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\18-yin-le-wen-jian-sao-miao-yu-mediastore-ji-cheng.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\19-ge-ci-jie-xi-yu-shi-shi-tong-bu-gao-liang.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\20-zhuan-ji-feng-mian-jia-zai-de-shuang-tong-dao-dou-di-ce-lue.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\21-qian-tai-fu-wu-tong-zhi-yu-mediasession.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\22-quan-xian-guan-li-yu-android-ban-ben-gua-pei.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\23-fragment-yu-bo-fang-qi-zi-yuan-shi-fang-shi-jian.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\24-yin-liang-dan-ru-dan-chu-yu-qie-huan-fang-dou.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\25-gradle-gou-jian-pei-zhi-yu-ban-ben-guan-li.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\26-github-actions-ci-liu-shui-xian.md`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\27-dan-yuan-ce-shi-fu-gai-yu-bian-xie-gui-fan.md`

### Target Root

- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 文档索引.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 架构知识图谱.canvas`

### Target Directories

- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障`

### Target Markdown Files

- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门\01-项目概览.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门\02-环境搭建与运行.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\03-三层架构总览.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\04-播放器主链路.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\05-Room 数据库与实体模型设计.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\06-DAO 接口与查询策略.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\07-MusicRepository 统一数据访问.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\08-MusicPlaybackService 前台播放服务.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\09-PlayerManager 播放状态管理单例.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\10-播放模式切换与队列控制.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\11-ContainerActivity 主容器与导航.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\12-Fragment 页面与数据观察.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\13-歌单详情页独立交互设计.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\14-封面 歌词 播放队列三页横滑模型.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\15-PlayerPageSwipeLayout 自定义滑动控件.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\16-手势仲裁 横向翻页与纵向滚动的边界切换.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\17-迷你播放器与全屏播放器上下切换.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理\18-音乐文件扫描与 MediaStore 集成.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理\19-歌词解析与实时同步高亮.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理\20-专辑封面加载的双通道兜底策略.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成\21-前台服务通知与 MediaSession.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成\22-权限管理与 Android 版本适配.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期\23-Fragment 与播放器资源释放实践.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期\24-音量淡入淡出与切换防抖.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障\25-Gradle 构建配置与版本管理.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障\26-GitHub Actions CI 流水线.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障\27-单元测试覆盖与编写规范.md`

## Chunk 1: Prepare Mapping And Navigation Skeleton

### Task 1: Confirm source mapping and prepare the target directory tree

**Files:**
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\current`
- Verify: `F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\wiki.json`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期`
- Create: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障`

- [ ] **Step 1: Read the zread version pointer and page map**

Run:

```powershell
Get-Content -LiteralPath 'F:\C2\application\repo\MusicApp2\.zread\wiki\current' -Encoding utf8
Get-Content -LiteralPath 'F:\C2\application\repo\MusicApp2\.zread\wiki\versions\2026-05-05-003445\wiki.json' -Encoding utf8
```

Expected:

- `current` points to `versions/2026-05-05-003445`
- `wiki.json` lists `27` pages and the `7` approved sections

- [ ] **Step 2: Create the ordered Obsidian directory tree**

Run:

```powershell
New-Item -ItemType Directory -Force -Path 'D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门'
New-Item -ItemType Directory -Force -Path 'D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计'
New-Item -ItemType Directory -Force -Path 'D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互'
New-Item -ItemType Directory -Force -Path 'D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理'
New-Item -ItemType Directory -Force -Path 'D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成'
New-Item -ItemType Directory -Force -Path 'D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期'
New-Item -ItemType Directory -Force -Path 'D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障'
```

Expected:

- all `7` target directories exist
- re-running the commands is idempotent

- [ ] **Step 3: Verify the directory layout before writing content**

Run:

```powershell
Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2' -Force | Select-Object Name,Mode
```

Expected:

- the root contains the `7` ordered directories
- the root is ready for `MusicApp2 文档索引.md` and `MusicApp2 架构知识图谱.canvas`

### Task 2: Create the index page and the shared navigation contract

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 文档索引.md`

- [ ] **Step 1: Write the frontmatter and source note**

Add:

- `title: MusicApp2 文档索引`
- tags for `MusicApp2` and `MOC`
- a short callout that states the source comes from `.zread`

- [ ] **Step 2: Build the seven ordered sections with exact note links**

Each section must:

- use the numbered directory title
- list the notes in zread order
- include one short description per note

- [ ] **Step 3: Add the graph entry link**

At the bottom, add a tip callout linking `[[MusicApp2 架构知识图谱.canvas]]`.

- [ ] **Step 4: Verify the index link names match the exact target files**

Run:

```powershell
Get-Content -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 文档索引.md' -Encoding utf8
```

Expected:

- all `27` note links are present
- no link points to the old zread slug names

## Chunk 2: Generate Fast Start And Architecture Notes

### Task 3: Create the fast-start notes

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门\01-项目概览.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门\02-环境搭建与运行.md`

- [ ] **Step 1: Read the two source markdown files**

Use:

- `1-xiang-mu-gai-lan.md`
- `2-huan-jing-da-jian-yu-yun-xing.md`

Carry over:

- main summary
- tables
- code fences
- `Sources`

- [ ] **Step 2: Rewrite both pages into the approved Obsidian template**

Each page must include:

- frontmatter
- a concise opening summary
- retained diagrams or code blocks
- page-end links to index, previous, next, and related documents

- [ ] **Step 3: Verify the directory now contains exactly two markdown notes**

Run:

```powershell
Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门' -Filter '*.md' | Select-Object Name
```

Expected:

- only `01-项目概览.md`
- only `02-环境搭建与运行.md`

### Task 4: Create architecture notes for the data and repository chain

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\03-三层架构总览.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\04-播放器主链路.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\05-Room 数据库与实体模型设计.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\06-DAO 接口与查询策略.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\07-MusicRepository 统一数据访问.md`

- [ ] **Step 1: Read the five matching zread source pages**

Use source files `3` through `7`.

- [ ] **Step 2: Preserve the architecture diagrams and convert cross-references to wikilinks**

Do not drop:

- three-layer architecture diagrams
- class diagrams
- repository code snippets
- source references

- [ ] **Step 3: Verify that the architecture directory now includes notes `03` through `07`**

Run:

```powershell
Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计' -Filter '*.md' | Select-Object Name
```

Expected:

- files `03` to `07` exist with exact approved names

### Task 5: Create architecture notes for service and UI coordination

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\08-MusicPlaybackService 前台播放服务.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\09-PlayerManager 播放状态管理单例.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\10-播放模式切换与队列控制.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\11-ContainerActivity 主容器与导航.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\12-Fragment 页面与数据观察.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\13-歌单详情页独立交互设计.md`

- [ ] **Step 1: Read the six matching zread source pages**

Use source files `8` through `13`.

- [ ] **Step 2: Keep the player-service facts aligned with repository memory**

Retain and surface clearly:

- `UI → PlayerManager → PlayerServiceConnection → MusicPlaybackService → ExoPlayer`
- service responsibilities
- `applicationContext` binding constraint
- `ContainerActivity` and `PlaylistDetailActivity` coordination

- [ ] **Step 3: Verify the architecture directory now contains exactly eleven notes**

Run:

```powershell
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计' -Filter '*.md' | Measure-Object).Count
```

Expected:

- output is `11`

## Chunk 3: Generate Interaction, Media, System, And Lifecycle Notes

### Task 6: Create the full-screen player interaction notes

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\14-封面 歌词 播放队列三页横滑模型.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\15-PlayerPageSwipeLayout 自定义滑动控件.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\16-手势仲裁 横向翻页与纵向滚动的边界切换.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\17-迷你播放器与全屏播放器上下切换.md`

- [ ] **Step 1: Read source files `14` through `17`**

These pages define the interaction model and must drive the canvas interaction section later.

- [ ] **Step 2: Convert the interaction pages into Obsidian-friendly reading blocks**

Surface clearly:

- page order
- non-cycling edge behavior
- nested-scroll host facts
- `BottomSheetBehavior` versus horizontal swipe responsibility split

- [ ] **Step 3: Verify the interaction directory contains exactly four notes**

Run:

```powershell
Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互' -Filter '*.md' | Select-Object Name
```

Expected:

- exactly `4` files exist with the approved names

### Task 7: Create the local media processing notes

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理\18-音乐文件扫描与 MediaStore 集成.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理\19-歌词解析与实时同步高亮.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理\20-专辑封面加载的双通道兜底策略.md`

- [ ] **Step 1: Read source files `18` through `20`**

Retain:

- scan pipeline
- lyric parser details
- album art fallback strategy

- [ ] **Step 2: Preserve the technical diagrams and performance-sensitive details**

Do not simplify away:

- binary search lyric sync
- external LRC versus embedded lyric order
- MediaStore and Jaudiotagger fallback chain

- [ ] **Step 3: Verify the media directory contains exactly three notes**

Run:

```powershell
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理' -Filter '*.md' | Measure-Object).Count
```

Expected:

- output is `3`

### Task 8: Create the system-integration and lifecycle notes

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成\21-前台服务通知与 MediaSession.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成\22-权限管理与 Android 版本适配.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期\23-Fragment 与播放器资源释放实践.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期\24-音量淡入淡出与切换防抖.md`

- [ ] **Step 1: Read source files `21` through `24`**

Focus on system hooks, lifecycle cleanup, and playback stability constraints.

- [ ] **Step 2: Make the cleanup and stability rules explicit in note structure**

Highlight:

- notification and `MediaSession`
- permission and version-specific behavior
- fragment cleanup requirements
- fade and debounce behavior

- [ ] **Step 3: Verify both directories contain the exact expected counts**

Run:

```powershell
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成' -Filter '*.md' | Measure-Object).Count
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期' -Filter '*.md' | Measure-Object).Count
```

Expected:

- system integration count is `2`
- lifecycle count is `2`

## Chunk 4: Generate Engineering Notes, Canvas, And Final Verification

### Task 9: Create the engineering quality notes

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障\25-Gradle 构建配置与版本管理.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障\26-GitHub Actions CI 流水线.md`
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障\27-单元测试覆盖与编写规范.md`

- [ ] **Step 1: Read source files `25` through `27`**

Keep:

- version catalog details
- CI flow
- test strategy

- [ ] **Step 2: Rewrite the three pages into the common note template**

Make sure each page still carries:

- relevant tables
- build or CI diagrams
- `Sources`
- links back to player architecture where appropriate

- [ ] **Step 3: Verify the engineering directory contains exactly three notes**

Run:

```powershell
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障' -Filter '*.md' | Measure-Object).Count
```

Expected:

- output is `3`

### Task 10: Create the high-contrast large canvas

**Files:**
- Create or replace: `D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 架构知识图谱.canvas`

- [ ] **Step 1: Build the JSON Canvas node skeleton with four major groups**

Required groups:

- index and reading path
- architecture main chain
- full-screen player interaction
- media and engineering support

- [ ] **Step 2: Add file nodes for the key overview pages**

At minimum include file nodes for:

- `MusicApp2 文档索引.md`
- `01-项目概览.md`
- `03-三层架构总览.md`
- `04-播放器主链路.md`
- `09-PlayerManager 播放状态管理单例.md`
- `08-MusicPlaybackService 前台播放服务.md`
- `11-ContainerActivity 主容器与导航.md`
- `14-封面 歌词 播放队列三页横滑模型.md`
- `19-歌词解析与实时同步高亮.md`
- `20-专辑封面加载的双通道兜底策略.md`
- `25-Gradle 构建配置与版本管理.md`

- [ ] **Step 3: Enforce the readability constraints from the spec**

Ensure:

- canvas spread is roughly `5600 × 3800` or larger
- node sizes are readable
- inter-group spacing is wide
- colors use dark, high-contrast groups

- [ ] **Step 4: Validate the canvas JSON**

Run:

```powershell
Get-Content -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 架构知识图谱.canvas' -Encoding utf8 | ConvertFrom-Json | Out-Null
```

Expected:

- no JSON parse error

### Task 11: Run the final knowledge-base verification pass

**Files:**
- Verify only

- [ ] **Step 1: Verify the final markdown count**

Run:

```powershell
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2' -Recurse -Filter '*.md' | Measure-Object).Count
```

Expected:

- output is `28` for `27` topic notes plus `1` index note

- [ ] **Step 2: Verify the directory counts by section**

Run:

```powershell
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门' -Filter '*.md' | Measure-Object).Count
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计' -Filter '*.md' | Measure-Object).Count
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互' -Filter '*.md' | Measure-Object).Count
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理' -Filter '*.md' | Measure-Object).Count
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成' -Filter '*.md' | Measure-Object).Count
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期' -Filter '*.md' | Measure-Object).Count
(Get-ChildItem -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障' -Filter '*.md' | Measure-Object).Count
```

Expected:

- counts are `2, 11, 4, 3, 2, 2, 3`

- [ ] **Step 3: Verify Obsidian-facing navigation in the index page**

If Obsidian is running, run:

```powershell
obsidian read path="项目概述/MusicApp2/MusicApp2 文档索引.md"
```

Expected:

- the note reads successfully
- all section headings and links render as expected

- [ ] **Step 4: Verify the canvas file exists and parses**

Run:

```powershell
Get-Item -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 架构知识图谱.canvas'
Get-Content -LiteralPath 'D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 架构知识图谱.canvas' -Encoding utf8 | ConvertFrom-Json | Out-Null
```

Expected:

- the canvas file exists
- JSON parsing succeeds

- [ ] **Step 5: Perform the manual visual pass in Obsidian**

Confirm:

- the index page works as the main entry
- previous and next note navigation is correct
- the canvas is visually readable without crowded central overlap
- dark group colors and text contrast are easy to read
