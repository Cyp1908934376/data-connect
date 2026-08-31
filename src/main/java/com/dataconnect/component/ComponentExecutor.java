package com.dataconnect.component;

import java.util.Map;

/**
 * 组件执行器接口
 * 所有组件都需要实现此接口
 */
public interface ComponentExecutor {

    /**
     * 获取执行器类型标识
     * 与 component_definition 表中的 execution_type 对应
     */
    String getType();

    /**
     * 执行组件
     * 
     * @param input 输入数据包
     * @param config 组件配置
     * @param context 执行上下文
     * @return 输出数据包
     */
    DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context);

    /**
     * 验证配置是否有效
     * 
     * @param config 组件配置
     * @return 验证结果，null表示通过，否则返回错误信息
     */
    default String validateConfig(Map<String, Object> config) {
        return null;
    }

    /**
     * 获取组件的输入端口定义
     * 
     * @return 端口名称列表
     */
    default String[] getInputPorts() {
        return new String[]{"input"};
    }

    /**
     * 获取组件的输出端口定义
     * 
     * @return 端口名称列表
     */
    default String[] getOutputPorts() {
        return new String[]{"output"};
    }
}
