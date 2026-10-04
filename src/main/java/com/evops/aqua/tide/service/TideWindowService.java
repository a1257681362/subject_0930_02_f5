package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.service.PondService;
import com.evops.aqua.tide.dto.WindowGenerateRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TidePoint;
import com.evops.aqua.tide.entity.TideTable;
import com.evops.aqua.tide.entity.TideWeatherBan;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.entity.TideWindowRule;
import com.evops.aqua.tide.mapper.TideWindowMapper;
import com.evops.aqua.tide.support.HalfOpenInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 换水窗口生成：海区潮汐表 → 高潮前后偏移生成换水候选（半开）
 * → 减去禁作天气时段 → 与人员班次并集取交集 → 可执行窗口。
 * <p>
 * 统一口径：候选与可执行窗口一律半开 [start,end)，归属日 plan_date = start 所在自然日，
 * 潮时被调到临界时间（00:00、整点相接）后重算，窗口归属仍由同一函数 {@link HalfOpenInterval#belongDate()} 决定。
 * 已排班/已关闭窗口冻结，重算只重建候选/可执行窗口。
 */
@Service
public class TideWindowService {

    private static final int DEFAULT_MIN_WINDOW_MINUTES = 30;

    private final TideWindowMapper windowMapper;
    private final TideMasterDataService masterDataService;
    private final PondService pondService;
    private final TideEvidenceService evidenceService;

    public TideWindowService(TideWindowMapper windowMapper,
                             TideMasterDataService masterDataService,
                             PondService pondService,
                             TideEvidenceService evidenceService) {
        this.windowMapper = windowMapper;
        this.masterDataService = masterDataService;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(WindowGenerateRequest request) {
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("生成结束日期不能早于开始日期");
        }
        int minMinutes = request.getMinWindowMinutes() == null
                ? DEFAULT_MIN_WINDOW_MINUTES : request.getMinWindowMinutes();
        if (minMinutes <= 0) {
            throw new IllegalArgumentException("可执行窗口最小时长必须大于0");
        }
        String seaArea = pond.getSeaArea();
        TideWindowRule rule = masterDataService.getRule(seaArea);
        if (rule == null) {
            throw new IllegalArgumentException("海区尚未配置换水候选展开规则：" + seaArea);
        }

        // 1) 候选窗口：逐日取潮表目标潮点，按偏移展开半开区间。
        // 相邻高潮展开后可能搭接（半开重叠），先合并候选，保证后续裁剪不产生重叠窗口。
        List<CandidateSegment> candidateSegments = new ArrayList<>();
        for (LocalDate date = request.getStartDate();
             !date.isAfter(request.getEndDate());
             date = date.plusDays(1)) {
            TideTable table = masterDataService.getTideTable(seaArea, date);
            if (table == null) {
                continue;
            }
            List<TidePoint> points = masterDataService.listPoints(table.getId());
            for (TidePoint point : points) {
                if (!rule.getTargetPointType().equals(point.getPointType())) {
                    continue;
                }
                HalfOpenInterval candidate = new HalfOpenInterval(
                        point.getPointTime().minusMinutes(rule.getBeforeMinutes()),
                        point.getPointTime().plusMinutes(rule.getAfterMinutes()));
                candidateSegments.add(new CandidateSegment(candidate, point.getId()));
            }
        }
        candidateSegments.sort(Comparator.comparing(s -> s.interval.getStart()));
        List<CandidateSegment> candidates = mergeCandidates(candidateSegments);

        // 2) 禁作天气阻塞段（半开减法）。
        List<TideWeatherBan> bans = masterDataService.listWeatherBans(
                seaArea, request.getStartDate(), request.getEndDate());
        List<HalfOpenInterval> weatherBlockers = new ArrayList<>();
        for (TideWeatherBan ban : bans) {
            weatherBlockers.add(new HalfOpenInterval(ban.getBanStart(), ban.getBanEnd()));
        }

        // 3) 人员班次并集（所有班组可出勤时段取并，具体班组在排班准备时再校验）。
        List<HalfOpenInterval> shiftCoverage = unionOfShifts(
                masterDataService.listShifts(null), request.getStartDate(), request.getEndDate());

        // 4) 重算：删除该池区间内未冻结（候选/可执行）窗口；已排班/已关闭窗口冻结保留。
        List<TideWindow> frozen = windowMapper.selectList(new QueryWrapper<TideWindow>()
                .eq("pond_id", pond.getId())
                .in("window_status", TideWindow.STATUS_SCHEDULED, TideWindow.STATUS_CLOSED)
                .ge("window_start", request.getStartDate().atStartOfDay())
                .lt("window_start", request.getEndDate().plusDays(1).atStartOfDay())
                .orderByAsc("window_start"));
        windowMapper.delete(new QueryWrapper<TideWindow>()
                .eq("pond_id", pond.getId())
                .in("window_status", TideWindow.STATUS_CANDIDATE, TideWindow.STATUS_EXECUTABLE)
                .ge("window_start", request.getStartDate().atStartOfDay())
                .lt("window_start", request.getEndDate().plusDays(1).atStartOfDay()));

        List<TideWindow> created = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();

        for (CandidateSegment candidateSeg : candidates) {
            HalfOpenInterval candidate = candidateSeg.interval;
            // 该候选已被冻结（已排班/已关闭）窗口覆盖，重算不再重建
            boolean served = false;
            for (TideWindow f : frozen) {
                HalfOpenInterval fi = new HalfOpenInterval(f.getWindowStart(), f.getWindowEnd());
                if (fi.overlaps(candidate)) {
                    served = true;
                    break;
                }
            }
            if (served) {
                continue;
            }

            List<HalfOpenInterval> weatherFree = candidate.subtract(weatherBlockers);
            if (weatherFree.isEmpty()) {
                excluded.add(excludedCandidate(candidate, "候选时段全程处于禁作天气区间内"));
                continue;
            }

            List<HalfOpenInterval> executable = new ArrayList<>();
            boolean weatherSegmentTooShort = false;
            for (HalfOpenInterval segment : weatherFree) {
                if (segment.lengthMinutes() < minMinutes) {
                    weatherSegmentTooShort = true;
                    continue;
                }
                for (HalfOpenInterval covered : shiftCoverage) {
                    HalfOpenInterval hit = segment.intersect(covered);
                    if (hit == null) {
                        continue;
                    }
                    if (hit.lengthMinutes() < minMinutes) {
                        continue;
                    }
                    executable.add(hit);
                }
            }
            if (executable.isEmpty()) {
                String reason = weatherSegmentTooShort
                        ? "天气裁剪后可作业段短于最小窗口时长" + minMinutes + "分钟"
                        : "候选时段无人员班次覆盖";
                excluded.add(excludedCandidate(candidate, reason));
                continue;
            }
            for (HalfOpenInterval exec : executable) {
                TideWindow window = new TideWindow();
                window.setWindowNo(buildWindowNo(pond.getId(), exec.getStart().toLocalDate(),
                        created, frozen));
                window.setPondId(pond.getId());
                window.setSeaArea(seaArea);
                window.setPlanDate(exec.belongDate());
                window.setWindowStart(exec.getStart());
                window.setWindowEnd(exec.getEnd());
                window.setWindowStatus(TideWindow.STATUS_EXECUTABLE);
                window.setCandidateStart(candidate.getStart());
                window.setCandidateEnd(candidate.getEnd());
                window.setSourceTidePointId(candidateSeg.sourcePointId);
                window.setFrozenFlag(false);
                windowMapper.insert(window);
                created.add(window);
            }
        }

        evidenceService.record(TideChangeEvidence.ENTITY_WINDOW, pond.getId(),
                TideChangeEvidence.ACTION_WINDOW_RECALC, null, request.getOperator(),
                null, created,
                "重算换水窗口：池=" + pond.getPondCode() + "，海区=" + seaArea
                        + "，区间=" + request.getStartDate() + "~" + request.getEndDate()
                        + "，候选数=" + candidates.size() + "，可执行窗口数=" + created.size()
                        + "，排除候选数=" + excluded.size() + "，冻结保留窗口数=" + frozen.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pondId", pond.getId());
        result.put("seaArea", seaArea);
        result.put("startDate", request.getStartDate());
        result.put("endDate", request.getEndDate());
        result.put("createdCount", created.size());
        result.put("excludedCount", excluded.size());
        result.put("frozenKeptCount", frozen.size());
        result.put("windows", created);
        result.put("excluded", excluded);
        return result;
    }

    public TideWindow getOrThrow(Long id) {
        TideWindow window = windowMapper.selectById(id);
        if (window == null) {
            throw new IllegalArgumentException("换水窗口不存在，id=" + id);
        }
        return window;
    }

    public List<TideWindow> list(Long pondId, LocalDate planDate, String status) {
        QueryWrapper<TideWindow> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(planDate != null, "plan_date", planDate);
        wrapper.eq(status != null && !status.trim().isEmpty(), "window_status", status);
        wrapper.orderByAsc("window_start");
        return windowMapper.selectList(wrapper);
    }

    /** 排班发布/关闭时更新窗口状态、服务班组与冻结标记。 */
    public void updateStatusAndCrew(TideWindow window) {
        windowMapper.updateById(window);
    }

    /**
     * 窗口归属日一致性：可执行窗口归属日必须等于其半开起始时刻所在自然日——
     * 候选与窗口同口径，供关键边界（00:00、跨日裁剪）核查。
     */
    public boolean belongsDateConsistent(TideWindow window) {
        return window.getPlanDate().equals(window.getWindowStart().toLocalDate());
    }

    private List<HalfOpenInterval> unionOfShifts(List<TideCrewShift> shifts, LocalDate from, LocalDate to) {        List<HalfOpenInterval> intervals = new ArrayList<>();
        for (TideCrewShift shift : shifts) {
            if (!shift.getShiftEnd().isAfter(from.atStartOfDay())) {
                continue;
            }
            if (!shift.getShiftStart().isBefore(to.plusDays(1).atStartOfDay())) {
                continue;
            }
            intervals.add(new HalfOpenInterval(shift.getShiftStart(), shift.getShiftEnd()));
        }
        return merge(intervals);
    }

    private List<HalfOpenInterval> merge(List<HalfOpenInterval> intervals) {
        List<HalfOpenInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(HalfOpenInterval::getStart));
        List<HalfOpenInterval> merged = new ArrayList<>();
        for (HalfOpenInterval iv : sorted) {
            if (merged.isEmpty()) {
                merged.add(iv);
                continue;
            }
            HalfOpenInterval last = merged.get(merged.size() - 1);
            if (!iv.getStart().isAfter(last.getEnd())) {
                merged.set(merged.size() - 1,
                        new HalfOpenInterval(last.getStart(),
                                iv.getEnd().isAfter(last.getEnd()) ? iv.getEnd() : last.getEnd()));
            } else {
                merged.add(iv);
            }
        }
        return merged;
    }

    /** 候选段：半开区间 + 来源潮点（搭接合并后取较早潮点）。 */
    private static class CandidateSegment {
        private final HalfOpenInterval interval;
        private final Long sourcePointId;

        private CandidateSegment(HalfOpenInterval interval, Long sourcePointId) {
            this.interval = interval;
            this.sourcePointId = sourcePointId;
        }
    }

    /**
     * 相邻高潮展开候选若半开重叠则合并为一个候选（端点相接不合并，保持两个窗口）；
     * 归属日统一取合并后起始时刻所在自然日。
     */
    private List<CandidateSegment> mergeCandidates(List<CandidateSegment> sorted) {
        List<CandidateSegment> merged = new ArrayList<>();
        for (CandidateSegment seg : sorted) {
            if (merged.isEmpty()) {
                merged.add(seg);
                continue;
            }
            CandidateSegment last = merged.get(merged.size() - 1);
            if (seg.interval.getStart().isBefore(last.interval.getEnd())) {
                LocalDateTime end = seg.interval.getEnd().isAfter(last.interval.getEnd())
                        ? seg.interval.getEnd() : last.interval.getEnd();
                merged.set(merged.size() - 1, new CandidateSegment(
                        new HalfOpenInterval(last.interval.getStart(), end), last.sourcePointId));
            } else {
                merged.add(seg);
            }
        }
        return merged;
    }

    private Map<String, Object> excludedCandidate(HalfOpenInterval candidate, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("candidateStart", candidate.getStart());
        item.put("candidateEnd", candidate.getEnd());
        item.put("belongDate", candidate.belongDate());
        item.put("reason", reason);
        return item;
    }

    /**
     * 窗口编号 WIN-{pondId}-{yyyyMMdd}-{序号}：同池同日按窗口起始时刻排序编号，
     * 冻结窗口保留原序号，新建窗口顺延取号，保证重算确定且唯一。
     */
    private String buildWindowNo(Long pondId, LocalDate date,
                                 List<TideWindow> creating, List<TideWindow> frozen) {
        int used = 0;
        for (TideWindow f : frozen) {
            if (date.equals(f.getPlanDate())) {
                used++;
            }
        }
        for (TideWindow c : creating) {
            if (date.equals(c.getPlanDate())) {
                used++;
            }
        }
        int seq = used + 1;
        return "WIN-" + pondId + "-" + date.toString().replace("-", "") + "-" + seq;
    }
}
