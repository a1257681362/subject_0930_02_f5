package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 发布后排班修改申请：不直接改动，生成待审批单（requestId 幂等）。
 * 重复提交同一 requestId 返回首次申请。
 */
public class ScheduleChangeRequest {

    @NotNull(message = "目标开始时间不能为空")
    private LocalDateTime requestedStart;
    @NotNull(message = "目标结束时间不能为空")
    private LocalDateTime requestedEnd;
    @NotBlank(message = "修改原因不能为空")
    private String changeReason;
    @NotBlank(message = "申请人不能为空")
    private String requestedBy;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public LocalDateTime getRequestedStart() { return requestedStart; }
    public void setRequestedStart(LocalDateTime requestedStart) { this.requestedStart = requestedStart; }
    public LocalDateTime getRequestedEnd() { return requestedEnd; }
    public void setRequestedEnd(LocalDateTime requestedEnd) { this.requestedEnd = requestedEnd; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
