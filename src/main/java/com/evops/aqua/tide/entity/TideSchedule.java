package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 换水排班单：一个可执行窗口一张单，绑定池、设备、班组。
 * originalStart/originalEnd 记录发布时的原计划，顺延与审批改期都不得改变原计划值。
 */
@TableName("t_tide_schedule")
public class TideSchedule extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ADJUST_PENDING = "ADJUST_PENDING";
    public static final String STATUS_RESCHEDULED = "RESCHEDULED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String scheduleNo;
    private Long windowId;
    private Long pondId;
    private Long equipmentId;
    private Long crewId;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime originalStart;
    private LocalDateTime originalEnd;
    private String scheduleStatus;
    private String publishedBy;
    private LocalDateTime publishedTime;
    private String changeReason;
    private String remark;

    public String getScheduleNo() { return scheduleNo; }
    public void setScheduleNo(String scheduleNo) { this.scheduleNo = scheduleNo; }
    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public Long getCrewId() { return crewId; }
    public void setCrewId(Long crewId) { this.crewId = crewId; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime scheduledStart) { this.scheduledStart = scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime scheduledEnd) { this.scheduledEnd = scheduledEnd; }
    public LocalDateTime getOriginalStart() { return originalStart; }
    public void setOriginalStart(LocalDateTime originalStart) { this.originalStart = originalStart; }
    public LocalDateTime getOriginalEnd() { return originalEnd; }
    public void setOriginalEnd(LocalDateTime originalEnd) { this.originalEnd = originalEnd; }
    public String getScheduleStatus() { return scheduleStatus; }
    public void setScheduleStatus(String scheduleStatus) { this.scheduleStatus = scheduleStatus; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
