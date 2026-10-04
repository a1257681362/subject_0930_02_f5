package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.CrewRequest;
import com.evops.aqua.tide.dto.CrewShiftRequest;
import com.evops.aqua.tide.dto.EquipmentRequest;
import com.evops.aqua.tide.dto.TideTableImportRequest;
import com.evops.aqua.tide.dto.TideWindowRuleRequest;
import com.evops.aqua.tide.dto.WeatherBanRequest;
import com.evops.aqua.tide.entity.TideCrew;
import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TideEquipment;
import com.evops.aqua.tide.entity.TidePoint;
import com.evops.aqua.tide.entity.TideTable;
import com.evops.aqua.tide.entity.TideWeatherBan;
import com.evops.aqua.tide.entity.TideWindowRule;
import com.evops.aqua.tide.service.TideMasterDataService;
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
 * 潮汐排班基础数据：潮汐表/潮点、候选展开规则、禁作天气、班组班次、换水设备。
 */
@RestController
@RequestMapping("/api/aqua/tide")
public class TideMasterDataController {

    private final TideMasterDataService masterDataService;

    public TideMasterDataController(TideMasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @PostMapping("/tide-tables")
    public ApiResponse<TideTable> importTideTable(@Validated @RequestBody TideTableImportRequest request) {
        return ApiResponse.ok(masterDataService.importTideTable(request));
    }

    @GetMapping("/tide-tables")
    public ApiResponse<Map<String, Object>> tideTable(
            @RequestParam String seaArea,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tideDate) {
        TideTable table = masterDataService.getTideTable(seaArea, tideDate);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("table", table);
        detail.put("points", table == null ? null : masterDataService.listPoints(table.getId()));
        return ApiResponse.ok(detail);
    }

    @PostMapping("/window-rules")
    public ApiResponse<TideWindowRule> saveRule(@Validated @RequestBody TideWindowRuleRequest request) {
        return ApiResponse.ok(masterDataService.saveRule(request));
    }

    @GetMapping("/window-rules")
    public ApiResponse<TideWindowRule> getRule(@RequestParam String seaArea) {
        return ApiResponse.ok(masterDataService.getRule(seaArea));
    }

    @PostMapping("/weather-bans")
    public ApiResponse<TideWeatherBan> createWeatherBan(@Validated @RequestBody WeatherBanRequest request) {
        return ApiResponse.ok(masterDataService.createWeatherBan(request));
    }

    @GetMapping("/weather-bans")
    public ApiResponse<List<TideWeatherBan>> listWeatherBans(
            @RequestParam String seaArea,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.ok(masterDataService.listWeatherBans(seaArea, fromDate, toDate));
    }

    @PostMapping("/crews")
    public ApiResponse<TideCrew> createCrew(@Validated @RequestBody CrewRequest request) {
        return ApiResponse.ok(masterDataService.createCrew(request));
    }

    @GetMapping("/crews")
    public ApiResponse<List<TideCrew>> listCrews() {
        return ApiResponse.ok(masterDataService.listCrews());
    }

    @PostMapping("/crew-shifts")
    public ApiResponse<TideCrewShift> createShift(@Validated @RequestBody CrewShiftRequest request) {
        return ApiResponse.ok(masterDataService.createShift(request));
    }

    @GetMapping("/crews/{crewId}/shifts")
    public ApiResponse<List<TideCrewShift>> listShifts(@PathVariable Long crewId) {
        return ApiResponse.ok(masterDataService.listShifts(crewId));
    }

    @PostMapping("/equipment")
    public ApiResponse<TideEquipment> createEquipment(@Validated @RequestBody EquipmentRequest request) {
        return ApiResponse.ok(masterDataService.createEquipment(request));
    }

    @GetMapping("/equipment")
    public ApiResponse<List<TideEquipment>> listEquipment() {
        return ApiResponse.ok(masterDataService.listEquipment());
    }
}
