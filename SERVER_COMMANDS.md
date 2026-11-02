# 服务器基础命令参考

本文档记录阿里云服务器上部署和管理聊天室服务端时常用的 Linux 命令。

## 进入服务端目录

```bash
cd /root/adServer
```

查看当前目录文件：

```bash
ls -la
```

## 启动服务端

前台启动：

```bash
./start_server.sh 5000
```

看到下面内容表示启动成功：

```text
Server started on port 5000
Waiting for Android clients...
```

前台启动会占用当前终端。停止时按：

```text
Ctrl + C
```

## 后台启动服务端

后台启动后，关闭 SSH 窗口服务端也会继续运行：

```bash
nohup ./start_server.sh 5000 > server.log 2>&1 &
```

查看后台日志：

```bash
tail -f server.log
```

退出日志查看：

```text
Ctrl + C
```

## 查看服务端进程

查看 Java 聊天服务端进程：

```bash
ps -ef | grep "java -cp out Main"
```

查看 5000 端口是否被占用：

```bash
ss -tunlp | grep 5000
```

如果系统支持 `netstat`，也可以用：

```bash
netstat -tunlp | grep 5000
```

## 停止服务端

按命令匹配停止：

```bash
pkill -f "java -cp out Main 5000"
```

如果知道进程 ID，也可以停止指定进程：

```bash
kill 进程ID
```

例如：

```bash
kill 12345
```

## 重新启动服务端

常用完整流程：

```bash
cd /root/adServer
pkill -f "java -cp out Main 5000"
./start_server.sh 5000
```

后台重启：

```bash
cd /root/adServer
pkill -f "java -cp out Main 5000"
nohup ./start_server.sh 5000 > server.log 2>&1 &
tail -f server.log
```

## 手动编译和启动

如果没有使用脚本，也可以手动编译：

```bash
cd /root/adServer
mkdir -p out
javac -encoding UTF-8 -source 8 -target 8 -d out src/Main.java
java -cp out Main 5000
```

后台运行手动启动版本：

```bash
nohup java -cp out Main 5000 > server.log 2>&1 &
```

## 查看服务器网络信息

查看服务器 IP：

```bash
curl ifconfig.me
```

查看当前监听端口：

```bash
ss -tunlp
```

只查看 5000 端口：

```bash
ss -tunlp | grep 5000
```

## 查看日志文件

实时查看日志：

```bash
tail -f server.log
```

查看最后 100 行日志：

```bash
tail -n 100 server.log
```

清空日志文件：

```bash
> server.log
```

## 上传新版服务端后的操作

上传或替换新版 `adServer` 后，通常执行：

```bash
cd /root/adServer
chmod +x start_server.sh
pkill -f "java -cp out Main 5000"
./start_server.sh 5000
```

如果想后台运行：

```bash
cd /root/adServer
chmod +x start_server.sh
pkill -f "java -cp out Main 5000"
nohup ./start_server.sh 5000 > server.log 2>&1 &
tail -f server.log
```

## 常见问题

### 提示 No such file or directory

先确认文件名和当前目录：

```bash
pwd
ls -la
```

比如脚本名是 `start_server.sh`，不要写成 `start_sever.sh`。

### 端口被占用

查看占用端口的进程：

```bash
ss -tunlp | grep 5000
```

停止旧服务端：

```bash
pkill -f "java -cp out Main 5000"
```

### 手机连不上服务器

检查三件事：

1. 服务端是否启动成功。
2. 阿里云安全组是否放行 TCP 端口 `5000`。
3. Android 客户端填写的 IP 和端口是否正确。

