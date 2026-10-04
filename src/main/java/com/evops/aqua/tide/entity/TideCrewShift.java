package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 人员班次：班组在某时段可出勤，半开 [shiftStart, shiftEnd)。
 */
@TableName("t_tide_crew_shift")
public class TideCrewShift extends BaseEntity {

    public static final String TYPE_NORMAL = "NORMAL";

    private Long crewId;
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;
    private String shiftType;
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
