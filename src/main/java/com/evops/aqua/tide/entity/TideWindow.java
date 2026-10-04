package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 换水窗口：潮汐候选 → 减天气禁作 → 交班次 后形成的可执行窗口。
 * 统一半开口径 [windowStart, windowEnd)；planDate 恒等于 windowStart 所在自然日。
 * 重算（潮表调整）对已排班/已关闭窗口冻结不动（frozenFlag=TRUE），只重建未排班窗口。
 */
@TableName("t_tide_window")
public class TideWindow extends BaseEntity {

    public static final String STATUS_CANDIDATE = "CANDIDATE";
    public static final String STATUS_EXECUTABLE = "EXECUTABLE";
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_CLOSED = "CLOSED";

    private String windowNo;
    private Long pondId;
    private String seaArea;
    private LocalDate planDate;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String windowStatus;
    private LocalDateTime candidateStart;
    private LocalDateTime candidateEnd;
    private Long sourceTidePointId;
    private Long crewId;
    private Boolean frozenFlag;
    private String excludeReason;
    private String remark;

    public String getWindowNo() { return windowNo; }
    public void setWindowNo(String windowNo) { this.windowNo = windowNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
    public String getWindowStatus() { return windowStatus; }
    public void setWindowStatus(String windowStatus) { this.windowStatus = windowStatus; }
    public LocalDateTime getCandidateStart() { return candidateStart; }
    public void setCandidateStart(LocalDateTime candidateStart) { this.candidateStart = candidateStart; }
    public LocalDateTime getCandidateEnd() { return candidateEnd; }
    public void setCandidateEnd(LocalDateTime candidateEnd) { this.candidateEnd = candidateEnd; }
    public Long getSourceTidePointId() { return sourceTidePointId; }
    public void setSourceTidePointId(Long sourceTidePointId) { this.sourceTidePointId = sourceTidePointId; }
    public Long getCrewId() { return crewId; }
    public void setCrewId(Long crewId) { this.crewId = crewId; }
    public Boolean getFrozenFlag() { return frozenFlag; }
    public void setFrozenFlag(Boolean frozenFlag) { this.frozenFlag = frozenFlag; }
    public String getExcludeReason() { return excludeReason; }
    public void setExcludeReason(String excludeReason) { this.excludeReason = excludeReason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
