package com.dataconnect.pipeline;

import java.util.Map;

/**
 * 管道步骤：一个具体的处理步骤，可以是模板执行或映射转换。
 */
public class PipelineStep {

    /** 步骤类型：TEMPLATE(模板管理中的Groovy模板) / MAPPING(数据对接模板) */
    private String type;

    /** 当 type=TEMPLATE 时，关联的模板ID */
    private Long templateId;

    /** 当 type=MAPPING 时，关联的数据对接模板ID */
    private Long mappingTemplateId;

    /** 该步骤的额外参数（会传递给模板的params绑定变量） */
    private Map<String, Object> params;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getMappingTemplateId() { return mappingTemplateId; }
    public void setMappingTemplateId(Long mappingTemplateId) { this.mappingTemplateId = mappingTemplateId; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
