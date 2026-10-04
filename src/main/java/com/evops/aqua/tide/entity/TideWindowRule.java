package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 换水候选展开参数（按海区一行）：
 * 候选窗口 = [高潮时刻 + beforeMinutes, 高潮时刻 + afterMinutes)，半开。
 */
@TableName("t_tide_window_rule")
public class TideWindowRule extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private String seaArea;
    private String ruleName;
    private String targetPointType;
    private Integer beforeMinutes;
    private Integer afterMinutes;
    private String status;
    private String remark;

    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getTargetPointType() { return targetPointType; }
    public void setTargetPointType(String targetPointType) { this.targetPointType = targetPointType; }
    public Integer getBeforeMinutes() { return beforeMinutes; }
    public void setBeforeMinutes(Integer beforeMinutes) { this.beforeMinutes = beforeMinutes; }
    public Integer getAfterMinutes() { return afterMinutes; }
    public void setAfterMinutes(Integer afterMinutes) { this.afterMinutes = afterMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
