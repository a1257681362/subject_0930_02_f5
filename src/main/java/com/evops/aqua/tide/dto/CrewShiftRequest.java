package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 人员班次登记：班组在 [shiftStart, shiftEnd) 可出勤。
 */
public class CrewShiftRequest {

    @NotNull(message = "班组不能为空")
    private Long crewId;
    @NotNull(message = "班次开始时间不能为空")
    private LocalDateTime shiftStart;
    @NotNull(message = "班次结束时间不能为空")
    private LocalDateTime shiftEnd;
    private String shiftType = "NORMAL";
    private String remark;

    public Long getCrewId() { return crewId; }
    public void setCrewId(Long crewId) { this.crewId = crewId; }
    public LocalDateTime getShiftStart() { return shiftStart; }
    public void setShiftStart(LocalDateTime shiftStart) { this.shiftStart = shiftStart; }
    public LocalDateTime getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(LocalDateTime shiftEnd) { this.shiftEnd = shiftEnd; }
    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
