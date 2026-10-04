package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 通知回执：班组确认已知悉窗口关闭，只追加，不可修改。
 */
public class NoticeAckRequest {

    @NotBlank(message = "回执人不能为空")
    private String ackBy;
    private String ackRemark;

    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
}
