package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 自动顺延：已发布排班因故顺延到新时段，直接生效、不走审批，
 * 但绝不改变 original_start/original_end（原计划永久保留），仅登记顺延证据。
 * 顺延后仍校验同一设备半开区间互斥。
 */
public class SchedulePostponeRequest {

    @NotNull(message = "顺延后开始时间不能为空")
    private LocalDateTime newStart;
    @NotNull(message = "顺延后结束时间不能为空")
    private LocalDateTime newEnd;
    @NotBlank(message = "顺延原因不能为空")
    private String reason;
    @NotBlank(message = "顺延操作人不能为空")
    private String operator;

    public LocalDateTime getNewStart() { return newStart; }
    public void setNewStart(LocalDateTime newStart) { this.newStart = newStart; }
    public LocalDateTime getNewEnd() { return newEnd; }
    public void setNewEnd(LocalDateTime newEnd) { this.newEnd = newEnd; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
