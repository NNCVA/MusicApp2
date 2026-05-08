# MusicApp2 Obsidian Knowledge Base Design

**Date:** 2026-05-08

**Project:** `MusicApp2`

## Summary

本设计定义 `MusicApp2` 项目的 Obsidian 知识库重构方案：以 `.zread/wiki/current` 指向的当前版本为唯一事实来源，将现有 `27` 篇 zread 文档重组为适合 Obsidian 浏览和面试复习的一次性交付知识库。

最终交付固定为：

- `1` 个总索引页
- `27` 篇顺序笔记
- `1` 张大尺寸总览 canvas

交付目标不是新增知识，而是让 zread 文档在 Obsidian 中更容易浏览、跳转和建立全局认知。

## Problem

当前 `MusicApp2/.zread` 已经包含完整的项目文档，但存在三个明显问题：

- 最新结构需要运行 zread 命令并通过网页查看，使用成本高
- zread 文档更适合线性阅读，不适合在 Obsidian 中作为长期知识入口
- 缺少像 `ChatPPP` 那样的总索引与大尺寸知识图谱，难以快速建立项目全局视角

用户已经明确希望最终形态参考 `D:\obsidian\repo\Android project\项目概述\ChatPPP`，但目录必须带顺序编号，单篇文件名采用 `01-项目概览.md` 这种命名方式，正文则要改成更适合 Obsidian 阅读的版式。

## Source Of Truth

本设计将以下路径定义为唯一事实来源：

- `F:\C2\application\repo\MusicApp2\.zread\wiki\current`

当前 `current` 文件内容指向：

- `versions/2026-05-05-003445`

该版本对应的 `wiki.json` 共 `27` 篇页面，分组如下：

- `快速上手`：`2` 篇
- `架构设计`：`11` 篇
- `全屏播放器交互`：`4` 篇
- `本地媒体处理`：`3` 篇
- `通知与系统集成`：`2` 篇
- `稳定性与生命周期`：`2` 篇
- `工程化与质量保障`：`3` 篇

Obsidian 侧只允许做重组、互链、版式增强和知识图谱重绘，不允许新增脱离该来源的独立事实结论。

## Goals

- 在 Obsidian 中为 `MusicApp2` 建立一个可直接浏览的知识入口
- 保留 zread 文档的主题边界、主要内容、图表和来源引用
- 通过顺序目录、双链和索引页提升查阅效率
- 提供一张比 `ChatPPP` 更疏朗、更高对比度的大尺寸 canvas
- 将交付收敛为一次性完整重构，不为长期增量维护设计复杂机制

## Non-Goals

- 不扩写 zread 之外的新专题
- 不新增独立的技术推断、架构结论或面试话术页
- 不拆分出更多细粒度专题子页
- 不做自动同步脚本或双向同步机制
- 不修改 `MusicApp2` 业务代码或现有播放器实现

## Approved Information Architecture

目标目录固定在：

- `D:\obsidian\repo\Android project\项目概述\MusicApp2`

目录结构固定为：

```text
项目概述/
└── MusicApp2/
    ├── MusicApp2 文档索引.md
    ├── MusicApp2 架构知识图谱.canvas
    ├── 01-快速入门/
    ├── 02-架构设计/
    ├── 03-全屏播放器交互/
    ├── 04-本地媒体处理/
    ├── 05-通知与系统集成/
    ├── 06-稳定性与生命周期/
    └── 07-工程化与质量保障/
```

各目录与 zread 分组采用一一对应关系：

| Obsidian 目录 | zread section | 篇数 |
|---|---|---:|
| `01-快速入门` | `快速上手` | 2 |
| `02-架构设计` | `架构设计` | 11 |
| `03-全屏播放器交互` | `全屏播放器交互` | 4 |
| `04-本地媒体处理` | `本地媒体处理` | 3 |
| `05-通知与系统集成` | `通知与系统集成` | 2 |
| `06-稳定性与生命周期` | `稳定性与生命周期` | 2 |
| `07-工程化与质量保障` | `工程化与质量保障` | 3 |

## File Naming Rules

文件命名规则固定为“顺序号 + 中文标题”，不带 `MusicApp2-` 前缀，例如：

- `01-项目概览.md`
- `02-环境搭建与运行.md`
- `03-三层架构总览.md`

目录内序号与全局 zread 页面序号保持一致，不因分目录重新编号。

## Page Template Rules

每篇笔记使用统一的 Obsidian 友好模板。

### Frontmatter

每篇笔记顶部使用最小必要 frontmatter，只保留：

- `title`
- `tags`
- `aliases`

不加入无实际用途的冗余属性。

### Body Structure

每篇正文按以下规则组织：

1. 首段用简短概述承接 zread 原文，说明该文档覆盖的主题和阅读价值
2. 中段保留 zread 的核心结构，包括：
   - `mermaid` 图
   - 关键代码片段
   - 关键表格
   - `Sources`
3. 长段落允许按 Obsidian 阅读习惯拆成更易扫读的内容块
4. 页尾统一增加导航区，包含：
   - 返回 `[[MusicApp2 文档索引]]`
   - 上一篇
   - 下一篇
   - 相关文档

### Allowed Enhancements

允许的 Obsidian 增强仅限以下三类：

- `wikilink = Obsidian 内部双链`
- `callout = Obsidian 提示块`
- 页尾顺序导航

### Forbidden Enhancements

以下内容禁止加入：

- zread 未给出的新结论
- 与事实无关的装饰性空页面
- 多余的“后续待办”型笔记
- 为了凑知识图谱而硬拆出的独立主题

## Index Page Design

`MusicApp2 文档索引.md` 是知识库总入口，其职责固定为：

- 说明资料来源来自 `.zread`
- 按 `01` 到 `07` 的目录顺序列出全部文档
- 为每篇笔记给出一句面向浏览者的简短说明
- 在页尾提示打开 `[[MusicApp2 架构知识图谱.canvas]]`

索引页应承担主导航作用，而不是重复所有正文内容。

## Canvas Design

`MusicApp2 架构知识图谱.canvas` 只承载总览、主链路和分区跳转，不承载全部细节。

### Layout Principles

画布布局采用四大分区：

- 左上：索引与阅读路径
- 右上：架构主链路
- 左下：全屏播放器交互
- 右下：媒体处理与工程化支撑区

### Readability Constraints

为解决用户明确提出的“紧凑、颜色浅、看不清”问题，canvas 必须满足以下约束：

- 可视范围至少约 `5600 × 3800`
- 普通笔记节点约 `520 × 210`
- 主链路关键节点比普通节点更大
- 主分区之间至少保留约 `260px` 空白
- 使用深色分组背景与高对比文字
- 不把每一条细节节点都塞进画布中心

### Content Scope

canvas 中必须出现的核心节点包括：

- `MusicApp2 文档索引`
- `项目概览`
- `三层架构总览`
- `播放器主链路`
- `PlayerManager`
- `MusicPlaybackService`
- `ContainerActivity`
- `三页横滑模型`
- `歌词解析`
- `封面双通道策略`
- `Gradle 构建配置`

canvas 中不要求出现全部 `27` 篇页面的逐条可视化细节，但必须能从总览层面引导到关键阅读路径。

## Mapping From zread To Obsidian

映射规则固定为“同序号、同主题、同篇名”：

- zread 页面 `01` 映射为 Obsidian 的 `01-项目概览.md`
- zread 页面 `02` 映射为 Obsidian 的 `02-环境搭建与运行.md`
- 以此类推，直到 `27`

允许发生的变化只有：

- 所在目录从原始平铺结构重组为有序目录
- 正文从网页文档版式改写为 Obsidian 友好版式
- 补充双链、callout 和前后页导航

不允许发生的变化包括：

- 页面事实内容被删改为与 zread 不一致
- 页面主题被重新划分为 zread 不存在的新主题
- 文章顺序被打乱

## Delivery Boundary

这是一次性交付，不为高频增量维护设计额外结构。

交付边界固定为：

- `1` 个索引页
- `27` 篇笔记
- `1` 张大 canvas

后续只有在 `.zread` 明确更新时，才考虑整批重刷；当前不设计新增笔记工作流，也不预留复杂的同步辅助文件。

## Expected Files To Create Or Replace

本次实现预计会在以下位置创建或覆盖内容：

- `D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 文档索引.md`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\MusicApp2 架构知识图谱.canvas`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\01-快速入门\*.md`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\02-架构设计\*.md`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\03-全屏播放器交互\*.md`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\04-本地媒体处理\*.md`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\05-通知与系统集成\*.md`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\06-稳定性与生命周期\*.md`
- `D:\obsidian\repo\Android project\项目概述\MusicApp2\07-工程化与质量保障\*.md`

## Verification Plan

实现阶段完成后，验证不以 Android 构建为主，而以知识库一致性和可读性为主：

1. 文档数量验证
   - 索引页 `1` 篇
   - 主题页 `27` 篇
   - canvas `1` 个
2. 结构一致性验证
   - 目录分组与 zread section 对齐
   - 文件顺序与 zread 页面顺序对齐
   - 标题与原页面标题对齐
3. 内容一致性验证
   - 核心图表、代码片段、Sources 保留
   - 无脱离 zread 的新增事实
4. Obsidian 可读性验证
   - 索引页跳转正常
   - 前后页导航正常
   - canvas 可读、分区不拥挤、颜色对比足够

## Risks And Mitigations

- zread 原文内容较长，直接搬运会让 Obsidian 页面仍显臃肿
  - 处理方式：允许重排段落与阅读提示，但不改事实边界

- canvas 如果试图容纳全部细节，会再次变得拥挤
  - 处理方式：只保留总览、主链路和分区跳转

- 目录命名若与顺序号脱钩，后续复习体验会下降
  - 处理方式：所有目录和页面统一使用顺序号方案

## Acceptance Criteria

- `D:\obsidian\repo\Android project\项目概述\MusicApp2` 下存在完整的有序目录结构
- 索引页、`27` 篇顺序笔记和大尺寸 canvas 全部创建完成
- 文档内容与 `.zread/wiki/current` 指向版本一致
- 正文版式已改写为更适合 Obsidian 阅读的结构
- canvas 相比 `ChatPPP` 同类图谱更疏朗，并使用更高对比度配色
