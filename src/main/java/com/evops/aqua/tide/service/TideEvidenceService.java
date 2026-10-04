package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.mapper.TideChangeEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 潮汐排班模块变更证据：所有修改统一落 t_tide_change_evidence，只追加。
 * 每条证据必须带操作者（operator）——定时/系统生成固定用调度系统标识，不丢操作者。
 */
@Service
public class TideEvidenceService {

    public static final String SYSTEM_OPERATOR = "system-scheduler";

    private final TideChangeEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;

    public TideEvidenceService(TideChangeEvidenceMapper evidenceMapper, ObjectMapper objectMapper) {
        this.evidenceMapper = evidenceMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, String requestId,
                       String operator, Object before, Object after, String detail) {
        TideChangeEvidence evidence = new TideChangeEvidence();
        evidence.setEntityType(entityType);
        evidence.setEntityId(entityId);
        evidence.setAction(action);
        evidence.setRequestId(requestId);
        evidence.setOperator(operator == null ? SYSTEM_OPERATOR : operator);
        evidence.setBeforeJson(toJson(before));
        evidence.setAfterJson(toJson(after));
        evidence.setDetail(detail);
        evidenceMapper.insert(evidence);
    }

    /**
     * 独立事务写证据：用于设备冲突等"业务回滚但冲突原因必须留下"的场景。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordStandalone(String entityType, Long entityId, String action, String requestId,
                                 String operator, Object before, Object after, String detail) {
        record(entityType, entityId, action, requestId, operator, before, after, detail);
    }

    public List<TideChangeEvidence> list(String entityType, Long entityId, String requestId) {
        QueryWrapper<TideChangeEvidence> wrapper = new QueryWrapper<>();
        wrapper.eq(entityType != null && !entityType.trim().isEmpty(), "entity_type", entityType);
        wrapper.eq(entityId != null, "entity_id", entityId);
        wrapper.eq(requestId != null && !requestId.trim().isEmpty(), "request_id", requestId);
        wrapper.orderByAsc("id");
        return evidenceMapper.selectList(wrapper);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
