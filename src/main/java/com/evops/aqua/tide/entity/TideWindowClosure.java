package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 窗口临时关闭：关闭原因必填，关闭后向受影响班组发送通知。
 */
@TableName("t_tide_window_closure")
public class TideWindowClosure extends BaseEntity {

    private Long windowId;
    private String requestId;
    private String closeReason;
    private String closedBy;
    private LocalDateTime closedTime;

    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedTime() { return closedTime; }
    public void setClosedTime(LocalDateTime closedTime) { this.closedTime = closedTime; }
}
