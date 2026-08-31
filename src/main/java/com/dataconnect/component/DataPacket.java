package com.dataconnect.component;

import java.util.*;

/**
 * 组件间统一数据传输格式
 * 所有组件的输入输出都使用DataPacket
 */
public class DataPacket {

    // 数据内容
    private List<Map<String, Object>> rows;
    private Map<String, Object> variables;

    // 元数据
    private DataType dataType;
    private String sourceNodeId;
    private long timestamp;
    private long rowCount;

    // 状态
    private boolean success;
    private String errorCode;
    private String errorMessage;

    // 流程控制信号
    private ControlSignal signal;

    // 数据类型枚举
    public enum DataType {
        EMPTY,      // 空数据
        SINGLE,     // 单行数据
        LIST,       // 多行数据列表
        SCALAR,     // 标量值（变量）
        ERROR       // 错误数据
    }

    // 控制信号枚举
    public enum ControlSignal {
        NONE,       // 正常
        BREAK,      // 跳出循环
        CONTINUE,   // 继续下一次循环
        STOP,       // 终止整个流程
        SKIP        // 跳过当前节点
    }

    // 构造函数
    public DataPacket() {
        this.rows = new ArrayList<>();
        this.variables = new LinkedHashMap<>();
        this.dataType = DataType.EMPTY;
        this.success = true;
        this.signal = ControlSignal.NONE;
        this.timestamp = System.currentTimeMillis();
    }

    public DataPacket(DataType dataType, List<Map<String, Object>> rows) {
        this();
        this.dataType = dataType;
        this.rows = rows != null ? rows : new ArrayList<>();
        this.rowCount = this.rows.size();
    }

    // 工厂方法

    /**
     * 创建空数据包
     */
    public static DataPacket empty() {
        return new DataPacket(DataType.EMPTY, new ArrayList<>());
    }

    /**
     * 创建单行数据包
     */
    public static DataPacket of(Map<String, Object> row) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);
        return new DataPacket(DataType.SINGLE, rows);
    }

    /**
     * 创建多行数据包
     */
    public static DataPacket ofList(List<Map<String, Object>> rows) {
        return new DataPacket(rows.isEmpty() ? DataType.EMPTY : DataType.LIST, rows);
    }

    /**
     * 创建标量数据包（变量）
     */
    public static DataPacket scalar(String name, Object value) {
        DataPacket packet = new DataPacket(DataType.SCALAR, new ArrayList<>());
        packet.getVariables().put(name, value);
        return packet;
    }

    /**
     * 创建错误数据包
     */
    public static DataPacket error(String code, String message) {
        DataPacket packet = new DataPacket(DataType.ERROR, new ArrayList<>());
        packet.setSuccess(false);
        packet.setErrorCode(code);
        packet.setErrorMessage(message);
        return packet;
    }

    // 实用方法

    /**
     * 获取第一行
     */
    public Map<String, Object> getFirstRow() {
        return rows != null && !rows.isEmpty() ? rows.get(0) : null;
    }

    /**
     * 获取指定字段值（从第一行）
     */
    public Object getValue(String fieldName) {
        Map<String, Object> firstRow = getFirstRow();
        return firstRow != null ? firstRow.get(fieldName) : null;
    }

    /**
     * 获取指定字段值（指定行）
     */
    public Object getValue(int rowIndex, String fieldName) {
        if (rows != null && rowIndex >= 0 && rowIndex < rows.size()) {
            return rows.get(rowIndex).get(fieldName);
        }
        return null;
    }

    /**
     * 获取变量值
     */
    public Object getVariable(String name) {
        return variables != null ? variables.get(name) : null;
    }

    /**
     * 设置变量
     */
    public void setVariable(String name, Object value) {
        if (variables == null) {
            variables = new LinkedHashMap<>();
        }
        variables.put(name, value);
    }

    /**
     * 合并另一个DataPacket
     */
    public DataPacket merge(DataPacket other) {
        if (other == null) return this;
        if (this.rows == null) this.rows = new ArrayList<>();
        if (other.getRows() != null) {
            this.rows.addAll(other.getRows());
        }
        if (other.getVariables() != null) {
            if (this.variables == null) this.variables = new LinkedHashMap<>();
            this.variables.putAll(other.getVariables());
        }
        this.rowCount = this.rows.size();
        return this;
    }

    /**
     * 转换为单行（取第一行）
     */
    public DataPacket toSingle() {
        if (rows != null && !rows.isEmpty()) {
            return DataPacket.of(rows.get(0));
        }
        return DataPacket.empty();
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return rows == null || rows.isEmpty();
    }

    /**
     * 获取行数
     */
    public int size() {
        return rows != null ? rows.size() : 0;
    }

    /**
     * 创建副本
     */
    public DataPacket copy() {
        DataPacket copy = new DataPacket();
        copy.dataType = this.dataType;
        copy.sourceNodeId = this.sourceNodeId;
        copy.success = this.success;
        copy.errorCode = this.errorCode;
        copy.errorMessage = this.errorMessage;
        copy.signal = this.signal;
        copy.timestamp = this.timestamp;
        copy.rowCount = this.rowCount;

        // 深拷贝rows
        if (this.rows != null) {
            copy.rows = new ArrayList<>();
            for (Map<String, Object> row : this.rows) {
                copy.rows.add(new LinkedHashMap<>(row));
            }
        }

        // 深拷贝variables
        if (this.variables != null) {
            copy.variables = new LinkedHashMap<>(this.variables);
        }

        return copy;
    }

    // Getters and Setters
    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; this.rowCount = rows != null ? rows.size() : 0; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
    public DataType getDataType() { return dataType; }
    public void setDataType(DataType dataType) { this.dataType = dataType; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getRowCount() { return rowCount; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public ControlSignal getSignal() { return signal; }
    public void setSignal(ControlSignal signal) { this.signal = signal; }
}
