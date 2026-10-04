package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 关闭通知与回执：每个受影响班组一行，送达后班组必须回执，只追加。
 */
@TableName("t_tide_window_notice")
public class TideWindowNotice extends BaseEntity {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACKED = "ACKED";

    private Long closureId;
    private Long scheduleId;
    private Long crewId;
    private String noticeStatus;
    private LocalDateTime sentTime;
    private String ackBy;
    private LocalDateTime ackTime;
    private String ackRemark;

    public Long getClosureId() { return closureId; }
    public void setClosureId(Long closureId) { this.closureId = closureId; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getCrewId() { return crewId; }
    public void setCrewId(Long crewId) { this.crewId = crewId; }
    public String getNoticeStatus() { return noticeStatus; }
    public void setNoticeStatus(String noticeStatus) { this.noticeStatus = noticeStatus; }
    public LocalDateTime getSentTime() { return sentTime; }
    public void setSentTime(LocalDateTime sentTime) { this.sentTime = sentTime; }
    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public LocalDateTime getAckTime() { return ackTime; }
    public void setAckTime(LocalDateTime ackTime) { this.ackTime = ackTime; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
}
