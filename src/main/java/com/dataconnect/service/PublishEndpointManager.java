package com.dataconnect.service;

import com.dataconnect.entity.PublishConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

/**
 * 发布端点管理器
 * 在独立端口上启动轻量HTTP服务器，暴露自定义API路径
 */
@Component
public class PublishEndpointManager {

    private static final Logger log = LoggerFactory.getLogger(PublishEndpointManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Lazy
    @Autowired
    private PublishService publishService;

    // publishId -> ServerSocket + 线程
    private final Map<Long, EndpointRuntime> endpoints = new ConcurrentHashMap<>();

    /**
     * 启动端点
     */
    public void startEndpoint(PublishConfig config) {
        Long publishId = config.getId();
        if (endpoints.containsKey(publishId)) {
            log.warn("端点已在运行, publishId={}", publishId);
            return;
        }

        int port = config.getPort();
        String apiPath = config.getApiPath() != null ? config.getApiPath() : "/api/data";

        try {
            EndpointRuntime runtime = new EndpointRuntime(publishId, port, apiPath);
            runtime.start();
            endpoints.put(publishId, runtime);
            log.info("发布端点已启动, publishId={}, port={}, path={}", publishId, port, apiPath);
        } catch (Exception e) {
            log.error("启动发布端点失败, publishId={}, port={}", publishId, port, e);
            throw new RuntimeException("启动端点失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止端点
     */
    public void stopEndpoint(Long publishId) {
        EndpointRuntime runtime = endpoints.remove(publishId);
        if (runtime != null) {
            runtime.stop();
            log.info("发布端点已停止, publishId={}", publishId);
        }
    }

    /**
     * 检查端点是否运行中
     */
    public boolean isEndpointRunning(Long publishId) {
        EndpointRuntime runtime = endpoints.get(publishId);
        return runtime != null && runtime.isRunning();
    }

    /**
     * 停止所有端点
     */
    @PreDestroy
    public void stopAll() {
        log.info("停止所有发布端点, count={}", endpoints.size());
        for (Long publishId : new ArrayList<>(endpoints.keySet())) {
            stopEndpoint(publishId);
        }
    }

    /**
     * 端点运行时 - 轻量HTTP服务器
     */
    private class EndpointRuntime {
        private final Long publishId;
        private final int port;
        private final String apiPath;
        private volatile boolean running = false;
        private ServerSocket serverSocket;
        private ExecutorService executor;

        EndpointRuntime(Long publishId, int port, String apiPath) {
            this.publishId = publishId;
            this.port = port;
            this.apiPath = apiPath;
        }

        void start() {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                executor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "publish-endpoint-" + publishId + "-" + port);
                    t.setDaemon(true);
                    return t;
                });

                // 监听线程
                Thread listener = new Thread(() -> {
                    while (running && !serverSocket.isClosed()) {
                        try {
                            Socket socket = serverSocket.accept();
                            executor.submit(() -> handleRequest(socket));
                        } catch (IOException e) {
                            if (running) log.debug("端点接受连接异常: {}", e.getMessage());
                        }
                    }
                }, "publish-listener-" + publishId);
                listener.setDaemon(true);
                listener.start();

            } catch (IOException e) {
                running = false;
                throw new RuntimeException("端口 " + port + " 绑定失败: " + e.getMessage(), e);
            }
        }

        void stop() {
            running = false;
            try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); } catch (IOException e) { /* ignore */ }
            if (executor != null) executor.shutdownNow();
        }

        boolean isRunning() {
            return running;
        }

        private void handleRequest(Socket socket) {
            try {
                socket.setSoTimeout(30000);
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                // 读取HTTP请求
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String requestLine = reader.readLine();
                if (requestLine == null) return;

                String[] parts = requestLine.split(" ");
                if (parts.length < 2) return;

                String method = parts[0];
                String path = parts[1];

                // 读取headers，找Content-Length和Body
                int contentLength = 0;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                // 读取请求体
                String body = "";
                if (contentLength > 0) {
                    char[] buf = new char[contentLength];
                    int read = 0;
                    while (read < contentLength) {
                        int n = reader.read(buf, read, contentLength - read);
                        if (n == -1) break;
                        read += n;
                    }
                    body = new String(buf, 0, read);
                }

                // 路由匹配
                if (!"POST".equalsIgnoreCase(method) || !path.equals(apiPath)) {
                    sendResponse(os, 404, "{\"success\":false,\"error\":\"Not Found\"}");
                    return;
                }

                // 解析参数
                Map<String, Object> params = new HashMap<>();
                if (!body.isEmpty()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
                        params = parsed;
                    } catch (Exception e) {
                        sendResponse(os, 400, "{\"success\":false,\"error\":\"Invalid JSON\"}");
                        return;
                    }
                }

                // 执行
                try {
                    Map<String, Object> result = publishService.execute(publishId, params);
                    String json = objectMapper.writeValueAsString(result);
                    sendResponse(os, 200, json);
                } catch (Exception e) {
                    log.error("端点执行失败, publishId={}", publishId, e);
                    Map<String, Object> errMap = new HashMap<>();
                    errMap.put("success", false);
                    errMap.put("error", e.getMessage());
                    String errJson = objectMapper.writeValueAsString(errMap);
                    sendResponse(os, 500, errJson);
                }

            } catch (Exception e) {
                log.debug("处理请求异常: {}", e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException e) { /* ignore */ }
            }
        }

        private void sendResponse(OutputStream os, int status, String body) throws IOException {
            String statusText;
            switch (status) {
                case 200: statusText = "OK"; break;
                case 400: statusText = "Bad Request"; break;
                case 404: statusText = "Not Found"; break;
                case 500: statusText = "Internal Server Error"; break;
                default: statusText = "Unknown"; break;
            }
            byte[] bodyBytes = body.getBytes("UTF-8");
            String header = "HTTP/1.1 " + status + " " + statusText + "\r\n" +
                    "Content-Type: application/json;charset=UTF-8\r\n" +
                    "Content-Length: " + bodyBytes.length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
            os.write(header.getBytes("UTF-8"));
            os.write(bodyBytes);
            os.flush();
        }
    }
}
