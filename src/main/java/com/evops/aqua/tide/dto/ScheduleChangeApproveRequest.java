package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 调度审批：APPROVED 才按申请时段改期（仍校验设备互斥，冲突拒绝且保持待审批）；
 * REJECTED 维持原计划，排班回到已发布。驳回意见建议必填。
 */
public class ScheduleChangeApproveRequest {

    @NotBlank(message = "审批结论不能为空（APPROVED/REJECTED）")
    private String approveStatus;
    @NotBlank(message = "审批人不能为空")
    private String approver;
    private String approveRemark;

    public String getApproveStatus() { return approveStatus; }
    public void setApproveStatus(String approveStatus) { this.approveStatus = approveStatus; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getApproveRemark() { return approveRemark; }
    public void setApproveRemark(String approveRemark) { this.approveRemark = approveRemark; }
}
