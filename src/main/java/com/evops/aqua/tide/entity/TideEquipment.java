package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 换水设备：同一设备同一时间只能服务一口池（窗口可端点相接，不可重叠）。
 */
@TableName("t_tide_equipment")
public class TideEquipment extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String equipmentCode;
    private String equipmentName;
    private String seaArea;
    private String status;
    private String remark;

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
