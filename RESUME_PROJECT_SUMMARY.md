# 简历项目总结：Android 多人聊天室（Java Socket / 多线程）

> 用途：把本项目写进简历时的事实依据与表述建议。本文档交给简历撰写会话参考。
>
> 定位：**课程作业 / 课程设计（个人项目）**，不是商业上线项目。不要编造用户量、QPS、性能提升、上线状态等数据。

## 0. 一句话版本

- 中文：基于 TCP Socket 的 Android 多人聊天室，独立实现 Android 客户端与 Java 多线程服务端，支持多客户端群聊、在线列表、上下线通知和本地聊天记录。
- 英文：An Android multi-client chat room over TCP sockets, with a Java threaded server, a custom line-based protocol, and local message caching.

## 1. 基本信息

| 项 | 内容 |
|---|---|
| 项目名称 | Android 多人聊天室（应用名：徽信；目录名：Theadwl） |
| 项目类型 | 课程作业 / 课程设计，C/S 架构，个人项目 |
| 角色 | 独立开发：Android 客户端、Java 服务端、启动脚本、文档（待确认） |
| 时间 | 2026.05–2026.06（按项目文件时间推断；请按实际修改） |
| 技术栈 | Java、Android SDK（minSdk 23 / target 36）、TCP Socket、ServerSocket、多线程（Thread / ExecutorService）、ConcurrentHashMap、AtomicInteger、SharedPreferences、JSON、Material Components、ConstraintLayout、EdgeToEdge、Gradle |
| 代码规模 | 客户端约 624 行（单 Activity）；服务端约 190 行（Main + ClientHandler）；4 种协议消息；本地缓存 200 条 |
| 运行形态 | Android 客户端连接 Java 服务端；服务端支持多客户端同时在线；可部署到云服务器 |
| 构建产物 | debug APK 可安装；`start_server.sh` 编译并启动服务端；另含客户端/服务端/运维文档 |

**待确认**：是否独立完成、实际项目时间、是否实际部署到阿里云、是否用两台设备/模拟器做过联调、文档是否本人撰写。

## 2. 项目背景与目标

- 背景：Android 应用开发课程作业，练习 Android 网络编程与多线程。
- 目标：实现一个多人聊天室，客户端连接服务器后可以实时收发群聊消息、查看在线成员、收到上下线通知，并在本地保留聊天记录。
- 约束：不使用 WebSocket/Retrofit/OkHttp 等现成网络库，直接用 TCP Socket 和阻塞 IO，重点理解连接管理、线程模型和主线程更新 UI 的限制。
- 交付：客户端 + 服务端 + 协议 + 本地缓存 + 部署脚本 + 中文文档。

## 3. 系统组成

- **Android 客户端**：单 Activity；顶部栏（标题/在线状态/设置/连接）、消息区（ScrollView + 动态 LinearLayout）、底部输入栏、欢迎页；设置弹窗；网络线程；SharedPreferences 缓存。
- **Java 服务端**：`ServerSocket` 监听端口；`accept()` 后为每个客户端创建线程；`ConcurrentHashMap.newKeySet()` 保存在线连接；`AtomicInteger` 生成客户端编号；广播消息、在线列表和系统通知。
- **通信协议**：按行传输、`|` 分隔的文本协议，UTF-8；4 种消息：`HELLO`、`MSG`、`USERS`、`SYS`。
- **本地存储**：`SharedPreferences` 保存服务器 IP、端口、昵称和最近 200 条聊天记录（JSON 数组）。
- **部署/文档**：`start_server.sh` 编译并启动；`nohup` 后台运行、日志、`pkill`、端口检查；阿里云安全组说明；客户端/服务端/运维三份中文文档。

## 4. 核心功能

- **多客户端群聊**：任一客户端发送消息，服务端补上发送者和服务器时间后广播给所有在线客户端。
- **在线列表**：服务端广播 `USERS|...`，客户端更新标题人数和在线昵称。
- **上下线通知**：客户端连接/断开时，服务端广播 `SYS|... 已连接/已断开`。
- **握手与设备信息**：客户端首条消息发送 `HELLO|昵称|设备名`，服务端记录昵称和设备，用于系统通知。
- **连接设置**：IP、端口、昵称可配置；校验空值、端口 1–65535；首次启动强制配置；配置持久化。
- **连接状态**：连接中显示进度圈并禁用按钮；已连接可发送；断开后输入框和发送按钮禁用；状态文字统一更新。
- **聊天记录**：普通消息/系统消息、自己/他人区分；本地缓存最近 200 条，启动时恢复；系统消息居中显示。
- **UI**：左右对齐气泡、自定义气泡背景、时间显示、最大宽度限制、dp 适配、自动滚动、欢迎页、Material 输入弹窗。
- **异常处理**：连接失败/服务器断开时更新状态、关闭 Socket、添加系统提示；主动断开与被动断开区分。

## 5. 技术实现（简历可用的点）

### 5.1 自定义 TCP 文本协议

```text
客户端 -> 服务端
HELLO|昵称|设备名
MSG|消息内容

服务端 -> 客户端
USERS|用户1,用户2
MSG|发送者|HH:mm:ss|消息内容
SYS|系统通知
```

- 一行一条消息，`|` 分隔；双方统一 UTF-8。
- 服务端补全发送者和服务器时间，保证同一消息在所有客户端使用同一时间戳。
- 昵称/设备名在客户端和服务端都清洗 `|`、`,`，避免破坏协议。
- 消息内容用 `split("\\|", 4)` 限长切分，保留内容中的 `|`；空消息和未知命令忽略。
- `PrintWriter(..., true)` 自动 flush，`BufferedReader.readLine()` 按行读取。

### 5.2 服务端多线程

- `ServerSocket.accept()` 主循环；每个连接一个 `ClientHandler` 线程，支持多个客户端同时在线。
- `ConcurrentHashMap.newKeySet()` 保存在线客户端；`AtomicInteger` 生成客户端编号。
- 每个客户端一个 `BufferedReader`/`PrintWriter`；`try-with-resources` 管理流。
- 客户端断开（正常/异常）在 `finally` 中统一清理：移出在线集合、关闭 Socket、广播下线通知和最新 `USERS`。
- `broadcast()` 遍历在线集合；`broadcastUsers()` 拼接在线昵称。

### 5.3 客户端线程模型

- Android 主线程不做网络：连接、发送、读取都提交到 `ExecutorService`（缓存线程池）。
- `volatile Socket socket`、`volatile PrintWriter writer`、`volatile boolean manualDisconnect` 保证后台线程和主线程的可见性。
- 接收循环 `while ((line = reader.readLine()) != null)` 持续读取；解析后通过 `runOnUiThread` 更新 UI。
- `Handler(Looper.getMainLooper()).postDelayed` 实现 1.2 秒欢迎页。
- `onDestroy` 关闭 Socket、`shutdownNow()` 线程池，避免后台连接残留。

### 5.4 连接状态与异常处理

- `updateConnectionUi(connecting, status)` 集中管理进度圈、按钮可用性、连接按钮文字、输入框/发送按钮、状态文字。
- `manualDisconnect` 区分“用户主动断开”和“服务器/网络异常断开”，避免重复提示。
- 连接成功/失败/服务器断开分别更新状态并写入系统消息。
- 端口校验、空值校验、昵称清洗；首次启动未配置时弹不可取消的设置框。

### 5.5 本地持久化

- `SharedPreferences` 保存 `server_ip`、`server_port`、`client_name`、`cached_messages`。
- `ChatMessage` 不可变模型：`sender`、`time`、`content`、`mine`、`system`；提供 `toJson()`/`fromJson()`。
- 每次消息追加后保存 JSON 数组；超过 200 条从头部淘汰；启动时读取并重绘。
- 没有数据库、没有服务端历史；历史只在本机。

### 5.6 UI 实现

- ConstraintLayout + ScrollView + LinearLayout；消息用 Java 动态创建 TextView/LinearLayout。
- 自己消息 `Gravity.END`、他人 `Gravity.START`、系统消息居中；自定义 `bg_bubble_mine`/`bg_bubble_other` drawable。
- `dp()` 做 dp→px 转换；气泡 `setMaxWidth(widthPixels - dp(96))`；时间小字浅色；`scrollToBottom()` 自动滚动。
- EdgeToEdge + 系统栏 insets padding；Material `TextInputLayout` 设置弹窗。
- 说明：没有使用 RecyclerView；长篇历史在视图层没有做回收（这是已知短板，不要写成亮点）。

### 5.7 部署与运维

- `start_server.sh`：读取端口参数（默认 5000）→ `javac -encoding UTF-8 -source 8 -target 8` → `java -cp out Main <port>`；无 `javac` 时回退到已有 class。
- 后台运行：`nohup ./start_server.sh 5000 > server.log 2>&1 &`；日志 `tail -f`；停止 `pkill -f "java -cp out Main 5000"`；端口检查 `ss -tunlp`。
- 文档包含阿里云安全组放行 TCP 5000、JDK 安装、编译/启动/重启/排错步骤。
- 说明：是否实际部署到阿里云、是否多设备联调，需要确认后再写进简历。

## 6. 难点与解决（STAR 素材）

| 难点 | 解决 |
|---|---|
| Android 主线程不能做网络 | 连接/收发放 `ExecutorService`，UI 用 `runOnUiThread` |
| 多客户端并发与共享状态 | 服务端 thread-per-client；在线集合用 `ConcurrentHashMap.newKeySet()`；编号用 `AtomicInteger` |
| 协议分隔符/编码冲突 | 统一 UTF-8；昵称/设备名双重清洗；消息内容限长 split；空消息忽略 |
| 主动断开与异常断开混淆 | `manualDisconnect` 标记；统一清理与状态更新 |
| 无数据库保存历史 | `SharedPreferences` + JSON；不可变 `ChatMessage`；200 条上限 |
| 连接状态 UI 分散 | `updateConnectionUi` 集中管理按钮/进度/输入框/状态文字 |
| 跨设备尺寸适配 | `dp()` 转换、气泡最大宽度、左右对齐/居中布局 |
| 服务端部署与排错 | 启动脚本、端口参数、后台运行、日志、安全组/防火墙文档 |

## 7. 结果与验证

- 客户端与服务端可端到端运行：多客户端连接同一服务端，群聊广播、在线列表、上下线通知、历史恢复均已实现。如果实际用两台设备/模拟器演示过，可以写“完成多端联调”；否则写“实现并通过协议层验证”。
- 服务端源码可编译启动；协议层已用双客户端脚本做过本地验证（HELLO/USERS/MSG/SYS、中文、`|` 内容、空消息、未知命令、上下线）。
- 构建产物：debug APK 可安装；服务端启动脚本可编译/启动。
- 文档：客户端、服务端、运维三份中文说明，合计约 2500 行。
- 规模参考：客户端约 624 行、服务端约 190 行、4 种协议消息、200 条本地缓存。
- **未做**：性能压测、并发上限测试、真机多品牌兼容性测试、自动化测试。不要把“未测”写成“已验证”。

## 8. 简历写法建议

### 8.1 项目标题

- 中文：**Android 多人聊天室（Java Socket / 多线程）**
- 英文：**Android Multi-client Chat Room (Java Socket, Multithreading)**
- 不建议直接用“徽信”作为简历标题（应用名是课程作业内部命名，且带微信谐音）；可以放在项目描述里作为应用名。

### 8.2 项目描述（一段话）

> 基于 TCP Socket 的 Android 多人聊天室课程项目，独立实现 Android 客户端与 Java 多线程服务端。自定义 HELLO/MSG/USERS/SYS 行文本协议，支持多客户端群聊、在线列表、上下线通知和服务器时间戳；客户端使用线程池执行网络操作、主线程更新 UI，并通过 SharedPreferences 持久化服务器配置与最近 200 条聊天记录。

### 8.3 Bullet 草案（中文，按需选 3–5 条）

1. 独立完成 Android 客户端 + Java 服务端的多人聊天室，基于 TCP Socket 设计 `HELLO/MSG/USERS/SYS` 行文本协议，实现群聊广播、在线列表、上下线系统通知与服务器时间戳。
2. 服务端采用 thread-per-client 模型，使用 `ConcurrentHashMap` 管理在线连接、`AtomicInteger` 生成客户端编号，在 `finally` 中统一清理并广播下线；客户端用 `ExecutorService` 执行连接/收发，`runOnUiThread` 回主线程更新 UI，避免主线程网络操作。
3. 统一 UTF-8 编码，客户端与服务端双重清洗昵称中的分隔符，消息内容用限长 `split` 保留 `|`；通过 `manualDisconnect` 区分主动断开与网络异常，集中管理连接中/已连接/断开三态 UI。
4. 使用 `SharedPreferences` + JSON 持久化服务器配置与最近 200 条聊天记录，启动时恢复；动态构建左右对齐聊天气泡、居中系统消息、最大宽度与 dp 适配，并实现欢迎页、连接进度和输入校验。
5. （可选，确认实际部署后）编写服务端编译/启动脚本，部署到阿里云 ECS，补充安全组、后台运行、日志排查文档，完成手机端与云服务端的联调。

### 8.4 技术栈行

`Java · Android SDK · TCP Socket · 多线程（Thread / ExecutorService） · ConcurrentHashMap · SharedPreferences · JSON · Material Components · ConstraintLayout · Gradle`

### 8.5 英文版本（可选）

**Android Multi-client Chat Room (Java Socket, Multithreading)**

- Built an Android client and a Java TCP server for a multi-client chat room with a custom `HELLO/MSG/USERS/SYS` line protocol, group broadcast, online list, join/leave notices, and server timestamps.
- Implemented a thread-per-client server with `ConcurrentHashMap`/`AtomicInteger` for connection management and `finally`-based cleanup; used `ExecutorService` for client networking and `runOnUiThread` for UI updates to avoid blocking the main thread.
- Added UTF-8 encoding, delimiter sanitization, limited `split` for pipe characters, `SharedPreferences` + JSON cache (last 200 messages), dynamic chat bubbles, and deployment/ops scripts with documentation.

## 9. 不要写 / 谨慎写

### 9.1 不要写（项目没有）

- 高并发、万级连接、QPS、延迟优化、性能提升百分比——没有压测数据。
- 上线产品、用户量、日活、留存——没有真实用户数据。
- WebSocket、Retrofit、OkHttp、Room、MVVM、Jetpack Compose、Kotlin——没有使用。
- TLS/加密、登录认证、JWT/OAuth、权限系统——没有实现。
- 消息可靠投递、ACK、自动重连、心跳、离线消息——没有实现。
- 私聊、群管理、图片/语音/文件/表情包——没有实现。
- 单元测试、UI 测试、CI/CD——只有模板测试，没有真实覆盖。
- RecyclerView——实际是 ScrollView + 动态 LinearLayout。
- 完善的生命周期/横屏/暗色模式/IME 适配——这些是已知短板。
- 数据库、服务端历史、分布式/集群/高可用——没有。
- Git 版本管理/多人协作——项目目录不是 Git 仓库（截至本次分析），除非你后来补了。
- AGP 9.0.0-alpha06 / 预览版工具链——写“Gradle 构建”即可，alpha 版本不是亮点。

### 9.2 谨慎写

- **“部署到阿里云”**：只有确实做过才写；否则写“本地联调”或省略。
- **“多客户端并发”**：可以写“支持多客户端同时在线”，不要给具体并发数。
- **“独立完成”**：确认后再写；如果文档/部分代码有外部帮助，措辞改为“参与/负责客户端与服务端开发”。
- **“编写文档”**：如果文档不是本人写的，不要写“编写约 2500 行文档”；可写“项目包含完整中文文档”。
- **“Edge-to-edge 适配”**：只做了系统栏 insets，没有处理 IME；写“处理系统栏 insets”即可，不要写“全面适配”。
- **“仿微信”**：可以写“聊天室风格 UI”，避免直接使用微信品牌表述。

### 9.3 已知短板（面试可能被追问，简历不要主动写）

- 旋转屏幕会重建 Activity，连接会断；没有 ViewModel/Service 保留连接。
- 没有心跳/自动重连；半开连接可能长期留在在线列表。
- 昵称不唯一，同名用户可能把对方消息识别成“自己”。
- 视图层没有做消息回收，长时间会话有性能隐患。
- 没有 IME/暗色模式适配；没有自动化测试。
- 服务端 thread-per-client，不适合高并发场景。
- 协议简单，没有认证/加密/消息确认。
- 如果被问“如何改进”：RecyclerView + ViewModel/Service、连接代号/重连、心跳、唯一用户 ID、TLS/token、测试与压测。

## 10. 待确认信息（交给用户填）

- [ ] 项目实际起止时间（当前推断 2026.05–2026.06）
- [ ] 是否独立完成（客户端、服务端、脚本、文档）
- [ ] 是否实际部署到阿里云 ECS；是否用手机 + 云服务器联调
- [ ] 是否用两台设备/两个模拟器演示过
- [ ] 文档是否本人撰写
- [ ] 目标岗位（Android / Java 后端 / 客户端 / 校招实习）
- [ ] 是否需要英文版
- [ ] 是否有截图/GIF 可放进简历或作品集
- [ ] 是否保留“徽信”这个应用名

## 11. 给简历撰写会话的指令

请基于以上事实撰写 1 个项目条目：标题 + 1 段项目描述 + 3–5 条 bullet + 技术栈行。

要求：

1. 保持“课程设计/个人项目”定位，不要写成商业上线项目。
2. 不要编造用户量、QPS、性能提升、并发数、上线状态；未确认的信息用 `[待确认]` 或省略。
3. 优先突出：TCP Socket 自定义协议、服务端多线程、客户端线程池 + 主线程切换、连接状态管理、SharedPreferences 缓存、动态聊天 UI。
4. 不要把“未实现的功能”写进去；参考第 9 节。
5. 中文简历为主；如需要英文版，按同一事实翻译，不要升级措辞。
6. 如果用户目标岗位是 Android，适当展开 UI/线程/生命周期；如果是 Java 后端，适当展开服务端并发/协议/部署。
