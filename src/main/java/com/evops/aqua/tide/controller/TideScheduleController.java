package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.ScheduleChangeApproveRequest;
import com.evops.aqua.tide.dto.ScheduleChangeRequest;
import com.evops.aqua.tide.dto.SchedulePostponeRequest;
import com.evops.aqua.tide.dto.SchedulePrepareRequest;
import com.evops.aqua.tide.dto.SchedulePublishRequest;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.entity.TideScheduleChange;
import com.evops.aqua.tide.service.TideScheduleService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 换水排班：准备草稿 → 发布；发布后修改经调度审批；自动顺延不改原计划。
 */
@RestController
@RequestMapping("/api/aqua/tide/schedules")
public class TideScheduleController {

    private final TideScheduleService scheduleService;

    public TideScheduleController(TideScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ApiResponse<TideSchedule> prepare(@Validated @RequestBody SchedulePrepareRequest request) {
        return ApiResponse.ok(scheduleService.prepare(request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<TideSchedule> publish(@PathVariable Long id,
                                             @Validated @RequestBody SchedulePublishRequest request) {
        return ApiResponse.ok(scheduleService.publish(id, request));
    }

    /** 发布后修改：只登记申请，等待调度审批（requestId 幂等）。 */
    @PostMapping("/{id}/changes")
    public ApiResponse<TideScheduleChange> requestChange(
            @PathVariable Long id,
            @Validated @RequestBody ScheduleChangeRequest request) {
        return ApiResponse.ok(scheduleService.requestChange(id, request));
    }

    /** 调度审批：APPROVED 改期（设备冲突仍拦截），REJECTED 维持原计划。 */
    @PostMapping("/changes/{changeId}/approve")
    public ApiResponse<TideScheduleChange> approveChange(
            @PathVariable Long changeId,
            @Validated @RequestBody ScheduleChangeApproveRequest request) {
        return ApiResponse.ok(scheduleService.approveChange(changeId, request));
    }

    /** 自动顺延：直接生效不走审批，但原计划永不变更。 */
    @PostMapping("/{id}/postpone")
    public ApiResponse<TideSchedule> postpone(@PathVariable Long id,
                                              @Validated @RequestBody SchedulePostponeRequest request) {
        return ApiResponse.ok(scheduleService.postpone(id, request));
    }

    @GetMapping
    public ApiResponse<List<TideSchedule>> list(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long crewId,
            @RequestParam(required = false) Long pondId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(scheduleService.list(equipmentId, crewId, pondId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<TideSchedule> detail(@PathVariable Long id) {
        return ApiResponse.ok(scheduleService.getOrThrow(id));
    }

    @GetMapping("/{id}/changes")
    public ApiResponse<List<TideScheduleChange>> listChanges(
            @PathVariable Long id,
            @RequestParam(required = false) String approveStatus) {
        return ApiResponse.ok(scheduleService.listChanges(id, approveStatus));
    }
}
