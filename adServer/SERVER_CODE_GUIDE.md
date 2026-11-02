# Java 服务端代码详解

本文档只讲本项目中的服务端 `adServer`，帮助你理解聊天室服务端是怎么启动、怎么监听端口、怎么接收 Android 客户端连接、怎么转发群聊消息的。

## 1. 服务端项目结构

服务端目录如下：

```text
adServer/
├── src/
│   └── Main.java
├── out/
│   ├── Main.class
│   └── Main$ClientHandler.class
├── start_server.sh
└── README.md
```

最重要的文件是：

```text
src/Main.java        服务端核心 Java 代码
start_server.sh     编译并启动服务端的脚本
```

`out/` 目录是编译后的 `.class` 文件输出目录，不是手写源码。

## 2. 服务端有几个类

服务端核心代码里有 2 个类：

```text
Main
└── ClientHandler
```

其中：

- `Main`：服务端入口，负责启动端口监听，等待客户端连接。
- `ClientHandler`：内部类，负责处理某一个客户端的连接、消息接收和断开通知。

`ClientHandler` 写在 `Main.java` 里面，所以服务端源码只有一个 `.java` 文件。

## 3. start_server.sh 脚本作用

文件位置：

```text
adServer/start_server.sh
```

这个脚本负责两件事：

```text
1. 编译 Java 服务端
2. 启动 Java 服务端
```

常用启动命令：

```bash
./start_server.sh 5000
```

其中 `5000` 是服务端监听端口。

脚本开头：

```bash
#!/usr/bin/env bash
set -euo pipefail
```

含义：

- `#!/usr/bin/env bash`：用 bash 执行脚本。
- `set -euo pipefail`：让脚本遇到错误时尽早停止，避免继续执行错误命令。

读取端口：

```bash
PORT="${1:-5000}"
```

含义是：

- 如果启动脚本时传了参数，就用传入的端口。
- 如果没有传参数，就默认使用 `5000`。

例如：

```bash
./start_server.sh 5000
```

此时 `PORT` 是 `5000`。

如果执行：

```bash
./start_server.sh
```

也会默认使用 `5000`。

切换到脚本所在目录：

```bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
```

这样无论你从哪里执行脚本，脚本都会自动进入 `adServer` 目录。

创建输出目录：

```bash
mkdir -p out
```

编译 Java：

```bash
javac -encoding UTF-8 -source 8 -target 8 -d out src/Main.java
```

含义：

- `javac`：Java 编译器。
- `-encoding UTF-8`：使用 UTF-8 编码，支持中文。
- `-source 8 -target 8`：按 Java 8 语法和目标版本编译。
- `-d out`：编译结果输出到 `out` 目录。
- `src/Main.java`：要编译的源码文件。

启动服务端：

```bash
exec java -cp out Main "$PORT"
```

含义：

- `java`：运行 Java 程序。
- `-cp out`：从 `out` 目录加载编译后的类。
- `Main`：运行 `Main` 类。
- `"$PORT"`：把端口传给 Java 程序。

## 4. Main.java 总览

文件位置：

```text
adServer/src/Main.java
```

它负责：

- 读取端口号。
- 创建 `ServerSocket`。
- 等待客户端连接。
- 为每个客户端创建一个线程。
- 保存在线客户端列表。
- 广播消息。
- 广播在线用户列表。
- 处理客户端上线和下线。

## 5. import 导入了什么

文件开头有很多 `import`：

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
...
```

重点可以分成几类：

网络相关：

```java
ServerSocket
Socket
```

- `ServerSocket`：服务端监听端口，等待客户端连接。
- `Socket`：服务端和某个客户端之间的连接。

输入输出相关：

```java
BufferedReader
InputStreamReader
OutputStreamWriter
PrintWriter
```

- `BufferedReader`：从客户端读取文本。
- `PrintWriter`：向客户端发送文本。

线程安全集合：

```java
ConcurrentHashMap
Set
AtomicInteger
```

- `ConcurrentHashMap.newKeySet()`：创建线程安全的客户端集合。
- `AtomicInteger`：安全地生成客户端编号。

时间格式：

```java
LocalDateTime
DateTimeFormatter
```

用于生成消息时间，比如：

```text
20:15:30
```

## 6. 静态变量

```java
private static final int DEFAULT_PORT = 5000;
```

默认端口是 `5000`。

```java
private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
```

给客户端生成编号：

```text
Client-1
Client-2
Client-3
```

`AtomicInteger` 是线程安全的，因为多个客户端可能同时连接。

```java
private static final Set<ClientHandler> CLIENTS = ConcurrentHashMap.newKeySet();
```

保存所有在线客户端。

为什么要线程安全？

因为每个客户端都有自己的线程，多个线程可能同时：

- 添加客户端。
- 删除客户端。
- 遍历客户端广播消息。

普通 `HashSet` 在这种情况下容易出错，所以这里用线程安全集合。

```java
private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
```

定义消息时间格式。

## 7. main 方法：服务端入口

Java 程序从这里开始执行：

```java
public static void main(String[] args)
```

第一步解析端口：

```java
int port = parsePort(args);
```

然后创建服务端 Socket：

```java
try (ServerSocket serverSocket = new ServerSocket(port)) {
```

这行代码的意思是：让服务器监听指定端口。

例如监听：

```text
5000
```

手机客户端连接：

```text
8.148.197.226:5000
```

就会连到这个 `ServerSocket`。

启动成功后打印：

```java
System.out.println("Server started on port " + port);
System.out.println("Waiting for Android clients...");
```

所以你在服务器终端能看到：

```text
Server started on port 5000
Waiting for Android clients...
```

## 8. accept：等待客户端连接

核心循环：

```java
while (true) {
    Socket socket = serverSocket.accept();
    ClientHandler client = new ClientHandler(socket);
    new Thread(client, "client-" + client.id).start();
}
```

这段代码非常重要。

`serverSocket.accept()` 会一直等待客户端连接。

当一个 Android 客户端连接进来时：

```java
Socket socket = serverSocket.accept();
```

就会得到一个新的 `Socket`。

然后创建客户端处理器：

```java
ClientHandler client = new ClientHandler(socket);
```

再创建线程：

```java
new Thread(client, "client-" + client.id).start();
```

也就是说：

```text
每连接一个手机
    ↓
服务端创建一个 ClientHandler
    ↓
服务端创建一个新线程处理这个手机
```

这样多个手机可以同时在线聊天。

## 9. parsePort：解析端口

```java
private static int parsePort(String[] args)
```

如果启动时没有传端口：

```java
if (args.length == 0) {
    return DEFAULT_PORT;
}
```

默认使用 `5000`。

如果传了端口，就尝试转成数字：

```java
return Integer.parseInt(args[0]);
```

如果传入的不是数字：

```java
catch (NumberFormatException e)
```

就打印提示，然后仍然使用默认端口。

## 10. broadcast：广播消息

```java
private static void broadcast(String message) {
    for (ClientHandler client : CLIENTS) {
        client.send(message);
    }
}
```

这个方法会把同一条消息发给所有在线客户端。

例如服务器收到张三发来的：

```text
MSG|你好
```

服务端会广播：

```text
MSG|张三|20:15:30|你好
```

所有在线手机都会收到这条消息。

## 11. broadcastUsers：广播在线用户列表

```java
private static void broadcastUsers()
```

它会遍历在线客户端：

```java
for (ClientHandler client : CLIENTS) {
    if (client.name != null) {
        names.add(client.name);
    }
}
```

然后发送：

```java
broadcast("USERS|" + String.join(",", names));
```

例如当前在线：

```text
张三
李四
```

服务端会广播：

```text
USERS|张三,李四
```

客户端收到后更新顶部：

```text
群聊 (2)
在线：张三、李四
```

## 12. now：生成当前时间

```java
private static String now() {
    return LocalDateTime.now().format(TIME_FORMATTER);
}
```

返回当前服务器时间，格式：

```text
HH:mm:ss
```

例如：

```text
21:06:15
```

聊天消息里的时间就是这里生成的。

## 13. ClientHandler 是什么

`ClientHandler` 是内部类：

```java
private static class ClientHandler implements Runnable
```

它代表一个客户端连接。

如果有 3 台手机连接服务器，就会有 3 个 `ClientHandler` 对象。

它实现了 `Runnable`，所以可以放进线程里运行：

```java
new Thread(client, "client-" + client.id).start();
```

线程启动后，会自动执行：

```java
public void run()
```

## 14. ClientHandler 的字段

```java
private final int id = NEXT_ID.getAndIncrement();
```

给每个客户端一个编号。

```java
private final Socket socket;
```

保存这个客户端的网络连接。

```java
private PrintWriter writer;
```

用来给这个客户端发送消息。

```java
private String name;
private String deviceName;
```

保存客户端昵称和设备名。

例如：

```text
name = "张三"
deviceName = "vivo V2203A"
```

## 15. ClientHandler 构造方法

```java
ClientHandler(Socket socket) {
    this.socket = socket;
}
```

当客户端连接进来时，`Main` 会把这个客户端的 `Socket` 传进来。

这个 `Socket` 之后用于：

- 读取客户端发来的消息。
- 给客户端发送服务器消息。

## 16. run：处理一个客户端的完整流程

`run()` 是 `ClientHandler` 最核心的方法。

第一步创建读取器和发送器：

```java
BufferedReader reader = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
);

PrintWriter out = new PrintWriter(
        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
        true
);
```

其中：

- `reader`：读客户端发来的消息。
- `out`：给客户端发送消息。
- `StandardCharsets.UTF_8`：保证中文正常。
- `true`：表示 `PrintWriter` 自动刷新。

保存发送器：

```java
writer = out;
```

读取客户端昵称和设备：

```java
name = readClientName(reader);
```

加入在线列表：

```java
CLIENTS.add(this);
```

打印日志：

```java
System.out.println(name + " connected from " + socket.getInetAddress().getHostAddress());
```

这里的 IP 是客户端来源 IP，比如手机流量或 Wi-Fi 的公网出口 IP。

发给当前客户端一条系统消息：

```java
send("SYS|Connected to server at " + now());
```

广播给所有人：

```java
broadcast("SYS|" + displayName() + " 已连接");
```

例如：

```text
SYS|张三（设备：vivo V2203A）已连接
```

广播在线用户列表：

```java
broadcastUsers();
```

然后开始循环读取客户端消息：

```java
while ((line = reader.readLine()) != null) {
    ...
}
```

只要客户端不断开，这个循环就会一直运行。

## 17. 处理客户端发来的 MSG

在循环里：

```java
if (line.startsWith("MSG|")) {
    String content = line.substring("MSG|".length()).trim();
    if (!content.isEmpty()) {
        broadcast("MSG|" + name + "|" + now() + "|" + content);
    }
}
```

客户端发来：

```text
MSG|你好
```

服务端取出：

```text
你好
```

然后加上发送者和时间，广播给所有客户端：

```text
MSG|张三|21:06:15|你好
```

这就是群聊转发的核心逻辑。

## 18. readClientName：读取昵称和设备名

客户端连接成功后，第一条消息应该是：

```text
HELLO|昵称|设备名称
```

服务端通过这个方法读取：

```java
private String readClientName(BufferedReader reader) throws IOException
```

先读一行：

```java
String hello = reader.readLine();
```

如果不是 `HELLO|` 开头，就给默认名字：

```java
String fallback = "Client-" + id;
```

如果格式正确：

```java
String[] parts = hello.split("\\|", 3);
```

最多切成 3 段：

```text
HELLO
昵称
设备名称
```

例如：

```text
HELLO|张三|vivo V2203A
```

会得到：

```text
parts[0] = HELLO
parts[1] = 张三
parts[2] = vivo V2203A
```

保存设备名：

```java
deviceName = sanitize(providedDevice.isEmpty() ? "未知设备" : providedDevice);
```

返回昵称：

```java
return providedName.isEmpty() ? fallback : sanitize(providedName);
```

## 19. sanitize：清理特殊字符

```java
private String sanitize(String value) {
    return value.replace(",", " ").replace("|", " ").trim();
}
```

因为协议里用：

```text
| 分隔字段
, 分隔用户列表
```

所以昵称和设备名里如果包含 `|` 或 `,`，可能破坏协议。

这里把它们替换成空格。

## 20. displayName：显示昵称和设备

```java
private String displayName() {
    String device = deviceName == null || deviceName.isEmpty() ? "未知设备" : deviceName;
    return name + "（设备：" + device + "）";
}
```

这个方法把昵称和设备拼在一起。

例如：

```text
张三（设备：vivo V2203A）
```

上线消息：

```text
张三（设备：vivo V2203A）已连接
```

下线消息：

```text
张三（设备：vivo V2203A）已断开
```

## 21. send：给某一个客户端发消息

```java
private void send(String message) {
    if (writer != null) {
        writer.println(message);
    }
}
```

`send` 是发给某一个客户端。

`broadcast` 是发给所有客户端。

关系是：

```text
broadcast(message)
    ↓
遍历所有 ClientHandler
    ↓
client.send(message)
```

## 22. closeAndNotify：客户端断开后的处理

```java
private void closeAndNotify()
```

这个方法在 `finally` 中调用：

```java
finally {
    closeAndNotify();
}
```

也就是说，无论客户端正常断开，还是网络异常断开，最后都会执行清理。

第一步从在线列表删除：

```java
boolean removed = CLIENTS.remove(this);
```

关闭 Socket：

```java
socket.close();
```

如果确实从在线列表删除成功，就广播下线消息：

```java
broadcast("SYS|" + displayName() + " 已断开");
broadcastUsers();
```

例如：

```text
SYS|张三（设备：vivo V2203A）已断开
USERS|李四
```

## 23. 服务端通信协议总结

客户端发给服务端：

```text
HELLO|昵称|设备名称
MSG|消息内容
```

服务端发给客户端：

```text
SYS|系统消息
USERS|用户1,用户2
MSG|发送者|时间|消息内容
```

完整例子：

```text
客户端 A 发：HELLO|张三|vivo V2203A
服务端广播：SYS|张三（设备：vivo V2203A）已连接
服务端广播：USERS|张三

客户端 A 发：MSG|你好
服务端广播：MSG|张三|21:06:15|你好

客户端 A 断开
服务端广播：SYS|张三（设备：vivo V2203A）已断开
服务端广播：USERS|
```

## 24. 多客户端群聊是怎么实现的

核心机制是：

```text
每个客户端一个线程
所有客户端放进 CLIENTS 集合
收到一个客户端的消息后，遍历 CLIENTS 发给所有人
```

流程：

```text
手机 A 发送 MSG|你好
    ↓
A 对应的 ClientHandler 收到消息
    ↓
调用 broadcast(...)
    ↓
遍历 CLIENTS
    ↓
手机 A、手机 B、手机 C 都收到消息
```

所以这就是群聊。

## 25. 一台手机连接服务器的完整流程

```text
Android 客户端连接 8.148.197.226:5000
    ↓
ServerSocket.accept() 返回一个 Socket
    ↓
创建 ClientHandler
    ↓
创建新线程
    ↓
ClientHandler.run()
    ↓
读取 HELLO|昵称|设备名称
    ↓
加入 CLIENTS 在线集合
    ↓
广播上线系统消息
    ↓
广播在线用户列表
    ↓
进入 while 循环等待 MSG
```

## 26. 一条聊天消息的完整流程

```text
客户端发送：MSG|你好
    ↓
服务端 readLine() 读到这一行
    ↓
判断 line.startsWith("MSG|")
    ↓
取出消息内容 "你好"
    ↓
拼成 MSG|昵称|时间|你好
    ↓
broadcast 给所有在线客户端
    ↓
每个客户端界面显示聊天气泡
```

## 27. 客户端断开的完整流程

```text
客户端关闭连接 / 网络断开 / App 退出
    ↓
服务端 reader.readLine() 返回 null 或抛出 IOException
    ↓
跳出 while 循环或进入 catch
    ↓
finally 执行 closeAndNotify()
    ↓
从 CLIENTS 删除该客户端
    ↓
关闭 socket
    ↓
广播 已断开 系统消息
    ↓
广播新的 USERS 在线列表
```

## 28. 为什么 CLIENTS 要用线程安全集合

每个客户端都有一个线程，比如：

```text
client-1 线程
client-2 线程
client-3 线程
```

它们可能同时操作 `CLIENTS`：

- 有人上线，添加到 `CLIENTS`。
- 有人下线，从 `CLIENTS` 删除。
- 有人发消息，遍历 `CLIENTS` 广播。

如果用普通集合，可能出现并发错误。

所以使用：

```java
ConcurrentHashMap.newKeySet()
```

它适合这种多线程场景。

## 29. 服务端目前的优点

- 代码短，适合学习。
- 逻辑清楚，只有一个源码文件。
- 协议简单，容易调试。
- 支持多个 Android 客户端同时聊天。
- 支持在线列表。
- 支持上线/下线系统通知。
- 支持显示设备名称。

## 30. 服务端目前的限制

- 没有账号和密码。
- 没有加密通信。
- 没有数据库。
- 不保存历史消息。
- 没有心跳检测。
- 没有限制非法连接。
- 一个客户端一个线程，学习项目够用，但大型项目通常会使用更高级的网络框架或线程池。

## 31. 建议阅读顺序

如果你是初学者，建议这样读：

```text
1. start_server.sh
2. Main.java 的静态变量
3. main()
4. parsePort()
5. ClientHandler 构造方法
6. ClientHandler.run()
7. readClientName()
8. broadcast()
9. broadcastUsers()
10. closeAndNotify()
```

看懂这 10 个点，就基本看懂服务端了。

## 32. 最核心的一句话

这个服务端的本质是：

```text
监听 5000 端口，接收多个客户端连接；谁发消息，就把消息转发给所有在线客户端。
```

