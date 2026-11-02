# Android 客户端代码详解

本文档只讲本项目中的 Android 客户端 `adClient`，帮助你理解这个聊天室 App 是怎么启动、怎么显示界面、怎么连接服务器、怎么发送和接收消息、怎么缓存聊天记录的。

## 1. 客户端项目结构

客户端主要目录如下：

```text
adClient/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/adclient/MainActivity.java
│       └── res/
│           ├── layout/activity_main.xml
│           ├── drawable/bg_bubble_mine.xml
│           ├── drawable/bg_bubble_other.xml
│           ├── drawable/bg_input.xml
│           └── values/strings.xml
├── build.gradle
└── settings.gradle
```

最重要的文件有三个：

```text
MainActivity.java       客户端核心逻辑
activity_main.xml       客户端主界面布局
AndroidManifest.xml     App 权限、入口 Activity 等配置
```

## 2. AndroidManifest.xml 的作用

文件位置：

```text
app/src/main/AndroidManifest.xml
```

这里声明了 App 需要访问网络：

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

如果没有这个权限，客户端就不能通过 Socket 连接阿里云服务器。

这里还允许明文网络通信：

```xml
android:usesCleartextTraffic="true"
```

因为当前项目使用的是普通 TCP Socket，不是 HTTPS/TLS，所以需要允许明文通信。

入口页面是：

```xml
<activity android:name=".MainActivity" android:exported="true">
```

也就是说，手机点击 App 图标后，系统会打开 `MainActivity`。

## 3. build.gradle 的作用

文件位置：

```text
app/build.gradle
```

这里配置了客户端包名、SDK 版本、依赖库等。

包名：

```gradle
applicationId "com.example.adclient"
```

最低支持 Android 版本：

```gradle
minSdk 23
```

主要依赖：

```gradle
implementation libs.appcompat
implementation libs.material
implementation libs.activity
implementation libs.constraintlayout
```

其中：

- `appcompat`：兼容老版本 Android 的基础库。
- `material`：提供 Material 风格控件，比如 `TextInputLayout`。
- `constraintlayout`：支持 `ConstraintLayout` 布局。

## 4. activity_main.xml 界面结构

文件位置：

```text
app/src/main/res/layout/activity_main.xml
```

这个文件定义了 App 主界面长什么样。根布局是：

```xml
<androidx.constraintlayout.widget.ConstraintLayout>
```

里面主要分成四块：

```text
顶部栏 topBar
消息列表 messageScroll
底部输入栏 inputBar
欢迎页 welcomePage
```

### 4.1 顶部栏 topBar

顶部栏显示：

- 群聊标题，比如 `群聊 (1)`。
- 在线状态，比如 `在线：张三、李四`。
- 设置按钮。
- 连接/断开按钮。

对应控件：

```xml
TextView  titleText
TextView  statusText
Button    settingsButton
Button    connectButton
```

这些控件在 Java 代码中通过 `findViewById` 找到，然后动态修改文字。

### 4.2 消息列表 messageScroll

消息区域是：

```xml
<ScrollView android:id="@+id/messageScroll">
    <LinearLayout android:id="@+id/messagesContainer"/>
</ScrollView>
```

`ScrollView` 负责滚动，`messagesContainer` 负责装每一条消息。

本项目没有使用 `RecyclerView`，而是用 Java 代码动态创建 `TextView` 和 `LinearLayout`，把消息一条条加到 `messagesContainer` 里面。

### 4.3 底部输入栏 inputBar

底部有：

```xml
EditText  messageInput
Button    sendButton
```

用户在 `messageInput` 输入文字，点击 `sendButton` 后发送给服务器。

### 4.4 欢迎页 welcomePage

欢迎页是一个盖在最上层的绿色界面：

```xml
<LinearLayout android:id="@+id/welcomePage">
```

里面显示：

```text
欢迎加入群聊
手机聊天室
```

默认是隐藏的：

```xml
android:visibility="gone"
```

App 打开后，Java 代码会让它显示 1.2 秒，然后隐藏。

## 5. MainActivity.java 总览

文件位置：

```text
app/src/main/java/com/example/adclient/MainActivity.java
```

这是客户端最核心的文件。它负责：

- 初始化界面。
- 读取本地保存的 IP、端口、昵称。
- 显示设置弹窗。
- 连接服务器。
- 发送消息。
- 接收服务器消息。
- 更新在线人数。
- 渲染聊天气泡。
- 缓存聊天历史。

可以把它理解成客户端的大脑。

## 6. 常量区：保存配置和默认值

代码开头有一些常量：

```java
private static final String PREFS_NAME = "chat_settings";
private static final String KEY_IP = "server_ip";
private static final String KEY_PORT = "server_port";
private static final String KEY_NAME = "client_name";
private static final String KEY_MESSAGES = "cached_messages";
private static final String DEFAULT_IP = "8.148.197.226";
private static final String DEFAULT_PORT = "5000";
private static final int MAX_CACHED_MESSAGES = 200;
```

含义如下：

- `PREFS_NAME`：本地存储文件名。
- `KEY_IP`：保存服务器 IP 的 key。
- `KEY_PORT`：保存端口号的 key。
- `KEY_NAME`：保存昵称的 key。
- `KEY_MESSAGES`：保存历史消息的 key。
- `DEFAULT_IP`：默认服务器公网 IP。
- `DEFAULT_PORT`：默认端口。
- `MAX_CACHED_MESSAGES`：最多缓存 200 条历史消息。

这些数据都存在 Android 的 `SharedPreferences` 里。

## 7. 成员变量：保存界面控件和连接状态

界面控件变量：

```java
private TextView titleText;
private TextView statusText;
private Button settingsButton;
private Button connectButton;
private Button sendButton;
private ProgressBar connectProgress;
private LinearLayout welcomePage;
private LinearLayout messagesContainer;
private EditText messageInput;
private ScrollView messageScroll;
```

这些变量对应 XML 里的控件。

网络相关变量：

```java
private volatile Socket socket;
private volatile PrintWriter writer;
```

- `socket`：客户端和服务器之间的 TCP 连接。
- `writer`：向服务器发送文本消息的工具。

状态变量：

```java
private volatile boolean manualDisconnect = false;
private boolean connected = false;
private String serverIp = DEFAULT_IP;
private String serverPort = DEFAULT_PORT;
private String serverAddress = "";
private String clientName = "";
```

- `manualDisconnect`：是否是用户主动断开。
- `connected`：当前是否已连接。
- `serverIp`：服务器 IP。
- `serverPort`：服务器端口。
- `serverAddress`：组合后的服务器地址。
- `clientName`：当前用户昵称。

线程池：

```java
private final ExecutorService executor = Executors.newCachedThreadPool();
```

Socket 网络操作不能放在主线程里，否则 Android 会报错或界面卡住。所以连接、发送、接收消息都放在线程池里执行。

## 8. onCreate：App 启动后的入口

`onCreate` 是 Activity 创建时最先执行的核心方法。

主要流程：

```java
setContentView(R.layout.activity_main);
bindViews();
loadSettings();
loadCachedMessages();
renderAllMessages();
updateConnectionUi(false, "未连接");
showWelcomePage();
```

可以按顺序理解：

1. 加载 XML 界面。
2. 绑定界面控件。
3. 从本地读取服务器 IP、端口、昵称。
4. 从本地读取历史消息。
5. 把历史消息显示到界面上。
6. 设置初始连接状态。
7. 显示欢迎页。

然后设置按钮点击事件：

```java
settingsButton.setOnClickListener(v -> showSettingsDialog(false));
```

点击“设置”按钮，弹出连接设置窗口。

```java
connectButton.setOnClickListener(v -> {
    if (connected) {
        disconnect("已断开连接");
    } else {
        connect();
    }
});
```

如果当前已连接，点击按钮就是断开；如果未连接，点击按钮就是连接。

```java
sendButton.setOnClickListener(v -> sendMessage());
```

点击发送按钮后调用 `sendMessage()`。

最后：

```java
if (!hasSavedSettings()) {
    showSettingsDialog(true);
}
```

如果用户还没有保存过 IP、端口、昵称，就强制弹出设置窗口。

## 9. bindViews：绑定 XML 控件

```java
private void bindViews() {
    titleText = findViewById(R.id.titleText);
    statusText = findViewById(R.id.statusText);
    ...
}
```

XML 中每个控件都有一个 `android:id`，Java 通过 `findViewById` 拿到控件对象，之后才能修改它们的文字、颜色、显示隐藏状态。

比如 XML 中有：

```xml
android:id="@+id/titleText"
```

Java 中就可以：

```java
titleText = findViewById(R.id.titleText);
titleText.setText("群聊 (2)");
```

## 10. 设置弹窗：填写 IP、端口、昵称

设置弹窗由这个方法创建：

```java
private void showSettingsDialog(boolean required)
```

参数 `required` 表示是否强制填写。

- `required = true`：第一次打开 App，必须填写，不能取消。
- `required = false`：用户主动点设置，可以取消。

弹窗里有三个输入框：

```java
TextInputEditText ipInput = addDialogInput(form, "服务器 IP", serverIp, "text");
TextInputEditText portInput = addDialogInput(form, "端口号", serverPort, "number");
TextInputEditText nameInput = addDialogInput(form, "我的昵称", clientName, "text");
```

保存时会检查：

- IP 不能为空。
- 端口不能为空。
- 昵称不能为空。
- 端口必须是 `1-65535` 之间的数字。

保存到本地：

```java
prefs.edit()
        .putString(KEY_IP, serverIp)
        .putString(KEY_PORT, serverPort)
        .putString(KEY_NAME, clientName)
        .apply();
```

这就是为什么下次打开 App 时还能记住上次填写的服务器地址和昵称。

## 11. SharedPreferences：本地保存数据

客户端用 `SharedPreferences` 保存两类数据：

```text
服务器 IP
端口号
用户昵称
聊天历史消息
```

读取设置：

```java
serverIp = prefs.getString(KEY_IP, DEFAULT_IP);
serverPort = prefs.getString(KEY_PORT, DEFAULT_PORT);
clientName = prefs.getString(KEY_NAME, "");
```

保存设置：

```java
prefs.edit()
        .putString(KEY_IP, serverIp)
        .putString(KEY_PORT, serverPort)
        .putString(KEY_NAME, clientName)
        .apply();
```

`SharedPreferences` 适合保存少量简单数据。本项目的历史消息也不多，所以先用它保存 JSON 字符串。

## 12. connect：连接服务器

连接服务器的方法是：

```java
private void connect()
```

核心代码：

```java
Socket newSocket = new Socket(ip, port);
```

这行代码会向服务器发起 TCP 连接。例如：

```text
8.148.197.226:5000
```

连接成功后，创建发送器：

```java
PrintWriter newWriter = new PrintWriter(
        new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8),
        true
);
```

创建接收器：

```java
BufferedReader reader = new BufferedReader(
        new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8)
);
```

然后保存连接对象：

```java
socket = newSocket;
writer = newWriter;
```

向服务器发送第一条握手消息：

```java
writer.println("HELLO|" + clientName + "|" + deviceName());
```

这条消息的格式是：

```text
HELLO|昵称|设备名称
```

比如：

```text
HELLO|张三|vivo V2203A
```

服务器收到后，就知道这个客户端叫什么名字、来自什么设备。

## 13. 为什么网络代码放在线程池里

连接服务器时使用：

```java
executor.execute(() -> {
    ...
});
```

原因是 Android 主线程负责界面显示。如果在主线程里执行网络请求，界面会卡住，甚至直接报错。

所以本项目把耗时操作放到后台线程：

```text
连接服务器
等待服务器消息
发送消息
```

但是修改界面必须回到主线程：

```java
runOnUiThread(() -> {
    connected = true;
    updateConnectionUi(false, "已连接 " + serverAddress);
});
```

你可以记住一句话：

```text
网络操作放后台线程，界面更新回主线程。
```

## 14. readMessages：持续读取服务器消息

连接成功后，客户端调用：

```java
readMessages(reader);
```

里面是一个循环：

```java
while ((line = reader.readLine()) != null) {
    handleServerMessage(line);
}
```

意思是：只要服务器没有断开连接，就一直等待服务器发来的新消息。

每收到一行文本，就交给：

```java
handleServerMessage(line);
```

## 15. 客户端和服务器的通信协议

当前协议是简单的文本协议，每条消息一行，用 `|` 分隔。

客户端发给服务器：

```text
HELLO|昵称|设备名称
MSG|消息内容
```

服务器发给客户端：

```text
USERS|用户1,用户2
MSG|发送者|时间|消息内容
SYS|系统消息
```

例子：

```text
USERS|张三,李四
MSG|张三|20:15:30|你好
SYS|张三（设备：vivo V2203A）已连接
```

这种协议很适合学习，因为简单、直观、容易在终端里观察。

## 16. handleServerMessage：解析服务器消息

服务器消息会进入：

```java
private void handleServerMessage(String line)
```

如果是在线列表：

```java
if (line.startsWith("USERS|")) {
    ...
}
```

客户端会解析用户列表，然后调用：

```java
updateUsers(users);
```

如果是聊天消息：

```java
else if (line.startsWith("MSG|")) {
    String[] parts = line.split("\\|", 4);
}
```

`split("\\|", 4)` 的意思是最多切成 4 段：

```text
MSG
发送者
时间
消息内容
```

然后创建一条 `ChatMessage`：

```java
ChatMessage message = ChatMessage.chat(
        parts[1],
        parts[2],
        parts[3],
        parts[1].equals(clientName)
);
```

最后显示到界面：

```java
runOnUiThread(() -> addMessage(message));
```

如果是系统消息：

```java
else if (line.startsWith("SYS|")) {
    runOnUiThread(() -> addMessage(ChatMessage.system(...)));
}
```

系统消息会居中显示，比如“某某已连接”。

## 17. updateUsers：更新顶部在线状态

```java
private void updateUsers(List<String> users)
```

这个方法会更新顶部标题：

```java
titleText.setText("群聊 (" + onlineCount + ")");
```

比如当前 2 人在线：

```text
群聊 (2)
```

同时更新在线列表：

```java
statusText.setText("在线：" + names);
```

比如：

```text
在线：张三、李四
```

## 18. sendMessage：发送聊天消息

点击发送按钮后，会调用：

```java
private void sendMessage()
```

先读取输入框：

```java
String content = textOf(messageInput).replace("\n", " ").trim();
```

如果内容为空，直接返回。

如果还没连接服务器：

```java
if (writer == null) {
    Toast.makeText(this, "请先连接服务器", Toast.LENGTH_SHORT).show();
    return;
}
```

真正发送消息：

```java
currentWriter.println("MSG|" + content);
```

发送给服务器的格式是：

```text
MSG|消息内容
```

注意：客户端发送后没有立刻自己显示，而是等服务器广播回来后再显示。这样可以保证所有客户端看到的消息顺序一致。

## 19. disconnect：断开连接

断开连接的方法：

```java
private void disconnect(String message)
```

做了几件事：

```java
manualDisconnect = true;
closeSocket();
connected = false;
updateConnectionUi(false, message);
addMessage(ChatMessage.system(message));
```

含义：

1. 标记为用户主动断开。
2. 关闭 Socket。
3. 设置连接状态为未连接。
4. 更新按钮和状态文字。
5. 在聊天记录里添加一条系统消息。

真正关闭 Socket 的方法是：

```java
private void closeSocket()
```

里面会调用：

```java
socket.close();
```

## 20. ChatMessage：消息数据模型

`ChatMessage` 是一个内部类，用来表示一条消息。

字段：

```java
final String sender;
final String time;
final String content;
final boolean mine;
final boolean system;
```

含义：

- `sender`：发送者昵称。
- `time`：消息时间。
- `content`：消息内容。
- `mine`：是否是自己发的消息。
- `system`：是否是系统消息。

普通聊天消息：

```java
static ChatMessage chat(String sender, String time, String content, boolean mine)
```

系统消息：

```java
static ChatMessage system(String content)
```

比如：

```java
ChatMessage.system("已连接服务器")
```

## 21. addMessage：添加消息

所有消息最终都会进入：

```java
private void addMessage(ChatMessage message)
```

流程：

```java
cachedMessages.add(message);
while (cachedMessages.size() > MAX_CACHED_MESSAGES) {
    cachedMessages.remove(0);
}
addMessageView(message);
saveCachedMessages();
scrollToBottom();
```

含义：

1. 加入内存列表。
2. 如果超过 200 条，删除最早的消息。
3. 显示到界面。
4. 保存到本地缓存。
5. 滚动到底部。

## 22. addMessageView：画聊天气泡

普通聊天消息由这个方法显示：

```java
private void addMessageView(ChatMessage message)
```

如果是系统消息：

```java
if (message.system) {
    addSystemMessageView(message.content);
    return;
}
```

普通消息会创建一个纵向 `LinearLayout`：

```java
LinearLayout row = new LinearLayout(this);
row.setOrientation(LinearLayout.VERTICAL);
row.setGravity(message.mine ? Gravity.END : Gravity.START);
```

如果是自己发的：

```text
靠右显示
```

如果是别人发的：

```text
靠左显示
```

别人的消息会显示昵称：

```java
if (!message.mine) {
    TextView nameView = new TextView(this);
    nameView.setText(message.sender);
}
```

消息内容显示在气泡里：

```java
bubble.setText(message.content);
bubble.setBackgroundResource(message.mine ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_other);
```

自己发的消息用绿色气泡：

```text
bg_bubble_mine.xml
```

别人发的消息用白色气泡：

```text
bg_bubble_other.xml
```

时间单独显示在气泡下面：

```java
timeView.setText(message.time);
timeView.setTextColor(Color.rgb(165, 165, 165));
timeView.setTextSize(10);
```

所以时间看起来会更小、更淡，不会抢消息内容。

## 23. addSystemMessageView：画系统消息

系统消息由这个方法显示：

```java
private void addSystemMessageView(String content)
```

系统消息居中显示，颜色较浅：

```java
systemView.setGravity(Gravity.CENTER);
systemView.setTextColor(Color.rgb(130, 130, 130));
systemView.setTextSize(12);
```

比如：

```text
已连接服务器
张三（设备：vivo V2203A）已连接
张三（设备：vivo V2203A）已断开
```

## 24. 聊天记录缓存

聊天记录保存在 `SharedPreferences` 里，最多 200 条。

读取缓存：

```java
private void loadCachedMessages()
```

保存缓存：

```java
private void saveCachedMessages()
```

保存格式是 JSON 数组，例如：

```json
[
  {
    "sender": "张三",
    "time": "20:15:30",
    "content": "你好",
    "mine": false,
    "system": false
  }
]
```

每条 `ChatMessage` 可以转成 JSON：

```java
JSONObject toJson()
```

也可以从 JSON 还原：

```java
static ChatMessage fromJson(JSONObject object)
```

## 25. updateConnectionUi：统一更新连接状态

连接、断开、连接中，都会调用：

```java
private void updateConnectionUi(boolean connecting, String status)
```

它负责统一更新：

```java
connectProgress.setVisibility(...)
settingsButton.setEnabled(...)
connectButton.setEnabled(...)
connectButton.setText(connected ? "断开" : "连接");
sendButton.setEnabled(connected);
messageInput.setEnabled(connected);
statusText.setText(status);
```

这能避免不同地方重复写按钮状态逻辑。

状态变化可以理解成：

```text
未连接：连接按钮显示“连接”，输入框不可用
连接中：显示进度圈，按钮暂时不可点
已连接：连接按钮显示“断开”，输入框可用
```

## 26. showWelcomePage：欢迎界面

```java
private void showWelcomePage() {
    welcomePage.setVisibility(View.VISIBLE);
    new Handler(Looper.getMainLooper()).postDelayed(() -> welcomePage.setVisibility(View.GONE), 1200);
}
```

含义：

1. 先显示欢迎页。
2. 1.2 秒后自动隐藏。

`Handler` 用来安排一段延迟执行的代码。

`Looper.getMainLooper()` 表示这段代码在主线程执行，因为它要修改界面。

## 27. deviceName：获取设备名称

```java
private String deviceName()
```

它通过 Android 系统信息获取设备名称：

```java
Build.MANUFACTURER
Build.MODEL
```

例如可能得到：

```text
vivo V2203A
Xiaomi 23013RK75C
HUAWEI ALN-AL00
```

然后连接服务器时发送：

```java
writer.println("HELLO|" + clientName + "|" + deviceName());
```

服务器就能广播：

```text
张三（设备：vivo V2203A）已连接
```

## 28. dp 方法：把 dp 转成像素

Android 布局一般用 `dp`，但是 Java 代码动态创建控件时，很多方法需要传入像素值。

所以项目里有：

```java
private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
}
```

比如：

```java
bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
```

这样不同分辨率手机上显示尺寸会更接近。

## 29. onDestroy：页面销毁时清理资源

当 Activity 被销毁时，会执行：

```java
protected void onDestroy()
```

里面做了清理：

```java
manualDisconnect = true;
closeSocket();
executor.shutdownNow();
```

含义：

1. 标记为主动断开。
2. 关闭 Socket。
3. 关闭线程池。

这是为了避免 App 退出后网络连接和后台线程还残留。

## 30. 一条消息从发送到显示的完整流程

假设用户输入：

```text
你好
```

点击发送后：

```text
sendButton 点击
    ↓
sendMessage()
    ↓
writer.println("MSG|你好")
    ↓
服务器收到 MSG
    ↓
服务器广播 "MSG|昵称|时间|你好"
    ↓
客户端 readMessages() 读到这一行
    ↓
handleServerMessage()
    ↓
创建 ChatMessage
    ↓
addMessage()
    ↓
addMessageView()
    ↓
界面出现聊天气泡
```

这个流程是理解整个客户端最关键的一条线。

## 31. 一个客户端连接服务器的完整流程

```text
打开 App
    ↓
onCreate()
    ↓
读取本地 IP、端口、昵称
    ↓
显示欢迎页
    ↓
点击“连接”
    ↓
connect()
    ↓
new Socket(ip, port)
    ↓
发送 "HELLO|昵称|设备名称"
    ↓
启动 readMessages() 循环
    ↓
收到 USERS、MSG、SYS 等服务器消息
    ↓
更新顶部在线状态和聊天内容
```

## 32. 这个客户端目前的特点

优点：

- 代码集中，容易学习。
- 使用简单文本协议，便于理解网络通信。
- 有本地设置保存。
- 有本地聊天记录缓存。
- 有基本微信群聊式界面。
- 服务端和客户端逻辑配合清楚。

限制：

- 没有登录账号系统。
- 没有加密通信。
- 没有消息撤回、图片、语音等高级功能。
- 历史消息只保存在本机，不会同步到其他设备。
- 消息协议比较简单，如果消息中包含特殊分隔符，仍可能需要进一步处理。

## 33. 建议阅读顺序

如果你是初学者，建议按这个顺序看代码：

```text
1. AndroidManifest.xml
2. activity_main.xml
3. MainActivity.onCreate()
4. bindViews()
5. showSettingsDialog()
6. connect()
7. sendMessage()
8. readMessages()
9. handleServerMessage()
10. addMessageView()
11. loadCachedMessages() / saveCachedMessages()
```

先理解主流程，再回头看细节，会轻松很多。

