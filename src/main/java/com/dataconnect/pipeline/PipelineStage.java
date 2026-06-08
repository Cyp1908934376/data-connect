package com.dataconnect.pipeline;

import java.util.List;

/**
 * 管道阶段：一个处理阶段包含一个标准位置和一组有序的处理步骤。
 */
public class PipelineStage {

    /** 标准插入位置：AFTER_READ / BEFORE_WRITE / AFTER_WRITE */
    private String position;

    /** 阶段显示名称 */
    private String name;

    /** 该阶段内有序的处理步骤列表 */
    private List<PipelineStep> steps;

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<PipelineStep> getSteps() { return steps; }
    public void setSteps(List<PipelineStep> steps) { this.steps = steps; }
}
