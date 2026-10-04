package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 换水设备建档。
 */
public class EquipmentRequest {

    @NotBlank(message = "设备编号不能为空")
    private String equipmentCode;
    @NotBlank(message = "设备名称不能为空")
    private String equipmentName;
    private String seaArea;
    private String remark;

    public String getEquipmentCode() { return equipmentCode; }
    public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
