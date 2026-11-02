package com.example.adclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    // SharedPreferences 用来保存少量本地数据：服务器地址、昵称和聊天记录缓存。
    private static final String PREFS_NAME = "chat_settings";
    private static final String KEY_IP = "server_ip";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_NAME = "client_name";
    private static final String KEY_MESSAGES = "cached_messages";
    private static final String DEFAULT_IP = "8.148.197.226";
    private static final String DEFAULT_PORT = "5000";
    private static final int MAX_CACHED_MESSAGES = 200;
    // Socket 网络操作不能放在 Android 主线程里，所以使用线程池执行连接、发送、接收。
    private final ExecutorService executor = Executors.newCachedThreadPool();
    // 内存中的消息列表，退出 App 前会同步保存到 SharedPreferences。
    private final List<ChatMessage> cachedMessages = new ArrayList<>();
    // 下面这些成员变量对应 activity_main.xml 中的界面控件。
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
    private SharedPreferences prefs;
    // volatile 让后台线程和主线程都能及时看到 socket/writer 的最新状态。
    private volatile Socket socket;
    private volatile PrintWriter writer;
    // 用来区分“用户主动断开”和“服务器/网络异常断开”，避免重复提示。
    private volatile boolean manualDisconnect = false;
    private boolean connected = false;
    private String serverIp = DEFAULT_IP;
    private String serverPort = DEFAULT_PORT;
    private String serverAddress = "";
    private String clientName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        bindViews();
        // 启动时先恢复本地配置和历史消息，再显示主界面状态。
        loadSettings();
        loadCachedMessages();
        renderAllMessages();
        updateConnectionUi(false, "未连接");
        showWelcomePage();

        settingsButton.setOnClickListener(v -> showSettingsDialog(false));
        connectButton.setOnClickListener(v -> {
            if (connected) {
                disconnect("已断开连接");
            } else {
                connect();
            }
        });
        sendButton.setOnClickListener(v -> sendMessage());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        if (!hasSavedSettings()) {
            // 第一次打开 App 时必须填写服务器 IP、端口和昵称。
            showSettingsDialog(true);
        }
    }

    // 把 XML 布局中的控件绑定到 Java 变量，后续才能修改文字、状态和可见性。
    private void bindViews() {
        titleText = findViewById(R.id.titleText);
        statusText = findViewById(R.id.statusText);
        settingsButton = findViewById(R.id.settingsButton);
        connectButton = findViewById(R.id.connectButton);
        sendButton = findViewById(R.id.sendButton);
        connectProgress = findViewById(R.id.connectProgress);
        welcomePage = findViewById(R.id.welcomePage);
        messagesContainer = findViewById(R.id.messagesContainer);
        messageInput = findViewById(R.id.messageInput);
        messageScroll = findViewById(R.id.messageScroll);
    }

    // 从本地读取上次保存的服务器 IP、端口和昵称；没有保存过就使用默认值。
    private void loadSettings() {
        serverIp = prefs.getString(KEY_IP, DEFAULT_IP);
        serverPort = prefs.getString(KEY_PORT, DEFAULT_PORT);
        clientName = prefs.getString(KEY_NAME, "");
        if (TextUtils.isEmpty(clientName)) {
            clientName = "用户" + (System.currentTimeMillis() % 10000);
        }
        serverAddress = serverIp + ":" + serverPort;
    }

    // 判断用户是否已经完成过首次配置。
    private boolean hasSavedSettings() {
        return prefs.contains(KEY_IP) && prefs.contains(KEY_PORT) && prefs.contains(KEY_NAME);
    }

    // 显示连接设置弹窗。required=true 时不可取消，用于首次启动强制配置。
    private void showSettingsDialog(boolean required) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        form.setPadding(padding, dp(8), padding, 0);

        TextInputEditText ipInput = addDialogInput(form, "服务器 IP", serverIp, "text");
        TextInputEditText portInput = addDialogInput(form, "端口号", serverPort, "number");
        TextInputEditText nameInput = addDialogInput(form, "我的昵称", clientName, "text");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("连接设置")
                .setView(form)
                .setPositiveButton("保存", null)
                .setNegativeButton(required ? null : "取消", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ip = textOf(ipInput);
            String port = textOf(portInput);
            String name = sanitizeName(textOf(nameInput));
            // 保存前做基本校验，避免空地址、非法端口或空昵称进入连接流程。
            if (ip.isEmpty() || port.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "请填写 IP、端口和昵称", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int parsedPort = Integer.parseInt(port);
                if (parsedPort <= 0 || parsedPort > 65535) {
                    Toast.makeText(this, "端口号应在 1-65535 之间", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口号格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean changedConnection = !ip.equals(serverIp)
                    || !port.equals(serverPort)
                    || !name.equals(clientName);
            serverIp = ip;
            serverPort = port;
            clientName = name;
            serverAddress = serverIp + ":" + serverPort;
            // apply() 是异步保存，适合这种轻量配置。
            prefs.edit()
                    .putString(KEY_IP, serverIp)
                    .putString(KEY_PORT, serverPort)
                    .putString(KEY_NAME, clientName)
                    .apply();
            if (changedConnection && connected) {
                disconnect("设置已更新，请重新连接");
            } else {
                updateConnectionUi(false, connected ? statusText.getText().toString() : "未连接");
            }
            dialog.dismiss();
        }));

        dialog.setCancelable(!required);
        dialog.setCanceledOnTouchOutside(!required);
        dialog.show();
    }

    // 动态创建弹窗里的输入框，避免专门再写一个 dialog XML。
    private TextInputEditText addDialogInput(LinearLayout parent, String hint, String value, String inputType) {
        TextInputLayout layout = new TextInputLayout(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, dp(10), 0, 0);
        layout.setLayoutParams(layoutParams);
        layout.setHint(hint);
        layout.setBoxCornerRadii(dp(8), dp(8), dp(8), dp(8));

        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        if ("number".equals(inputType)) {
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        } else {
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        }
        layout.addView(input);
        parent.addView(layout);
        return input;
    }

    // 连接阿里云服务器。真实网络连接在后台线程中执行，连接成功后再回主线程更新 UI。
    private void connect() {
        String ip = serverIp;
        String portText = serverPort;
        if (ip.isEmpty() || portText.isEmpty() || clientName.isEmpty()) {
            showSettingsDialog(true);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showSettingsDialog(true);
            return;
        }

        setConnecting(true);
        manualDisconnect = false;
        statusText.setText("正在连接 " + ip + ":" + port + "...");

        executor.execute(() -> {
            try {
                // Socket 建立的是 TCP 连接，对应服务端的 ServerSocket.accept()。
                Socket newSocket = new Socket(ip, port);
                PrintWriter newWriter = new PrintWriter(
                        new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8),
                        true
                );
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8)
                );

                socket = newSocket;
                writer = newWriter;
                serverAddress = ip + ":" + port;
                // 第一条消息是握手消息，告诉服务端自己的昵称和设备名称。
                writer.println("HELLO|" + clientName + "|" + deviceName());

                runOnUiThread(() -> {
                    connected = true;
                    updateConnectionUi(false, "已连接 " + serverAddress);
                    addMessage(ChatMessage.system("已连接服务器"));
                });
                readMessages(reader);
            } catch (IOException e) {
                if (!manualDisconnect) {
                    runOnUiThread(() -> {
                        connected = false;
                        updateConnectionUi(false, "连接失败：" + e.getMessage());
                    });
                }
                closeSocket();
            }
        });
    }

    // 持续读取服务端发来的每一行文本。服务端断开时 readLine() 会返回 null。
    private void readMessages(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            handleServerMessage(line);
        }

        if (!manualDisconnect) {
            runOnUiThread(() -> disconnect("服务器连接已断开"));
        }
    }

    // 解析服务端协议：USERS 更新在线列表，MSG 显示聊天消息，SYS 显示系统消息。
    private void handleServerMessage(String line) {
        if (line.startsWith("USERS|")) {
            String usersPart = line.substring("USERS|".length());
            List<String> users = usersPart.isEmpty()
                    ? new ArrayList<>()
                    : new ArrayList<>(Arrays.asList(usersPart.split(",")));
            runOnUiThread(() -> updateUsers(users));
        } else if (line.startsWith("MSG|")) {
            // 最多切成 4 段，保证消息内容里即使有竖线，也尽量保留在第 4 段。
            String[] parts = line.split("\\|", 4);
            if (parts.length == 4) {
                ChatMessage message = ChatMessage.chat(
                        parts[1],
                        parts[2],
                        parts[3],
                        parts[1].equals(clientName)
                );
                runOnUiThread(() -> addMessage(message));
            }
        } else if (line.startsWith("SYS|")) {
            runOnUiThread(() -> addMessage(ChatMessage.system(line.substring("SYS|".length()))));
        }
    }

    // 根据服务端广播的在线用户名列表，刷新顶部标题和在线状态。
    private void updateUsers(List<String> users) {
        int onlineCount = users.size();
        titleText.setText("群聊 (" + onlineCount + ")");
        String names = users.isEmpty() ? "暂无在线成员" : TextUtils.join("、", users);
        statusText.setText("在线：" + names);
    }

    // 把输入框内容发送给服务端。客户端不直接显示，等服务端广播回来后再显示。
    private void sendMessage() {
        String content = textOf(messageInput).replace("\n", " ").trim();
        if (content.isEmpty()) {
            return;
        }
        if (writer == null) {
            Toast.makeText(this, "请先连接服务器", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            PrintWriter currentWriter = writer;
            if (currentWriter != null) {
                // MSG 是客户端发给服务端的聊天消息协议。
                currentWriter.println("MSG|" + content);
            }
        });
        messageInput.setText("");
    }

    // 主动或被动断开连接后，统一清理 Socket、更新 UI，并添加一条系统消息。
    private void disconnect(String message) {
        manualDisconnect = true;
        closeSocket();
        connected = false;
        updateConnectionUi(false, message);
        addMessage(ChatMessage.system(message));
    }

    // 关闭 Socket。关闭 socket 会让后台 readLine() 结束。
    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
            writer = null;
        }
    }

    // 添加一条消息：先进入内存列表，再渲染到界面，最后保存到本地缓存。
    private void addMessage(ChatMessage message) {
        cachedMessages.add(message);
        while (cachedMessages.size() > MAX_CACHED_MESSAGES) {
            cachedMessages.remove(0);
        }
        addMessageView(message);
        saveCachedMessages();
        scrollToBottom();
    }

    // App 启动时把本地缓存的历史消息重新画到消息区。
    private void renderAllMessages() {
        messagesContainer.removeAllViews();
        for (ChatMessage message : cachedMessages) {
            addMessageView(message);
        }
        scrollToBottom();
    }

    // 动态创建聊天气泡。自己的消息靠右，别人的消息靠左，系统消息居中。
    private void addMessageView(ChatMessage message) {
        if (message.system) {
            addSystemMessageView(message.content);
            return;
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        // Gravity.END 表示靠右，Gravity.START 表示靠左。
        row.setGravity(message.mine ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(6), 0, dp(6));
        messagesContainer.addView(row, rowParams);

        if (!message.mine) {
            // 别人的消息在气泡上方显示昵称，自己的消息不重复显示昵称。
            TextView nameView = new TextView(this);
            nameView.setText(message.sender);
            nameView.setTextColor(Color.rgb(120, 120, 120));
            nameView.setTextSize(12);
            nameView.setPadding(dp(4), 0, dp(4), dp(3));
            row.addView(nameView);
        }

        TextView bubble = new TextView(this);
        bubble.setText(message.content);
        bubble.setTextColor(Color.rgb(17, 17, 17));
        bubble.setTextSize(15);
        bubble.setLineSpacing(dp(2), 1.0f);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackgroundResource(message.mine ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_other);
        // 限制气泡最大宽度，避免长消息横向撑满屏幕。
        bubble.setMaxWidth(getResources().getDisplayMetrics().widthPixels - dp(96));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        row.addView(bubble, bubbleParams);

        if (!message.time.isEmpty()) {
            // 时间放在气泡下方，并用更小更浅的文字，避免干扰消息内容。
            TextView timeView = new TextView(this);
            timeView.setText(message.time);
            timeView.setTextColor(Color.rgb(165, 165, 165));
            timeView.setTextSize(10);
            timeView.setGravity(message.mine ? Gravity.END : Gravity.START);
            timeView.setPadding(dp(4), dp(3), dp(4), 0);

            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            row.addView(timeView, timeParams);
        }
    }

    // 系统消息不使用气泡，居中显示，例如“已连接服务器”“某某已断开”。
    private void addSystemMessageView(String content) {
        TextView systemView = new TextView(this);
        systemView.setText(content);
        systemView.setTextColor(Color.rgb(130, 130, 130));
        systemView.setTextSize(12);
        systemView.setGravity(Gravity.CENTER);
        systemView.setPadding(dp(8), dp(5), dp(8), dp(5));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        messagesContainer.addView(systemView, params);
    }

    // 从 SharedPreferences 读取 JSON 字符串，并恢复成 ChatMessage 列表。
    private void loadCachedMessages() {
        cachedMessages.clear();
        String raw = prefs.getString(KEY_MESSAGES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            int start = Math.max(0, array.length() - MAX_CACHED_MESSAGES);
            for (int i = start; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                cachedMessages.add(ChatMessage.fromJson(object));
            }
        } catch (JSONException e) {
            // 如果缓存格式损坏，清掉缓存，避免 App 每次启动都解析失败。
            prefs.edit().remove(KEY_MESSAGES).apply();
        }
    }

    // 把内存中的消息列表转成 JSON 字符串保存到本地。
    private void saveCachedMessages() {
        JSONArray array = new JSONArray();
        for (ChatMessage message : cachedMessages) {
            array.put(message.toJson());
        }
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply();
    }

    // 统一管理连接相关 UI，避免连接、断开、失败等地方重复写按钮逻辑。
    private void updateConnectionUi(boolean connecting, String status) {
        connectProgress.setVisibility(connecting ? View.VISIBLE : View.GONE);
        settingsButton.setEnabled(!connecting);
        connectButton.setEnabled(!connecting);
        connectButton.setText(connected ? "断开" : "连接");
        sendButton.setEnabled(connected);
        messageInput.setEnabled(connected);
        statusText.setText(status);
        if (!connected) {
            titleText.setText("群聊");
        }
    }

    // 连接中状态的快捷方法。
    private void setConnecting(boolean connecting) {
        updateConnectionUi(connecting, connecting ? "正在连接..." : statusText.getText().toString());
    }

    // 打开 App 后短暂显示欢迎页，1.2 秒后自动隐藏。
    private void showWelcomePage() {
        welcomePage.setVisibility(View.VISIBLE);
        new Handler(Looper.getMainLooper()).postDelayed(() -> welcomePage.setVisibility(View.GONE), 1200);
    }

    // 每次新增消息后自动滚动到底部，保证用户看到最新消息。
    private void scrollToBottom() {
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    // 安全读取 TextView/EditText 的文本并去掉首尾空格。
    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    // 昵称会进入文本协议，去掉协议分隔符，防止破坏 USERS 或 HELLO 格式。
    private String sanitizeName(String name) {
        return name.replace("|", " ").replace(",", " ").trim();
    }

    // 获取手机设备名，发送给服务端用于“某某（设备：xxx）已连接”的系统提示。
    private String deviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "Android" : Build.MODEL.trim();
        String device;
        if (!manufacturer.isEmpty() && !model.toLowerCase().contains(manufacturer.toLowerCase())) {
            device = manufacturer + " " + model;
        } else {
            device = model;
        }
        return device.replace("|", " ").replace(",", " ").trim();
    }

    // Java 动态创建控件时尺寸单位是像素，这里把 dp 转成像素，适配不同屏幕密度。
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        // Activity 销毁时释放网络和线程资源，避免后台连接残留。
        manualDisconnect = true;
        closeSocket();
        executor.shutdownNow();
        super.onDestroy();
    }

    // 聊天消息的数据模型。普通消息、系统消息、本地缓存都用这个类表示。
    private static class ChatMessage {
        final String sender;
        final String time;
        final String content;
        final boolean mine;
        final boolean system;

        private ChatMessage(String sender, String time, String content, boolean mine, boolean system) {
            this.sender = sender;
            this.time = time;
            this.content = content;
            this.mine = mine;
            this.system = system;
        }

        // 创建普通聊天消息。
        static ChatMessage chat(String sender, String time, String content, boolean mine) {
            return new ChatMessage(sender, time, content, mine, false);
        }

        // 创建系统消息，例如连接、断开、上线提示。
        static ChatMessage system(String content) {
            return new ChatMessage("系统", "", content, false, true);
        }

        // 从本地缓存的 JSON 对象恢复一条消息。
        static ChatMessage fromJson(JSONObject object) {
            return new ChatMessage(
                    object.optString("sender", ""),
                    object.optString("time", ""),
                    object.optString("content", ""),
                    object.optBoolean("mine", false),
                    object.optBoolean("system", false)
            );
        }

        // 把一条消息转成 JSON，方便保存到 SharedPreferences。
        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("sender", sender);
                object.put("time", time);
                object.put("content", content);
                object.put("mine", mine);
                object.put("system", system);
            } catch (JSONException ignored) {
            }
            return object;
        }
    }
}
