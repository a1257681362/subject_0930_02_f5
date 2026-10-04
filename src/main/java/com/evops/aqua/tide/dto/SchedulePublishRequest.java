package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 排班发布：仅草稿可发布；发布即冻结原计划（original_start/end），
 * 此后修改必须经调度审批。
 */
public class SchedulePublishRequest {

    @NotBlank(message = "发布人不能为空")
    private String publishedBy;
    private String remark;

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
