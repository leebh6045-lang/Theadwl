import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    // 启动脚本没有传端口时，默认监听 5000。
    private static final int DEFAULT_PORT = 5000;
    // 给每个连接进来的客户端生成一个递增编号，作为兜底昵称使用。
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    // 保存所有在线客户端。多个客户端线程会同时访问它，所以使用线程安全集合。
    private static final Set<ClientHandler> CLIENTS = ConcurrentHashMap.newKeySet();
    // 服务端给聊天消息生成时间，格式如 21:30:05。
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        int port = parsePort(args);

        // ServerSocket 是服务端监听端口的对象，Android 客户端会连接到这个端口。
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            System.out.println("Waiting for Android clients...");

            while (true) {
                // accept() 会阻塞等待。每当一个客户端连接进来，就返回一个 Socket。
                Socket socket = serverSocket.accept();
                ClientHandler client = new ClientHandler(socket);
                // 每个客户端单独一个线程，这样多个手机可以同时聊天。
                new Thread(client, "client-" + client.id).start();
            }
        } catch (IOException e) {
            System.err.println("Server stopped: " + e.getMessage());
        }
    }

    // 从启动参数解析端口。例如 java -cp out Main 5000 中的 5000。
    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }

        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port, using default " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    // 给所有在线客户端发送同一条消息，这是群聊转发的核心。
    private static void broadcast(String message) {
        for (ClientHandler client : CLIENTS) {
            client.send(message);
        }
    }

    // 广播当前在线用户昵称列表，客户端收到 USERS 后刷新顶部在线人数和名称。
    private static void broadcastUsers() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : CLIENTS) {
            if (client.name != null) {
                names.add(client.name);
            }
        }
        broadcast("USERS|" + String.join(",", names));
    }

    // 生成当前服务器时间，作为聊天消息时间戳。
    private static String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    // 一个 ClientHandler 对象代表一个已连接的 Android 客户端。
    private static class ClientHandler implements Runnable {
        private final int id = NEXT_ID.getAndIncrement();
        // 当前客户端对应的 TCP 连接。
        private final Socket socket;
        // writer 用来给这个客户端发消息。
        private PrintWriter writer;
        // 客户端昵称和设备名来自客户端第一条 HELLO 消息。
        private String name;
        private String deviceName;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // try-with-resources 会在 run() 结束时自动关闭 reader/out。
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
            ) {
                writer = out;
                // 客户端连接后第一条消息应为 HELLO|昵称|设备名。
                name = readClientName(reader);
                // 完成握手后加入在线列表，之后才能收到广播。
                CLIENTS.add(this);

                System.out.println(name + " connected from " + socket.getInetAddress().getHostAddress());
                // 只发给当前客户端，告诉它已经连上服务器。
                send("SYS|Connected to server at " + now());
                // 广播给所有客户端，显示“某某（设备：xxx）已连接”。
                broadcast("SYS|" + displayName() + " 已连接");
                // 每次上线后都广播最新在线用户列表。
                broadcastUsers();

                String line;
                // 持续读取当前客户端发来的消息，直到客户端断开连接。
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MSG|")) {
                        String content = line.substring("MSG|".length()).trim();
                        if (!content.isEmpty()) {
                            // 服务端补上发送者和时间，再广播给所有客户端。
                            broadcast("MSG|" + name + "|" + now() + "|" + content);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection lost: " + (name == null ? "client-" + id : name));
            } finally {
                // 无论正常断开还是异常断开，都要移出在线列表并通知其他人。
                closeAndNotify();
            }
        }

        // 读取并解析客户端的第一条握手消息：HELLO|昵称|设备名。
        private String readClientName(BufferedReader reader) throws IOException {
            String hello = reader.readLine();
            String fallback = "Client-" + id;
            if (hello == null || !hello.startsWith("HELLO|")) {
                // 如果旧客户端或异常客户端没有发送 HELLO，就使用兜底名称。
                deviceName = "未知设备";
                return fallback;
            }

            // 最多切成 3 段，设备名里如果有额外内容也留在第 3 段。
            String[] parts = hello.split("\\|", 3);
            String providedName = parts.length >= 2 ? parts[1].trim() : "";
            String providedDevice = parts.length >= 3 ? parts[2].trim() : "";
            deviceName = sanitize(providedDevice.isEmpty() ? "未知设备" : providedDevice);
            return providedName.isEmpty() ? fallback : sanitize(providedName);
        }

        // 昵称和设备名会进入文本协议，移除分隔符，避免破坏 USERS 或 HELLO 格式。
        private String sanitize(String value) {
            return value.replace(",", " ").replace("|", " ").trim();
        }

        // 用于系统通知的显示名称，例如：张三（设备：vivo V2203A）。
        private String displayName() {
            String device = deviceName == null || deviceName.isEmpty() ? "未知设备" : deviceName;
            return name + "（设备：" + device + "）";
        }

        // 给当前这个客户端发送一行协议文本。
        private void send(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        // 客户端离线后的清理逻辑：删除在线状态、关闭连接、广播下线和在线列表。
        private void closeAndNotify() {
            boolean removed = CLIENTS.remove(this);
            try {
                socket.close();
            } catch (IOException ignored) {
            }

            if (removed && name != null) {
                System.out.println(name + " disconnected");
                broadcast("SYS|" + displayName() + " 已断开");
                broadcastUsers();
            }
        }
    }
}
