package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.NoticeAckRequest;
import com.evops.aqua.tide.dto.WindowCloseRequest;
import com.evops.aqua.tide.entity.TideWindowClosure;
import com.evops.aqua.tide.entity.TideWindowNotice;
import com.evops.aqua.tide.service.TideNoticeService;
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
import java.util.Map;

/**
 * 窗口临时关闭通知与班组回执。
 */
@RestController
@RequestMapping("/api/aqua/tide")
public class TideNoticeController {

    private final TideNoticeService noticeService;

    public TideNoticeController(TideNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 临时关闭窗口：向受影响班组发送通知（requestId 幂等）。 */
    @PostMapping("/windows/{windowId}/close")
    public ApiResponse<Map<String, Object>> closeWindow(
            @PathVariable Long windowId,
            @Validated @RequestBody WindowCloseRequest request) {
        return ApiResponse.ok(noticeService.closeWindow(windowId, request));
    }

    /** 班组回执：确认已知悉关闭通知，重复回执返回首次回执。 */
    @PostMapping("/notices/{noticeId}/ack")
    public ApiResponse<TideWindowNotice> acknowledge(
            @PathVariable Long noticeId,
            @Validated @RequestBody NoticeAckRequest request) {
        return ApiResponse.ok(noticeService.acknowledge(noticeId, request));
    }

    @GetMapping("/notices")
    public ApiResponse<List<TideWindowNotice>> listNotices(
            @RequestParam(required = false) Long crewId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(noticeService.listNotices(crewId, status));
    }

    @GetMapping("/closures")
    public ApiResponse<List<TideWindowClosure>> listClosures() {
        return ApiResponse.ok(noticeService.listClosures());
    }
}
