package com.dataconnect.component;

import java.util.*;

/**
 * 组件执行上下文
 * 在整个流程执行过程中共享的上下文信息
 */
public class ExecutionContext {

    // 执行ID
    private String executionId;

    // 流程ID
    private Long flowConfigId;

    // 当前节点ID
    private String currentNodeId;

    // 节点执行结果缓存
    private Map<String, DataPacket> nodeResults;

    // 全局变量
    private Map<String, Object> globalVariables;

    // 执行日志
    private List<ExecutionLog> logs;

    private LogListener logListener;

    public interface LogListener {
        void onLog(ExecutionLog item);
    }

    public void setLogListener(LogListener logListener) {
        this.logListener = logListener;
    }

    public LogListener getLogListener() {
        return logListener;
    }

    // 执行状态
    private volatile boolean cancelled = false;
    private volatile boolean paused = false;

    // 日志级别
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    // 执行日志
    public static class ExecutionLog {
        private long timestamp;
        private String nodeId;
        private LogLevel level;
        private String message;

        public ExecutionLog(String nodeId, LogLevel level, String message) {
            this.timestamp = System.currentTimeMillis();
            this.nodeId = nodeId;
            this.level = level;
            this.message = message;
        }

        public long getTimestamp() { return timestamp; }
        public String getNodeId() { return nodeId; }
        public LogLevel getLevel() { return level; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return String.format("[%s] [%s] %s: %s", 
                new Date(timestamp), level, nodeId, message);
        }
    }

    public ExecutionContext() {
        this.nodeResults = new LinkedHashMap<>();
        this.globalVariables = new LinkedHashMap<>();
        this.logs = new ArrayList<>();
        this.executionId = UUID.randomUUID().toString();
    }

    public ExecutionContext(Long flowConfigId) {
        this();
        this.flowConfigId = flowConfigId;
    }

    /**
     * 记录日志
     */
    public void log(LogLevel level, String message) {
        ExecutionLog item = new ExecutionLog(currentNodeId, level, message);
        logs.add(item);
        if (logListener != null) {
            logListener.onLog(item);
        }
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    /**
     * 设置节点执行结果
     */
    public void setNodeResult(String nodeId, DataPacket result) {
        nodeResults.put(nodeId, result);
    }

    /**
     * 获取节点执行结果
     */
    public DataPacket getNodeResult(String nodeId) {
        return nodeResults.get(nodeId);
    }

    /**
     * 设置全局变量
     */
    public void setGlobalVariable(String name, Object value) {
        globalVariables.put(name, value);
    }

    /**
     * 获取全局变量
     */
    public Object getGlobalVariable(String name) {
        return globalVariables.get(name);
    }

    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 取消执行
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * 检查是否已暂停
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * 暂停执行
     */
    public void pause() {
        this.paused = true;
    }

    /**
     * 恢复执行
     */
    public void resume() {
        this.paused = false;
    }

    /**
     * 等待暂停恢复
     */
    public void waitIfPaused() {
        while (paused && !cancelled) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Getters and Setters
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public Long getFlowConfigId() { return flowConfigId; }
    public void setFlowConfigId(Long flowConfigId) { this.flowConfigId = flowConfigId; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
    public Map<String, DataPacket> getNodeResults() { return nodeResults; }
    public Map<String, Object> getGlobalVariables() { return globalVariables; }
    public List<ExecutionLog> getLogs() { return logs; }
}
