package com.evops.aqua.tide.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 换水候选展开参数（按海区唯一，存在即更新）：
 * 候选窗口 = [目标潮点时刻 + beforeMinutes, 目标潮点时刻 + afterMinutes)。
 */
public class TideWindowRuleRequest {

    @NotBlank(message = "海区不能为空")
    private String seaArea;
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;
    private String targetPointType = "HIGH";

    @NotNull(message = "潮前偏移分钟不能为空")
    @Min(value = 0, message = "潮前偏移不能为负")
    private Integer beforeMinutes;

    @NotNull(message = "潮后偏移分钟不能为空")
    @Min(value = 1, message = "潮后偏移必须大于0")
    private Integer afterMinutes;

    private String remark;
    private String operator = "system";

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
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
