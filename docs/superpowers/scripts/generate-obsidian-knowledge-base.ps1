$ErrorActionPreference = 'Stop'

$repoRoot = 'F:\C2\application\repo\MusicApp2'
$vaultRoot = 'D:\obsidian\repo\Android project\项目概述\MusicApp2'
$currentFile = Join-Path $repoRoot '.zread\wiki\current'
$currentPointer = (Get-Content -LiteralPath $currentFile -Encoding utf8 -Raw).Trim()
if ($currentPointer.StartsWith('versions/')) {
    $versionId = $currentPointer.Substring('versions/'.Length)
} else {
    $versionId = $currentPointer
}
$sourceRoot = Join-Path $repoRoot ('.zread\wiki\versions\' + $versionId)
$wiki = Get-Content -LiteralPath (Join-Path $sourceRoot 'wiki.json') -Encoding utf8 -Raw | ConvertFrom-Json

$sectionDirMap = [ordered]@{
    '快速上手' = '01-快速入门'
    '架构设计' = '02-架构设计'
    '全屏播放器交互' = '03-全屏播放器交互'
    '本地媒体处理' = '04-本地媒体处理'
    '通知与系统集成' = '05-通知与系统集成'
    '稳定性与生命周期' = '06-稳定性与生命周期'
    '工程化与质量保障' = '07-工程化与质量保障'
}

$notePlan = @(
    [pscustomobject]@{ TargetBase='01-项目概览'; Description='项目定位、技术栈、三层架构与阅读路径' },
    [pscustomobject]@{ TargetBase='02-环境搭建与运行'; Description='开发环境、构建命令与首次运行' },
    [pscustomobject]@{ TargetBase='03-三层架构总览'; Description='数据层、服务层、UI 层的职责划分' },
    [pscustomobject]@{ TargetBase='04-播放器主链路'; Description='从 UI 到 ExoPlayer 的核心播放通路' },
    [pscustomobject]@{ TargetBase='05-Room 数据库与实体模型设计'; Description='Room 表结构、实体关系与数据建模' },
    [pscustomobject]@{ TargetBase='06-DAO 接口与查询策略'; Description='DAO 接口设计、查询方式与访问边界' },
    [pscustomobject]@{ TargetBase='07-MusicRepository 统一数据访问'; Description='Repository 门面与多数据源协同' },
    [pscustomobject]@{ TargetBase='08-MusicPlaybackService 前台播放服务'; Description='前台服务、ExoPlayer 与系统媒体控制' },
    [pscustomobject]@{ TargetBase='09-PlayerManager 播放状态管理单例'; Description='播放代理单例、状态同步与对外入口' },
    [pscustomobject]@{ TargetBase='10-播放模式切换与队列控制'; Description='播放模式、队列推进与控制逻辑' },
    [pscustomobject]@{ TargetBase='11-ContainerActivity 主容器与导航'; Description='主页面容器、播放器挂载与导航入口' },
    [pscustomobject]@{ TargetBase='12-Fragment 页面与数据观察'; Description='页面观察模型与 UI 数据联动' },
    [pscustomobject]@{ TargetBase='13-歌单详情页独立交互设计'; Description='歌单详情场景下的独立播放器交互' },
    [pscustomobject]@{ TargetBase='14-封面 歌词 播放队列三页横滑模型'; Description='全屏播放器三页模型与核心职责' },
    [pscustomobject]@{ TargetBase='15-PlayerPageSwipeLayout 自定义滑动控件'; Description='分页手势容器的结构与动画结算' },
    [pscustomobject]@{ TargetBase='16-手势仲裁 横向翻页与纵向滚动的边界切换'; Description='横滑与纵滚的冲突处理与边界规则' },
    [pscustomobject]@{ TargetBase='17-迷你播放器与全屏播放器上下切换'; Description='BottomSheet 展开收起与页面切换联动' },
    [pscustomobject]@{ TargetBase='18-音乐文件扫描与 MediaStore 集成'; Description='本地音频扫描链路与 MediaStore 读取' },
    [pscustomobject]@{ TargetBase='19-歌词解析与实时同步高亮'; Description='LRC 解析、进度同步与高亮刷新' },
    [pscustomobject]@{ TargetBase='20-专辑封面加载的双通道兜底策略'; Description='MediaStore 与元数据双通道封面获取' },
    [pscustomobject]@{ TargetBase='21-前台服务通知与 MediaSession'; Description='通知栏控制、MediaSession 与系统集成' },
    [pscustomobject]@{ TargetBase='22-权限管理与 Android 版本适配'; Description='权限申请流程与 Android 版本兼容点' },
    [pscustomobject]@{ TargetBase='23-Fragment 与播放器资源释放实践'; Description='页面销毁、回调解绑与资源清理' },
    [pscustomobject]@{ TargetBase='24-音量淡入淡出与切换防抖'; Description='切歌平滑过渡与状态抖动控制' },
    [pscustomobject]@{ TargetBase='25-Gradle 构建配置与版本管理'; Description='版本目录、构建脚本与工程配置组织' },
    [pscustomobject]@{ TargetBase='26-GitHub Actions CI 流水线'; Description='CI 任务顺序、产物与验证链路' },
    [pscustomobject]@{ TargetBase='27-单元测试覆盖与编写规范'; Description='测试范围、重点对象与编写约束' }
)

if ($wiki.pages.Count -ne $notePlan.Count) {
    throw "zread page count $($wiki.pages.Count) does not match note plan count $($notePlan.Count)"
}

function Quote-Yaml([string]$text) {
    return '"' + ($text -replace '\\', '\\' -replace '"', '\"') + '"'
}

$notes = @()
for ($i = 0; $i -lt $wiki.pages.Count; $i++) {
    $page = $wiki.pages[$i]
    $plan = $notePlan[$i]
    $targetDir = $sectionDirMap[$page.section]
    if (-not $targetDir) {
        throw "Unknown section: $($page.section)"
    }
    $notes += [pscustomobject]@{
        Number = $i + 1
        SourceFile = $page.file
        SourceSlug = $page.slug
        OriginalTitle = $page.title
        Section = $page.section
        TargetDir = $targetDir
        TargetBase = $plan.TargetBase
        Description = $plan.Description
        TargetPath = Join-Path (Join-Path $vaultRoot $targetDir) ($plan.TargetBase + '.md')
    }
}

foreach ($dir in $sectionDirMap.Values) {
    New-Item -ItemType Directory -Force -Path (Join-Path $vaultRoot $dir) | Out-Null
}

$sourceMap = @{}
foreach ($note in $notes) {
    $sourceMap[$note.SourceFile] = $note.TargetBase
    $sourceMap[[IO.Path]::GetFileNameWithoutExtension($note.SourceFile)] = $note.TargetBase
    $sourceMap[$note.SourceSlug] = $note.TargetBase
}

function Convert-InternalLinks([string]$text, [hashtable]$map) {
    $pattern = '\[([^\]]+)\]\(([^)]+)\)'
    return [regex]::Replace($text, $pattern, {
        param($match)
        $label = $match.Groups[1].Value
        $href = $match.Groups[2].Value.Trim()
        $clean = ($href -split '[#?]')[0].Trim()
        if ([string]::IsNullOrWhiteSpace($clean)) { return $match.Value }
        $fileName = [IO.Path]::GetFileName($clean)
        $stem = [IO.Path]::GetFileNameWithoutExtension($clean)
        $target = $null
        if ($map.ContainsKey($fileName)) {
            $target = $map[$fileName]
        } elseif ($map.ContainsKey($stem)) {
            $target = $map[$stem]
        }
        if ($null -ne $target) {
            if ($label -eq $target) {
                return "[[$target]]"
            }
            return "[[$target|$label]]"
        }
        return $match.Value
    })
}

$notesBySection = @{}
foreach ($note in $notes) {
    if (-not $notesBySection.ContainsKey($note.Section)) {
        $notesBySection[$note.Section] = New-Object System.Collections.ArrayList
    }
    [void]$notesBySection[$note.Section].Add($note)
}

foreach ($note in $notes) {
    $sourcePath = Join-Path $sourceRoot $note.SourceFile
    $body = (Get-Content -LiteralPath $sourcePath -Encoding utf8 -Raw).Trim()
    $body = $body -replace "`r`n", "`n"
    $body = Convert-InternalLinks -text $body -map $sourceMap

    $sameSection = @($notesBySection[$note.Section])
    $sameIndex = -1
    for ($j = 0; $j -lt $sameSection.Count; $j++) {
        if ($sameSection[$j].TargetBase -eq $note.TargetBase) {
            $sameIndex = $j
            break
        }
    }
    $sectionRelated = @()
    if ($sameIndex -gt 0) { $sectionRelated += $sameSection[$sameIndex - 1].TargetBase }
    if ($sameIndex -ge 0 -and $sameIndex -lt ($sameSection.Count - 1)) { $sectionRelated += $sameSection[$sameIndex + 1].TargetBase }
    $sectionRelated = $sectionRelated | Select-Object -Unique
    $relatedText = if ($sectionRelated.Count -gt 0) { ($sectionRelated | ForEach-Object { "[[$_]]" }) -join '、' } else { '无' }

    $prev = if ($note.Number -gt 1) { $notes[$note.Number - 2].TargetBase } else { $null }
    $next = if ($note.Number -lt $notes.Count) { $notes[$note.Number].TargetBase } else { $null }
    $prevText = if ($prev) { "[[$prev]]" } else { '起始页' }
    $nextText = if ($next) { "[[$next]]" } else { '末页' }

    $aliases = @()
    $aliases += ($note.TargetBase -replace '^\d{2}-', '')
    $aliases += $note.OriginalTitle
    $aliases = $aliases | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
    $aliasesYaml = ($aliases | ForEach-Object { '  - ' + (Quote-Yaml $_) }) -join "`n"

    $frontmatter = @"
---
title: $(Quote-Yaml $note.OriginalTitle)
tags:
  - MusicApp2
  - $($note.Section)
aliases:
$aliasesYaml
---

> [!note] 来源
> 本文档整理自 `MusicApp2/.zread/wiki/current` 指向的 zread 页面《$($note.OriginalTitle)》，按 Obsidian 阅读习惯重组，事实内容与源文档保持一致。

"@

    $navSection = @"

## 顺序导航

- `返回索引`：[[MusicApp2 文档索引]]
- `所属目录`：[[MusicApp2 文档索引#$($note.TargetDir)|$($note.TargetDir)]]
- `上一篇`：$prevText
- `下一篇`：$nextText
- `同目录推荐`：$relatedText
"@

    $content = ($frontmatter + $body + $navSection).TrimEnd() + "`n"
    Set-Content -LiteralPath $note.TargetPath -Value $content -Encoding utf8
}

$indexPath = Join-Path $vaultRoot 'MusicApp2 文档索引.md'
$indexSections = @()
foreach ($sectionEntry in $sectionDirMap.GetEnumerator()) {
    $section = $sectionEntry.Key
    $dirName = $sectionEntry.Value
    $sectionNotes = $notes | Where-Object Section -eq $section
    $rows = @('| # | 文档 | 说明 |', '|---|---|---|')
    foreach ($note in $sectionNotes) {
        $rows += "| $($note.Number) | [[$($note.TargetBase)]] | $($note.Description) |"
    }
    $indexSections += "## $dirName`n`n" + ($rows -join "`n")
}
$indexContent = @"
---
title: "MusicApp2 文档索引"
tags:
  - MusicApp2
  - MOC
aliases:
  - "MusicApp2 文档目录"
---

> [!note] 来源
> 本索引整理自 `MusicApp2/.zread/wiki/current` 指向的当前 zread 版本，共 `27` 篇主题文档，按 Obsidian 浏览习惯重组为有序目录。

$($indexSections -join "`n`n")

---

> [!tip] 知识图谱
> 在 Obsidian 中打开 [[MusicApp2 架构知识图谱.canvas]] 查看项目结构、播放器主链路与重点阅读路径的总览视图。
"@
Set-Content -LiteralPath $indexPath -Value ($indexContent.TrimStart() + "`n") -Encoding utf8

function New-CanvasId([string]$seed) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($seed)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    return -join ($hash[0..7] | ForEach-Object { $_.ToString('x2') })
}

function New-GroupNode($id, $x, $y, $w, $h, $label, $color) {
    return [ordered]@{ id=$id; type='group'; x=$x; y=$y; width=$w; height=$h; label=$label; color=$color }
}

function New-FileNode($id, $path, $x, $y, $w, $h, $color=$null) {
    $node = [ordered]@{ id=$id; type='file'; file=$path; x=$x; y=$y; width=$w; height=$h }
    if ($color) { $node.color = $color }
    return $node
}

function New-TextNode($id, $text, $x, $y, $w, $h, $color=$null) {
    $node = [ordered]@{ id=$id; type='text'; text=$text; x=$x; y=$y; width=$w; height=$h }
    if ($color) { $node.color = $color }
    return $node
}

function New-Edge($id, $from, $fromSide, $to, $toSide, $label, $color=$null) {
    $edge = [ordered]@{ id=$id; fromNode=$from; fromSide=$fromSide; toNode=$to; toSide=$toSide; toEnd='arrow'; label=$label }
    if ($color) { $edge.color = $color }
    return $edge
}

$canvasRoot = '项目概述/MusicApp2'
$pathIndex = "$canvasRoot/MusicApp2 文档索引.md"
$pathOverview = "$canvasRoot/01-快速入门/01-项目概览.md"
$pathEnv = "$canvasRoot/01-快速入门/02-环境搭建与运行.md"
$pathArch = "$canvasRoot/02-架构设计/03-三层架构总览.md"
$pathChain = "$canvasRoot/02-架构设计/04-播放器主链路.md"
$pathService = "$canvasRoot/02-架构设计/08-MusicPlaybackService 前台播放服务.md"
$pathManager = "$canvasRoot/02-架构设计/09-PlayerManager 播放状态管理单例.md"
$pathContainer = "$canvasRoot/02-架构设计/11-ContainerActivity 主容器与导航.md"
$pathPlayMode = "$canvasRoot/02-架构设计/10-播放模式切换与队列控制.md"
$pathSwipe = "$canvasRoot/03-全屏播放器交互/14-封面 歌词 播放队列三页横滑模型.md"
$pathSwipeLayout = "$canvasRoot/03-全屏播放器交互/15-PlayerPageSwipeLayout 自定义滑动控件.md"
$pathGesture = "$canvasRoot/03-全屏播放器交互/16-手势仲裁 横向翻页与纵向滚动的边界切换.md"
$pathBottom = "$canvasRoot/03-全屏播放器交互/17-迷你播放器与全屏播放器上下切换.md"
$pathScan = "$canvasRoot/04-本地媒体处理/18-音乐文件扫描与 MediaStore 集成.md"
$pathLyrics = "$canvasRoot/04-本地媒体处理/19-歌词解析与实时同步高亮.md"
$pathCover = "$canvasRoot/04-本地媒体处理/20-专辑封面加载的双通道兜底策略.md"
$pathNotify = "$canvasRoot/05-通知与系统集成/21-前台服务通知与 MediaSession.md"
$pathPermission = "$canvasRoot/05-通知与系统集成/22-权限管理与 Android 版本适配.md"
$pathLifecycle = "$canvasRoot/06-稳定性与生命周期/23-Fragment 与播放器资源释放实践.md"
$pathFade = "$canvasRoot/06-稳定性与生命周期/24-音量淡入淡出与切换防抖.md"
$pathGradle = "$canvasRoot/07-工程化与质量保障/25-Gradle 构建配置与版本管理.md"
$pathCi = "$canvasRoot/07-工程化与质量保障/26-GitHub Actions CI 流水线.md"
$pathTest = "$canvasRoot/07-工程化与质量保障/27-单元测试覆盖与编写规范.md"

$nodes = @(
    (New-GroupNode (New-CanvasId 'g-index') -1500 -520 1460 1240 '入口区 — 索引与阅读路径' '#0f766e'),
    (New-GroupNode (New-CanvasId 'g-arch') 320 -620 3120 1760 '主链路区 — 三层架构与播放通路' '#1d4ed8'),
    (New-GroupNode (New-CanvasId 'g-interaction') -1500 1080 2400 1720 '交互区 — 全屏播放器与手势系统' '#b45309'),
    (New-GroupNode (New-CanvasId 'g-support') 1180 1160 3220 1980 '支撑区 — 媒体处理、系统集成与工程化' '#7c2d12'),

    (New-FileNode (New-CanvasId 'n-index') $pathIndex -1320 -300 560 220 '#6'),
    (New-FileNode (New-CanvasId 'n-overview') $pathOverview -700 -300 560 220),
    (New-FileNode (New-CanvasId 'n-env') $pathEnv -700 0 560 220),
    (New-TextNode (New-CanvasId 't-path') "## 推荐阅读路径`n`n1. 项目概览`n2. 三层架构总览`n3. 播放器主链路`n4. 三页横滑模型`n5. 歌词解析`n6. Gradle 与 CI" -1320 20 1120 420 '#3'),

    (New-FileNode (New-CanvasId 'n-arch') $pathArch 520 -360 560 220),
    (New-FileNode (New-CanvasId 'n-chain') $pathChain 1160 -360 560 220),
    (New-FileNode (New-CanvasId 'n-manager') $pathManager 1800 -360 560 220),
    (New-FileNode (New-CanvasId 'n-service') $pathService 2440 -360 560 220),
    (New-FileNode (New-CanvasId 'n-container') $pathContainer 520 120 560 220),
    (New-FileNode (New-CanvasId 'n-playmode') $pathPlayMode 1160 120 560 220),
    (New-TextNode (New-CanvasId 't-flow') "## 播放器主链路`n`nUI`n→ PlayerManager`n→ PlayerServiceConnection`n→ MusicPlaybackService`n→ ExoPlayer`n`n这一条链路是整张图的阅读中心。" 1800 60 1200 540 '#5'),

    (New-FileNode (New-CanvasId 'n-swipe') $pathSwipe -1280 1280 560 220),
    (New-FileNode (New-CanvasId 'n-swipe-layout') $pathSwipeLayout -1280 1580 560 220),
    (New-FileNode (New-CanvasId 'n-gesture') $pathGesture -620 1280 560 220),
    (New-FileNode (New-CanvasId 'n-bottom') $pathBottom -620 1580 560 220),
    (New-TextNode (New-CanvasId 't-interaction') "## 三页交互基线`n`n- 页面顺序：封面 → 歌词 → 播放队列`n- 边界不循环，只回弹`n- BottomSheet 负责上下展开`n- PlayerPageSwipeLayout 负责全屏内部横滑`n- 纵向滚动到边界后允许外层接管" -1280 1880 1220 540 '#2'),

    (New-FileNode (New-CanvasId 'n-scan') $pathScan 1360 1380 520 210),
    (New-FileNode (New-CanvasId 'n-lyrics') $pathLyrics 1980 1380 520 210),
    (New-FileNode (New-CanvasId 'n-cover') $pathCover 2600 1380 520 210),
    (New-FileNode (New-CanvasId 'n-notify') $pathNotify 3220 1380 520 210),
    (New-FileNode (New-CanvasId 'n-perm') $pathPermission 1360 1680 520 210),
    (New-FileNode (New-CanvasId 'n-life') $pathLifecycle 1980 1680 520 210),
    (New-FileNode (New-CanvasId 'n-fade') $pathFade 2600 1680 520 210),
    (New-FileNode (New-CanvasId 'n-gradle') $pathGradle 1360 1980 520 210),
    (New-FileNode (New-CanvasId 'n-ci') $pathCi 1980 1980 520 210),
    (New-FileNode (New-CanvasId 'n-test') $pathTest 2600 1980 520 210),
    (New-TextNode (New-CanvasId 't-support') "## 支撑能力`n`n本地区负责扫描、歌词和封面；`n系统区负责通知、权限与生命周期；`n工程区负责 Gradle、CI 与测试保证。" 3220 1700 940 500 '#4')
)

$edges = @(
    (New-Edge (New-CanvasId 'e-index-overview') (New-CanvasId 'n-index') 'right' (New-CanvasId 'n-overview') 'left' '入口'),
    (New-Edge (New-CanvasId 'e-overview-arch') (New-CanvasId 'n-overview') 'right' (New-CanvasId 'n-arch') 'left' '阅读主线'),
    (New-Edge (New-CanvasId 'e-arch-chain') (New-CanvasId 'n-arch') 'right' (New-CanvasId 'n-chain') 'left' '架构核心'),
    (New-Edge (New-CanvasId 'e-chain-manager') (New-CanvasId 'n-chain') 'right' (New-CanvasId 'n-manager') 'left' '状态入口'),
    (New-Edge (New-CanvasId 'e-manager-service') (New-CanvasId 'n-manager') 'right' (New-CanvasId 'n-service') 'left' '服务代理'),
    (New-Edge (New-CanvasId 'e-chain-container') (New-CanvasId 'n-chain') 'bottom' (New-CanvasId 'n-container') 'top' 'UI 挂载'),
    (New-Edge (New-CanvasId 'e-container-swipe') (New-CanvasId 'n-container') 'left' (New-CanvasId 'n-swipe') 'top' '全屏播放器'),
    (New-Edge (New-CanvasId 'e-swipe-layout') (New-CanvasId 'n-swipe') 'bottom' (New-CanvasId 'n-swipe-layout') 'top' '手势容器'),
    (New-Edge (New-CanvasId 'e-swipe-gesture') (New-CanvasId 'n-swipe') 'right' (New-CanvasId 'n-gesture') 'left' '边界切换'),
    (New-Edge (New-CanvasId 'e-swipe-bottom') (New-CanvasId 'n-swipe') 'bottom' (New-CanvasId 'n-bottom') 'top' '上下切换'),
    (New-Edge (New-CanvasId 'e-swipe-lyrics') (New-CanvasId 'n-swipe') 'right' (New-CanvasId 'n-lyrics') 'left' '歌词联动'),
    (New-Edge (New-CanvasId 'e-overview-scan') (New-CanvasId 'n-overview') 'bottom' (New-CanvasId 'n-scan') 'top' '本地媒体'),
    (New-Edge (New-CanvasId 'e-scan-lyrics') (New-CanvasId 'n-scan') 'right' (New-CanvasId 'n-lyrics') 'left' '歌词源'),
    (New-Edge (New-CanvasId 'e-scan-cover') (New-CanvasId 'n-scan') 'right' (New-CanvasId 'n-cover') 'left' '封面源'),
    (New-Edge (New-CanvasId 'e-service-notify') (New-CanvasId 'n-service') 'bottom' (New-CanvasId 'n-notify') 'top' '系统集成'),
    (New-Edge (New-CanvasId 'e-container-life') (New-CanvasId 'n-container') 'right' (New-CanvasId 'n-life') 'left' '生命周期'),
    (New-Edge (New-CanvasId 'e-manager-fade') (New-CanvasId 'n-manager') 'bottom' (New-CanvasId 'n-fade') 'top' '切歌体验'),
    (New-Edge (New-CanvasId 'e-arch-gradle') (New-CanvasId 'n-arch') 'bottom' (New-CanvasId 'n-gradle') 'top' '工程化'),
    (New-Edge (New-CanvasId 'e-gradle-ci') (New-CanvasId 'n-gradle') 'right' (New-CanvasId 'n-ci') 'left' 'CI'),
    (New-Edge (New-CanvasId 'e-ci-test') (New-CanvasId 'n-ci') 'right' (New-CanvasId 'n-test') 'left' '质量保障')
)

$canvasObject = [ordered]@{ nodes = $nodes; edges = $edges }
$canvasPath = Join-Path $vaultRoot 'MusicApp2 架构知识图谱.canvas'
$canvasJson = $canvasObject | ConvertTo-Json -Depth 8
Set-Content -LiteralPath $canvasPath -Value $canvasJson -Encoding utf8

$counts = [ordered]@{}
foreach ($sectionEntry in $sectionDirMap.GetEnumerator()) {
    $counts[$sectionEntry.Value] = (Get-ChildItem -LiteralPath (Join-Path $vaultRoot $sectionEntry.Value) -Filter '*.md' | Measure-Object).Count
}
$canvas = Get-Content -LiteralPath $canvasPath -Encoding utf8 -Raw | ConvertFrom-Json
$xs = @()
$ys = @()
foreach ($n in $canvas.nodes) {
    $xs += [int]$n.x
    $xs += ([int]$n.x + [int]$n.width)
    $ys += [int]$n.y
    $ys += ([int]$n.y + [int]$n.height)
}

[pscustomobject]@{
    Version = $versionId
    IndexExists = (Test-Path -LiteralPath $indexPath)
    MarkdownTotal = (Get-ChildItem -LiteralPath $vaultRoot -Recurse -Filter '*.md' | Measure-Object).Count
    CanvasExists = (Test-Path -LiteralPath $canvasPath)
    CanvasNodeCount = $canvas.nodes.Count
    CanvasEdgeCount = $canvas.edges.Count
    CanvasWidth = (($xs | Measure-Object -Maximum).Maximum - ($xs | Measure-Object -Minimum).Minimum)
    CanvasHeight = (($ys | Measure-Object -Maximum).Maximum - ($ys | Measure-Object -Minimum).Minimum)
    FastStart = $counts['01-快速入门']
    Architecture = $counts['02-架构设计']
    Interaction = $counts['03-全屏播放器交互']
    Media = $counts['04-本地媒体处理']
    SystemIntegration = $counts['05-通知与系统集成']
    Lifecycle = $counts['06-稳定性与生命周期']
    Engineering = $counts['07-工程化与质量保障']
} | Format-List
