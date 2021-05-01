package com.ustc.bio_chatroom.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 聊天室服务端
 * 注意在进行多线程处理连接请求的时候要考虑线程的安全性
 * 使用线程池实现伪异步IO, 减少线程创建和销毁带来的资源损耗
 * @author Ning
 * @since 2020.04.01
 */
public class ChatServer {
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";

    private ServerSocket serverSocket;
    private Map<Integer, Writer> connectedClients;
    private ThreadPoolExecutor threadPoolExecutor;

    // 线程池的参数设置
    private static final int CPU_CORE_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_CORE_SIZE * 2;
    private static final int MAX_POOL_SIZE = CPU_CORE_SIZE * 4;
    private static final int BLOCK_QUEUE_SIZE = 1000;

    public ChatServer() {
        threadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(BLOCK_QUEUE_SIZE));
        connectedClients = new HashMap<>();
    }

    public synchronized void addClient(Socket socket) throws IOException {
        if (socket != null) {
            int port = socket.getPort();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
            connectedClients.put(port, writer);
            System.out.println("客户端[" + port + "]已经连接到服务器");
        }
    }

    public synchronized void removeClient(Socket socket) throws IOException {
        if (socket != null) {
            int port = socket.getPort();
            if (connectedClients.containsKey(port)) {
                connectedClients.get(port).close();
            }
            connectedClients.remove(port);
            System.out.println("客户端[" + port + "]已经断开连接");
        }
    }

    public synchronized void forwardMessage(Socket socket, String fwdMsg) throws IOException {
        for (Integer port : connectedClients.keySet()) {
            if (!port.equals(socket.getPort())) {
                Writer writer = connectedClients.get(port);
                writer.write(fwdMsg);
                writer.flush();
            }
        }
    }

    /**
     * 服务器启动方法
     */
    public void start() {
        try {
            // 绑定监听端口
            serverSocket = new ServerSocket(DEFAULT_PORT);
            System.out.println("启动服务器, 监听端口: " + DEFAULT_PORT);
            while (true) {
                // 等待客户端连接
                Socket socket = serverSocket.accept();
                // 创建ChatHandler线程处理连接
                // new Thread(new ChatHandler(this, socket)).start();
                threadPoolExecutor.execute(new ChatHandler(this, socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    /**
     * 关闭 serverSocket
     */
    public synchronized void close() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                System.out.println("已经关闭了serverSocket");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查用户是否准备退出
     * @param msg
     * @return
     */
    public boolean readyToQuit(String msg) {
        if (msg.equalsIgnoreCase(QUIT)) {
            return true;
        }
        return false;
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start();
    }
}
