package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 排班修改审批：发布后任何修改必须经调度审批。
 * approveStatus: PENDING/APPROVED/REJECTED。自动顺延不经过本表。
 */
@TableName("t_tide_schedule_change")
public class TideScheduleChange extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private Long scheduleId;
    private String requestId;
    private LocalDateTime requestedStart;
    private LocalDateTime requestedEnd;
    private String changeReason;
    private String requestedBy;
    private LocalDateTime requestTime;
    private String approveStatus;
    private String approver;
    private String approveRemark;
    private LocalDateTime approveTime;

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getRequestedStart() { return requestedStart; }
    public void setRequestedStart(LocalDateTime requestedStart) { this.requestedStart = requestedStart; }
    public LocalDateTime getRequestedEnd() { return requestedEnd; }
    public void setRequestedEnd(LocalDateTime requestedEnd) { this.requestedEnd = requestedEnd; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }
    public String getApproveStatus() { return approveStatus; }
    public void setApproveStatus(String approveStatus) { this.approveStatus = approveStatus; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }
    public LocalDateTime getApproveTime() { return approveTime; }
    public void setApproveTime(LocalDateTime approveTime) { this.approveTime = approveTime; }
}
