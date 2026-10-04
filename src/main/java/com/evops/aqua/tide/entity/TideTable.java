package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 海区潮汐表：按海区+日期一行，导入即覆盖该海区当日潮点。
 */
@TableName("t_tide_table")
public class TideTable extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private String tideTableNo;
    private String seaArea;
    private LocalDate tideDate;
    private String tideType;
    private String source;
    private String status;
    private String remark;

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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
