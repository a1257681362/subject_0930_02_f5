package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.dto.ScheduleChangeApproveRequest;
import com.evops.aqua.tide.dto.ScheduleChangeRequest;
import com.evops.aqua.tide.dto.SchedulePostponeRequest;
import com.evops.aqua.tide.dto.SchedulePrepareRequest;
import com.evops.aqua.tide.dto.SchedulePublishRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideCrew;
import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TideEquipment;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.entity.TideScheduleChange;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.mapper.TideScheduleChangeMapper;
import com.evops.aqua.tide.mapper.TideScheduleMapper;
import com.evops.aqua.tide.support.HalfOpenInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 换水排班：可执行窗口准备草稿 → 发布；发布后修改必须经调度审批；自动顺延直接生效但不改原计划。
 * <p>
 * 设备互斥统一半开口径：同一设备时段重叠（{@link HalfOpenInterval#overlaps}）才冲突，端点相接允许；
 * 作业班组必须在作业时段有班次覆盖；原计划 original_start/end 一经发布永不改变。
 */
@Service
public class TideScheduleService {

    /** 参与设备互斥的有效排班状态（已取消不占位）。 */
    private static final List<String> ACTIVE_STATUSES = Arrays.asList(
            TideSchedule.STATUS_DRAFT,
            TideSchedule.STATUS_PUBLISHED,
            TideSchedule.STATUS_ADJUST_PENDING,
            TideSchedule.STATUS_RESCHEDULED);

    private final TideScheduleMapper scheduleMapper;
    private final TideScheduleChangeMapper changeMapper;
    private final TideWindowService windowService;
    private final TideMasterDataService masterDataService;
    private final TideEvidenceService evidenceService;

    public TideScheduleService(TideScheduleMapper scheduleMapper,
                               TideScheduleChangeMapper changeMapper,
                               TideWindowService windowService,
                               TideMasterDataService masterDataService,
                               TideEvidenceService evidenceService) {
        this.scheduleMapper = scheduleMapper;
        this.changeMapper = changeMapper;
        this.windowService = windowService;
        this.masterDataService = masterDataService;
        this.evidenceService = evidenceService;
    }

    /**
     * 准备排班草稿：窗口必须可执行；设备同时段单池互斥；班组班次须覆盖作业时段；
     * 作业时段缺省取整个可执行窗口，指定时必须落在窗口半开区间内。
     * 冲突时业务回滚，但冲突原因以独立事务留证。
     */
    @Transactional(rollbackFor = Exception.class)
    public TideSchedule prepare(SchedulePrepareRequest request) {
        TideWindow window = windowService.getOrThrow(request.getWindowId());
        if (TideWindow.STATUS_CLOSED.equals(window.getWindowStatus())) {
            throw new IllegalArgumentException("窗口已临时关闭，不能排班：" + window.getWindowNo());
        }
        if (TideWindow.STATUS_SCHEDULED.equals(window.getWindowStatus())) {
            throw new IllegalArgumentException("窗口已存在排班，不能重复准备：" + window.getWindowNo());
        }
        if (!TideWindow.STATUS_EXECUTABLE.equals(window.getWindowStatus())) {
            throw new IllegalArgumentException("仅可执行窗口能排班，当前状态=" + window.getWindowStatus());
        }
        TideEquipment equipment = masterDataService.getEquipmentOrThrow(request.getEquipmentId());
        TideCrew crew = masterDataService.getCrewOrThrow(request.getCrewId());
        if (!TideCrew.STATUS_ACTIVE.equals(crew.getStatus())) {
            throw new IllegalArgumentException("班组已停用：" + crew.getCrewCode());
        }

        LocalDateTime start = request.getScheduledStart() == null
                ? window.getWindowStart() : request.getScheduledStart();
        LocalDateTime end = request.getScheduledEnd() == null
                ? window.getWindowEnd() : request.getScheduledEnd();
        HalfOpenInterval job;
        try {
            job = new HalfOpenInterval(start, end);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("排班作业时段非法：" + ex.getMessage());
        }
        HalfOpenInterval windowIv = new HalfOpenInterval(window.getWindowStart(), window.getWindowEnd());
        if (start.isBefore(window.getWindowStart()) || end.isAfter(window.getWindowEnd())) {
            throw new IllegalArgumentException("作业时段必须落在可执行窗口内：窗口"
                    + windowIv + "，作业" + job);
        }

        String conflict = checkEquipmentConflict(equipment.getId(), job, null);
        if (conflict != null) {
            // 失败证据独立事务提交：排班被拦截，但设备冲突事实必须留下
            evidenceService.recordStandalone(TideChangeEvidence.ENTITY_SCHEDULE, window.getId(),
                    TideChangeEvidence.ACTION_SCHEDULE_CONFLICT, null, request.getOperator(),
                    null, null, "排班准备被设备互斥拦截：窗口=" + window.getWindowNo()
                            + "，设备=" + equipment.getEquipmentCode() + "，作业" + job + "，" + conflict);
            throw new IllegalArgumentException("换水设备时段冲突，同一时间只能服务一口池：" + conflict);
        }
        String shiftGap = checkCrewShiftCoverage(crew.getId(), job);
        if (shiftGap != null) {
            throw new IllegalArgumentException("作业班组在作业时段无完整班次覆盖：" + shiftGap);
        }

        TideSchedule schedule = new TideSchedule();
        schedule.setScheduleNo("SCH-" + window.getId());
        schedule.setWindowId(window.getId());
        schedule.setPondId(window.getPondId());
        schedule.setEquipmentId(equipment.getId());
        schedule.setCrewId(crew.getId());
        schedule.setScheduledStart(start);
        schedule.setScheduledEnd(end);
        schedule.setOriginalStart(start);
        schedule.setOriginalEnd(end);
        schedule.setScheduleStatus(TideSchedule.STATUS_DRAFT);
        schedule.setRemark(request.getRemark());
        scheduleMapper.insert(schedule);

        evidenceService.record(TideChangeEvidence.ENTITY_SCHEDULE, schedule.getId(),
                TideChangeEvidence.ACTION_SCHEDULE_PREPARE, schedule.getScheduleNo(),
                request.getOperator(), null, schedule,
                "准备换水排班草稿：窗口=" + window.getWindowNo() + "，设备="
                        + equipment.getEquipmentCode() + "，班组=" + crew.getCrewName() + "，作业" + job);
        return schedule;
    }

    /** 发布：仅草稿可发布；发布即冻结原计划，窗口转已排班并冻结（重算不重建）。 */
    @Transactional(rollbackFor = Exception.class)
    public TideSchedule publish(Long scheduleId, SchedulePublishRequest request) {
        TideSchedule before = copyOf(getOrThrow(scheduleId));
        TideSchedule schedule = getOrThrow(scheduleId);
        if (!TideSchedule.STATUS_DRAFT.equals(schedule.getScheduleStatus())) {
            throw new IllegalArgumentException("仅草稿排班可发布，当前状态=" + schedule.getScheduleStatus());
        }
        schedule.setScheduleStatus(TideSchedule.STATUS_PUBLISHED);
        schedule.setPublishedBy(request.getPublishedBy());
        schedule.setPublishedTime(LocalDateTime.now());
        if (request.getRemark() != null) {
            schedule.setRemark(request.getRemark());
        }
        scheduleMapper.updateById(schedule);

        TideWindow window = windowService.getOrThrow(schedule.getWindowId());
        window.setWindowStatus(TideWindow.STATUS_SCHEDULED);
        window.setCrewId(schedule.getCrewId());
        window.setFrozenFlag(true);
        // 冻结窗口更新复用 mapper，避免与窗口服务循环依赖
        windowFrozenUpdate(window);

        evidenceService.record(TideChangeEvidence.ENTITY_SCHEDULE, schedule.getId(),
                TideChangeEvidence.ACTION_SCHEDULE_PUBLISH, schedule.getScheduleNo(),
                request.getPublishedBy(), before, schedule,
                "发布换水排班：" + schedule.getScheduleNo() + "，原计划冻结"
                        + new HalfOpenInterval(schedule.getOriginalStart(), schedule.getOriginalEnd()));
        return schedule;
    }

    /**
     * 发布后修改申请：不直接改动，生成待审批单（requestId 幂等）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TideScheduleChange requestChange(Long scheduleId, ScheduleChangeRequest request) {
        TideScheduleChange duplicated = changeMapper.selectOne(
                new QueryWrapper<TideScheduleChange>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated;
        }
        TideSchedule schedule = getOrThrow(scheduleId);
        if (!TideSchedule.STATUS_PUBLISHED.equals(schedule.getScheduleStatus())
                && !TideSchedule.STATUS_RESCHEDULED.equals(schedule.getScheduleStatus())) {
            throw new IllegalArgumentException(
                    "仅已发布/已顺延排班可申请修改，当前状态=" + schedule.getScheduleStatus());
        }
        if (!request.getRequestedEnd().isAfter(request.getRequestedStart())) {
            throw new IllegalArgumentException("目标作业结束必须严格晚于开始（半开区间）");
        }
        TideScheduleChange change = new TideScheduleChange();
        change.setScheduleId(schedule.getId());
        change.setRequestId(request.getRequestId());
        change.setRequestedStart(request.getRequestedStart());
        change.setRequestedEnd(request.getRequestedEnd());
        change.setChangeReason(request.getChangeReason());
        change.setRequestedBy(request.getRequestedBy());
        change.setRequestTime(LocalDateTime.now());
        change.setApproveStatus(TideScheduleChange.STATUS_PENDING);
        changeMapper.insert(change);

        schedule.setScheduleStatus(TideSchedule.STATUS_ADJUST_PENDING);
        scheduleMapper.updateById(schedule);

        evidenceService.record(TideChangeEvidence.ENTITY_CHANGE, change.getId(),
                TideChangeEvidence.ACTION_CHANGE_REQUEST, request.getRequestId(),
                request.getRequestedBy(), schedule, change,
                "排班修改申请待调度审批：" + schedule.getScheduleNo()
                        + "，目标时段[" + request.getRequestedStart() + "," + request.getRequestedEnd()
                        + ")，原因=" + request.getChangeReason());
        return change;
    }

    /**
     * 调度审批：APPROVED 按申请时段改期（仍校验设备互斥，冲突则保持待审批并留失败证据）；
     * REJECTED 维持原计划，排班回到已发布，驳回必须填意见。
     * 无论通过与否，original_start/end 永不变。
     */
    @Transactional(rollbackFor = Exception.class)
    public TideScheduleChange approveChange(Long changeId, ScheduleChangeApproveRequest request) {
        TideScheduleChange change = changeMapper.selectById(changeId);
        if (change == null) {
            throw new IllegalArgumentException("排班修改审批单不存在，id=" + changeId);
        }
        if (!TideScheduleChange.STATUS_PENDING.equals(change.getApproveStatus())) {
            throw new IllegalArgumentException("审批单已处理，不能重复审批：" + change.getApproveStatus());
        }
        TideSchedule beforeSchedule = copyOf(getOrThrow(change.getScheduleId()));
        TideSchedule schedule = getOrThrow(change.getScheduleId());
        String verdict = request.getApproveStatus() == null
                ? "" : request.getApproveStatus().trim().toUpperCase();

        if (TideScheduleChange.STATUS_REJECTED.equals(verdict)) {
            if (request.getApproveRemark() == null || request.getApproveRemark().trim().isEmpty()) {
                throw new IllegalArgumentException("驳回修改必须填写审批意见");
            }
            change.setApproveStatus(TideScheduleChange.STATUS_REJECTED);
            change.setApprover(request.getApprover());
            change.setApproveRemark(request.getApproveRemark());
            change.setApproveTime(LocalDateTime.now());
            changeMapper.updateById(change);
            schedule.setScheduleStatus(TideSchedule.STATUS_PUBLISHED);
            scheduleMapper.updateById(schedule);
            assertOriginalUnchanged(schedule);
            evidenceService.record(TideChangeEvidence.ENTITY_CHANGE, change.getId(),
                    TideChangeEvidence.ACTION_CHANGE_REJECT, change.getRequestId(),
                    request.getApprover(), beforeSchedule, schedule,
                    "调度驳回排班修改，维持原计划：" + schedule.getScheduleNo()
                            + "，意见=" + request.getApproveRemark());
            return change;
        }
        if (!TideScheduleChange.STATUS_APPROVED.equals(verdict)) {
            throw new IllegalArgumentException("审批结论只能是 APPROVED/REJECTED，实际=" + request.getApproveStatus());
        }

        HalfOpenInterval target;
        try {
            target = new HalfOpenInterval(change.getRequestedStart(), change.getRequestedEnd());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("目标作业时段非法：" + ex.getMessage());
        }
        TideWindow window = windowService.getOrThrow(schedule.getWindowId());
        if (target.getStart().isBefore(window.getWindowStart())
                || target.getEnd().isAfter(window.getWindowEnd())) {
            throw new IllegalArgumentException("审批改期时段必须落在原可执行窗口内：窗口"
                    + new HalfOpenInterval(window.getWindowStart(), window.getWindowEnd())
                    + "，目标" + target);
        }
        String conflict = checkEquipmentConflict(schedule.getEquipmentId(), target, schedule.getId());
        if (conflict != null) {
            evidenceService.recordStandalone(TideChangeEvidence.ENTITY_CHANGE, change.getId(),
                    TideChangeEvidence.ACTION_SCHEDULE_CONFLICT, change.getRequestId(),
                    request.getApprover(), beforeSchedule, null,
                    "审批通过但设备时段冲突，保持待审批：排班=" + schedule.getScheduleNo()
                            + "，目标" + target + "，" + conflict);
            throw new IllegalArgumentException("审批改期设备时段冲突，未生效：" + conflict);
        }

        change.setApproveStatus(TideScheduleChange.STATUS_APPROVED);
        change.setApprover(request.getApprover());
        change.setApproveRemark(request.getApproveRemark());
        change.setApproveTime(LocalDateTime.now());
        changeMapper.updateById(change);

        schedule.setScheduledStart(change.getRequestedStart());
        schedule.setScheduledEnd(change.getRequestedEnd());
        schedule.setScheduleStatus(TideSchedule.STATUS_RESCHEDULED);
        schedule.setChangeReason(change.getChangeReason());
        scheduleMapper.updateById(schedule);
        assertOriginalUnchanged(schedule);

        evidenceService.record(TideChangeEvidence.ENTITY_CHANGE, change.getId(),
                TideChangeEvidence.ACTION_CHANGE_APPROVE, change.getRequestId(),
                request.getApprover(), beforeSchedule, schedule,
                "调度批准排班修改并改期：" + schedule.getScheduleNo() + "，新时段" + target
                        + "，原计划[" + schedule.getOriginalStart() + "," + schedule.getOriginalEnd()
                        + ")保持不变");
        return change;
    }

    /**
     * 自动顺延：直接生效、不走审批，但只能向后顺延且绝不改变原计划；仍校验设备互斥。
     */
    @Transactional(rollbackFor = Exception.class)
    public TideSchedule postpone(Long scheduleId, SchedulePostponeRequest request) {
        TideSchedule before = copyOf(getOrThrow(scheduleId));
        TideSchedule schedule = getOrThrow(scheduleId);
        if (TideSchedule.STATUS_ADJUST_PENDING.equals(schedule.getScheduleStatus())) {
            throw new IllegalArgumentException("排班存在待审批修改，不能顺延");
        }
        if (!TideSchedule.STATUS_PUBLISHED.equals(schedule.getScheduleStatus())
                && !TideSchedule.STATUS_RESCHEDULED.equals(schedule.getScheduleStatus())) {
            throw new IllegalArgumentException("仅已发布/已顺延排班可顺延，当前状态=" + schedule.getScheduleStatus());
        }
        if (request.getNewStart().isBefore(schedule.getScheduledStart())) {
            throw new IllegalArgumentException("顺延只能向后调整，不能提前");
        }
        HalfOpenInterval target;
        try {
            target = new HalfOpenInterval(request.getNewStart(), request.getNewEnd());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("顺延时段非法：" + ex.getMessage());
        }
        TideWindow window = windowService.getOrThrow(schedule.getWindowId());
        if (target.getStart().isBefore(window.getWindowStart())
                || target.getEnd().isAfter(window.getWindowEnd())) {
            throw new IllegalArgumentException("顺延时段必须仍落在可执行窗口内：窗口"
                    + new HalfOpenInterval(window.getWindowStart(), window.getWindowEnd())
                    + "，目标" + target);
        }
        String conflict = checkEquipmentConflict(schedule.getEquipmentId(), target, schedule.getId());
        if (conflict != null) {
            evidenceService.recordStandalone(TideChangeEvidence.ENTITY_SCHEDULE, schedule.getId(),
                    TideChangeEvidence.ACTION_SCHEDULE_CONFLICT, null, request.getOperator(),
                    before, null, "顺延时设备时段冲突，未生效：排班=" + schedule.getScheduleNo()
                            + "，目标" + target + "，" + conflict);
            throw new IllegalArgumentException("顺延目标时段设备冲突：" + conflict);
        }
        schedule.setScheduledStart(request.getNewStart());
        schedule.setScheduledEnd(request.getNewEnd());
        schedule.setScheduleStatus(TideSchedule.STATUS_RESCHEDULED);
        schedule.setChangeReason(request.getReason());
        scheduleMapper.updateById(schedule);
        assertOriginalUnchanged(schedule);

        evidenceService.record(TideChangeEvidence.ENTITY_SCHEDULE, schedule.getId(),
                TideChangeEvidence.ACTION_POSTPONE, null, request.getOperator(),
                before, schedule,
                "换水排班自动顺延：" + schedule.getScheduleNo() + "，顺延至" + target
                        + "，原计划[" + schedule.getOriginalStart() + "," + schedule.getOriginalEnd()
                        + ")不改变，原因=" + request.getReason());
        return schedule;
    }

    public TideSchedule getOrThrow(Long id) {
        TideSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new IllegalArgumentException("换水排班不存在，id=" + id);
        }
        return schedule;
    }

    public TideSchedule findByWindowId(Long windowId) {
        return scheduleMapper.selectOne(new QueryWrapper<TideSchedule>().eq("window_id", windowId));
    }

    /** 窗口临时关闭联动：排班置已取消（原计划仍保留），由通知服务在同事务内调用并留通知证据。 */
    public void cancelForClosure(TideSchedule schedule) {
        scheduleMapper.updateById(schedule);
    }

    public List<TideSchedule> list(Long equipmentId, Long crewId, Long pondId, String status) {
        QueryWrapper<TideSchedule> wrapper = new QueryWrapper<>();
        wrapper.eq(equipmentId != null, "equipment_id", equipmentId);
        wrapper.eq(crewId != null, "crew_id", crewId);
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "schedule_status", status);
        wrapper.orderByAsc("scheduled_start");
        return scheduleMapper.selectList(wrapper);
    }

    public List<TideScheduleChange> listChanges(Long scheduleId, String approveStatus) {
        QueryWrapper<TideScheduleChange> wrapper = new QueryWrapper<>();
        wrapper.eq(scheduleId != null, "schedule_id", scheduleId);
        wrapper.eq(approveStatus != null && !approveStatus.trim().isEmpty(),
                "approve_status", approveStatus);
        wrapper.orderByAsc("id");
        return changeMapper.selectList(wrapper);
    }

    /**
     * 设备互斥（半开口径）：同设备有效排班与作业区间重叠即冲突；端点相接不冲突。
     * SQL 按半开条件粗筛，内存用 HalfOpenInterval.overlaps 复核，保证全模块同一边界口径。
     */
    private String checkEquipmentConflict(Long equipmentId, HalfOpenInterval job, Long excludeScheduleId) {
        QueryWrapper<TideSchedule> wrapper = new QueryWrapper<TideSchedule>()
                .eq("equipment_id", equipmentId)
                .in("schedule_status", ACTIVE_STATUSES)
                .lt("scheduled_start", job.getEnd())
                .gt("scheduled_end", job.getStart());
        List<TideSchedule> candidates = scheduleMapper.selectList(wrapper);
        List<String> conflicts = new ArrayList<>();
        for (TideSchedule other : candidates) {
            if (excludeScheduleId != null && excludeScheduleId.equals(other.getId())) {
                continue;
            }
            HalfOpenInterval otherIv = new HalfOpenInterval(other.getScheduledStart(), other.getScheduledEnd());
            if (job.overlaps(otherIv)) {
                conflicts.add("排班" + other.getScheduleNo() + "占用池" + other.getPondId()
                        + " 时段" + otherIv);
            }
        }
        return conflicts.isEmpty() ? null : String.join("；", conflicts);
    }

    /** 班组班次须完整覆盖作业时段（半开减法后无剩余即全覆盖；相接即视为覆盖）。 */
    private String checkCrewShiftCoverage(Long crewId, HalfOpenInterval job) {
        List<TideCrewShift> shifts = masterDataService.listShifts(crewId);
        List<HalfOpenInterval> intervals = new ArrayList<>();
        for (TideCrewShift shift : shifts) {
            HalfOpenInterval iv;
            try {
                iv = new HalfOpenInterval(shift.getShiftStart(), shift.getShiftEnd());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            intervals.add(iv);
        }
        List<HalfOpenInterval> uncovered = job.subtract(intervals);
        if (!uncovered.isEmpty()) {
            return "未覆盖时段" + uncovered;
        }
        return null;
    }

    private void assertOriginalUnchanged(TideSchedule schedule) {
        TideSchedule persisted = scheduleMapper.selectById(schedule.getId());
        if (persisted.getOriginalStart() == null || persisted.getOriginalEnd() == null
                || !persisted.getOriginalStart().equals(schedule.getOriginalStart())
                || !persisted.getOriginalEnd().equals(schedule.getOriginalEnd())) {
            throw new IllegalStateException("内部错误：顺延/改期不得改变原计划");
        }
    }

    private void windowFrozenUpdate(TideWindow window) {
        // 经窗口服务自身的 mapper 路径更新：直接使用注入的 schedule mapper 无法访问窗口表，
        // 故通过 windowService 暴露的更新方法完成；见 TideWindowService#updateFrozen。
        windowService.updateStatusAndCrew(window);
    }

    private TideSchedule copyOf(TideSchedule s) {
        TideSchedule c = new TideSchedule();
        c.setId(s.getId());
        c.setScheduleNo(s.getScheduleNo());
        c.setWindowId(s.getWindowId());
        c.setPondId(s.getPondId());
        c.setEquipmentId(s.getEquipmentId());
        c.setCrewId(s.getCrewId());
        c.setScheduledStart(s.getScheduledStart());
        c.setScheduledEnd(s.getScheduledEnd());
        c.setOriginalStart(s.getOriginalStart());
        c.setOriginalEnd(s.getOriginalEnd());
        c.setScheduleStatus(s.getScheduleStatus());
        c.setPublishedBy(s.getPublishedBy());
        c.setChangeReason(s.getChangeReason());
        return c;
    }
}
