# Android Socket Chat Server

这是课程项目的 Java 服务端。两个 Android 客户端连接到同一个服务器后，可以同步在线客户端列表，并互相发送消息。

## 本地启动

```bash
cd adServer
chmod +x start_server.sh
./start_server.sh 5000
```

看到下面输出就表示服务端已启动：

```text
Server started on port 5000
Waiting for Android clients...
```

## 阿里云服务器启动

1. 在阿里云控制台安全组中放行 TCP 端口 `5000`。
2. 把 `adServer` 文件夹上传到云服务器。
3. 登录服务器，确认 Java 可用：

```bash
java -version
javac -version
```

如果能看到版本号，说明 Java 环境已经可以使用，不需要重复安装。

如果 `java -version` 有结果，但 `javac -version` 提示 `command not found`，说明服务器只安装了 Java 运行环境，还缺少 JDK 编译器。可以执行：

```bash
yum install -y java-17-openjdk-devel
```

如果提示没有这个包，再试：

```bash
dnf install -y java-17-openjdk-devel
```

4. 进入服务端目录并启动：

```bash
cd adServer
chmod +x start_server.sh
./start_server.sh 5000
```

5. Android 客户端填写：

```text
IP：8.148.197.226
端口：5000
```

## 后台运行

如果需要关闭终端后服务仍继续运行：

```bash
cd adServer
chmod +x start_server.sh
nohup ./start_server.sh 5000 > server.log 2>&1 &
```

查看日志：

```bash
tail -f server.log
```

停止服务：

```bash
pkill -f "java -cp out Main 5000"
```

## 通信协议

- 客户端连接后发送：`HELLO|客户端名称`
- 服务端广播在线列表：`USERS|客户端1,客户端2`
- 客户端发送消息：`MSG|消息内容`
- 服务端广播消息：`MSG|发送者|时间|消息内容`
- 服务端广播系统通知：`SYS|通知内容`
