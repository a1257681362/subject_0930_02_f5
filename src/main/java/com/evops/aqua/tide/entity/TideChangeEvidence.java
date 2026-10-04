package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 潮汐排班模块变更证据：所有修改留痕（含设备冲突等失败原因），只追加。
 */
@TableName("t_tide_change_evidence")
public class TideChangeEvidence extends BaseEntity {

    public static final String ENTITY_TIDE_TABLE = "TIDE_TABLE";
    public static final String ENTITY_RULE = "WINDOW_RULE";
    public static final String ENTITY_WEATHER = "WEATHER_BAN";
    public static final String ENTITY_WINDOW = "WINDOW";
    public static final String ENTITY_SCHEDULE = "SCHEDULE";
    public static final String ENTITY_CHANGE = "SCHEDULE_CHANGE";
    public static final String ENTITY_CLOSURE = "WINDOW_CLOSURE";
    public static final String ENTITY_NOTICE = "WINDOW_NOTICE";

    public static final String ACTION_TIDE_IMPORT = "TIDE_IMPORT";
    public static final String ACTION_WINDOW_RECALC = "WINDOW_RECALC";
    public static final String ACTION_SCHEDULE_PREPARE = "SCHEDULE_PREPARE";
    public static final String ACTION_SCHEDULE_CONFLICT = "SCHEDULE_CONFLICT";
    public static final String ACTION_SCHEDULE_PUBLISH = "SCHEDULE_PUBLISH";
    public static final String ACTION_CHANGE_REQUEST = "CHANGE_REQUEST";
    public static final String ACTION_CHANGE_APPROVE = "CHANGE_APPROVE";
    public static final String ACTION_CHANGE_REJECT = "CHANGE_REJECT";
    public static final String ACTION_POSTPONE = "POSTPONE";
    public static final String ACTION_WINDOW_CLOSE = "WINDOW_CLOSE";
    public static final String ACTION_NOTICE_SEND = "NOTICE_SEND";
    public static final String ACTION_NOTICE_ACK = "NOTICE_ACK";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String operator;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
