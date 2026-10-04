package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.WindowGenerateRequest;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.service.TideWindowService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 换水窗口：潮汐候选叠加天气/班次生成可执行窗口，统一半开口径与归属日。
 */
@RestController
@RequestMapping("/api/aqua/tide/windows")
public class TideWindowController {

    private final TideWindowService windowService;

    public TideWindowController(TideWindowService windowService) {
        this.windowService = windowService;
    }

    /** 生成/重算：已排班/已关闭窗口冻结保留，候选与可执行窗口按最新潮表同口径重建。 */
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@Validated @RequestBody WindowGenerateRequest request) {
        return ApiResponse.ok(windowService.generate(request));
    }

    @GetMapping
    public ApiResponse<List<TideWindow>> list(
            @RequestParam(required = false) Long pondId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(windowService.list(pondId, planDate, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        TideWindow window = windowService.getOrThrow(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("window", window);
        detail.put("belongDateConsistent", windowService.belongsDateConsistent(window));
        return ApiResponse.ok(detail);
    }
}
