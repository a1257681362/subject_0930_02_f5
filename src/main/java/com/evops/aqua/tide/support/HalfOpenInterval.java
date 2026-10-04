package com.evops.aqua.tide.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 半开时间区间 [start, end) —— 全模块唯一时间口径。
 * <p>
 * 候选生成、禁作天气裁剪、班次交集、设备互斥、归属日计算全部经此类型：
 * <ul>
 *   <li>端点相接（a.end == b.start）不算重叠 —— 设备可在同一时刻服务完一口池立即接下一口；</li>
 *   <li>重叠判定 {@code a.start < b.end && b.start < a.end}；</li>
 *   <li>归属日统一取 start 所在自然日（{@link #belongDate()}），候选与可执行窗口同一口径。</li>
 * </ul>
 */
public class HalfOpenInterval {

    private final LocalDateTime start;
    private final LocalDateTime end;

    public HalfOpenInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("区间端点不能为空");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("半开区间结束必须严格晚于开始：[" + start + "," + end + ")");
        }
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }

    /** 归属日：窗口起始时刻所在自然日（存储与 Jackson 均按 Asia/Shanghai，见 application.yml）。 */
    public java.time.LocalDate belongDate() {
        return start.toLocalDate();
    }

    public long lengthMinutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }

    /** 两个半开区间是否重叠：端点相等（相接）不算重叠。 */
    public boolean overlaps(HalfOpenInterval other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    /** 是否包含某时刻（左闭右开）。 */
    public boolean contains(LocalDateTime t) {
        return !t.isBefore(start) && t.isBefore(end);
    }

    /** 与另一区间的交集；相接或不相交返回 null。 */
    public HalfOpenInterval intersect(HalfOpenInterval other) {
        LocalDateTime s = max(this.start, other.start);
        LocalDateTime e = min(this.end, other.end);
        if (e.isAfter(s)) {
            return new HalfOpenInterval(s, e);
        }
        return null;
    }

    /**
     * 从本区间中减去 blockers 中所有半开区间，返回剩余片段（已按开始时刻排序）。
     * 天气禁作与班次镂空共用此方法；blocker 与本区间相接（不重叠）时不产生影响。
     */
    public List<HalfOpenInterval> subtract(List<HalfOpenInterval> blockers) {
        List<HalfOpenInterval> sorted = new ArrayList<>();
        if (blockers != null) {
            for (HalfOpenInterval b : blockers) {
                if (this.overlaps(b)) {
                    HalfOpenInterval clip = new HalfOpenInterval(
                            max(this.start, b.start), min(this.end, b.end));
                    sorted.add(clip);
                }
            }
        }
        if (sorted.isEmpty()) {
            return new ArrayList<>(Collections.singletonList(this));
        }
        sorted.sort(Comparator.comparing(HalfOpenInterval::getStart));
        // 合并相互搭接的阻塞段，保证镂空结果不重不漏
        List<HalfOpenInterval> merged = new ArrayList<>();
        for (HalfOpenInterval b : sorted) {
            if (merged.isEmpty()) {
                merged.add(b);
                continue;
            }
            HalfOpenInterval last = merged.get(merged.size() - 1);
            if (!b.start.isAfter(last.end)) {
                merged.set(merged.size() - 1,
                        new HalfOpenInterval(last.start, max(last.end, b.end)));
            } else {
                merged.add(b);
            }
        }
        List<HalfOpenInterval> remain = new ArrayList<>();
        LocalDateTime cursor = this.start;
        for (HalfOpenInterval b : merged) {
            if (b.start.isAfter(cursor)) {
                remain.add(new HalfOpenInterval(cursor, b.start));
            }
            if (b.end.isAfter(cursor)) {
                cursor = b.end;
            }
        }
        if (cursor.isBefore(this.end)) {
            remain.add(new HalfOpenInterval(cursor, this.end));
        }
        return remain;
    }

    @Override
    public String toString() {
        return "[" + start + "," + end + ")";
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
