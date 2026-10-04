package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 潮点：高/低潮时刻与潮高（厘米）。换水候选窗口由 HIGH 潮点前后偏移展开。
 */
@TableName("t_tide_point")
public class TidePoint extends BaseEntity {

    public static final String TYPE_HIGH = "HIGH";
    public static final String TYPE_LOW = "LOW";

    private Long tideTableId;
    private Integer pointSeq;
    private String pointType;
    private LocalDateTime pointTime;
    private Integer heightCm;

    public Long getTideTableId() { return tideTableId; }
    public void setTideTableId(Long tideTableId) { this.tideTableId = tideTableId; }
    public Integer getPointSeq() { return pointSeq; }
    public void setPointSeq(Integer pointSeq) { this.pointSeq = pointSeq; }
    public String getPointType() { return pointType; }
    public void setPointType(String pointType) { this.pointType = pointType; }
    public LocalDateTime getPointTime() { return pointTime; }
    public void setPointTime(LocalDateTime pointTime) { this.pointTime = pointTime; }
    public Integer getHeightCm() { return heightCm; }
    public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }
}
