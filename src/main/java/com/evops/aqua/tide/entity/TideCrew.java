package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 换水作业班组：通知与回执的接收对象。
 */
@TableName("t_tide_crew")
public class TideCrew extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String crewCode;
    private String crewName;
    private String leader;
    private String status;
    private String remark;

    public String getCrewCode() { return crewCode; }
    public void setCrewCode(String crewCode) { this.crewCode = crewCode; }
    public String getCrewName() { return crewName; }
    public void setCrewName(String crewName) { this.crewName = crewName; }
    public String getLeader() { return leader; }
    public void setLeader(String leader) { this.leader = leader; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
