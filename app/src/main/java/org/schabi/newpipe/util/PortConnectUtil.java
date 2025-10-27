package org.schabi.newpipe.util;

import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;

/**
 * 检查指定 IP 地址上的 TCP 端口是否处于 LISTENING 状态。
 * 注意：网络操作必须在非主线程中执行。
 */
public class PortConnectUtil {

    private static final String TAG = "PortConnectUtil";

    /**
     * 检查目标主机上的 TCP 端口是否可达且处于监听状态。
     * * @param host 目标主机名或IP地址 (对于本地端口，使用 "127.0.0.1" 或 "localhost")
     * @param port 目标端口号
     * @param timeoutMillis 连接超时时间 (毫秒)
     * @return 如果端口可连接 (LISTENING)，返回 true；否则返回 false。
     */
    public static boolean isPortListening(String host, int port, int timeoutMillis) {
        Socket socket = new Socket();
        try {
            // 尝试连接到目标 IP 和端口
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);

            // 如果代码执行到这里，说明连接成功，目标端口处于 LISTENING 状态。
            // Log.d(TAG, "Port " + port + " on " + host + " is LISTENING.");
            return true;
        } catch (IOException e) {
            // 连接失败的原因通常是：
            // 1. Connection refused (目标端口无人监听)
            // 2. Timeout (连接超时，可能被防火墙阻挡)
            // 3. UnknownHostException (主机名无法解析)
            // Log.w(TAG, "Port " + port + " on " + host + " is not accessible or not LISTENING. Error: " + e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    // 必须关闭 Socket 释放资源
                    socket.close();
                } catch (IOException e) {
                    // 忽略关闭时的错误
                }
            }
        }
    }
}