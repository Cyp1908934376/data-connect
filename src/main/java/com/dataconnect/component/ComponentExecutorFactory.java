package com.dataconnect.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组件执行器工厂
 * 管理所有组件执行器的注册和获取
 */
@Component
public class ComponentExecutorFactory {

    private static final Logger log = LoggerFactory.getLogger(ComponentExecutorFactory.class);

    // 执行器注册表
    private final Map<String, ComponentExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 自动注入所有ComponentExecutor实现
     */
    @org.springframework.beans.factory.annotation.Autowired
    private List<ComponentExecutor> executorList;

    @PostConstruct
    public void init() {
        // 注册所有执行器
        for (ComponentExecutor executor : executorList) {
            register(executor);
        }
        log.info("组件执行器初始化完成, count={}", executors.size());
    }

    /**
     * 注册执行器
     */
    public void register(ComponentExecutor executor) {
        String type = executor.getType();
        if (executors.containsKey(type)) {
            log.warn("覆盖已注册的组件执行器: {}", type);
        }
        executors.put(type, executor);
        log.debug("注册组件执行器: {}", type);
    }

    /**
     * 获取执行器
     */
    public ComponentExecutor getExecutor(String type) {
        ComponentExecutor executor = executors.get(type);
        if (executor == null) {
            throw new RuntimeException("未找到组件执行器: " + type);
        }
        return executor;
    }

    /**
     * 检查是否存在执行器
     */
    public boolean hasExecutor(String type) {
        return executors.containsKey(type);
    }

    /**
     * 获取所有已注册的执行器类型
     */
    public String[] getRegisteredTypes() {
        return executors.keySet().toArray(new String[0]);
    }
}
