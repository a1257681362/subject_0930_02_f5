package com.evops.aqua.tide.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 海区潮汐表导入请求：按海区+日期导入潮点。
 * 同一海区同一天重复导入即覆盖（先删后插，同事务留证）——潮汐数据调到临界时间后重算窗口用同一口径。
 */
public class TideTableImportRequest {

    @NotBlank(message = "潮汐表编号不能为空")
    private String tideTableNo;

    @NotBlank(message = "海区不能为空")
    private String seaArea;

    @NotNull(message = "潮汐日期不能为空")
    private LocalDate tideDate;

    private String tideType = "SEMI_DIURNAL";
    private String source;
    private String remark;

    /** 导入操作人（留证），缺省调度系统标识。 */
    private String operator = "system";

    @NotEmpty(message = "潮点列表不能为空")
    @Valid
    private List<PointInput> points;

    public static class PointInput {
        @NotNull(message = "潮点序号不能为空")
        private Integer pointSeq;
        @NotBlank(message = "潮点类型不能为空（HIGH/LOW）")
        private String pointType;
        @NotNull(message = "潮点时刻不能为空")
        private LocalDateTime pointTime;
        private Integer heightCm;

        public Integer getPointSeq() { return pointSeq; }
        public void setPointSeq(Integer pointSeq) { this.pointSeq = pointSeq; }
        public String getPointType() { return pointType; }
        public void setPointType(String pointType) { this.pointType = pointType; }
        public LocalDateTime getPointTime() { return pointTime; }
        public void setPointTime(LocalDateTime pointTime) { this.pointTime = pointTime; }
        public Integer getHeightCm() { return heightCm; }
        public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }
    }

    public String getTideTableNo() { return tideTableNo; }
    public void setTideTableNo(String tideTableNo) { this.tideTableNo = tideTableNo; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public LocalDate getTideDate() { return tideDate; }
    public void setTideDate(LocalDate tideDate) { this.tideDate = tideDate; }
    public String getTideType() { return tideType; }
    public void setTideType(String tideType) { this.tideType = tideType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<PointInput> getPoints() { return points; }
    public void setPoints(List<PointInput> points) { this.points = points; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
