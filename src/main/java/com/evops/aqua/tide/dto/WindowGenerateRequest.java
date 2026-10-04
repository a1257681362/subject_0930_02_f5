package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 换水窗口生成/重算请求：按池+日期区间，从该池所属海区潮汐表生成候选，
 * 叠加禁作天气与人员班次后形成可执行窗口。
 * 已排班/已关闭窗口冻结不动；未排班窗口按最新潮表重建（同一半开口径与归属日）。
 */
public class WindowGenerateRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 可执行窗口最小时长（分钟）：裁剪后短于该值的候选段不形成窗口；为空默认 30 分钟。 */
    private Integer minWindowMinutes;

    /** 重算操作人（留证）。 */
    private String operator = "system";

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getMinWindowMinutes() { return minWindowMinutes; }
    public void setMinWindowMinutes(Integer minWindowMinutes) { this.minWindowMinutes = minWindowMinutes; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
