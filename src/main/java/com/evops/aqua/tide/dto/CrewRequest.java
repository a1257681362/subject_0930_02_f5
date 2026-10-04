package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 班组建档。
 */
public class CrewRequest {

    @NotBlank(message = "班组编号不能为空")
    private String crewCode;
    @NotBlank(message = "班组名称不能为空")
    private String crewName;
    private String leader;
    private String remark;

    public String getCrewCode() { return crewCode; }
    public void setCrewCode(String crewCode) { this.crewCode = crewCode; }
    public String getCrewName() { return crewName; }
    public void setCrewName(String crewName) { this.crewName = crewName; }
    public String getLeader() { return leader; }
    public void setLeader(String leader) { this.leader = leader; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
