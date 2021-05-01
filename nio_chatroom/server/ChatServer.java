package com.ustc.nio_chatroom.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 聊天室服务端
 */
public class ChatServer {
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER_SIZE = 1024;

    private ServerSocketChannel server;
    private Selector selector; //多路复用器
    private ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private Charset charset = Charset.forName("UTF-8");
    private int port;

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public ChatServer(int port) {
        this.port = port;
    }

    /**
     * 服务端启动方法
     */
    public void start() {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.socket().bind(new InetSocketAddress(port));

            // 在epoll模型下, open() --> epoll_create()
            selector = Selector.open();
            // 如果是poll select: jvm 开辟一个数组将fd(listen状态)放入
            // 如果是epoll: 调用epoll_ctl()
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("启动服务器, 监听端口: " + port);

            while (true) {
                // 调用多路复用器 epoll -> epoll_wait()
                // 可以设置超时时间
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey key : selectionKeys) {
                    // 处理被触发的事件
                    handles(key);
                }
                selectionKeys.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(selector);
        }
    }

    private void handles(SelectionKey key) throws IOException {
        // ACCEPT事件 - 和客户端建立了连接
        if (key.isAcceptable()) {
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
            System.out.println("客户端[" + client.socket().getPort() + "]已经连接到服务器");
        }
        // READ事件 - 客户端发送了消息
        else if (key.isReadable()) {
            SocketChannel client = (SocketChannel) key.channel();
            String fwdMsg = receive(client);
            if (fwdMsg.isEmpty()) {
                // 客户端异常
                key.cancel();
                selector.wakeup();
            } else {
                System.out.println("客户端[" + client.socket().getPort() + "]: " + fwdMsg);
                forwardMessage(client, fwdMsg);

                // 检查用户是否退出
                if (readyToQuit(fwdMsg)) {
                    key.cancel();
                    selector.wakeup();
                    System.out.println("客户端[" + client.socket().getPort() + "]已经断开连接");
                }
            }
        }
    }

    private void forwardMessage(SocketChannel client, String fwdMsg) throws IOException {
        for (SelectionKey key : selector.keys()) {
            Channel connectedClient = key.channel();
            if (connectedClient instanceof ServerSocketChannel) {
                continue;
            }

            if (key.isValid() && !client.equals(connectedClient)) {
                writeBuffer.clear();
                writeBuffer.put(charset.encode("用户[" + client.socket().getPort() +"]: " + fwdMsg));
                writeBuffer.flip();
                while (writeBuffer.hasRemaining()) {
                    ((SocketChannel) connectedClient).write(writeBuffer);
                }
            }
        }
    }

    private String receive(SocketChannel client) throws IOException {
        readBuffer.clear();
        StringBuilder sb = new StringBuilder();
        String str = null;
        while (client.read(readBuffer) > 0) {
            readBuffer.flip();
            str = String.valueOf(charset.decode(readBuffer));
            sb.append(str);
            readBuffer.clear();
        }
        return sb.toString();
    }

    /**
     * 检查用户是否准备退出
     *
     * @param msg
     * @return
     */
    public boolean readyToQuit(String msg) {
        if (msg.equalsIgnoreCase(QUIT)) {
            return true;
        }
        return false;
    }

    /**
     * 关闭连接方法
     */
    public void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer(7777);
        chatServer.start();
    }
}
