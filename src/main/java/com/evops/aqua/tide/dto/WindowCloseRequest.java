package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 临时关闭窗口：关闭原因必填。关闭后向该窗口全部受影响班组发送通知，
 * 已发布排班同时置为已取消，通知须班组回执。requestId 幂等。
 */
public class WindowCloseRequest {

    @NotBlank(message = "关闭原因不能为空")
    private String closeReason;
    @NotBlank(message = "关闭操作人不能为空")
    private String closedBy;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
