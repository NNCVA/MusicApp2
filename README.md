# 本地音乐播放器 Android App



一个功能完整的本地音乐播放器Android应用，使用Kotlin语言开发，采用MVVM架构模式。



## 功能特性

- ✅ 完全本地音乐播放（无网络依赖）
- ✅ 歌曲扫描、搜索、排序
- ✅ 歌单创建、管理、批量添加
- ✅ 最近播放记录（最多20首）
- ✅ 随机播放、顺序播放、单曲循环
- ✅ 底部播放栏 + 播放详情页
- ✅ 歌词解析与同步滚动（LRC）
- ✅ 前台播放Service + 通知栏控制



## 技术架构

### 开发环境
- Android Gradle Plugin：8.x
- Gradle：8.2
- JDK：17
- Kotlin：1.9.21
- 最低支持：Android 8.0（API 26）
- 目标版本：Android 14（API 34）

### 架构模式
- **MVVM + Repository**：清晰的数据流和职责分离
- **Room Database**：本地数据持久化
- **ExoPlayer**：强大的媒体播放引擎
- **MediaSession**：标准媒体控制接口
- **LiveData/StateFlow**：响应式数据更新

### 核心组件

#### 数据层
- `MusicDatabase` - Room数据库
- `SongDao` - 歌曲数据访问
- `PlaylistDao` - 歌单数据访问
- `RecentPlayDao` - 最近播放数据访问
- `MusicRepository` - 数据仓库

#### 业务层
- `MusicPlaybackService` - 前台播放服务
- `PlayerManager` - 播放器管理器
- `MusicScanner` - 音乐文件扫描器
- `LyricsParser` - LRC歌词解析器
- `PermissionManager` - 权限管理工具

#### UI层
- `MainActivity` - 主界面（歌曲列表）
- `PlayerDetailActivity` - 播放详情页
- `PlaylistActivity` - 歌单管理页面
- `PlaylistDetailActivity` - 歌单详情页
- `ScanMusicActivity` - 音乐扫描页面
- `RecentPlayActivity` - 最近播放页面



## 项目结构

```
app/src/main/java/com/musicplayer/
├── data/                     # 数据层
│   ├── dao/                  # 数据访问对象
│   ├── database/            # 数据库
│   ├── model/               # 数据模型
│   └── repository/          # 数据仓库
├── service/                 # 服务层
│   ├── MusicPlaybackService.kt
│   └── PlayerManager.kt
├── ui/                      # UI层
│   ├── main/               # 主界面
│   ├── player/             # 播放详情
│   ├── playlist/           # 歌单管理
│   ├── recent/             # 最近播放
│   └── scan/               # 音乐扫描
├── util/                    # 工具类
│   ├── MusicScanner.kt
│   ├── LyricsParser.kt
│   └── PermissionManager.kt
└── MusicPlayerApplication.kt
```



## 页面结构

### 1. 歌曲页（首页）
- 顶部：功能列表图标、标题、搜索图标
- 功能：排序、随机播放
- 歌曲列表：专辑封面、歌曲名、歌手名、菜单
- 底部：迷你播放栏

### 2. 搜索页面
- 实时搜索歌曲名或歌手名
- 显示搜索结果列表

### 3. 侧边功能列表
- 歌曲
- 歌单
- 扫描音乐
- 最近播放

### 4. 歌单页面
- 歌单列表显示
- 新建、重命名、删除歌单

### 5. 歌单歌曲页
- 显示歌单内的歌曲
- 支持播放全部

### 6. 扫描音乐页面
- 扫描音乐按钮
- 选择文件夹
- 显示扫描结果

### 7. 最近播放页面
- 显示最近播放的20首歌曲
- 支持清空记录

### 8. 播放详情页
- 歌曲信息展示

- 旋转专辑封面

- 歌词显示与同步

- 播放控制

- 播放模式切换

  

## 使用的第三方库

- **ExoPlayer** - 媒体播放引擎
- **Room** - SQLite数据库抽象层
- **Glide** - 图片加载库
- **Material Components** - Material Design组件
- **ViewModel & LiveData** - 生命周期感知组件



## 构建和运行

### 环境要求
- JDK 17
- Android Studio (推荐最新版本)
- Android SDK with API 34

### 构建步骤
1. 克隆项目到本地
2. 使用Android Studio打开项目
3. 等待Gradle同步完成
4. 连接Android设备或启动模拟器
5. 点击运行按钮或执行 `./gradlew installDebug`

### 权限说明
应用需要以下权限：
- **READ_MEDIA_AUDIO** (Android 13+) - 读取音频文件

- **READ_EXTERNAL_STORAGE** (Android 12及以下) - 读取存储权限

- **POST_NOTIFICATIONS** (Android 13+) - 显示播放通知

- **FOREGROUND_SERVICE** - 前台服务权限

  

## 开发注意事项

### 代码规范
- 使用Kotlin语言特性
- 遵循MVVM架构模式
- 使用LiveData/StateFlow进行数据更新
- 所有UI操作在主线程执行
- 数据库操作在IO线程执行

### 性能优化
- 使用RecyclerView进行列表展示
- 图片异步加载和缓存
- 避免内存泄漏（正确管理Service连接）
- 合理使用Coroutines进行异步操作

### 未来扩展的功能
- 添加均衡器功能
- 支持更多音频格式
- 添加主题切换功能
- 实现睡眠定时器
- 添加桌面小部件

