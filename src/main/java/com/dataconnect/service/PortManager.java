package com.dataconnect.service;

import com.dataconnect.repository.PublishConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

/**
 * 端口管理服务
 * 负责端口分配、占用检测、释放
 */
@Service
public class PortManager {

    private static final Logger log = LoggerFactory.getLogger(PortManager.class);
    
    private static final int PORT_RANGE_MIN = 10000;
    private static final int PORT_RANGE_MAX = 65535;
    private static final int MAX_RETRY = 100;
    
    @Autowired
    private PublishConfigRepository publishConfigRepository;
    
    private final Random random = new Random();
    
    /**
     * 检查端口是否被占用（系统级别）
     * @param port 端口号
     * @return true=占用, false=空闲
     */
    public boolean isPortInUse(int port) {
        // 先检查系统级占用
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return false;  // 能绑定说明空闲
        } catch (IOException e) {
            return true;  // 绑定失败说明占用
        }
    }
    
    /**
     * 检查端口是否被发布配置占用（业务级别）
     * @param port 端口号
     * @return true=已被发布配置占用
     */
    public boolean isPortAllocated(int port) {
        return publishConfigRepository.existsByPort(port);
    }
    
    /**
     * 分配一个可用端口
     * @return 可用端口号
     * @throws RuntimeException 如果无法找到可用端口
     */
    public int allocatePort() {
        for (int i = 0; i < MAX_RETRY; i++) {
            int port = PORT_RANGE_MIN + random.nextInt(PORT_RANGE_MAX - PORT_RANGE_MIN + 1);
            if (!isPortInUse(port) && !isPortAllocated(port)) {
                log.info("分配端口: {}", port);
                return port;
            }
        }
        throw new RuntimeException("无法找到可用端口，已尝试 " + MAX_RETRY + " 次");
    }
    
    /**
     * 验证端口是否可用
     * @param port 端口号
     * @param excludePublishId 排除的发布ID（编辑时排除自己）
     * @return true=可用
     */
    public boolean isPortAvailable(int port, Long excludePublishId) {
        // 检查系统级占用
        if (isPortInUse(port)) {
            return false;
        }
        
        // 检查业务级占用（排除自己）
        com.dataconnect.entity.PublishConfig existing = publishConfigRepository.findByPort(port);
        if (existing != null && !existing.getId().equals(excludePublishId)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取端口状态信息
     * @param port 端口号
     * @return 状态描述
     */
    public String getPortStatus(int port) {
        if (isPortInUse(port)) {
            return "系统占用";
        }
        if (isPortAllocated(port)) {
            return "已分配";
        }
        return "空闲";
    }
}
