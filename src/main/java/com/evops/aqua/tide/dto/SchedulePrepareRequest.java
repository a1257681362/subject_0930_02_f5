package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 换水排班准备（草稿）：把一个可执行窗口安排给设备+班组。
 * 同一设备同一时间只能服务一口池——与已存在的有效排班（草稿/已发布/待审批/已顺延）
 * 半开区间重叠即拒绝并一次性说明冲突；窗口端点相接（一端相等）不算冲突。
 * 可在窗口内指定实际作业时段（scheduledStart/scheduledEnd），缺省取整个可执行窗口。
 */
public class SchedulePrepareRequest {

    @NotNull(message = "换水窗口不能为空")
    private Long windowId;
    @NotNull(message = "换水设备不能为空")
    private Long equipmentId;
    @NotNull(message = "作业班组不能为空")
    private Long crewId;

    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;

    @NotNull(message = "排班操作人不能为空")
    private String operator;

    private String remark;

    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public Long getCrewId() { return crewId; }
    public void setCrewId(Long crewId) { this.crewId = crewId; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime scheduledStart) { this.scheduledStart = scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime scheduledEnd) { this.scheduledEnd = scheduledEnd; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
